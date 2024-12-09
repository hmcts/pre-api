package uk.gov.hmcts.reform.preapi.entities.batch;

import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PassItem {

    private String regexPattern;
    private String archiveName;
    private String caseReference;
    private Boolean isTest;
    private Timestamp scheduledFor;
    private String origin;
    private String ingestAddress;
    private String liveOutputURL;
    private Timestamp startedAt;
    private UUID startedByUserId;
    private Timestamp finishedAt;
    private UUID finishedByUserId;
    private Integer version;
    private String fileName;
    private String status;
    private Duration duration;
    private List<Map<String, Map<String, String>>> participants; //participant type: firstname, last name
    private List<Map<String, Map<String, String>>> users; //email : first name, last name
    private List<Map<UUID, UUID>> shareBookings;

    private UUID caseId;
    private UUID courtId;
    private UUID bookingId;
    private UUID recordingId;
    private UUID captureSessionId;
    private UUID parentRecordingId;
    private List<UUID> participantIds;
    private List<UUID> shareBookingIds;
    private List<UUID> userIds;

    public PassItem(
        String regexPattern,
        String archiveName, 
        Case acase, 
        Booking booking, 
        CaptureSession captureSession, 
        Recording recording,
        Set<Participant> participants,
        List<ShareBooking> shareBookings,
        List<User> users
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

        this.participants = new ArrayList<>();
        this.users = new ArrayList<>();
        this.shareBookings = new ArrayList<>();
        this.participantIds = null;
        this.shareBookingIds = null;
        this.userIds = null;
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
  
    
    public List<Map<String, Map<String, String>>> getParticipants() {
        return participants;
    }

    public List<Map<String, Map<String, String>>> getUsers() {
        return users;
    }

    public List<Map<UUID, UUID>> getShareBookings() {
        return shareBookings;
    }

    public List<UUID> getParticipantIds() {
        return participantIds;
    }

    public List<UUID> getShareBookingIds() {
        return shareBookingIds;
    }

    public List<UUID> getUserIds() {
        return userIds;
    }

    @Override
    public String toString() {
        return "PassItem{" 
                + "regexPattern='" + regexPattern 
                + ", archiveName='" + archiveName  
                +  ", caseId=" + caseId 
                + ", courtId=" + courtId 
                + ", caseReference='" + caseReference  
                + ", isTest=" + isTest 
                + ", bookingId=" + bookingId 
                + ", scheduledFor=" + scheduledFor 
                + ", captureSessionId=" + captureSessionId 
                + ", origin='" + origin  
                + ", ingestAddress='" + ingestAddress  
                + ", liveOutputURL='" + liveOutputURL  
                + ", startedAt=" + startedAt 
                + ", startedByUserId=" + (startedByUserId != null ? "****" : null) 
                + ", finishedAt=" + finishedAt 
                + ", finishedByUserId=" + (finishedByUserId != null ? "****" : null) 
                + ", status='" + status  
                + ", recordingId=" + recordingId 
                + ", parentRecordingId=" + parentRecordingId 
                + ", version=" + version 
                + ", fileName='" + fileName  
                + ", duration=" + duration 
            + '}';   
    }
}
