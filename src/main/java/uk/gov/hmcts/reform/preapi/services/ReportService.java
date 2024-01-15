package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.reports.CaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.EditReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingsPerCaseReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.SharedReportDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.repositories.ShareBookingRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final CaptureSessionRepository captureSessionRepository;
    private final RecordingRepository recordingRepository;
    private final CaseRepository caseRepository;
    private final ShareBookingRepository shareBookingRepository;

    @Autowired
    public ReportService(CaptureSessionRepository captureSessionRepository,
                         RecordingRepository recordingRepository,
                         CaseRepository caseRepository,
                         ShareBookingRepository shareBookingRepository) {
        this.captureSessionRepository = captureSessionRepository;
        this.recordingRepository = recordingRepository;
        this.caseRepository = caseRepository;
        this.shareBookingRepository = shareBookingRepository;
    }

    @Transactional
    public List<CaptureSessionReportDTO> reportCaptureSessions() {
        return captureSessionRepository
            .findAll()
            .stream()
            .map(c ->
                recordingRepository
                    .findByCaptureSessionAndDeletedAtIsNullAndVersionOrderByCreatedAt(c, 1)
                    .map(CaptureSessionReportDTO::new)
                    .orElse(new CaptureSessionReportDTO(c))
            ).collect(Collectors.toList());
    }

    @Transactional
    public List<RecordingsPerCaseReportDTO> reportRecordingsPerCase() {
        return caseRepository
            .findAll()
            .stream()
            .map(c -> new RecordingsPerCaseReportDTO(
                c,
                captureSessionRepository.countAllByBooking_CaseId_IdAndStatus(c.getId(), RecordingStatus.AVAILABLE)
            ))
            .sorted((case1, case2) -> Integer.compare(case2.getCount(), case1.getCount()))
            .collect(Collectors.toList());
    }

    @Transactional
    public List<EditReportDTO> reportEdits() {
        return recordingRepository
            .findAllByParentRecordingIsNotNull()
            .stream()
            .map(EditReportDTO::new)
            .sorted(Comparator.comparing(EditReportDTO::getCreatedAt))
            .collect(Collectors.toList());
    }

    @Transactional
    public List<SharedReportDTO> reportShared() {
        return shareBookingRepository
            .findAll()
            .stream()
            .map(SharedReportDTO::new)
            .sorted(Comparator.comparing(SharedReportDTO::getSharedAt))
            .collect(Collectors.toList());
    }
}
