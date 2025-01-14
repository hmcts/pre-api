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


    @Override
    public void write(Chunk<? extends MigratedItemGroup> items) throws Exception {
        Logger.getAnonymousLogger().info("WRITER: Starting to process chunk of MigratedItemGroups.");
        List<MigratedItemGroup> migratedItems = new ArrayList<>();
        
        for (MigratedItemGroup entity : items) {
            if (entity != null) {
                Logger.getAnonymousLogger().info("WRITER: Adding MigratedItemGroup to the batch: " + entity);
                migratedItems.add(entity);
            } else {
                Logger.getAnonymousLogger().warning("WRITER: Encountered a null entity in the chunk.");
            }
        }
        
        saveMigratedItems(migratedItems);
    }

    private void saveMigratedItems(List<MigratedItemGroup> migratedItems) {
        if (migratedItems.isEmpty()) {
            Logger.getAnonymousLogger().info("WRITER: No items to migrate in this batch.");
            return;
        }
        for (MigratedItemGroup migratedItem : migratedItems) {
            Logger.getAnonymousLogger().info("WRITER: Processing MigratedItemGroup: " + migratedItem);

            Case acase = migratedItem.getCase();
            if (acase != null) {
                caseRepository.saveAndFlush(acase);
            }

            Set<Participant> participants = migratedItem.getParticipants();
            if (participants != null) {
                try {
                    participantRepository.saveAllAndFlush(participants);
                } catch (Exception e) {
                    Logger.getAnonymousLogger().info("WRITER: Issue with participants: " + e.getMessage());
                }
            }

            Booking booking = migratedItem.getBooking();
            if (booking != null) {
                bookingRepository.saveAndFlush(booking);
            }

            CaptureSession captureSession = migratedItem.getCaptureSession();
            if (captureSession != null) {
                captureSessionRepository.saveAndFlush(captureSession);
            }

            Recording recording = migratedItem.getRecording();
            if (recording != null) {
                try {
                    recordingRepository.saveAndFlush(recording);
                } catch (Exception e) {
                    Logger.getAnonymousLogger().info("WRITER: Issue with recording: " + e.getMessage());
                }
            }
            List<User> users = migratedItem.getUsers();
            List<ShareBooking> shareBookings = migratedItem.getShareBookings();

            if (users != null && !users.isEmpty()) {
                for (User user : users) {
                    userRepository.saveAndFlush(user);
                }
            }

            if (shareBookings != null && !shareBookings.isEmpty()) {
                for (ShareBooking shareBooking : shareBookings) {
                    shareBookingRepository.saveAndFlush(shareBooking);
                }
            }

            PassItem passItem = migratedItem.getPassItem();
            if (passItem != null) {
                migrationTrackerService.addMigratedItem(passItem);
            }    
        }
        
    }

}
