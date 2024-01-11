package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.reports.CaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final CaptureSessionRepository captureSessionRepository;
    private final RecordingRepository recordingRepository;

    @Autowired
    public ReportService(CaptureSessionRepository captureSessionRepository,
                         RecordingRepository recordingRepository) {
        this.captureSessionRepository = captureSessionRepository;
        this.recordingRepository = recordingRepository;
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
}
