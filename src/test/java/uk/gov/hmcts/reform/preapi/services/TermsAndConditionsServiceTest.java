package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.TermsAndConditionsRepository;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = TermsAndConditionsService.class)
public class TermsAndConditionsServiceTest {
    @MockitoBean
    private TermsAndConditionsRepository termsAndConditionsRepository;

    @Autowired
    private TermsAndConditionsService termsAndConditionsService;

    @Test
    @DisplayName("Should get the latest app terms and conditions and return model")
    public void getLatestAppTermsAndConditionsSuccess() {
        var termsAndConditions = HelperFactory.createTermsAndConditions(TermsAndConditionsType.APP, "some content");
        termsAndConditions.setCreatedAt(Timestamp.from(Instant.now()));

        when(termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.APP))
            .thenReturn(Optional.of(termsAndConditions));

        var model = termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.APP);

        assertThat(model.getId()).isEqualTo(termsAndConditions.getId());
        assertThat(model.getType()).isEqualTo(termsAndConditions.getType());
        assertThat(model.getHtml()).isEqualTo(termsAndConditions.getContent());
        assertThat(model.getCreatedAt()).isEqualTo(termsAndConditions.getCreatedAt());

        verify(termsAndConditionsRepository, times(1)).findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.APP);
    }

    @Test
    @DisplayName("Should get the latest portal terms and conditions and return model")
    public void getLatestPortalTermsAndConditionsSuccess() {
        var termsAndConditions = HelperFactory.createTermsAndConditions(TermsAndConditionsType.PORTAL, "some content");
        termsAndConditions.setCreatedAt(Timestamp.from(Instant.now()));

        when(termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.PORTAL))
            .thenReturn(Optional.of(termsAndConditions));

        var model = termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.PORTAL);

        assertThat(model.getId()).isEqualTo(termsAndConditions.getId());
        assertThat(model.getType()).isEqualTo(termsAndConditions.getType());
        assertThat(model.getHtml()).isEqualTo(termsAndConditions.getContent());
        assertThat(model.getCreatedAt()).isEqualTo(termsAndConditions.getCreatedAt());

        verify(termsAndConditionsRepository, times(1))
            .findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.PORTAL);
    }

    @Test
    @DisplayName("Should throw exception when there are no terms matching the specified type")
    public void getLatestTermsAndConditionsNotFound() {
        when(termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.APP))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.APP)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Terms and conditions of type: APP");

        verify(termsAndConditionsRepository, times(1)).findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.APP);

    }
}
