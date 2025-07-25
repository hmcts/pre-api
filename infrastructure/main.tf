provider "azurerm" {
  features {}
}

locals {
  app_name = "pre-api"
  // rather than removing this and the counts I've left it in so that we can disable an env easily in the future
  // without deleting the resource as moving from a count to no count will delete and recreate the resource
  // env_to_deploy = var.env != "prod" ? 1 : 0
  env_to_deploy    = 1
  env_long_name    = var.env == "sbox" ? "sandbox" : var.env == "stg" ? "staging" : var.env
  apim_service_url = var.env == "prod" ? "https://pre-api.platform.hmcts.net" : "https://pre-api.${local.env_long_name}.platform.hmcts.net"
}

data "azurerm_client_config" "current" {}

data "azurerm_resource_group" "rg" {
  name = "pre-${var.env}"
}

data "azurerm_key_vault" "keyvault" {
  name                = "pre-hmctskv-${var.env}"
  resource_group_name = data.azurerm_resource_group.rg.name
}

module "pre_product" {
  count                 = local.env_to_deploy
  source                = "git@github.com:hmcts/cnp-module-api-mgmt-product?ref=master"
  api_mgmt_name         = "sds-api-mgmt-${var.env}"
  api_mgmt_rg           = "ss-${var.env}-network-rg"
  approval_required     = false
  name                  = local.app_name
  published             = true
  subscription_required = true
}

module "pre_api" {
  count                 = local.env_to_deploy
  source                = "git@github.com:hmcts/cnp-module-api-mgmt-api?ref=master"
  name                  = "pre-api"
  api_mgmt_rg           = "ss-${var.env}-network-rg"
  api_mgmt_name         = "sds-api-mgmt-${var.env}"
  display_name          = "Pre Recorded Evidence API"
  revision              = "113"
  product_id            = module.pre_product[0].product_id
  path                  = "pre-api"
  service_url           = local.apim_service_url
  swagger_url           = "https://raw.githubusercontent.com/hmcts/cnp-api-docs/master/docs/specs/pre-api.json"
  content_format        = "openapi+json-link"
  protocols             = ["http", "https"]
  subscription_required = true
}

module "apim_subscription_smoketest" {
  count            = local.env_to_deploy
  sub_display_name = "pre-api smoke test subscription"
  source           = "git@github.com:hmcts/cnp-module-api-mgmt-subscription?ref=master"
  api_mgmt_name    = "sds-api-mgmt-${var.env}"
  api_mgmt_rg      = "ss-${var.env}-network-rg"
  state            = "active"
  allow_tracing    = var.env == "stg" || var.env == "demo" ? true : false
}
resource "azurerm_key_vault_secret" "apim_subscription_smoketest_primary_key" {
  count        = local.env_to_deploy
  name         = "apim-sub-smoketest-primary-key"
  value        = module.apim_subscription_smoketest[0].subscription_primary_key
  key_vault_id = data.azurerm_key_vault.keyvault.id
}
resource "azurerm_key_vault_secret" "apim_subscription_smoketest_secondary_key" {
  count        = local.env_to_deploy
  name         = "apim-sub-smoketest-secondary-key"
  value        = module.apim_subscription_smoketest[0].subscription_secondary_key
  key_vault_id = data.azurerm_key_vault.keyvault.id
}

module "apim_subscription_powerplatform" {
  count            = local.env_to_deploy
  sub_display_name = "PRE Power Platform subscription"
  source           = "git@github.com:hmcts/cnp-module-api-mgmt-subscription?ref=master"
  api_mgmt_name    = "sds-api-mgmt-${var.env}"
  api_mgmt_rg      = "ss-${var.env}-network-rg"
  state            = "active"
  allow_tracing    = var.env == "stg" || var.env == "demo" ? true : false
}
resource "azurerm_key_vault_secret" "apim_subscription_powerplatform_primary_key" {
  count        = local.env_to_deploy
  name         = "apim-sub-powerplatform-primary-key"
  value        = module.apim_subscription_powerplatform[0].subscription_primary_key
  key_vault_id = data.azurerm_key_vault.keyvault.id
}
resource "azurerm_key_vault_secret" "apim_subscription_powerplatform_secondary_key" {
  count        = local.env_to_deploy
  name         = "apim-sub-powerplatform-secondary-key"
  value        = module.apim_subscription_powerplatform[0].subscription_secondary_key
  key_vault_id = data.azurerm_key_vault.keyvault.id
}

module "apim_subscription_portal" {
  count            = local.env_to_deploy
  sub_display_name = "PRE Portal subscription"
  source           = "git@github.com:hmcts/cnp-module-api-mgmt-subscription?ref=master"
  api_mgmt_name    = "sds-api-mgmt-${var.env}"
  api_mgmt_rg      = "ss-${var.env}-network-rg"
  state            = "active"
  allow_tracing    = var.env == "stg" || var.env == "demo" ? true : false
}
resource "azurerm_key_vault_secret" "apim_subscription_portal_primary_key" {
  count        = local.env_to_deploy
  name         = "apim-sub-portal-primary-key"
  value        = module.apim_subscription_portal[0].subscription_primary_key
  key_vault_id = data.azurerm_key_vault.keyvault.id
}
resource "azurerm_key_vault_secret" "apim_subscription_portal_secondary_key" {
  count        = local.env_to_deploy
  name         = "apim-sub-portal-secondary-key"
  value        = module.apim_subscription_portal[0].subscription_secondary_key
  key_vault_id = data.azurerm_key_vault.keyvault.id
}

module "apim_subscription_editvm" {
  count            = local.env_to_deploy
  sub_display_name = "PRE Edit VM subscription"
  source           = "git@github.com:hmcts/cnp-module-api-mgmt-subscription?ref=master"
  api_mgmt_name    = "sds-api-mgmt-${var.env}"
  api_mgmt_rg      = "ss-${var.env}-network-rg"
  state            = "active"
  allow_tracing    = var.env == "stg" || var.env == "demo" ? true : false
}
resource "azurerm_key_vault_secret" "apim_subscription_editvm_primary_key" {
  count        = local.env_to_deploy
  name         = "apim-sub-editvm-primary-key"
  value        = module.apim_subscription_editvm[0].subscription_primary_key
  key_vault_id = data.azurerm_key_vault.keyvault.id
}
resource "azurerm_key_vault_secret" "apim_subscription_editvm_secondary_key" {
  count        = local.env_to_deploy
  name         = "apim-sub-editvm-secondary-key"
  value        = module.apim_subscription_editvm[0].subscription_secondary_key
  key_vault_id = data.azurerm_key_vault.keyvault.id
}

module "pre-api-mgmt-api-policy" {
  count         = local.env_to_deploy
  source        = "git@github.com:hmcts/cnp-module-api-mgmt-api-policy?ref=master"
  api_name      = module.pre_api[0].name
  api_mgmt_name = "sds-api-mgmt-${var.env}"
  api_mgmt_rg   = "ss-${var.env}-network-rg"

  api_policy_xml_content = <<XML
<policies>
    <inbound>
        <base />
        <set-backend-service base-url="${local.apim_service_url}" />
        <cors allow-credentials="false">
            <allowed-origins>
                <origin>*</origin>
            </allowed-origins>
            <allowed-methods>
                <method>*</method>
            </allowed-methods>
            <allowed-headers>
                <header>*</header>
            </allowed-headers>
        </cors>
    </inbound>
    <backend>
        <base />
    </backend>
    <outbound>
        <base />
    </outbound>
    <on-error>
        <base />
    </on-error>
</policies>
XML
}
