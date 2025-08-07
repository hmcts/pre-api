package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;


@Service
public class MigrationGroupBuilderService {
    private final LoggingService loggingService;
    private final EntityCreationService entityCreationService;
    private final InMemoryCacheService cacheService;
    private final MigrationRecordService migrationRecordService;

    protected static final String BOOKING_FIELD = "booking";
    protected static final String CAPTURE_SESSION_FIELD = "captureSession";
    protected static final String RECORDING_FIELD = "recordingField";


    @Autowired
    public MigrationGroupBuilderService(final LoggingService loggingService,
                                        final EntityCreationService entityCreationService,
                                        final InMemoryCacheService cacheService,
                                        final MigrationRecordService migrationRecordService) {
        this.loggingService = loggingService;
        this.entityCreationService = entityCreationService;
        this.cacheService = cacheService;
        this.migrationRecordService = migrationRecordService;
    }

    // =========================
    // Entity Creation
    // =========================
    public MigratedItemGroup createMigratedItemGroup(ExtractedMetadata item, ProcessedRecording cleansedData) {
        CreateCaseDTO aCase = createCaseIfOrig(cleansedData);
        
        if (aCase == null) {
            String version = cleansedData.getExtractedRecordingVersion();
            if (version == null || !version.toUpperCase().contains("COPY")) {
                loggingService.logError("Failed to find or create case for file: %s", cleansedData.getFileName());
                return null;
            }
            loggingService.logError("COPY file with missing case in cache: %s", cleansedData.getFileName());
            return null;
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
            loggingService.logDebug("Invalid case reference: '%s'", caseReference);
            return null;
        }

        Optional<CreateCaseDTO> existingCaseOpt = cacheService.getCase(caseReference);
        if (existingCaseOpt.isPresent()) {
            CreateCaseDTO existingCase = existingCaseOpt.get();
            loggingService.logDebug("Existing case ID: %s, Reference: %s", 
                                existingCase.getId(), existingCase.getReference());
            return updateExistingCase(caseReference, cleansedData, existingCase);
        }

        loggingService.logDebug("Case not found in cache, creating new case for reference: '%s'", caseReference);
        
        return createNewCase(caseReference, cleansedData);
    }

    protected boolean isInvalidCaseReference(String caseReference) {
        return caseReference == null || caseReference.isBlank();
    }

    private CreateCaseDTO updateExistingCase(
        String caseReference, ProcessedRecording cleansedData, CreateCaseDTO existingCase) {

        Set<CreateParticipantDTO> currentParticipants = existingCase.getParticipants() != null
            ? existingCase.getParticipants()
            : new HashSet<>();

        Set<CreateParticipantDTO> newParticipants = entityCreationService.createParticipants(cleansedData);
        Set<CreateParticipantDTO> updatedParticipants = new HashSet<>(currentParticipants);

        boolean changed = addNewParticipants(currentParticipants, newParticipants, updatedParticipants);

        if (changed) {
            existingCase.setParticipants(updatedParticipants);
            cacheService.saveCase(caseReference, existingCase);
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
        cacheService.saveCase(caseReference, newCase);
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
