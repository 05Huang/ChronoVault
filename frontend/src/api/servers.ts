import client from './client'
import type { Server, Container, Volume, LogEntry, ServerHealth, TopologyEdge, CreateServerRequest, UpdateSshConfigRequest, AddVolumeRequest, ConnectionTestResult, EnvironmentScanResult, AiAnalyzeResult } from '@/types'

export interface AgentInstallResult {
  success: boolean
  message: string
  steps: string[]
  apiKey?: string
  agentId?: string
}

export const serversApi = {
  getAll: () => client.get<Server[]>('/servers') as unknown as Promise<Server[]>,
  get: (id: number) => client.get<Server>(`/servers/${id}`) as unknown as Promise<Server>,
  create: (data: CreateServerRequest) =>
    client.post<Server>('/servers', data) as unknown as Promise<Server>,
  getContainers: (id: number) => client.get<Container[]>(`/servers/${id}/containers`) as unknown as Promise<Container[]>,
  getVolumes: (id: number) => client.get<Volume[]>(`/servers/${id}/volumes`) as unknown as Promise<Volume[]>,
  addVolume: (id: number, data: AddVolumeRequest) =>
    client.post<Volume>(`/servers/${id}/volumes`, data) as unknown as Promise<Volume>,
  getLogs: (id: number) => client.get<LogEntry[]>(`/servers/${id}/logs`) as unknown as Promise<LogEntry[]>,
  clearLogs: (id: number) => client.delete(`/servers/${id}/logs`) as unknown as Promise<void>,
  connect: (id: number) => client.post(`/servers/${id}/connect`) as unknown as Promise<any>,
  updateSshConfig: (id: number, data: UpdateSshConfigRequest) =>
    client.put(`/servers/${id}/ssh`, data) as unknown as Promise<void>,
  testConnection: (id: number) => client.post<ConnectionTestResult>(`/servers/${id}/test-connection`) as unknown as Promise<ConnectionTestResult>,
  getTopology: (id: number) => client.get<TopologyEdge[]>(`/servers/${id}/topology`) as unknown as Promise<TopologyEdge[]>,
  getHealth: (id: number) => client.get<ServerHealth>(`/servers/${id}/health`) as unknown as Promise<ServerHealth>,
  refreshHealth: (id: number) => client.post<ServerHealth>(`/servers/${id}/health/refresh`) as unknown as Promise<ServerHealth>,
  startContainer: (id: number, cid: string) => client.post(`/servers/${id}/containers/${cid}/start`) as unknown as Promise<void>,
  stopContainer: (id: number, cid: string) => client.post(`/servers/${id}/containers/${cid}/stop`) as unknown as Promise<void>,
  restartContainer: (id: number, cid: string) => client.post(`/servers/${id}/containers/${cid}/restart`) as unknown as Promise<void>,
  scanEnvironment: (id: number) => client.post<EnvironmentScanResult>(`/servers/${id}/scan-environment`) as unknown as Promise<EnvironmentScanResult>,
  aiAnalyze: (id: number) => client.post<AiAnalyzeResult>(`/servers/${id}/ai-analyze`) as unknown as Promise<AiAnalyzeResult>,
  installAgent: (id: number) => client.post<AgentInstallResult>(`/servers/${id}/install-agent`) as unknown as Promise<AgentInstallResult>,
}
