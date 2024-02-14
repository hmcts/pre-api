package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = InviteService.class)
@SuppressWarnings({"PMD.LawOfDemeter", "PMD.TooManyMethods"})
class InviteServiceTest {
    @Autowired
    private InviteService inviteService;

    @BeforeAll
    static void setUp() {

    }
}
