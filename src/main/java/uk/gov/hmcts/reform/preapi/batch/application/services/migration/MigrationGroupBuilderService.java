package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.processor.MediaTransformationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.RedisService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CleansedData;
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
    private static final String REDIS_BOOKING_FIELD = "bookingField";
    private static final String REDIS_CAPTURE_SESSION_FIELD = "captureSessionField";
    private static final String REDIS_RECORDING_FIELD = "recordingField";
    private final CaseRepository caseRepository;

    private final EntityCreationService entityCreationService;
    private final RedisService redisService;
    private final MigrationTrackerService migrationTrackerService;
    private final MediaTransformationService recordingMediaKindTransform;
    private final Map<String, CreateCaseDTO> caseCache = new HashMap<>();


    @Autowired
    public MigrationGroupBuilderService(
        EntityCreationService entityCreationService,
        RedisService redisService,
        MigrationTrackerService migrationTrackerService,
        CaseRepository caseRepository,
        MediaTransformationService recordingMediaKindTransform
    ) {
        this.entityCreationService = entityCreationService;
        this.redisService = redisService;
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
        String pattern,
        CSVArchiveListData archiveItem,
        CleansedData cleansedData
    ) {

        CreateCaseDTO acase = createCaseIfOrig(cleansedData);
        if (acase == null) {
            return null;
        }

        String participantPair = cleansedData.getWitnessFirstName() + '-' + cleansedData.getDefendantLastName();
        String baseKey = redisService.generateBaseKey(acase.getReference(), participantPair);

        CreateBookingDTO booking = processBooking(baseKey, cleansedData, acase);
        CreateCaptureSessionDTO captureSession = processCaptureSession(baseKey, cleansedData, booking);
        CreateRecordingDTO recording = processRecording(baseKey, archiveItem, cleansedData, baseKey, captureSession);

        if (recording != null) {
            recordingMediaKindTransform.processMedia(recording.getFilename(), recording.getId());
        }

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

        PassItem passItem = new PassItem(pattern, archiveItem, cleansedData);

        Set<CreateParticipantDTO> participants = entityCreationService.createParticipants(cleansedData);

        return new MigratedItemGroup(
            acase, booking, captureSession, recording, participants,
            shareBookings, invites, passItem
        );
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
        if (redisService.checkHashKeyExists(baseKey, REDIS_BOOKING_FIELD)) {
            CreateBookingDTO bookingDTO = redisService.getHashValue(
                baseKey,
                REDIS_BOOKING_FIELD,
                CreateBookingDTO.class
            );
            return bookingDTO;
        }
        return entityCreationService.createBooking(cleansedData, acase, baseKey);
    }

    private CreateCaptureSessionDTO processCaptureSession(
        String baseKey,
        CleansedData cleansedData,
        CreateBookingDTO booking
    ) {
        if (redisService.checkHashKeyExists(baseKey, REDIS_CAPTURE_SESSION_FIELD)) {
            CreateCaptureSessionDTO captureSessionDTO = redisService.getHashValue(
                baseKey,
                REDIS_CAPTURE_SESSION_FIELD,
                CreateCaptureSessionDTO.class
            );
            return captureSessionDTO;
        }
        return entityCreationService.createCaptureSession(cleansedData, booking, baseKey);
    }

    private CreateRecordingDTO processRecording(
        String baseKey,
        CSVArchiveListData archiveItem,
        CleansedData cleansedItem,
        String redisKey,
        CreateCaptureSessionDTO captureSession
    ) {
        if (redisService.checkHashKeyExists(baseKey, REDIS_RECORDING_FIELD)) {
            CreateRecordingDTO recordingDTO = redisService.getHashValue(
                baseKey,
                REDIS_RECORDING_FIELD,
                CreateRecordingDTO.class
            );
            return recordingDTO;
        }
        CreateRecordingDTO recording = entityCreationService.createRecording(baseKey, cleansedItem, captureSession);
        return recording;
    }
}
