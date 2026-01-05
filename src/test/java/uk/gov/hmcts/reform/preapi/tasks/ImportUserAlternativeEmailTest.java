package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportUserAlternativeEmailTest {

    private ImportUserAlternativeEmail task;
    private UserService userService;
    private UserAuthenticationService userAuthenticationService;
    private AzureVodafoneStorageService azureVodafoneStorageService;
    private static final String ROBOT_USER_EMAIL = "robot@example.com";
    private static final String TEST_CONTAINER = "test-container";
    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() throws Exception {
        userService = mock(UserService.class);
        userAuthenticationService = mock(UserAuthenticationService.class);
        azureVodafoneStorageService = mock(AzureVodafoneStorageService.class);

        task = new ImportUserAlternativeEmail(
            userService,
            userAuthenticationService,
            ROBOT_USER_EMAIL,
            azureVodafoneStorageService
        );

        Field containerNameField = ImportUserAlternativeEmail.class.getDeclaredField("containerName");
        containerNameField.setAccessible(true);
        containerNameField.set(task, TEST_CONTAINER);

        Field useLocalCsvField = ImportUserAlternativeEmail.class.getDeclaredField("useLocalCsv");
        useLocalCsvField.setAccessible(true);
        useLocalCsvField.set(task, false);

        var userAuth = mock(UserAuthentication.class);
        when(userAuth.isAdmin()).thenReturn(true);
        var access = new uk.gov.hmcts.reform.preapi.dto.AccessDTO();
        var appAccess = new uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        access.setAppAccess(Set.of(appAccess));
        when(userService.findByEmail(ROBOT_USER_EMAIL)).thenReturn(access);
        when(userAuthenticationService.validateUser(appAccess.getId().toString()))
            .thenReturn(Optional.of(userAuth));

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setFirstName("Test");
        testUser.setLastName("User");

        otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setEmail("other@example.com");
        otherUser.setFirstName("Other");
        otherUser.setLastName("User");
    }

    @DisplayName("Should successfully import alternative email from Azure blob")
    @Test
    void runWithAzureBlobSuccess() {
        String csvContent = """
            email,alternativeEmail
            test@example.com,test@example.com.cjsm.net
            """;
        InputStreamResource blobResource = new InputStreamResource(
            new ByteArrayInputStream(csvContent.getBytes())
        );

        when(azureVodafoneStorageService.fetchSingleXmlBlob(TEST_CONTAINER, "alternative_email_import.csv"))
            .thenReturn(blobResource);
        when(userService.findByOriginalEmail("test@example.com"))
            .thenReturn(Optional.of(testUser));

        task.run();

        verify(azureVodafoneStorageService, times(1)).fetchSingleXmlBlob(TEST_CONTAINER, 
            "alternative_email_import.csv");
        verify(userService, times(1)).findByOriginalEmail("test@example.com");
        verify(userService, times(1)).updateAlternativeEmail(testUser.getId(), "test@example.com.cjsm.net");
    }

    @DisplayName("Should skip rows with empty alternative email")
    @Test
    void runSkipsEmptyAlternativeEmail() {
        String csvContent = """
            email,alternativeEmail
            test@example.com,
            test2@example.com,  
            """;
        InputStreamResource blobResource = new InputStreamResource(
            new ByteArrayInputStream(csvContent.getBytes())
        );

        when(azureVodafoneStorageService.fetchSingleXmlBlob(TEST_CONTAINER, "alternative_email_import.csv"))
            .thenReturn(blobResource);

        task.run();

        verify(userService, never()).findByOriginalEmail(anyString());
        verify(userService, never()).updateAlternativeEmail(any(), anyString());
    }

    @DisplayName("Should handle user not found")
    @Test
    void runHandlesUserNotFound() {
        String csvContent = """
            email,alternativeEmail
            notfound@example.com,notfound@example.com.cjsm.net
            """;
        InputStreamResource blobResource = new InputStreamResource(
            new ByteArrayInputStream(csvContent.getBytes())
        );

        when(azureVodafoneStorageService.fetchSingleXmlBlob(TEST_CONTAINER, "alternative_email_import.csv"))
            .thenReturn(blobResource);
        when(userService.findByOriginalEmail("notfound@example.com"))
            .thenReturn(Optional.empty());

        task.run();

        verify(userService, times(1)).findByOriginalEmail("notfound@example.com");
        verify(userService, never()).updateAlternativeEmail(any(), anyString());
    }

    @DisplayName("Should handle alternative email already exists for another user")
    @Test
    void runHandlesAlternativeEmailExists() {
        String csvContent = """
            email,alternativeEmail
            test@example.com,existing@example.com.cjsm.net
            """;
        InputStreamResource blobResource = new InputStreamResource(
            new ByteArrayInputStream(csvContent.getBytes())
        );

        when(azureVodafoneStorageService.fetchSingleXmlBlob(TEST_CONTAINER, "alternative_email_import.csv"))
            .thenReturn(blobResource);
        when(userService.findByOriginalEmail("test@example.com"))
            .thenReturn(Optional.of(testUser));
        when(userService.updateAlternativeEmail(testUser.getId(), "existing@example.com.cjsm.net"))
            .thenThrow(new uk.gov.hmcts.reform.preapi.exception.ConflictException(
                "Alternative email: existing@example.com.cjsm.net already exists for another user"));

        task.run();

        verify(userService, times(1)).findByOriginalEmail("test@example.com");
        verify(userService, times(1)).updateAlternativeEmail(testUser.getId(), "existing@example.com.cjsm.net");
    }

    @DisplayName("Should handle alternative email already exists for same user")
    @Test
    void runHandlesAlternativeEmailExistsForSameUser() {
        String csvContent = """
            email,alternativeEmail
            test@example.com,existing@example.com.cjsm.net
            """;
        InputStreamResource blobResource = new InputStreamResource(
            new ByteArrayInputStream(csvContent.getBytes())
        );

        when(azureVodafoneStorageService.fetchSingleXmlBlob(TEST_CONTAINER, "alternative_email_import.csv"))
            .thenReturn(blobResource);
        when(userService.findByOriginalEmail("test@example.com"))
            .thenReturn(Optional.of(testUser));

        task.run();

        verify(userService, times(1)).findByOriginalEmail("test@example.com");
        verify(userService, times(1)).updateAlternativeEmail(testUser.getId(), "existing@example.com.cjsm.net");
    }

    @DisplayName("Should handle errors during processing")
    @Test
    void runHandlesProcessingErrors() {
        String csvContent = """
            email,alternativeEmail
            test@example.com,test@example.com.cjsm.net
            """;
        InputStreamResource blobResource = new InputStreamResource(
            new ByteArrayInputStream(csvContent.getBytes())
        );

        when(azureVodafoneStorageService.fetchSingleXmlBlob(TEST_CONTAINER, "alternative_email_import.csv"))
            .thenReturn(blobResource);
        when(userService.findByOriginalEmail("test@example.com"))
            .thenThrow(new RuntimeException("Database error"));

        task.run();

        verify(userService, times(1)).findByOriginalEmail("test@example.com");
        verify(userService, never()).updateAlternativeEmail(any(), anyString());
    }

    @DisplayName("Should throw exception when CSV file not found in Azure")
    @Test
    void runThrowsExceptionWhenCsvNotFound() {
        when(azureVodafoneStorageService.fetchSingleXmlBlob(TEST_CONTAINER, "alternative_email_import.csv"))
            .thenReturn(null);

        assertThatThrownBy(() -> task.run())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to import user alternative email data");
    }

    @DisplayName("Should process multiple rows successfully")
    @Test
    void runProcessesMultipleRows() {
        String csvContent = """
            email,alternativeEmail
            test@example.com,test@example.com.cjsm.net
            test2@example.com,test2@example.com.cjsm.net
            """;

        User testUser2 = new User();
        testUser2.setId(UUID.randomUUID());
        testUser2.setEmail("test2@example.com");
        testUser2.setFirstName("Test2");
        testUser2.setLastName("User");

        InputStreamResource blobResource = new InputStreamResource(
            new ByteArrayInputStream(csvContent.getBytes())
        );
        
        when(azureVodafoneStorageService.fetchSingleXmlBlob(TEST_CONTAINER, "alternative_email_import.csv"))
            .thenReturn(blobResource);
        when(userService.findByOriginalEmail("test@example.com"))
            .thenReturn(Optional.of(testUser));
        when(userService.findByOriginalEmail("test2@example.com"))
            .thenReturn(Optional.of(testUser2));

        task.run();

        verify(userService, times(1)).findByOriginalEmail("test@example.com");
        verify(userService, times(1)).findByOriginalEmail("test2@example.com");
        verify(userService, times(1)).updateAlternativeEmail(testUser.getId(), "test@example.com.cjsm.net");
        verify(userService, times(1)).updateAlternativeEmail(testUser2.getId(), "test2@example.com.cjsm.net");
    }

    @DisplayName("Should handle invalid email format exception")
    @Test
    void runHandlesInvalidEmailFormat() {
        String csvContent = """
            email,alternativeEmail
            test@example.com,invalid@test
            """;
        InputStreamResource blobResource = new InputStreamResource(
            new ByteArrayInputStream(csvContent.getBytes())
        );

        when(azureVodafoneStorageService.fetchSingleXmlBlob(TEST_CONTAINER, "alternative_email_import.csv"))
            .thenReturn(blobResource);
        when(userService.findByOriginalEmail("test@example.com"))
            .thenReturn(Optional.of(testUser));
        when(userService.updateAlternativeEmail(testUser.getId(), "invalid@test"))
            .thenThrow(new IllegalArgumentException(
                "Alternative email format is invalid: must be a well-formed email address"));

        task.run();

        verify(userService, times(1)).findByOriginalEmail("test@example.com");
        verify(userService, times(1)).updateAlternativeEmail(testUser.getId(), "invalid@test");
    }

    @DisplayName("Should handle alternative email same as main email exception")
    @Test
    void runHandlesAlternativeEmailSameAsMainEmail() {
        String csvContent = """
            email,alternativeEmail
            test@example.com,test@example.com
            """;
        InputStreamResource blobResource = new InputStreamResource(
            new ByteArrayInputStream(csvContent.getBytes())
        );

        when(azureVodafoneStorageService.fetchSingleXmlBlob(TEST_CONTAINER, "alternative_email_import.csv"))
            .thenReturn(blobResource);
        when(userService.findByOriginalEmail("test@example.com"))
            .thenReturn(Optional.of(testUser));
        when(userService.updateAlternativeEmail(testUser.getId(), "test@example.com"))
            .thenThrow(new IllegalArgumentException(
                "Alternative email cannot be the same as the main email"));

        task.run();

        verify(userService, times(1)).findByOriginalEmail("test@example.com");
        verify(userService, times(1)).updateAlternativeEmail(testUser.getId(), "test@example.com");
    }
}
