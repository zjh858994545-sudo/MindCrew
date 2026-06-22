import request from '@/utils/request'

export interface TraceRecord {
  traceId: string
  conversationId?: number
  userId?: string
  question?: string
  answer?: string
  status: string
  totalLatencyMs?: number
  modelName?: string
  createdAt: string
}

export interface TraceSpan {
  spanId: string
  traceId: string
  spanType: string
  name: string
  inputSummary?: string
  outputSummary?: string
  latencyMs?: number
  status: string
  errorMessage?: string
  startedAt?: string
  endedAt?: string
}

export interface TraceDetail {
  trace?: TraceRecord
  spans: TraceSpan[]
}

export interface SafetyEvent {
  traceId?: string
  userId?: string
  riskType: string
  riskLevel: string
  action: string
  matchedRule?: string
  inputSummary?: string
  createdAt: string
}

export const agentTraceApi = {
  list: (): Promise<TraceRecord[]> => request.get('/v2/agent-traces'),
  detail: (traceId: string): Promise<TraceDetail> => request.get(`/v2/agent-traces/${traceId}`),
  safetyEvents: (): Promise<SafetyEvent[]> => request.get('/v2/agent-traces/safety-events'),
}
