module "pre-api-exception-alert" {
  count             = var.env == "prod" || var.env == "stg" ? 1 : 0
  source            = "git@github.com:hmcts/cnp-module-metric-alert"
  location          = data.azurerm_application_insights.app_insights.location
  app_insights_name = data.azurerm_application_insights.app_insights.name

  alert_name  = "PRE_API_exception"
  alert_desc  = "Triggers when pre api receives at least one exception within a 15 minutes window timeframe."
  common_tags = var.common_tags

  app_insights_query = <<EOF
union exceptions, traces
| where severityLevel >= 3
| where cloud_RoleInstance startswith "pre-api-java" or cloud_RoleInstance startswith "pre-api-cron"
EOF

  frequency_in_minutes       = "15"
  time_window_in_minutes     = "15"
  severity_level             = "1"
  action_group_name          = data.azurerm_monitor_action_group.action_group[count.index].name
  custom_email_subject       = "[${var.env}] PRE API Exception"
  trigger_threshold_operator = "GreaterThan"
  trigger_threshold          = "0"
  resourcegroup_name         = "${var.product}-${var.env}"
}
