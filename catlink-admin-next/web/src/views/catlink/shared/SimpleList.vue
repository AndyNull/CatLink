<template>
  <div class="app-container">
    <h2>{{ title }}</h2>
    <div class="filter-container">
      <el-input v-model="query.keyword" placeholder="搜索" clearable />
      <el-button @click="load(true)">查询</el-button>
    </div>
    <el-table v-loading="loading" :data="rows">
      <el-table-column v-for="column in columns" :key="column[0]" :prop="column[0]" :label="column[1]" min-width="120" show-overflow-tooltip />
      <el-table-column v-if="$slots.actions" label="操作" width="160">
        <template #default="{ row }"><slot name="actions" :row="row" :reload="() => load(true)" /></template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" v-model:page="query.page" v-model:limit="query.pageSize" :total="total" @pagination="load(true)" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

type Row = Record<string, unknown>
type PageResponse = { data: { items: Row[]; total: number }}
type CachedPage = { at: number; items: Row[]; total: number }
const pageCache = new Map<string, CachedPage>()
const CACHE_TTL_MS = 30_000
const props = defineProps<{ title: string; columns: string[][]; loader: (params: Record<string, unknown>) => Promise<PageResponse> }>()
const query = reactive({ page: 1, pageSize: 20, keyword: '' })
const rows = ref<Row[]>([])
const total = ref(0)
const loading = ref(false)
async function load(force = false) {
  const key = `${props.title}:${query.page}:${query.pageSize}:${query.keyword}`
  const cached = pageCache.get(key)
  if (!force && cached && Date.now() - cached.at < CACHE_TTL_MS) {
    rows.value = cached.items
    total.value = cached.total
    return
  }
  loading.value = true
  try {
    const data = (await props.loader(query)).data
    rows.value = data.items || []
    total.value = data.total || 0
    pageCache.set(key, { at: Date.now(), items: rows.value, total: total.value })
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
