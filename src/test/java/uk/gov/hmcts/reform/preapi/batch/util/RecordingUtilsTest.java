package uk.gov.hmcts.reform.preapi.batch.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RecordingUtilsTest {
    @Test
    void getValidVersionNumber() {
        assertThat(RecordingUtils.getValidVersionNumber(null)).isEqualTo("1");
        assertThat(RecordingUtils.getValidVersionNumber("")).isEqualTo("1");
        assertThat(RecordingUtils.getValidVersionNumber("2")).isEqualTo("2");
    }

    @Test
    void getRecordingVersionNumber() {
        assertThat(RecordingUtils.getRecordingVersionNumber("ORIG")).isEqualTo(1);
        assertThat(RecordingUtils.getRecordingVersionNumber("ORG")).isEqualTo(1);
        assertThat(RecordingUtils.getRecordingVersionNumber("ORI")).isEqualTo(1);
        assertThat(RecordingUtils.getRecordingVersionNumber("COPY")).isEqualTo(2);
    }

    @Test
    @DisplayName("Should return true when no stored version found")
    void isMostRecentVersionNoStoredVersion() {
        assertThat(RecordingUtils.isMostRecentVersion(
            "ORIG",
            "1",
            Map.of()
        )).isTrue();
    }

    @Test
    @DisplayName("Should return true when current version is newer")
    void isMostRecentVersionCurrentIsNewer() {
        assertThat(RecordingUtils.isMostRecentVersion(
            "ORIG",
            "2",
            Map.of("origVersionNumber", "1")
        )).isTrue();
    }

    @Test
    @DisplayName("Should return false when current version is older")
    void isMostRecentVersionCurrentIsOlder() {
        assertThat(RecordingUtils.isMostRecentVersion(
            "ORIG",
            "1",
            Map.of("origVersionNumber", "2")
        )).isFalse();
    }

    @Test
    @DisplayName("Should update metadata for newer original recording")
    void updateVersionMetadataForNewerOriginalRecording() {
        Map<String, Object> existing = Map.of("origVersionNumber", "1");

        Map<String, Object> result = RecordingUtils.updateVersionMetadata("ORIG", "2", "archive-2", existing);

        assertThat(result.get("origVersionNumber")).isEqualTo("2");
        assertThat(result.get("origVersionArchiveName")).isEqualTo("archive-2");
    }

    @Test
    @DisplayName("Should not update metadata for older recording (copy)")
    void updateVersionMetadata() {
        Map<String, Object> existing = Map.of(
            "copyVersionNumber", "3",
            "copyVersionArchiveName", "archive-3"
        );

        Map<String, Object> result = RecordingUtils.updateVersionMetadata("COPY", "2", "archive-2", existing);

        assertThat(result.get("copyVersionNumber")).isEqualTo("3");
        assertThat(result.get("copyVersionArchiveName")).isEqualTo("archive-3");
    }

    @Test
    @DisplayName("Should process versioning for valid original")
    void processVersioningForValidOriginal() {
        Map<String, Object> existing = Map.of("origVersionNumber", "1");

        RecordingUtils.VersionDetails result = RecordingUtils.processVersioning(
            "ORIG", "1", "URN123", "def1", "wit1", existing);

        assertThat(result).isNotNull();
        assertThat(result.versionType()).isEqualTo("ORIG");
        assertThat(result.versionNumberStr()).isEqualTo("1");
        assertThat(result.versionNumber()).isEqualTo(1);
        assertThat(result.isMostRecent()).isTrue();
    }

    @Test
    @DisplayName("Should throw error when processVersioning receives invalid version type")
    void processVersioningInvalidVersionType() {
        String message = assertThrows(IllegalArgumentException.class, () ->
            RecordingUtils.processVersioning("invalid", "1", "urn", "def", "wit", Map.of())
        ).getMessage();

        assertThat(message).isEqualTo("Invalid recording version: invalid");
    }
}
