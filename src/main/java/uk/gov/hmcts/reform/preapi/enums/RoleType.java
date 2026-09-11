package uk.gov.hmcts.reform.preapi.enums;

public enum RoleType {
    ROLE_SUPER_USER("Super User"),
    ROLE_LEVEL_1("Level 1"),
    ROLE_LEVEL_2("Level 2"),
    ROLE_LEVEL_3("Level 3"),
    ROLE_LEVEL_4("Level 4");

    public final String roleLabel;

    RoleType(String roleLabel) {
        this.roleLabel = roleLabel;
    }
}
