package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
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
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {EntityCreationService.class})
public class EntityCreationServiceTest {
    private static final String VODAFONE_EMAIL = "vodafone@test.com";

    @MockitoBean
    private LoggingService loggingService;

    @MockitoBean
    private InMemoryCacheService cacheService;

    @MockitoBean
    private RecordingService recordingService;

    @MockitoBean
    private MigrationRecordService migrationRecordService;

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
        CreateCaseDTO aCase = new CreateCaseDTO();
        aCase.setId(UUID.randomUUID());
        aCase.setParticipants(Set.of());

        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .recordingTimestamp(Timestamp.from(Instant.now()))
            .archiveId("ARCHIVE1")
            .extractedRecordingVersion("ORIG")
            .extractedRecordingVersionNumberStr("1")
            .build();

        when(migrationRecordService.findByArchiveId("ARCHIVE1"))
            .thenReturn(Optional.of(new MigrationRecord()));

        CreateBookingDTO result = entityCreationService.createBooking(processedRecording, aCase, "key");
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getScheduledFor()).isEqualTo(processedRecording.getRecordingTimestamp());
        assertThat(result.getParticipants()).isNotNull();
    }

    @Test
    @DisplayName("Should return null if COPY version and no original booking ID exists")
    public void createBookingReturnsNullIfCopyHasNoOrigBookingId() {
        UUID archiveId = UUID.randomUUID();
        MigrationRecord copyRecord = new MigrationRecord();
        copyRecord.setArchiveId(archiveId.toString());

        when(migrationRecordService.findByArchiveId(archiveId.toString()))
            .thenReturn(Optional.of(copyRecord));
        when(migrationRecordService.getOrigFromCopy(copyRecord))
            .thenReturn(Optional.of(new MigrationRecord())); 

        ProcessedRecording recording = ProcessedRecording.builder()
            .archiveId(archiveId.toString())
            .extractedRecordingVersion("COPY")
            .build();

        CreateCaseDTO caseDTO = new CreateCaseDTO();
        caseDTO.setId(UUID.randomUUID());

        CreateBookingDTO result = entityCreationService.createBooking(recording, caseDTO, "key");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should create a capture session successfully")
    public void createCaptureSessionSuccess() {
        BaseUserDTO user = new UserDTO();
        user.setId(UUID.randomUUID());
        AccessDTO accessDTO = new AccessDTO();
        accessDTO.setUser(user);
        when(userService.findByEmail(VODAFONE_EMAIL)).thenReturn(accessDTO);

        CreateBookingDTO booking = new CreateBookingDTO();
        booking.setId(UUID.randomUUID());

        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .recordingTimestamp(Timestamp.from(Instant.now()))
            .duration(Duration.ofMinutes(3))
            .extractedRecordingVersion("null")
            .build();

        CreateCaptureSessionDTO result = entityCreationService.createCaptureSession(processedRecording, booking);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getBookingId()).isEqualTo(booking.getId());
        assertThat(result.getStartedAt()).isEqualTo(processedRecording.getRecordingTimestamp());
        assertThat(result.getStartedByUserId()).isEqualTo(user.getId());
        assertThat(result.getFinishedAt()).isNotNull();
        assertThat(result.getFinishedByUserId()).isEqualTo(user.getId());
        assertThat(result.getStatus()).isEqualTo(RecordingStatus.NO_RECORDING);
        assertThat(result.getOrigin()).isEqualTo(RecordingOrigin.VODAFONE);

        verify(userService, times(1)).findByEmail(VODAFONE_EMAIL);
    }

    @Test
    @DisplayName("Should return null when original capture session ID is missing for COPY")
    public void createCaptureSessionOrigMissingId() {
        MigrationRecord copyRecord = new MigrationRecord();
        when(migrationRecordService.findByArchiveId("ARCH123")).thenReturn(Optional.of(copyRecord));
        when(migrationRecordService.getOrigFromCopy(copyRecord)).thenReturn(Optional.of(new MigrationRecord()));

        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .archiveId("ARCH123")
            .extractedRecordingVersion("COPY")
            .recordingTimestamp(Timestamp.from(Instant.now()))
            .build();

        CreateBookingDTO booking = new CreateBookingDTO();
        booking.setId(UUID.randomUUID());

        CreateCaptureSessionDTO session = entityCreationService.createCaptureSession(processedRecording, booking);
        assertThat(session).isNull();
    }


    @Test
    @DisplayName("Should create a recording without parent ID when version is 1")
    public void createRecordingVersionOne() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .fileName("test_file.mp4")
            .duration(null)
            .recordingVersionNumber(1)
            .extractedRecordingVersion("ORIG")
            .caseReference("CASE123")
            .defendantLastName("Smith")
            .witnessFirstName("John")
            .build();

        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());

        CreateRecordingDTO result = entityCreationService.createRecording(processedRecording, captureSession);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCaptureSessionId()).isEqualTo(captureSession.getId());
        assertThat(result.getFilename()).isEqualTo("test_file.mp4");
        assertThat(result.getVersion()).isEqualTo(1);
        assertThat(result.getParentRecordingId()).isNull();

    }

    @Test
    @DisplayName("Should create a recording with parent ID when version is greater than 1 and metadata exists")
    public void createRecordingWithParent() {
        UUID parentId = UUID.randomUUID();

        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());

        MigrationRecord currentRecord = new MigrationRecord();
        MigrationRecord origRecord = new MigrationRecord();
        origRecord.setRecordingId(parentId);
        origRecord.setArchiveId("ORIG123");

        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .archiveId("ARCH123")
            .fileName("test_file.mp4")
            .duration(Duration.ofMinutes(5))
            .recordingVersionNumber(2)
            .extractedRecordingVersion("COPY")
            .extractedRecordingVersionNumberStr("2")
            .caseReference("key")
            .defendantLastName("Smith")
            .witnessFirstName("John")
            .origVersionNumberStr("2")
            .build();

        when(migrationRecordService.findByArchiveId("ARCH123")).thenReturn(Optional.of(currentRecord));
        when(migrationRecordService.getOrigFromCopy(currentRecord)).thenReturn(Optional.of(origRecord));
        when(recordingService.getNextVersionNumber(parentId)).thenReturn(2);


        CreateRecordingDTO result = entityCreationService.createRecording(processedRecording, captureSession);

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

        CreateRecordingDTO result = entityCreationService.createRecording(processedRecording, captureSession);

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
    @DisplayName("Should filter participants by witness or defendant name")
    public void createBookingShouldFilterParticipants() {
        
        CreateParticipantDTO witness = new CreateParticipantDTO();
        witness.setId(UUID.randomUUID());
        witness.setFirstName("John");
        witness.setParticipantType(ParticipantType.WITNESS);

        CreateParticipantDTO defendant = new CreateParticipantDTO();
        defendant.setId(UUID.randomUUID());
        defendant.setLastName("Smith");
        defendant.setParticipantType(ParticipantType.DEFENDANT);

        UUID caseId = UUID.randomUUID();
        String witnessFirstName = "John";
        String defendantLastName = "Smith";

        CreateCaseDTO caseDTO = new CreateCaseDTO();
        caseDTO.setId(caseId);
        caseDTO.setParticipants(Set.of(witness, defendant));

        ProcessedRecording recording = ProcessedRecording.builder()
            .recordingTimestamp(Timestamp.from(Instant.now()))
            .archiveId("ARCH456")
            .extractedRecordingVersion("ORIG")
            .witnessFirstName(witnessFirstName)
            .defendantLastName(defendantLastName)
            .build();

        when(migrationRecordService.findByArchiveId("ARCH456"))
            .thenReturn(Optional.empty());

        CreateBookingDTO result = entityCreationService.createBooking(recording, caseDTO, "key");

        assertThat(result).isNotNull();
        assertThat(result.getParticipants()).containsExactlyInAnyOrder(witness, defendant);
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
    @DisplayName("Should get user by email and return id")
    public void getUserByIdSuccess() {
        String email = "example@example.com";
        UUID userId = UUID.randomUUID();

        UserDTO user = new UserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("User");
        user.setEmail(email);

        AccessDTO accessDTO = new AccessDTO();
        accessDTO.setUser(user);

        when(userService.findByEmail(email)).thenReturn(accessDTO);

        UUID result = entityCreationService.getUserByEmail(email);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(userId);
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

    @Test
    @DisplayName("Should create booking with new booking ID for non-COPY recording")
    void createBookingShouldCreateNewBookingForNonCopyRecording() {
        ProcessedRecording recording = new ProcessedRecording();
        recording.setArchiveId("archive123");
        recording.setExtractedRecordingVersion("ORIG");
        recording.setRecordingTimestamp(Timestamp.from(Instant.now()));

        CreateCaseDTO caseDTO = new CreateCaseDTO();
        caseDTO.setId(UUID.randomUUID());
        caseDTO.setParticipants(Set.of());

        when(migrationRecordService.findByArchiveId("archive123")).thenReturn(Optional.empty());

        CreateBookingDTO result = entityCreationService.createBooking(recording, caseDTO, "key");

        assertThat(result.getId()).isNotNull();
        assertThat(result.getCaseId()).isEqualTo(caseDTO.getId());
        assertThat(result.getScheduledFor()).isEqualTo(recording.getRecordingTimestamp());
        verify(migrationRecordService).updateBookingId("archive123", result.getId());
    }

    @Test
    @DisplayName("Should create user with generated UUID")
    void createUserShouldCreateUserWithGeneratedId() {
        CreateUserDTO result = entityCreationService.createUser("John", "Doe", "john.doe@example.com");

        assertThat(result.getId()).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(result.getPortalAccess()).isEmpty();
        assertThat(result.getAppAccess()).isNull();
    }

    @Test
    @DisplayName("Should create user with specified UUID")
    void createUserShouldCreateUserWithSpecifiedId() {
        UUID specificId = UUID.randomUUID();

        CreateUserDTO result = entityCreationService.createUser("Jane", "Smith", "jane.smith@example.com", specificId);

        assertThat(result.getId()).isEqualTo(specificId);
        assertThat(result.getFirstName()).isEqualTo("Jane");
        assertThat(result.getLastName()).isEqualTo("Smith");
        assertThat(result.getEmail()).isEqualTo("jane.smith@example.com");
    }

    @Test
    @DisplayName("Should create share booking and invite when user does not exist")
    void createShareBookingAndInviteIfNotExistsShouldCreateNewUserAndInvite() {

        UserDTO vodafoneUser = new UserDTO();
        vodafoneUser.setId(UUID.randomUUID());
        vodafoneUser.setEmail(VODAFONE_EMAIL);

        AccessDTO accessDTO = new AccessDTO();
        accessDTO.setUser(vodafoneUser);

        when(userService.findByEmail(VODAFONE_EMAIL)).thenReturn(accessDTO);

        // Mock cache service - ensure vodafone user ID is found in cache
        when(cacheService.getHashValue(Constants.CacheKeys.USERS_PREFIX, "test@example.com", String.class))
            .thenReturn(null);
        when(cacheService.getHashValue(Constants.CacheKeys.USERS_PREFIX, VODAFONE_EMAIL, String.class))
            .thenReturn(vodafoneUser.getId().toString());
        when(cacheService.getShareBooking(anyString())).thenReturn(Optional.empty());

        BookingDTO booking = createTestBooking();
        when(cacheService.generateEntityCacheKey(eq("share-booking"), eq(booking.getId().toString()), anyString()))
            .thenReturn("share-key");

        var result = entityCreationService.createShareBookingAndInviteIfNotExists(
            booking,
            "test@example.com",
            "Test",
            "User"
        );

        assertThat(result).isNotNull();
        assertThat(result.getInvites()).hasSize(1);
        assertThat(result.getShareBookings()).hasSize(1);
        assertThat(result.getInvites().get(0).getEmail()).isEqualTo("test@example.com");

        verify(cacheService, times(2)).saveUser(eq("test@example.com"), any(UUID.class));
        verify(cacheService).saveShareBooking(anyString(), any(CreateShareBookingDTO.class));
    }

    @Test
    @DisplayName("Should create share booking without invite when user already exists")
    void createShareBookingAndInviteIfNotExistsShouldCreateShareBookingForExistingUser() {
        BookingDTO booking = createTestBooking();
        UUID existingUserId = UUID.randomUUID();
        UUID vodafoneUserId = UUID.randomUUID();

        // Mock existing user in cache
        when(cacheService.getHashValue(Constants.CacheKeys.USERS_PREFIX, "existing@example.com", String.class))
            .thenReturn(existingUserId.toString());
        when(cacheService.getHashValue(Constants.CacheKeys.USERS_PREFIX, VODAFONE_EMAIL, String.class))
            .thenReturn(vodafoneUserId.toString());
        when(cacheService.getShareBooking(anyString())).thenReturn(Optional.empty());
        when(cacheService.generateEntityCacheKey(
            "share-booking",
            booking.getId().toString(),
            existingUserId.toString()
        ))
            .thenReturn("share-key");

        var result = entityCreationService.createShareBookingAndInviteIfNotExists(
            booking,
            "existing@example.com",
            "Existing",
            "User"
        );

        assertThat(result).isNotNull();
        assertThat(result.getInvites()).isEmpty();
        assertThat(result.getShareBookings()).hasSize(1);
        assertThat(result.getShareBookings().get(0).getSharedWithUser()).isEqualTo(existingUserId);

        verify(cacheService).saveShareBooking(anyString(), any(CreateShareBookingDTO.class));
    }

    @Test
    @DisplayName("Should return null when share booking already exists")
    void createShareBookingAndInviteIfNotExistsShouldReturnNullWhenShareBookingExists() {
        var booking = createTestBooking();
        var existingUserId = UUID.randomUUID();

        when(cacheService.getHashValue(Constants.CacheKeys.USERS_PREFIX, "test@example.com", String.class))
            .thenReturn(existingUserId.toString());
        when(cacheService.generateEntityCacheKey(
            "share-booking",
            booking.getId().toString(),
            existingUserId.toString()
        ))
            .thenReturn("share-key");
        when(cacheService.getShareBooking("share-key")).thenReturn(Optional.of(new CreateShareBookingDTO()));

        var result = entityCreationService.createShareBookingAndInviteIfNotExists(
            booking,
            "test@example.com",
            "Test",
            "User"
        );

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null when vodafone user not found in cache")
    void createShareBookingAndInviteIfNotExistsShouldReturnNullWhenVodafoneUserNotFound() {
        var booking = createTestBooking();

        when(cacheService.getHashValue(Constants.CacheKeys.USERS_PREFIX, "test@example.com", String.class))
            .thenReturn(null);
        when(cacheService.getHashValue(Constants.CacheKeys.USERS_PREFIX, VODAFONE_EMAIL, String.class))
            .thenReturn(null);
        when(cacheService.getShareBooking(anyString())).thenReturn(Optional.empty());

        var result = entityCreationService.createShareBookingAndInviteIfNotExists(
            booking,
            "test@example.com",
            "Test",
            "User"
        );

        assertThat(result).isNull();
        verify(loggingService).logWarning("Vodafone user ID not found in cache for email: %s", VODAFONE_EMAIL);
    }

    @Test
    @DisplayName("Should handle email case normalization")
    void createShareBookingAndInviteIfNotExistsShouldNormalizeEmailCase() {
        var booking = createTestBooking();
        var vodafoneUserId = UUID.randomUUID();

        when(cacheService.getHashValue(eq(Constants.CacheKeys.USERS_PREFIX), eq("test@example.com"), eq(String.class)))
            .thenReturn(null);
        when(cacheService.getHashValue(eq(Constants.CacheKeys.USERS_PREFIX), eq(VODAFONE_EMAIL), eq(String.class)))
            .thenReturn(vodafoneUserId.toString());
        when(cacheService.getShareBooking(anyString())).thenReturn(Optional.empty());
        when(cacheService.generateEntityCacheKey(eq("share-booking"), eq(booking.getId().toString()), anyString()))
            .thenReturn("share-key");

        var result = entityCreationService.createShareBookingAndInviteIfNotExists(
            booking,
            "TEST@EXAMPLE.COM", // Upper case email
            "Test",
            "User"
        );

        assertThat(result).isNotNull();
        verify(cacheService, times(2)).saveUser(
            eq("test@example.com"),
            any(UUID.class)
        ); // Should be normalized to lowercase
    }

    @Test
    @DisplayName("Should handle exception when getting user by email")
    void getUserByEmailShouldHandleException() {
        String email = "error@example.com";
        
        when(userService.findByEmail(email)).thenThrow(new RuntimeException("Service error"));

        UUID result = entityCreationService.getUserByEmail(email);

        assertThat(result).isNull();
        verify(loggingService, times(1)).logWarning(eq("Could not find user by email: %s - %s"), eq(email), any());
    }

    @Test
    @DisplayName("Should return null for COPY recording when no migration record found")
    void createRecordingShouldReturnNullForCopyWithNoMigrationRecord() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .archiveId("MISSING_ARCH")
            .fileName("test_file.mp4")
            .duration(Duration.ofMinutes(5))
            .recordingVersionNumber(2)
            .extractedRecordingVersion("COPY")
            .build();

        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());

        when(migrationRecordService.findByArchiveId("MISSING_ARCH")).thenReturn(Optional.empty());

        CreateRecordingDTO result = entityCreationService.createRecording(processedRecording, captureSession);

        assertThat(result).isNull();
        verify(loggingService, times(1)).logWarning("No migration record found for COPY archiveId: %s", "MISSING_ARCH");
    }

    @Test
    @DisplayName("Should return null for COPY recording when no ORIG record found")
    void createRecordingShouldReturnNullForCopyWithNoOrigRecord() {
        MigrationRecord copyRecord = new MigrationRecord();
        copyRecord.setArchiveId("COPY123");

        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());

        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .archiveId("COPY123")
            .fileName("test_file.mp4")
            .duration(Duration.ofMinutes(5))
            .recordingVersionNumber(2)
            .extractedRecordingVersion("COPY")
            .build();

        when(migrationRecordService.findByArchiveId("COPY123")).thenReturn(Optional.of(copyRecord));
        when(migrationRecordService.getOrigFromCopy(copyRecord)).thenReturn(Optional.empty());

        CreateRecordingDTO result = entityCreationService.createRecording(processedRecording, captureSession);

        assertThat(result).isNull();
        verify(loggingService, times(1)).logWarning("No ORIG found for COPY archiveId: %s", "COPY123");
    }

    @Test
    @DisplayName("Should return null for COPY recording when ORIG has no recording ID")
    void createRecordingShouldReturnNullForCopyWhenOrigHasNoRecordingId() {
        MigrationRecord copyRecord = new MigrationRecord();
        copyRecord.setArchiveId("COPY123");
        
        MigrationRecord origRecord = new MigrationRecord();
        origRecord.setArchiveId("ORIG123");
        origRecord.setRecordingId(null); // No recording ID

        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());

        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .archiveId("COPY123")
            .fileName("test_file.mp4")
            .duration(Duration.ofMinutes(5))
            .recordingVersionNumber(2)
            .extractedRecordingVersion("COPY")
            .build();

        when(migrationRecordService.findByArchiveId("COPY123")).thenReturn(Optional.of(copyRecord));
        when(migrationRecordService.getOrigFromCopy(copyRecord)).thenReturn(Optional.of(origRecord));

        CreateRecordingDTO result = entityCreationService.createRecording(processedRecording, captureSession);

        assertThat(result).isNull();
        verify(loggingService, times(1)).logWarning(
            "Parent ORIG found but has no recording ID (archiveId: %s)", "ORIG123");
    }

    @Test
    @DisplayName("Should create COPY recording with parent recording ID when all conditions met")
    void createRecordingShouldCreateCopyWithParentRecordingId() {
        UUID parentRecordingId = UUID.randomUUID();
        
        MigrationRecord copyRecord = new MigrationRecord();
        copyRecord.setArchiveId("COPY123");
        
        MigrationRecord origRecord = new MigrationRecord();
        origRecord.setArchiveId("ORIG123");
        origRecord.setRecordingId(parentRecordingId);

        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());

        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .archiveId("COPY123")
            .fileName("test_file.mp4")
            .duration(Duration.ofMinutes(5))
            .recordingVersionNumber(2)
            .extractedRecordingVersion("COPY")
            .build();

        when(migrationRecordService.findByArchiveId("COPY123")).thenReturn(Optional.of(copyRecord));
        when(migrationRecordService.getOrigFromCopy(copyRecord)).thenReturn(Optional.of(origRecord));
        when(recordingService.getNextVersionNumber(parentRecordingId)).thenReturn(2);

        CreateRecordingDTO result = entityCreationService.createRecording(processedRecording, captureSession);

        assertThat(result).isNotNull();
        assertThat(result.getParentRecordingId()).isEqualTo(parentRecordingId);
        assertThat(result.getVersion()).isEqualTo(2);
        verify(migrationRecordService, times(1)).updateRecordingId(eq("COPY123"), any(UUID.class));
    }

    @Test
    @DisplayName("Should test isOrigRecordingPersisted method through reflection for false case")
    void isOrigRecordingPersistedShouldReturnFalseWhenRecordNotFound() throws Exception {
        Method method = EntityCreationService.class.getDeclaredMethod("isOrigRecordingPersisted", String.class);
        method.setAccessible(true);

        when(migrationRecordService.findByArchiveId("MISSING_ARCH")).thenReturn(Optional.empty());

        Boolean result = (Boolean) method.invoke(entityCreationService, "MISSING_ARCH");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should test isOrigRecordingPersisted method for false case when no orig found")
    void isOrigRecordingPersistedShouldReturnFalseWhenNoOrigFound() throws Exception {
        Method method = EntityCreationService.class.getDeclaredMethod("isOrigRecordingPersisted", String.class);
        method.setAccessible(true);

        MigrationRecord copyRecord = new MigrationRecord();
        when(migrationRecordService.findByArchiveId("COPY123")).thenReturn(Optional.of(copyRecord));
        when(migrationRecordService.getOrigFromCopy(copyRecord)).thenReturn(Optional.empty());

        Boolean result = (Boolean) method.invoke(entityCreationService, "COPY123");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should test isOrigRecordingPersisted method for false case when recording ID is null")
    void isOrigRecordingPersistedShouldReturnFalseWhenRecordingIdIsNull() throws Exception {
        Method method = EntityCreationService.class.getDeclaredMethod("isOrigRecordingPersisted", String.class);
        method.setAccessible(true);

        MigrationRecord copyRecord = new MigrationRecord();
        MigrationRecord origRecord = new MigrationRecord();
        origRecord.setRecordingId(null);

        when(migrationRecordService.findByArchiveId("COPY123")).thenReturn(Optional.of(copyRecord));
        when(migrationRecordService.getOrigFromCopy(copyRecord)).thenReturn(Optional.of(origRecord));

        Boolean result = (Boolean) method.invoke(entityCreationService, "COPY123");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should test isOrigRecordingPersisted method for true case")
    void isOrigRecordingPersistedShouldReturnTrueWhenRecordingIdExists() throws Exception {
        Method method = EntityCreationService.class.getDeclaredMethod("isOrigRecordingPersisted", String.class);
        method.setAccessible(true);

        UUID recordingId = UUID.randomUUID();
        MigrationRecord copyRecord = new MigrationRecord();
        MigrationRecord origRecord = new MigrationRecord();
        origRecord.setRecordingId(recordingId);

        when(migrationRecordService.findByArchiveId("COPY123")).thenReturn(Optional.of(copyRecord));
        when(migrationRecordService.getOrigFromCopy(copyRecord)).thenReturn(Optional.of(origRecord));

        Boolean result = (Boolean) method.invoke(entityCreationService, "COPY123");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should create capture session and update migration record for non-COPY version")
    void createCaptureSessionShouldUpdateMigrationRecordForNonCopyVersion() {
        BaseUserDTO user = new UserDTO();
        user.setId(UUID.randomUUID());
        AccessDTO accessDTO = new AccessDTO();
        accessDTO.setUser(user);
        when(userService.findByEmail(VODAFONE_EMAIL)).thenReturn(accessDTO);

        CreateBookingDTO booking = new CreateBookingDTO();
        booking.setId(UUID.randomUUID());

        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .archiveId("ARCH789")
            .recordingTimestamp(Timestamp.from(Instant.now()))
            .duration(Duration.ofMinutes(3))
            .extractedRecordingVersion("ORIG")
            .build();

        CreateCaptureSessionDTO result = entityCreationService.createCaptureSession(processedRecording, booking);

        assertThat(result).isNotNull();
        verify(migrationRecordService, times(1)).updateCaptureSessionId(eq("ARCH789"), any(UUID.class));
    }

    @Test
    @DisplayName("Should create recording and update migration record for non-COPY version")
    void createRecordingShouldUpdateMigrationRecordForNonCopyVersion() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .archiveId("ARCH456")
            .fileName("test_file.mp4")
            .duration(Duration.ofSeconds(100))
            .recordingVersionNumber(1)
            .extractedRecordingVersion("ORIG")
            .build();

        CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());

        CreateRecordingDTO result = entityCreationService.createRecording(processedRecording, captureSession);

        assertThat(result).isNotNull();
        verify(migrationRecordService, times(1)).updateRecordingId(eq("ARCH456"), any(UUID.class));
    }

    @Test
    @DisplayName("Should handle empty trimmed participant names")
    void createParticipantsShouldIgnoreEmptyTrimmedNames() {
        ProcessedRecording recording = ProcessedRecording.builder()
            .witnessFirstName("   ") 
            .defendantLastName("") 
            .build();

        Set<CreateParticipantDTO> participants = entityCreationService.createParticipants(recording);

        assertThat(participants).isEmpty();
    }


    private BookingDTO createTestBooking() {
        BookingDTO booking = new BookingDTO();
        booking.setId(UUID.randomUUID());

        CaseDTO caseDTO = new CaseDTO();
        caseDTO.setId(UUID.randomUUID());
        caseDTO.setParticipants(Collections.emptyList());

        booking.setCaseDTO(caseDTO);
        booking.setScheduledFor(Timestamp.from(Instant.now()));

        return booking;
    }
}
