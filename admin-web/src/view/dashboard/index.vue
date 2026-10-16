<template>
  <div class="gva-container2">
    <div class="flex items-center justify-between pb-4">
      <div>
        <h1 class="text-xl font-semibold">运营概览</h1>
        <p class="mt-1 text-sm text-muted-foreground">{{ today }}</p>
      </div>
      <el-button icon="refresh" @click="load">刷新</el-button>
    </div>

    <div class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
      <gva-card v-for="item in statCards" :key="item.key">
        <div class="text-sm text-muted-foreground">{{ item.label }}</div>
        <div class="mt-2 text-2xl font-semibold">{{ stats[item.key] ?? 0 }}</div>
      </gva-card>
    </div>

    <div class="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-2">
      <gva-card title="待处理举报">
        <el-table :data="reports" empty-text="没有待处理举报">
          <el-table-column prop="targetType" label="对象" width="90" />
          <el-table-column prop="reason" label="原因" min-width="220" show-overflow-tooltip />
          <el-table-column width="80">
            <template #default><el-button link type="primary" @click="go('communityAdmin')">处理</el-button></template>
          </el-table-column>
        </el-table>
      </gva-card>

      <gva-card title="待审核领养申请">
        <el-table :data="adoptions" empty-text="没有待审核申请">
          <el-table-column prop="catId" label="猫咪 ID" width="100" />
          <el-table-column prop="message" label="申请说明" min-width="220" show-overflow-tooltip />
          <el-table-column width="80">
            <template #default><el-button link type="primary" @click="go('catAdmin')">审核</el-button></template>
          </el-table-column>
        </el-table>
      </gva-card>

      <gva-card title="最近社区内容">
        <el-table :data="posts" empty-text="暂无帖子">
          <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="topic" label="板块" width="90" />
          <el-table-column prop="commentCount" label="评论" width="70" />
        </el-table>
      </gva-card>

      <gva-card title="最近公开猫咪">
        <el-table :data="cats" empty-text="暂无公开猫咪">
          <el-table-column prop="nickname" label="昵称" width="110" />
          <el-table-column prop="locationName" label="地区" min-width="180" />
          <el-table-column label="状态" width="140"><template #default="{ row }">{{ catStatusText(row) }}</template></el-table-column>
        </el-table>
      </gva-card>

      <gva-card title="猫咪状态分布">
        <el-table :data="catStatusRows" empty-text="暂无公开猫咪">
          <el-table-column prop="label" label="主状态" />
          <el-table-column prop="count" label="数量" width="90" />
        </el-table>
      </gva-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { GvaCard } from './components'
import { getCatlinkAdoptions, getCatlinkCats, getCatlinkPosts, getCatlinkReports, getCatlinkStats } from '@/api/catlink'

defineOptions({ name: 'Dashboard' })

const router = useRouter()
const stats = reactive({})
const reports = ref([])
const adoptions = ref([])
const posts = ref([])
const cats = ref([])
const statCards = [
  { key: 'users', label: '用户' },
  { key: 'cats', label: '公开猫咪' },
  { key: 'posts', label: '社区帖子' },
  { key: 'pendingReports', label: '待处理举报' },
  { key: 'adoptions', label: '领养申请' },
  { key: 'ads', label: '广告' }
]
const catStateLabels = { rescue: '救助中', treating: '治疗中', adoptable: '待领养', adopted: '已领养', archived: '已归档' }
const archiveReasonLabels = { lost: '失踪', deceased: '离世', other: '其他' }
const catStatusRows = computed(() => Object.entries(catStateLabels).map(([key, label]) => ({ label, count: stats.catStatuses?.[key] || 0 })))
const catStatusText = (row) => row.status === 'archived' && row.archiveReason ? `已归档 · ${archiveReasonLabels[row.archiveReason] || row.archiveReason}` : (catStateLabels[row.status] || row.status)
const today = computed(() => new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }))
const data = (res) => res?.data || {}

const load = async () => {
  const [statsRes, reportsRes, adoptionsRes, postsRes, catsRes] = await Promise.all([
    getCatlinkStats(),
    getCatlinkReports({ page: 1, pageSize: 5, status: 'pending' }),
    getCatlinkAdoptions({ page: 1, pageSize: 5, status: 'pending' }),
    getCatlinkPosts({ page: 1, pageSize: 5 }),
    getCatlinkCats({ page: 1, pageSize: 5 })
  ])
  Object.assign(stats, data(statsRes))
  reports.value = data(reportsRes).items || []
  adoptions.value = data(adoptionsRes).items || []
  posts.value = data(postsRes).items || []
  cats.value = data(catsRes).items || []
}

const go = (name) => router.push({ name })
onMounted(load)
</script>
