import request from '@/utils/request'

export type ServiceTicketStatus = 'new' | 'ai_drafted' | 'needs_review' | 'accepted' | 'rejected'
export type ServiceTicketCategory = 'HR' | 'IT' | 'FINANCE' | 'SECURITY' | 'SALES' | 'GENERAL'

export interface ServiceTicket {
  id: number
  ticketNo: string
  title: string
  requester?: string
  requesterRole?: string
  department?: string
  priority: string
  channel: string
  status: ServiceTicketStatus
  category: ServiceTicketCategory | string
  question: string
  expectedOutcome?: string
  kbScope?: string
  confidence?: number | string
  answerDraft?: string
  finalAnswer?: string
  sourceSummary?: string
  aiTraceId?: string
  accepted?: number
  feedbackStatus?: string
  goldenPairId?: number
  resolutionOwner?: string
  resolvedAt?: string
  createTime?: string
  updateTime?: string
}

export interface ServiceTicketEvent {
  id: number
  ticketId: number
  eventType: string
  actor: string
  detail?: string
  createTime?: string
}

export interface ServiceDeskStats {
  total: number
  newCount: number
  drafted: number
  needsReview: number
  accepted: number
  rejected: number
  goldenCandidates: number
  goldenSynced?: number
  knowledgeGaps?: number
  acceptanceRate: number | string
  avgConfidence: number | string
}

export interface DraftResult {
  ticket: ServiceTicket
  recommendation: string
  lowConfidence: boolean
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

export interface ServiceDeskReindexReport {
  success: boolean
  knowledgeBaseCount: number
  chunkCount: number
  indexedCount: number
  message: string
  results: Array<{
    knowledgeBaseId: number
    knowledgeBaseName: string
    success: boolean
    chunkCount: number
    indexedCount: number
    message: string
  }>
}

export const serviceDeskApi = {
  tickets: (params: {
    current?: number
    size?: number
    status?: string
    category?: string
    keyword?: string
  }): Promise<PageResult<ServiceTicket>> =>
    request.get('/service-desk/tickets', { params }),

  ticket: (id: number): Promise<ServiceTicket> =>
    request.get(`/service-desk/tickets/${id}`),

  events: (id: number): Promise<ServiceTicketEvent[]> =>
    request.get(`/service-desk/tickets/${id}/events`),

  create: (data: Partial<ServiceTicket>): Promise<number> =>
    request.post('/service-desk/tickets', data),

  draft: (id: number): Promise<DraftResult> =>
    request.post(`/service-desk/tickets/${id}/draft`),

  accept: (id: number, finalAnswer?: string): Promise<ServiceTicket> =>
    request.post(`/service-desk/tickets/${id}/accept`, { finalAnswer }),

  retryGoldenPair: (id: number): Promise<ServiceTicket> =>
    request.post(`/service-desk/tickets/${id}/golden-pair/retry`),

  reject: (id: number, reason?: string): Promise<ServiceTicket> =>
    request.post(`/service-desk/tickets/${id}/reject`, { reason }),

  stats: (): Promise<ServiceDeskStats> =>
    request.get('/service-desk/stats'),

  reindexKnowledge: (): Promise<ServiceDeskReindexReport> =>
    request.post('/service-desk/knowledge/reindex'),
}
