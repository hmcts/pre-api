package uk.gov.hmcts.reform.preapi.batch.processor;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.util.List;

@Component
public class PreProcessor {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CourtRepository courtRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;

    private static final String COURT_KEY_PREFIX = "court:";
    private static final String CASE_KEY_PREFIX = "case:";
    private static final String USER_KEY_PREFIX = "user:";

    public PreProcessor(RedisTemplate<String, Object> redisTemplate,
                        CourtRepository courtRepository,
                        CaseRepository caseRepository,
                        UserRepository userRepository) {
        this.redisTemplate = redisTemplate;
        this.courtRepository = courtRepository;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
    }

    public void initialize() {
        loadCourtData();
        loadCaseData();
        loadUserData();
    }

    @Transactional
    public void loadCourtData() {
        List<Court> courts = courtRepository.findAll();
        for (Court court : courts) {
            String courtKey = COURT_KEY_PREFIX + court.getName();
            redisTemplate.opsForValue().set(courtKey, court.getId().toString());
        }
    }

    @Transactional
    public void loadCaseData() {
        List<Case> cases = caseRepository.findAll();
        for (Case acase : cases) {
            String caseKey = CASE_KEY_PREFIX + acase.getReference();
            redisTemplate.opsForValue().set(caseKey, acase.getId().toString());
        }
    }

    @Transactional
    public void loadUserData() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            String userKey = USER_KEY_PREFIX + user.getEmail();
            redisTemplate.opsForValue().set(userKey, user.getId().toString());
        }
    }

}
