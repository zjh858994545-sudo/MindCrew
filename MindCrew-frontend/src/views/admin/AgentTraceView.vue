<template>
  <div class="trace-page">
    <header class="page-head">
      <div>
        <h1>Agent Trace</h1>
        <p>问答链路、Span 时间线、安全事件</p>
      </div>
      <el-button size="large" @click="refresh">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </header>

    <section class="trace-grid">
      <article class="panel trace-list">
        <div class="panel-head">
          <h2>Trace 列表</h2>
          <span>{{ traces.length }} 条</span>
        </div>
        <button
          v-for="item in traces"
          :key="item.traceId"
          class="trace-item"
          :class="{ active: currentTraceId === item.traceId }"
          @click="selectTrace(item.traceId)"
        >
          <span class="status-dot" :class="item.status.toLowerCase()"></span>
          <span class="trace-main">
            <strong>{{ item.question || item.traceId }}</strong>
            <small>{{ item.traceId }}</small>
          </span>
          <span class="latency">{{ item.totalLatencyMs || 0 }} ms</span>
        </button>
      </article>

      <article class="panel trace-detail">
        <div class="panel-head">
          <h2>Span 时间线</h2>
          <span>{{ detail?.spans?.length || 0 }} 个 Span</span>
        </div>
        <div v-if="detail?.trace" class="trace-summary">
          <div>
            <span>模型</span>
            <strong>{{ detail.trace.modelName || '-' }}</strong>
          </div>
          <div>
            <span>状态</span>
            <strong>{{ detail.trace.status }}</strong>
          </div>
          <div>
            <span>耗时</span>
            <strong>{{ detail.trace.totalLatencyMs || 0 }} ms</strong>
          </div>
        </div>

        <div class="timeline">
          <div v-for="span in detail?.spans || []" :key="span.spanId" class="span-row">
            <div class="span-type">{{ span.spanType }}</div>
            <div class="span-body">
              <div class="span-top">
                <strong>{{ span.name }}</strong>
                <el-tag size="small" :type="span.status === 'OK' ? 'success' : 'warning'">{{ span.status }}</el-tag>
              </div>
              <p v-if="span.inputSummary">输入：{{ span.inputSummary }}</p>
              <p v-if="span.outputSummary">输出：{{ span.outputSummary }}</p>
              <small>{{ span.latencyMs || 0 }} ms</small>
            </div>
          </div>
          <el-empty v-if="!detail?.spans?.length" description="暂无 Trace，请先发起一次问答" />
        </div>
      </article>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h2>安全事件</h2>
        <span>{{ safetyEvents.length }} 条</span>
      </div>
      <el-table :data="safetyEvents" height="260" empty-text="暂无安全事件">
        <el-table-column prop="riskType" label="风险类型" width="180" />
        <el-table-column prop="riskLevel" label="等级" width="100" />
        <el-table-column prop="action" label="动作" width="100" />
        <el-table-column prop="matchedRule" label="规则" width="160" />
        <el-table-column prop="inputSummary" label="输入摘要" min-width="320" show-overflow-tooltip />
        <el-table-column prop="traceId" label="Trace" width="260" show-overflow-tooltip />
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { agentTraceApi, type SafetyEvent, type TraceDetail, type TraceRecord } from '@/api/agentTrace'

const traces = ref<TraceRecord[]>([])
const detail = ref<TraceDetail | null>(null)
const safetyEvents = ref<SafetyEvent[]>([])
const currentTraceId = ref('')

async function refresh() {
  const [traceList, events] = await Promise.all([
    agentTraceApi.list(),
    agentTraceApi.safetyEvents(),
  ])
  traces.value = traceList
  safetyEvents.value = events
  if (!currentTraceId.value && traceList.length) {
    const firstTrace = traceList[0]
    if (firstTrace) {
      await selectTrace(firstTrace.traceId)
    }
  } else if (currentTraceId.value) {
    await selectTrace(currentTraceId.value)
  }
}

async function selectTrace(traceId: string) {
  currentTraceId.value = traceId
  detail.value = await agentTraceApi.detail(traceId)
}

onMounted(refresh)
</script>

<style scoped>
.trace-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  color: #172033;
}

.page-head,
.panel-head,
.trace-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-head h1,
.panel-head h2 {
  margin: 0;
}

.page-head h1 {
  font-size: 24px;
}

.page-head p,
.panel-head span,
.trace-summary span {
  margin: 4px 0 0;
  color: #7c8798;
}

.trace-grid {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 16px;
}

.panel {
  background: #fff;
  border: 1px solid #e7ebf3;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.04);
}

.trace-list {
  min-height: 520px;
}

.trace-item {
  width: 100%;
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 12px;
  margin-top: 8px;
  border: 1px solid #edf1f7;
  background: #fbfcff;
  border-radius: 8px;
  text-align: left;
  cursor: pointer;
}

.trace-item.active {
  border-color: #3d5afe;
  background: #f4f7ff;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #94a3b8;
}

.status-dot.done {
  background: #10b981;
}

.status-dot.failed {
  background: #ef4444;
}

.trace-main {
  min-width: 0;
}

.trace-main strong,
.trace-main small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-main small,
.latency {
  color: #7c8798;
  font-size: 12px;
}

.trace-summary {
  margin: 12px 0;
  justify-content: flex-start;
}

.trace-summary div {
  min-width: 140px;
}

.trace-summary strong {
  display: block;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.span-row {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: 12px;
}

.span-type {
  padding-top: 12px;
  font-size: 12px;
  font-weight: 700;
  color: #2850c8;
}

.span-body {
  border: 1px solid #edf1f7;
  border-radius: 8px;
  padding: 12px;
  background: #fbfcff;
}

.span-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.span-body p {
  margin: 8px 0 0;
  color: #566174;
  word-break: break-word;
}

.span-body small {
  display: block;
  margin-top: 8px;
  color: #7c8798;
}

@media (max-width: 1080px) {
  .trace-grid {
    grid-template-columns: 1fr;
  }
}
</style>
