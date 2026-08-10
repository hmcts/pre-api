package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.TermsAndConditionsRepository;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = TermsAndConditionsService.class,
    properties = {
        "feature-flags.dynatrace-terms-and-conditions.enabled=true",
        "feature-flags.dynatrace-terms-and-conditions.cut-off-date=2026-07-15"
    })
public class TermsAndConditionsServiceTest {
    @MockitoBean
    private TermsAndConditionsRepository termsAndConditionsRepository;

    @Autowired
    private TermsAndConditionsService underTest;

    @AfterEach
    void resetTermsFeatureFlags() {
        ReflectionTestUtils.setField(termsAndConditionsService, "isDynatraceAppTermsEnabled", true);
        ReflectionTestUtils
            .setField(termsAndConditionsService, "cutOffDate", LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("Should get the latest app terms and conditions and return model")
    void getLatestAppTermsAndConditionsSuccess() {
        var termsAndConditions =
            HelperFactory.createTermsAndConditions(TermsAndConditionsType.APP, "some content");
        termsAndConditions.setCreatedAt(Timestamp.from(Instant.now()));

        when(termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.APP))
            .thenReturn(Optional.of(termsAndConditions));

        var model = underTest.getLatestTermsAndConditionsByType(TermsAndConditionsType.APP);

        assertThat(model.getId()).isEqualTo(termsAndConditions.getId());
        assertThat(model.getType()).isEqualTo(termsAndConditions.getType());
        assertThat(model.getHtml()).isEqualTo(termsAndConditions.getContent());
        assertThat(model.getCreatedAt()).isEqualTo(termsAndConditions.getCreatedAt());

        verify(termsAndConditionsRepository,
               times(1)).findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.APP);
    }

    @Test
    @DisplayName("Should get the latest portal terms and conditions and return model")
    void getLatestPortalTermsAndConditionsSuccess() {
        var termsAndConditions =
            HelperFactory.createTermsAndConditions(TermsAndConditionsType.PORTAL, "some content");
        termsAndConditions.setCreatedAt(Timestamp.from(Instant.now()));

        when(termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.PORTAL))
            .thenReturn(Optional.of(termsAndConditions));

        var model = underTest.getLatestTermsAndConditionsByType(TermsAndConditionsType.PORTAL);

        assertThat(model.getId()).isEqualTo(termsAndConditions.getId());
        assertThat(model.getType()).isEqualTo(termsAndConditions.getType());
        assertThat(model.getHtml()).isEqualTo(termsAndConditions.getContent());
        assertThat(model.getCreatedAt()).isEqualTo(termsAndConditions.getCreatedAt());

        verify(termsAndConditionsRepository, times(1))
            .findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.PORTAL);
    }

    @Test
    @DisplayName("Should throw exception when there are no terms matching the specified type")
    void getLatestTermsAndConditionsByTypeNotFound() {
        when(termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.APP))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.getLatestTermsAndConditionsByType(TermsAndConditionsType.APP)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Terms and conditions of type: APP");

        verify(termsAndConditionsRepository,
               times(1)).findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.APP);

    }

    @Test
    @DisplayName("Should get the latest terms and conditions")
    void getLatestTermsAndConditionsSuccess() {
        TermsAndConditions termsAndConditionsPortal = mock(TermsAndConditions.class);
        TermsAndConditions termsAndConditionsApp = mock(TermsAndConditions.class);

        when(termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.PORTAL))
            .thenReturn(Optional.of(termsAndConditionsPortal));
        when(termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.APP))
            .thenReturn(Optional.of(termsAndConditionsApp));

        Set<TermsAndConditions> allLatestTermsAndConditions = underTest.getAllLatestTermsAndConditions();
        assertThat(allLatestTermsAndConditions.size()).isEqualTo(2);
        assertThat(allLatestTermsAndConditions)
            .containsExactlyInAnyOrder(termsAndConditionsPortal, termsAndConditionsApp);
        verify(termsAndConditionsRepository, times(1))
            .findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.APP);
        verify(termsAndConditionsRepository, times(1))
            .findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.PORTAL);
        verifyNoMoreInteractions(termsAndConditionsRepository);
    }

    @Test
    @DisplayName("Should cope if there are no latest terms and conditions")
    void handleGracefullyIfNothingReturnedFromDB() {
        when(termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.PORTAL))
            .thenReturn(Optional.empty());
        when(termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.APP))
            .thenReturn(Optional.empty());

        Set<TermsAndConditions> allLatestTermsAndConditions = underTest.getAllLatestTermsAndConditions();
        assertThat(allLatestTermsAndConditions.isEmpty());
        verify(termsAndConditionsRepository, times(1))
            .findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.APP);
        verify(termsAndConditionsRepository, times(1))
            .findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.PORTAL);
        verifyNoMoreInteractions(termsAndConditionsRepository):
    }

    @Test
    @DisplayName("Should use terms before cutoff for APP when dynatrace terms flag is disabled")
    void appUsesCutoffDateWhenFlagDisabled() {
        ReflectionTestUtils.setField(termsAndConditionsService, "isDynatraceAppTermsEnabled", false);
        ReflectionTestUtils
            .setField(termsAndConditionsService, "cutOffDate", LocalDate.of(2026, 7, 15));

        var terms = HelperFactory.createTermsAndConditions(TermsAndConditionsType.APP, "some content");
        terms.setCreatedAt(Timestamp.from(Instant.now()));

        when(termsAndConditionsRepository.findFirstByTypeAndCreatedAtBeforeOrderByCreatedAtDesc(
            eq(TermsAndConditionsType.APP),
            eq(Timestamp.valueOf(LocalDate.of(2026, 7, 15).atStartOfDay()))))
            .thenReturn(Optional.of(terms));

        termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.APP);

        verify(termsAndConditionsRepository, times(1))
            .findFirstByTypeAndCreatedAtBeforeOrderByCreatedAtDesc(
                eq(TermsAndConditionsType.APP), any(Timestamp.class));
        verify(termsAndConditionsRepository, never())
            .findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.APP);
    }

    @Test
    @DisplayName("Should throw not found exception when there are no APP terms before cutoff date "
        + "and dynatrace terms flag is disabled")
    void appThrowsNotFoundWhenNoTermsBeforeCutoffAndFlagDisabled() {
        ReflectionTestUtils.setField(termsAndConditionsService, "isDynatraceAppTermsEnabled", false);
        ReflectionTestUtils
            .setField(termsAndConditionsService, "cutOffDate", LocalDate.of(2026, 7, 15));

        when(termsAndConditionsRepository.findFirstByTypeAndCreatedAtBeforeOrderByCreatedAtDesc(
            eq(TermsAndConditionsType.APP),
            eq(Timestamp.valueOf(LocalDate.of(2026, 7, 15).atStartOfDay()))))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.APP)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Terms and conditions of type: APP");
    }

    @Test
    @DisplayName("Portal should ignore dynatrace terms flag and always return the latest terms")
    void portalDoesNotUseCutoffDateWhenFlagDisabled() {
        ReflectionTestUtils.setField(termsAndConditionsService, "isDynatraceAppTermsEnabled", false);
        ReflectionTestUtils
            .setField(termsAndConditionsService, "cutOffDate", LocalDate.of(2026, 7, 15));

        var terms = HelperFactory.createTermsAndConditions(TermsAndConditionsType.PORTAL, "some content");
        terms.setCreatedAt(Timestamp.from(Instant.now()));

        when(termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(
            eq(TermsAndConditionsType.PORTAL)))
            .thenReturn(Optional.of(terms));

        termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.PORTAL);

        verify(termsAndConditionsRepository, never())
            .findFirstByTypeAndCreatedAtBeforeOrderByCreatedAtDesc(
                any(TermsAndConditionsType.class), any(Timestamp.class));
        verify(termsAndConditionsRepository, times(1))
            .findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType.PORTAL);
    }
}
