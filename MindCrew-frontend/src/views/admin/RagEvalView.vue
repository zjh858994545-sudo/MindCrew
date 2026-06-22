<template>
  <div class="rag-eval-page">
    <header class="page-head">
      <div>
        <h1>RAG 评测</h1>
        <p>Golden QA、策略对比、指标报告</p>
      </div>
      <div class="head-actions">
        <el-input-number v-model="topK" :min="1" :max="10" size="large" controls-position="right" />
        <el-checkbox v-model="includeSecurity" size="large">安全 Case</el-checkbox>
        <el-button type="primary" size="large" :loading="running" @click="runEval">
          <el-icon><VideoPlay /></el-icon>
          运行评测
        </el-button>
      </div>
    </header>

    <section class="metric-grid">
      <article class="metric">
        <span class="metric-label">Case</span>
        <strong>{{ report?.caseCount ?? cases.length }}</strong>
      </article>
      <article class="metric">
        <span class="metric-label">Corpus</span>
        <strong>{{ report?.corpusChunkCount ?? '-' }}</strong>
      </article>
      <article class="metric">
        <span class="metric-label">TopK</span>
        <strong>{{ report?.topK ?? topK }}</strong>
      </article>
      <article class="metric">
        <span class="metric-label">耗时</span>
        <strong>{{ report ? `${report.elapsedMs} ms` : '-' }}</strong>
      </article>
    </section>

    <section v-if="report" class="report-strip">
      <div>
        <span class="strip-label">Run ID</span>
        <code>{{ report.runId }}</code>
      </div>
      <div>
        <span class="strip-label">Report</span>
        <code>{{ report.reportPath }}</code>
      </div>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h2>策略对比</h2>
        <span>{{ report?.datasetName || 'MindCrew 内置 Golden QA 评测集' }}</span>
      </div>
      <el-table :data="report?.strategies || []" height="260" empty-text="点击运行评测生成结果">
        <el-table-column prop="strategy" label="策略" width="170" />
        <el-table-column label="Recall@K" width="130">
          <template #default="{ row }">{{ percent(row.summary.recallAtK) }}</template>
        </el-table-column>
        <el-table-column label="Hit@K" width="110">
          <template #default="{ row }">{{ percent(row.summary.hitAtK) }}</template>
        </el-table-column>
        <el-table-column label="MRR" width="110">
          <template #default="{ row }">{{ row.summary.mrr.toFixed(4) }}</template>
        </el-table-column>
        <el-table-column label="CitationHit" width="130">
          <template #default="{ row }">{{ percent(row.summary.citationHit) }}</template>
        </el-table-column>
        <el-table-column label="拒答正确率" width="130">
          <template #default="{ row }">
            {{ row.summary.refusalAccuracy == null ? '-' : percent(row.summary.refusalAccuracy) }}
          </template>
        </el-table-column>
        <el-table-column label="平均耗时">
          <template #default="{ row }">{{ row.summary.avgLatencyMs.toFixed(1) }} ms</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="split">
      <article class="panel">
        <div class="panel-head">
          <h2>Golden QA</h2>
          <span>{{ cases.length }} 条</span>
        </div>
        <el-table :data="cases" height="420">
          <el-table-column prop="id" label="ID" width="92" />
          <el-table-column prop="category" label="分类" width="100" />
          <el-table-column prop="question" label="问题" min-width="260" show-overflow-tooltip />
          <el-table-column label="期望来源" width="170">
            <template #default="{ row }">
              <el-tag v-for="id in row.expectedChunkIds" :key="id" size="small">{{ id }}</el-tag>
              <el-tag v-if="row.shouldRefuse" size="small" type="danger">REFUSE</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article class="panel">
        <div class="panel-head">
          <h2>Case 明细</h2>
          <el-select v-model="selectedStrategy" placeholder="策略" size="small" style="width: 170px">
            <el-option
              v-for="s in report?.strategies || []"
              :key="s.strategy"
              :label="s.strategy"
              :value="s.strategy"
            />
          </el-select>
        </div>
        <el-table :data="selectedResults" height="420" empty-text="暂无结果">
          <el-table-column prop="caseId" label="Case" width="92" />
          <el-table-column prop="question" label="问题" min-width="220" show-overflow-tooltip />
          <el-table-column label="Hit" width="80">
            <template #default="{ row }">
              <el-tag :type="row.hitAtK > 0 ? 'success' : 'danger'" size="small">
                {{ row.hitAtK > 0 ? 'Y' : 'N' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="MRR" width="88">
            <template #default="{ row }">{{ row.mrr.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="安全" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.safetyReason" type="warning" size="small">BLOCK</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ragEvalApi, type RagEvalCase, type RagEvalReport } from '@/api/ragEval'

const cases = ref<RagEvalCase[]>([])
const report = ref<RagEvalReport | null>(null)
const running = ref(false)
const topK = ref(5)
const includeSecurity = ref(true)
const selectedStrategy = ref('')

const selectedResults = computed(() => {
  const strategies = report.value?.strategies || []
  const current = strategies.find(s => s.strategy === selectedStrategy.value) || strategies[0]
  return current?.results || []
})

watch(report, value => {
  selectedStrategy.value = value?.strategies?.[0]?.strategy || ''
})

const percent = (value: number) => `${(value * 100).toFixed(1)}%`

async function loadCases() {
  cases.value = await ragEvalApi.cases(includeSecurity.value)
}

async function runEval() {
  running.value = true
  try {
    report.value = await ragEvalApi.run({ topK: topK.value, includeSecurity: includeSecurity.value })
    ElMessage.success('RAG 评测完成')
  } finally {
    running.value = false
  }
}

onMounted(async () => {
  await loadCases()
})

watch(includeSecurity, loadCases)
</script>

<style scoped>
.rag-eval-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  color: #172033;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.page-head h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 760;
}

.page-head p {
  margin: 4px 0 0;
  color: #697386;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(140px, 1fr));
  gap: 12px;
}

.metric,
.panel,
.report-strip {
  background: #fff;
  border: 1px solid #e7ebf3;
  border-radius: 8px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.04);
}

.metric {
  padding: 16px;
}

.metric-label,
.strip-label {
  display: block;
  color: #7c8798;
  font-size: 12px;
  margin-bottom: 6px;
}

.metric strong {
  font-size: 24px;
}

.report-strip {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  padding: 14px 16px;
}

code {
  color: #2850c8;
  word-break: break-all;
}

.panel {
  padding: 16px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel-head h2 {
  margin: 0;
  font-size: 16px;
}

.panel-head span {
  color: #7c8798;
}

.split {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
}

.el-tag + .el-tag {
  margin-left: 4px;
}

@media (max-width: 1180px) {
  .page-head,
  .head-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .metric-grid,
  .split,
  .report-strip {
    grid-template-columns: 1fr;
  }
}
</style>
