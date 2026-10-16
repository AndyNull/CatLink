<template>
  <div>
    <div class="gva-search-box">
      <el-form inline>
        <el-form-item label="关键词"><el-input v-model="keyword" clearable :placeholder="tab === 'cats' ? '昵称、故事或地区' : '猫咪、申请人或发布者'" @keyup.enter="search" /></el-form-item>
        <el-form-item v-if="tab === 'cats'" label="主状态"><el-select v-model="catStatus" clearable style="width: 140px"><el-option v-for="item in catStates" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item v-else label="状态">
          <el-select v-model="status" clearable style="width: 140px"><el-option v-for="item in adoptionStates" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" icon="search" @click="search">查询</el-button><el-button icon="refresh" @click="reset">重置</el-button></el-form-item>
      </el-form>
    </div>
    <div class="gva-table-box catlink-table-section">
      <el-tabs v-model="tab" class="catlink-tabs" @tab-change="search"><el-tab-pane label="公开猫咪" name="cats" /><el-tab-pane label="领养申请" name="adoptions" /></el-tabs>
      <el-table v-if="tab === 'cats'" v-loading="loading" :data="rows" row-key="id">
        <el-table-column prop="id" label="ID" width="70" /><el-table-column prop="nickname" label="昵称" width="120" /><el-table-column prop="locationName" label="地区" min-width="160" />
        <el-table-column label="主状态" width="130"><template #default="{ row }">{{ catStateLabel(row.status) }}</template></el-table-column>
        <el-table-column label="归档原因" width="100"><template #default="{ row }">{{ archiveReasonLabel[row.archiveReason] || '-' }}</template></el-table-column>
        <el-table-column label="发布状态" width="110"><template #default="{ row }"><el-tag>{{ stateLabel(row.state) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="280" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openStatus(row)">修改主状态</el-button><el-button link type="success" @click="setState(row, 'published')">发布</el-button><el-button link type="warning" @click="setState(row, 'paused')">暂停</el-button><el-button link type="danger" @click="setState(row, 'archived')">下架</el-button></template></el-table-column>
      </el-table>
      <el-table v-else v-loading="loading" :data="rows" row-key="id">
        <el-table-column prop="id" label="ID" width="70" /><el-table-column prop="catNickname" label="猫咪" width="120" /><el-table-column prop="applicantName" label="申请人" width="120" /><el-table-column prop="ownerName" label="发布者" width="120" />
        <el-table-column prop="message" label="申请说明" min-width="220" show-overflow-tooltip /><el-table-column prop="contactSummary" label="联系方式" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="100"><template #default="{ row }">{{ adoptionStateLabel(row.status) }}</template></el-table-column>
        <el-table-column label="操作" width="250" fixed="right"><template #default="{ row }"><template v-if="activeAdoptionStates.includes(row.status)"><el-button v-if="row.status === 'pending'" link @click="openAdoption(row, 'contact')">沟通</el-button><el-button v-if="row.status !== 'home_visit'" link @click="openAdoption(row, 'home_visit')">家访</el-button><el-button link type="success" @click="openAdoption(row, 'approved')">通过</el-button><el-button link type="danger" @click="openAdoption(row, 'rejected')">拒绝</el-button></template><span v-else>-</span></template></el-table-column>
      </el-table>
      <div class="gva-pagination"><el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="load" /></div>
    </div>
    <el-dialog v-model="statusDialog" title="修改猫咪主状态" width="min(520px, 92vw)">
      <el-form label-width="88px">
        <el-form-item label="主状态"><el-select v-model="statusForm.status" style="width: 100%"><el-option v-for="item in catStates" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item v-if="statusForm.status === 'archived'" label="归档原因" required><el-select v-model="statusForm.archiveReason" style="width: 100%"><el-option v-for="item in archiveReasons" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="状态说明"><el-input v-model="statusForm.statusReason" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="statusDialog = false">取消</el-button><el-button type="primary" :disabled="statusForm.status === 'archived' && !statusForm.archiveReason" @click="saveStatus">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="adoptionDialog" :title="`领养申请 · ${adoptionStateLabel(adoptionForm.status)}`" width="min(520px, 92vw)">
      <el-form label-width="80px"><el-form-item label="处理说明"><el-input v-model="adoptionForm.note" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="adoptionDialog = false">取消</el-button><el-button type="primary" @click="saveAdoption">确认</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCatlinkAdoptions, getCatlinkCats, setCatlinkAdoptionStatus, setCatlinkCatState, setCatlinkCatStatus } from '@/api/catlink'

defineOptions({ name: 'CatAdmin' })
const tab = ref('cats'), keyword = ref(''), status = ref(''), catStatus = ref(''), rows = ref([]), total = ref(0), page = ref(1), loading = ref(false)
const adoptionStates = [{ label: '待审核', value: 'pending' }, { label: '沟通中', value: 'contact' }, { label: '家访中', value: 'home_visit' }, { label: '已通过', value: 'approved' }, { label: '已拒绝', value: 'rejected' }, { label: '已撤回', value: 'withdrawn' }]
const activeAdoptionStates = ['pending', 'contact', 'home_visit']
const catStates = [{ label: '救助中', value: 'rescue' }, { label: '治疗中', value: 'treating' }, { label: '待领养', value: 'adoptable' }, { label: '已领养', value: 'adopted' }, { label: '已归档', value: 'archived' }]
const archiveReasons = [{ label: '失踪', value: 'lost' }, { label: '离世', value: 'deceased' }, { label: '其他', value: 'other' }]
const archiveReasonLabel = { lost: '失踪', deceased: '离世', other: '其他' }
const adoptionStateLabel = (value) => adoptionStates.find(item => item.value === value)?.label || value
const catStateLabel = (value) => catStates.find(item => item.value === value)?.label || value
const stateLabel = (state) => ({ published: '已发布', paused: '已暂停', archived: '已归档' }[state] || state)
const statusDialog = ref(false)
const statusForm = reactive({ id: 0, status: 'rescue', archiveReason: '', statusReason: '' })
const adoptionDialog = ref(false)
const adoptionForm = reactive({ id: 0, status: 'contact', note: '' })
const load = async () => {
  loading.value = true
  const params = { page: page.value, pageSize: 20, keyword: keyword.value, status: status.value, catStatus: catStatus.value }
  const res = tab.value === 'cats' ? await getCatlinkCats(params) : await getCatlinkAdoptions(params)
  rows.value = res.data?.items || []; total.value = res.data?.total || 0; loading.value = false
}
const search = () => { page.value = 1; load() }
const reset = () => { keyword.value = ''; status.value = ''; catStatus.value = ''; search() }
const confirm = async (message, action) => { await ElMessageBox.confirm(message, '确认操作', { type: 'warning' }); await action(); ElMessage.success('操作成功'); load() }
const setState = (row, state) => confirm(`将 ${row.nickname} 设置为${stateLabel(state)}？`, () => setCatlinkCatState(row.id, state))
const openStatus = (row) => { Object.assign(statusForm, { id: row.id, status: row.status, archiveReason: row.archiveReason || '', statusReason: row.statusReason || '' }); statusDialog.value = true }
const saveStatus = async () => { await setCatlinkCatStatus(statusForm.id, { status: statusForm.status, archiveReason: statusForm.status === 'archived' ? statusForm.archiveReason : '', statusReason: statusForm.statusReason }); statusDialog.value = false; ElMessage.success('主状态已更新'); load() }
const openAdoption = (row, status) => { Object.assign(adoptionForm, { id: row.id, status, note: '' }); adoptionDialog.value = true }
const saveAdoption = async () => { await setCatlinkAdoptionStatus(adoptionForm.id, { status: adoptionForm.status, note: adoptionForm.note }); adoptionDialog.value = false; ElMessage.success('申请状态已更新'); load() }
onMounted(load)
</script>

<style scoped>
.catlink-table-section { display: flex; flex-direction: column; gap: 16px; }
.catlink-table-section :deep(.catlink-tabs .el-tabs__header), .catlink-table-section :deep(.gva-pagination) { margin: 0; }
</style>
