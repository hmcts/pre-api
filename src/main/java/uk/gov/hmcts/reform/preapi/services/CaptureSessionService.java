package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.reports.CaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;

import java.util.List;

@Service
public class CaptureSessionService {

    private final CaptureSessionRepository captureSessionRepository;

    public CaptureSessionService(CaptureSessionRepository captureSessionRepository) {
        this.captureSessionRepository = captureSessionRepository;
    }

    @Transactional
    public List<CaptureSessionReportDTO> reportCaptureSessions() {
//        return captureSessionRepository.
        return null;
    }
}
