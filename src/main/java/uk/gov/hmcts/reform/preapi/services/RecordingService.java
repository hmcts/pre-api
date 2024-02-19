package uk.gov.hmcts.reform.preapi.services;


import org.springframework.beans.factory.annotation.Autowired;
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
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class RecordingService {

    private final RecordingRepository recordingRepository;
    private final CaptureSessionRepository captureSessionRepository;

    @Autowired
    public RecordingService(RecordingRepository recordingRepository,
                            CaptureSessionRepository captureSessionRepository) {
        this.recordingRepository = recordingRepository;
        this.captureSessionRepository = captureSessionRepository;
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
        params.setScheduledForFrom(params.getScheduledFor() != null
                                       ? Timestamp.from(params.getScheduledFor().toInstant())
                                       : null
        );

        params.setScheduledForUntil(params.getScheduledForFrom() != null
                                        ? Timestamp.from(params
                                                             .getScheduledForFrom()
                                                             .toInstant()
                                                             .plus(86399, ChronoUnit.SECONDS))
                                        : null
        );

        var auth = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication());
        params.setAuthorisedBookings(
            auth.isAdmin() || auth.isAppUser() ? null : auth.getSharedBookings()
        );
        params.setAuthorisedCourt(
            auth.isPortalUser() ? null : auth.getCourtId()
        );

        return recordingRepository
            .searchAllBy(params, includeDeleted, pageable)
            .map(RecordingDTO::new);
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

        var recordingEntity = recording.orElse(new Recording());
        recordingEntity.setId(createRecordingDTO.getId());
        recordingEntity.setCaptureSession(captureSession);
        if (createRecordingDTO.getParentRecordingId() != null) {
            var parentRecording = recordingRepository.findById(createRecordingDTO.getParentRecordingId());
            if (parentRecording.isEmpty()) {
                throw new NotFoundException("Recording: " + createRecordingDTO.getParentRecordingId());
            }
            recordingEntity.setParentRecording(parentRecording.get());
        } else {
            recordingEntity.setParentRecording(null);
        }
        recordingEntity.setVersion(createRecordingDTO.getVersion());
        recordingEntity.setUrl(createRecordingDTO.getUrl());
        recordingEntity.setFilename(createRecordingDTO.getFilename());
        recordingEntity.setDuration(createRecordingDTO.getDuration());
        recordingEntity.setEditInstruction(createRecordingDTO.getEditInstructions());

        recordingRepository.save(recordingEntity);

        var isUpdate = recording.isPresent();
        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
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

        recordingRepository.deleteById(recordingId);
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteCascade(CaptureSession captureSession) {
        recordingRepository.deleteAllByCaptureSession(captureSession);
    }
}
