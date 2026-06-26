package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CleanupNullRecordingDurationTest {
    @Nested
    @DisplayName("CleanupNullRecordingDuration task when upsert enabled")
    @SpringBootTest(classes = CleanupNullRecordingDuration.class,
        properties = "feature-flags.cleanup-null-duration.upsert-enabled=true")
    class UpsertEnabled extends BaseCleanupNullRecordingDurationTest {

        @Test
        @DisplayName("Should process recording with null duration successfully")
        void runSuccess() {
            UUID recordingId = UUID.randomUUID();
            RecordingDTO recordingDto = new RecordingDTO();
            recordingDto.setId(recordingId);
            CaptureSessionDTO captureSessionDto = new CaptureSessionDTO();
            captureSessionDto.setId(UUID.randomUUID());
            recordingDto.setCaptureSession(captureSessionDto);
            recordingDto.setFilename("test.mp4");
            when(recordingService.findAllDurationNull()).thenReturn(List.of(recordingDto));
            when(azureFinalStorageService.doesContainerExist(recordingId.toString())).thenReturn(true);
            when(azureFinalStorageService.getRecordingDuration(recordingId)).thenReturn(Duration.ofMinutes(10));
            when(azureFinalStorageService.doesBlobExist(recordingId.toString(), "test.mp4")).thenReturn(true);

            cleanupNullRecordingDuration.run();

            ArgumentCaptor<CreateRecordingDTO> captor = ArgumentCaptor.forClass(CreateRecordingDTO.class);
            verify(recordingService).forceUpsert(captor.capture());
            CreateRecordingDTO capturedRecording = captor.getValue();
            assertThat(capturedRecording.getDuration()).isEqualTo(Duration.ofMinutes(10));
        }

        @Test
        @DisplayName("Should skip recording when storage container does not exist")
        void runSkipRecordingWithNoStorageContainer() {
            UUID recordingId = UUID.randomUUID();
            RecordingDTO recordingDto = new RecordingDTO();
            recordingDto.setId(recordingId);
            when(recordingService.findAllDurationNull()).thenReturn(List.of(recordingDto));
            when(azureFinalStorageService.doesContainerExist(recordingId.toString())).thenReturn(false);

            cleanupNullRecordingDuration.run();

            verifyNoInteractions(ffmpegService);
            verify(recordingService, never()).forceUpsert(any());
        }

        @Test
        @DisplayName("Should update filename when current filename is invalid")
        void runUpdateFilenameWhenFilenameIsInvalid() {
            UUID recordingId = UUID.randomUUID();
            CaptureSessionDTO captureSessionDto = new CaptureSessionDTO();
            captureSessionDto.setId(UUID.randomUUID());
            RecordingDTO recordingDto = new RecordingDTO();
            recordingDto.setId(recordingId);
            recordingDto.setCaptureSession(captureSessionDto);
            recordingDto.setFilename(null);
            when(recordingService.findAllDurationNull()).thenReturn(List.of(recordingDto));
            String newFilename = "new-filename.mp4";
            when(azureFinalStorageService.getMp4FileName(recordingId.toString())).thenReturn(newFilename);
            when(azureFinalStorageService.doesContainerExist(recordingId.toString())).thenReturn(true);
            when(azureFinalStorageService.getRecordingDuration(recordingId)).thenReturn(null);
            when(ffmpegService.getDurationFromMp4(recordingId.toString(), newFilename))
                .thenReturn(Duration.ofMinutes(10));

            cleanupNullRecordingDuration.run();

            verify(azureFinalStorageService, times(1)).doesContainerExist(recordingId.toString());
            verify(azureFinalStorageService, never()).doesBlobExist(any(), any());
            verify(azureFinalStorageService, times(1)).getMp4FileName(recordingId.toString());
            verify(ffmpegService, times(1)).getDurationFromMp4(recordingId.toString(), newFilename);
            ArgumentCaptor<CreateRecordingDTO> captor = ArgumentCaptor.forClass(CreateRecordingDTO.class);
            verify(recordingService, times(1)).forceUpsert(captor.capture());

            CreateRecordingDTO capturedRecording = captor.getValue();
            assertThat(capturedRecording.getDuration()).isEqualTo(Duration.ofMinutes(10));
            assertThat(capturedRecording.getFilename()).isEqualTo(newFilename);
        }

        @Test
        @DisplayName("Should skip recording when mp4 file is not found")
        void runSkipRecordingWithoutMp4File() {
            UUID recordingId = UUID.randomUUID();
            CaptureSessionDTO captureSessionDto = new CaptureSessionDTO();
            captureSessionDto.setId(UUID.randomUUID());
            RecordingDTO recordingDto = new RecordingDTO();
            recordingDto.setId(recordingId);
            recordingDto.setCaptureSession(captureSessionDto);
            recordingDto.setFilename(null);
            when(recordingService.findAllDurationNull()).thenReturn(List.of(recordingDto));
            when(azureFinalStorageService.doesContainerExist(recordingId.toString())).thenReturn(true);
            when(azureFinalStorageService.getMp4FileName(recordingId.toString())).thenThrow(
                new NotFoundException("File not found"));
            when(azureFinalStorageService.getRecordingDuration(recordingId)).thenReturn(null);

            cleanupNullRecordingDuration.run();

            verify(azureFinalStorageService, times(1)).doesContainerExist(recordingId.toString());
            verify(azureFinalStorageService).getMp4FileName(recordingId.toString());
            verify(recordingService, never()).forceUpsert(any());
        }

        @Test
        @DisplayName("Should try to get duration from ffmpeg when metadata duration is null")
        void runGetDurationFromFfmpegWhenMetadataDurationIsNull() {
            UUID recordingId = UUID.randomUUID();
            CaptureSessionDTO captureSessionDto = new CaptureSessionDTO();
            captureSessionDto.setId(UUID.randomUUID());
            RecordingDTO recordingDto = new RecordingDTO();
            recordingDto.setId(recordingId);
            recordingDto.setCaptureSession(captureSessionDto);
            recordingDto.setFilename("test.mp4");
            when(recordingService.findAllDurationNull()).thenReturn(List.of(recordingDto));
            when(azureFinalStorageService.doesContainerExist(recordingId.toString())).thenReturn(true);
            when(azureFinalStorageService.getRecordingDuration(recordingId)).thenReturn(null);
            when(azureFinalStorageService.doesBlobExist(recordingId.toString(), "test.mp4")).thenReturn(true);
            when(ffmpegService.getDurationFromMp4(recordingId.toString(), "test.mp4"))
                .thenReturn(Duration.ofMinutes(5));

            cleanupNullRecordingDuration.run();

            verify(ffmpegService).getDurationFromMp4(recordingId.toString(), "test.mp4");
            verify(recordingService).forceUpsert(any(CreateRecordingDTO.class));
        }

        @Test
        @DisplayName("Should log error when ffmpeg fails to get duration")
        void runDoNotUpdateWhenFfmpegFailsToGetDuration() {
            UUID recordingId = UUID.randomUUID();
            CaptureSessionDTO captureSessionDto = new CaptureSessionDTO();
            captureSessionDto.setId(UUID.randomUUID());
            RecordingDTO recordingDto = new RecordingDTO();
            recordingDto.setId(recordingId);
            recordingDto.setCaptureSession(captureSessionDto);
            recordingDto.setFilename("test.mp4");
            when(recordingService.findAllDurationNull()).thenReturn(Collections.singletonList(recordingDto));
            when(azureFinalStorageService.doesContainerExist(recordingId.toString())).thenReturn(true);
            when(azureFinalStorageService.getRecordingDuration(recordingId)).thenReturn(null);
            when(azureFinalStorageService.doesBlobExist(recordingId.toString(), "test.mp4")).thenReturn(true);
            when(azureFinalStorageService.generateReadSasUrl(recordingId.toString(), "test.mp4")).thenReturn("sas-url");
            when(ffmpegService.getDurationFromMp4(recordingId.toString(), "test.mp4"))
                .thenThrow(new RuntimeException("FFmpeg error"));

            cleanupNullRecordingDuration.run();

            verify(ffmpegService).getDurationFromMp4(recordingId.toString(), "test.mp4");
            verify(recordingService, never()).forceUpsert(any());
        }

        @Test
        @DisplayName("Should use fallback when filename blob does not exist")
        void runGetFilenameWhenBlobDoesntExist() {
            UUID recordingId = UUID.randomUUID();
            CaptureSessionDTO captureSessionDto = new CaptureSessionDTO();
            captureSessionDto.setId(UUID.randomUUID());

            RecordingDTO recordingDto = new RecordingDTO();
            recordingDto.setId(recordingId);
            recordingDto.setCaptureSession(captureSessionDto);
            recordingDto.setFilename("missing.mp4");

            when(recordingService.findAllDurationNull()).thenReturn(List.of(recordingDto));
            when(azureFinalStorageService.doesContainerExist(recordingId.toString())).thenReturn(true);
            when(azureFinalStorageService.getRecordingDuration(recordingId)).thenReturn(null);
            when(azureFinalStorageService.doesBlobExist(recordingId.toString(), "missing.mp4")).thenReturn(false);

            String fallbackFilename = "fallback.mp4";
            when(azureFinalStorageService.getMp4FileName(recordingId.toString())).thenReturn(fallbackFilename);
            when(azureFinalStorageService.doesBlobExist(recordingId.toString(), fallbackFilename)).thenReturn(true);
            when(ffmpegService.getDurationFromMp4(recordingId.toString(), fallbackFilename))
                .thenReturn(Duration.ofMinutes(3));

            cleanupNullRecordingDuration.run();

            verify(azureFinalStorageService).getMp4FileName(recordingId.toString());
            verify(ffmpegService).getDurationFromMp4(recordingId.toString(), fallbackFilename);

            ArgumentCaptor<CreateRecordingDTO> captor = ArgumentCaptor.forClass(CreateRecordingDTO.class);
            verify(recordingService).forceUpsert(captor.capture());

            CreateRecordingDTO captured = captor.getValue();
            assertThat(captured.getFilename()).isEqualTo(fallbackFilename);
            assertThat(captured.getDuration()).isEqualTo(Duration.ofMinutes(3));
        }
    }

    @Nested
    @DisplayName("CleanupNullRecordingDuration task when upsert disabled")
    @SpringBootTest(classes = CleanupNullRecordingDuration.class,
        properties = "feature-flags.cleanup-null-duration.upsert-enabled=false")
    class UpsertDisabled extends BaseCleanupNullRecordingDurationTest {
        @Test
        @DisplayName("Should process recording with null duration successfully")
        void runSuccess() {
            UUID recordingId = UUID.randomUUID();
            RecordingDTO recordingDto = new RecordingDTO();
            recordingDto.setId(recordingId);
            CaptureSessionDTO captureSessionDto = new CaptureSessionDTO();
            captureSessionDto.setId(UUID.randomUUID());
            recordingDto.setCaptureSession(captureSessionDto);
            recordingDto.setFilename("test.mp4");
            when(recordingService.findAllDurationNull()).thenReturn(List.of(recordingDto));
            when(azureFinalStorageService.doesContainerExist(recordingId.toString())).thenReturn(true);
            when(azureFinalStorageService.getRecordingDuration(recordingId)).thenReturn(Duration.ofMinutes(10));
            when(azureFinalStorageService.doesBlobExist(recordingId.toString(), "test.mp4")).thenReturn(true);

            cleanupNullRecordingDuration.run();

            verify(recordingService, never()).forceUpsert(any());
        }

        @Test
        @DisplayName("Should update filename when current filename is invalid")
        void runUpdateFilenameWhenFilenameIsInvalid() {
            UUID recordingId = UUID.randomUUID();
            CaptureSessionDTO captureSessionDto = new CaptureSessionDTO();
            captureSessionDto.setId(UUID.randomUUID());
            RecordingDTO recordingDto = new RecordingDTO();
            recordingDto.setId(recordingId);
            recordingDto.setCaptureSession(captureSessionDto);
            recordingDto.setFilename(null);
            when(recordingService.findAllDurationNull()).thenReturn(List.of(recordingDto));
            String newFilename = "new-filename.mp4";
            when(azureFinalStorageService.getMp4FileName(recordingId.toString())).thenReturn(newFilename);
            when(azureFinalStorageService.doesContainerExist(recordingId.toString())).thenReturn(true);
            when(azureFinalStorageService.getRecordingDuration(recordingId)).thenReturn(null);
            when(ffmpegService.getDurationFromMp4(recordingId.toString(), newFilename))
                .thenReturn(Duration.ofMinutes(10));

            cleanupNullRecordingDuration.run();

            verify(azureFinalStorageService, times(1)).doesContainerExist(recordingId.toString());
            verify(azureFinalStorageService, never()).doesBlobExist(any(), any());
            verify(azureFinalStorageService, times(1)).getMp4FileName(recordingId.toString());
            verify(ffmpegService, times(1)).getDurationFromMp4(recordingId.toString(), newFilename);
            verify(recordingService, never()).forceUpsert(any());
        }

        @Test
        @DisplayName("Should try to get duration from ffmpeg when metadata duration is null")
        void runGetDurationFromFfmpegWhenMetadataDurationIsNull() {
            UUID recordingId = UUID.randomUUID();
            CaptureSessionDTO captureSessionDto = new CaptureSessionDTO();
            captureSessionDto.setId(UUID.randomUUID());
            RecordingDTO recordingDto = new RecordingDTO();
            recordingDto.setId(recordingId);
            recordingDto.setCaptureSession(captureSessionDto);
            recordingDto.setFilename("test.mp4");
            when(recordingService.findAllDurationNull()).thenReturn(List.of(recordingDto));
            when(azureFinalStorageService.doesContainerExist(recordingId.toString())).thenReturn(true);
            when(azureFinalStorageService.getRecordingDuration(recordingId)).thenReturn(null);
            when(azureFinalStorageService.doesBlobExist(recordingId.toString(), "test.mp4")).thenReturn(true);
            when(ffmpegService.getDurationFromMp4(recordingId.toString(), "test.mp4"))
                .thenReturn(Duration.ofMinutes(5));

            cleanupNullRecordingDuration.run();

            verify(ffmpegService).getDurationFromMp4(recordingId.toString(), "test.mp4");
            verify(recordingService, never()).forceUpsert(any());
        }

        @Test
        @DisplayName("Should use fallback when filename blob does not exist")
        void runGetFilenameWhenBlobDoesntExist() {
            UUID recordingId = UUID.randomUUID();
            CaptureSessionDTO captureSessionDto = new CaptureSessionDTO();
            captureSessionDto.setId(UUID.randomUUID());

            RecordingDTO recordingDto = new RecordingDTO();
            recordingDto.setId(recordingId);
            recordingDto.setCaptureSession(captureSessionDto);
            recordingDto.setFilename("missing.mp4");

            when(recordingService.findAllDurationNull()).thenReturn(List.of(recordingDto));
            when(azureFinalStorageService.doesContainerExist(recordingId.toString())).thenReturn(true);
            when(azureFinalStorageService.getRecordingDuration(recordingId)).thenReturn(null);
            when(azureFinalStorageService.doesBlobExist(recordingId.toString(), "missing.mp4")).thenReturn(false);

            String fallbackFilename = "fallback.mp4";
            when(azureFinalStorageService.getMp4FileName(recordingId.toString())).thenReturn(fallbackFilename);
            when(azureFinalStorageService.doesBlobExist(recordingId.toString(), fallbackFilename)).thenReturn(true);
            when(ffmpegService.getDurationFromMp4(recordingId.toString(), fallbackFilename))
                .thenReturn(Duration.ofMinutes(3));

            cleanupNullRecordingDuration.run();

            verify(azureFinalStorageService).getMp4FileName(recordingId.toString());
            verify(ffmpegService).getDurationFromMp4(recordingId.toString(), fallbackFilename);
            verify(recordingService, never()).forceUpsert(any());
        }
    }
}
