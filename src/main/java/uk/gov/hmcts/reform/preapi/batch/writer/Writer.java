package uk.gov.hmcts.reform.preapi.batch.writer;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.batch.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.entities.batch.PassItem;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.InviteService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationTrackerService;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Spring batch component responsible for writing migrated data to the database.
 */
@Component
@Transactional(propagation = Propagation.REQUIRED)
public class Writer implements ItemWriter<MigratedItemGroup> {

    @Autowired
    private CaseService caseService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RecordingService recordingService;

    @Autowired
    private CaptureSessionService captureSessionService;

    @Autowired
    private InviteService inviteService;

    @Autowired
    private MigrationTrackerService migrationTrackerService;

    /**
     * Writes a chunk of MigratedItemGroup items to the database.
     * This method processes each item in the chunk, saves it to the appropriate
     * repository, and tracks successful migrations.
     * @param items The chunk of  MigratedItemGroup items to be written.
     * @throws Exception If an error occurs during the write operation.
     */
    @Override
    public void write(Chunk<? extends MigratedItemGroup> items) throws Exception {
        List<MigratedItemGroup> migratedItems = new ArrayList<>();
        Logger.getAnonymousLogger().info("Processing chunk with " + items.size() + " migrated item(s).");
        
        for (MigratedItemGroup entity : items) {
            if (entity != null) {
                migratedItems.add(entity);
            } 
        }
        saveMigratedItems(migratedItems);
    }

    /**
     * This method processes each item, saves its associated entities (e.g., cases,
     * bookings, participants, etc.) to their respective repositories.
     * @param migratedItems The list of MigratedItemGroup items to be saved.
     */
    private void saveMigratedItems(List<MigratedItemGroup> migratedItems) {
        if (migratedItems.isEmpty()) {
            return;
        }
        for (MigratedItemGroup migratedItem : migratedItems) {

            CreateCaseDTO acase = migratedItem.getCase();
            if (acase != null) {
                try {
                    UpsertResult result = caseService.upsert(acase);
                    Logger.getAnonymousLogger().info(String.format("Case upsert succeeded. Case id: %s, Result: %s",
                            acase.getId(), result));
                } catch (Exception e) {
                    Logger.getAnonymousLogger().info("Failed to upsert case. Case id: %s. Exception: %s"
                        + acase.getId() + e);
                }
            }

            CreateBookingDTO booking = migratedItem.getBooking();
            try {
                UpsertResult result = bookingService.upsert(booking);
               
                Logger.getAnonymousLogger().info(String.format("Booking upsert succeeded. Booking id: %s, Result: %s",
                            booking.getId(), result));
            } catch (Exception e) {
                Logger.getAnonymousLogger().info("Failed to upsert booking. Booking id: %s. Exception: %s"
                    + booking.getId() + e);
            }

            CreateCaptureSessionDTO captureSession = migratedItem.getCaptureSession();
            try {
                UpsertResult result = captureSessionService.upsert(captureSession);
                Logger.getAnonymousLogger().info("Capture Session upsert result: " + result);
            } catch (Exception e) {
                Logger.getAnonymousLogger().info("Failed to upsert capture: " + e.getMessage());
            }

            CreateRecordingDTO recording = migratedItem.getRecording();
            try {
                UpsertResult result = recordingService.upsert(recording);
                Logger.getAnonymousLogger().info("Recroding upsert result: " + result);
            } catch (Exception e) {
                Logger.getAnonymousLogger().info("Failed to upsert recording: " + e.getMessage());
            }

            
            List<CreateInviteDTO> invites = migratedItem.getInvites();
            if (invites != null && !invites.isEmpty()) {
                for (CreateInviteDTO invite : invites) {
                    try {
                        inviteService.upsert(invite);
                    } catch (Exception e) {
                        Logger.getAnonymousLogger().info("Failed to upsert invite: " + e.getMessage());
                    }
                }
            }

            // List<CreateShareBookingDTO> shareBookings = migratedItem.getShareBookings();
            // if (shareBookings != null && !shareBookings.isEmpty()) {
            //     Logger.getAnonymousLogger().info("Share bookings in writer: "+shareBookings);
            //     for (CreateShareBookingDTO shareBooking : shareBookings) {
            //         try{
            //             shareBookingService.shareBookingById(shareBooking);
            //         } catch (Exception e){
            //             Logger.getAnonymousLogger().info("Failed to upsert shareBooking: "+ e.getMessage() );
            //         }
            //     }
            // } else {
            //     Logger.getAnonymousLogger().info("Share bookings in writer is emptuy");
            // }
            
            
            try {
                PassItem passItem = migratedItem.getPassItem();
                if (passItem != null) {
                    migrationTrackerService.addMigratedItem(passItem);
                }   
            } catch (Exception e) {
                Logger.getAnonymousLogger().info("Failed to create migrated item: %s. Exception: %s" + e);
            }
             
        }
    }
}
