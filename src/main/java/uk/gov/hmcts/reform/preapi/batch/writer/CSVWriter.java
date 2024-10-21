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
import uk.gov.hmcts.reform.preapi.entities.batch.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.entities.batch.PassItem;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationTrackerService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;


@Component
@Transactional(propagation = Propagation.REQUIRED)
public class CSVWriter implements ItemWriter<MigratedItemGroup> {

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
    private MigrationTrackerService migrationTrackerService;


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

    private void saveMigratedItems(List<MigratedItemGroup> migratedItems) {
        if (!migratedItems.isEmpty()) {
            for (MigratedItemGroup migratedItem : migratedItems) {
          
                Case acase = migratedItem.getCase();
                if (acase != null) {
                    caseRepository.saveAndFlush(acase);
                }

                Set<Participant> participants = migratedItem.getParticipants();
                if (participants != null){
                    try{
                        participantRepository.saveAllAndFlush(participants);
                    } catch (Exception e){
                        Logger.getAnonymousLogger().info("Issue with participants: " + e.getMessage());
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
                        Logger.getAnonymousLogger().info("Issue with recording: " + e.getMessage());
                    }
                }

                PassItem passItem = migratedItem.getPassItem();
                if (passItem != null) {
                    migrationTrackerService.addMigratedItem(passItem);
                }    
            }
           
        }
    }
}
