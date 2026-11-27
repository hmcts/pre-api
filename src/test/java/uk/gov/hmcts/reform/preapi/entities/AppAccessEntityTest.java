package uk.gov.hmcts.reform.preapi.entities;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class AppAccessEntityTest {

    @Test
    @DisplayName("Sort app access by active, then last access (descending), then role")
    void testAppAccessSorting() {
        Role superUserRole = new Role();
        superUserRole.setName("Super user");

        Role level1Role = new Role();
        level1Role.setName("Level 1");

        Role level2Role = new Role();
        level2Role.setName("Level 2");

        final Instant now = Instant.now();
        final Instant oneMinuteAgo = now.minusSeconds(60);
        final Instant twoMinutesAgo = now.minusSeconds(120);

        final AppAccess mostRecent = createAppAccess(level2Role, now, true);
        final AppAccess neverAccessed = createAppAccess(level2Role, null, true);
        final AppAccess inactive = createAppAccess(level2Role, now, false);
        final AppAccess oneMinuteAgoLevel1 = createAppAccess(level1Role, oneMinuteAgo, true);
        final AppAccess oneMinuteAgoLevel2 = createAppAccess(level2Role, oneMinuteAgo, true);
        final AppAccess oneMinuteAgoSuperUser = createAppAccess(superUserRole, oneMinuteAgo, true);
        final AppAccess twoMinutesAgoSuperUser = createAppAccess(superUserRole, twoMinutesAgo, true);

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

    private static @NonNull AppAccess createAppAccess(Role role, Instant timestamp, boolean activeStatus) {
        AppAccess appAccess = new AppAccess();
        appAccess.setRole(role);
        if (timestamp != null) {
            appAccess.setLastAccess(Timestamp.from(timestamp));
        }
        appAccess.setActive(activeStatus);
        return appAccess;
    }
}
