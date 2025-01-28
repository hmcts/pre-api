package uk.gov.hmcts.reform.preapi.batch.writer;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.entities.batch.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.entities.batch.PassItem;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.repositories.ShareBookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationTrackerService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Spring batch component responsible for writing migrated data to the database.
 */
@Component
@Transactional(propagation = Propagation.REQUIRED)
public class Writer implements ItemWriter<MigratedItemGroup> {

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CaptureSessionRepository captureSessionRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShareBookingRepository shareBookingRepository;

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

            // Save the case entity if it exists
            Case acase = migratedItem.getCase();
            if (acase != null) {
                caseRepository.saveAndFlush(acase);
            }

            // Save the participants if they exist
            Set<Participant> participants = migratedItem.getParticipants();
            if (participants != null) {
                try {
                    participantRepository.saveAllAndFlush(participants);
                } catch (Exception e) {
                    Logger.getAnonymousLogger().info("Writer: Issue with participants: " + e.getMessage());
                }
            }

            // Save the booking entity if it exists
            Booking booking = migratedItem.getBooking();
            if (booking != null) {
                bookingRepository.saveAndFlush(booking);
            }

            // Save the capture session entity if it exists
            CaptureSession captureSession = migratedItem.getCaptureSession();
            if (captureSession != null) {
                captureSessionRepository.saveAndFlush(captureSession);
            }

            // Save the recording entity if it exists
            Recording recording = migratedItem.getRecording();
            if (recording != null) {
                try {
                    recordingRepository.saveAndFlush(recording);
                } catch (Exception e) {
                    Logger.getAnonymousLogger().info("Writer: Issue with recording: " + e.getMessage());
                }
            }
            // Save the users if they exist
            List<User> users = migratedItem.getUsers();
            if (users != null && !users.isEmpty()) {
                for (User user : users) {
                    userRepository.saveAndFlush(user);
                }
            }
            
            // Save the share bookings if they exist
            List<ShareBooking> shareBookings = migratedItem.getShareBookings();
            if (shareBookings != null && !shareBookings.isEmpty()) {
                for (ShareBooking shareBooking : shareBookings) {
                    shareBookingRepository.saveAndFlush(shareBooking);
                }
            }

            // Track the successful migration using the MigrationTrackerService
            PassItem passItem = migratedItem.getPassItem();
            if (passItem != null) {
                migrationTrackerService.addMigratedItem(passItem);
            }    
        }
    }
}
