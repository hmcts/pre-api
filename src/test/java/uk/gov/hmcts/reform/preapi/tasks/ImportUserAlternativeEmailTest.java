package uk.gov.hmcts.reform.preapi.tasks;

import com.microsoft.graph.models.ObjectIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.B2CGraphService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    private B2CGraphService b2cGraphService;
    private static final String ROBOT_USER_EMAIL = "robot@example.com";
    private static final String TEST_CONTAINER = "test-container";
    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() throws Exception {
        userService = mock(UserService.class);
        userAuthenticationService = mock(UserAuthenticationService.class);
        azureVodafoneStorageService = mock(AzureVodafoneStorageService.class);
        b2cGraphService = mock(B2CGraphService.class);

        task = new ImportUserAlternativeEmail(
            userService,
            userAuthenticationService,
            ROBOT_USER_EMAIL,
            azureVodafoneStorageService,
            b2cGraphService
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

        com.microsoft.graph.models.User b2cUser = new com.microsoft.graph.models.User();
        b2cUser.setId("b2c-user-id");
        ObjectIdentity primaryIdentity = new ObjectIdentity();
        primaryIdentity.setSignInType("emailAddress");
        primaryIdentity.setIssuer("contoso.onmicrosoft.com");
        primaryIdentity.setIssuerAssignedId("test@example.com");
        List<ObjectIdentity> identities = new ArrayList<>();
        identities.add(primaryIdentity);
        b2cUser.setIdentities(identities);

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
        when(userService.findByAlternativeEmail("test@example.com.cjsm.net"))
            .thenReturn(Optional.empty());
        when(b2cGraphService.findUserByPrimaryEmail("test@example.com"))
            .thenReturn(Optional.of(b2cUser));

        task.run();

        verify(azureVodafoneStorageService, times(1)).fetchSingleXmlBlob(TEST_CONTAINER,
            "alternative_email_import.csv");
        verify(userService, times(1)).findByOriginalEmail("test@example.com");
        verify(userService, times(1)).findByAlternativeEmail("test@example.com.cjsm.net");
        verify(b2cGraphService, times(1)).findUserByPrimaryEmail("test@example.com");
        verify(b2cGraphService, times(1)).updateUserIdentities(eq("b2c-user-id"), anyList());
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
        when(userService.findByAlternativeEmail("existing@example.com.cjsm.net"))
            .thenReturn(Optional.of(otherUser));

        task.run();

        verify(userService, times(1)).findByOriginalEmail("test@example.com");
        verify(userService, times(1)).findByAlternativeEmail("existing@example.com.cjsm.net");
        verify(userService, never()).updateAlternativeEmail(any(), anyString());
    }

    @DisplayName("Should handle alternative email already exists for same user")
    @Test
    void runHandlesAlternativeEmailExistsForSameUser() {

        com.microsoft.graph.models.User b2cUser = new com.microsoft.graph.models.User();
        b2cUser.setId("b2c-user-id");
        ObjectIdentity primaryIdentity = new ObjectIdentity();
        primaryIdentity.setSignInType("emailAddress");
        primaryIdentity.setIssuer("contoso.onmicrosoft.com");
        primaryIdentity.setIssuerAssignedId("test@example.com");
        List<ObjectIdentity> identities = new ArrayList<>();
        identities.add(primaryIdentity);
        b2cUser.setIdentities(identities);

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
        when(userService.findByAlternativeEmail("existing@example.com.cjsm.net"))
            .thenReturn(Optional.of(testUser));
        when(b2cGraphService.findUserByPrimaryEmail("test@example.com"))
            .thenReturn(Optional.of(b2cUser));

        task.run();

        verify(userService, times(1)).findByOriginalEmail("test@example.com");
        verify(userService, times(1)).findByAlternativeEmail("existing@example.com.cjsm.net");
        verify(b2cGraphService, times(1)).findUserByPrimaryEmail("test@example.com");
        verify(b2cGraphService, times(1)).updateUserIdentities(eq("b2c-user-id"), anyList());
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

        User testUser2 = new User();
        testUser2.setId(UUID.randomUUID());
        testUser2.setEmail("test2@example.com");
        testUser2.setFirstName("Test2");
        testUser2.setLastName("User");

        com.microsoft.graph.models.User b2cUser1 = new com.microsoft.graph.models.User();
        b2cUser1.setId("b2c-user-id-1");
        ObjectIdentity primaryIdentity1 = new ObjectIdentity();
        primaryIdentity1.setSignInType("emailAddress");
        primaryIdentity1.setIssuer("contoso.onmicrosoft.com");
        primaryIdentity1.setIssuerAssignedId("test@example.com");
        List<ObjectIdentity> identities1 = new ArrayList<>();
        identities1.add(primaryIdentity1);
        b2cUser1.setIdentities(identities1);

        com.microsoft.graph.models.User b2cUser2 = new com.microsoft.graph.models.User();
        b2cUser2.setId("b2c-user-id-2");
        ObjectIdentity primaryIdentity2 = new ObjectIdentity();
        primaryIdentity2.setSignInType("emailAddress");
        primaryIdentity2.setIssuer("contoso.onmicrosoft.com");
        primaryIdentity2.setIssuerAssignedId("test2@example.com");
        List<ObjectIdentity> identities2 = new ArrayList<>();
        identities2.add(primaryIdentity2);
        b2cUser2.setIdentities(identities2);


        String csvContent = """
            email,alternativeEmail
            test@example.com,test@example.com.cjsm.net
            test2@example.com,test2@example.com.cjsm.net
            """;
        InputStreamResource blobResource = new InputStreamResource(
            new ByteArrayInputStream(csvContent.getBytes())
        );
        when(azureVodafoneStorageService.fetchSingleXmlBlob(TEST_CONTAINER, "alternative_email_import.csv"))
            .thenReturn(blobResource);
        when(userService.findByOriginalEmail("test@example.com"))
            .thenReturn(Optional.of(testUser));
        when(userService.findByOriginalEmail("test2@example.com"))
            .thenReturn(Optional.of(testUser2));
        when(userService.findByAlternativeEmail("test@example.com.cjsm.net"))
            .thenReturn(Optional.empty());
        when(userService.findByAlternativeEmail("test2@example.com.cjsm.net"))
            .thenReturn(Optional.empty());
        when(b2cGraphService.findUserByPrimaryEmail("test@example.com"))
            .thenReturn(Optional.of(b2cUser1));
        when(b2cGraphService.findUserByPrimaryEmail("test2@example.com"))
            .thenReturn(Optional.of(b2cUser2));

        task.run();

        verify(userService, times(1)).findByOriginalEmail("test@example.com");
        verify(userService, times(1)).findByOriginalEmail("test2@example.com");
        verify(b2cGraphService, times(1)).findUserByPrimaryEmail("test@example.com");
        verify(b2cGraphService, times(1)).findUserByPrimaryEmail("test2@example.com");
        verify(b2cGraphService, times(2)).updateUserIdentities(anyString(), anyList());
        verify(userService, times(1)).updateAlternativeEmail(testUser.getId(), "test@example.com.cjsm.net");
        verify(userService, times(1)).updateAlternativeEmail(testUser2.getId(), "test2@example.com.cjsm.net");
    }

    @DisplayName("Should handle B2C user not found")
    @Test
    void runHandlesB2CUserNotFound() {
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
        when(userService.findByAlternativeEmail("test@example.com.cjsm.net"))
            .thenReturn(Optional.empty());
        when(b2cGraphService.findUserByPrimaryEmail("test@example.com"))
            .thenReturn(Optional.empty());

        task.run();

        verify(userService, times(1)).findByOriginalEmail("test@example.com");
        verify(userService, times(1)).findByAlternativeEmail("test@example.com.cjsm.net");
        verify(b2cGraphService, times(1)).findUserByPrimaryEmail("test@example.com");
        verify(b2cGraphService, never()).updateUserIdentities(anyString(), anyList());
        verify(userService, never()).updateAlternativeEmail(any(), anyString());
    }

    @DisplayName("Should handle B2C update failure")
    @Test
    void runHandlesB2CUpdateFailure() {

        com.microsoft.graph.models.User b2cUser = new com.microsoft.graph.models.User();
        b2cUser.setId("b2c-user-id");
        ObjectIdentity primaryIdentity = new ObjectIdentity();
        primaryIdentity.setSignInType("emailAddress");
        primaryIdentity.setIssuer("contoso.onmicrosoft.com");
        primaryIdentity.setIssuerAssignedId("test@example.com");
        List<ObjectIdentity> identities = new ArrayList<>();
        identities.add(primaryIdentity);
        b2cUser.setIdentities(identities);

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
        when(userService.findByAlternativeEmail("test@example.com.cjsm.net"))
            .thenReturn(Optional.empty());
        when(b2cGraphService.findUserByPrimaryEmail("test@example.com"))
            .thenReturn(Optional.of(b2cUser));
        doThrow(new RuntimeException("B2C update failed"))
            .when(b2cGraphService).updateUserIdentities(eq("b2c-user-id"), anyList());

        task.run();

        verify(userService, times(1)).findByOriginalEmail("test@example.com");
        verify(userService, times(1)).findByAlternativeEmail("test@example.com.cjsm.net");
        verify(b2cGraphService, times(1)).findUserByPrimaryEmail("test@example.com");
        verify(b2cGraphService, times(1)).updateUserIdentities(eq("b2c-user-id"), anyList());
        verify(userService, never()).updateAlternativeEmail(any(), anyString());
    }

    @DisplayName("Should skip when alternative email already exists as B2C identity")
    @Test
    void runSkipsWhenAlternativeEmailExistsInB2C() {

        com.microsoft.graph.models.User b2cUser = new com.microsoft.graph.models.User();
        b2cUser.setId("b2c-user-id");
        ObjectIdentity primaryIdentity = new ObjectIdentity();
        primaryIdentity.setSignInType("emailAddress");
        primaryIdentity.setIssuer("contoso.onmicrosoft.com");
        primaryIdentity.setIssuerAssignedId("test@example.com");
        List<ObjectIdentity> identities = new ArrayList<>();
        identities.add(primaryIdentity);
        // Alternative email already exists as an identity
        ObjectIdentity alternativeIdentity = new ObjectIdentity();
        alternativeIdentity.setSignInType("emailAddress");
        alternativeIdentity.setIssuer("contoso.onmicrosoft.com");
        alternativeIdentity.setIssuerAssignedId("test@example.com.cjsm.net");
        identities.add(alternativeIdentity);
        b2cUser.setIdentities(identities);

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
        when(userService.findByAlternativeEmail("test@example.com.cjsm.net"))
            .thenReturn(Optional.empty());
        when(b2cGraphService.findUserByPrimaryEmail("test@example.com"))
            .thenReturn(Optional.of(b2cUser));

        task.run();

        verify(userService, times(1)).findByOriginalEmail("test@example.com");
        verify(userService, times(1)).findByAlternativeEmail("test@example.com.cjsm.net");
        verify(b2cGraphService, times(1)).findUserByPrimaryEmail("test@example.com");
        // Should not call updateUserIdentities since identity already exists
        verify(b2cGraphService, never()).updateUserIdentities(anyString(), anyList());
        // Should still update local DB
        verify(userService, times(1)).updateAlternativeEmail(testUser.getId(), "test@example.com.cjsm.net");
    }

    @DisplayName("Should handle B2C user with null identities")
    @Test
    void runHandlesB2CUserWithNullIdentities() {
        String csvContent = """
            email,alternativeEmail
            test@example.com,test@example.com.cjsm.net
            """;
        InputStreamResource blobResource = new InputStreamResource(
            new ByteArrayInputStream(csvContent.getBytes())
        );

        com.microsoft.graph.models.User b2cUser = new com.microsoft.graph.models.User();
        b2cUser.setId("b2c-user-id");
        b2cUser.setIdentities(null);

        when(azureVodafoneStorageService.fetchSingleXmlBlob(TEST_CONTAINER, "alternative_email_import.csv"))
            .thenReturn(blobResource);
        when(userService.findByOriginalEmail("test@example.com"))
            .thenReturn(Optional.of(testUser));
        when(userService.findByAlternativeEmail("test@example.com.cjsm.net"))
            .thenReturn(Optional.empty());
        when(b2cGraphService.findUserByPrimaryEmail("test@example.com"))
            .thenReturn(Optional.of(b2cUser));

        task.run();

        verify(userService, times(1)).findByOriginalEmail("test@example.com");
        verify(userService, times(1)).findByAlternativeEmail("test@example.com.cjsm.net");
        verify(b2cGraphService, times(1)).findUserByPrimaryEmail("test@example.com");
        verify(b2cGraphService, times(1)).updateUserIdentities(eq("b2c-user-id"), anyList());
        verify(userService, times(1)).updateAlternativeEmail(testUser.getId(), "test@example.com.cjsm.net");
    }
}
