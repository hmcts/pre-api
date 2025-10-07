data "azurerm_monitor_action_group" "action_group" {
  count               = var.env == "prod" || var.env == "stg" ? 1 : 0
  name                = "CriticalAlertsAction"
  resource_group_name = "${var.product}-${var.env}"
}

data "azurerm_application_insights" "app_insights" {
  name                = "pre-${var.env}-appinsights"
  resource_group_name = "${var.product}-${var.env}"
}

data "azuread_application" "pre_apim_b2c_app" {
  display_name = "${var.product}-apim-b2c-${var.env}"
}
