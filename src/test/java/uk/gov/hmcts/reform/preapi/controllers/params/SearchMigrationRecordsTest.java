package uk.gov.hmcts.reform.preapi.controllers.params;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SearchMigrationRecordsTest {
    @Test
    @DisplayName("Should return timestamp for createDateTo when is not null")
    public void getCreateDateToTimestampNotNull() throws ParseException {
        String inputDate = "2025-07-28";
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date date = formatter.parse(inputDate);

        SearchMigrationRecords searchMigrationRecords = new SearchMigrationRecords();
        searchMigrationRecords.setCreateDateTo(date);

        Timestamp result = searchMigrationRecords.getCreateDateToTimestamp();

        Timestamp expectedTimestamp = Timestamp.valueOf("2025-07-28 23:59:59");
        assertEquals(expectedTimestamp, result);
    }

    @Test
    @DisplayName("Should return null for createDateTo when is null")
    public void getCreateDateToTimestampNull() {
        SearchMigrationRecords searchMigrationRecords = new SearchMigrationRecords();
        searchMigrationRecords.setCreateDateTo(null);

        Timestamp result = searchMigrationRecords.getCreateDateToTimestamp();

        assertNull(result);
    }

    @Test
    @DisplayName("Should return timestamp for createDateFrom when is not null")
    public void getCreateDateFromTimestampNotNull() throws ParseException {
        String inputDate = "2025-07-28";
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date date = formatter.parse(inputDate);

        SearchMigrationRecords searchMigrationRecords = new SearchMigrationRecords();
        searchMigrationRecords.setCreateDateFrom(date);

        Timestamp result = searchMigrationRecords.getCreateDateFromTimestamp();

        Timestamp expectedTimestamp = Timestamp.valueOf("2025-07-28 00:00:00");
        assertEquals(expectedTimestamp, result);
    }

    @Test
    @DisplayName("Should return null for createDateFrom when is null")
    public void getCreateDateFromTimestampNull() {
        SearchMigrationRecords searchMigrationRecords = new SearchMigrationRecords();
        searchMigrationRecords.setCreateDateFrom(null);

        Timestamp result = searchMigrationRecords.getCreateDateFromTimestamp();

        assertNull(result);
    }

}
