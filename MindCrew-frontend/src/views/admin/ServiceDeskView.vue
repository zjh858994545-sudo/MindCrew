<template>
  <div class="service-desk-page">
    <header class="desk-header">
      <div>
        <h1>企业知识服务台</h1>
        <p>工单接入、AI 草稿、人工确认、反馈沉淀</p>
      </div>
      <div class="desk-actions">
        <el-input
          v-model="keyword"
          :prefix-icon="Search"
          clearable
          placeholder="搜索工单、问题、申请人"
          @keyup.enter="loadTickets"
          @clear="loadTickets"
        />
        <el-button :icon="Refresh" @click="refreshAll" />
        <el-tooltip content="重建服务台知识向量" placement="bottom">
          <el-button :icon="Connection" :loading="indexing" @click="reindexKnowledge" />
        </el-tooltip>
        <el-button type="primary" :icon="Plus" @click="createVisible = true">新建工单</el-button>
      </div>
    </header>

    <section class="metric-grid">
      <article v-for="item in metricItems" :key="item.label" class="metric">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </section>

    <main class="desk-main">
      <aside class="ticket-queue">
        <div class="queue-filters">
          <el-select v-model="status" clearable placeholder="状态" @change="loadTickets">
            <el-option label="全部" value="" />
            <el-option label="新工单" value="new" />
            <el-option label="已生成草稿" value="ai_drafted" />
            <el-option label="待人工复核" value="needs_review" />
            <el-option label="已采纳" value="accepted" />
            <el-option label="已驳回" value="rejected" />
          </el-select>
          <el-select v-model="category" clearable placeholder="知识域" @change="loadTickets">
            <el-option label="全部" value="" />
            <el-option label="HR" value="HR" />
            <el-option label="IT" value="IT" />
            <el-option label="财务" value="FINANCE" />
            <el-option label="安全合规" value="SECURITY" />
            <el-option label="销售商务" value="SALES" />
            <el-option label="通用" value="GENERAL" />
          </el-select>
        </div>

        <div class="ticket-list" v-loading="loading">
          <button
            v-for="ticket in tickets"
            :key="ticket.id"
            class="ticket-row"
            :class="{ active: selected?.id === ticket.id }"
            type="button"
            @click="selectTicket(ticket)"
          >
            <span class="ticket-top">
              <span class="ticket-no">{{ ticket.ticketNo }}</span>
              <el-tag size="small" :type="priorityType(ticket.priority)">{{ ticket.priority }}</el-tag>
            </span>
            <strong>{{ ticket.title }}</strong>
            <span class="ticket-question">{{ ticket.question }}</span>
            <span class="ticket-meta">
              <el-tag size="small" :type="statusType(ticket.status)">{{ statusLabel(ticket.status) }}</el-tag>
              <span>{{ ticket.category }}</span>
              <span>{{ ticket.requester || '-' }}</span>
            </span>
          </button>

          <div v-if="!loading && tickets.length === 0" class="empty">暂无工单</div>
        </div>

        <el-pagination
          v-if="total > pageSize"
          v-model:current-page="currentPage"
          class="queue-pagination"
          :page-size="pageSize"
          :total="total"
          background
          layout="prev, pager, next"
          @current-change="loadTickets"
        />
      </aside>

      <section class="ticket-detail" v-if="selected">
        <div class="detail-head">
          <div>
            <span class="ticket-no">{{ selected.ticketNo }}</span>
            <h2>{{ selected.title }}</h2>
            <p>{{ selected.department || '-' }} / {{ selected.requester || '-' }} / {{ selected.requesterRole || '-' }}</p>
          </div>
          <div class="detail-tags">
            <el-tag :type="statusType(selected.status)">{{ statusLabel(selected.status) }}</el-tag>
            <el-tag type="info">{{ selected.category }}</el-tag>
            <el-tag v-if="selected.confidence" :type="confidenceTag(selected.confidence)">
              置信度 {{ confidenceText(selected.confidence) }}
            </el-tag>
            <el-tag v-if="selected.goldenPairId" type="success">Golden Pair #{{ selected.goldenPairId }}</el-tag>
          </div>
        </div>

        <div class="workflow-strip">
          <div class="step" :class="{ done: !!selected.question }">1. 接入</div>
          <div class="step" :class="{ done: !!selected.answerDraft }">2. AI 草稿</div>
          <div class="step" :class="{ done: selected.status === 'accepted' || selected.status === 'rejected' }">3. 人工确认</div>
          <div class="step" :class="{ done: selected.feedbackStatus === 'golden_pair_candidate' || selected.feedbackStatus === 'golden_pair_synced' || selected.feedbackStatus === 'kb_gap' }">4. 沉淀</div>
        </div>

        <div class="detail-grid">
          <article class="panel question-panel">
            <div class="panel-head">
              <h3>业务问题</h3>
              <span>{{ selected.kbScope || '未指定知识范围' }}</span>
            </div>
            <p class="question-text">{{ selected.question }}</p>
            <div class="expected" v-if="selected.expectedOutcome">
              <span>期望结果</span>
              <p>{{ selected.expectedOutcome }}</p>
            </div>
          </article>

          <article class="panel timeline-panel">
            <div class="panel-head">
              <h3>事件时间线</h3>
              <span>{{ events.length }} 条</span>
            </div>
            <el-timeline>
              <el-timeline-item
                v-for="event in events"
                :key="event.id"
                :timestamp="formatTime(event.createTime)"
                placement="top"
              >
                <strong>{{ event.eventType }}</strong>
                <p>{{ event.actor }}：{{ event.detail || '-' }}</p>
              </el-timeline-item>
            </el-timeline>
          </article>
        </div>

        <article class="panel answer-panel">
          <div class="panel-head">
            <h3>AI 答复草稿</h3>
            <el-button
              v-if="selected.aiTraceId"
              class="trace-link"
              link
              type="primary"
              @click="openTrace(selected.aiTraceId)"
            >
              {{ selected.aiTraceId }}
            </el-button>
            <span v-else>尚未生成 Trace</span>
          </div>
          <div v-if="selected.answerDraft" class="answer-text">{{ selected.answerDraft }}</div>
          <div v-else class="empty-answer">还没有生成答复草稿</div>
          <div v-if="selected.sourceSummary" class="source-box">
            <span>引用来源</span>
            <code>{{ selected.sourceSummary }}</code>
          </div>
          <div class="recommendation" v-if="recommendation">{{ recommendation }}</div>
        </article>

        <footer class="detail-actions">
          <el-button type="primary" :icon="MagicStick" :loading="drafting" @click="draftSelected">
            生成答复
          </el-button>
          <el-button type="success" :icon="CircleCheck" :disabled="!selected.answerDraft" @click="openAccept">
            采纳沉淀
          </el-button>
          <el-button
            v-if="selected.status === 'accepted' && !selected.goldenPairId"
            type="warning"
            plain
            :icon="Refresh"
            :loading="retrying"
            @click="retryGoldenPair"
          >
            重试 GP
          </el-button>
          <el-button type="danger" plain :icon="Close" :disabled="!selected.answerDraft" @click="rejectVisible = true">
            驳回补知识
          </el-button>
        </footer>
      </section>

      <section v-else class="ticket-detail empty-detail">
        <el-empty description="请选择一条工单" />
      </section>
    </main>

    <el-dialog v-model="acceptVisible" title="采纳并沉淀为高质量样本" width="720px" :close-on-click-modal="false">
      <el-input
        v-model="finalAnswer"
        type="textarea"
        :autosize="{ minRows: 9, maxRows: 18 }"
        maxlength="6000"
        show-word-limit
      />
      <template #footer>
        <el-button @click="acceptVisible = false">取消</el-button>
        <el-button type="success" :loading="submitting" @click="confirmAccept">确认采纳</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectVisible" title="驳回并标记知识缺口" width="560px">
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="5"
        placeholder="例如：缺少最新制度、引用来源不够、需要业务负责人确认"
        maxlength="800"
        show-word-limit
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createVisible" title="新建业务工单" width="680px">
      <el-form :model="createForm" label-width="88px">
        <el-form-item label="标题">
          <el-input v-model="createForm.title" maxlength="100" />
        </el-form-item>
        <el-form-item label="知识域">
          <el-select v-model="createForm.category" style="width: 100%">
            <el-option label="HR" value="HR" />
            <el-option label="IT" value="IT" />
            <el-option label="财务" value="FINANCE" />
            <el-option label="安全合规" value="SECURITY" />
            <el-option label="销售商务" value="SALES" />
            <el-option label="通用" value="GENERAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-segmented v-model="createForm.priority" :options="['P0', 'P1', 'P2', 'P3']" />
        </el-form-item>
        <el-form-item label="申请人">
          <el-input v-model="createForm.requester" maxlength="40" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="createForm.department" maxlength="60" />
        </el-form-item>
        <el-form-item label="问题">
          <el-input v-model="createForm.question" type="textarea" :rows="5" maxlength="1200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheck, Close, Connection, MagicStick, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  serviceDeskApi,
  type ServiceDeskStats,
  type ServiceTicket,
  type ServiceTicketEvent,
} from '@/api/serviceDesk'

const router = useRouter()
const loading = ref(false)
const drafting = ref(false)
const submitting = ref(false)
const indexing = ref(false)
const retrying = ref(false)
const tickets = ref<ServiceTicket[]>([])
const events = ref<ServiceTicketEvent[]>([])
const selected = ref<ServiceTicket | null>(null)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const status = ref('')
const category = ref('')
const keyword = ref('')
const recommendation = ref('')

const stats = ref<ServiceDeskStats>({
  total: 0,
  newCount: 0,
  drafted: 0,
  needsReview: 0,
  accepted: 0,
  rejected: 0,
  goldenCandidates: 0,
  goldenSynced: 0,
  knowledgeGaps: 0,
  acceptanceRate: 0,
  avgConfidence: 0,
})

const metricItems = computed(() => [
  { label: '总工单', value: stats.value.total },
  { label: '待处理', value: stats.value.newCount },
  { label: '待复核', value: stats.value.needsReview },
  { label: '已采纳', value: stats.value.accepted },
  { label: '沉淀样本', value: stats.value.goldenCandidates },
  { label: '已同步 GP', value: stats.value.goldenSynced || 0 },
  { label: '知识缺口', value: stats.value.knowledgeGaps || 0 },
  { label: '采纳率', value: `${numberValue(stats.value.acceptanceRate).toFixed(1)}%` },
])

async function loadTickets() {
  loading.value = true
  try {
    const page = await serviceDeskApi.tickets({
      current: currentPage.value,
      size: pageSize.value,
      status: status.value || undefined,
      category: category.value || undefined,
      keyword: keyword.value || undefined,
    })
    tickets.value = page.records || []
    total.value = page.total || 0
    const first = tickets.value[0]
    if (!selected.value && first) {
      await selectTicket(first)
    } else if (selected.value) {
      const latest = tickets.value.find(item => item.id === selected.value?.id)
      if (latest) selected.value = latest
    }
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  stats.value = await serviceDeskApi.stats()
}

async function selectTicket(ticket: ServiceTicket) {
  selected.value = ticket
  recommendation.value = ''
  events.value = await serviceDeskApi.events(ticket.id)
}

async function refreshAll() {
  await Promise.all([loadTickets(), loadStats()])
  if (selected.value) {
    events.value = await serviceDeskApi.events(selected.value.id)
  }
}

async function reindexKnowledge() {
  indexing.value = true
  try {
    const report = await serviceDeskApi.reindexKnowledge()
    const summary = `${report.indexedCount}/${report.chunkCount}`
    if (report.success) {
      ElMessage.success(`服务台知识向量重建完成：${summary}`)
    } else {
      ElMessage.warning(`向量重建未完全成功：${summary}；${report.message}`)
    }
  } finally {
    indexing.value = false
  }
}

async function draftSelected() {
  if (!selected.value) return
  drafting.value = true
  try {
    const result = await serviceDeskApi.draft(selected.value.id)
    selected.value = result.ticket
    recommendation.value = result.recommendation
    ElMessage.success(result.lowConfidence ? '已生成草稿，需要人工复核' : '已生成可采纳草稿')
    await refreshAll()
  } finally {
    drafting.value = false
  }
}

const acceptVisible = ref(false)
const rejectVisible = ref(false)
const createVisible = ref(false)
const finalAnswer = ref('')
const rejectReason = ref('')
const createForm = reactive({
  title: '',
  category: 'GENERAL',
  priority: 'P2',
  requester: '',
  requesterRole: '',
  department: '',
  channel: 'web',
  question: '',
  expectedOutcome: '',
  kbScope: '',
})

function openAccept() {
  if (!selected.value) return
  finalAnswer.value = selected.value.finalAnswer || selected.value.answerDraft || ''
  acceptVisible.value = true
}

async function confirmAccept() {
  if (!selected.value) return
  submitting.value = true
  try {
    const accepted = await serviceDeskApi.accept(selected.value.id, finalAnswer.value)
    selected.value = accepted
    if (accepted.goldenPairId) {
      ElMessage.success(`已采纳，并同步为 Golden Pair #${accepted.goldenPairId}`)
    } else {
      ElMessage.warning('已采纳，已保留为 Golden Pair 候选；配置真实模型 Key 后可重试同步')
    }
    acceptVisible.value = false
    await refreshAll()
  } finally {
    submitting.value = false
  }
}

async function retryGoldenPair() {
  if (!selected.value) return
  retrying.value = true
  try {
    const ticket = await serviceDeskApi.retryGoldenPair(selected.value.id)
    selected.value = ticket
    if (ticket.goldenPairId) {
      ElMessage.success(`Golden Pair 同步成功：#${ticket.goldenPairId}`)
    } else {
      ElMessage.warning('仍未同步成功；确认真实模型 Key 和 Milvus 后再重试')
    }
    await refreshAll()
  } finally {
    retrying.value = false
  }
}

function openTrace(traceId?: string) {
  if (!traceId) return
  router.push({ name: 'AgentTrace', query: { traceId } })
}

async function confirmReject() {
  if (!selected.value) return
  submitting.value = true
  try {
    selected.value = await serviceDeskApi.reject(selected.value.id, rejectReason.value)
    ElMessage.success('已驳回，并标记为知识缺口')
    rejectVisible.value = false
    rejectReason.value = ''
    await refreshAll()
  } finally {
    submitting.value = false
  }
}

async function confirmCreate() {
  if (!createForm.title || !createForm.question) {
    ElMessage.warning('标题和问题不能为空')
    return
  }
  submitting.value = true
  try {
    const id = await serviceDeskApi.create({ ...createForm })
    ElMessage.success('工单已创建')
    createVisible.value = false
    Object.assign(createForm, {
      title: '',
      category: 'GENERAL',
      priority: 'P2',
      requester: '',
      requesterRole: '',
      department: '',
      channel: 'web',
      question: '',
      expectedOutcome: '',
      kbScope: '',
    })
    await refreshAll()
    const created = await serviceDeskApi.ticket(id)
    await selectTicket(created)
  } finally {
    submitting.value = false
  }
}

function statusLabel(value: string) {
  return ({
    new: '新工单',
    ai_drafted: '已生成草稿',
    needs_review: '待人工复核',
    accepted: '已采纳',
    rejected: '已驳回',
  } as Record<string, string>)[value] || value
}

function statusType(value: string) {
  return ({
    new: 'info',
    ai_drafted: 'primary',
    needs_review: 'warning',
    accepted: 'success',
    rejected: 'danger',
  } as Record<string, any>)[value] || 'info'
}

function priorityType(value: string) {
  return value === 'P0' || value === 'P1' ? 'danger' : value === 'P2' ? 'warning' : 'info'
}

function confidenceTag(value: number | string) {
  const n = numberValue(value)
  if (n >= 0.8) return 'success'
  if (n >= 0.7) return 'warning'
  return 'danger'
}

function confidenceText(value: number | string) {
  return `${(numberValue(value) * 100).toFixed(0)}%`
}

function numberValue(value: number | string | undefined) {
  if (value == null) return 0
  const n = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(n) ? n : 0
}

function formatTime(value?: string) {
  if (!value) return ''
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(async () => {
  await Promise.all([loadTickets(), loadStats()])
})
</script>

<style scoped>
.service-desk-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
  padding: 20px 24px 24px;
  overflow: hidden;
  color: var(--ink-1);
}

.desk-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.desk-header h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 760;
  letter-spacing: 0;
}

.desk-header p {
  margin: 4px 0 0;
  color: var(--ink-3);
  font-size: 13px;
}

.desk-actions {
  display: grid;
  grid-template-columns: minmax(240px, 320px) auto auto auto;
  align-items: center;
  gap: 10px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(92px, 1fr));
  gap: 10px;
}

.metric,
.ticket-queue,
.ticket-detail,
.panel {
  background: var(--bg-surface);
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
}

.metric {
  padding: 12px 14px;
}

.metric span {
  display: block;
  color: var(--ink-3);
  font-size: 12px;
  margin-bottom: 4px;
}

.metric strong {
  font-size: 22px;
  font-weight: 760;
}

.desk-main {
  display: grid;
  grid-template-columns: 390px minmax(0, 1fr);
  gap: 14px;
  min-height: 0;
  flex: 1;
}

.ticket-queue {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: 12px;
}

.queue-filters {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 10px;
}

.ticket-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.ticket-row {
  display: flex;
  flex-direction: column;
  gap: 7px;
  width: 100%;
  padding: 12px;
  text-align: left;
  background: var(--bg-subtle);
  border: 1px solid transparent;
  border-radius: 8px;
  color: inherit;
  cursor: pointer;
  transition: border-color 140ms ease, background 140ms ease;
}

.ticket-row:hover,
.ticket-row.active {
  background: var(--bg-surface);
  border-color: var(--brand);
}

.ticket-top,
.ticket-meta,
.detail-tags,
.detail-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ticket-top {
  justify-content: space-between;
}

.ticket-no {
  color: var(--brand);
  font-family: 'Manrope', sans-serif;
  font-size: 12px;
  font-weight: 700;
}

.ticket-row strong {
  font-size: 14px;
  line-height: 1.35;
}

.ticket-question {
  color: var(--ink-3);
  display: -webkit-box;
  font-size: 12.5px;
  line-height: 1.45;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.ticket-meta {
  color: var(--ink-4);
  font-size: 12px;
}

.queue-pagination {
  justify-content: center;
  margin-top: 10px;
}

.ticket-detail {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: 16px;
  overflow-y: auto;
}

.empty-detail {
  align-items: center;
  justify-content: center;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.detail-head h2 {
  margin: 4px 0;
  font-size: 20px;
  font-weight: 760;
}

.detail-head p {
  margin: 0;
  color: var(--ink-3);
  font-size: 13px;
}

.workflow-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 14px;
}

.step {
  padding: 9px 10px;
  background: var(--bg-subtle);
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--ink-3);
  font-size: 12px;
  font-weight: 650;
  text-align: center;
}

.step.done {
  color: #047857;
  background: rgba(52, 211, 153, 0.12);
  border-color: rgba(52, 211, 153, 0.28);
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(300px, 0.8fr);
  gap: 12px;
}

.panel {
  padding: 14px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.panel-head h3 {
  margin: 0;
  font-size: 15px;
}

.panel-head span {
  color: var(--ink-4);
  font-size: 12px;
}

.trace-link {
  max-width: 360px;
  padding: 0;
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.question-text,
.answer-text {
  margin: 0;
  color: var(--ink-1);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.expected {
  margin-top: 12px;
  padding: 10px 12px;
  background: rgba(14, 165, 233, 0.08);
  border: 1px solid rgba(14, 165, 233, 0.2);
  border-radius: 8px;
}

.expected span,
.source-box span {
  display: block;
  color: var(--ink-4);
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 4px;
}

.expected p {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
}

.timeline-panel {
  max-height: 330px;
  overflow-y: auto;
}

.timeline-panel p {
  margin: 4px 0 0;
  color: var(--ink-3);
  font-size: 12.5px;
  line-height: 1.45;
}

.answer-panel {
  margin-top: 12px;
}

.empty-answer,
.empty {
  color: var(--ink-4);
  padding: 36px 0;
  text-align: center;
}

.source-box,
.recommendation {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
}

.source-box {
  background: var(--bg-subtle);
  border: 1px solid var(--line);
}

.source-box code {
  color: var(--brand);
  white-space: pre-wrap;
}

.recommendation {
  color: #92400e;
  background: rgba(245, 158, 11, 0.12);
  border: 1px solid rgba(245, 158, 11, 0.24);
  font-size: 13px;
  line-height: 1.55;
}

.detail-actions {
  justify-content: flex-end;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

@media (max-width: 1280px) {
  .metric-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .desk-main,
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .ticket-queue {
    min-height: 320px;
  }
}

@media (max-width: 760px) {
  .service-desk-page {
    padding: 14px;
  }

  .desk-header,
  .detail-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .desk-actions,
  .metric-grid,
  .workflow-strip {
    grid-template-columns: 1fr;
    width: 100%;
  }

  .detail-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
