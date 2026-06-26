terraform {
  backend "azurerm" {}
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      # Temporarily pinned: do not upgrade azurerm until issue #29502 is resolved.
      # https://github.com/hashicorp/terraform-provider-azurerm/issues/29502
      version = "4.21.0"
    }
  }
}
