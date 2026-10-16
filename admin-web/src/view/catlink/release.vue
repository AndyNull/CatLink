<template>
  <div class="gva-table-box catlink-admin-page">
    <el-tabs v-model="tab" class="catlink-tabs"><el-tab-pane label="App 版本" name="versions" /><el-tab-pane label="全员公告" name="broadcast" /></el-tabs>
    <template v-if="tab === 'versions'">
      <div class="gva-btn-list"><el-button type="primary" icon="plus" @click="versionDialog = true">发布版本</el-button></div>
      <el-table v-loading="loading" :data="versions" row-key="id">
        <el-table-column prop="versionCode" label="版本码" width="100" /><el-table-column prop="versionName" label="版本名" width="120" />
        <el-table-column prop="content" label="更新内容" min-width="260" show-overflow-tooltip /><el-table-column prop="downloadUrl" label="下载地址" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="danger" @click="removeVersion(row)">删除</el-button></template></el-table-column>
      </el-table>
    </template>
    <el-form v-else label-width="88px" style="max-width: 880px" @submit.prevent>
      <el-form-item label="公告标题"><el-input v-model="broadcast.title" maxlength="120" show-word-limit /></el-form-item>
      <el-form-item label="公告内容"><el-input v-model="broadcast.body" type="textarea" :rows="7" /></el-form-item>
      <el-form-item label="跳转链接"><el-input v-model="broadcast.link" placeholder="可留空" /></el-form-item>
      <el-form-item label="发送人群">
        <el-select v-model="broadcast.segmentType" style="width: 180px"><el-option label="全部用户" value="all" /><el-option label="所在区县" value="district" /><el-option label="关注猫状态" value="cat_status" /><el-option label="近期活跃" value="active_days" /></el-select>
        <el-input v-if="broadcast.segmentType === 'district'" v-model="broadcast.segmentValue" placeholder="区县名称" style="width: 220px; margin-left: 12px" />
        <el-select v-else-if="broadcast.segmentType === 'cat_status'" v-model="broadcast.segmentValue" style="width: 180px; margin-left: 12px"><el-option label="救助中" value="rescue" /><el-option label="治疗中" value="treating" /><el-option label="待领养" value="adoptable" /><el-option label="已领养" value="adopted" /><el-option label="已归档" value="archived" /></el-select>
        <el-input-number v-else-if="broadcast.segmentType === 'active_days'" v-model="broadcast.segmentValue" :min="1" :max="365" style="margin-left: 12px" />
      </el-form-item>
      <el-form-item label="定时发送"><el-date-picker v-model="broadcast.scheduledAt" type="datetime" placeholder="留空立即发送" /></el-form-item>
      <el-form-item><el-button type="primary" @click="submitBroadcast">{{ broadcast.scheduledAt ? '创建定时公告' : '立即发布' }}</el-button></el-form-item>
      <el-table :data="campaigns" row-key="id">
        <el-table-column prop="title" label="公告" min-width="180" show-overflow-tooltip />
        <el-table-column label="分群" width="150"><template #default="{ row }">{{ segmentLabel(row) }}</template></el-table-column>
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }">{{ statusLabel[row.status] || row.status }}</template></el-table-column>
        <el-table-column label="触达/已读" width="130"><template #default="{ row }">{{ row.deliveredCount }}/{{ row.readCount }}/{{ row.audienceCount }}</template></el-table-column>
        <el-table-column prop="scheduledAt" label="发送时间" width="180" />
        <el-table-column label="操作" width="80"><template #default="{ row }"><el-button v-if="row.status !== 'withdrawn'" link type="danger" @click="withdraw(row)">撤回</el-button></template></el-table-column>
      </el-table>
    </el-form>

    <el-dialog v-model="versionDialog" title="发布 App 版本" width="560px">
      <el-form label-width="88px"><el-form-item label="版本码"><el-input-number v-model="version.versionCode" :min="1" /></el-form-item><el-form-item label="版本名"><el-input v-model="version.versionName" /></el-form-item><el-form-item label="更新内容"><el-input v-model="version.content" type="textarea" :rows="4" /></el-form-item><el-form-item label="下载地址"><el-input v-model="version.downloadUrl" placeholder="https://" /></el-form-item></el-form>
      <template #footer><el-button @click="versionDialog = false">取消</el-button><el-button type="primary" @click="submitVersion">发布</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { broadcastCatlinkNotification, createCatlinkVersion, deleteCatlinkVersion, getCatlinkAnnouncementCampaigns, getCatlinkVersions, withdrawCatlinkAnnouncement } from '@/api/catlink'

defineOptions({ name: 'ReleaseAdmin' })
const tab = ref('versions'), versions = ref([]), loading = ref(false), versionDialog = ref(false)
const campaigns = ref([])
const version = reactive({ versionCode: 1, versionName: '', content: '', downloadUrl: '' })
const broadcast = reactive({ title: '', body: '', link: '', segmentType: 'all', segmentValue: '', scheduledAt: null })
const statusLabel = { scheduled: '待发送', sending: '发送中', sent: '已发送', failed: '失败', withdrawn: '已撤回' }
const catStatusLabel = { rescue: '救助中', treating: '治疗中', adoptable: '待领养', adopted: '已领养', archived: '已归档' }
const segmentLabel = (row) => ({ all: '全部用户', district: `区县：${row.segmentValue}`, cat_status: `猫状态：${catStatusLabel[row.segmentValue] || row.segmentValue}`, active_days: `${row.segmentValue} 天活跃` }[row.segmentType] || row.segmentType)
const load = async () => { loading.value = true; const [versionRes, campaignRes] = await Promise.all([getCatlinkVersions(), getCatlinkAnnouncementCampaigns()]); versions.value = versionRes.data?.items || []; campaigns.value = campaignRes.data?.items || []; loading.value = false }
const submitVersion = async () => { await createCatlinkVersion(version); versionDialog.value = false; ElMessage.success('版本已发布'); load() }
const removeVersion = async (row) => { await ElMessageBox.confirm('删除这个版本记录？', '确认操作', { type: 'warning' }); await deleteCatlinkVersion(row.id); load() }
const submitBroadcast = async () => { await ElMessageBox.confirm('确认创建这条公告？', '确认发布', { type: 'warning' }); const payload = { ...broadcast, segmentValue: String(broadcast.segmentValue || ''), scheduledAt: broadcast.scheduledAt ? broadcast.scheduledAt.toISOString() : '' }; const res = await broadcastCatlinkNotification(payload); ElMessage.success(res.data?.status === 'scheduled' ? '定时公告已创建' : `已发送给 ${res.data?.sent || 0} 位用户`); Object.assign(broadcast, { title: '', body: '', link: '', segmentType: 'all', segmentValue: '', scheduledAt: null }); load() }
const withdraw = async (row) => { await ElMessageBox.confirm('撤回后用户端不再显示这条公告，是否继续？', '撤回公告', { type: 'warning' }); await withdrawCatlinkAnnouncement(row.id); ElMessage.success('公告已撤回'); load() }
onMounted(load)
</script>

<style scoped>
.catlink-admin-page { display: flex; flex-direction: column; gap: 16px; }
.catlink-admin-page :deep(.catlink-tabs .el-tabs__header), .catlink-admin-page :deep(.gva-btn-list) { margin: 0; }
</style>
