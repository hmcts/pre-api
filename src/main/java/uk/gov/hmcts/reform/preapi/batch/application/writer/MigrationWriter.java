package uk.gov.hmcts.reform.preapi.batch.application.writer;

import org.jetbrains.annotations.NotNull;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class MigrationWriter implements ItemWriter<MigratedItemGroup> {
    private final LoggingService loggingService;
    private final CaseService caseService;
    private final BookingService bookingService;
    private final RecordingService recordingService;
    private final CaptureSessionService captureSessionService;

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    @Autowired
    public MigrationWriter(final LoggingService loggingService,
                           final CaseService caseService,
                           final BookingService bookingService,
                           final RecordingService recordingService,
                           final CaptureSessionService captureSessionService
                           ) {
        this.loggingService = loggingService;
        this.caseService = caseService;
        this.bookingService = bookingService;
        this.recordingService = recordingService;
        this.captureSessionService = captureSessionService;
    }

    @Override
    public void write(@NotNull Chunk<? extends MigratedItemGroup> items) {
        List<MigratedItemGroup> migratedItems = filterValidItems(items);
        loggingService.logInfo("Processing chunk with %d migrated items", items.size());

        if (migratedItems.isEmpty()) {
            loggingService.logWarning("No valid items found in the current chunk.");
            return;
        }

        saveMigratedItems(migratedItems);
        logBatchStatistics();
    }

    private List<MigratedItemGroup> filterValidItems(Chunk<? extends MigratedItemGroup> items) {
        return items.getItems().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
    }

    private void saveMigratedItems(List<MigratedItemGroup> migratedItems) {
        if (migratedItems.isEmpty()) {
            return;
        }

        for (MigratedItemGroup item : migratedItems) {
            try {
                loggingService.logDebug("Processing case: %s", item.getCase().getReference());

                boolean isSuccess = processItem(item);
                if (isSuccess) {
                    successCount.incrementAndGet();
                    loggingService.markSuccess();
                } else {
                    failureCount.incrementAndGet();
                    loggingService.markHandled();
                }
            } catch (Exception e) {
                failureCount.incrementAndGet();
                loggingService.logError(
                    "Failed to process migrated item: %s | %s",
                    item.getCase().getReference(), e.getMessage()
                );
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    protected boolean processItem(MigratedItemGroup item) {
        try {
            CaseDTO persistedCase = processCaseData(item.getCase());
            if (persistedCase == null) {
                return false;
            }

            CreateBookingDTO booking = item.getBooking();
            if (booking != null) {
                booking.setCaseId(persistedCase.getId());
                remapBookingParticipantsToPersisted(booking, persistedCase);
            }
            boolean bookingOk = processBookingData(item.getBooking());
            boolean captureOk = processCaptureSessionData(item.getCaptureSession());
            boolean recordingOk = processRecordingData(item.getRecording());
            return bookingOk && captureOk && recordingOk;
        } catch (Exception e) {
            loggingService.logError("Failed to process migrated item: %s | %s",
                item.getCase() != null ? item.getCase().getReference() : "(no case)", e.getMessage());
            return false;
        }
    }

    private CaseDTO processCaseData(CreateCaseDTO caseData) {
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
                return null;
            }
            return fetchByRefAndCourt(caseData.getReference(), caseData.getCourtId());
        }

        Map<String, CreateParticipantDTO> union = new HashMap<>();
        if (persisted.getParticipants() != null) {
            for (ParticipantDTO p : persisted.getParticipants()) {
                union.put(p.getParticipantType() + "|" + normalize(p.getFirstName())
                    + "|" + normalize(p.getLastName()), toCreate(p));
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
            return persisted;
        }

        CaseDTO after = fetchByRefAndCourt(caseData.getReference(), caseData.getCourtId());
        return after != null ? after : persisted;
    }

    private boolean processBookingData(CreateBookingDTO bookingData) {
        if (bookingData != null) {
            try {
                bookingService.upsert(bookingData);
                return true;
            } catch (Exception e) {
                loggingService.logError("Failed to upsert booking. Booking id: %s | %s", bookingData.getId(), e);
                return false;
            }
        }
        return true;
    }

    private boolean processCaptureSessionData(CreateCaptureSessionDTO captureSessionData) {
        if (captureSessionData != null) {
            try {
                captureSessionService.upsert(captureSessionData);
                return true;
            } catch (Exception e) {
                loggingService.logError(
                    "Failed to upsert capture session. Capture Session id: %s | %s",
                    captureSessionData.getId(), e);
                return false;
            }
        }
        return true;
    }

    private boolean processRecordingData(CreateRecordingDTO recordingData) {
        if (recordingData != null) {
            try {
                recordingService.upsert(recordingData);
                return true;
            } catch (Exception e) {
                loggingService.logError("Failed to upsert recording. Recording id: %s | %s",
                    recordingData.getId(), e);
                return false;
            }
        }
        return true;
    }

    private void logBatchStatistics() {
        loggingService.logInfo(
            "Batch processing - Successful: %d, Failed: %d",
            successCount.get(), failureCount.get()
        );
    }

    private void remapBookingParticipantsToPersisted(CreateBookingDTO booking, CaseDTO persistedCase) {
        if (booking == null || booking.getParticipants() == null) {
            return;
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
                }
                loggingService.logWarning("Booking participant not in persisted case, skipping: %s", key);
                return null;
            })
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());

        booking.setParticipants(remapped);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static String participantKey(CreateParticipantDTO p) {
        return p.getParticipantType() + "|" + normalize(p.getFirstName()) + "|" + normalize(p.getLastName());
    }

    private static CreateParticipantDTO toCreate(ParticipantDTO p) {
        var participantDTO = new CreateParticipantDTO();
        participantDTO.setId(p.getId());
        participantDTO.setParticipantType(p.getParticipantType());
        participantDTO.setFirstName(p.getFirstName());
        participantDTO.setLastName(p.getLastName());
        return participantDTO;
    }

    private CaseDTO fetchByRefAndCourt(String reference, java.util.UUID courtId) {
        var page = caseService.searchBy(reference, courtId, false, PageRequest.of(0, 1));
        return page.hasContent() ? page.getContent().get(0) : null;
    }

}
