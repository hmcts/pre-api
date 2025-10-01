package uk.gov.hmcts.reform.preapi.batch.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractedMetadataTest {

    @Test
    @DisplayName("Should format defendant and witness names correctly")
    void testFormattedNames() {
        ExtractedMetadata metadata = new ExtractedMetadata(
            "CourtRef",
            UUID.randomUUID(),
            "210101",
            "urn123",
            "ex123",
            "smith-jones",
            "anna-marie",
            "ORIG",
            "1",
            ".mp4",
            LocalDateTime.of(2021, Month.JANUARY, 1, 12, 0),
            120,
            "video.mp4",
            "20MB",
            "archiveId",
            "filename.mp4"
        );

        assertThat(metadata.getDefendantLastName()).isEqualTo("Smith-Jones");
        assertThat(metadata.getWitnessFirstName()).isEqualTo("Anna-Marie");
        assertThat(metadata.getUrn()).isEqualTo("URN123");
        assertThat(metadata.getExhibitReference()).isEqualTo("EX123");
    }

    @Test
    @DisplayName("Should create case reference from URN and exhibit")
    void testCreateCaseReference() {
        ExtractedMetadata meta = new ExtractedMetadata();
        meta.setUrn("URN1234567");
        meta.setExhibitReference("EX456789012");
        assertThat(meta.createCaseReference()).isEqualTo("URN1234567");

        meta.setUrn(null);
        assertThat(meta.createCaseReference()).isEqualTo("EX456789012");

        meta.setUrn("URN1234567");
        meta.setExhibitReference(null);
        assertThat(meta.createCaseReference()).isEqualTo("URN1234567");

        meta.setUrn("");
        meta.setExhibitReference("");
        assertThat(meta.createCaseReference()).isEqualTo("");
    }

    @Test
    @DisplayName("Should default create time if null or invalid")
    void testGetCreateTimeAsLocalDateTime() {
        ExtractedMetadata meta = new ExtractedMetadata();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime result = meta.getCreateTimeAsLocalDateTime();
        assertThat(result).isAfter(now.minusSeconds(1));

        meta.setCreateTime(LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC));
        result = meta.getCreateTimeAsLocalDateTime();
        assertThat(result).isAfter(now.minusSeconds(1));

        meta.setCreateTime(LocalDateTime.of(2023, 6, 15, 10, 0));
        assertThat(meta.getCreateTimeAsLocalDateTime()).isEqualTo(LocalDateTime.of(2023, 6, 15, 10, 0));
    }

    @Test
    @DisplayName("Should remove file extension from archive name")
    void testGetArchiveNameNoExt() {
        ExtractedMetadata meta = new ExtractedMetadata();
        meta.setArchiveName("filename.mp4");
        assertThat(meta.getArchiveNameNoExt()).isEqualTo("filename");

        meta.setArchiveName("noextension");
        assertThat(meta.getArchiveNameNoExt()).isEqualTo("noextension");
    }

    @Test
    @DisplayName("toString should include key fields")
    void testToString() {
        ExtractedMetadata meta = new ExtractedMetadata();
        meta.setCourtReference("TEST");
        meta.setUrn("URN");
        meta.setExhibitReference("EX");
        meta.setDefendantLastName("Doe");
        meta.setWitnessFirstName("Jane");
        meta.setRecordingVersion("ORIG");
        meta.setRecordingVersionNumber("1");
        meta.setFileExtension(".mp4");
        meta.setCreateTime(LocalDateTime.of(2023, 1, 1, 0, 0));
        meta.setDuration(60);
        meta.setFileName("file.mp4");
        meta.setFileSize("20MB");

        String result = meta.toString();
        assertThat(result).contains("courtReference='TEST'")
                          .contains("urn='URN'")
                          .contains("fileSize='20MB'");
    }
}