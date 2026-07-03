package uk.gov.hmcts.reform.preapi.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVParser;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import lombok.Cleanup;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.preapi.batch.application.reader.CSVReader;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.VisibleRecording;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.CaptureSessionNotDeletedException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.utils.InputSanitizerUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static uk.gov.hmcts.reform.preapi.batch.application.reader.CSVReader.createReader;

@Slf4j
@Service
public class RecordingService {

    private static final String ROLE_SUPER_USER = "ROLE_SUPER_USER";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RecordingRepository recordingRepository;
    private final CaptureSessionRepository captureSessionRepository;
    private final CaptureSessionService captureSessionService;
    private final AzureFinalStorageService azureFinalStorageService;

    @Setter
    private boolean enableMigratedData;

    @Setter
    private boolean hideReencodedRecordings;

    @Autowired
    public RecordingService(RecordingRepository recordingRepository,
                            CaptureSessionRepository captureSessionRepository,
                            @Lazy CaptureSessionService captureSessionService,
                            AzureFinalStorageService azureFinalStorageService,
                            @Value("${migration.enableMigratedData:false}") boolean enableMigratedData,
                            @Value("${feature-flags.hide-reencoded-recordings:true}")
                            boolean hideReencodedRecordings) {
        this.recordingRepository = recordingRepository;
        this.captureSessionRepository = captureSessionRepository;
        this.captureSessionService = captureSessionService;
        this.azureFinalStorageService = azureFinalStorageService;
        this.enableMigratedData = enableMigratedData;
        this.hideReencodedRecordings = hideReencodedRecordings;
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasRecordingAccess(authentication, #recordingId)")
    public RecordingDTO findById(UUID recordingId) {
        boolean includeReencodedRecordings = canViewReencodedRecordings();
        return recordingRepository.findByIdAndDeletedAtIsNull(recordingId, includeReencodedRecordings)
            .map(recording -> new RecordingDTO(recording, includeReencodedRecordings))
            .orElseThrow(() -> new NotFoundException("RecordingDTO: " + recordingId));
    }

    @Transactional
    @PreAuthorize(
        """
            (!#includeDeleted or @authorisationService.canViewDeleted(authentication))
            and @authorisationService.canSearchByCaseClosed(authentication, #params.getCaseOpen())
            """
    )
    public Page<RecordingDTO> findAll(
        SearchRecordings params,
        boolean includeDeleted,
        Pageable pageable
    ) {
        params.setStartedAtFrom(params.getStartedAt() != null
                                    ? Timestamp.from(params.getStartedAt().toInstant())
                                    : null
        );

        params.setStartedAtUntil(params.getStartedAtFrom() != null
                                     ? Timestamp.from(params
                                                          .getStartedAtFrom()
                                                          .toInstant()
                                                          .plus(86399, ChronoUnit.SECONDS))
                                     : null
        );

        UserAuthentication auth = (UserAuthentication) SecurityContextHolder.getContext().getAuthentication();
        boolean includeReencodedRecordings = canViewReencodedRecordings(auth);
        params.setAuthorisedBookings(
            auth.isAdmin() || auth.isAppUser() ? null : auth.getSharedBookings()
        );
        params.setAuthorisedCourt(
            auth.isPortalUser() || auth.isAdmin() ? null : auth.getCourtId()
        );

        return recordingRepository
            .searchAllBy(
                params,
                includeDeleted,
                enableMigratedData || auth.hasRole(ROLE_SUPER_USER),
                includeReencodedRecordings,
                pageable
            )
            .map(recording -> new RecordingDTO(recording, includeReencodedRecordings));
    }

    @Transactional
    protected UpsertResult upsert(Optional<Recording> recording,
                                  CaptureSession captureSession,
                                  CreateRecordingDTO createRecordingDTO) {
        Recording recordingEntity = recording.orElse(new Recording());
        recordingEntity.setId(createRecordingDTO.getId());
        recordingEntity.setCaptureSession(captureSession);
        if (createRecordingDTO.getParentRecordingId() != null) {
            Recording parentRecording = recordingRepository
                .findById(createRecordingDTO.getParentRecordingId())
                .orElseThrow(() -> new NotFoundException("Recording: " + createRecordingDTO.getParentRecordingId()));
            recordingEntity.setParentRecording(parentRecording);
        } else {
            recordingEntity.setParentRecording(null);
        }
        recordingEntity.setVersion(createRecordingDTO.getVersion());
        recordingEntity.setFilename(createRecordingDTO.getFilename());
        recordingEntity.setDuration(createRecordingDTO.getDuration());
        recordingEntity.setEditInstruction(createRecordingDTO.getEditInstructions());
        recordingEntity.setReencode(isReencodedRecording(createRecordingDTO.getEditInstructions()));

        recordingRepository.save(recordingEntity);

        return recording.isPresent() ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasUpsertAccess(authentication, #createRecordingDTO)")
    public UpsertResult upsert(CreateRecordingDTO createRecordingDTO) {
        Optional<Recording> recording = recordingRepository.findById(createRecordingDTO.getId());

        if (recording.isPresent() && recording.get().isDeleted()) {
            throw new ResourceInDeletedStateException("RecordingDTO", createRecordingDTO.getId().toString());
        }

        CaptureSession captureSession = captureSessionRepository
            .findByIdAndDeletedAtIsNull(createRecordingDTO.getCaptureSessionId())
            .orElseThrow(() -> new NotFoundException("CaptureSession: " + createRecordingDTO.getCaptureSessionId()));

        if (captureSession.getBooking().getCaseId().getState() != CaseState.OPEN) {
            throw new ResourceInWrongStateException(
                "Recording",
                createRecordingDTO.getId(),
                captureSession.getBooking().getCaseId().getState(),
                "OPEN"
            );
        }

        return upsert(recording, captureSession, createRecordingDTO);
    }

    private boolean isReencodedRecording(String editInstructions) {
        if (editInstructions == null || editInstructions.isBlank()) {
            return false;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(editInstructions);
            return root.path("forceReencode").asBoolean(false)
                || root.path("editInstructions").path("forceReencode").asBoolean(false);
        } catch (Exception e) {
            log.warn("Unable to parse recording edit instructions while checking re-encode visibility marker", e);
            return false;
        }
    }

    private boolean canViewReencodedRecordings() {
        UserAuthentication auth = (UserAuthentication) SecurityContextHolder.getContext().getAuthentication();
        return canViewReencodedRecordings(auth);
    }

    private boolean canViewReencodedRecordings(UserAuthentication auth) {
        return !hideReencodedRecordings || auth != null && auth.hasRole(ROLE_SUPER_USER);
    }

    @Transactional
    public UpsertResult forceUpsert(CreateRecordingDTO createRecordingDTO) {
        // ignores deleted_at and case state
        Optional<Recording> recording = recordingRepository.findById(createRecordingDTO.getId());

        CaptureSession captureSession = captureSessionRepository
            .findByIdAndDeletedAtIsNull(createRecordingDTO.getCaptureSessionId())
            .orElseThrow(() -> new NotFoundException("CaptureSession: " + createRecordingDTO.getCaptureSessionId()));

        return upsert(recording, captureSession, createRecordingDTO);
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasRecordingAccess(authentication, #recordingId)")
    public void deleteById(UUID recordingId) {
        Recording recording = recordingRepository.findByIdAndDeletedAtIsNull(recordingId)
            .orElseThrow(() -> new NotFoundException("Recording: " + recordingId));
        recording.setDeleteOperation(true);
        recording.setDeletedAt(Timestamp.from(Instant.now()));

        recordingRepository.saveAndFlush(recording);
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void checkIfCaptureSessionHasAssociatedRecordings(CaptureSession captureSession) {
        Optional<Recording> recording = recordingRepository.findFirstByCaptureSessionAndDeletedAtIsNull(captureSession);
        if (recording.isPresent()) {
            UUID captureSessionId = captureSession.getId();
            UUID recordingId = recording.get().getId();
            log.error(
                "Cannot delete capture session because an associated recording has not been deleted. "
                    + "captureSessionId={} "
                    + "recordingId={}",
                captureSessionId,
                recordingId
            );
            throw new CaptureSessionNotDeletedException(captureSessionId, recordingId);
        }
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasRecordingAccess(authentication, #id)")
    public void undelete(UUID id) {
        Recording entity = recordingRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Recording: " + id));
        captureSessionService.undelete(entity.getCaptureSession().getId());
        if (!entity.isDeleted()) {
            return;
        }
        entity.setDeletedAt(null);
        recordingRepository.save(entity);
    }

    @Transactional
    public int getNextVersionNumber(UUID parentRecordingId) {
        return recordingRepository.countByParentRecording_Id(parentRecordingId) + 2;
    }

    @Transactional
    public void syncRecordingMetadataWithStorage(UUID recordingId) {
        Recording recording = recordingRepository.findById(recordingId)
            .orElseThrow(() -> new NotFoundException("Recording: " + recordingId));

        String storageMp4Filename = azureFinalStorageService.getMp4FileName(recordingId.toString());
        Duration storageDuration = azureFinalStorageService.getRecordingDuration(recordingId);

        boolean filenameChanged = !Objects.equals(recording.getFilename(), storageMp4Filename);
        boolean durationChanged = !Objects.equals(recording.getDuration(), storageDuration);
        if (filenameChanged) {
            log.warn("Recording {} filename has changed to {}", recordingId, storageMp4Filename);
            recording.setFilename(storageMp4Filename);
        }

        if (durationChanged) {
            log.warn("Recording {} duration has changed to {}", recordingId, storageDuration);
            recording.setDuration(storageDuration);
        }

        if (filenameChanged || durationChanged) {
            recordingRepository.saveAndFlush(recording);
        }

        if (recording.getParentRecording() != null) {
            syncRecordingMetadataWithStorage(recording.getParentRecording().getId());
        }
    }

    @Transactional
    public List<RecordingDTO> findAllDurationNull() {
        return recordingRepository.findAllByDurationIsNullAndDeletedAtIsNull()
            .stream()
            .map(RecordingDTO::new)
            .toList();
    }


    @Transactional
    public List<RecordingDTO> findAllVodafoneRecordings() {
        // return recordingRepository.findAllOriginVodafone().stream()
        return recordingRepository.findAllOriginVodafoneNoDuration().stream()
            .map(RecordingDTO::new)
            .collect(Collectors.toList());
    }

    public List<UUID> getVisibleRecordingsList(boolean visible) {
        return recordingRepository.getVisibleRecordingsList(visible);
    }

    @Transactional
    public List<VisibleRecording> updateVisibleRecordingsList(MultipartFile visibilityData) {
        List<VisibleRecording> visibilityInputList = parseCsv(visibilityData);

        visibilityInputList.forEach(recordingVisibility -> {
            if (recordingVisibility.getVisible() == null || recordingVisibility.getVisible().isBlank()) {
                recordingRepository.resetRecordingVisilibity(recordingVisibility.getRecordingId());
                return;
            }
            if (recordingVisibility.getVisible().equalsIgnoreCase("true")
                || recordingVisibility.getVisible().equalsIgnoreCase("yes")){
                recordingRepository.setRecordingVisilibity(recordingVisibility.getRecordingId(), true);
            }

            if (recordingVisibility.getVisible().equalsIgnoreCase("false")
                || recordingVisibility.getVisible().equalsIgnoreCase("no")){
                recordingRepository.setRecordingVisilibity(recordingVisibility.getRecordingId(), false);
            }
        });

        return visibilityInputList;
    }

    @Transactional
    public List<UUID> resetVisibleRecordingsList(List<UUID> recordingIds) {
        recordingIds.forEach(recordingRepository::resetRecordingVisilibity);
        return recordingIds;
    }

    private List<VisibleRecording> parseCsv(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            file.getInputStream(),
            StandardCharsets.UTF_8
        ))) {
            return new CsvToBeanBuilder<VisibleRecording>(reader)
                .withType(VisibleRecording.class)
                .withIgnoreLeadingWhiteSpace(true)
                .withIgnoreEmptyLine(true)
                .build()
                .parse();
        } catch (Exception e) {
            log.error("Error when reading CSV file: {} ", e.getMessage());
            throw new UnknownServerException("Uploaded CSV file incorrectly formatted", e);
        }
    }

}
