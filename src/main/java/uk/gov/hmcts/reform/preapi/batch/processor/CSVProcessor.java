package uk.gov.hmcts.reform.preapi.batch.processor;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
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
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.services.batch.DataExtractionService;
import uk.gov.hmcts.reform.preapi.services.batch.DataTransformationService;
import uk.gov.hmcts.reform.preapi.services.batch.DataValidationService;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationTrackerService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;

@Component
public class CSVProcessor implements ItemProcessor<Object, MigratedItemGroup> {
    private final RedisTemplate<String, Object> redisTemplate; 
    private final DataExtractionService extractionService;
    private final DataTransformationService transformationService;
    private final DataValidationService validationService;
    private final MigrationTrackerService migrationTrackerService;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final Map<String, Case> caseCache = new HashMap<>();
    private final Map<String, String> sitesDataMap = new HashMap<>();
    private final Map<String, List<String[]>> channelUserDataMap = new HashMap<>();
    private final Map<String, Recording> origRecordingsMap = new HashMap<>();

    @Autowired
    public CSVProcessor(
            RedisTemplate<String, Object> redisTemplate,
            DataExtractionService extractionService,
            DataTransformationService transformationService,
            DataValidationService validationService,
            CaseRepository caseRepository,
            CourtRepository courtRepository,
            UserRepository userRepository,
            MigrationTrackerService migrationTrackerService) {
        this.redisTemplate = redisTemplate;
        this.extractionService = extractionService;
        this.transformationService = transformationService;
        this.validationService = validationService;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.migrationTrackerService = migrationTrackerService;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void init() {
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
            Logger.getAnonymousLogger().severe("Unsuported item type: " + item.getClass().getName());
        }
        return null;
    }

    private Object processSitesData(CSVSitesData sitesItem) {
        sitesDataMap.put(sitesItem.getSiteReference(), sitesItem.getSiteName());
        return sitesItem;
    }

    private Object processChannelUserData(CSVChannelData channelDataItem) {
        channelUserDataMap
            .computeIfAbsent(channelDataItem.getChannelName(), k -> new ArrayList<>())
            .add(new String[]{channelDataItem.getChannelUser(), channelDataItem.getChannelUserEmail()});
        return channelDataItem;
    }

    private MigratedItemGroup processArchiveListData(CSVArchiveListData archiveItem) {
        try {
            Map<String, Object> transformationResult = transformationService.transformArchiveListData(
                archiveItem, sitesDataMap, channelUserDataMap);

            String errorMessage = (String) transformationResult.get("errorMessage");

            if (errorMessage != null) {
                migrationTrackerService.addFailedItem(new FailedItem(archiveItem, errorMessage));
                return null;  
            }   

            String pattern = "";
            try {
                Map.Entry<String, Matcher> patternMatch = getPatternMatch(archiveItem);
                pattern = patternMatch != null ? patternMatch.getKey() : null; 
            } catch (Exception e) {
                migrationTrackerService.addFailedItem(new FailedItem(archiveItem, e.getMessage()));
            }

            CleansedData cleansedData = (CleansedData) transformationResult.get("cleansedData");
            if (!validationService.validateCleansedData(cleansedData, archiveItem)) {
                return null;
            }
            return createMigratedItemGroup(pattern, archiveItem, cleansedData);

        } catch (Exception e) {
            migrationTrackerService.addFailedItem(new FailedItem(
                archiveItem, "Error processing item: " + e.getMessage()));
        }

        return null;
    }

    private Map.Entry<String, Matcher> getPatternMatch(CSVArchiveListData archiveItem) {
        return extractionService.matchPattern(archiveItem);
    }

    private MigratedItemGroup createMigratedItemGroup(String pattern, 
        CSVArchiveListData archiveItem, CleansedData cleansedData) {
        MigratedItemGroup migratedItemGroup = null; 

        Case acase = createCaseIfOrig(cleansedData);
        
        if (acase != null) {            
            String participantPair =  cleansedData.getWitnessFirstName() + '-' + cleansedData.getDefendantLastName();
            String baseKey = generateRedisBaseKey(acase, participantPair); 
            Map<String, String> redisKeys = generateRedisKeys(baseKey, acase,  participantPair, cleansedData);
            
            Booking booking = null;
            CaptureSession captureSession = null; 

            if (redisTemplate.opsForHash().hasKey(baseKey, "participantPairField")) {
                if (redisTemplate.opsForHash().hasKey(baseKey, "bookingField")) {
                    Object bookingObj = redisTemplate.opsForHash().get(baseKey, "bookingField");
                    if (bookingObj instanceof BookingDTO) {
                        BookingDTO bookingDTO = (BookingDTO) bookingObj;
                        booking = convertToBooking(bookingDTO); 
                    } else {
                        return null;
                    }
                } else {
                    booking = createBooking(cleansedData, baseKey, acase);
                }

                if (redisTemplate.opsForHash().hasKey(baseKey, "captureSessionField")) {
                    Object captureSessionObj = redisTemplate.opsForHash().get(baseKey, "captureSessionField");
                    if (captureSessionObj instanceof CaptureSessionDTO) {
                        CaptureSessionDTO captureSessionDTO = (CaptureSessionDTO) captureSessionObj;
                        captureSession = convertToCaptureSession(captureSessionDTO, booking);
                    } else {
                        return null;
                    }
                } else {
                    captureSession = createCaptureSession(cleansedData, baseKey, booking);
                }
            }

            Set<Participant> participants = createParticipants(cleansedData, acase);

            Recording recording = createRecording(archiveItem, cleansedData, baseKey, captureSession);
            List<ShareBooking> shareBookings = new ArrayList<>();
            List<User> users = new ArrayList<>();
            try {
                List<Object> shareBookingResult = createShareBookings(cleansedData, booking);
                // shareBookings = (List<ShareBooking>) shareBookingResult.get(0);
                // users = (List<User>) shareBookingResult.get(1);
            } catch (Exception e) {
                Logger.getAnonymousLogger().severe("Error in createMigratedItemGroup: " + e.getMessage());
                return null;
            }
            PassItem passItem = new PassItem(pattern, archiveItem.getArchiveName(), acase, 
                booking, captureSession, recording, participants, shareBookings, users);
            try {
                migratedItemGroup = new MigratedItemGroup(acase, booking, 
                    captureSession, recording, participants, passItem, shareBookings, users);
                
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
        return aCase;
    }

    private Booking createBooking(CleansedData cleansedItem, String redisKey, Case acase) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(acase);
        booking.setScheduledFor(cleansedItem.getRecordingTimestamp());
        booking.setCreatedAt(cleansedItem.getRecordingTimestamp());

        BookingDTO bookingDTO = new BookingDTO(booking);

        redisTemplate.opsForHash().put(redisKey, "bookingField", bookingDTO);

        return booking;
    }

    private CaptureSession createCaptureSession(CleansedData cleansedItem, String redisKey,  Booking booking) {       
        CaptureSession captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
        captureSession.setOrigin(RecordingOrigin.VODAFONE);
        captureSession.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        CaptureSessionDTO captureSessionDTO = new CaptureSessionDTO(captureSession);

        redisTemplate.opsForHash().put(redisKey, "captureSessionField", captureSessionDTO);

        return captureSession;
    }


    private List<Object> createShareBookings(CleansedData cleansedData, Booking booking) {
        List<Object> results = new ArrayList<>();
        try {
            if (cleansedData == null || booking == null) {
                return null;
            }

            List<ShareBooking> shareBookings = new ArrayList<>();
            List<User> sharedWithUsers = new ArrayList<>();

            for (Map<String, String> contactInfo : cleansedData.getShareBookingContacts()) {
                User user = getOrCreateUser(contactInfo);
                ShareBooking shareBooking = new ShareBooking();
                shareBooking.setId(UUID.randomUUID());
                shareBooking.setBooking(booking);
                shareBooking.setSharedBy(user); 
                shareBooking.setSharedWith(user); 

                sharedWithUsers.add(user);
                shareBookings.add(shareBooking);
            }

            results.add(shareBookings);
            results.add(sharedWithUsers);

        } catch (Exception e) {
            Logger.getAnonymousLogger().severe("Error in createShareBookings: " + e.getMessage());
        }
        return results.isEmpty() ? null : results;
    }

    private User getOrCreateUser(Map<String, String> contactInfo) {
        String email = contactInfo.get("email");
        if (email == null) {
            return null;
        }
        String redisUserKey = "user:" + email;
        String userId = (String) redisTemplate.opsForValue().get(redisUserKey);
    

        User user = userRepository.findById(UUID.fromString(userId)).orElse(null);
        if (userId != null) {
            return user;
        } else {            
            User newUser = new User();
            newUser.setId(UUID.randomUUID());
            newUser.setFirstName(contactInfo.getOrDefault("firstName", "Unknown"));
            newUser.setLastName(contactInfo.getOrDefault("lastName", "Unknown"));
            newUser.setEmail(email);

            redisTemplate.opsForValue().set(redisUserKey, newUser.getId().toString());
            return newUser;
        }
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
        witness.setFirstName(cleansedItem.getWitnessFirstName() != null 
            ? cleansedItem.getWitnessFirstName() 
            : "Unknown");
        witness.setLastName(""); 

        Participant defendant = new Participant();
        defendant.setId(UUID.randomUUID());
        defendant.setParticipantType(ParticipantType.DEFENDANT);
        defendant.setCaseId(acase); 
        defendant.setFirstName(""); 
        defendant.setLastName(cleansedItem.getDefendantLastName() != null 
            ? cleansedItem.getDefendantLastName() 
            : "Unknown");

        return Set.of(witness, defendant);
    }


    private void loadCaseCache() {
        List<Case> cases = caseRepository.findAll();
        for (Case acase : cases) {
            caseCache.put(acase.getReference(), acase);
        }
    }


    private Map<String, String> generateRedisKeys(String baseKey, Case acase, String participantPair, 
        CleansedData cleansedData) {
        if (participantPair == null || participantPair.isBlank()) {
            throw new IllegalArgumentException("Participant pair key cannot be null or blank");
        }

        Map<String, String> redisKeys = new HashMap<>();

        redisTemplate.opsForHash().put(baseKey, "participantPairField", participantPair);
        redisKeys.put("participantPairField", participantPair);
        redisKeys.put("bookingField", "booking");
        redisKeys.put("captureSessionField", "captureSession");
        redisKeys.put("highestOrigVersion", "ORIG");
        redisKeys.put("highestCopyVersion", "COPY");

        return redisKeys;
    }


    private String generateRedisBaseKey(Case acase, String participantPair) {
        if (acase == null || acase.getReference() == null || acase.getReference().isBlank()) {
            throw new IllegalArgumentException("Case reference cannot be null or blank");
        }

        return "caseParticipants:" + acase.getReference() + "--" + participantPair;
    }

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


}