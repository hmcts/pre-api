package uk.gov.hmcts.reform.preapi.batch.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.config.batch.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.entities.batch.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.InviteService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.ShareBookingService;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationTrackerService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Spring batch component responsible for writing migrated data to the database.
 */

@Component
@Transactional(propagation = Propagation.REQUIRED)
public class Writer implements ItemWriter<MigratedItemGroup> {

    private static final Logger logger = LoggerFactory.getLogger(BatchConfiguration.class);

    private final CaseService caseService;
    private final BookingService bookingService;
    private final RecordingService recordingService;
    private final CaptureSessionService captureSessionService;
    private final InviteService inviteService;
    private final ShareBookingService shareBookingService;
    private final MigrationTrackerService migrationTrackerService;

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);


    @Autowired
    public Writer(
        CaseService caseService,
        BookingService bookingService,
        RecordingService recordingService,
        CaptureSessionService captureSessionService,
        InviteService inviteService,
        ShareBookingService shareBookingService,
        MigrationTrackerService migrationTrackerService
    ) {
        this.caseService = caseService;
        this.bookingService = bookingService;
        this.recordingService = recordingService;
        this.captureSessionService = captureSessionService;
        this.inviteService = inviteService;
        this.shareBookingService = shareBookingService;
        this.migrationTrackerService = migrationTrackerService;
    }

    /**
     * Writes a chunk of MigratedItemGroup items to the database.
     * This method processes each item in the chunk, saves it to the appropriate
     * repository, and tracks successful migrations.
     * @param items The chunk of  MigratedItemGroup items to be written.
     * @throws Exception If an error occurs during the write operation.
     */
    @Override
    public void write(Chunk<? extends MigratedItemGroup> items) {
        List<MigratedItemGroup> migratedItems = filterValidItems(items);
        logger.info("Processing chunk with {} migrated items", items.size());

        try {
            saveMigratedItems(migratedItems);
            logBatchStatistics();
        } catch (Exception e) {
            logger.error("error ");
        }
    }

    private List<MigratedItemGroup> filterValidItems(Chunk<? extends MigratedItemGroup> items) {
        return items.getItems().stream()
            .filter(item -> item != null)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * This method processes each item, saves its associated entities (e.g., cases,
     * bookings, participants, etc.) to their respective repositories.
     * @param migratedItems The list of MigratedItemGroup items to be saved.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveMigratedItems(List<MigratedItemGroup> migratedItems) {
        if (migratedItems.isEmpty()) {
            return;
        }
        
        for (MigratedItemGroup item : migratedItems) {

            try {
                processItem(item);
                migrationTrackerService.addMigratedItem(item.getPassItem());
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                logger.error("Failed to process migrated item: {}", item.getCase().getReference(), e);
            }
             
        }
    }

    private void processItem(MigratedItemGroup item) {
        processCaseData(item.getCase());
        processBookingData(item.getBooking());
        processCaptureSessionData(item.getCaptureSession());
        processRecordingData(item.getRecording());
        processInvitesData(item.getInvites());
        processShareBookingsData(item.getShareBookings());
    }


    private void processCaseData(CreateCaseDTO caseData){
        if (caseData != null) {
            try {
                caseService.upsert(caseData);
            } catch (Exception e) {
                logger.error("Failed to upsert case. Case id: {}", caseData.getId(), e);
            }
        }
    }

    private void processBookingData(CreateBookingDTO bookingData){
        if (bookingData != null) {
            try {
                bookingService.upsert(bookingData);
            } catch (Exception e) {
                logger.error("Failed to upsert booking. Booking id: {}", bookingData.getId(), e);
            }
        }
    }

    private void processCaptureSessionData(CreateCaptureSessionDTO captureSessionData){
        if (captureSessionData != null) {
            try {
                captureSessionService.upsert(captureSessionData);
            } catch (Exception e) {
                logger.error("Failed to upsert capture session. Capture Session id: {}", captureSessionData.getId(), e);
            }
        }
    }

    private void processRecordingData(CreateRecordingDTO recordingData){
        if (recordingData != null) {
            try {
                recordingService.upsert(recordingData);
            } catch (Exception e) {
                logger.info("Failed to upsert recording. Recording id: {}", recordingData.getId(), e);
            }
        }
    }

    private void processInvitesData(List<CreateInviteDTO> invites) {
        if (invites != null && !invites.isEmpty()) {
            for (CreateInviteDTO invite : invites) {
                try {
                    inviteService.upsert(invite);
                } catch (Exception e) {
                    logger.error("Failed to upsert invite. Invite email: {}", invite.getEmail(), e);
                }
            }
        }
    }

    private void processShareBookingsData(List<CreateShareBookingDTO> shareBookings) {
        if (shareBookings != null && !shareBookings.isEmpty()) {
            for (CreateShareBookingDTO shareBooking : shareBookings) {
                try {
                    shareBookingService.shareBookingById(shareBooking);
                } catch (Exception e) {
                    logger.error("Failed to upsert share booking: {}", shareBooking.getId(), e);
                }
            }
        }
    }

    private void logBatchStatistics() {
        logger.info("Batch processing - Successful: {}, Failed: {}", 
            successCount.get(), failureCount.get());
    }
    
}
