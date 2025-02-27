package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import java.util.Map;

public class PortalInvite extends BaseTemplate {
    public PortalInvite(String to,
                        String firstName,
                        String lastName,
                        String portalUrl,
                        String userGuideLink,
                        String processGuideLink,
                        String faqsLink) {
        super(
            to,
            Map.of(
                "first_name", firstName,
                "last_name", lastName,
                "portal_url", portalUrl,
                "user_guide_link", userGuideLink,
                "process_guide_link", processGuideLink,
                "faqs_link", faqsLink
            )
        );
    }

    public String getTemplateId() {
        return "e04adfb8-58e0-44be-ab42-bd6d896ccfb7";
    }
}
