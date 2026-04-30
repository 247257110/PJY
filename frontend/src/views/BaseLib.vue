<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">验收材料基础库</h2>
      <el-button class="btn-gold-outline" @click="batchDialogVisible = true">
        <el-icon><UploadFilled /></el-icon>
        批量初始化
      </el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :model="query" inline class="search-form">
        <el-form-item label="姓名">
          <el-input v-model="query.name" placeholder="请输入姓名" clearable style="width:140px" />
        </el-form-item>
        <el-form-item label="公司名称">
          <el-input v-model="query.companyName" placeholder="请输入公司名称" clearable style="width:190px" />
        </el-form-item>
        <el-form-item label="项目名称">
          <el-input v-model="query.projectName" placeholder="请输入项目名称" clearable style="width:190px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button class="btn-text" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table :data="tableData" border style="width:100%" v-loading="loading">
        <el-table-column prop="companyName" label="公司名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="projectName" label="项目名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="name" label="姓名" width="90" />
        <el-table-column prop="actualStartDate" label="实际开始" width="110" />
        <el-table-column prop="actualEndDate" label="实际结束" width="110" />
        <el-table-column prop="actualDays" label="实际人天" width="100" align="right" />
        <el-table-column prop="standardDays" label="标准人天" width="100" align="right" />
        <el-table-column prop="attendanceDays" label="考勤天数" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.attendanceDays != null">{{ row.attendanceDays }}</span>
            <span v-else style="color:var(--c-text-3)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="考勤校对" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.attendanceVerified === 1" type="success" size="small">通过</el-tag>
            <el-tag v-else-if="row.attendanceVerified === 0" type="danger" size="small">不通过</el-tag>
            <el-tag v-else type="info" size="small">未校对</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="workContent" label="工作内容" min-width="180" show-overflow-tooltip />
        <el-table-column prop="sourceFile" label="来源文件" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="showAttendance(row)">查看考勤</el-button>
            <el-divider direction="vertical" />
            <el-popconfirm title="确认删除该记录？" @confirm="handleDelete(row.id)">
              <template #reference>
                <button class="del-link">删除</button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @change="loadData"
        />
      </div>
    </div>

    <!-- 考勤明细对话框 -->
    <el-dialog v-model="attDialogVisible" :title="`考勤明细 — ${attRow?.name}`" width="560px">
      <div v-if="attLoading" style="text-align:center;padding:20px">
        <el-icon class="is-loading"><Loading /></el-icon> 加载中...
      </div>
      <template v-else>
        <el-table :data="attList" border size="small" max-height="400">
          <el-table-column prop="checkDate" label="日期" width="120" />
          <el-table-column label="是否工作日" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isWorkday ? '' : 'info'" size="small">{{ row.isWorkday ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="签到状态" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.signed" type="success" size="small">已签到</el-tag>
              <el-tag v-else-if="row.isWorkday" type="danger" size="small">无考勤</el-tag>
              <span v-else style="color:var(--c-text-3)">—</span>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top:12px;font-size:14px;color:var(--c-text-2)">
          签到天数：<strong>{{ attTotalSigned }}</strong> 天 &nbsp;/&nbsp; 标准人天：<strong>{{ attRow?.standardDays ?? '—' }}</strong> 天
        </div>
      </template>
      <template #footer>
        <el-button type="primary" @click="attDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 批量初始化对话框 -->
    <el-dialog v-model="batchDialogVisible" title="批量初始化" width="860px" @close="resetBatch">
      <!-- 步骤条 -->
      <el-steps :active="batchStep - 1" finish-status="success" simple style="margin-bottom:24px">
        <el-step title="上传解析" />
        <el-step title="预览校验" />
        <el-step title="入库结果" />
      </el-steps>

      <!-- Step 1：上传 -->
      <div v-if="batchStep === 1">
        <el-form label-width="90px" style="margin-bottom:16px">
          <el-form-item label="机构/公司">
            <el-select v-if="isAdmin" v-model="selectedOrgId" placeholder="请选择项目所属公司" style="width:300px" clearable>
              <el-option v-for="org in orgList" :key="org.id" :label="org.orgName" :value="org.id" />
            </el-select>
            <el-input v-else :value="selectedOrgName" disabled style="width:300px" />
          </el-form-item>
        </el-form>
        <el-upload ref="uploadRef" multiple :auto-upload="false" :on-change="onFileChange" :on-remove="onFileRemove"
          accept=".pdf,.xlsx,.xls,.doc,.docx,.jpg,.jpeg,.png" drag class="upload-zone">
          <el-icon style="font-size:44px;color:var(--c-text-3)"><UploadFilled /></el-icon>
          <div class="upload-text">拖拽文件到此处，或 <em>点击上传</em></div>
          <template #tip>
            <div class="upload-tip">支持 PDF、图片（JPG/PNG）、Excel、Word 文件，可多选</div>
          </template>
        </el-upload>
      </div>

      <!-- Step 2：预览校验 -->
      <div v-else-if="batchStep === 2">
        <div style="margin-bottom:10px;font-size:13px;color:var(--c-text-2)">
          共解析 <strong>{{ parseData.workRecords.length }}</strong> 条记录，
          考勤通过 <strong style="color:var(--el-color-success)">{{ parseData.workRecords.filter(r=>r.attendanceVerified===1).length }}</strong> 条，
          不通过 <strong style="color:var(--el-color-danger)">{{ parseData.workRecords.filter(r=>r.attendanceVerified===0).length }}</strong> 条，
          无考勤 <strong style="color:var(--c-text-3)">{{ parseData.workRecords.filter(r=>r.attendanceVerified==null).length }}</strong> 条
        </div>
        <el-table :data="parseData.workRecords" border size="small" max-height="420" style="width:100%">
          <el-table-column prop="companyName" label="公司" min-width="110" show-overflow-tooltip />
          <el-table-column prop="name" label="姓名" width="80">
            <template #default="{ row }">
              <el-button type="primary" link @click="showBatchAttendance(row)">{{ row.name }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="projectName" label="项目" min-width="110" show-overflow-tooltip />
          <el-table-column prop="actualStartDate" label="开始" width="100" />
          <el-table-column prop="actualEndDate" label="结束" width="100" />
          <el-table-column prop="actualDays" label="实际人天" width="80" align="right" />
          <el-table-column prop="standardDays" label="标准人天" width="80" align="right" />
          <el-table-column prop="signedDays" label="签到天数" width="80" align="right" />
          <el-table-column label="考勤校对" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.attendanceVerified === 1" type="success" size="small">通过</el-tag>
              <el-tag v-else-if="row.attendanceVerified === 0" type="danger" size="small">不通过</el-tag>
              <el-tag v-else type="info" size="small">无考勤</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="workContent" label="工作内容" min-width="140" show-overflow-tooltip />
          <el-table-column prop="sourceFile" label="来源文件" min-width="120" show-overflow-tooltip />
        </el-table>
      </div>

      <!-- Step 3：入库结果 -->
      <div v-else-if="batchStep === 3">
        <el-alert
          :title="`入库完成，共入库 ${saveTotal} 条记录`"
          :type="saveTotal > 0 ? 'success' : 'warning'"
          show-icon :closable="false"
        />
      </div>

      <template #footer>
        <template v-if="batchStep === 1">
          <el-button @click="batchDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="batchLoading"
            :disabled="fileList.length === 0 || (isAdmin && !selectedOrgId)"
            @click="doParse">开始解析</el-button>
        </template>
        <template v-else-if="batchStep === 2">
          <el-button @click="batchStep = 1">上一步</el-button>
          <el-button type="primary" :loading="batchLoading"
            :disabled="parseData.workRecords.length === 0"
            @click="doSave">确认入库</el-button>
        </template>
        <template v-else>
          <el-button type="primary" @click="batchDialogVisible = false">关闭</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { auth, baseLib, sysOrg } from '../api/index.js'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({ name: '', companyName: '', projectName: '', page: 1, size: 20 })

const batchDialogVisible = ref(false)
const batchLoading = ref(false)
const batchStep = ref(1)
const parseData = ref({ workRecords: [], attendances: [] })
const saveTotal = ref(0)
const fileList = ref([])
const uploadRef = ref()

// 考勤明细
const attDialogVisible = ref(false)
const attLoading = ref(false)
const attRow = ref(null)
const attList = ref([])
const attTotalSigned = ref(0)

function showBatchAttendance(row) {
  attRow.value = row
  attLoading.value = false
  attList.value = []
  attTotalSigned.value = 0

  const signedDates = new Set(
    (parseData.value.attendances || [])
      .filter(a => a.name === row.name)
      .map(a => a.checkDate)
  )

  const start = new Date(row.actualStartDate)
  const end = new Date(row.actualEndDate)
  const list = []
  let signed = 0
  for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
    const dateStr = d.toISOString().slice(0, 10)
    const dow = d.getDay()
    const isWorkday = dow !== 0 && dow !== 6
    const isSigned = signedDates.has(dateStr)
    if (isSigned) signed++
    list.push({ checkDate: dateStr, isWorkday, signed: isSigned })
  }
  attList.value = list
  attTotalSigned.value = signed
  attDialogVisible.value = true
}

async function showAttendance(row) {
  attRow.value = row
  attDialogVisible.value = true
  attLoading.value = true
  attList.value = []
  attTotalSigned.value = 0
  try {
    const res = await baseLib.attendance(row.id)
    if (res.data.code === 200) {
      attList.value = res.data.data.list
      attTotalSigned.value = res.data.data.totalSigned
    }
  } catch {
    ElMessage.error('加载考勤明细失败')
  } finally {
    attLoading.value = false
  }
}

// 当前用户信息
const currentUser = JSON.parse(localStorage.getItem('user') || '{}')
const isAdmin = (currentUser.roles || []).includes('ADMIN')
const orgList = ref([])
// admin 用 selectedOrgId 绑定下拉，普通用户只用 selectedOrgName（只读显示）
const selectedOrgId = ref(isAdmin ? null : (currentUser.orgId || null))
const selectedOrgName = ref(currentUser.orgName || null)
// admin 选中机构后同步 orgName（用于 companyName 参数）
const resolvedOrgName = computed(() => {
  if (isAdmin) {
    const found = orgList.value.find(o => o.id === selectedOrgId.value)
    return found ? found.orgName : null
  }
  return selectedOrgName.value
})

async function loadOrgList() {
  if (!isAdmin) return
  try {
    const res = await sysOrg.list({})
    if (res.data.code === 200) {
      orgList.value = res.data.data.filter(org => org.orgCode !== 'HBJH' && org.id !== 1)
    }
  } catch {
    // 加载失败不阻断主流程
  }
}

function onFileChange(file, files) { fileList.value = files }
function onFileRemove(file, files) { fileList.value = files }

function resetBatch() {
  batchStep.value = 1
  parseData.value = { workRecords: [], attendances: [] }
  saveTotal.value = 0
  fileList.value = []
  uploadRef.value?.clearFiles()
  if (isAdmin) selectedOrgId.value = null
}

async function doParse() {
  if (fileList.value.length === 0) return
  if (isAdmin && !selectedOrgId.value) {
    ElMessage.warning('请先选择项目所属公司')
    return
  }
  batchLoading.value = true
  try {
    const formData = new FormData()
    fileList.value.forEach(f => formData.append('files', f.raw))
    if (resolvedOrgName.value) formData.append('companyName', resolvedOrgName.value)
    const orgIdVal = isAdmin ? selectedOrgId.value : currentUser.orgId
    if (orgIdVal) formData.append('orgId', orgIdVal)
    const res = await baseLib.batchParse(formData)
    if (res.data.code === 200) {
      parseData.value = { workRecords: res.data.workRecords || [], attendances: res.data.attendances || [] }
      batchStep.value = 2
    } else {
      ElMessage.error('解析失败')
    }
  } catch (e) {
    ElMessage.error('解析失败：' + (e.response?.data?.message || e.message))
  } finally {
    batchLoading.value = false
  }
}

async function doSave() {
  batchLoading.value = true
  try {
    const res = await baseLib.batchSave({
      workRecords: parseData.value.workRecords,
      attendances: parseData.value.attendances
    })
    if (res.data.code === 200) {
      saveTotal.value = res.data.total
      batchStep.value = 3
      loadData()
    } else {
      ElMessage.error('入库失败')
    }
  } catch (e) {
    ElMessage.error('入库失败：' + (e.response?.data?.message || e.message))
  } finally {
    batchLoading.value = false
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await baseLib.list(query)
    if (res.data.code === 200) {
      tableData.value = res.data.data.list
      total.value = res.data.data.total
    }
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.name = ''
  query.companyName = ''
  query.projectName = ''
  query.page = 1
  loadData()
}

async function handleDelete(id) {
  try {
    await baseLib.delete(id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(async () => {
  // 刷新用户信息（补全 orgName，兼容旧 localStorage 缓存）
  try {
    const r = await auth.info()
    if (r.data.code === 200) {
      const freshUser = r.data.user
      localStorage.setItem('user', JSON.stringify(freshUser))
      if (!isAdmin && freshUser.orgName) {
        selectedOrgName.value = freshUser.orgName
        selectedOrgId.value = freshUser.orgId || null
      }
    }
  } catch { /* 失败不影响主流程 */ }

  loadData()
  loadOrgList()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page-title {
  font-family: 'Noto Serif SC', serif;
  font-size: 20px;
  font-weight: 600;
  color: var(--c-text-1);
}

.btn-gold-outline {
  border: 1px solid var(--c-gold) !important;
  color: var(--c-gold) !important;
  background: transparent !important;
  font-weight: 500;
}
.btn-gold-outline:hover { background: rgba(22,119,255,0.06) !important; }

.btn-text {
  border: none !important;
  background: transparent !important;
  color: var(--c-text-2) !important;
}
.btn-text:hover { color: var(--c-text-1) !important; }

.search-bar {
  background: var(--c-white);
  border: 1px solid var(--c-border);
  border-radius: 8px;
  padding: 16px 20px 4px;
}
.search-form :deep(.el-form-item) { margin-bottom: 12px; }
.search-form :deep(.el-form-item__label) { font-size: 13px; color: var(--c-text-2); }

.table-card {
  background: var(--c-white);
  border: 1px solid var(--c-border);
  border-radius: 8px;
  overflow: hidden;
}
.table-card :deep(.el-table__inner-wrapper::before) { display: none; }

.del-link {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--c-danger);
  font-size: 13px;
  font-family: inherit;
  padding: 0;
  opacity: 0.8;
  transition: opacity 0.15s;
}
.del-link:hover { opacity: 1; }

.pagination {
  padding: 14px 20px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--c-border);
}

.upload-zone :deep(.el-upload-dragger) {
  border-color: var(--c-border);
  border-radius: 8px;
  background: var(--c-surface);
  transition: border-color 0.2s, background 0.2s;
}
.upload-zone :deep(.el-upload-dragger:hover) {
  border-color: var(--c-gold);
  background: #eff6ff;
}
.upload-text { margin-top: 10px; font-size: 14px; color: var(--c-text-2); }
.upload-text em { color: var(--c-gold); font-style: normal; font-weight: 500; }
.upload-tip { font-size: 12px; color: var(--c-text-3); margin-top: 6px; text-align: center; }
</style>
