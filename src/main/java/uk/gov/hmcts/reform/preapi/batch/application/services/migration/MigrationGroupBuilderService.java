package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.processor.MediaTransformationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.CleansedData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.PassItem;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


@Service
public class MigrationGroupBuilderService {
    private LoggingService loggingService;
    private static final String BOOKING_FIELD = "bookingField";
    private static final String CAPTURE_SESSION_FIELD = "captureSessionField";
    private static final String RECORDING_FIELD = "recordingField";
    private final CaseRepository caseRepository;

    private final EntityCreationService entityCreationService;
    private final InMemoryCacheService cacheService;
    private final MigrationTrackerService migrationTrackerService;
    private final MediaTransformationService recordingMediaKindTransform;
    private final Map<String, CreateCaseDTO> caseCache = new HashMap<>();


    @Autowired
    public MigrationGroupBuilderService(
        LoggingService loggingService,
        EntityCreationService entityCreationService,
        InMemoryCacheService cacheService,
        MigrationTrackerService migrationTrackerService,
        CaseRepository caseRepository,
        MediaTransformationService recordingMediaKindTransform
    ) {
        this.loggingService = loggingService;
        this.entityCreationService = entityCreationService;
        this.cacheService = cacheService;
        this.migrationTrackerService = migrationTrackerService;
        this.caseRepository = caseRepository;
        this.recordingMediaKindTransform = recordingMediaKindTransform;
    }

    // =========================
    // Initialization
    // =========================
    @EventListener(ContextRefreshedEvent.class)
    @Transactional
    public void init() {
        loadCaseCache();
    }

    private void loadCaseCache() {
        List<Case> cases = caseRepository.findAll();
        for (Case acase : cases) {
            CreateCaseDTO createCaseDTO = new CreateCaseDTO(acase);
            caseCache.put(acase.getReference(), createCaseDTO);
        }
    }

    // =========================
    // Entity Creation
    // =========================
    @SuppressWarnings("unchecked")
    public MigratedItemGroup createMigratedItemGroup(
        ExtractedMetadata item,
        CleansedData cleansedData
    ) {

        CreateCaseDTO acase = createCaseIfOrig(cleansedData);
        if (acase == null) {
            return null;
        }

        String participantPair = cleansedData.getWitnessFirstName() + '-' + cleansedData.getDefendantLastName();
        String baseKey = cacheService.generateBaseKey(acase.getReference(), participantPair);
        CreateBookingDTO booking = processBooking(baseKey, cleansedData, acase);
        CreateCaptureSessionDTO captureSession = processCaptureSession(baseKey, cleansedData, booking);
        CreateRecordingDTO recording = processRecording(baseKey, cleansedData, captureSession);

        // if (recording != null) {
        //     recordingMediaKindTransform.processMedia(recording.getFilename(), recording.getId());
        // }

        List<CreateShareBookingDTO> shareBookings = new ArrayList<>();
        List<CreateInviteDTO> invites = new ArrayList<>();
        List<Object> shareBookingResult = entityCreationService.createShareBookings(cleansedData, booking);

        if (shareBookingResult != null && shareBookingResult.size() == 2) {
            shareBookings = (List<CreateShareBookingDTO>) shareBookingResult.get(0);
            invites = (List<CreateInviteDTO>) shareBookingResult.get(1);
        }
        if (invites != null && !invites.isEmpty()) {
            for (CreateInviteDTO invite : invites) {
                migrationTrackerService.addInvitedUser(invite);
            }
        }
        Set<CreateParticipantDTO> participants = entityCreationService.createParticipants(cleansedData);
        PassItem passItem = new PassItem(item, cleansedData);
        MigratedItemGroup migrationGroup = new MigratedItemGroup(
            acase, booking, captureSession, recording, participants,
            shareBookings, invites, passItem
        );
        loggingService.logInfo("Migrating group: %s", migrationGroup);
        return migrationGroup;
    }

    private CreateCaseDTO createCaseIfOrig(CleansedData cleansedData) {
        String caseReference = cleansedData.getCaseReference();

        if (caseReference == null || caseReference.isBlank()) {
            return null;
        }

        if (caseCache.containsKey(caseReference)) {
            CreateCaseDTO existingCase = caseCache.get(caseReference);

            if (existingCase == null) {
                return null;
            }

            Set<CreateParticipantDTO> currentParticipants = existingCase.getParticipants();
            if (currentParticipants == null) {
                currentParticipants = new HashSet<>();
            }

            Set<CreateParticipantDTO> newParticipants = entityCreationService.createParticipants(cleansedData);
            Set<CreateParticipantDTO> updatedParticipants = new HashSet<>(currentParticipants);
            boolean changed = false;

            for (CreateParticipantDTO newParticipant : newParticipants) {
                boolean exists = false;

                for (CreateParticipantDTO existingParticipant : currentParticipants) {
                    if (isSameParticipant(existingParticipant, newParticipant)) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    updatedParticipants.add(newParticipant);
                    changed = true;
                }
            }

            if (changed) {
                existingCase.setParticipants(updatedParticipants);
            }
            return existingCase;
        }

        CreateCaseDTO newCase = entityCreationService.createCase(cleansedData);
        if (newCase == null) {
            return null;
        }

        caseCache.put(caseReference, newCase);
        return newCase;
    }

    private boolean isSameParticipant(CreateParticipantDTO p1, CreateParticipantDTO p2) {
        return p1.getParticipantType() == p2.getParticipantType()
            && Objects.equals(normalizeName(p1.getFirstName()), normalizeName(p2.getFirstName()))
            && Objects.equals(normalizeName(p1.getLastName()), normalizeName(p2.getLastName()));
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private CreateBookingDTO processBooking(String baseKey, CleansedData cleansedData, CreateCaseDTO acase) {
        if(cacheService.checkHashKeyExists(baseKey, BOOKING_FIELD)){
            CreateBookingDTO bookingDTO = cacheService.getHashValue(baseKey, BOOKING_FIELD, CreateBookingDTO.class);
            return bookingDTO;
        }
        return entityCreationService.createBooking(cleansedData, acase, baseKey);
    }

    private CreateCaptureSessionDTO processCaptureSession(
        String baseKey,
        CleansedData cleansedData,
        CreateBookingDTO booking
    ) {
        if(cacheService.checkHashKeyExists(baseKey, CAPTURE_SESSION_FIELD)){
            CreateCaptureSessionDTO captureSessionDTO = cacheService.getHashValue(
                baseKey,
                CAPTURE_SESSION_FIELD,
                CreateCaptureSessionDTO.class
            );
            return captureSessionDTO;
        }
        return entityCreationService.createCaptureSession(cleansedData, booking, baseKey);
    }

    private CreateRecordingDTO processRecording(
        String baseKey,
        CleansedData cleansedItem,
        CreateCaptureSessionDTO captureSession
    ) {
        if(cacheService.checkHashKeyExists(baseKey, RECORDING_FIELD)){
            CreateRecordingDTO recordingDTO = cacheService.getHashValue(
                baseKey,
                RECORDING_FIELD,
                CreateRecordingDTO.class
            );
        
            return recordingDTO;
        }
        CreateRecordingDTO recording = entityCreationService.createRecording(baseKey, cleansedItem, captureSession);
        return recording;
    }
}
