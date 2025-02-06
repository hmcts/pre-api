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
import uk.gov.hmcts.reform.preapi.entities.batch.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.entities.batch.PassItem;
import uk.gov.hmcts.reform.preapi.entities.batch.TransformationResult;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.services.batch.AzureBlobService;
import uk.gov.hmcts.reform.preapi.services.batch.CsvWriterService;
import uk.gov.hmcts.reform.preapi.services.batch.DataExtractionService;
import uk.gov.hmcts.reform.preapi.services.batch.DataTransformationService;
import uk.gov.hmcts.reform.preapi.services.batch.DataValidationService;
import uk.gov.hmcts.reform.preapi.services.batch.EntityCreationService;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.services.batch.RedisService;
import uk.gov.hmcts.reform.preapi.util.batch.RecordingUtils;

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
    private static final String REDIS_SITES_KEY = "sites_data";
    private static final String REDIS_BOOKING_FIELD = "bookingField";
    private static final String REDIS_CAPTURE_SESSION_FIELD = "captureSessionField";
    private final DataExtractionService extractionService;
    private final RedisService redisService;
    private final EntityCreationService entityCreationService;
    private final DataTransformationService transformationService;
    private final DataValidationService validationService;
    private final MigrationTrackerService migrationTrackerService;
    private final CaseRepository caseRepository;
    private final ReferenceDataProcessor referenceDataProcessor;
    private final RecordingMediaKindTransform recordingMediaKindTransform;
    private final CsvWriterService csvWriterService;

    private final Map<String, Case> caseCache = new HashMap<>();
    // private final Map<String, List<String[]>> channelUserDataMap = new HashMap<>();
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
        CsvWriterService csvWriterService,
        ReferenceDataProcessor referenceDataProcessor,
        RecordingMediaKindTransform recordingMediaKindTransform,
        UserRepository userRepository,
        MigrationTrackerService migrationTrackerService
    ) {
        this.extractionService = extractionService;
        this.redisService = redisService;
        this.entityCreationService = entityCreationService;
        this.transformationService = transformationService;
        this.validationService = validationService;
        this.csvWriterService = csvWriterService;
        this.caseRepository = caseRepository;
        this.referenceDataProcessor = referenceDataProcessor;
        this.recordingMediaKindTransform = recordingMediaKindTransform;
        this.migrationTrackerService = migrationTrackerService;
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
        if (item instanceof CSVArchiveListData ) {
            return process((CSVArchiveListData) item);
        } else if (item instanceof CSVSitesData || item instanceof CSVChannelData) {
            referenceDataProcessor.process(item);
            return null;
        } else {
            Logger.getAnonymousLogger().severe("Unsuported item type: " + item.getClass().getName());
        }
        return null;
    }

    private MigratedItemGroup process(CSVArchiveListData archiveItem) {
        // 1. Transform Data
        CleansedData cleansedData;
        try {
            cleansedData = transformArchiveData(archiveItem);
        } catch (Exception e) {
            migrationTrackerService.addFailedItem(
                new FailedItem(archiveItem, "Transformation error: " + e.getMessage())
            );
            return null;
        }
       
        // 2. Check if already migrated
        boolean alreadyMigrated = redisService.hashKeyExists("vf:case:", cleansedData.getCaseReference());
        if (alreadyMigrated) {
            migrationTrackerService.addFailedItem(new FailedItem(archiveItem, "Already migrated: "+ cleansedData.getCaseReference()));
            return null;
        }

        // 3. Validate Transformed Data
        boolean validated = validateTransformedData(cleansedData, archiveItem);
        if (!validated){
            return null;
        } 

        // 4. Extract Pattern from Archive
        String pattern ;
        pattern = extractPattern(archiveItem);

        // 5. Create Migrated Item Group
        try {
            return createMigratedItemGroup(pattern, archiveItem, cleansedData);
        } catch (Exception e) {
            migrationTrackerService.addFailedItem(
                new FailedItem(archiveItem, "Failed to create migrated item group: " + e.getMessage())
            );
            return null;
        }
    }

    
    // =========================
    // Transformation and Validation
    // =========================
    private CleansedData transformArchiveData(CSVArchiveListData archiveItem) {
        Map<String, List<String[]>> channelUserDataMap= referenceDataProcessor.getChannelUserDataMap();

        TransformationResult transformationResult = transformationService.transformArchiveListData(archiveItem, channelUserDataMap);
        String errorMessage = (String) transformationResult.getErrorMessage();

        if (errorMessage != null) {
            migrationTrackerService.addFailedItem(new FailedItem(archiveItem, errorMessage));
            return null;
        }

        return (CleansedData) transformationResult.getCleansedData();
    }

    private boolean validateTransformedData(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        TransformationResult validationResult = validationService.validateCleansedData(cleansedData, archiveItem);
        String validationErrorMessage = (String) validationResult.getErrorMessage();

        if (validationErrorMessage != null) {
            FailedItem fail =  new FailedItem(archiveItem, validationErrorMessage);
            Logger.getAnonymousLogger().info("Failed item: " + fail.getReason());
            migrationTrackerService.addFailedItem(new FailedItem(archiveItem, validationErrorMessage));
            return false;
        }

        return true;
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

    // =========================
    // Entity Creation Logic
    // =========================
    @SuppressWarnings("unchecked")
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
        // recordingMediaKindTransform.processMedia(cleansedData.getFileName(), recording.getId());

        Set<Participant> participants = entityCreationService.createParticipants(cleansedData, acase);
        List<ShareBooking> shareBookings = new ArrayList<>();
        List<User> users = new ArrayList<>();
        List<Object> shareBookingResult = entityCreationService.createShareBookings(cleansedData, booking);
        
        if (shareBookingResult != null && shareBookingResult.size() == 2) {
            shareBookings = (List<ShareBooking>) shareBookingResult.get(0);
            users = (List<User>) shareBookingResult.get(1);
        }
        
        // writeShareBookingUsersCsv(users);

        PassItem passItem = new PassItem(pattern, archiveItem.getArchiveName(), acase, 
            booking, captureSession, recording, participants, shareBookings, users);
        return new MigratedItemGroup(acase, booking, captureSession, recording, participants, 
            passItem, shareBookings, users);
    }

    
    private Case createCaseIfOrig(CleansedData cleansedData) {
        String caseReference = cleansedData.getCaseReference();        

        if (caseCache.containsKey(caseReference)) {
            return caseCache.get(caseReference);
        }

        Case newCase = entityCreationService.createCase(cleansedData);
        caseCache.put(caseReference, newCase);
        return newCase;
        
    }

    private Booking processBooking(String baseKey, CleansedData cleansedData, Case acase) {
        if (redisService.hashKeyExists(baseKey, REDIS_BOOKING_FIELD)) {
            BookingDTO bookingDTO = redisService.getHashValue(baseKey, REDIS_BOOKING_FIELD, BookingDTO.class);
            return convertToBooking(bookingDTO);
        }
        return createBooking(cleansedData, baseKey, acase);
    }

    private CaptureSession processCaptureSession(String baseKey, CleansedData cleansedData, Booking booking) {
        if (redisService.hashKeyExists(baseKey, REDIS_CAPTURE_SESSION_FIELD)) {
            CaptureSessionDTO captureSessionDTO = redisService.getHashValue(baseKey,
                REDIS_CAPTURE_SESSION_FIELD, CaptureSessionDTO.class);
            return convertToCaptureSession(captureSessionDTO, booking);
        }
        return createCaptureSession(cleansedData, baseKey, booking);
    }

    private Booking createBooking(CleansedData cleansedItem, String redisKey, Case acase) {
        Booking booking = entityCreationService.createBooking(cleansedItem,acase);
        BookingDTO bookingDTO = new BookingDTO(booking);
        redisService.saveHashValue(redisKey, REDIS_BOOKING_FIELD, bookingDTO);
        return booking;
    }

    private CaptureSession createCaptureSession(CleansedData cleansedItem, String redisKey,  Booking booking) {       
        CaptureSession captureSession = entityCreationService.createCaptureSession(cleansedItem, booking);
        CaptureSessionDTO captureSessionDTO = new CaptureSessionDTO(captureSession);
        redisService.saveHashValue(redisKey, REDIS_CAPTURE_SESSION_FIELD, captureSessionDTO);
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

    private Recording determineParentRecording(CleansedData cleansedItem, Recording currentRecording) {
        if (RecordingUtils.isOriginalVersion(cleansedItem)) {
            Logger.getAnonymousLogger().info("This is an original and it's the parent recording" );
            currentRecording.setParentRecording(currentRecording);
            currentRecording.setVersion(1);
            origRecordingsMap.put(cleansedItem.getCaseReference(), currentRecording);
        } else {
            Recording parentRecording = origRecordingsMap.get(cleansedItem.getCaseReference());
            Logger.getAnonymousLogger().info("This is NOT an original " + parentRecording );
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

    public void writeShareBookingUsersCsv(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        
        List<String> headers = List.of(
           "user_id","First Name", "Last Name","Email"
        );

        List<List<String>> rows = new ArrayList<>();
        for (User user : users) {
            List<String> row = List.of(
                user.getId().toString(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail()
            );
        rows.add(row);
        }

        String fileName = "Invited_Users";
        String outputDir = "ZZZMigration Reports";
        csvWriterService.writeToCsv(headers, rows, fileName, outputDir, true);
    }

}

