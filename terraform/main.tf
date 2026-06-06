terraform {
  required_providers {
    chronovault = {
      source  = "chronovault/chronovault"
      version = "~> 0.1.0"
    }
  }
}

provider "chronovault" {
  base_url = var.base_url
  api_key  = var.api_key
}

variable "base_url" {
  description = "ChronoVault Backend URL"
  type        = string
  default     = "http://localhost:8080/api/v1"
}

variable "api_key" {
  description = "ChronoVault API Key"
  type        = string
  sensitive   = true
}

# Example: Register a server
resource "chronovault_server" "web_server" {
  name = "web-server-01"
  ip   = "192.168.1.100"
  os   = "Ubuntu 22.04"
}

# Example: Create a scheduled backup
resource "chronovault_scheduled_backup" "daily_backup" {
  server_id       = chronovault_server.web_server.id
  name            = "Daily Backup"
  cron_expression = "0 2 * * *"
  paths           = ["/etc", "/opt"]
}

# Example: Set retention policy
resource "chronovault_retention_policy" "production" {
  server_id   = chronovault_server.web_server.id
  keep_daily  = 7
  keep_weekly = 4
  keep_monthly = 12
}

output "server_id" {
  value = chronovault_server.web_server.id
}

output "backup_schedule" {
  value = chronovault_scheduled_backup.daily_backup.cron_expression
}
