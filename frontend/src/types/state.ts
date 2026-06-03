// State snapshot types — matching the Agent's state.json format

export interface OSInfo {
  name: string
  version: string
  kernel: string
  arch: string
}

export interface PackageInfo {
  name: string
  version: string
  manager: string
}

export interface ServiceInfo {
  name: string
  status: string
  enabled: boolean
  pid?: number
}

export interface PortInfo {
  port: number
  protocol: string
  process: string
  state: string
}

export interface DockerContainer {
  id: string
  name: string
  image: string
  status: string
  ports: string[]
}

export interface DockerState {
  available: boolean
  containers: DockerContainer[]
  compose_files: string[]
}

export interface ConfigHash {
  path: string
  sha256: string
  size: number
}

export interface CrontabEntry {
  user: string
  schedule: string
  command: string
}

export interface StateSnapshot {
  collected_at: string
  agent_version: string
  os: OSInfo
  packages: PackageInfo[]
  services: ServiceInfo[]
  ports: PortInfo[]
  docker: DockerState
  configs: ConfigHash[]
  crontab: CrontabEntry[]
}

// Diff result types

export interface PackageDiff {
  added: PackageInfo[]
  removed: PackageInfo[]
  upgraded: { name: string; fromVersion: string; toVersion: string }[]
}

export interface ServiceChange {
  name: string
  fromStatus: string
  toStatus: string
  fromEnabled: boolean
  toEnabled: boolean
}

export interface ServiceDiff {
  added: string[]
  removed: string[]
  changed: ServiceChange[]
}

export interface PortDiff {
  added: string[]
  removed: string[]
}

export interface DockerDiff {
  containersAdded: string[]
  containersRemoved: string[]
  containersChanged: string[]
}

export interface ConfigDiff {
  added: string[]
  removed: string[]
  changed: string[]
}

export interface CrontabDiff {
  added: string[]
  removed: string[]
}

export interface DiffSummary {
  packagesAdded: number
  packagesRemoved: number
  packagesUpgraded: number
  servicesChanged: number
  portsChanged: number
  dockerChanged: number
  configsChanged: number
  crontabChanged: number
}

export interface StateDiffResult {
  snapshot_a: number
  snapshot_b: number
  summary: DiffSummary
  packages?: PackageDiff
  services?: ServiceDiff
  ports?: PortDiff
  docker?: DockerDiff
  configs?: ConfigDiff
  crontab?: CrontabDiff
}
