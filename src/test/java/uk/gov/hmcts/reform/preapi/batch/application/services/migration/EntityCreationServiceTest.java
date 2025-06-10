package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { EntityCreationService.class })
public class EntityCreationServiceTest {
    @MockitoBean
    private LoggingService loggingService;

    @MockitoBean
    private InMemoryCacheService cacheService;

    @MockitoBean
    private UserService userService;

    @Autowired
    private EntityCreationService entityCreationService;

    @Test
    @DisplayName("Should throw IllegalArgumentException when processed data")
    public void createCaseDataIsNull() {
        String message = assertThrows(
            IllegalArgumentException.class,
            () -> entityCreationService.createCase(null)
        ).getMessage();
        assertThat(message).isEqualTo("ProcessedRecording cannot be null");
    }

    @Test
    @DisplayName("Should throw error when court is null")
    public void createCaseCourtNull() {
        ProcessedRecording processedRecording = ProcessedRecording.builder().build();
        String message = assertThrows(
            IllegalArgumentException.class,
            () -> entityCreationService.createCase(processedRecording)
        ).getMessage();
        assertThat(message).isEqualTo("Court information is missing");
    }

    @Test
    @DisplayName("Should throw error when court id is null")
    public void createCaseCourtIdNull() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .court(new Court())
            .build();
        String message = assertThrows(
            IllegalArgumentException.class,
            () -> entityCreationService.createCase(processedRecording)
        ).getMessage();
        assertThat(message).isEqualTo("Court information is missing");
    }

    @Test
    @DisplayName("Should create a case")
    public void createCaseSuccess() {
        Court court = new Court();
        court.setId(UUID.randomUUID());
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .court(court)
            .caseReference("caseReference")
            .witnessFirstName("witnessFirstName")
            .defendantLastName("defendantLastName")
            .build();

        CreateCaseDTO result = entityCreationService.createCase(processedRecording);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCourtId()).isEqualTo(court.getId());
        assertThat(result.getReference()).isEqualTo("caseReference");
        assertThat(result.getParticipants()).hasSize(2);
        assertThat(result.getParticipants().stream()
                       .anyMatch(p -> p.getParticipantType().equals(ParticipantType.WITNESS)
                           && p.getFirstName().equals("witnessFirstName")))
            .isTrue();
        assertThat(result.getParticipants().stream()
                       .anyMatch(p -> p.getParticipantType().equals(ParticipantType.DEFENDANT)
                           && p.getLastName().equals("defendantLastName")))
            .isTrue();
        assertThat(result.isTest()).isFalse();
        assertThat(result.getState()).isEqualTo(CaseState.OPEN);
        assertThat(result.getClosedAt()).isNull();
        assertThat(result.getOrigin()).isEqualTo(RecordingOrigin.VODAFONE);
    }

    @Test
    @DisplayName("Should create a booking successfully")
    public void createBookingSuccess() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .recordingTimestamp(Timestamp.from(Instant.now()))
            .extractedRecordingVersionNumberStr("1")
            .build();
        CreateCaseDTO aCase = new CreateCaseDTO();
        aCase.setId(UUID.randomUUID());
        aCase.setParticipants(Set.of());

        String bookingKey = cacheService.generateBookingCacheKey("key", "1");

        CreateBookingDTO result = entityCreationService.createBooking(processedRecording, aCase, "key");
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getScheduledFor()).isEqualTo(processedRecording.getRecordingTimestamp());
        assertThat(result.getParticipants()).isNotNull();

        verify(cacheService, times(1)).saveHashValue(bookingKey, "id", result.getId().toString());
    }

    @Test
    @DisplayName("Should create a capture session successfully")
    public void createCaptureSessionSuccess() {
        BaseUserDTO user = new UserDTO();
        user.setId(UUID.randomUUID());
        AccessDTO accessDTO = new AccessDTO();
        accessDTO.setUser(user);
        when(userService.findByEmail("vodafone@test.com")).thenReturn(accessDTO);

        CreateBookingDTO booking = new CreateBookingDTO();
        booking.setId(UUID.randomUUID());

        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .recordingTimestamp(Timestamp.from(Instant.now()))
            .duration(Duration.ofMinutes(3))
            .extractedRecordingVersion("null")
            .build();

        CreateCaptureSessionDTO result = entityCreationService.createCaptureSession(processedRecording, booking, "key");
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getBookingId()).isEqualTo(booking.getId());
        assertThat(result.getStartedAt()).isEqualTo(processedRecording.getRecordingTimestamp());
        assertThat(result.getStartedByUserId()).isEqualTo(user.getId());
        assertThat(result.getFinishedAt()).isNotNull();
        assertThat(result.getFinishedByUserId()).isEqualTo(user.getId());
        assertThat(result.getStatus()).isEqualTo(RecordingStatus.RECORDING_AVAILABLE);
        assertThat(result.getOrigin()).isEqualTo(RecordingOrigin.VODAFONE);

        String expectedKey = "key:version:null:sessionId";
        verify(userService, times(1)).findByEmail("vodafone@test.com");
        verify(cacheService, times(1)).saveHashValue(expectedKey, "id", result.getId().toString());
    }

    @Test
    @DisplayName("Should create a recording without parent ID when version is 1")
    public void createRecordingVersionOne() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .fileName("test_file.mp4")
            .duration(Duration.ofMinutes(5))
            .recordingVersionNumber(1)
            .extractedRecordingVersion("ORIG")
            .caseReference("CASE123")
            .defendantLastName("Smith")
            .witnessFirstName("John")
            .build();

        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());

        CreateRecordingDTO result = entityCreationService.createRecording("key", processedRecording, captureSession);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCaptureSessionId()).isEqualTo(captureSession.getId());
        assertThat(result.getFilename()).isEqualTo("test_file.mp4");
        assertThat(result.getDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(result.getVersion()).isEqualTo(1);
        assertThat(result.getParentRecordingId()).isNull();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> fieldCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

        verify(cacheService).saveHashValue(
            keyCaptor.capture(),
            fieldCaptor.capture(),
            valueCaptor.capture()
        );

        String expectedKey = cacheService.generateCacheKey(
            "recording",
            processedRecording.getCaseReference(),
            processedRecording.getDefendantLastName(),
            processedRecording.getWitnessFirstName()
        );
        String expectedField = "parentLookup:null"; 

        assertThat(keyCaptor.getValue()).isEqualTo(expectedKey);
        assertThat(fieldCaptor.getValue()).isEqualTo(expectedField);
        assertThat(valueCaptor.getValue()).startsWith(result.getId().toString());
    }

    @Test
    @DisplayName("Should create a recording with parent ID when version is greater than 1 and metadata exists")
    public void createRecordingWithParent() {
        UUID parentId = UUID.randomUUID();
        String metadata = parentId.toString();

        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .fileName("test_file.mp4")
            .duration(Duration.ofMinutes(5))
            .recordingVersionNumber(2)
            .extractedRecordingVersion("COPY")
            .extractedRecordingVersionNumberStr("2")
            .caseReference("key")
            .defendantLastName("Smith")
            .witnessFirstName("John")
            .build();

        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();

        String expectedKey = cacheService.generateCacheKey(
            "recording", "key", "Smith", "John"
        );
        String expectedField = "parentLookup:2";

        when(cacheService.getHashValue(expectedKey, expectedField, String.class)).thenReturn(metadata);

        CreateRecordingDTO result = entityCreationService.createRecording("key", processedRecording, captureSession);

        assertThat(result).isNotNull();
        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(result.getParentRecordingId()).isEqualTo(parentId);
    }

    @Test
    @DisplayName("Should not set parent ID if metadata is missing for version > 1")
    public void createRecordingWithMissingMetadata() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .fileName("test_file.mp4")
            .duration(Duration.ofMinutes(5))
            .recordingVersionNumber(2)
            .build();

        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());

        when(cacheService.getHashValue("key", "recordingMetadata", String.class)).thenReturn(null);

        CreateRecordingDTO result = entityCreationService.createRecording("key", processedRecording, captureSession);

        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(result.getParentRecordingId()).isNull();
    }

    @Test
    @DisplayName("Should create only witness participant")
    public void createParticipantsWithOnlyWitness() {
        ProcessedRecording recording = ProcessedRecording.builder()
            .witnessFirstName("Example")
            .build();

        Set<CreateParticipantDTO> participants = entityCreationService.createParticipants(recording);

        assertThat(participants).hasSize(1);
        assertThat(participants.stream().findFirst().get().getParticipantType()).isEqualTo(ParticipantType.WITNESS);
        assertThat(participants.stream().findFirst().get().getFirstName()).isEqualTo("Example");
    }

    @Test
    @DisplayName("Should create only defendant participant")
    public void createParticipantsWithOnlyDefendant() {
        ProcessedRecording recording = ProcessedRecording.builder()
            .defendantLastName("Example")
            .build();

        Set<CreateParticipantDTO> participants = entityCreationService.createParticipants(recording);

        assertThat(participants).hasSize(1);
        assertThat(participants.stream().findFirst().get().getParticipantType()).isEqualTo(ParticipantType.DEFENDANT);
        assertThat(participants.stream().findFirst().get().getLastName()).isEqualTo("Example");
    }

    @Test
    @DisplayName("Should create both witness and defendant participants")
    public void createParticipantsWithBoth() {
        ProcessedRecording recording = ProcessedRecording.builder()
            .witnessFirstName("Witness")
            .defendantLastName("Defendant")
            .build();

        Set<CreateParticipantDTO> participants = entityCreationService.createParticipants(recording);

        assertThat(participants).hasSize(2);
        assertThat(participants).anyMatch(p -> p.getParticipantType() == ParticipantType.WITNESS
            && "Witness".equals(p.getFirstName()));
        assertThat(participants).anyMatch(p -> p.getParticipantType() == ParticipantType.DEFENDANT
            && "Defendant".equals(p.getLastName()));
    }

    @Test
    @DisplayName("Should return empty set when no names are provided")
    public void createParticipantsWithNoData() {
        ProcessedRecording recording = ProcessedRecording.builder().build();

        Set<CreateParticipantDTO> participants = entityCreationService.createParticipants(recording);

        assertThat(participants).isEmpty();
    }


    @Test
    @DisplayName("Should return CreateShareBookingDTO")
    public void createShareBookingsCreateShareBookingDTO() {
        CreateBookingDTO booking = new CreateBookingDTO();
        booking.setId(UUID.randomUUID());
        CreateUserDTO sharedWith = new CreateUserDTO();
        sharedWith.setId(UUID.randomUUID());
        CreateUserDTO sharedBy = new CreateUserDTO();
        sharedBy.setId(UUID.randomUUID());

        CreateShareBookingDTO result = entityCreationService.createShareBooking(booking, sharedWith, sharedBy);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getBookingId()).isEqualTo(booking.getId());
        assertThat(result.getSharedWithUser()).isEqualTo(sharedWith.getId());
        assertThat(result.getSharedByUser()).isEqualTo(sharedBy.getId());
    }

    @Test
    @DisplayName("Should return user id from cache")
    public void getUserIdFromCacheSuccess() {
        String userId = UUID.randomUUID().toString();
        when(cacheService.getHashValue(Constants.CacheKeys.USERS_PREFIX, "example@example.com", String.class))
            .thenReturn(userId);

        assertThat(cacheService.getHashValue(Constants.CacheKeys.USERS_PREFIX, "example@example.com", String.class))
            .isEqualTo(userId);

        verify(cacheService, times(1))
            .getHashValue(Constants.CacheKeys.USERS_PREFIX, "example@example.com", String.class);
    }

    @Test
    @DisplayName("Should get user by id")
    public void getUserByIdSuccess() {
        UserDTO user = new UserDTO();
        user.setId(UUID.randomUUID());
        user.setFirstName("Example");
        user.setLastName("User");
        user.setEmail("example@example.com");
        when(userService.findById(user.getId())).thenReturn(user);

        CreateUserDTO result = entityCreationService.getUserById(user.getId().toString());
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(user.getId());
        assertThat(result.getFirstName()).isEqualTo(user.getFirstName());
        assertThat(result.getLastName()).isEqualTo(user.getLastName());
        assertThat(result.getEmail()).isEqualTo(user.getEmail());
        assertThat(result.getPortalAccess()).isEmpty();
        assertThat(result.getAppAccess()).isNull();
    }

    @Test
    @DisplayName("Should create invite for CreateUserDTO")
    public void createInviteSuccess() {
        CreateUserDTO user = new CreateUserDTO();
        user.setId(UUID.randomUUID());
        user.setFirstName("Example");
        user.setLastName("User");
        user.setEmail("example@example.com");

        CreateInviteDTO invite = entityCreationService.createInvite(user);
        assertThat(invite).isNotNull();
        assertThat(invite.getUserId()).isEqualTo(user.getId());
        assertThat(invite.getEmail()).isEqualTo(user.getEmail());
        assertThat(invite.getFirstName()).isEqualTo(user.getFirstName());
        assertThat(invite.getLastName()).isEqualTo(user.getLastName());
    }
}
