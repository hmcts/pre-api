package uk.gov.hmcts.reform.preapi.batch.processor;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVChannelData;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVSitesData;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.entities.batch.FailedItem;
import uk.gov.hmcts.reform.preapi.entities.batch.FailedItemXML;
import uk.gov.hmcts.reform.preapi.entities.batch.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.entities.batch.PassItem;
import uk.gov.hmcts.reform.preapi.entities.batch.UnifiedArchiveData;
import uk.gov.hmcts.reform.preapi.entities.batch.XMLArchiveFileData;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.services.batch.AzureBlobService;
import uk.gov.hmcts.reform.preapi.services.batch.DataExtractionService;
import uk.gov.hmcts.reform.preapi.services.batch.DataTransformationService;
import uk.gov.hmcts.reform.preapi.services.batch.DataValidationService;
import uk.gov.hmcts.reform.preapi.services.batch.EntityCreationService;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationTrackerServiceXML;
import uk.gov.hmcts.reform.preapi.services.batch.RedisService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;

/**
 * Processes various CSV data types and transforms them into MigratedItemGroup for further processing.
 */
@Component
public class Processor implements ItemProcessor<Object, MigratedItemGroup> {
    private final DataExtractionService extractionService;
    private final RedisService redisService;
    private final EntityCreationService entityCreationService;
    private final DataTransformationService transformationService;
    private final DataValidationService validationService;
    private final MigrationTrackerService migrationTrackerService;
    private final MigrationTrackerServiceXML migrationTrackerServiceXML;
    private final CaseRepository caseRepository;
    private final Map<String, Case> caseCache = new HashMap<>();
    private final Map<String, List<String[]>> channelUserDataMap = new HashMap<>();
    private final Map<String, Recording> origRecordingsMap = new HashMap<>();

    @Autowired
    public Processor(
        DataExtractionService extractionService,
        EntityCreationService entityCreationService,
        AzureBlobService azureBlobFetcher,
        DataTransformationService transformationService,
        DataValidationService validationService,
        RedisService redisService,
        CaseRepository caseRepository,
        UserRepository userRepository,
        MigrationTrackerService migrationTrackerService,
        MigrationTrackerServiceXML migrationTrackerServiceXML
    ) {
        this.extractionService = extractionService;
        this.redisService = redisService;
        this.entityCreationService = entityCreationService;
        this.transformationService = transformationService;
        this.validationService = validationService;
        this.caseRepository = caseRepository;
        this.migrationTrackerService = migrationTrackerService;
        this.migrationTrackerServiceXML = migrationTrackerServiceXML;
    }

    // =========================
    // Initialization
    // =========================
    @EventListener(ContextRefreshedEvent.class)
    public void init() {
        loadCaseCache();
    }

    private void loadCaseCache() {
        List<Case> cases = caseRepository.findAll();
        for (Case acase : cases) {
            caseCache.put(acase.getReference(), acase);
        }
    }

    // =========================
    // Main Processor Logic
    // =========================
    @Override
    public MigratedItemGroup process(Object item) throws Exception {
        if (item instanceof CSVArchiveListData) {
            return processArchiveListData((CSVArchiveListData) item);
        } else if (item instanceof XMLArchiveFileData) {
            return processXMLData((XMLArchiveFileData) item);
        } else if (item instanceof CSVSitesData) {
            processSitesData((CSVSitesData) item);
        } else if (item instanceof CSVChannelData) {
            processChannelUserData((CSVChannelData) item);
        } else {
            Logger.getAnonymousLogger().severe("Unsuported item type: " + item.getClass().getName());
        }
        return null;
    }

    private void processSitesData(CSVSitesData sitesItem) {
        Logger.getAnonymousLogger().info("Processor: saving sites data");
        redisService.saveHashValue("sites_data", sitesItem.getSiteReference(), sitesItem.getSiteName());
        Logger.getAnonymousLogger().info("Processor: finished saving sites data");
    }

    private void processChannelUserData(CSVChannelData channelDataItem) {
        channelUserDataMap
            .computeIfAbsent(channelDataItem.getChannelName(), k -> new ArrayList<>())
            .add(new String[]{channelDataItem.getChannelUser(), channelDataItem.getChannelUserEmail()});
    }

    private MigratedItemGroup processArchiveListData(CSVArchiveListData archiveItem) {
        try {
            Map<String, Object> transformationResult = transformData(archiveItem);
            Logger.getAnonymousLogger().info("processArchiveListData: transformation result "+transformationResult );
            String errorMessage = (String) transformationResult.get("errorMessage");
            if (errorMessage != null) {
                migrationTrackerService.addFailedItem(new FailedItem(archiveItem, errorMessage));
                return null;  
            }   

            CleansedData cleansedData = (CleansedData) transformationResult.get("cleansedData");
            if (!validateData(cleansedData, archiveItem)) {
                return null;
            }

            String pattern = extractPattern(archiveItem);
            return createMigratedItemGroup(pattern, archiveItem, cleansedData);
        } catch (Exception e) {
            migrationTrackerService.addFailedItem(new FailedItem(
                archiveItem, "Error processing item: " + e.getMessage()));
        }
        return null;
    }

    private MigratedItemGroup processXMLData(XMLArchiveFileData archiveItem) {
        try {
            Logger.getAnonymousLogger().info("PROCESSOR: Starting processing for XML file: " 
                + archiveItem.getDisplayName());

            UnifiedArchiveData unifiedData = convertXMLToUnifiedData(archiveItem);
            Logger.getAnonymousLogger().info("PROCESSOR: Successfully converted XML data to unified format for file: " 
                + archiveItem.getDisplayName());

            Map<String, Object> transformationResult = transformDataXML(unifiedData);
            Logger.getAnonymousLogger().info("PROCESSOR: Transformation step completed for file: " 
                + archiveItem.getDisplayName());

            String errorMessage = (String) transformationResult.get("errorMessage");
            if (errorMessage != null) {
                Logger.getAnonymousLogger().warning("PROCESSOR: Transformation failed for file: " 
                    + archiveItem.getDisplayName() + ". Error: " + errorMessage);
                migrationTrackerServiceXML.addFailedItem(new FailedItemXML(unifiedData, errorMessage));
                return null;  
            }   

            CleansedData cleansedData = (CleansedData) transformationResult.get("cleansedData");
            if (!validateDataXML(cleansedData, unifiedData)) {
                Logger.getAnonymousLogger().warning("PROCESSOR: Validation failed for file: " 
                    + archiveItem.getDisplayName());
                return null;
            }

            Logger.getAnonymousLogger().info("PROCESSOR: Data validation passed for file: " 
                + archiveItem.getDisplayName());

            String pattern = extractPatternXML(unifiedData);
            Logger.getAnonymousLogger().info("PROCESSOR: Pattern extracted successfully for file: " 
                + archiveItem.getDisplayName() + ". Pattern: " + pattern);
                                                     
            MigratedItemGroup migratedItem = createMigratedItemGroupXML(pattern, unifiedData, cleansedData);

            Logger.getAnonymousLogger().info("PROCESSOR: Successfully created migrated item for file: " 
                + archiveItem.getDisplayName());

            Logger.getAnonymousLogger().info("PROCESSOR: Completed processing for XML file: " 
                + archiveItem.getDisplayName());
            return migratedItem;

        } catch (Exception e) {
            Logger.getAnonymousLogger().severe("PROCESSOR: Exception occurred while processing file: " 
                + archiveItem.getDisplayName() + ". Error: " + e.getMessage());            
            // migrationTrackerServiceXML.addFailedItem(new FailedItemXML(
            //     unifiedData, "Error processing item: " + e.getMessage()));
        }
        return null;
    }

    // =========================
    // Transformation and Validation
    // =========================
    private Map<String, Object> transformDataXML(UnifiedArchiveData archiveItem) {
        Map<String, String> sitesDataMap = redisService.getHashAll("sites_data", String.class, String.class);

        if (sitesDataMap == null || sitesDataMap.isEmpty()) {
            throw new IllegalStateException("Sites data not found in Redis");
        }
        
        // Map<String, List<String[]>> channelUserDataMap = loadChannelDataFromRedis();

        return transformationService.transformArchiveListDataXML(archiveItem, sitesDataMap, channelUserDataMap);
    }


    private Map<String, Object> transformData(CSVArchiveListData archiveItem) {
        Map<String, String> sitesDataMap = redisService.getHashAll("sites_data", String.class, String.class);

        if (sitesDataMap == null || sitesDataMap.isEmpty()) {
            throw new IllegalStateException("Sites data not found in Redis");
        }
        
        // Map<String, List<String[]>> channelUserDataMap = loadChannelDataFromRedis();

        return transformationService.transformArchiveListData(archiveItem, sitesDataMap, channelUserDataMap);
    }

    private boolean validateData(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        return validationService.validateCleansedData(cleansedData, archiveItem);
    }

    private boolean validateDataXML(CleansedData cleansedData, UnifiedArchiveData archiveItem) {
        return validationService.validateCleansedDataXML(cleansedData, archiveItem);
    }

    private String extractPattern(CSVArchiveListData archiveItem) {
        try {
            Map.Entry<String, Matcher> patternMatch = extractionService.matchPattern(archiveItem);
            return patternMatch != null ? patternMatch.getKey() : null; 
        } catch (Exception e) {
            migrationTrackerService.addFailedItem(new FailedItem(archiveItem, e.getMessage()));
            return null;
        }
    }

    private String extractPatternXML(UnifiedArchiveData archiveItem) {
        try {
            Map.Entry<String, Matcher> patternMatch = extractionService.matchPatternXML(archiveItem);
            return patternMatch != null ? patternMatch.getKey() : null; 
        } catch (Exception e) {
            Logger.getAnonymousLogger().info(e.getMessage());
            // migrationTrackerService.addFailedItem(new FailedItem(archiveItem, e.getMessage()));
            return null;
        }
    }

    // =========================
    // Entity Creation Logic
    // =========================
    private MigratedItemGroup createMigratedItemGroup(String pattern, 
        CSVArchiveListData archiveItem, CleansedData cleansedData) {

        Case acase = createCaseIfOrig(cleansedData);
        if (acase == null) {
            return null;
        }
        
        String participantPair =  cleansedData.getWitnessFirstName() + '-' + cleansedData.getDefendantLastName();
        String baseKey = redisService.generateBaseKey(acase.getReference(), participantPair);
        Booking booking = processBooking(baseKey, cleansedData, acase);
        CaptureSession captureSession = processCaptureSession(baseKey, cleansedData, booking);
        Recording recording = createRecording(archiveItem, cleansedData, baseKey, captureSession);
        
        Set<Participant> participants = entityCreationService.createParticipants(cleansedData, acase);

        List<ShareBooking> shareBookings = new ArrayList<>();
        List<User> users = new ArrayList<>();
        List<Object> shareBookingResult = entityCreationService.createShareBookings(cleansedData, booking);
        // shareBookings = (List<ShareBooking>) shareBookingResult.get(0);
        // users = (List<User>) shareBookingResult.get(1);
        
        PassItem passItem = new PassItem(pattern, archiveItem.getArchiveName(), acase, 
            booking, captureSession, recording, participants, shareBookings, users);
        return  new MigratedItemGroup(acase, booking, captureSession, recording, participants, 
            passItem, shareBookings, users);
    }

    private MigratedItemGroup createMigratedItemGroupXML(String pattern, 
        UnifiedArchiveData archiveItem, CleansedData cleansedData) {

        Case acase = createCaseIfOrig(cleansedData);
        if (acase == null) {
            return null;
        }
        
        String participantPair =  cleansedData.getWitnessFirstName() + '-' + cleansedData.getDefendantLastName();
        String baseKey = redisService.generateBaseKey(acase.getReference(), participantPair);
        Booking booking = processBooking(baseKey, cleansedData, acase);
        CaptureSession captureSession = processCaptureSession(baseKey, cleansedData, booking);
        Recording recording = createRecordingXML(archiveItem, cleansedData, baseKey, captureSession);
        
        Set<Participant> participants = entityCreationService.createParticipants(cleansedData, acase);

        List<ShareBooking> shareBookings = new ArrayList<>();
        List<User> users = new ArrayList<>();
        List<Object> shareBookingResult = entityCreationService.createShareBookings(cleansedData, booking);
        // shareBookings = (List<ShareBooking>) shareBookingResult.get(0);
        // users = (List<User>) shareBookingResult.get(1);
        
        PassItem passItem = new PassItem(pattern, archiveItem.getArchiveName(), acase, 
            booking, captureSession, recording, participants, shareBookings, users);

        return  new MigratedItemGroup(acase, booking, captureSession, recording, participants, 
            passItem, shareBookings, users);
    }

    
    private Case createCaseIfOrig(CleansedData cleansedData) {
        String caseReference = transformationService.buildCaseReference(cleansedData);
        if (caseCache.containsKey(caseReference)) {
            return caseCache.get(caseReference);
        }

        if (transformationService.isOriginalVersion(cleansedData)) {
            Case newCase = entityCreationService.createCase(cleansedData);
            caseCache.put(caseReference, newCase);
            return newCase;
        }
        return null;
    }

    private Booking processBooking(String baseKey, CleansedData cleansedData, Case acase) {
        if (redisService.hashKeyExists(baseKey, "bookingField")) {
            BookingDTO bookingDTO = redisService.getHashValue(baseKey, "bookingField", BookingDTO.class);
            return convertToBooking(bookingDTO);
        }
        return createBooking(cleansedData, baseKey, acase);
    }

    private CaptureSession processCaptureSession(String baseKey, CleansedData cleansedData, Booking booking) {
        if (redisService.hashKeyExists(baseKey, "captureSessionField")) {
            CaptureSessionDTO captureSessionDTO = redisService.getHashValue(baseKey,
                "captureSessionField", CaptureSessionDTO.class);
            return convertToCaptureSession(captureSessionDTO, booking);
        }
        return createCaptureSession(cleansedData, baseKey, booking);
    }

    private Booking createBooking(CleansedData cleansedItem, String redisKey, Case acase) {
        Booking booking = entityCreationService.createBooking(cleansedItem,acase);
        BookingDTO bookingDTO = new BookingDTO(booking);
        redisService.saveHashValue(redisKey, "bookingField", bookingDTO);
        return booking;
    }

    private CaptureSession createCaptureSession(CleansedData cleansedItem, String redisKey,  Booking booking) {       
        CaptureSession captureSession = entityCreationService.createCaptureSession(cleansedItem, booking);
        CaptureSessionDTO captureSessionDTO = new CaptureSessionDTO(captureSession);
        redisService.saveHashValue(redisKey, "captureSessionField", captureSessionDTO);
        return captureSession;
    }

    private Recording createRecording(CSVArchiveListData archiveItem, CleansedData cleansedItem, 
        String redisKey, CaptureSession captureSession) {
        Recording recording = new Recording();
        UUID recordingID = UUID.randomUUID();
        recording.setId(recordingID);
        recording.setCaptureSession(captureSession);
        recording.setDuration(cleansedItem.getDuration());
        recording.setFilename(archiveItem.getArchiveName());
        recording.setCreatedAt(cleansedItem.getRecordingTimestamp());
        recording.setVersion(cleansedItem.getRecordingVersionNumber());

        return determineParentRecording(cleansedItem, recording);
    }

    private Recording createRecordingXML(UnifiedArchiveData archiveItem, CleansedData cleansedItem, 
        String redisKey, CaptureSession captureSession) {
        Recording recording = new Recording();
        UUID recordingID = UUID.randomUUID();
        recording.setId(recordingID);
        recording.setCaptureSession(captureSession);
        recording.setDuration(cleansedItem.getDuration());
        recording.setFilename(archiveItem.getArchiveName());
        recording.setCreatedAt(cleansedItem.getRecordingTimestamp());
        recording.setVersion(cleansedItem.getRecordingVersionNumber());

        return determineParentRecording(cleansedItem, recording);
    }

    private Recording determineParentRecording(CleansedData cleansedItem, Recording currentRecording) {
        if (transformationService.isOriginalVersion(cleansedItem)) {
            currentRecording.setParentRecording(currentRecording);
            currentRecording.setVersion(1);
            origRecordingsMap.put(cleansedItem.getUrn(), currentRecording);
        } else {
            Recording parentRecording = origRecordingsMap.get(cleansedItem.getUrn());
            if (parentRecording != null) {
                currentRecording.setParentRecording(parentRecording);
                currentRecording.setVersion(2);
            }
        }
        return currentRecording;
    }

    
    //======================
    // Conversion Methods
    //======================
    private Booking convertToBooking(BookingDTO bookingDTO) {
        if (bookingDTO == null) {
            return null;
        }
        
        Booking booking = new Booking();
        booking.setId(bookingDTO.getId());
    
        if (bookingDTO.getCaseDTO() != null) {
            Case acase = new Case();
            acase.setId(bookingDTO.getCaseDTO().getId());
            acase.setReference(bookingDTO.getCaseDTO().getReference());
            booking.setCaseId(acase);
        }
    
        booking.setScheduledFor(bookingDTO.getScheduledFor());
        booking.setCreatedAt(bookingDTO.getCreatedAt());
        booking.setModifiedAt(bookingDTO.getModifiedAt());
        return booking;
    }


    private CaptureSession convertToCaptureSession(CaptureSessionDTO captureSessionDTO, Booking booking) {
        if (captureSessionDTO == null) {
            return null;
        }

        CaptureSession captureSession = new CaptureSession();
        captureSession.setId(captureSessionDTO.getId());
        captureSession.setBooking(booking); 
        captureSession.setOrigin(captureSessionDTO.getOrigin());
        captureSession.setStatus(captureSessionDTO.getStatus());

        return captureSession;
    }

    //======================
    // CONVERTING CSV AND XML TO UNIFIED DATA
    //======================
    public UnifiedArchiveData convertCSVToUnifiedData(CSVArchiveListData csvData) {
        return new UnifiedArchiveData(
            csvData.getArchiveNameNoExt(),
            csvData.getCreateTime(),
            csvData.getDuration(),
            "From CSV: " + csvData.getDescription()
        );
    }

    //  method to convert XML data to UnifiedArchiveData
    public UnifiedArchiveData convertXMLToUnifiedData(XMLArchiveFileData xmlData) {
        List<XMLArchiveFileData.MP4File> mp4Files = xmlData.getMp4FileGrp().getMp4Files();
        
        if (mp4Files == null || mp4Files.isEmpty()) {
            throw new IllegalArgumentException("No MP4 files found in the XML data");
        }
        
        XMLArchiveFileData.MP4File mp4File = mp4Files.get(0);

        return new UnifiedArchiveData(
            xmlData.getDisplayName(),
            String.valueOf(mp4File.getCreatTime()), 
            xmlData.getDuration(),
            "From XML"
        );
    }



}