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
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.email.IEmailService;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.services.ShareBookingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
            emailServiceFactory,
            transactionManager,
            "vodafone@example.com"
        );
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
        CreateShareBookingDTO share = new CreateShareBookingDTO();
        share.setId(UUID.randomUUID());
        share.setSharedWithUser(UUID.randomUUID());

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
        CreateShareBookingDTO share = new CreateShareBookingDTO();
        share.setId(UUID.randomUUID());
        share.setSharedWithUser(UUID.randomUUID());
        share.setSharedByUser(UUID.randomUUID());

        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(null);
        item.setShareBookings(List.of(share));

        executor.processOneItem(item);

        verify(shareBookingService).shareBookingById(share);
        verify(migrationTrackerService).addShareBookingReport(share, "", "vodafone@example.com");
    }

    @Test
    void processOneItem_recordsFailure() {
        UUID userId = UUID.randomUUID();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(userId);
        invite.setEmail("blank@example.com");

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
}
