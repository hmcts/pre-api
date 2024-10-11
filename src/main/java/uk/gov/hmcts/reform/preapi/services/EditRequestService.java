package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EditRequestService {

    private final EditRequestRepository editRequestRepository;

    @Autowired
    public EditRequestService(EditRequestRepository editRequestRepository) {
        this.editRequestRepository = editRequestRepository;
    }

    @Transactional
    public List<EditRequest> getPendingEditRequests() {
        return editRequestRepository.findAllByStatusIsOrderByCreatedAt(EditRequestStatus.PENDING);
    }

    @Transactional
    public EditRequestStatus performEdit(UUID editId) {
        // retrieves locked edit request
        var request = editRequestRepository.findById(editId)
            .orElseThrow(() -> new NotFoundException("Edit Request: " + editId));

        if (request.getStatus() != EditRequestStatus.PENDING) {
            throw new ResourceInWrongStateException(
                EditRequest.class.getSimpleName(),
                request.getId().toString(),
                request.getStatus().toString(),
                EditRequestStatus.PENDING.toString()
            );
        }

        request.setStartedAt(Timestamp.from(Instant.now()));
        request.setStatus(EditRequestStatus.PROCESSING);
        editRequestRepository.save(request);

        // todo ffmpeg happens here
        // Thread.sleep(10000);

        request.setFinishedAt(Timestamp.from(Instant.now()));
        request.setStatus(EditRequestStatus.COMPLETE);
        editRequestRepository.save(request);

        return request.getStatus();
    }
}
