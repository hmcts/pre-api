package uk.gov.hmcts.reform.preapi.batch.entities;

import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;

import java.util.List;
import java.util.Set;


public class MigratedItemGroup {
    private CreateCaseDTO acase;
    private CreateBookingDTO booking;
    private CreateCaptureSessionDTO captureSession;
    private CreateRecordingDTO recording;
    private Set<CreateParticipantDTO> participants;
    private List<CreateShareBookingDTO> shareBookings;
    private List<CreateInviteDTO> invites; 
    private PassItem passItem;

    public MigratedItemGroup(
        CreateCaseDTO aCase, 
        CreateBookingDTO booking, 
        CreateCaptureSessionDTO captureSession, 
        CreateRecordingDTO recording, 
        Set<CreateParticipantDTO> participants,
        List<CreateShareBookingDTO> shareBookings,
        List<CreateInviteDTO> invites,
        PassItem passItem
    ) {
        this.acase = aCase;
        this.booking = booking;
        this.captureSession = captureSession;
        this.recording = recording;
        this.participants = participants;
        this.shareBookings = shareBookings;
        this.invites = invites;
        this.passItem = passItem;
    }

    public CreateCaseDTO getCase() {
        return acase;
    }

    public CreateBookingDTO getBooking() {
        return booking;
    }

    public CreateCaptureSessionDTO getCaptureSession() {
        return captureSession;
    }

    public CreateRecordingDTO getRecording() {
        return recording;
    }

    public Set<CreateParticipantDTO> getParticipants() {
        return participants;
    }

    public PassItem getPassItem() {
        return passItem;
    }

    public List<CreateShareBookingDTO> getShareBookings() {
        return shareBookings;
    }

    public List<CreateInviteDTO> getInvites() {
        return invites;
    }

    @Override
    public String toString() {
        return "MigratedItemGroup{" 
            + "case=" + (acase != null ? acase.toString() : "null") 
            + ", booking=" + (booking != null ? booking.toString() : "null") 
            + ", captureSession=" + (captureSession != null ? captureSession.toString() : "null") 
            + ", recording=" + (recording != null ? recording.toString() : "null") 
            + ", participants=" + (participants != null ? participants.toString() : "null") 
            + ", shareBookings=" + (shareBookings != null ? shareBookings.toString() : "null") 
            + ", passItem=" + (passItem != null ? passItem.toString() : "null") 
            + '}';
    }
}
