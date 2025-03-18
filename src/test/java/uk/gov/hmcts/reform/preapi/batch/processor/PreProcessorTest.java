// package uk.gov.hmcts.reform.preapi.batch.processor;

// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.mockito.InjectMocks;
// import org.mockito.Mock;
// import org.mockito.MockitoAnnotations;
// import org.springframework.data.redis.core.RedisTemplate;
// import org.springframework.data.redis.core.ValueOperations;
// import uk.gov.hmcts.reform.preapi.entities.Case;
// import uk.gov.hmcts.reform.preapi.entities.Court;
// import uk.gov.hmcts.reform.preapi.entities.User;
// import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
// import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
// import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

// import java.util.Arrays;
// import java.util.List;
// import java.util.UUID;

// import static org.mockito.Mockito.times;
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;

// class PreProcessorTest {

//     @Mock
//     private RedisTemplate<String, Object> redisTemplate;

//     @Mock
//     private ValueOperations<String, Object> valueOperations;

//     @Mock
//     private CourtRepository courtRepository;

//     @Mock
//     private CaseRepository caseRepository;

//     @Mock
//     private UserRepository userRepository;

//     @InjectMocks
//     private PreProcessor preProcessor;

//     @BeforeEach
//     void setUp() {
//         MockitoAnnotations.openMocks(this);  
//         when(redisTemplate.opsForValue()).thenReturn(valueOperations);
//     }

//     @Test
//     void testInitialize() {
//         Court court = new Court();
//         court.setName("Court 1");
//         court.setId(UUID.randomUUID()); 

//         Case caseEntity = new Case();
//         caseEntity.setReference("Case123");
//         caseEntity.setId(UUID.randomUUID());  

//         User user = new User();
//         user.setEmail("user@test.com");
//         user.setId(UUID.randomUUID());  

//         List<Court> courts = Arrays.asList(court);
//         List<Case> cases = Arrays.asList(caseEntity);
//         List<User> users = Arrays.asList(user);

//         when(courtRepository.findAll()).thenReturn(courts);
//         when(caseRepository.findAll()).thenReturn(cases);
//         when(userRepository.findAll()).thenReturn(users);

//         preProcessor.initialize();

//         verify(valueOperations, times(1)).set("batch-preprocessor:court:Court 1", court.getId().toString());
//         verify(valueOperations, times(1)).set("batch-preprocessor:case:Case123", caseEntity.getId().toString());
//         verify(valueOperations, times(1)).set("batch-preprocessor:user:user@test.com", user.getId().toString());

//         verify(courtRepository, times(1)).findAll();
//         verify(caseRepository, times(1)).findAll();
//         verify(userRepository, times(1)).findAll();
//     }

//     @Test
//     void testLoadCourtData() {
//         Court court = new Court();
//         court.setName("Court 1");
//         court.setId(UUID.randomUUID());
//         List<Court> courts = Arrays.asList(court);

//         when(courtRepository.findAll()).thenReturn(courts);

//         preProcessor.loadCourtData();

//         verify(valueOperations, times(1)).set("batch-preprocessor:court:Court 1", court.getId().toString());
//     }


//     @Test
//     void testLoadCaseData() {
//         Case caseEntity = new Case();
//         caseEntity.setReference("Case123");
//         caseEntity.setId(UUID.randomUUID());
//         List<Case> cases = Arrays.asList(caseEntity);

//         when(caseRepository.findAll()).thenReturn(cases);

//         preProcessor.loadCaseData();

//         verify(valueOperations, times(1)).set("batch-preprocessor:case:Case123", caseEntity.getId().toString());
//     }

//     @Test
//     void testLoadUserData() {
//         User user = new User();
//         user.setEmail("user@test.com");
//         user.setId(UUID.randomUUID());
//         List<User> users = Arrays.asList(user);

//         when(userRepository.findAll()).thenReturn(users);

//         preProcessor.loadUserData();

//         verify(valueOperations, times(1)).set("batch-preprocessor:user:user@test.com", user.getId().toString());
//     }
// }
