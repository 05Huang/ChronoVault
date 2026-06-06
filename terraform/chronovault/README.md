# ChronoVault Terraform Provider

Terraform provider for managing ChronoVault servers and snapshot policies via Infrastructure as Code.

## Usage

### Provider Configuration

```hcl
terraform {
  required_providers {
    chronovault = {
      source  = "chronovault/chronovault"
      version = "~> 0.1"
    }
  }
}

provider "chronovault" {
  host  = "https://chronovault.example.com"
  token = var.chronovault_token
}
```

### Register a Server

```hcl
resource "chronovault_server" "web" {
  name = "web-production"
  ip   = "192.168.1.10"
  os   = "Ubuntu 22.04"
}

resource "chronovault_server" "db" {
  name = "database-production"
  ip   = "192.168.1.20"
  os   = "Ubuntu 22.04"
}
```

### Create a Snapshot Retention Policy

```hcl
resource "chronovault_snapshot_policy" "production_daily" {
  server_id     = chronovault_server.web.id
  name          = "production-daily"
  max_count     = 30
  max_age_days  = 90
  min_keep_days = 7
  enabled       = true
}

resource "chronovault_snapshot_policy" "staging_weekly" {
  server_id     = chronovault_server.db.id
  name          = "staging-weekly"
  max_count     = 10
  max_age_days  = 30
  min_keep_days = 3
  enabled       = true
}
```

### Import Existing Resources

```bash
# Import an existing server
terraform import chronovault_server.web 42

# Import an existing snapshot policy
terraform import chronovault_snapshot_policy.production_daily 7
```

## Resources

| Resource | Description |
|----------|-------------|
| `chronovault_server` | Register/manage a server in ChronoVault |
| `chronovault_snapshot_policy` | Manage snapshot retention policies |

## Environment Variables

| Variable | Description |
|----------|-------------|
| `CHRONOVAULT_HOST` | ChronoVault server URL |
| `CHRONOVAULT_TOKEN` | API authentication token |

## Building

```bash
go build -o terraform-provider-chronovault
```

## Requirements

- Go 1.22+
- Terraform 1.5+
- ChronoVault server with API access
