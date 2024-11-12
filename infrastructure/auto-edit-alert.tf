module "pre-api-auto-edit-alert" {
  count               = var.env == "prod" || var.env == "stg" ? 1 : 0
  source              = "git@github.com:hmcts/cnp-module-metric-alert"
  location            = data.azurerm_application_insights.app_insights.location
  app_insights_name   = data.azurerm_application_insights.app_insights.name

  alert_name          = "PRE_API_AutoEditingEvent"
  alert_desc          = "Triggers when automated editing event is tracked in pre-api within a 15 min window."
  common_tags         = var.common_tags

  app_insights_query  = "customEvents | where name startswith 'PerformEditRequest' | where cloud_RoleName == 'pre-api'"

  frequency_in_minutes       = "15"
  time_window_in_minutes     = "15"
  severity_level             = "3"
  action_group_name          = data.azurerm_monitor_action_group.pre-teams-wh[count.index].name
  custom_email_subject       = "[${var.env}] PRE API Automated Edit"
  trigger_threshold_operator = "GreaterThan"
  trigger_threshold          = "0"
  resourcegroup_name         = "${var.product}-${var.env}"
}
