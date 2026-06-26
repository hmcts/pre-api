variable "product" {}

variable "component" {}

variable "location" {
  default = "UK South"
}

variable "env" {}

variable "subscription" {}

variable "common_tags" {
  type = map(string)
}

variable "tenant_id" {}

variable "pre_apim_b2c_client_id" {}

variable "pre_apim_b2c_dev_client_id" {
  default = ""
}

