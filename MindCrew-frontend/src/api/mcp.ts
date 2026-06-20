import request from '@/utils/request'

export interface McpTool {
  id: number
  name: string
  description: string
  mode: 'embedded' | 'remote'
  callCount: number
  avgLatencyMs: number
  status: 'active' | 'disabled'
  createTime: string
  updateTime: string
}

export interface McpTestResult {
  toolId: number
  toolName: string
  mode: string
  status: string
  testResult: 'SUCCESS' | 'ERROR' | 'SKIPPED'
  message: string
  latencyMs: number
  timestamp: number
  sampleOutput?: unknown
}

export interface McpClient {
  id: number
  clientId: string
  displayName: string
  status: 'active' | 'disabled'
  defaultRateLimitPerMinute: number
  allowedKbIds?: string
  description?: string
}

export interface McpToolPolicy {
  id: number
  clientId: string
  toolName: string
  enabled: 0 | 1
  rateLimitPerMinute: number
  kbScopeJson?: string
  description?: string
}

export interface McpAuditLog {
  id: number
  requestId: string
  clientId: string
  userId?: string
  toolName: string
  action: string
  status: 'SUCCESS' | 'ERROR' | 'SKIPPED' | 'BLOCK'
  latencyMs: number
  reason?: string
  inputSummary?: string
  outputSummary?: string
  createTime: string
}

export const mcpApi = {
  listTools: (): Promise<McpTool[]> =>
    request.get('/mcp/tools'),

  updateStatus: (id: number, status: 'active' | 'disabled'): Promise<void> =>
    request.put(`/mcp/tools/${id}/status`, { status }),

  testTool: (id: number): Promise<McpTestResult> =>
    request.post(`/mcp/tools/${id}/test`),

  listClients: (): Promise<McpClient[]> =>
    request.get('/mcp/clients'),

  listPolicies: (clientId?: string): Promise<McpToolPolicy[]> =>
    request.get('/mcp/policies', { params: clientId ? { clientId } : {} }),

  listAudits: (limit = 30): Promise<McpAuditLog[]> =>
    request.get('/mcp/audits', { params: { limit } }),
}
