<<<<<<<< HEAD:src/main/java/uk/gov/hmcts/reform/preapi/controllers/RootController.java
package uk.gov.hmcts.reform.preapi.controllers;
========
package uk.gov.hmcts.reform.pre-api.controllers;
>>>>>>>> 943a58c4500e409cd9c5c7378b603568d2968ef5:src/main/java/uk/gov/hmcts/reform/pre-api/controllers/RootController.java

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.ResponseEntity.ok;

/**
 * Default endpoints per application.
 */
@RestController
public class RootController {

    /**
     * Root GET endpoint.
     *
     * <p>Azure application service has a hidden feature of making requests to root endpoint when
     * "Always On" is turned on.
     * This is the endpoint to deal with that and therefore silence the unnecessary 404s as a response code.
     *
     * @return Welcome message from the service.
     */
    @GetMapping("/")
    public ResponseEntity<String> welcome() {
        return ok("Welcome to pre-api");
    }
}
