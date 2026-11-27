package uk.gov.hmcts.reform.preapi.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class AppAccessEntityTest {

    @Test
    @DisplayName("Sort app access by active, then last access (descending), then role")
    public void testAppAccessSorting() {
        Role superUserRole = new Role();
        superUserRole.setName("Super user");

        Role level1Role = new Role();
        level1Role.setName("Level 1");

        Role level2Role = new Role();
        level2Role.setName("Level 2");

        Instant now = Instant.now();
        Instant oneMinuteAgo = now.minusSeconds(60);
        Instant twoMinutesAgo = now.minusSeconds(120);

        AppAccess mostRecent = new AppAccess();
        mostRecent.setRole(level2Role);
        mostRecent.setLastAccess(Timestamp.from(now));
        mostRecent.setActive(true);

        AppAccess neverAccessed = new AppAccess();
        neverAccessed.setRole(level2Role);
        neverAccessed.setLastAccess(null);
        neverAccessed.setActive(true);

        AppAccess inactive = new AppAccess();
        inactive.setRole(level2Role);
        inactive.setLastAccess(Timestamp.from(now));
        inactive.setActive(false);

        AppAccess oneMinuteAgoLevel1 = new AppAccess();
        oneMinuteAgoLevel1.setRole(level1Role);
        oneMinuteAgoLevel1.setLastAccess(Timestamp.from(oneMinuteAgo));
        oneMinuteAgoLevel1.setActive(true);

        AppAccess oneMinuteAgoLevel2 = new AppAccess();;
        oneMinuteAgoLevel2.setRole(level2Role);
        oneMinuteAgoLevel2.setLastAccess(Timestamp.from(oneMinuteAgo));
        oneMinuteAgoLevel2.setActive(true);

        AppAccess oneMinuteAgoSuperUser = new AppAccess();
        oneMinuteAgoSuperUser.setRole(superUserRole);
        oneMinuteAgoSuperUser.setLastAccess(Timestamp.from(oneMinuteAgo));
        oneMinuteAgoSuperUser.setActive(true);

        AppAccess twoMinutesAgoSuperUser = new AppAccess();
        twoMinutesAgoSuperUser.setRole(superUserRole);
        twoMinutesAgoSuperUser.setLastAccess(Timestamp.from(twoMinutesAgo));
        twoMinutesAgoSuperUser.setActive(true);

        AppAccess[] appAccessList = new AppAccess[]{
            neverAccessed,
            oneMinuteAgoLevel2,
            inactive,
            twoMinutesAgoSuperUser,
            mostRecent,
            oneMinuteAgoLevel1,
            oneMinuteAgoSuperUser,
        };

        List<AppAccess> sortedList = Stream.of(appAccessList)
            .sorted(AppAccess.BY_ACTIVE_LAST_ACCESS_ROLE).toList();

        assertThat(sortedList.get(0)).isEqualTo(mostRecent);
        assertThat(sortedList.get(1)).isEqualTo(oneMinuteAgoSuperUser);
        assertThat(sortedList.get(2)).isEqualTo(oneMinuteAgoLevel1);
        assertThat(sortedList.get(3)).isEqualTo(oneMinuteAgoLevel2);
        assertThat(sortedList.get(4)).isEqualTo(twoMinutesAgoSuperUser);
        assertThat(sortedList.get(5)).isEqualTo(neverAccessed);
        assertThat(sortedList.get(6)).isEqualTo(inactive);
    }
}
