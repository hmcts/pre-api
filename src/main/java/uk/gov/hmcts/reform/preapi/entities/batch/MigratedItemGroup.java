package uk.gov.hmcts.reform.preapi.entities.batch;

import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.Participant;

import java.util.Set;


public class MigratedItemGroup {
    private Case acase;
    private Booking booking;
    private CaptureSession captureSession;
    private Recording recording;
    private Set<Participant> participants;
    private PassItem passItem;

    public MigratedItemGroup(
        Case aCase, 
        Booking booking, 
        CaptureSession captureSession, 
        Recording recording, 
        Set<Participant> participants,
        PassItem passItem
        ) {
        this.acase = aCase;
        this.booking = booking;
        this.captureSession = captureSession;
        this.recording = recording;
        this.participants = participants;
        this.passItem = passItem;
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

    public PassItem getPassItem(){
        return passItem;
    }
}
