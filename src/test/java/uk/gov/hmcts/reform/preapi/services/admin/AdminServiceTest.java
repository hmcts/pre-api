package uk.gov.hmcts.reform.preapi.services.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.admin.AdminRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AdminService.class)
public class AdminServiceTest {

    @MockitoBean
    AdminRepository adminRepository;

    @Autowired
    AdminService adminService;

    @DisplayName("Should return the correct table type if UUID exists in relevant database tables")
    @ParameterizedTest
    @EnumSource(AdminService.UuidTableType.class)
    void shouldReturnUuidTableType(AdminService.UuidTableType type) {
        UUID givenUuid = UUID.randomUUID();
        String tableName = type.name().toLowerCase();
        when(adminRepository.findUuidType(givenUuid)).thenReturn(Optional.of(tableName));

        AdminService.UuidTableType result = adminService.findUuidType(givenUuid);

        verify(adminRepository, times(1)).findUuidType(givenUuid);
        assertThat(result).isEqualTo(type);
    }

    @DisplayName("Should throw NotFoundException if UUID does not exist in relevant database tables")
    @Test
    void shouldThrowNotFoundExceptionIfUuidNotPresentInDb() {
        UUID givenUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174abc");
        String expectedMessage = "Not found: " + givenUuid + " does not exist in any relevant table";

        when(adminRepository.findUuidType(givenUuid)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(
            NotFoundException.class,
            () -> adminService.findUuidType(givenUuid)
        );

        verify(adminRepository, times(1)).findUuidType(givenUuid);
        assertThat(exception.getMessage()).isEqualTo(expectedMessage);
    }

    @DisplayName("Should throw IllegalArgumentException table type returned by database is not expected")
    @Test
    void shouldThrowIllegalArgumentExceptionWhenInvalidTableType() {
        UUID givenUuid = UUID.randomUUID();
        String invalidTableName = "not_an_expected_table_type";

        when(adminRepository.findUuidType(givenUuid)).thenReturn(Optional.of(invalidTableName));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> adminService.findUuidType(givenUuid)
        );

        verify(adminRepository, times(1)).findUuidType(givenUuid);
        assertThat(exception.getMessage()).contains("No enum constant");
        assertThat(exception.getMessage()).contains(invalidTableName.toUpperCase());
    }

    @DisplayName("Should not attempt to call database when passed a null UUID")
    @Test
    void shouldThrowExceptionWhenUuidIsNull() {
        UUID givenUuid = null;

        assertThrows(
            NullPointerException.class,
            () -> adminService.findUuidType(givenUuid)
        );

        verify(adminRepository, never()).findUuidType(givenUuid);
    }
}
