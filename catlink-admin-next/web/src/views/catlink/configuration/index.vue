<template>
  <PageContainer>
    <el-tabs v-model="tab" @tab-change="loadTab">
      <el-tab-pane label="品牌" name="brand">
        <el-form label-width="90px" class="config-form">
          <el-form-item label="应用名称"><el-input v-model="brand.name" /></el-form-item>
          <el-form-item label="标志地址"><el-input v-model="brand.logoUrl" /></el-form-item>
          <el-form-item><el-button v-permisaction="['catlink:brand:save']" type="primary" @click="saveBrandConfig">保存</el-button></el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="登录与验证码" name="security">
        <el-form label-width="110px" class="config-form">
          <el-form-item label="验证码策略">
            <el-select v-model="security.captchaOpen">
              <el-option label="关闭" :value="0" /><el-option label="失败后启用" :value="1" /><el-option label="始终启用" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item label="验证码长度"><el-input-number v-model="security.captchaLength" :min="4" :max="8" /></el-form-item>
          <el-form-item label="图片宽度"><el-input-number v-model="security.captchaWidth" :min="80" :max="640" /></el-form-item>
          <el-form-item label="图片高度"><el-input-number v-model="security.captchaHeight" :min="32" :max="320" /></el-form-item>
          <el-form-item><el-button v-permisaction="['catlink:security:save']" type="primary" @click="saveSecurity">保存</el-button></el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="定位服务" name="location">
        <el-form label-width="120px" class="config-form">
          <el-form-item label="地图服务商">
            <el-select v-model="location.provider" style="width: 220px" @change="onLocationProviderChange">
              <el-option label="高德地图" value="amap" />
              <el-option label="百度地图" value="baidu" />
              <el-option label="OpenStreetMap" value="osm" />
              <el-option label="自定义服务" value="custom" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="location.provider === 'amap'" label="高德 Web Key">
            <el-input v-model="location.amapKey" type="password" show-password :placeholder="location.amapKeyConfigured ? '已配置，留空不修改' : '填写服务端 Key'" />
          </el-form-item>
          <el-form-item v-if="location.provider === 'baidu'" label="百度 Web AK">
            <el-input v-model="location.baiduAk" type="password" show-password :placeholder="location.baiduAkConfigured ? '已配置，留空不修改' : '填写服务端 AK'" />
          </el-form-item>
          <el-form-item label="地点搜索地址"><el-input v-model="location.searchUrl" placeholder="https://.../search" /></el-form-item>
          <el-form-item label="反向解析地址"><el-input v-model="location.reverseUrl" placeholder="https://.../reverse" /></el-form-item>
          <el-form-item label="IP 定位地址"><el-input v-model="location.ipUrl" placeholder="可选，地址中必须包含 {ip}" /></el-form-item>
          <el-form-item label="信任代理定位头"><el-switch v-model="location.trustProxyHeaders" /></el-form-item>
          <el-alert title="App 只调用服务端接口；服务商和 Key 保存到数据库，切换后立即生效，不需要重新编译 App。" type="info" :closable="false" />
          <el-form-item><el-button v-permisaction="['catlink:integrations:save']" type="primary" @click="saveLocationConfig">保存</el-button></el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="图片限制" name="media-limits">
        <el-form label-width="130px" class="config-form">
          <el-form-item label="社区动态"><el-input-number v-model="mediaLimits.post" :min="1" :max="50" /></el-form-item>
          <el-form-item label="猫咪相册"><el-input-number v-model="mediaLimits.catAlbum" :min="1" :max="50" /></el-form-item>
          <el-form-item label="领养现场"><el-input-number v-model="mediaLimits.adoptionSite" :min="1" :max="50" /></el-form-item>
          <el-form-item label="合同附件"><el-input-number v-model="mediaLimits.contract" :min="1" :max="50" /></el-form-item>
          <el-form-item label="回访截图"><el-input-number v-model="mediaLimits.followUp" :min="1" :max="50" /></el-form-item>
          <el-form-item label="单条私聊"><el-input-number v-model="mediaLimits.chat" :min="1" :max="50" /></el-form-item>
          <el-form-item label="单文件上限"><el-input-number v-model="mediaMaxMb" :min="1" :max="200" /><span class="unit">MB</span></el-form-item>
          <el-form-item v-for="item in mediaCategories" :key="item.key" :label="`${item.label}上限`"><el-input-number :model-value="mediaCategoryMb(item.key)" :min="1" :max="mediaMaxMb" @update:model-value="setMediaCategoryMb(item.key, $event)" /><span class="unit">MB</span></el-form-item>
          <el-form-item label="允许格式">
            <el-checkbox-group v-model="mediaFormats">
              <el-checkbox label="jpg">JPEG</el-checkbox>
              <el-checkbox label="png">PNG</el-checkbox>
              <el-checkbox label="webp">WebP</el-checkbox>
              <el-checkbox label="pdf">PDF</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item><el-button v-permisaction="['catlink:mediaLimits:save']" type="primary" @click="saveMediaLimitConfig">保存</el-button></el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="邮件服务" name="email">
        <el-form label-width="100px" class="config-form">
          <el-form-item label="启用"><el-switch v-model="smtp.enabled" /></el-form-item>
          <el-form-item label="SMTP"><el-input v-model="smtp.host" /></el-form-item>
          <el-form-item label="端口"><el-input-number v-model="smtp.port" :min="1" :max="65535" /></el-form-item>
          <el-form-item label="发件邮箱"><el-input v-model="smtp.from" /></el-form-item>
          <el-form-item label="登录账号"><el-input v-model="smtp.username" /></el-form-item>
          <el-form-item label="授权码"><el-input v-model="smtpSecret" type="password" show-password :placeholder="smtpSecretConfigured ? '已配置，留空不修改' : ''" /></el-form-item>
          <el-form-item label="发件名称"><el-input v-model="smtp.nickname" /></el-form-item>
          <el-form-item label="加密方式"><el-select v-model="smtp.tlsMode"><el-option label="隐式 TLS" value="implicit" /><el-option label="STARTTLS" value="starttls" /></el-select></el-form-item>
          <el-form-item>
            <el-button v-permisaction="['catlink:integrations:save']" type="primary" @click="saveSMTP">保存</el-button>
            <el-input v-model="testRecipient" placeholder="测试收件邮箱" class="test-recipient" />
            <el-button v-permisaction="['catlink:email:test']" @click="sendTestEmail">测试发信</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="templates" row-key="purpose">
          <el-table-column label="用途" width="120"><template #default="{ row }">{{ purposeLabel(row.purpose) }}</template></el-table-column>
          <el-table-column label="主题" min-width="180"><template #default="{ row }"><el-input v-model="row.subject" /></template></el-table-column>
          <el-table-column label="标题" min-width="160"><template #default="{ row }"><el-input v-model="row.title" /></template></el-table-column>
          <el-table-column label="说明" min-width="260"><template #default="{ row }"><el-input v-model="row.description" /></template></el-table-column>
          <el-table-column label="页脚" min-width="220"><template #default="{ row }"><el-input v-model="row.footer" /></template></el-table-column>
          <el-table-column label="操作" width="80"><template #default="{ row }"><el-button v-permisaction="['catlink:emailTemplate:save']" link type="primary" @click="saveTemplate(row)">保存</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="广告" name="ads">
        <div class="toolbar"><el-button v-permisaction="['catlink:ads:add']" type="primary" @click="openEditor('ads')">新增广告</el-button></div>
        <el-table :data="ads" row-key="id">
          <el-table-column prop="id" label="ID" width="70" /><el-table-column prop="name" label="名称" min-width="150" />
          <el-table-column prop="slot" label="位置" width="110" /><el-table-column prop="image_url" label="图片" min-width="220" show-overflow-tooltip />
          <el-table-column prop="link_url" label="跳转" min-width="220" show-overflow-tooltip /><el-table-column prop="enabled" label="启用" width="80" />
          <el-table-column label="操作" width="120"><template #default="{ row }"><el-button v-permisaction="['catlink:ads:edit']" link @click="openEditor('ads', row)">编辑</el-button><el-button v-permisaction="['catlink:ads:delete']" link type="danger" @click="remove('ads', row.id)">删除</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="版本" name="versions">
        <div class="toolbar"><el-button v-permisaction="['catlink:versions:add']" type="primary" @click="openEditor('versions')">发布版本</el-button></div>
        <el-table :data="versions" row-key="id">
          <el-table-column prop="version_code" label="版本号" width="100" /><el-table-column prop="version_name" label="版本名称" width="130" />
          <el-table-column prop="content" label="更新内容" min-width="260" /><el-table-column prop="download_url" label="下载地址" min-width="240" show-overflow-tooltip />
          <el-table-column label="操作" width="120"><template #default="{ row }"><el-button v-permisaction="['catlink:versions:edit']" link @click="openEditor('versions', row)">编辑</el-button><el-button v-permisaction="['catlink:versions:delete']" link type="danger" @click="remove('versions', row.id)">删除</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="温柔语句" name="quotes">
        <div class="toolbar"><el-button v-permisaction="['catlink:quotes:import']" :loading="generatingQuotes" @click="generateQuoteDraft">AI 生成导入</el-button><el-button v-permisaction="['catlink:quotes:import']" @click="importVisible = true">批量导入</el-button><el-button v-permisaction="['catlink:quotes:add']" type="primary" @click="openEditor('quotes')">新增语句</el-button></div>
        <el-table :data="quotes" row-key="id">
          <el-table-column prop="quote" label="语句" min-width="300" /><el-table-column prop="source" label="来源" width="140" /><el-table-column prop="enabled" label="启用" width="80" />
          <el-table-column label="操作" width="120"><template #default="{ row }"><el-button v-permisaction="['catlink:quotes:edit']" link @click="openEditor('quotes', row)">编辑</el-button><el-button v-permisaction="['catlink:quotes:delete']" link type="danger" @click="remove('quotes', row.id)">删除</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="AI" name="ai">
        <el-form label-width="100px" class="config-form">
          <el-form-item label="启用"><el-switch v-model="ai.enabled" /></el-form-item>
          <el-form-item label="接口地址"><el-input v-model="ai.baseUrl" /></el-form-item>
          <el-form-item label="模型"><el-input v-model="ai.model" /></el-form-item>
          <el-form-item label="认证方式"><el-select v-model="ai.authMode"><el-option label="Bearer API Key" value="bearer" /><el-option label="无需认证" value="none" /></el-select></el-form-item>
          <el-form-item v-if="ai.authMode !== 'none'" label="API Key"><el-input v-model="aiSecret" type="password" show-password :placeholder="aiSecretConfigured ? '已配置，留空不修改' : ''" /></el-form-item>
          <el-form-item label="超时秒数"><el-input-number v-model="ai.timeoutSeconds" :min="5" :max="120" /></el-form-item>
          <el-form-item><el-button v-permisaction="['catlink:integrations:save']" type="primary" @click="saveAI">保存</el-button><el-button v-permisaction="['catlink:ai:test']" @click="checkAI">测试连接</el-button></el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="地区库" name="regions">
        <el-form inline @submit.prevent="loadRegions">
          <el-form-item label="关键词"><el-input v-model="regionQuery.keyword" clearable placeholder="名称或编码" /></el-form-item>
          <el-form-item label="层级"><el-select v-model="regionQuery.level" clearable placeholder="全部" style="width: 130px"><el-option v-for="item in regionLevels" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="上级编码"><el-input v-model="regionQuery.parentCode" clearable /></el-form-item>
          <el-form-item><el-button native-type="submit">搜索</el-button><el-button v-permisaction="['catlink:regions:manage']" type="primary" @click="openRegion()">新增地区</el-button></el-form-item>
        </el-form>
        <el-table v-loading="regionLoading" :data="regions" row-key="id">
          <el-table-column prop="name" label="名称" min-width="160" /><el-table-column prop="code" label="编码" min-width="130" />
          <el-table-column prop="parentCode" label="上级编码" min-width="130" /><el-table-column label="层级" width="100"><template #default="{ row }">{{ regionLevelLabel(row.level) }}</template></el-table-column>
          <el-table-column prop="sort" label="排序" width="80" /><el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="120"><template #default="{ row }"><el-button v-permisaction="['catlink:regions:manage']" link type="primary" @click="openRegion(row)">编辑</el-button><el-button v-permisaction="['catlink:regions:manage']" link type="danger" @click="removeRegion(row)">删除</el-button></template></el-table-column>
        </el-table>
        <el-pagination v-model:current-page="regionQuery.pageIndex" v-model:page-size="regionQuery.pageSize" layout="total, prev, pager, next" :total="regionTotal" @current-change="loadRegions" />
      </el-tab-pane>
      <el-tab-pane label="数据保留" name="retention">
        <div class="toolbar"><el-button @click="previewRetention">刷新预览</el-button><el-button v-permisaction="['catlink:retention:run']" type="primary" @click="executeRetention">执行清理</el-button></div>
        <el-table :data="retention" row-key="kind">
          <el-table-column label="数据类型" min-width="180"><template #default="{ row }">{{ retentionLabel(row.kind) }}</template></el-table-column>
          <el-table-column label="保留天数" width="180"><template #default="{ row }"><el-input-number v-model="row.days" :min="1" :max="3650" /></template></el-table-column>
          <el-table-column label="最大条数" width="190"><template #default="{ row }"><el-input-number v-model="row.maxRows" :min="0" :max="10000000" :disabled="row.kind === 'temporary_media'" /></template></el-table-column>
          <el-table-column label="启用" width="90"><template #default="{ row }"><el-switch v-model="row.enabled" /></template></el-table-column>
          <el-table-column label="待清理" width="100"><template #default="{ row }">{{ retentionCounts[row.kind] || 0 }}</template></el-table-column>
          <el-table-column label="操作" width="80"><template #default="{ row }"><el-button v-permisaction="['catlink:retention:save']" link type="primary" @click="saveRetention(row)">保存</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="editorVisible" :title="editorTitle" width="560px" destroy-on-close>
      <el-form label-width="90px">
        <template v-if="editorResource === 'ads'">
          <el-form-item label="名称"><el-input v-model="editor.name" /></el-form-item>
          <el-form-item label="位置"><el-select v-model="editor.slot"><el-option label="启动页" value="launch" /><el-option label="社区" value="community" /></el-select></el-form-item>
          <el-form-item label="图片地址"><el-input v-model="editor.imageURL" /></el-form-item>
          <el-form-item label="跳转地址"><el-input v-model="editor.linkURL" /></el-form-item>
          <el-form-item label="高度模式"><el-select v-model="editor.heightMode"><el-option label="比例" value="ratio" /><el-option label="固定" value="fixed" /></el-select></el-form-item>
          <el-form-item label="高度值"><el-input-number v-model="editor.heightValue" :min="0" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="editor.enabled" /></el-form-item>
        </template>
        <template v-else-if="editorResource === 'versions'">
          <el-form-item label="版本号"><el-input-number v-model="editor.versionCode" :min="1" /></el-form-item>
          <el-form-item label="版本名称"><el-input v-model="editor.versionName" /></el-form-item>
          <el-form-item label="更新内容"><el-input v-model="editor.content" type="textarea" /></el-form-item>
          <el-form-item label="下载地址"><el-input v-model="editor.downloadURL" /></el-form-item>
          <el-form-item label="强制更新"><el-switch v-model="editor.forced" :active-value="1" :inactive-value="0" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="语句"><el-input v-model="editor.quote" type="textarea" /></el-form-item>
          <el-form-item label="来源"><el-input v-model="editor.source" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="editor.enabled" /></el-form-item>
        </template>
      </el-form>
      <template #footer><el-button @click="editorVisible = false">取消</el-button><el-button v-permisaction="editorPermission" type="primary" @click="submitEditor">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="importVisible" title="批量导入温柔语句" width="560px"><el-input v-model="importText" type="textarea" :rows="10" placeholder="每行一条语句" /><template #footer><el-button @click="importVisible = false">取消</el-button><el-button v-permisaction="['catlink:quotes:import']" type="primary" @click="submitImport">导入</el-button></template></el-dialog>
    <el-dialog v-model="regionVisible" :title="regionForm.id ? '编辑地区' : '新增地区'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="regionForm.name" /></el-form-item><el-form-item label="编码"><el-input v-model="regionForm.code" /></el-form-item>
        <el-form-item label="层级"><el-select v-model="regionForm.level"><el-option v-for="item in regionLevels" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item v-if="regionForm.level !== 'province'" label="上级编码"><el-input v-model="regionForm.parentCode" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="regionForm.sort" :min="-10000" :max="10000" /></el-form-item><el-form-item label="启用"><el-switch v-model="regionForm.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="regionVisible = false">取消</el-button><el-button v-permisaction="['catlink:regions:manage']" type="primary" @click="submitRegion">保存</el-button></template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TabsPaneContext } from 'element-plus'
import PageContainer from '@/components/PageContainer/index.vue'
import {
  createConfiguration, createRegion, deleteConfiguration, deleteRegion, generateQuotes, getBrand, getBrickConfig, getEmailTemplates, getSecurityPolicy, importQuotes,
  getMediaLimits, getRetentionPolicies, getRetentionPreview, listConfiguration, runRetention, saveBrand, saveBrickConfig, saveEmailTemplate,
  listRegions, saveMediaLimits, saveRetentionPolicy, saveSecurityPolicy, testAI, testEmail, updateConfiguration, updateRegion, getLocationProvider, saveLocationProvider, type MediaLimits, type RegionRow, type RetentionPolicy
} from '@/api/catlink'

defineOptions({ name: 'CatLinkConfiguration' })
type Resource = 'ads' | 'versions' | 'quotes'
type Row = Record<string, any>
const route = useRoute()
const tabs = new Set(['brand', 'security', 'location', 'media-limits', 'email', 'ads', 'versions', 'quotes', 'ai', 'regions', 'retention'])
const routeTab = () => tabs.has(String(route.query.tab)) ? String(route.query.tab) : 'security'
const tab = ref(routeTab())
const brand = ref({ name: '有猫腻', logoUrl: '/api/v1/assets/default-avatar.png' })
const ads = ref<Row[]>([])
const versions = ref<Row[]>([])
const quotes = ref<Row[]>([])
const templates = ref<Row[]>([])
const security = ref({ captchaOpen: 0, captchaLength: 6, captchaWidth: 240, captchaHeight: 80 })
const location = ref({ provider: 'osm', apiKey: '', amapKey: '', baiduAk: '', apiKeyConfigured: false, amapKeyConfigured: false, baiduAkConfigured: false, searchUrl: '', reverseUrl: '', ipUrl: '', trustProxyHeaders: false })
const locationDefaults: Record<string, { searchUrl: string; reverseUrl: string }> = {
  amap: { searchUrl: 'https://restapi.amap.com/v5/place/text', reverseUrl: 'https://restapi.amap.com/v3/geocode/regeo' },
  baidu: { searchUrl: 'https://api.map.baidu.com/place/v3/region', reverseUrl: 'https://api.map.baidu.com/reverse_geocoding/v3/' },
  osm: { searchUrl: 'https://nominatim.openstreetmap.org/search', reverseUrl: 'https://nominatim.openstreetmap.org/reverse' }
}
const defaultMediaBytes = 25 * 1024 * 1024
const mediaLimits = ref<MediaLimits>({
  post: 9, catAlbum: 30, adoptionSite: 9, contract: 9, followUp: 9, chat: 1,
  maxBytes: defaultMediaBytes,
  categoryMaxBytes: Object.fromEntries(['cat', 'contract', 'followup', 'avatar', 'post', 'chat', 'misc'].map(key => [key, defaultMediaBytes])),
  allowedMimeTypes: ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'],
  allowedExtensions: ['jpg', 'jpeg', 'png', 'webp', 'pdf']
})
const mediaCategories = [{ key: 'cat', label: '猫咪图片' }, { key: 'contract', label: '合同' }, { key: 'followup', label: '回访' }, { key: 'avatar', label: '头像' }, { key: 'post', label: '动态' }, { key: 'chat', label: '私聊' }, { key: 'misc', label: '其他文件' }]
const mediaMaxMb = computed({
  get: () => Math.max(1, Math.round(mediaLimits.value.maxBytes / 1024 / 1024)),
  set: value => {
    const bytes = value * 1024 * 1024
    mediaLimits.value.maxBytes = bytes
    mediaLimits.value.categoryMaxBytes = Object.fromEntries(Object.entries(mediaLimits.value.categoryMaxBytes).map(([key, limit]) => [key, Math.min(limit, bytes)]))
  }
})
const mediaCategoryMb = (key: string) => Math.max(1, Math.round((mediaLimits.value.categoryMaxBytes[key] || mediaLimits.value.maxBytes) / 1024 / 1024))
const setMediaCategoryMb = (key: string, value: number | undefined) => {
  if (value) mediaLimits.value.categoryMaxBytes = { ...mediaLimits.value.categoryMaxBytes, [key]: value * 1024 * 1024 }
}
const mediaFormats = computed<string[]>({
  get: () => mediaLimits.value.allowedExtensions.filter(value => value !== 'jpeg'),
  set: values => {
    mediaLimits.value.allowedExtensions = values.flatMap(value => value === 'jpg' ? ['jpg', 'jpeg'] : [value])
    const mimeByExtension: Record<string, string> = { jpg: 'image/jpeg', png: 'image/png', webp: 'image/webp', pdf: 'application/pdf' }
    mediaLimits.value.allowedMimeTypes = values.map(value => mimeByExtension[value])
  }
})
const smtp = ref<Row>({ enabled: false, host: '', port: 465, from: '', username: '', nickname: '有猫腻', tlsMode: 'implicit' })
const smtpSecret = ref('')
const smtpSecretConfigured = ref(false)
const testRecipient = ref('')
const ai = ref<Row>({ enabled: true, baseUrl: 'https://glm.996986.xyz/v1', model: 'glm-5.2', timeoutSeconds: 30, authMode: 'none' })
const aiSecret = ref('')
const aiSecretConfigured = ref(false)
const retention = ref<RetentionPolicy[]>([])
const retentionCounts = ref<Record<string, number>>({})
const editorVisible = ref(false)
const editorResource = ref<Resource>('ads')
const editor = ref<Row>({})
const editorTitle = ref('')
const editorPermission = computed(() => [`catlink:${editorResource.value}:${editor.value.id ? 'edit' : 'add'}`])
const importVisible = ref(false); const importText = ref('')
const generatingQuotes = ref(false)
const regionLevels = [{ value: 'province', label: '省级' }, { value: 'city', label: '市级' }, { value: 'district', label: '区县' }, { value: 'street', label: '街道/乡镇' }, { value: 'community', label: '社区/村' }] as const
const regionLevelLabel = (value: string) => regionLevels.find(item => item.value === value)?.label || value
const regions = ref<RegionRow[]>([])
const regionTotal = ref(0)
const regionLoading = ref(false)
const regionVisible = ref(false)
const regionQuery = ref({ pageIndex: 1, pageSize: 20, keyword: '', level: '', parentCode: '' })
const regionForm = ref<RegionRow>({ code: '', parentCode: '', name: '', level: 'district', sort: 0, enabled: true })

const list = (resource: Resource) => ({ ads, versions, quotes })[resource]
async function loadResource(resource: Resource) { list(resource).value = ((await listConfiguration(resource, { pageIndex: 1, pageSize: 200 })).data.items || []) as Row[] }
async function loadSecurity() { security.value = { ...security.value, ...(await getSecurityPolicy()).data } }
async function loadLocation() { location.value = { ...location.value, ...(await getLocationProvider()).data } }
function onLocationProviderChange(provider: string) {
  const defaults = locationDefaults[provider]
  if (!defaults) return
  location.value.searchUrl = defaults.searchUrl
  location.value.reverseUrl = defaults.reverseUrl
}
async function loadMediaLimits() { mediaLimits.value = (await getMediaLimits()).data }
async function loadBrand() { brand.value = (await getBrand()).data }
async function loadSMTP() { const value = (await getBrickConfig('smtp')).data; smtp.value = { ...smtp.value, ...value.values }; smtpSecretConfigured.value = value.configuredSecrets.includes('password'); smtpSecret.value = '' }
async function loadAI() { const value = (await getBrickConfig('ai')).data; ai.value = { ...ai.value, ...value.values }; aiSecretConfigured.value = value.configuredSecrets.includes('apiKey'); aiSecret.value = '' }
async function loadEmail() { await loadSMTP(); templates.value = ((await getEmailTemplates({ pageIndex: 1, pageSize: 20 })).data.items || []) as Row[]; for (const purpose of ['register', 'password_reset']) if (!templates.value.some(row => row.purpose === purpose)) templates.value.push({ purpose, subject: '', title: '', description: '', footer: '' }) }
async function loadRetention() { retention.value = (await getRetentionPolicies()).data.items || []; await previewRetention() }
async function loadTab(value: TabsPaneContext['paneName'] = tab.value) { const name = String(value); if (name === 'brand') await loadBrand(); else if (name === 'security') await loadSecurity(); else if (name === 'location') await loadLocation(); else if (name === 'media-limits') await loadMediaLimits(); else if (name === 'email') await loadEmail(); else if (name === 'ai') await loadAI(); else if (name === 'regions') await loadRegions(); else if (name === 'retention') await loadRetention(); else await loadResource(name as Resource) }
async function saveBrandConfig() { await saveBrand(brand.value); ElMessage.success('已保存') }
async function saveSecurity() { await saveSecurityPolicy(security.value); ElMessage.success('已保存') }
async function saveLocationConfig() {
  await saveLocationProvider(location.value)
  await loadLocation()
  location.value.apiKey = ''
  location.value.amapKey = ''
  location.value.baiduAk = ''
  ElMessage.success('已保存')
}
async function saveMediaLimitConfig() { await saveMediaLimits(mediaLimits.value); ElMessage.success('已保存') }
async function saveSMTP() { await saveBrickConfig('smtp', smtp.value, { password: smtpSecret.value }); await loadSMTP(); ElMessage.success('已保存') }
async function saveAI() { await saveBrickConfig('ai', ai.value, { apiKey: aiSecret.value }); await loadAI(); ElMessage.success('已保存') }
async function checkAI() { await testAI(); ElMessage.success('AI 服务连接正常') }
async function previewRetention() { retentionCounts.value = (await getRetentionPreview()).data.counts || {} }
async function saveRetention(row: RetentionPolicy) { await saveRetentionPolicy(row); ElMessage.success('已保存'); await previewRetention() }
async function executeRetention() { await ElMessageBox.confirm('按当前策略清理过期数据？', '执行清理', { type: 'warning' }); await runRetention(); ElMessage.success('清理任务已进入队列') }
async function sendTestEmail() { await testEmail(testRecipient.value); ElMessage.success('测试邮件已发送') }
async function saveTemplate(row: Row) { await saveEmailTemplate(row.purpose, row); ElMessage.success('模板已保存') }
const purposeLabel = (purpose: string) => purpose === 'register' ? '注册验证' : '找回密码'
const retentionLabel = (kind: string) => ({
  notifications: '通知记录',
  notification_outbox: '通知投递记录',
  temporary_media: '临时媒体',
  admin_login_logs: '管理端登录日志',
  admin_operation_logs: '管理端操作日志',
  app_audit_logs: 'App 业务审计'
})[kind] || kind
function openEditor(resource: Resource, row?: Row) {
  editorResource.value = resource
  editor.value = row ? { ...row, imageURL: row.image_url, linkURL: row.link_url, heightMode: row.height_mode, heightValue: row.height_value, versionCode: row.version_code, versionName: row.version_name, downloadURL: row.download_url } : resource === 'ads' ? { slot: 'community', heightMode: 'ratio', heightValue: 42.857, enabled: true } : resource === 'versions' ? { forced: 0 } : { source: '有猫腻', enabled: true }
  editorTitle.value = resource === 'ads' ? '广告' : resource === 'versions' ? '版本' : '温柔语句'
  editorVisible.value = true
}
async function submitEditor() {
  const id = Number(editor.value.id || 0)
  if (id) await updateConfiguration(editorResource.value, id, editor.value)
  else await createConfiguration(editorResource.value, editor.value)
  editorVisible.value = false
  await loadResource(editorResource.value)
}
async function remove(resource: Resource, id: number) { await ElMessageBox.confirm('确认删除？', '删除', { type: 'warning' }); await deleteConfiguration(resource, id); list(resource).value = list(resource).value.filter(row => row.id !== id) }
async function submitImport() { await importQuotes(importText.value); importVisible.value = false; importText.value = ''; await loadResource('quotes') }
async function generateQuoteDraft() {
  generatingQuotes.value = true
  try {
    importText.value = (await generateQuotes(20)).data.items.join('\n')
    importVisible.value = true
  } finally {
    generatingQuotes.value = false
  }
}
async function loadRegions() {
  regionLoading.value = true
  try { const data = (await listRegions(regionQuery.value)).data; regions.value = data.items; regionTotal.value = data.total } finally { regionLoading.value = false }
}
function openRegion(row?: RegionRow) { regionForm.value = row ? { ...row } : { code: '', parentCode: '', name: '', level: 'district', sort: 0, enabled: true }; regionVisible.value = true }
async function submitRegion() { if (regionForm.value.id) await updateRegion(regionForm.value); else await createRegion(regionForm.value); regionVisible.value = false; await loadRegions() }
async function removeRegion(row: RegionRow) { if (!row.id) return; await ElMessageBox.confirm(`确认删除“${row.name}”？`, '删除地区', { type: 'warning' }); await deleteRegion(row.id); await loadRegions() }
watch(() => route.query.tab, async() => { tab.value = routeTab(); await loadTab() })
onMounted(() => loadTab())
</script>

<style scoped>
.config-form { max-width: 720px; }
.unit { margin-left: 8px; }
.toolbar { display: flex; justify-content: flex-end; margin-bottom: 12px; }
.test-recipient { width: 240px; margin-left: 12px; }
</style>
