variable "region" {
  type        = string
  default     = "us-east-1"
  description = "AWS region"
}

variable "project" {
  type    = string
  default = "fdt"
}

variable "environment" {
  type        = string
  default     = "dev"
  description = "dev, staging, or prod"
}

variable "vpc_cidr" {
  type    = string
  default = "10.42.0.0/16"
}

variable "availability_zones" {
  type    = list(string)
  default = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

variable "db_username" {
  type    = string
  default = "fdt"
}
