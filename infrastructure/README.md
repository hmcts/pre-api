# App infrastructure

Add any application specific infrastructure to the terraform files in this folder

This could be things like:
* a database
* redis
* vault
* application insights

## Contributing

Ensure that infrastructure code is formatted and automatically documented using the pre-commit hooks.
Install it with:

```shell
$ brew install pre-commit terraform-docs
$ pre-commit install
```

If you add a new hook make sure to run it against all files:
```shell
$ pre-commit run --all-files
```
<!-- BEGIN_TF_DOCS -->
## Requirements

| Name | Version |
|------|---------|
| <a name="requirement_azurerm"></a> [azurerm](#requirement\_azurerm) | 3.108.0 |

## Providers

| Name | Version |
|------|---------|
| <a name="provider_azurerm"></a> [azurerm](#provider\_azurerm) | 3.108.0 |

## Modules

| Name | Source | Version |
|------|--------|---------|
| <a name="module_apim_subscription_portal"></a> [apim\_subscription\_portal](#module\_apim\_subscription\_portal) | git@github.com:hmcts/cnp-module-api-mgmt-subscription | master |
| <a name="module_apim_subscription_powerplatform"></a> [apim\_subscription\_powerplatform](#module\_apim\_subscription\_powerplatform) | git@github.com:hmcts/cnp-module-api-mgmt-subscription | master |
| <a name="module_apim_subscription_smoketest"></a> [apim\_subscription\_smoketest](#module\_apim\_subscription\_smoketest) | git@github.com:hmcts/cnp-module-api-mgmt-subscription | master |
| <a name="module_pre-api-exception-alert"></a> [pre-api-exception-alert](#module\_pre-api-exception-alert) | git@github.com:hmcts/cnp-module-metric-alert | n/a |
| <a name="module_pre-api-liveness-alert"></a> [pre-api-liveness-alert](#module\_pre-api-liveness-alert) | git@github.com:hmcts/cnp-module-metric-alert | n/a |
| <a name="module_pre-api-mgmt-api-policy"></a> [pre-api-mgmt-api-policy](#module\_pre-api-mgmt-api-policy) | git@github.com:hmcts/cnp-module-api-mgmt-api-policy | master |
| <a name="module_pre_api"></a> [pre\_api](#module\_pre\_api) | git@github.com:hmcts/cnp-module-api-mgmt-api | master |
| <a name="module_pre_product"></a> [pre\_product](#module\_pre\_product) | git@github.com:hmcts/cnp-module-api-mgmt-product | master |

## Resources

| Name | Type |
|------|------|
| [azurerm_key_vault_secret.apim_subscription_portal_primary_key](https://registry.terraform.io/providers/hashicorp/azurerm/3.108.0/docs/resources/key_vault_secret) | resource |
| [azurerm_key_vault_secret.apim_subscription_portal_secondary_key](https://registry.terraform.io/providers/hashicorp/azurerm/3.108.0/docs/resources/key_vault_secret) | resource |
| [azurerm_key_vault_secret.apim_subscription_powerplatform_primary_key](https://registry.terraform.io/providers/hashicorp/azurerm/3.108.0/docs/resources/key_vault_secret) | resource |
| [azurerm_key_vault_secret.apim_subscription_powerplatform_secondary_key](https://registry.terraform.io/providers/hashicorp/azurerm/3.108.0/docs/resources/key_vault_secret) | resource |
| [azurerm_key_vault_secret.apim_subscription_smoketest_primary_key](https://registry.terraform.io/providers/hashicorp/azurerm/3.108.0/docs/resources/key_vault_secret) | resource |
| [azurerm_key_vault_secret.apim_subscription_smoketest_secondary_key](https://registry.terraform.io/providers/hashicorp/azurerm/3.108.0/docs/resources/key_vault_secret) | resource |
| [azurerm_application_insights.app_insights](https://registry.terraform.io/providers/hashicorp/azurerm/3.108.0/docs/data-sources/application_insights) | data source |
| [azurerm_key_vault.keyvault](https://registry.terraform.io/providers/hashicorp/azurerm/3.108.0/docs/data-sources/key_vault) | data source |
| [azurerm_monitor_action_group.action_group](https://registry.terraform.io/providers/hashicorp/azurerm/3.108.0/docs/data-sources/monitor_action_group) | data source |
| [azurerm_resource_group.rg](https://registry.terraform.io/providers/hashicorp/azurerm/3.108.0/docs/data-sources/resource_group) | data source |

## Inputs

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|:--------:|
| <a name="input_common_tags"></a> [common\_tags](#input\_common\_tags) | n/a | `map(string)` | n/a | yes |
| <a name="input_component"></a> [component](#input\_component) | n/a | `any` | n/a | yes |
| <a name="input_env"></a> [env](#input\_env) | n/a | `any` | n/a | yes |
| <a name="input_location"></a> [location](#input\_location) | n/a | `string` | `"UK South"` | no |
| <a name="input_product"></a> [product](#input\_product) | n/a | `any` | n/a | yes |
| <a name="input_subscription"></a> [subscription](#input\_subscription) | n/a | `any` | n/a | yes |
<!-- END_TF_DOCS -->
