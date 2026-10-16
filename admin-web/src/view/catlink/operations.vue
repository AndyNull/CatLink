<template>
  <div class="catlink-admin-page">
    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane label="运行指标" name="metrics" />
      <el-tab-pane label="身份认证" name="identity" />
      <el-tab-pane label="保留策略" name="retention" />
      <el-tab-pane label="邮件服务" name="email" />
    </el-tabs>
    <div v-if="tab === 'metrics'" class="metric-grid">
      <el-card v-for="(value, key) in metrics.counters" :key="key"><template #header>{{ metricLabel[key] || key }}</template><strong>{{ value }}</strong></el-card>
    </div>
    <el-table v-else-if="tab === 'identity'" :data="identities" row-key="id">
      <el-table-column prop="id" label="ID" width="80" /><el-table-column prop="userId" label="用户 ID" width="100" /><el-table-column prop="kind" label="类型" width="140" /><el-table-column prop="displayName" label="名称" min-width="180" /><el-table-column prop="status" label="状态" width="120" />
      <el-table-column label="操作" width="180"><template #default="{ row }"><el-button link type="success" @click="resolve(row, 'verified')">通过</el-button><el-button link type="danger" @click="resolve(row, 'rejected')">驳回</el-button></template></el-table-column>
    </el-table>
    <el-table v-else-if="tab === 'retention'" :data="policies" row-key="id">
      <el-table-column prop="kind" label="对象" min-width="180" /><el-table-column label="待清理" width="100"><template #default="{ row }">{{ preview[row.kind] || 0 }}</template></el-table-column><el-table-column label="保留天数" width="180"><template #default="{ row }"><el-input-number v-model="row.days" :min="1" :max="3650" /></template></el-table-column><el-table-column label="启用" width="100"><template #default="{ row }"><el-switch v-model="row.enabled" /></template></el-table-column><el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="save(row)">保存</el-button></template></el-table-column>
    </el-table>
    <el-form v-else class="email-form" label-width="130px">
      <el-form-item label="启用 SMTP"><el-switch v-model="email.enabled" /></el-form-item>
      <el-form-item label="运行状态"><el-tag :type="email.ready ? 'success' : 'info'">{{ email.ready ? '配置就绪' : '尚未就绪' }}</el-tag></el-form-item>
      <el-form-item label="服务器"><el-input v-model.trim="email.host" placeholder="smtp.example.com" /></el-form-item>
      <el-form-item label="端口"><el-input-number v-model="email.port" :min="1" :max="65535" /></el-form-item>
      <el-form-item label="TLS"><el-select v-model="email.tlsMode"><el-option label="隐式 TLS" value="implicit" /><el-option label="STARTTLS" value="starttls" /></el-select></el-form-item>
      <el-form-item label="发件邮箱"><el-input v-model.trim="email.from" /></el-form-item>
      <el-form-item label="登录用户名"><el-input v-model.trim="email.username" placeholder="默认使用发件邮箱" /></el-form-item>
      <el-form-item label="发件人名称"><el-input v-model.trim="email.nickname" /></el-form-item>
      <el-form-item label="密钥环境变量"><el-input v-model.trim="email.passwordEnv" placeholder="CATLINK_SMTP_PASSWORD" /><div class="form-tip">只填写环境变量名，不保存 SMTP 密钥。</div></el-form-item>
      <el-form-item><el-button type="primary" @click="saveEmail">保存配置</el-button></el-form-item>
      <el-divider />
      <el-form-item label="测试收件邮箱"><el-input v-model.trim="testRecipient" /><el-button class="test-button" @click="sendTestEmail">发送测试</el-button></el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getCatlinkMetrics, getCatlinkRetentionPolicies, getCatlinkRetentionPreview, saveCatlinkRetentionPolicy, getCatlinkIdentityBadges, resolveCatlinkIdentityBadge, getCatlinkEmailConfig, saveCatlinkEmailConfig, testCatlinkEmail } from '@/api/catlink'

defineOptions({ name: 'OperationsAdmin' })
const tab = ref('metrics'), metrics = ref({ counters: {} }), policies = ref([]), identities = ref([])
const preview = ref({})
const email = reactive({ enabled: false, ready: false, host: '', port: 465, from: '', username: '', passwordEnv: '', nickname: '有猫腻', tlsMode: 'implicit' })
const testRecipient = ref('')
const metricLabel = { users: '用户', posts: '帖子', cats: '公开猫咪', media: '媒体', notifications: '通知', outbox: '通知队列', migrationJobs: '迁移任务', missingCases: '失踪协作', rescueTasks: '救助任务' }
const load = async () => {
  if (tab.value === 'metrics') { const res = await getCatlinkMetrics(); if (res.code === 0) metrics.value = res.data }
  if (tab.value === 'retention') { const [policyRes, previewRes] = await Promise.all([getCatlinkRetentionPolicies(), getCatlinkRetentionPreview()]); if (policyRes.code === 0) policies.value = policyRes.data.items || []; if (previewRes.code === 0) preview.value = previewRes.data.counts || {} }
  if (tab.value === 'identity') { const res = await getCatlinkIdentityBadges(); if (res.code === 0) identities.value = res.data.items || [] }
  if (tab.value === 'email') { const res = await getCatlinkEmailConfig(); if (res.code === 0) Object.assign(email, res.data || {}) }
}
const save = async (row) => { const res = await saveCatlinkRetentionPolicy(row); if (res.code === 0) ElMessage.success('已保存') }
const resolve = async (row, status) => { const res = await resolveCatlinkIdentityBadge(row.id, status); if (res.code === 0) { row.status = status; ElMessage.success('已更新') } }
const saveEmail = async () => { const res = await saveCatlinkEmailConfig(email); if (res.code === 0) { Object.assign(email, res.data || {}); ElMessage.success('邮件配置已保存') } }
const sendTestEmail = async () => { const res = await testCatlinkEmail(testRecipient.value); if (res.code === 0) ElMessage.success('测试邮件已发送') }
onMounted(load)
</script>

<style scoped>
.catlink-admin-page { display: flex; flex-direction: column; gap: 16px; }
.metric-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 16px; }
.metric-grid strong { font-size: 24px; }
.email-form { max-width: 760px; }
.form-tip, .field-tip { color: var(--el-text-color-secondary); font-size: 12px; margin-left: 8px; }
.test-button { margin-left: 8px; }
</style>
