import request from '@/utils/request'

export interface RagEvalCase {
  id: string
  question: string
  expectedAnswer: string
  expectedChunkIds: string[]
  expectedKeywords: string[]
  category: string
  difficulty: string
  shouldRefuse: boolean
}

export interface RagEvalSummary {
  recallAtK: number
  hitAtK: number
  mrr: number
  citationHit: number
  refusalAccuracy?: number
  avgLatencyMs: number
  caseCount: number
}

export interface RagEvalResult {
  caseId: string
  question: string
  strategy: string
  answer: string
  recallAtK: number
  hitAtK: number
  mrr: number
  citationHit: number
  refusalCorrect?: number
  latencyMs: number
  safetyReason?: string
}

export interface RagEvalStrategyReport {
  strategy: string
  summary: RagEvalSummary
  results: RagEvalResult[]
}

export interface RagEvalReport {
  runId: string
  datasetName: string
  createdAt: string
  topK: number
  caseCount: number
  corpusChunkCount: number
  reportPath: string
  strategies: RagEvalStrategyReport[]
  elapsedMs: number
}

export const ragEvalApi = {
  cases: (includeSecurity = true): Promise<RagEvalCase[]> =>
    request.get('/v2/rag-eval/cases', { params: { includeSecurity } }),

  strategies: (): Promise<string[]> =>
    request.get('/v2/rag-eval/strategies'),

  run: (data: { strategies?: string[]; topK?: number; includeSecurity?: boolean }): Promise<RagEvalReport> =>
    request.post('/v2/rag-eval/runs', data),

  latest: (): Promise<RagEvalReport> =>
    request.get('/v2/rag-eval/runs/latest'),
}
