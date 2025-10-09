package uk.gov.hmcts.reform.preapi.batch.application.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.EntityCreationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.ShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.services.BookingService;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostMigrationItemReaderTest {

    @Mock
    private MigrationRecordService migrationRecordService;

    @Mock
    private InMemoryCacheService cacheService;

    @Mock
    private BookingService bookingService;

    @Mock
    private EntityCreationService entityCreationService;

    @Mock
    private MigrationTrackerService migrationTrackerService;

    @Mock
    private LoggingService loggingService;

    private PostMigrationItemReader reader;

    @BeforeEach
    void setUp() {
        reader = new PostMigrationItemReader(
            migrationRecordService,
            cacheService,
            bookingService,
            entityCreationService,
            migrationTrackerService,
            loggingService,
            "vodafone@example.com"
        );
    }

    @Test
    void createReaderPreparesDataEvenInDryRunMode() throws Exception {
        MigrationRecord record = migrationRecord("archive-1", "case|segment");
        record.setBookingId(UUID.randomUUID());

        when(migrationRecordService.findShareableOrigs()).thenReturn(List.of(record));
        Map<String, List<String[]>> channelMap = new HashMap<>();
        channelMap.put("Case Segment", Collections.singletonList(new String[] {"bob.smith", "bob@example.com"}));
        when(cacheService.getAllChannelReferences()).thenReturn(channelMap);

        BookingDTO booking = mock(BookingDTO.class);
        when(bookingService.findById(record.getBookingId())).thenReturn(booking);
        when(booking.getShares()).thenReturn(List.of());

        assertDoesNotThrow(() -> reader.createReader(true).read());
        verify(entityCreationService).createShareBookingAndInviteIfNotExists(
            eq(booking), eq("bob@example.com"), eq("bob"), eq("smith")
        );
    }

    @Test
    void createReaderBuildsItemGroupsForMatchingUsers() throws Exception {
        MigrationRecord record = migrationRecord("archive-2", "case|segment");
        record.setBookingId(UUID.randomUUID());

        when(migrationRecordService.findShareableOrigs()).thenReturn(List.of(record));
        Map<String, List<String[]>> channelMap = new HashMap<>();
        channelMap.put("Case Segment", Arrays.asList(
            new String[] {"alice.jones", "alice@example.com"},
            new String[] {"bob.smith", "bob@example.com"}
        ));
        when(cacheService.getAllChannelReferences()).thenReturn(channelMap);

        BookingDTO booking = buildBookingWithShares("alice@example.com");
        when(bookingService.findById(record.getBookingId())).thenReturn(booking);

        PostMigratedItemGroup group = new PostMigratedItemGroup();
        when(entityCreationService.createShareBookingAndInviteIfNotExists(
            booking,
            "bob@example.com",
            "bob",
            "smith"
        )).thenReturn(group);

        var itemReader = reader.createReader(false);
        assertThat(assertDoesNotThrow(itemReader::read)).isEqualTo(group);
        assertThat(assertDoesNotThrow(itemReader::read)).isNull();

        verify(entityCreationService).createShareBookingAndInviteIfNotExists(
            booking,
            "bob@example.com",
            "bob",
            "smith"
        );

        verify(loggingService).logDebug("Prepared data for user: %s", "bob@example.com");
    }

    @Test
    void createReaderSkipsWhenNoChannelMatch() throws Exception {
        MigrationRecord record = migrationRecord("archive-3", "case|segment");
        record.setBookingId(UUID.randomUUID());

        when(migrationRecordService.findShareableOrigs()).thenReturn(List.of(record));
        Map<String, List<String[]>> channelMap = new HashMap<>();
        channelMap.put("other", Collections.singletonList(new String[] {"user", "user@example.com"}));
        when(cacheService.getAllChannelReferences()).thenReturn(channelMap);

        var readerResult = reader.createReader(false);
        assertThat(assertDoesNotThrow(readerResult::read)).isNull();
        verify(loggingService).logDebug("No matching channel users found for groupKey=%s", 
            record.getRecordingGroupKey());
    }

    @Test
    void createReaderSkipsWhenBookingLookupFails() throws Exception {
        MigrationRecord record = migrationRecord("archive-4", "case|segment");
        record.setBookingId(UUID.randomUUID());

        when(migrationRecordService.findShareableOrigs()).thenReturn(List.of(record));
        Map<String, List<String[]>> channelMap = new HashMap<>();
        channelMap.put("case|segment", Collections.singletonList(new String[] {"user.name", "user@example.com"}));
        when(cacheService.getAllChannelReferences()).thenReturn(channelMap);
        when(bookingService.findById(record.getBookingId())).thenThrow(new RuntimeException("missing"));

        var readerResult = reader.createReader(false);
        assertThat(assertDoesNotThrow(readerResult::read)).isNull();
        verify(loggingService)
            .logWarning("No booking found for record %s (bookingId=%s) — %s",
                record.getArchiveId(), record.getBookingId(), "missing");
    }

    @Test
    void createReaderSkipsWhenPreparationReturnsNull() throws Exception {
        MigrationRecord record = migrationRecord("archive-5", "case|segment");
        record.setBookingId(UUID.randomUUID());

        when(migrationRecordService.findShareableOrigs()).thenReturn(List.of(record));
        Map<String, List<String[]>> channelMap = new HashMap<>();
        channelMap.put("case|segment", Collections.singletonList(new String[] {"user.name", "user@example.com"}));
        when(cacheService.getAllChannelReferences()).thenReturn(channelMap);

        BookingDTO booking = buildBookingWithShares(null);
        when(bookingService.findById(record.getBookingId())).thenReturn(booking);
        when(entityCreationService.createShareBookingAndInviteIfNotExists(booking, "user@example.com", "user", "name"))
            .thenReturn(null);

        var readerResult = reader.createReader(false);
        assertThat(assertDoesNotThrow(readerResult::read)).isNull();
        verify(entityCreationService).createShareBookingAndInviteIfNotExists(booking, "user@example.com", "user", "name");
    }

    @Test
    void channelMatchingHelperHandlesDateSegments() {
        assertThat(invokeChannelMatch("case|2024-04-01", "case_240401_segment")).isTrue();
        assertThat(invokeChannelMatch("case|name", "case-other")).isFalse();
    }

    private boolean invokeChannelMatch(String key, String channel) {
        try {
            var method = PostMigrationItemReader.class
                .getDeclaredMethod("channelContainsAllGroupParts", String.class, String.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, key, channel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MigrationRecord migrationRecord(String archiveId, String groupKey) {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId(archiveId);
        record.setRecordingGroupKey(groupKey);
        return record;
    }

    private BookingDTO buildBookingWithShares(String alreadySharedEmail) {
        ShareBookingDTO share = new ShareBookingDTO();
        share.setId(UUID.randomUUID());
        BaseUserDTO sharedWith = new BaseUserDTO();
        sharedWith.setId(UUID.randomUUID());
        sharedWith.setFirstName("Alice");
        sharedWith.setLastName("Jones");
        if (alreadySharedEmail != null) {
            sharedWith.setEmail(alreadySharedEmail);
        } else {
            sharedWith.setEmail("shared@example.com");
        }
        share.setSharedWithUser(sharedWith);
        BookingDTO booking = mock(BookingDTO.class);
        when(booking.getShares()).thenReturn(List.of(share));
        return booking;
    }
}
