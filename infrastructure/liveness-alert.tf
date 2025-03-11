module "pre-api-liveness-alert" {
  count             = var.env == "prod" ? 1 : 0
  source            = "git@github.com:hmcts/cnp-module-metric-alert"
  location          = data.azurerm_application_insights.app_insights.location
  app_insights_name = data.azurerm_application_insights.app_insights.name

  alert_name  = "PRE_API_liveness"
  alert_desc  = "Triggers when pre api looks it's been down within a 30 minutes window timeframe."
  common_tags = var.common_tags

  app_insights_query = <<EOF
requests
| where name == "GET /health" and resultCode != "200"
| where cloud_RoleInstance startswith "pre-api-java"
EOF

  frequency_in_minutes       = "15"
  time_window_in_minutes     = "30"
  severity_level             = "2"
  action_group_name          = data.azurerm_monitor_action_group.action_group[count.index].name
  custom_email_subject       = "PRE API liveness"
  trigger_threshold_operator = "GreaterThan"
  trigger_threshold          = "5"
  resourcegroup_name         = "${var.product}-${var.env}"
}
