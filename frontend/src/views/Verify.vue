<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">项目验收材料校验</h2>
    </div>



    <!-- Custom stepper -->
    <div class="stepper">
      <div class="step" :class="{ active: true, done: batchId || checkDone }">
        <div class="step-circle">{{ (batchId || checkDone) ? '✓' : '1' }}</div>
        <span class="step-label">上传材料</span>
      </div>
      <div class="step-line" :class="{ active: !!batchId }"></div>
      <div class="step" :class="{ active: !!batchId, done: checkDone }">
        <div class="step-circle">{{ checkDone ? '✓' : '2' }}</div>
        <span class="step-label">解析预览</span>
      </div>
      <div class="step-line" :class="{ active: checkDone }"></div>
      <div class="step" :class="{ active: checkDone }">
        <div class="step-circle">3</div>
        <span class="step-label">校验结果</span>
      </div>
    </div>

    <!-- Step 1: 上传文件 -->
    <div class="step-card">
      <div class="step-card-header">
        <span class="step-card-title">第一步：上传验收材料</span>
      </div>
      <div class="step-card-body">
        <el-form label-width="90px" style="margin-bottom:16px">
          <el-form-item label="机构/公司">
            <el-select v-if="isAdmin" v-model="selectedOrgId" placeholder="请选择项目所属公司" style="width:300px" clearable>
              <el-option v-for="org in orgList" :key="org.id" :label="org.orgName" :value="org.id" />
            </el-select>
            <el-input v-else :value="selectedOrgName" disabled style="width:300px" />
          </el-form-item>
        </el-form>
        <el-upload
          class="upload-zone"
          drag
          :auto-upload="false"
          :on-change="handleFileChange"
          :show-file-list="false"
          accept=".pdf,.xlsx,.xls,.doc,.docx,.jpg,.jpeg,.png"
        >
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <div class="upload-text">拖拽文件到此处，或 <em>点击上传</em></div>
          <div class="upload-hint">支持 PDF、Excel、Word、图片格式</div>
        </el-upload>

        <div v-if="selectedFile" class="file-info">
          <el-icon style="color:var(--c-gold);flex-shrink:0"><Document /></el-icon>
          <span class="file-name">{{ selectedFile.name }}</span>
          <el-button type="primary" :loading="uploading"
            :disabled="!selectedFile || (isAdmin && !selectedOrgId)"
            @click="doUpload" size="small">
            解析上传
          </el-button>
        </div>
      </div>
    </div>

    <!-- 历史解析批次 -->
    <div class="step-card" v-if="batchList.length > 0 || batchListLoading">
      <div class="step-card-header">
        <span class="step-card-title">历史解析批次</span>
      </div>
      <div class="step-card-body no-pad">
        <el-table :data="batchList" border size="small" v-loading="batchListLoading">
          <el-table-column prop="sourceFile" label="来源文件" min-width="160" show-overflow-tooltip />
          <el-table-column prop="recordCount" label="记录数" width="80" align="right" />
          <el-table-column v-if="isAdmin" prop="orgName" label="机构名称" min-width="120" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="上传时间" width="160" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button type="danger" link size="small" @click="enterPreview(row)">进入预览</el-button>
              <el-button type="danger" link size="small" @click="deleteBatch(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- Step 2: 解析预览 -->
    <div v-if="batchId" class="step-card" ref="previewCard">
      <div class="step-card-header">
        <span class="step-card-title">第二步：解析结果预览（共 {{ previewData.length }} 条）</span>
        <el-button type="primary" :loading="checking" @click="doCheck" size="small">开始校验</el-button>
      </div>
      <!-- 工作内容时间统计表 -->
      <div class="step-card-body no-pad">
        <el-table :data="previewData" border size="small" max-height="320">
          <el-table-column prop="companyName" label="公司名称" min-width="130" show-overflow-tooltip />
          <el-table-column prop="projectName" label="项目名称" min-width="110" show-overflow-tooltip />
          <el-table-column prop="name" label="姓名" width="80" />
          <el-table-column prop="actualStartDate" label="开始时间" width="110" />
          <el-table-column prop="actualEndDate" label="结束时间" width="110" />
          <el-table-column prop="actualDays" label="实际人天" width="90" align="right" />
          <el-table-column prop="standardDays" label="标准人天" width="90" align="right" />
          <el-table-column prop="workContent" label="工作内容" min-width="160" show-overflow-tooltip />
        </el-table>
      </div>
      <!-- 考勤登记表 -->
      <div v-if="attendances.length > 0" class="attendance-section">
        <div class="attendance-header">合作公司人员考勤登记表（共 {{ attendances.length }} 条）</div>
        <el-table :data="attendances" border size="small" max-height="320">
          <el-table-column prop="name" label="姓名" width="80" />
          <el-table-column prop="projectName" label="项目名称" min-width="130" show-overflow-tooltip />
          <el-table-column prop="checkDate" label="日期" width="110" />
          <el-table-column prop="morning" label="上午" width="70" align="center" />
          <el-table-column prop="afternoon" label="下午" width="70" align="center" />
        </el-table>
      </div>
    </div>

    <!-- Step 3: 校验结果 -->
    <div v-if="checkDone" class="step-card">
      <div class="step-card-header">
        <span class="step-card-title">第三步：校验结果</span>
      </div>
      <div class="step-card-body">
        <!-- 校验通过 -->
        <div v-if="checkPass" class="result-row">
          <div class="result-icon-wrap pass"><el-icon><CircleCheckFilled /></el-icon></div>
          <div class="result-text">
            <strong>校验通过</strong>
            <span>1、未发现项目人员交叉重复投入，2、人员投入标准人天与有效考勤天数一致，可纳入基础库</span>
          </div>
          <div class="result-actions">
            <el-button type="primary" :loading="confirming" @click="doConfirm">确认入库</el-button>
            <el-button class="btn-text" @click="doCancel">取消</el-button>
          </div>
        </div>

        <!-- 校验不通过 -->
        <div v-else>
          <div class="result-row">
            <div class="result-icon-wrap fail"><el-icon><CircleCloseFilled /></el-icon></div>
            <div class="result-text">
              <strong>校验不通过</strong>
              <span>
                <template v-if="conflicts.length">发现 {{ conflicts.length }} 处重复投入</template>
                <template v-if="conflicts.length && attendanceMismatches.length">；</template>
                <template v-if="attendanceMismatches.length">{{ attendanceMismatches.length }} 条考勤天数与标准人天不一致</template>
              </span>
            </div>
            <div class="result-actions">
              <el-button @click="doCancel">重新上传</el-button>
            </div>
          </div>

          <!-- 重复投入明细 -->
          <div v-if="conflicts.length" class="conflict-table-wrap">
            <div class="mismatch-header">重复投入明细</div>
            <el-table :data="conflicts" border size="small">
              <el-table-column prop="name" label="姓名" width="90" />
              <el-table-column prop="conflictStartDate" label="重复开始" width="110" />
              <el-table-column prop="conflictEndDate" label="重复结束" width="110" />
              <el-table-column prop="conflictDays" label="重复天数" width="90" align="right" />
              <el-table-column prop="project1" label="项目1" min-width="140" show-overflow-tooltip />
              <el-table-column prop="project2" label="项目2" min-width="140" show-overflow-tooltip />
            </el-table>
          </div>

          <!-- 考勤天数不一致明细 -->
          <div v-if="attendanceMismatches.length" class="conflict-table-wrap">
            <div class="mismatch-header">考勤天数与标准人天不一致明细</div>
            <el-table :data="attendanceMismatches" border size="small">
              <el-table-column prop="name" label="姓名" width="90" />
              <el-table-column prop="projectName" label="项目名称" min-width="160" show-overflow-tooltip />
              <el-table-column prop="standardDays" label="标准人天" width="100" align="right" />
              <el-table-column prop="attendanceDays" label="考勤有效天数" width="110" align="right" />
              <el-table-column label="差异" width="90" align="right">
                <template #default="{ row }">
                  <span :style="{ color: 'var(--c-danger)' }">
                    {{ (row.attendanceDays - row.standardDays).toFixed(1) }}
                  </span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auth, verify, sysOrg } from '../api/index.js'

const selectedFile = ref(null)
const uploading = ref(false)
const checking = ref(false)
const confirming = ref(false)

const batchId = ref('')
const previewData = ref([])
const attendances = ref([])
const checkDone = ref(false)
const checkPass = ref(false)
const conflicts = ref([])
const attendanceMismatches = ref([])
const previewCard = ref(null)
const batchList = ref([])
const batchListLoading = ref(false)

// 机构/公司
const currentUser = JSON.parse(localStorage.getItem('user') || '{}')
const isAdmin = (currentUser.roles || []).includes('ADMIN')
const orgList = ref([])
const selectedOrgId = ref(isAdmin ? null : (currentUser.orgId || null))
const selectedOrgName = ref(currentUser.orgName || null)
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
  } catch { /* 加载失败不阻断主流程 */ }
}

function handleFileChange(file) {
  selectedFile.value = file.raw
  batchId.value = ''
  previewData.value = []
  attendances.value = []
  checkDone.value = false
  checkPass.value = false
  conflicts.value = []
  attendanceMismatches.value = []
}

async function doUpload() {
  if (!selectedFile.value) return
  if (isAdmin && !selectedOrgId.value) {
    ElMessage.warning('请先选择项目所属公司')
    return
  }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', selectedFile.value)
    const orgIdVal = isAdmin ? selectedOrgId.value : currentUser.orgId
    if (orgIdVal) fd.append('orgId', orgIdVal)
    if (resolvedOrgName.value) fd.append('companyName', resolvedOrgName.value)
    const res = await verify.upload(fd)
    if (res.data.code === 200) {
      batchId.value = res.data.batchId
      previewData.value = res.data.records
      attendances.value = res.data.attendances || []
      ElMessage.success(`解析成功，共 ${res.data.total} 条记录`)
      await nextTick()
      previewCard.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    } else {
      ElMessage.error(res.data.message || '解析失败')
    }
  } catch(e) {
    if (e.code === 'ECONNABORTED' || e.message?.includes('timeout')) {
      ElMessage.error('解析超时，请稍后重试（AI 解析大文件需要较长时间）')
    } else {
      ElMessage.error('上传失败：' + (e.response?.data?.message || e.message || '请检查网络或文件格式'))
    }
  } finally {
    uploading.value = false
  }
}

async function doCheck() {
  checking.value = true
  try {
    const res = await verify.check(batchId.value)
    checkDone.value = true
    checkPass.value = res.data.pass
    conflicts.value = res.data.conflicts || []
    attendanceMismatches.value = res.data.attendanceMismatches || []
    if (checkPass.value) {
      ElMessage.success('校验通过')
    } else {
      const msg = []
      if (conflicts.value.length) msg.push(`${conflicts.value.length} 处重复投入`)
      if (attendanceMismatches.value.length) msg.push(`${attendanceMismatches.value.length} 条考勤天数不一致`)
      ElMessage.warning('发现 ' + msg.join('，'))
    }
  } catch {
    ElMessage.error('校验失败')
  } finally {
    checking.value = false
  }
}

async function doConfirm() {
  confirming.value = true
  try {
    const res = await verify.confirm(batchId.value)
    if (res.data.code === 200) {
      ElMessage.success('已成功纳入基础库')
      resetAll()
    } else {
      ElMessage.error(res.data.message || '入库失败')
    }
  } catch {
    ElMessage.error('入库失败')
  } finally {
    confirming.value = false
  }
}

async function doCancel() {
  if (batchId.value) await verify.cancel(batchId.value).catch(() => {})
  resetAll()
}

function resetAll() {
  selectedFile.value = null
  batchId.value = ''
  previewData.value = []
  attendances.value = []
  checkDone.value = false
  checkPass.value = false
  conflicts.value = []
  attendanceMismatches.value = []
}

async function loadBatches() {
  batchListLoading.value = true
  try {
    const res = await verify.batches()
    if (res.data.code === 200) batchList.value = res.data.data
  } catch { /* 不阻断主流程 */ }
  finally { batchListLoading.value = false }
}

async function enterPreview(batch) {
  try {
    const res = await verify.preview(batch.batchId)
    if (res.data.code === 200) {
      batchId.value = batch.batchId
      previewData.value = res.data.data
      attendances.value = res.data.attendances || []
      checkDone.value = false
      checkPass.value = false
      conflicts.value = []
      attendanceMismatches.value = []
      await nextTick()
      previewCard.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    } else {
      ElMessage.error(res.data.message || '加载失败')
    }
  } catch {
    ElMessage.error('加载批次数据失败')
  }
}

async function deleteBatch(batch) {
  try {
    await ElMessageBox.confirm(`确认删除批次"${batch.sourceFile}"的解析数据？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await verify.cancel(batch.batchId)
    ElMessage.success('已删除')
    if (batchId.value === batch.batchId) resetAll()
    loadBatches()
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(async () => {
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
  loadOrgList()
  loadBatches()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }

.page-header { margin-bottom: 4px; }
.page-title {
  font-family: 'Noto Serif SC', serif;
  font-size: 20px;
  font-weight: 600;
  color: var(--c-text-1);
}

/* ── Stepper ─────────────────────────────────────────── */
.stepper {
  display: flex;
  align-items: center;
  background: var(--c-white);
  border: 1px solid var(--c-border);
  border-radius: 8px;
  padding: 20px 40px;
}
.step { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.step-circle {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 2px solid var(--c-border);
  color: var(--c-text-3);
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--c-white);
  transition: all 0.25s;
}
.step.active .step-circle { border-color: var(--c-gold); color: var(--c-gold); }
.step.done .step-circle { background: var(--c-ink); border-color: var(--c-ink); color: var(--c-gold); }
.step-label { font-size: 12px; color: var(--c-text-3); white-space: nowrap; }
.step.active .step-label { color: var(--c-text-1); font-weight: 500; }

.step-line {
  flex: 1;
  height: 1px;
  background: var(--c-border);
  margin: 0 16px;
  margin-bottom: 18px;
  transition: background 0.3s;
}
.step-line.active { background: var(--c-gold); }

/* ── Step Cards ──────────────────────────────────────── */
.step-card {
  background: var(--c-white);
  border: 1px solid var(--c-border);
  border-radius: 8px;
  overflow: hidden;
}
.step-card-header {
  padding: 14px 20px;
  border-bottom: 1px solid var(--c-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.step-card-title { font-size: 14px; font-weight: 500; color: var(--c-text-1); }
.step-card-body { padding: 20px; }
.step-card-body.no-pad { padding: 0; }

/* Upload */
.upload-zone :deep(.el-upload) { width: 100%; }
.upload-zone :deep(.el-upload-dragger) {
  width: 100%;
  border-color: var(--c-border);
  border-radius: 8px;
  background: var(--c-surface);
  transition: border-color 0.2s, background 0.2s;
  padding: 32px 0;
}
.upload-zone :deep(.el-upload-dragger:hover) {
  border-color: var(--c-gold);
  background: #eff6ff;
}
.upload-icon { font-size: 44px; color: var(--c-text-3); margin-bottom: 8px; }
.upload-text { font-size: 14px; color: var(--c-text-2); }
.upload-text em { color: var(--c-gold); font-style: normal; font-weight: 500; }
.upload-hint { font-size: 12px; color: var(--c-text-3); margin-top: 6px; }

.file-info {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  padding: 10px 14px;
  background: #eff6ff;
  border: 1px solid rgba(22,119,255,0.25);
  border-radius: 6px;
  font-size: 14px;
  color: var(--c-text-1);
}
.file-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* Results */
.result-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.result-icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}
.result-icon-wrap.pass { background: #dcfce7; color: var(--c-success); }
.result-icon-wrap.fail { background: #fee2e2; color: var(--c-danger); }
.result-text { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.result-text strong { font-size: 15px; color: var(--c-text-1); }
.result-text span { font-size: 13px; color: var(--c-text-2); }
.result-actions { display: flex; gap: 8px; align-items: center; }

.btn-text {
  border: none !important;
  background: transparent !important;
  color: var(--c-text-2) !important;
}
.btn-text:hover { color: var(--c-text-1) !important; }

.conflict-table-wrap { margin-top: 16px; border-radius: 6px; overflow: hidden; }
.conflict-table-wrap :deep(.el-table__row td.el-table__cell) { background-color: #fff9f9; }
.mismatch-header {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 500;
  color: var(--c-text-2);
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border);
}

.attendance-section { border-top: 1px solid var(--c-border); }
.attendance-header {
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--c-text-2);
  background: var(--c-surface);
}
</style>
