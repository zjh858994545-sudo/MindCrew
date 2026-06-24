import request from '@/utils/request'
import axios from 'axios'

export interface KnowledgeBase {
  id: number
  name: string
  description: string
  category: string
  fileUrl: string
  fileType: string
  fileSize: number
  chunkCount: number
  status: 'uploading' | 'processing' | 'ready' | 'failed'
  errorMsg: string
  createTime: string
  tags?: string
  summary?: string
  answerableQuestions?: string
  qualityReport?: string
  categoryUserSet?: number
  /** 任务 7 · 可见性 */
  visibility?: 'public' | 'scoped' | 'private'
}

export interface KnowledgeListParams {
  current?: number
  size?: number
  category?: string
  status?: string
}

export const knowledgeApi = {
  // 上传文档（带进度回调）
  upload: (
    file: File,
    category: string,
    description: string,
    onProgress?: (percent: number) => void
  ): Promise<number> => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('category', category)
    if (description) formData.append('description', description)

    return axios.post('/api/knowledge/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
        Authorization: `Bearer ${localStorage.getItem('token')}`
      },
      onUploadProgress: (e) => {
        if (onProgress && e.total) {
          onProgress(Math.round((e.loaded / e.total) * 100))
        }
      }
    }).then(res => {
      const { code, message, data } = res.data
      if (code === 200) return data
      throw new Error(message)
    })
  },

  list: (params: KnowledgeListParams): Promise<any> =>
    request.get('/knowledge/list', { params }),

  getById: (id: number): Promise<KnowledgeBase> =>
    request.get(`/knowledge/${id}`),

  delete: (id: number): Promise<void> =>
    request.delete(`/knowledge/${id}`),

  reprocess: (id: number): Promise<void> =>
    request.post(`/knowledge/${id}/reprocess`),

  getCategories: (): Promise<string[]> =>
    request.get('/knowledge/categories'),

  /** 任务 7 · 切换可见性 (public / scoped / private) */
  updateVisibility: (id: number, visibility: string): Promise<void> =>
    request.put(`/knowledge/${id}/visibility`, null, { params: { visibility } })
}
