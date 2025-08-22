package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.dto.VerifyEmailRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.email.IEmailService;
import uk.gov.hmcts.reform.preapi.services.UserService;

@RestController
@RequestMapping("/b2c")
public class B2CController {

    private final UserService userService;
    private final EmailServiceFactory emailServiceFactory;
    private final String emailServiceName;

    public B2CController(@Autowired UserService userService,
                         @Autowired EmailServiceFactory emailServiceFactory,
                         @Value("${email.service}") String emailServiceName) {
        this.userService = userService;
        this.emailServiceFactory = emailServiceFactory;
        this.emailServiceName = emailServiceName;
    }

    @PostMapping("/email-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "postEmailVerification",
        summary = "Trigger an email verification email to be sent out via gov notify"
    )
    public void postEmailVerification(@Valid @RequestBody VerifyEmailRequestDTO request) {
        BaseUserDTO user = userService.findByEmail(request.getEmail()).getUser();
        IEmailService emailService = this.emailServiceFactory.getEnabledEmailService(emailServiceName);
        emailService.emailVerification(request.getEmail(),
                                       user.getFirstName(),
                                       user.getLastName(),
                                       request.getVerificationCode());
    }
}
