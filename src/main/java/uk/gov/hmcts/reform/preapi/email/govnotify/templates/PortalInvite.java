package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import java.util.Map;

public class PortalInvite extends BaseTemplate {

    @SuppressWarnings("java:S107")
    public PortalInvite(String to,
                        String firstName,
                        String lastName,
                        String portalUrl,
                        String userGuideLink,
                        String processGuideLink,
                        String faqsLink,
                        String editRequestFormLink) {
        super(
            to,
            Map.of(
                "first_name", firstName,
                "last_name", lastName,
                "portal_url", portalUrl,
                "user_guide_link", userGuideLink,
                "process_guide_link", processGuideLink,
                "faqs_link", faqsLink,
                "edit_request_form_link", editRequestFormLink
            )
        );
    }

    @Override
    public String getTemplateId() {
        return "e04adfb8-58e0-44be-ab42-bd6d896ccfb7";
    }
}
