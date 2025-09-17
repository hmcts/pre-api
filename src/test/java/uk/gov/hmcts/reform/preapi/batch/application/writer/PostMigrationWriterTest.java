package uk.gov.hmcts.reform.preapi.batch.application.writer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.services.InviteService;
import uk.gov.hmcts.reform.preapi.services.ShareBookingService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostMigrationWriterTest {

    @Mock
    private InviteService inviteService;

    @Mock
    private ShareBookingService shareBookingService;

    @Mock
    private LoggingService loggingService;

    private PostMigrationWriter postMigrationWriter;

    @BeforeEach
    void setUp() {
        postMigrationWriter = new PostMigrationWriter(inviteService, shareBookingService, loggingService);
    }

    @Test
    void write_shouldLogInfoAndProcessAllItems() {
        // Given
        PostMigratedItemGroup item1 = createItemGroup();
        PostMigratedItemGroup item2 = createItemGroup();
        Chunk<PostMigratedItemGroup> items = new Chunk<>(List.of(item1, item2));

        // When
        postMigrationWriter.write(items);

        // Then
        verify(loggingService).logInfo("PostMigrationWriter triggered with %d item(s)", 2);
        verify(loggingService, times(2)).logDebug(eq("Processing item group: %s"), any());
    }

    @Test
    void processMigratedItem_withInvites_shouldCreateInvitesSuccessfully() throws Exception {
        // Given
        CreateInviteDTO invite1 = createInviteDTO("user1@example.com");
        CreateInviteDTO invite2 = createInviteDTO("user2@example.com");
        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite1, invite2));

        // When
        postMigrationWriter.processMigratedItem(item);

        // Then
        verify(inviteService).upsert(invite1);
        verify(inviteService).upsert(invite2);
        verify(loggingService).logInfo("Invite created: %s", "user1@example.com");
        verify(loggingService).logInfo("Invite created: %s", "user2@example.com");
    }

    @Test
    void processMigratedItem_withInviteFailure_shouldLogErrorAndContinue() throws Exception {
        // Given
        CreateInviteDTO invite = createInviteDTO("user@example.com");
        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));

        Exception exception = new RuntimeException("Database error");
        doThrow(exception).when(inviteService).upsert(invite);

        // When
        postMigrationWriter.processMigratedItem(item);

        // Then
        verify(inviteService).upsert(invite);
        verify(loggingService).logError("Failed to create invite: %s | %s", "user@example.com", exception);
    }

    @Test
    void processMigratedItem_withShareBookings_shouldCreateShareBookingsSuccessfully() throws Exception {
        // Given
        CreateShareBookingDTO share1 = createShareBookingDTO();
        CreateShareBookingDTO share2 = createShareBookingDTO();
        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setShareBookings(List.of(share1, share2));

        // When
        postMigrationWriter.processMigratedItem(item);

        // Then
        verify(shareBookingService).shareBookingById(share1);
        verify(shareBookingService).shareBookingById(share2);
        verify(loggingService).logInfo("Share booking created: %s", share1.getId());
        verify(loggingService).logInfo("Share booking created: %s", share2.getId());
    }

    @Test
    void processMigratedItem_withShareBookingFailure_shouldLogErrorAndContinue() throws Exception {
        // Given
        CreateShareBookingDTO share = createShareBookingDTO();
        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setShareBookings(List.of(share));

        Exception exception = new RuntimeException("Service error");
        doThrow(exception).when(shareBookingService).shareBookingById(share);

        // When
        postMigrationWriter.processMigratedItem(item);

        // Then
        verify(shareBookingService).shareBookingById(share);
        verify(loggingService).logError("Failed to create share booking: %s | %s", share.getId(), exception);
    }

    @Test
    void processMigratedItem_withBothInvitesAndShareBookings_shouldProcessBoth() throws Exception {
        // Given
        CreateInviteDTO invite = createInviteDTO("user@example.com");
        CreateShareBookingDTO share = createShareBookingDTO();
        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(invite));
        item.setShareBookings(List.of(share));

        // When
        postMigrationWriter.processMigratedItem(item);

        // Then
        verify(inviteService).upsert(invite);
        verify(shareBookingService).shareBookingById(share);
        verify(loggingService).logInfo("Invite created: %s", "user@example.com");
        verify(loggingService).logInfo("Share booking created: %s", share.getId());
    }

    @Test
    void processMigratedItem_withNullInvites_shouldNotProcessInvites() {
        // Given
        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(null);

        // When
        postMigrationWriter.processMigratedItem(item);

        // Then
        verify(inviteService, never()).upsert(any());
        verify(loggingService).logDebug("Processing item group: %s", item);
    }

    @Test
    void processMigratedItem_withNullShareBookings_shouldNotProcessShareBookings() {
        // Given
        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setShareBookings(null);

        // When
        postMigrationWriter.processMigratedItem(item);

        // Then
        verify(shareBookingService, never()).shareBookingById(any());
        verify(loggingService).logDebug("Processing item group: %s", item);
    }

    @Test
    void processMigratedItem_withEmptyCollections_shouldNotProcessAnyItems() {
        // Given
        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of());
        item.setShareBookings(List.of());

        // When
        postMigrationWriter.processMigratedItem(item);

        // Then
        verify(inviteService, never()).upsert(any());
        verify(shareBookingService, never()).shareBookingById(any());
        verify(loggingService).logDebug("Processing item group: %s", item);
    }

    @Test
    void write_withEmptyChunk_shouldLogZeroItems() {
        // Given
        Chunk<PostMigratedItemGroup> emptyItems = new Chunk<>();

        // When
        postMigrationWriter.write(emptyItems);

        // Then
        verify(loggingService).logInfo("PostMigrationWriter triggered with %d item(s)", 0);
        verify(loggingService, never()).logDebug(anyString(), any());
    }

    private PostMigratedItemGroup createItemGroup() {
        PostMigratedItemGroup item = new PostMigratedItemGroup();
        item.setInvites(List.of(createInviteDTO("test@example.com")));
        item.setShareBookings(List.of(createShareBookingDTO()));
        return item;
    }

    private CreateInviteDTO createInviteDTO(String email) {
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setEmail(email);
        return invite;
    }

    private CreateShareBookingDTO createShareBookingDTO() {
        CreateShareBookingDTO share = new CreateShareBookingDTO();
        share.setId(UUID.randomUUID());
        return share;
    }
}
