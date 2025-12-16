resource "azurerm_api_management_api_diagnostic" "api_mgmt_logs" {
  identifier = "applicationinsights"
  resource_group_name = "ss-${var.env}-network-rg"
  api_management_name = "sds-api-mgmt-${var.env}"
  api_name = module.pre_api[0].name
  api_management_logger_id = "/subscriptions/${data.azurerm_client_config.current.subscription_id}/resourceGroups/ss-${var.env}-network-rg/providers/Microsoft.ApiManagement/service/sds-api-mgmt-${var.env}/loggers/sds-api-mgmt-${var.env}-logger"

  sampling_percentage = 100.0
  always_log_errors = true
  log_client_ip = true
  verbosity = "verbose"
  http_correlation_protocol = "W3C"

  frontend_request {
    body_bytes = 8192
    headers_to_log = [
      "content-type",
      "accept",
      "origin",
      "X-User-Id"
    ]
  }

  frontend_response {
    body_bytes = 8192
    headers_to_log = [
      "content-type",
      "accept",
      "origin"
    ]
  }

  backend_request {
    body_bytes = 8192
    headers_to_log = [
      "content-type",
      "accept",
      "origin",
      "X-User-Id"
    ]
  }

  backend_response {
    body_bytes = 8192
    headers_to_log = [
      "content-type",
      "accept",
      "origin"
    ]
  }

  depends_on = [module.pre_api[0].azurerm_api_management_api.api]
}
