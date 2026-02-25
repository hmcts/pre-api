package uk.gov.hmcts.reform.preapi.batch.application.writer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.ParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MigrationItemExecutor {

    private final LoggingService loggingService;
    private final CaseService caseService;
    private final BookingService bookingService;
    private final RecordingService recordingService;
    private final CaptureSessionService captureSessionService;

    @Autowired
    public MigrationItemExecutor(final LoggingService loggingService,
                                 final CaseService caseService,
                                 final BookingService bookingService,
                                 final RecordingService recordingService,
                                 final CaptureSessionService captureSessionService) {
        this.loggingService = loggingService;
        this.caseService = caseService;
        this.bookingService = bookingService;
        this.recordingService = recordingService;
        this.captureSessionService = captureSessionService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean processOneItem(MigratedItemGroup item) {
        CaseDTO persistedCase = processCaseDataOrThrow(item.getCase());
        if (persistedCase == null) {
            throw new IllegalStateException("Case persist failed");
        }

        CreateBookingDTO booking = item.getBooking();
        if (booking != null) {
            booking.setCaseId(persistedCase.getId());
            if (persistedCase.getParticipants() != null && !persistedCase.getParticipants().isEmpty()) {
                remapBookingParticipantsToPersisted(booking, persistedCase);
            }
        }

        processBookingDataOrThrow(item.getBooking());
        processCaptureSessionDataOrThrow(item.getCaptureSession());
        processRecordingDataOrThrow(item.getRecording());

        return true;
    }

    private CaseDTO processCaseDataOrThrow(CreateCaseDTO caseData) {
        if (caseData == null) {
            return null;
        }

        CaseDTO persisted = fetchByRefAndCourt(caseData.getReference(), caseData.getCourtId());

        if (persisted == null) {
            try {
                caseService.upsert(caseData);
            } catch (Exception e) {
                loggingService.logError(
                    "Create case failed (safe path). ref=%s court=%s | %s",
                    caseData.getReference(), caseData.getCourtId(), e.getMessage()
                );
                throw e; 
            }
            return fetchByRefAndCourt(caseData.getReference(), caseData.getCourtId());
        }

        Map<String, CreateParticipantDTO> union = new HashMap<>();
        if (persisted.getParticipants() != null) {
            for (ParticipantDTO p : persisted.getParticipants()) {
                union.put(
                    p.getParticipantType() + "|" + normalize(p.getFirstName()) + "|" + normalize(p.getLastName()),
                    toCreate(p)
                );
            }
        }
        if (caseData.getParticipants() != null) {
            for (var p : caseData.getParticipants()) {
                union.putIfAbsent(participantKey(p), p);
            }
        }

        var patch = new CreateCaseDTO();
        patch.setId(persisted.getId());
        patch.setCourtId(persisted.getCourt().getId());
        patch.setReference(persisted.getReference());
        patch.setOrigin(persisted.getOrigin());
        patch.setTest(persisted.isTest());
        patch.setState(persisted.getState() == null ? CaseState.OPEN : persisted.getState());
        patch.setClosedAt(persisted.getClosedAt());
        patch.setParticipants(new HashSet<>(union.values()));

        try {
            caseService.upsert(patch);
        } catch (Exception e) {
            loggingService.logError(
                "Merge participants failed (safe path). caseId=%s | %s",
                persisted.getId(), e.getMessage()
            );
            throw e; 
        }

        CaseDTO after = fetchByRefAndCourt(caseData.getReference(), caseData.getCourtId());
        return after != null ? after : persisted;
    }

 
    private void processBookingDataOrThrow(CreateBookingDTO bookingData) {
        if (bookingData == null) {
            throw new IllegalStateException("Recording creation failed - no booking data found");
        }
        bookingService.upsert(bookingData);
    }

    private void processCaptureSessionDataOrThrow(CreateCaptureSessionDTO captureSessionData) {
        if (captureSessionData == null) {
            throw new IllegalStateException("Recording creation failed - no capture session data found");
        }
        captureSessionService.upsert(captureSessionData); 
    }

    private void processRecordingDataOrThrow(CreateRecordingDTO recordingData) {
        if (recordingData == null) {
            throw new IllegalStateException("Recording creation failed - no recording data found");
        }
        recordingService.upsert(recordingData);
    }

    // ------------------------
    // Helpers
    // ------------------------
    private void remapBookingParticipantsToPersisted(CreateBookingDTO booking, CaseDTO persistedCase) {
        if (booking == null || booking.getParticipants() == null) {
            throw new IllegalStateException("Recording creation failed - no recording data found");
        }

        Map<String, ParticipantDTO> persistedMap = new HashMap<>();
        if (persistedCase.getParticipants() != null) {
            for (ParticipantDTO p : persistedCase.getParticipants()) {
                String key = p.getParticipantType() + "|" + normalize(p.getFirstName()) 
                    + "|" + normalize(p.getLastName());
                persistedMap.put(key, p);
            }
        }

        Set<CreateParticipantDTO> remapped = booking.getParticipants().stream()
            .map(p -> {
                String key = participantKey(p);
                ParticipantDTO match = persistedMap.get(key);
                if (match != null) {
                    var cp = new CreateParticipantDTO();
                    cp.setId(match.getId());
                    cp.setParticipantType(match.getParticipantType());
                    cp.setFirstName(match.getFirstName());
                    cp.setLastName(match.getLastName());
                    return cp;
                } else {
                    loggingService.logInfo("Booking participant not in persisted case, will create: %s", key);
                    return p; 
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        booking.setParticipants(remapped);
    }

    private CaseDTO fetchByRefAndCourt(String reference, java.util.UUID courtId) {
        var page = caseService.searchBy(reference, courtId, false, PageRequest.of(0, 1));
        return page.hasContent() ? page.getContent().get(0) : null;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static String participantKey(CreateParticipantDTO p) {
        return p.getParticipantType() + "|" + normalize(p.getFirstName()) + "|" + normalize(p.getLastName());
    }

    private static CreateParticipantDTO toCreate(ParticipantDTO p) {
        var dto = new CreateParticipantDTO();
        dto.setId(p.getId());
        dto.setParticipantType(p.getParticipantType());
        dto.setFirstName(p.getFirstName());
        dto.setLastName(p.getLastName());
        return dto;
    }
}