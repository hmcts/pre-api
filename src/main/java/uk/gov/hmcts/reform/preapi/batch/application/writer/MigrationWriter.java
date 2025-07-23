package uk.gov.hmcts.reform.preapi.batch.application.writer;

import org.jetbrains.annotations.NotNull;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = Exception.class)
public class MigrationWriter implements ItemWriter<MigratedItemGroup> {
    private final LoggingService loggingService;
    private final CaseService caseService;
    private final BookingService bookingService;
    private final RecordingService recordingService;
    private final CaptureSessionService captureSessionService;
    private final MigrationTrackerService migrationTrackerService;

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    @Autowired
    public MigrationWriter(
        LoggingService loggingService,
        CaseService caseService,
        BookingService bookingService,
        RecordingService recordingService,
        CaptureSessionService captureSessionService,
        MigrationTrackerService migrationTrackerService
    ) {
        this.loggingService = loggingService;
        this.caseService = caseService;
        this.bookingService = bookingService;
        this.recordingService = recordingService;
        this.captureSessionService = captureSessionService;
        this.migrationTrackerService = migrationTrackerService;
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
                    migrationTrackerService.addMigratedItem(item.getPassItem());
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
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

    private boolean processItem(MigratedItemGroup item) {
        boolean caseOk = processCaseData(item.getCase());
        boolean bookingOk = processBookingData(item.getBooking());
        boolean captureOk = processCaptureSessionData(item.getCaptureSession());
        boolean recordingOk = processRecordingData(item.getRecording());

        return caseOk && bookingOk && captureOk && recordingOk;
    }

    private boolean processCaseData(CreateCaseDTO caseData) {
        if (caseData != null) {
            try {
                caseService.upsert(caseData);
                return true;
            } catch (Exception e) {
                loggingService.logError("Failed to upsert case. Case id: %s | %s", caseData.getId(), e);
                return false;
            }
        }
        return true;
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
}
