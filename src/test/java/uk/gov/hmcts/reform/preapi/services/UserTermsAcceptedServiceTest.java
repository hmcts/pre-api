package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.entities.UserTermsAccepted;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.TermsAndConditionsRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserTermsAcceptedRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = UserTermsAcceptedService.class)
public class UserTermsAcceptedServiceTest {
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private TermsAndConditionsRepository termsAndConditionsRepository;

    @MockitoBean
    private UserTermsAcceptedRepository userTermsAcceptedRepository;

    @MockitoBean
    CacheManager cacheManager;

    @Autowired
    private UserTermsAcceptedService userTermsAcceptedService;

    @Test
    @DisplayName("Should successfully accept terms and conditions for a user")
    public void acceptTermsAndConditionsSuccess() {
        var user = new User();
        user.setId(UUID.randomUUID());
        mockUser(user);
        var terms = new TermsAndConditions();
        terms.setId(UUID.randomUUID());

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(termsAndConditionsRepository.findById(terms.getId())).thenReturn(Optional.of(terms));

        userTermsAcceptedService.acceptTermsAndConditions(terms.getId());

        verify(userRepository, times(1)).findById(user.getId());
        verify(termsAndConditionsRepository, times(1)).findById(terms.getId());
        verify(userTermsAcceptedRepository, times(1)).save(any(UserTermsAccepted.class));
        verify(cacheManager, times(1)).getCache("users");
    }

    @Test
    @DisplayName("Should throw not found when user doesn't exist (shouldn't happen)")
    public void acceptTermsAndConditionsUserNotFound() {
        var user = new User();
        user.setId(UUID.randomUUID());
        mockUser(user);
        var terms = new TermsAndConditions();
        terms.setId(UUID.randomUUID());

        when(userRepository.findById(user.getId())).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> userTermsAcceptedService.acceptTermsAndConditions(terms.getId())
        ).getMessage();
        assertThat(message).isEqualTo("Not found: User: " + user.getId());

        verify(userRepository, times(1)).findById(user.getId());
        verify(termsAndConditionsRepository, never()).findById(any());
        verify(userTermsAcceptedRepository, never()).save(any());
        verify(cacheManager, never()).getCache("users");
    }

    @Test
    @DisplayName("Should throw not found when terms and conditions doesn't exist")
    public void acceptTermsAndConditionsTsCsNotFound() {
        var user = new User();
        user.setId(UUID.randomUUID());
        mockUser(user);
        var terms = new TermsAndConditions();
        terms.setId(UUID.randomUUID());

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(termsAndConditionsRepository.findById(terms.getId())).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> userTermsAcceptedService.acceptTermsAndConditions(terms.getId())
        ).getMessage();
        assertThat(message).isEqualTo("Not found: TermsAndConditions: " + terms.getId());

        verify(userRepository, times(1)).findById(user.getId());
        verify(termsAndConditionsRepository, times(1)).findById(terms.getId());
        verify(userTermsAcceptedRepository, never()).save(any());
        verify(cacheManager, never()).getCache("users");
    }

    private void mockUser(User user) {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        when(mockAuth.getUserId()).thenReturn(user.getId());

        SecurityContextHolder.getContext().setAuthentication(mockAuth);
    }
}
