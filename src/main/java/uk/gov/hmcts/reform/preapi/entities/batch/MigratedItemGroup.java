package uk.gov.hmcts.reform.preapi.entities.batch;

import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Recording;

public class MigratedItemGroup {
    private Case acase;
    private Booking booking;
    private CaptureSession captureSession;
    private Recording recording;

    public MigratedItemGroup(Case aCase, Booking booking, CaptureSession captureSession, Recording recording) {
        this.acase = aCase;
        this.booking = booking;
        this.captureSession = captureSession;
        this.recording = recording;
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
}
