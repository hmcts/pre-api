package uk.gov.hmcts.reform.preapi.batch.processor;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVChannelData;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVSitesData;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.entities.batch.FailedItem;
import uk.gov.hmcts.reform.preapi.entities.batch.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.entities.batch.PassItem;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.services.batch.DataExtractionService;
import uk.gov.hmcts.reform.preapi.services.batch.DataTransformationService;
import uk.gov.hmcts.reform.preapi.services.batch.DataValidationService;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationTrackerService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;

@Component
public class CSVProcessor implements ItemProcessor<Object, MigratedItemGroup> {

    private final DataExtractionService extractionService;
    private final DataTransformationService transformationService;
    private final DataValidationService validationService;
    private final MigrationTrackerService migrationTrackerService;
    private final CaseRepository caseRepository;
    private final CourtRepository courtRepository;
    private final Map<String, Case> caseCache = new HashMap<>();
    private final Map<String, UUID> courtCache = new HashMap<>();
    private final Map<String, String> sitesDataMap = new HashMap<>();
    private final Map<String, String> channelUserDataMap = new HashMap<>();
    private final Map<String, Recording> origRecordingsMap = new HashMap<>();

    @Autowired
    public CSVProcessor(
            DataExtractionService extractionService,
            DataTransformationService transformationService,
            DataValidationService validationService,
            CaseRepository caseRepository,
            CourtRepository courtRepository,
            ParticipantRepository participantRepository,
            MigrationTrackerService migrationTrackerService) {
        this.extractionService = extractionService;
        this.transformationService = transformationService;
        this.validationService = validationService;
        this.caseRepository = caseRepository;
        this.courtRepository = courtRepository;
        this.migrationTrackerService = migrationTrackerService;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void init() {
        loadCourtCache();
        loadCaseCache();
    }

    @Override
    public MigratedItemGroup process(Object item) throws Exception {
        if (item instanceof CSVArchiveListData) {
            return processArchiveListData((CSVArchiveListData) item);
        } else if (item instanceof CSVSitesData) {
            processSitesData((CSVSitesData) item);
        } else if (item instanceof CSVChannelData) {
            processChannelUserData((CSVChannelData) item);
        } else {
            Logger.getAnonymousLogger().severe("Unsupported item type: " + item.getClass().getName());
        }
        return null;
    }

    private Object processSitesData(CSVSitesData sitesItem) {
        sitesDataMap.put(sitesItem.getSiteReference(), sitesItem.getSiteName());
        return sitesItem;
    }

    private Object processChannelUserData(CSVChannelData channelDataItem) {
        channelUserDataMap.put(channelDataItem.getChannelName(), channelDataItem.getChannelUser());
        return channelDataItem;
    }

    private MigratedItemGroup processArchiveListData(CSVArchiveListData archiveItem) {
        try {
            CleansedData cleansedData = transformationService.transformArchiveListData(
                    archiveItem, sitesDataMap, courtCache);

            if (!validationService.validateCleansedData(cleansedData, archiveItem)) {
                return null;
            }

            Map.Entry<String, Matcher> patternMatch = getPatternMatch(archiveItem);
            String pattern = patternMatch != null ? patternMatch.getKey() : null; 

            MigratedItemGroup migratedItemGroup = createMigratedItemGroup(
                pattern, archiveItem.getArchiveName(), cleansedData);
            return migratedItemGroup;
           
        } catch (Exception e) {
            migrationTrackerService.addFailedItem(new FailedItem(
                archiveItem, "Error processing item: " + e.getMessage()));
        }
        return null;
    }


    private Map.Entry<String, Matcher> getPatternMatch(CSVArchiveListData archiveItem) {
        return extractionService.matchPattern(archiveItem);
    }

    private MigratedItemGroup createMigratedItemGroup(String pattern, String archiveName, CleansedData cleansedData) {
        MigratedItemGroup migratedItemGroup = null; 
        
        Case acase = createCaseIfOrig(cleansedData);
        if (acase != null) {
            Booking booking = createBooking(cleansedData, acase);
            CaptureSession captureSession = createCaptureSession(cleansedData, booking);
            Recording recording = createRecording(cleansedData, captureSession);
            Set<Participant> participants = createParticipants(cleansedData, acase);

            PassItem passItem = new PassItem(pattern, archiveName, acase, booking, captureSession, recording);

            try {
                migratedItemGroup = new MigratedItemGroup(acase, booking, captureSession, recording, participants, passItem);
                migrationTrackerService.addMigratedItem(passItem);
            } catch (Exception e) {
                Logger.getAnonymousLogger().info("ERROR: " + e.getMessage()); 
            }
            
            return migratedItemGroup; 
        }
        
        return null;
    }

    private Case createCaseIfOrig(CleansedData cleansedData) {
        String caseReference = transformationService.buildCaseReference(cleansedData);

        if (caseCache.containsKey(caseReference)) {
            return caseCache.get(caseReference);
        }

        if (transformationService.isOriginalVersion(cleansedData)) {
            Case newCase = createCase(cleansedData);
            caseCache.put(caseReference, newCase);
            return newCase;
        }

        return null;
    }

    private Case createCase(CleansedData cleansedItem) {
        Case aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setCourt(cleansedItem.getCourt());
        aCase.setReference(transformationService.buildCaseReference(cleansedItem));
        aCase.setTest(cleansedItem.isTest());
        aCase.setCreatedAt(cleansedItem.getRecordingTimestamp());
        aCase.setState(cleansedItem.getState());
        // aCase.setParticipants(new HashSet<>());
        // aCase.setParticipants(createParticipants(cleansedItem,aCase));
        
        return aCase;
    }

    private Booking createBooking(CleansedData cleansedItem, Case acase) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(acase);
        booking.setScheduledFor(cleansedItem.getRecordingTimestamp());
        booking.setCreatedAt(cleansedItem.getRecordingTimestamp());
        // booking.setParticipants(acase.getParticipants());

        return booking;
    }

    private CaptureSession createCaptureSession(CleansedData cleansedItem, Booking booking) {
        CaptureSession captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
        captureSession.setOrigin(RecordingOrigin.VODAFONE);
        captureSession.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        return captureSession;
    }

    private Recording createRecording(CleansedData cleansedItem, CaptureSession captureSession) {
        Recording recording = new Recording();
        UUID recordingID = UUID.randomUUID();
        recording.setId(recordingID);
        recording.setCaptureSession(captureSession);
        recording.setDuration(cleansedItem.getDuration());
        recording.setFilename("....");
        recording.setCreatedAt(cleansedItem.getRecordingTimestamp());
        recording.setVersion(cleansedItem.getRecordingVersionNumber());

        if (transformationService.isOriginalVersion(cleansedItem)) {
            recording.setParentRecording(recording);
            origRecordingsMap.put(cleansedItem.getUrn(), recording);
        } else {
            Recording parentRecording = origRecordingsMap.get(cleansedItem.getUrn());
            if (parentRecording != null) {
                recording.setParentRecording(parentRecording);
            }
        }

        return recording;
    }

    private Set<Participant> createParticipants(CleansedData cleansedItem, Case acase) {
        Participant witness = new Participant();
        witness.setId(UUID.randomUUID());
        witness.setParticipantType(ParticipantType.WITNESS);
        witness.setCaseId(acase); 
        witness.setFirstName(cleansedItem.getWitnessFirstName());
        witness.setLastName(""); 

        Participant defendant = new Participant();
        defendant.setId(UUID.randomUUID());
        defendant.setParticipantType(ParticipantType.DEFENDANT);
        defendant.setCaseId(acase); 
        defendant.setFirstName(""); 
        defendant.setLastName(cleansedItem.getDefendantLastName());

        var participants = Set.of(witness, defendant);
        // acase.setParticipants(participants);
        return participants;
    }

    private void loadCourtCache() {
        List<Court> courts = courtRepository.findAll();
        for (Court court : courts) {
            courtCache.put(court.getName(), court.getId());
        }
    }

    private void loadCaseCache() {
        List<Case> cases = caseRepository.findAll();
        for (Case acase : cases) {
            caseCache.put(acase.getReference(), acase);
        }
    }
}