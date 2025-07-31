package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.CaptureSessionNotDeletedException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class RecordingService {

    private final RecordingRepository recordingRepository;
    private final CaptureSessionRepository captureSessionRepository;
    private final CaptureSessionService captureSessionService;
    private final AzureFinalStorageService azureFinalStorageService;

    @Autowired
    public RecordingService(RecordingRepository recordingRepository,
                            CaptureSessionRepository captureSessionRepository,
                            @Lazy CaptureSessionService captureSessionService,
                            AzureFinalStorageService azureFinalStorageService) {
        this.recordingRepository = recordingRepository;
        this.captureSessionRepository = captureSessionRepository;
        this.captureSessionService = captureSessionService;
        this.azureFinalStorageService = azureFinalStorageService;
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasRecordingAccess(authentication, #recordingId)")
    public RecordingDTO findById(UUID recordingId) {
        return recordingRepository
            .findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNullAndCaptureSession_Booking_DeletedAtIsNull(
                recordingId
            )
            .map(RecordingDTO::new)
            .orElseThrow(() -> new NotFoundException("RecordingDTO: " + recordingId));
    }

    @Transactional
    @PreAuthorize("!#includeDeleted or @authorisationService.canViewDeleted(authentication)")
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

        var auth = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication());
        params.setAuthorisedBookings(
            auth.isAdmin() || auth.isAppUser() ? null : auth.getSharedBookings()
        );
        params.setAuthorisedCourt(
            auth.isPortalUser() || auth.isAdmin() ? null : auth.getCourtId()
        );

        return recordingRepository
            .searchAllBy(params, includeDeleted, pageable)
            .map(RecordingDTO::new);
    }

    @Transactional
    protected UpsertResult upsert(Optional<Recording> recording,
                                  CaptureSession captureSession,
                                  CreateRecordingDTO createRecordingDTO) {
        Recording recordingEntity = recording.orElse(new Recording());
        recordingEntity.setId(createRecordingDTO.getId());
        recordingEntity.setCaptureSession(captureSession);
        if (createRecordingDTO.getParentRecordingId() != null) {
            Optional<Recording> parentRecording = recordingRepository
                .findById(createRecordingDTO.getParentRecordingId());
            if (parentRecording.isEmpty()) {
                throw new NotFoundException("Recording: " + createRecordingDTO.getParentRecordingId());
            }
            recordingEntity.setParentRecording(parentRecording.get());
        } else {
            recordingEntity.setParentRecording(null);
        }
        recordingEntity.setVersion(createRecordingDTO.getVersion());
        recordingEntity.setFilename(createRecordingDTO.getFilename());
        recordingEntity.setDuration(createRecordingDTO.getDuration());
        recordingEntity.setEditInstruction(createRecordingDTO.getEditInstructions());

        recordingRepository.save(recordingEntity);

        return recording.isPresent() ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasUpsertAccess(authentication, #createRecordingDTO)")
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public UpsertResult upsert(CreateRecordingDTO createRecordingDTO) {
        var recording = recordingRepository.findById(createRecordingDTO.getId());

        if (recording.isPresent() && recording.get().isDeleted()) {
            throw new ResourceInDeletedStateException("RecordingDTO", createRecordingDTO.getId().toString());
        }

        var captureSession = captureSessionRepository
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
        var recording = recordingRepository
            .findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNullAndCaptureSession_Booking_DeletedAtIsNull(
                recordingId
            );

        if (recording.isEmpty() || recording.get().isDeleted()) {
            throw new NotFoundException("Recording: " + recordingId);
        }

        var recordingEntity = recording.get();
        recordingEntity.setDeleteOperation(true);
        recordingEntity.setDeletedAt(Timestamp.from(Instant.now()));

        recordingRepository.saveAndFlush(recordingEntity);
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void checkIfCaptureSessionHasAssociatedRecordings(CaptureSession captureSession) {
        if (recordingRepository.existsByCaptureSessionAndDeletedAtIsNull(captureSession)) {
            throw new CaptureSessionNotDeletedException();
        }
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasRecordingAccess(authentication, #id)")
    public void undelete(UUID id) {
        var entity = recordingRepository.findById(id).orElseThrow(() -> new NotFoundException("Recording: " + id));
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
    }

    @Transactional
    public List<RecordingDTO> findAllDurationNull() {
        return recordingRepository.findAllByDurationIsNullAndDeletedAtIsNull()
            .stream()
            .map(RecordingDTO::new)
            .toList();
    }
}
