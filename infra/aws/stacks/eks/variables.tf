variable "environment" {
  description = "dev / staging / prod。"
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment は dev / staging / prod のいずれか。"
  }
}

variable "region" {
  description = "AWS リージョン。"
  type        = string
  default     = "ap-northeast-1"
}

variable "kubernetes_version" {
  description = "Kubernetes version。 全 env で同 version を採用。"
  type        = string
  default     = "1.31"
}

variable "cluster_endpoint_public_access" {
  description = "public endpoint 有効化。 v1 では全 env false。"
  type        = bool
  default     = false
}

variable "cluster_endpoint_public_access_cidrs" {
  description = "public endpoint 許可 CIDR。 public_access = true の場合のみ有効。"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "system_node_min_size" {
  description = "system NG min size。"
  type        = number
}

variable "system_node_max_size" {
  description = "system NG max size。"
  type        = number
}

variable "system_node_desired_size" {
  description = "system NG desired size。"
  type        = number
}

variable "system_node_instance_types" {
  description = "system NG instance types。"
  type        = list(string)
}

variable "system_node_capacity_type" {
  description = "system NG capacity type (ON_DEMAND / SPOT)。"
  type        = string
  default     = "ON_DEMAND"
}
