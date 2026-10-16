<template>
  <PageContainer :card="false">
    <header class="dashboard-header">
      <div><h1>有猫腻运营工作台</h1><p v-if="updatedAt">更新于 {{ updatedAt }}</p></div>
      <el-button :loading="loading" @click="load">刷新数据</el-button>
    </header>
    <section v-loading="loading" class="metric-strip" aria-label="业务总览">
      <button v-for="item in coreMetrics" :key="item.key" type="button" @click="open(item.path)">
        <span>{{ item.label }}</span><strong>{{ value(item.key) }}</strong>
      </button>
    </section>
    <div class="operations-grid">
      <section class="work-section">
        <h2>待处理</h2>
        <div class="work-list">
          <button v-for="item in pendingMetrics" :key="item.key" type="button" @click="open(item.path)">
            <span>{{ item.label }}</span><strong>{{ value(item.key) }}</strong><span>处理</span>
          </button>
        </div>
      </section>
      <section class="work-section">
        <h2>今日动态</h2>
        <div class="work-list today-list">
          <div v-for="item in todayMetrics" :key="item.key"><span>{{ item.label }}</span><strong>{{ value(item.key) }}</strong></div>
        </div>
      </section>
    </div>
    <section class="entry-section">
      <h2>业务入口</h2>
      <div class="entry-groups">
        <div v-for="group in entryGroups" :key="group.label">
          <h3>{{ group.label }}</h3>
          <button v-for="item in group.items" :key="item.path" type="button" @click="open(item.path)">{{ item.label }}</button>
        </div>
      </div>
    </section>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageContainer from '@/components/PageContainer/index.vue'
import { getCatLinkStats, type CatLinkStats } from '@/api/catlink'

// eslint-disable-next-line vue/multi-word-component-names
defineOptions({ name: 'Dashboard' })
const router = useRouter()
const loading = ref(false)
const stats = ref<CatLinkStats>({})
const updatedAt = ref('')
const coreMetrics = [
  { key: 'users', label: '用户账号', path: '/catlink/users' },
  { key: 'privateCats', label: '完整猫咪档案', path: '/catlink/cats' },
  { key: 'publicCats', label: '公开猫咪', path: '/catlink/public-cats' },
  { key: 'posts', label: '社区帖子', path: '/catlink/posts' },
  { key: 'comments', label: '社区评论', path: '/catlink/comments' },
  { key: 'media', label: '媒体资源', path: '/catlink/media' }
]
const todayMetrics = [
  { key: 'todayUsers', label: '新增用户' }, { key: 'todayPosts', label: '新增帖子' },
  { key: 'todayComments', label: '新增评论' }, { key: 'todayMedia', label: '新增媒体' }
]
const pendingMetrics = [
  { key: 'reportsPending', label: '待处理举报', path: '/catlink/reports' },
  { key: 'lockedPosts', label: '已锁定帖子', path: '/catlink/posts' },
  { key: 'storageMigrationsRunning', label: '存储迁移任务', path: '/catlink/bricks' },
  { key: 'unreadNotifications', label: '用户未读消息', path: '/catlink/announcements' }
]
const entryGroups = [
  { label: '用户与档案', items: [{ label: '用户账号', path: '/catlink/users' }, { label: '完整档案', path: '/catlink/cats' }, { label: '公开猫咪', path: '/catlink/public-cats' }] },
  { label: '内容与互动', items: [{ label: '社区帖子', path: '/catlink/posts' }, { label: '社区评论', path: '/catlink/comments' }, { label: '举报处理', path: '/catlink/reports' }, { label: '公告管理', path: '/catlink/announcements' }] },
  { label: '资产与治理', items: [{ label: '媒体库', path: '/catlink/media' }, { label: '业务审计', path: '/catlink/logs' }, { label: '存储与扩展', path: '/catlink/bricks' }] }
]
const value = (key: string) => stats.value[key] ?? 0
const open = (path: string) => router.push(path)
async function load() {
  if (loading.value) return
  loading.value = true
  try {
    stats.value = (await getCatLinkStats()).data || {}
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.dashboard-header, .work-list div, .work-list button { display: flex; align-items: center; justify-content: space-between; }
.dashboard-header { margin-bottom: 24px; }
.dashboard-header h1 { margin: 0 0 4px; font-size: 24px; }
.dashboard-header p { margin: 0; }
.metric-strip { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 1px; margin-bottom: 24px; }
.metric-strip button { display: grid; gap: 8px; min-height: 88px; padding: 16px; border: 1px solid var(--ga-border-light); background: var(--ga-bg-container); color: inherit; text-align: left; cursor: pointer; }
.metric-strip strong { font-size: 24px; font-variant-numeric: tabular-nums; }
.operations-grid { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(280px, .65fr); gap: 24px; margin-bottom: 32px; }
.work-section h2, .entry-section h2 { margin: 0 0 12px; font-size: 18px; }
.work-list { border-top: 1px solid var(--ga-border-light); }
.work-list button, .work-list div { width: 100%; min-height: 48px; padding: 0 8px; border: 0; border-bottom: 1px solid var(--ga-border-light); background: transparent; color: inherit; font: inherit; text-align: left; }
.work-list button { display: grid; grid-template-columns: 1fr auto 48px; gap: 16px; cursor: pointer; }
.work-list strong { font-variant-numeric: tabular-nums; }
.entry-groups { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 24px; }
.entry-groups h3 { margin: 0 0 8px; font-size: 14px; }
.entry-groups button { min-height: 40px; padding: 0 12px; border: 0; background: transparent; color: var(--ga-brand); cursor: pointer; }
@media (max-width: 900px) { .operations-grid { grid-template-columns: 1fr; } }
</style>
