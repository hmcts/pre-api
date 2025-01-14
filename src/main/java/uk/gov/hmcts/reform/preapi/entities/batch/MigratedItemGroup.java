package uk.gov.hmcts.reform.preapi.entities.batch;

import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;

import java.util.List;
import java.util.Set;


public class MigratedItemGroup {
    private Case acase;
    private Booking booking;
    private CaptureSession captureSession;
    private Recording recording;
    private Set<Participant> participants;
    private PassItem passItem;
    private List<ShareBooking> shareBookings;
    private List<User> users; 

    public MigratedItemGroup(
        Case aCase, 
        Booking booking, 
        CaptureSession captureSession, 
        Recording recording, 
        Set<Participant> participants,
        PassItem passItem,
        List<ShareBooking> shareBookings,
        List<User> users 
    ) {
        this.acase = aCase;
        this.booking = booking;
        this.captureSession = captureSession;
        this.recording = recording;
        this.participants = participants;
        this.passItem = passItem;
        this.shareBookings = shareBookings;
        this.users = users;
    }

    public Case getCase() {
        return acase;
    }

    public Booking getBooking() {
        return booking;
    }

    public CaptureSession getCaptureSession() {
        return captureSession;
    }

    public Recording getRecording() {
        return recording;
    }

    public Set<Participant> getParticipants() {
        return participants;
    }

    public PassItem getPassItem() {
        return passItem;
    }

    public List<ShareBooking> getShareBookings() {
        return shareBookings;
    }

    public List<User> getUsers() {
        return users;
    }

    @Override
    public String toString() {
        return "MigratedItemGroup{" 
            + "case=" + (acase != null ? acase.toString() : "null") 
            + ", booking=" + (booking != null ? booking.toString() : "null") 
            + ", captureSession=" + (captureSession != null ? captureSession.toString() : "null") 
            + ", recording=" + (recording != null ? recording.toString() : "null") 
            + ", participants=" + (participants != null ? participants.toString() : "null") 
            + ", passItem=" + (passItem != null ? passItem.toString() : "null") 
            + ", shareBookings=" + (shareBookings != null ? shareBookings.toString() : "null") 
            + ", users=" + (users != null ? users.toString() : "null") 
            + '}';
    }
}
