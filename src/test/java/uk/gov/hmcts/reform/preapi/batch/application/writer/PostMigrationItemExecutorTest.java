package uk.gov.hmcts.reform.preapi.batch.application.writer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.email.IEmailService;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.services.ShareBookingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostMigrationItemExecutorTest {

    @Mock
    private LoggingService loggingService;

    @Mock
    private MigrationTrackerService migrationTrackerService;

    @Mock
    private ShareBookingService shareBookingService;

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PortalAccessRepository portalAccessRepository;

    @Mock
    private EmailServiceFactory emailServiceFactory;

    @Mock
    private IEmailService emailService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private PostMigrationItemExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new PostMigrationItemExecutor(
            loggingService,
            migrationTrackerService,
            shareBookingService,
            userService,
            userRepository,
            portalAccessRepository,
            emailServiceFactory,
            transactionManager,
            "vodafone@example.com"
        );
    }

    private void mockActiveUser(UUID userId, String email) {
        UserDTO activeUser = new UserDTO();
        activeUser.setId(userId);
        activeUser.setEmail(email);
        activeUser.setDeletedAt(null);

        when(userService.findById(userId)).thenReturn(activeUser);
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId))
            .thenReturn(Optional.empty());
        when(portalAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(userId))
            .thenReturn(List.of());
    }

    @Test
    void processOneItem_invitesAreProcessedInNewTransaction() {
        UUID userId = UUID.randomUUID();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(userId);
        invite.setEmail("person@example.com");

        User storedUser = new User();
        storedUser.setId(userId);
        storedUser.setEmail(invite.getEmail());

        mockActiveUser(userId, invite.getEmail());
        when(userRepository.findById(userId)).thenReturn(Optional.of(storedUser));
        when(emailServiceFactory.isEnabled()).thenReturn(true);
        when(emailServiceFactory.getEnabledEmailService()).thenReturn(emailService);

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));

        executor.processOneItem(item);

        verify(userService).upsert(invite);
        verify(migrationTrackerService).addInvitedUser(invite);
        verify(emailService).portalInvite(storedUser);
        verify(migrationTrackerService, never()).addShareInviteFailure(any());
    }

    @Test
    void processOneItem_skipsEmailWhenFactoryDisabled() {
        UUID userId = UUID.randomUUID();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(userId);
        invite.setEmail("disabled@example.com");

        mockActiveUser(userId, invite.getEmail());
        when(emailServiceFactory.isEnabled()).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));

        executor.processOneItem(item);

        verify(emailServiceFactory, never()).getEnabledEmailService();
    }

    @Test
    void processOneItem_recordsFailureWhenInviteUpsertFails() {
        UUID userId = UUID.randomUUID();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(userId);
        invite.setEmail("broken@example.com");

        mockActiveUser(userId, invite.getEmail());
        doThrow(new RuntimeException("boom"))
            .when(userService).upsert(invite);

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));

        executor.processOneItem(item);

        ArgumentCaptor<MigrationTrackerService.ShareInviteFailureEntry> captor =
            ArgumentCaptor.forClass(MigrationTrackerService.ShareInviteFailureEntry.class);

        verify(migrationTrackerService).addShareInviteFailure(captor.capture());
        assertThat(captor.getValue().entityType()).isEqualTo("Invite");
        assertThat(captor.getValue().action()).isEqualTo("USER_CREATION");
        assertThat(captor.getValue().reason()).isEqualTo("boom");
    }

    @Test
    void processOneItem_shareBookingsUseNewTransactionAndReport() {
        UUID inviteUserId = UUID.randomUUID();
        UUID shareId = UUID.randomUUID();

        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(inviteUserId);
        invite.setEmail("share@example.com");

        CreateShareBookingDTO share = new CreateShareBookingDTO();
        share.setId(shareId);
        share.setSharedWithUser(inviteUserId);
        share.setSharedByUser(UUID.randomUUID());

        mockActiveUser(inviteUserId, "share@example.com");

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));
        item.setShareBookings(List.of(share));

        executor.processOneItem(item);

        verify(shareBookingService).shareBookingById(share);
        verify(migrationTrackerService).addShareBooking(share);
        verify(migrationTrackerService).addShareBookingReport(share, "share@example.com", "vodafone@example.com");
    }

    @Test
    void processOneItem_recordsFailureWhenShareCreationFails() {
        UUID sharedWithUserId = UUID.randomUUID();
        CreateShareBookingDTO share = new CreateShareBookingDTO();
        share.setId(UUID.randomUUID());
        share.setSharedWithUser(sharedWithUserId);

        mockActiveUser(sharedWithUserId, "test@example.com");

        doThrow(new IllegalStateException("conflict"))
            .when(shareBookingService).shareBookingById(share);

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setShareBookings(List.of(share));

        executor.processOneItem(item);

        ArgumentCaptor<MigrationTrackerService.ShareInviteFailureEntry> captor =
            ArgumentCaptor.forClass(MigrationTrackerService.ShareInviteFailureEntry.class);

        verify(migrationTrackerService).addShareInviteFailure(captor.capture());
        assertThat(captor.getValue().entityType()).isEqualTo("ShareBooking");
        assertThat(captor.getValue().action()).isEqualTo("SHARE");
        assertThat(captor.getValue().reason()).isEqualTo("conflict");
    }

    @Test
    void processOneItem_logsEmailFailureButContinues() {
        UUID userId = UUID.randomUUID();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(userId);
        invite.setEmail("email.fail@example.com");

        User storedUser = new User();
        storedUser.setId(userId);
        storedUser.setEmail(invite.getEmail());

        mockActiveUser(userId, invite.getEmail());
        when(userRepository.findById(userId)).thenReturn(Optional.of(storedUser));
        when(emailServiceFactory.isEnabled()).thenReturn(true);
        when(emailServiceFactory.getEnabledEmailService()).thenReturn(emailService);
        doThrow(new RuntimeException("notify"))
            .when(emailService).portalInvite(storedUser);

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));

        executor.processOneItem(item);

        verify(emailService).portalInvite(storedUser);
        verify(migrationTrackerService, never()).addShareInviteFailure(any());
    }

    @Test
    void processOneItem_handlesShareWithoutMatchingInvite() {
        UUID sharedWithUserId = UUID.randomUUID();
        CreateShareBookingDTO share = new CreateShareBookingDTO();
        share.setId(UUID.randomUUID());
        share.setSharedWithUser(sharedWithUserId);
        share.setSharedByUser(UUID.randomUUID());

        mockActiveUser(sharedWithUserId, "test@example.com");

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(null);
        item.setShareBookings(List.of(share));

        executor.processOneItem(item);

        verify(shareBookingService).shareBookingById(share);
        verify(migrationTrackerService).addShareBookingReport(share, "test@example.com", "vodafone@example.com");
    }

    @Test
    void processOneItem_recordsFailure() {
        UUID userId = UUID.randomUUID();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(userId);
        invite.setEmail("blank@example.com");

        mockActiveUser(userId, invite.getEmail());
        doThrow(new RuntimeException("   ")) 
            .when(userService).upsert(invite);

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));

        executor.processOneItem(item);

        ArgumentCaptor<MigrationTrackerService.ShareInviteFailureEntry> captor =
            ArgumentCaptor.forClass(MigrationTrackerService.ShareInviteFailureEntry.class);

        verify(migrationTrackerService).addShareInviteFailure(captor.capture());
        assertThat(captor.getValue().entityType()).isEqualTo("Invite");
        assertThat(captor.getValue().action()).isEqualTo("USER_CREATION");
        assertThat(captor.getValue().reason()).isEqualTo("RuntimeException"); 
    }

    @Test
    void processOneItem_skipsInviteForInactiveUser() {
        UUID userId = UUID.randomUUID();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(userId);
        invite.setEmail("inactive@example.com");

        UserDTO deletedUser = new UserDTO();
        deletedUser.setId(userId);
        deletedUser.setEmail(invite.getEmail());
        deletedUser.setDeletedAt(Timestamp.from(java.time.Instant.now()));

        when(userService.findById(userId)).thenReturn(deletedUser);

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));

        executor.processOneItem(item);

        verify(userService, never()).upsert(any(CreateInviteDTO.class));
        verify(migrationTrackerService, never()).addInvitedUser(any());
        
        ArgumentCaptor<MigrationTrackerService.ShareInviteFailureEntry> captor =
            ArgumentCaptor.forClass(MigrationTrackerService.ShareInviteFailureEntry.class);
        verify(migrationTrackerService).addShareInviteFailure(captor.capture());
        assertThat(captor.getValue().entityType()).isEqualTo("Invite");
        assertThat(captor.getValue().action()).isEqualTo("SKIPPED");
        assertThat(captor.getValue().reason()).isEqualTo("User is inactive or deleted");
    }

    @Test
    void processOneItem_skipsInviteForUserWithInactivePortalAccess() {
        UUID userId = UUID.randomUUID();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(userId);
        invite.setEmail("inactive@example.com");

        UserDTO activeUser = new UserDTO();
        activeUser.setId(userId);
        activeUser.setEmail(invite.getEmail());
        activeUser.setDeletedAt(null);

        PortalAccess inactiveAccess = new PortalAccess();
        inactiveAccess.setStatus(AccessStatus.INACTIVE);

        when(userService.findById(userId)).thenReturn(activeUser);
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId))
            .thenReturn(Optional.of(inactiveAccess));

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));

        executor.processOneItem(item);

        verify(userService, never()).upsert(any(CreateInviteDTO.class));
        verify(migrationTrackerService, never()).addInvitedUser(any());
        
        ArgumentCaptor<MigrationTrackerService.ShareInviteFailureEntry> captor =
            ArgumentCaptor.forClass(MigrationTrackerService.ShareInviteFailureEntry.class);
        verify(migrationTrackerService).addShareInviteFailure(captor.capture());
        assertThat(captor.getValue().entityType()).isEqualTo("Invite");
        assertThat(captor.getValue().action()).isEqualTo("SKIPPED");
    }

    @Test
    void processOneItem_skipsInviteForUserWithDeletedPortalAccess() {
        UUID userId = UUID.randomUUID();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(userId);
        invite.setEmail("deleted@example.com");

        UserDTO activeUser = new UserDTO();
        activeUser.setId(userId);
        activeUser.setEmail(invite.getEmail());
        activeUser.setDeletedAt(null);

        PortalAccess deletedAccess = new PortalAccess();
        deletedAccess.setDeletedAt(Timestamp.from(java.time.Instant.now()));

        when(userService.findById(userId)).thenReturn(activeUser);
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId))
            .thenReturn(Optional.empty());
        when(portalAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(userId))
            .thenReturn(List.of(deletedAccess));

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));

        executor.processOneItem(item);

        verify(userService, never()).upsert(any(CreateInviteDTO.class));
        verify(migrationTrackerService, never()).addInvitedUser(any());
        
        ArgumentCaptor<MigrationTrackerService.ShareInviteFailureEntry> captor =
            ArgumentCaptor.forClass(MigrationTrackerService.ShareInviteFailureEntry.class);
        verify(migrationTrackerService).addShareInviteFailure(captor.capture());
        assertThat(captor.getValue().entityType()).isEqualTo("Invite");
        assertThat(captor.getValue().action()).isEqualTo("SKIPPED");
    }

    @Test
    void processOneItem_skipsInviteWhenExistingInviteExists() {
        UUID userId = UUID.randomUUID();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(userId);
        invite.setEmail("existing@example.com");

        UserDTO activeUser = new UserDTO();
        activeUser.setId(userId);
        activeUser.setEmail(invite.getEmail());
        activeUser.setDeletedAt(null);

        PortalAccess existingInvite = new PortalAccess();
        existingInvite.setStatus(AccessStatus.INVITATION_SENT);

        when(userService.findById(userId)).thenReturn(activeUser);
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId))
            .thenReturn(Optional.empty());
        when(portalAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(userId))
            .thenReturn(List.of());
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNullAndStatus(
            userId, AccessStatus.INVITATION_SENT))
            .thenReturn(Optional.of(existingInvite));

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));

        executor.processOneItem(item);

        verify(userService, never()).upsert(any(CreateInviteDTO.class));
        verify(migrationTrackerService).addInvitedUser(invite);
        verify(loggingService).logDebug("Skipping invite for user %s — invite already exists", invite.getEmail());
    }

    @Test
    void processOneItem_allowsInviteWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(userId);
        invite.setEmail("new@example.com");

        when(userService.findById(userId)).thenThrow(new NotFoundException("User not found"));

        User storedUser = new User();
        storedUser.setId(userId);
        storedUser.setEmail(invite.getEmail());

        when(userRepository.findById(userId)).thenReturn(Optional.of(storedUser));
        when(emailServiceFactory.isEnabled()).thenReturn(true);
        when(emailServiceFactory.getEnabledEmailService()).thenReturn(emailService);

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));

        executor.processOneItem(item);

        verify(userService).upsert(invite);
        verify(migrationTrackerService).addInvitedUser(invite);
    }

    @Test
    void processOneItem_skipsShareBookingForInactiveUser() {
        UUID sharedWithUserId = UUID.randomUUID();
        CreateShareBookingDTO share = new CreateShareBookingDTO();
        share.setId(UUID.randomUUID());
        share.setSharedWithUser(sharedWithUserId);

        UserDTO deletedUser = new UserDTO();
        deletedUser.setId(sharedWithUserId);
        deletedUser.setEmail("inactive@example.com");
        deletedUser.setDeletedAt(Timestamp.from(java.time.Instant.now()));

        when(userService.findById(sharedWithUserId)).thenReturn(deletedUser);

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setShareBookings(List.of(share));

        executor.processOneItem(item);

        verify(shareBookingService, never()).shareBookingById(any());
        
        ArgumentCaptor<MigrationTrackerService.ShareInviteFailureEntry> captor =
            ArgumentCaptor.forClass(MigrationTrackerService.ShareInviteFailureEntry.class);
        verify(migrationTrackerService).addShareInviteFailure(captor.capture());
        assertThat(captor.getValue().entityType()).isEqualTo("ShareBooking");
        assertThat(captor.getValue().action()).isEqualTo("SKIPPED");
        assertThat(captor.getValue().reason()).isEqualTo("User is inactive or deleted");
    }

    @Test
    void processOneItem_skipsShareBookingWhenSharedWithUserIsNull() {
        CreateShareBookingDTO share = new CreateShareBookingDTO();
        share.setId(UUID.randomUUID());
        share.setSharedWithUser(null);

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setShareBookings(List.of(share));

        executor.processOneItem(item);

        verify(shareBookingService, never()).shareBookingById(any());
        
        ArgumentCaptor<MigrationTrackerService.ShareInviteFailureEntry> captor =
            ArgumentCaptor.forClass(MigrationTrackerService.ShareInviteFailureEntry.class);
        verify(migrationTrackerService).addShareInviteFailure(captor.capture());
        assertThat(captor.getValue().entityType()).isEqualTo("ShareBooking");
        assertThat(captor.getValue().action()).isEqualTo("SKIPPED");
        assertThat(captor.getValue().reason()).isEqualTo("SharedWithUser is null");
    }

    @Test
    void processOneItem_resolvesEmailFromInvites() {
        UUID inviteUserId = UUID.randomUUID();
        UUID shareId = UUID.randomUUID();

        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(inviteUserId);
        invite.setEmail("invite@example.com");

        CreateShareBookingDTO share = new CreateShareBookingDTO();
        share.setId(shareId);
        share.setSharedWithUser(inviteUserId);
        share.setSharedByUser(UUID.randomUUID());

        mockActiveUser(inviteUserId, "invite@example.com");

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));
        item.setShareBookings(List.of(share));

        executor.processOneItem(item);

        verify(shareBookingService).shareBookingById(share);
        verify(migrationTrackerService).addShareBookingReport(share, "invite@example.com", "vodafone@example.com");
    }

    @Test
    void processOneItem_resolvesEmailFromUserServiceWhenNotInInvites() {
        UUID sharedWithUserId = UUID.randomUUID();
        CreateShareBookingDTO share = new CreateShareBookingDTO();
        share.setId(UUID.randomUUID());
        share.setSharedWithUser(sharedWithUserId);

        UserDTO user = new UserDTO();
        user.setId(sharedWithUserId);
        user.setEmail("user@example.com");

        when(userService.findById(sharedWithUserId)).thenReturn(user);
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(sharedWithUserId))
            .thenReturn(Optional.empty());
        when(portalAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(sharedWithUserId))
            .thenReturn(List.of());

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(null);
        item.setShareBookings(List.of(share));

        executor.processOneItem(item);

        verify(shareBookingService).shareBookingById(share);
        verify(migrationTrackerService).addShareBookingReport(share, "user@example.com", "vodafone@example.com");
    }

    @Test
    void processOneItem_handlesEmailResolutionException() {
        UUID sharedWithUserId = UUID.randomUUID();
        CreateShareBookingDTO share = new CreateShareBookingDTO();
        share.setId(UUID.randomUUID());
        share.setSharedWithUser(sharedWithUserId);

        UserDTO activeUser = createActiveUser(sharedWithUserId, "user@example.com");
        when(userService.findById(sharedWithUserId))
            .thenThrow(new RuntimeException("Service error"))  
            .thenThrow(new RuntimeException("Service error")) 
            .thenReturn(activeUser); 

        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(sharedWithUserId))
            .thenReturn(Optional.empty());
        when(portalAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(sharedWithUserId))
            .thenReturn(List.of());

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(null);
        item.setShareBookings(List.of(share));

        executor.processOneItem(item);

        verify(loggingService).logWarning("Could not find user email for ID: %s - %s", 
            sharedWithUserId, "Service error");
        verify(loggingService).logWarning("Could not resolve email for user ID: %s - %s", 
            sharedWithUserId, "Service error");
        verify(shareBookingService).shareBookingById(share);
    }

    @Test
    void processOneItem_handlesUserNotFoundWhenSendingPortalInvite() {
        UUID userId = UUID.randomUUID();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(userId);
        invite.setEmail("notfound@example.com");

        mockActiveUser(userId, invite.getEmail());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));

        executor.processOneItem(item);

        verify(userService).upsert(invite);
        verify(migrationTrackerService).addInvitedUser(invite);
        verify(loggingService).logError(eq("Failed to send portal invite for user: %s | %s"),
            eq(invite.getEmail()), any(String.class));
        verify(emailService, never()).portalInvite(any());
        verify(emailServiceFactory, never()).isEnabled();
        verify(emailServiceFactory, never()).getEnabledEmailService();
    }

    @Test
    void processOneItem_handlesExceptionInIsUserActiveForMigration() {
        UUID userId = UUID.randomUUID();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(userId);
        invite.setEmail("error@example.com");

        when(userService.findById(userId)).thenThrow(new RuntimeException("Database error"));

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));

        executor.processOneItem(item);

        verify(userService, never()).upsert(any(CreateInviteDTO.class));
        verify(migrationTrackerService, never()).addInvitedUser(any());
        
        ArgumentCaptor<MigrationTrackerService.ShareInviteFailureEntry> captor =
            ArgumentCaptor.forClass(MigrationTrackerService.ShareInviteFailureEntry.class);
        verify(migrationTrackerService).addShareInviteFailure(captor.capture());
        assertThat(captor.getValue().entityType()).isEqualTo("Invite");
        assertThat(captor.getValue().action()).isEqualTo("SKIPPED");
    }

    @Test
    void processOneItem_handlesInviteWithNullUserId() {
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(null);
        invite.setEmail("nulluser@example.com");

        User storedUser = new User();
        storedUser.setId(UUID.randomUUID());
        storedUser.setEmail(invite.getEmail());

        when(userRepository.findById(any())).thenReturn(Optional.of(storedUser));
        when(emailServiceFactory.isEnabled()).thenReturn(true);
        when(emailServiceFactory.getEnabledEmailService()).thenReturn(emailService);

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));

        executor.processOneItem(item);

        verify(userService).upsert(invite);
        verify(migrationTrackerService).addInvitedUser(invite);
    }

    @Test
    void processOneItem_handlesShareBookingWithEmailResolutionFromUserServiceException() {
        UUID sharedWithUserId = UUID.randomUUID();
        CreateShareBookingDTO share = new CreateShareBookingDTO();
        share.setId(UUID.randomUUID());
        share.setSharedWithUser(sharedWithUserId);

        when(userService.findById(sharedWithUserId))
            .thenThrow(new NotFoundException("User not found"))  
            .thenThrow(new NotFoundException("User not found")) 
            .thenThrow(new NotFoundException("User not found")); 

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(null);
        item.setShareBookings(List.of(share));

        executor.processOneItem(item);

        verify(loggingService).logWarning(eq("User not found for ID: %s - %s"), 
            eq(sharedWithUserId), any(String.class));
        verify(loggingService).logWarning(eq("Could not resolve email for user ID: %s - %s"), 
            eq(sharedWithUserId), any(String.class));
        verify(shareBookingService).shareBookingById(share);
    }

    private UserDTO createActiveUser(UUID userId, String email) {
        UserDTO user = new UserDTO();
        user.setId(userId);
        user.setEmail(email);
        user.setDeletedAt(null);
        return user;
    }
}
