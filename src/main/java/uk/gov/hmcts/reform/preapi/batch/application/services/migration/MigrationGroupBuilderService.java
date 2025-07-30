package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.PassItem;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


@Service
public class MigrationGroupBuilderService {
    private final LoggingService loggingService;
    private final EntityCreationService entityCreationService;
    private final InMemoryCacheService cacheService;
    private final CaseRepository caseRepository;
    private final MigrationRecordService migrationRecordService;

    protected static final String BOOKING_FIELD = "booking";
    protected static final String CAPTURE_SESSION_FIELD = "captureSession";
    protected static final String RECORDING_FIELD = "recordingField";

    protected final Map<String, CreateCaseDTO> caseCache = new HashMap<>();

    @Autowired
    public MigrationGroupBuilderService(final LoggingService loggingService,
                                        final EntityCreationService entityCreationService,
                                        final InMemoryCacheService cacheService,
                                        final MigrationRecordService migrationRecordService,
                                        final CaseRepository caseRepository) {
        this.loggingService = loggingService;
        this.entityCreationService = entityCreationService;
        this.cacheService = cacheService;
        this.migrationRecordService = migrationRecordService;
        this.caseRepository = caseRepository;
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
    public MigratedItemGroup createMigratedItemGroup(ExtractedMetadata item, ProcessedRecording cleansedData) {
        CreateCaseDTO aCase = createCaseIfOrig(cleansedData);
        String version = cleansedData.getExtractedRecordingVersion();
        if (aCase == null && (version == null || !version.toUpperCase().contains("COPY"))) {
            return null;
        }

        if (aCase == null) {
            aCase = caseCache.get(cleansedData.getCaseReference());
            if (aCase == null) {
                loggingService.logError("COPY file with missing case in cache: %s", cleansedData.getFileName());
                return null;
            }
        }

        String baseKey = cacheService.generateEntityCacheKey(
            "booking",
            aCase.getReference(),
            cleansedData.getDefendantLastName(),
            cleansedData.getWitnessFirstName(),
            cleansedData.getOrigVersionNumberStr()
        );
        CreateBookingDTO booking = processBooking(baseKey, cleansedData, aCase);
        CreateCaptureSessionDTO captureSession = processCaptureSession(baseKey, cleansedData, booking);
        CreateRecordingDTO recording = processRecording(baseKey, cleansedData, captureSession);

        Set<CreateParticipantDTO> participants = entityCreationService.createParticipants(cleansedData);
        PassItem passItem = new PassItem(item, cleansedData);
        MigratedItemGroup migrationGroup = new MigratedItemGroup(
            aCase,
            booking,
            captureSession,
            recording,
            participants,
            passItem
        );
        loggingService.logDebug("Migrating group: %s", migrationGroup);

        migrationRecordService.updateToSuccess(item.getArchiveId());
        return migrationGroup;
    }

    protected CreateCaseDTO createCaseIfOrig(ProcessedRecording cleansedData) {
        String caseReference = cleansedData.getCaseReference();
        if (isInvalidCaseReference(caseReference)) {
            return null;
        }

        if (caseCache.containsKey(caseReference)) {
            return updateExistingCase(caseReference, cleansedData);
        }

        return createNewCase(caseReference, cleansedData);
    }

    protected boolean isInvalidCaseReference(String caseReference) {
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

    protected boolean addNewParticipants(Set<CreateParticipantDTO> currentParticipants,
                                         Set<CreateParticipantDTO> newParticipants,
                                         Set<CreateParticipantDTO> updatedParticipants) {
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
        return participants.stream().anyMatch(existingParticipant -> isSameParticipant(existingParticipant, candidate));
    }

    private boolean isSameParticipant(CreateParticipantDTO p1, CreateParticipantDTO p2) {
        return p1.getParticipantType() == p2.getParticipantType()
            && Objects.equals(normalizeName(p1.getFirstName()), normalizeName(p2.getFirstName()))
            && Objects.equals(normalizeName(p1.getLastName()), normalizeName(p2.getLastName()));
    }

    protected CreateCaseDTO createNewCase(String caseReference, ProcessedRecording cleansedData) {
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

    protected CreateBookingDTO processBooking(String baseKey, ProcessedRecording cleansedData, CreateCaseDTO aCase) {
        return cacheService.checkHashKeyExists(baseKey, BOOKING_FIELD)
            ? cacheService.getHashValue(baseKey, BOOKING_FIELD, CreateBookingDTO.class)
            : entityCreationService.createBooking(cleansedData, aCase, baseKey);
    }

    protected CreateCaptureSessionDTO processCaptureSession(String baseKey,
                                                            ProcessedRecording cleansedData,
                                                            CreateBookingDTO booking) {
        return cacheService.checkHashKeyExists(baseKey, CAPTURE_SESSION_FIELD)
            ? cacheService.getHashValue(baseKey, CAPTURE_SESSION_FIELD, CreateCaptureSessionDTO.class)
            : entityCreationService.createCaptureSession(cleansedData, booking);
    }

    protected CreateRecordingDTO processRecording(String baseKey,
                                                  ProcessedRecording cleansedItem,
                                                  CreateCaptureSessionDTO captureSession) {
        return cacheService.checkHashKeyExists(baseKey, RECORDING_FIELD)
            ? cacheService.getHashValue(baseKey, RECORDING_FIELD, CreateRecordingDTO.class)
            : entityCreationService.createRecording(cleansedItem, captureSession);
    }
}
