package uk.gov.hmcts.reform.preapi.entities.batch;

import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;

public class PassItem {

    private String regexPattern;
    private String archiveName;
    private UUID caseId;
    private UUID courtId;
    private String caseReference;
    private Boolean isTest;
    private UUID bookingId;
    private Timestamp scheduledFor;
    private UUID captureSessionId;
    private String origin;
    private String ingestAddress;
    private String liveOutputURL;
    private Timestamp startedAt;
    private UUID startedByUserId;
    private Timestamp finishedAt;
    private UUID finishedByUserId;
    private String status;
    private UUID recordingId;
    private UUID parentRecordingId;
    private Integer version;
    private String fileName;
    private Duration duration;
    
    private UUID witnessId;
    private String witnessFirstName;
    private String witnessLastName;

    private UUID defendantId;
    private String defendantFirstName;
    private String defendantLastName;

    public PassItem(
        String regexPattern,
        String archiveName, 
        Case acase, 
        Booking booking, 
        CaptureSession captureSession, 
        Recording recording
    ) {
        this.regexPattern = regexPattern;
        this.archiveName = archiveName;
        
        this.caseId = acase != null ? acase.getId() : null;
        this.courtId = (acase != null && acase.getCourt() != null) 
            ? acase.getCourt().getId() 
            : null;
        this.caseReference = acase != null ? acase.getReference() : null;
        this.isTest = acase != null ? acase.isTest() : null;

        this.bookingId = booking != null ? booking.getId() : null;
        this.scheduledFor = booking != null ? booking.getScheduledFor() : null;

        this.captureSessionId = captureSession != null ? captureSession.getId() : null;
        this.origin = captureSession != null && captureSession.getOrigin() != null 
            ? captureSession.getOrigin().name() 
            : null;
        this.ingestAddress = captureSession != null ? captureSession.getIngestAddress() : null;
        this.liveOutputURL = captureSession != null ? captureSession.getLiveOutputUrl() : null;
        this.startedAt = captureSession != null ? captureSession.getStartedAt() : null;
        this.startedByUserId = (captureSession != null && captureSession.getStartedByUser() != null) 
            ? captureSession.getStartedByUser().getId() 
            : null;
        this.finishedAt = captureSession != null ? captureSession.getFinishedAt() : null;
        this.finishedByUserId = (captureSession != null && captureSession.getFinishedByUser() != null) 
            ? captureSession.getFinishedByUser().getId() 
            : null;
        this.status = captureSession != null && captureSession.getStatus() != null 
            ? captureSession.getStatus().name() 
            : null;
        this.recordingId = recording != null ? recording.getId() : null;
        this.parentRecordingId = (recording != null && recording.getParentRecording() != null) 
            ? recording.getParentRecording().getId() 
            : null;
        this.version = recording != null ? recording.getVersion() : null;
        this.fileName = recording != null ? recording.getFilename() : null;
        this.duration = recording != null ? recording.getDuration() : null;

        Participant witness = acase.getParticipants().stream()
            .filter(p -> p.getParticipantType() == ParticipantType.WITNESS)
            .findFirst().orElse(null);

        if (witness != null) {
            this.witnessId = witness.getId();
            this.witnessFirstName = witness.getFirstName();
            this.witnessLastName = witness.getLastName();
        }

        Participant defendant = acase.getParticipants().stream()
            .filter(p -> p.getParticipantType() == ParticipantType.DEFENDANT)
            .findFirst().orElse(null);

        if (defendant != null) {
            this.defendantId = defendant.getId();
            this.defendantFirstName = defendant.getFirstName();
            this.defendantLastName = defendant.getLastName();
        }
    }

    public String getRegexPattern() {
        return regexPattern;
    }

    public String getArchiveName() {
        return archiveName;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public UUID getCourtId() {
        return courtId;
    }

    public String getCaseReference() {
        return caseReference;
    }

    public Boolean getIsTest() {
        return isTest;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public Timestamp getScheduledFor() {
        return scheduledFor;
    }

    public UUID getCaptureSessionId() {
        return captureSessionId;
    }

    public String getOrigin() {
        return origin;
    }

    public String getIngestAddress() {
        return ingestAddress;
    }

    public String getLiveOutputURL() {
        return liveOutputURL;
    }

    public Timestamp getStartedAt() {
        return startedAt;
    }

    public UUID getStartedByUserId() {
        return startedByUserId;
    }

    public Timestamp getFinishedAt() {
        return finishedAt;
    }

    public UUID getFinishedByUserId() {
        return finishedByUserId;
    }

    public String getStatus() {
        return status;
    }

    public UUID getRecordingId() {
        return recordingId;
    }

    public UUID getParentRecordingId() {
        return parentRecordingId;
    }

    public Integer getVersion() {
        return version;
    }

    public String getFileName() {
        return fileName;
    }

    public Duration getDuration() {
        return duration;
    }

    public UUID getWitnessId() {
        return witnessId;
    }

    public String getWitnessFirstName() {
        return witnessFirstName;
    }

    public String getWitnessLastName() {
        return witnessLastName;
    }

    public UUID getDefendantId() {
        return defendantId;
    }

    public String getDefendantFirstName() {
        return defendantFirstName;
    }

    public String getDefendantLastName() {
        return defendantLastName;
    }
}
