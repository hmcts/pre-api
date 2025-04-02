package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.processor.MediaTransformationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.PassItem;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
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
import java.util.Optional;
import java.util.Set;


@Service
public class MigrationGroupBuilderService {
    private static final String BOOKING_FIELD = "bookingField";
    private static final String CAPTURE_SESSION_FIELD = "captureSessionField";
    private static final String RECORDING_FIELD = "recordingField";

    private final LoggingService loggingService;
    private final EntityCreationService entityCreationService;
    private final InMemoryCacheService cacheService;
    private final MigrationTrackerService migrationTrackerService;
    private final CaseRepository caseRepository;
    private final MediaTransformationService mediaTransformationService;

    private final Map<String, CreateCaseDTO> caseCache = new HashMap<>();

    @Autowired
    public MigrationGroupBuilderService(
        final LoggingService loggingService,
        final EntityCreationService entityCreationService,
        final InMemoryCacheService cacheService,
        final MigrationTrackerService migrationTrackerService,
        final CaseRepository caseRepository,
        final MediaTransformationService mediaTransformationService
    ) {
        this.loggingService = loggingService;
        this.entityCreationService = entityCreationService;
        this.cacheService = cacheService;
        this.migrationTrackerService = migrationTrackerService;
        this.caseRepository = caseRepository;
        this.mediaTransformationService = mediaTransformationService;
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
        ProcessedRecording cleansedData
    ) {

        CreateCaseDTO aCase = createCaseIfOrig(cleansedData);
        if (aCase == null) {
            return null;
        }

        String participantPair = cleansedData.getWitnessFirstName() + '-' + cleansedData.getDefendantLastName();
        String baseKey = cacheService.generateBaseKey(aCase.getReference(), participantPair);
        CreateBookingDTO booking = processBooking(baseKey, cleansedData, aCase);
        CreateCaptureSessionDTO captureSession = processCaptureSession(baseKey, cleansedData, booking);
        CreateRecordingDTO recording = processRecording(baseKey, cleansedData, captureSession);

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
            aCase, booking, captureSession, recording, participants,
            shareBookings, invites, passItem
        );
        loggingService.logInfo("Migrating group: %s", migrationGroup);
        return migrationGroup;
    }

    private CreateCaseDTO createCaseIfOrig(ProcessedRecording cleansedData) {
        String caseReference = cleansedData.getCaseReference();

        // 1 - return if case reference is invalid
        if (isInvalidCaseReference(caseReference)) {
            return null;
        }

        // update existing case if present
        if (caseCache.containsKey(caseReference)) {
            return updateExistingCase(caseReference, cleansedData);
        }

        // otherwise return a new case
        return createNewCase(caseReference, cleansedData);
    }

    private boolean isInvalidCaseReference(String caseReference) {
        return caseReference == null || caseReference.isBlank();
    }

    private CreateCaseDTO updateExistingCase(String caseReference, ProcessedRecording cleansedData) {
        CreateCaseDTO existingCase = caseCache.get(caseReference);
        if (existingCase == null) {
            return null;
        }

        Set<CreateParticipantDTO> currentParticipants = existingCase.getParticipants() != null
            ? existingCase.getParticipants()
            : new HashSet<>();

        Set<CreateParticipantDTO> newParticipants = entityCreationService.createParticipants(cleansedData);
        Set<CreateParticipantDTO> updatedParticipants = new HashSet<>(currentParticipants);

        boolean changed = addNewParticipants(currentParticipants, newParticipants, updatedParticipants);

        if (changed) {
            existingCase.setParticipants(updatedParticipants);
        }
        return existingCase;
    }

    private boolean addNewParticipants(
        Set<CreateParticipantDTO> currentParticipants,
        Set<CreateParticipantDTO> newParticipants,
        Set<CreateParticipantDTO> updatedParticipants
    ) {
        boolean changed = false;
        for (CreateParticipantDTO newParticipant : newParticipants) {
            if (!participantExists(currentParticipants, newParticipant)) {
                updatedParticipants.add(newParticipant);
                changed = true;
            }
        }
        return changed;
    }

    private boolean participantExists(Set<CreateParticipantDTO> participants, CreateParticipantDTO candidate) {
        for (CreateParticipantDTO existingParticipant : participants) {
            if (isSameParticipant(existingParticipant, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameParticipant(CreateParticipantDTO p1, CreateParticipantDTO p2) {
        return p1.getParticipantType() == p2.getParticipantType()
            && Objects.equals(normalizeName(p1.getFirstName()), normalizeName(p2.getFirstName()))
            && Objects.equals(normalizeName(p1.getLastName()), normalizeName(p2.getLastName()));
    }

    private CreateCaseDTO createNewCase(String caseReference, ProcessedRecording cleansedData) {
        CreateCaseDTO newCase = entityCreationService.createCase(cleansedData);
        if (newCase == null) {
            return null;
        }
        caseCache.put(caseReference, newCase);
        return newCase;
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private CreateBookingDTO processBooking(String baseKey, ProcessedRecording cleansedData, CreateCaseDTO aCase) {
        return cacheService.checkHashKeyExists(baseKey, BOOKING_FIELD)
            ? cacheService.getHashValue(baseKey, BOOKING_FIELD, CreateBookingDTO.class)
            : entityCreationService.createBooking(cleansedData, aCase, baseKey);
    }

    private CreateCaptureSessionDTO processCaptureSession(
        String baseKey,
        ProcessedRecording cleansedData,
        CreateBookingDTO booking
    ) {
        return cacheService.checkHashKeyExists(baseKey, CAPTURE_SESSION_FIELD)
            ? cacheService.getHashValue(baseKey, CAPTURE_SESSION_FIELD, CreateCaptureSessionDTO.class)
            : entityCreationService.createCaptureSession(cleansedData, booking, baseKey);
    }

    private CreateRecordingDTO processRecording(
        String baseKey,
        ProcessedRecording cleansedItem,
        CreateCaptureSessionDTO captureSession
    ) {
        return cacheService.checkHashKeyExists(baseKey, RECORDING_FIELD)
            ? cacheService.getHashValue(baseKey, RECORDING_FIELD, CreateRecordingDTO.class)
            : entityCreationService.createRecording(baseKey, cleansedItem, captureSession);
    }
}
