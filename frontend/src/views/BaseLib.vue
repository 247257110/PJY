<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">验收材料基础库</h2>
      <div style="display:flex;gap:8px">
        <el-button class="btn-gold-outline" @click="manualDialogVisible = true">
          <el-icon><EditPen /></el-icon>
          手工录入
        </el-button>
        <el-button class="btn-gold-outline" @click="importDialogVisible = true">
          <el-icon><UploadFilled /></el-icon>
          导入初始化
        </el-button>
        <el-button class="btn-gold-outline" @click="batchDialogVisible = true">
          <el-icon><UploadFilled /></el-icon>
          批量初始化
        </el-button>
      </div>
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

    <!-- 手工录入对话框 -->
    <el-dialog v-model="manualDialogVisible" title="手工录入" width="560px" @close="resetManual">
      <el-form :model="manualForm" :rules="manualRules" ref="manualFormRef" label-width="90px">
        <el-form-item label="机构/公司">
          <el-select v-if="isAdmin" v-model="manualForm.orgId" placeholder="请选择公司" style="width:100%" clearable
            @change="onManualOrgChange">
            <el-option v-for="org in orgList" :key="org.id" :label="org.orgName" :value="org.id" />
          </el-select>
          <el-input v-else :value="selectedOrgName" disabled />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="manualForm.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="manualForm.projectName" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="开始日期" prop="actualStartDate">
          <el-date-picker v-model="manualForm.actualStartDate" type="date" value-format="YYYY-MM-DD"
            placeholder="选择开始日期" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束日期" prop="actualEndDate">
          <el-date-picker v-model="manualForm.actualEndDate" type="date" value-format="YYYY-MM-DD"
            placeholder="选择结束日期" style="width:100%" />
        </el-form-item>
        <el-form-item label="实际人天">
          <el-input-number v-model="manualForm.actualDays" :min="0" :precision="1" :step="0.5" style="width:100%" />
        </el-form-item>
        <el-form-item label="标准人天" prop="standardDays">
          <el-input-number v-model="manualForm.standardDays" :min="0" :precision="1" :step="0.5" style="width:100%" />
        </el-form-item>
        <el-form-item label="工作内容">
          <el-input v-model="manualForm.workContent" type="textarea" :rows="3" placeholder="请输入工作内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="manualLoading" @click="doManualSave">保存入库</el-button>
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

    <!-- 导入初始化对话框 -->
    <el-dialog v-model="importDialogVisible" title="导入初始化" width="960px" @close="resetImport">
      <el-steps :active="importStep - 1" finish-status="success" simple style="margin-bottom:24px">
        <el-step title="导入文件" />
        <el-step title="数据预览" />
        <el-step title="校验并入库" />
      </el-steps>

      <!-- Step 1：导入文件 -->
      <div v-if="importStep === 1" v-loading="importLoading" element-loading-text="解析加载中...">
        <el-form label-width="200px" style="margin-bottom:16px">
          <el-form-item label="机构/公司">
            <el-select v-if="isAdmin" v-model="importOrgId" placeholder="请选择项目所属公司" style="width:300px" clearable>
              <el-option v-for="org in orgList" :key="org.id" :label="org.orgName" :value="org.id" />
            </el-select>
            <el-input v-else :value="selectedOrgName" disabled style="width:300px" />
          </el-form-item>
          <el-form-item label="合作公司人员工作内容及时间统计表">
            <el-upload ref="importWorkUploadRef" :auto-upload="false" :limit="1"
              accept=".doc,.docx" :on-change="onImportWorkChange" :on-remove="onImportWorkRemove" class="inline-upload">
              <el-button type="primary">
                <el-icon><UploadFilled /></el-icon> 选择Word文件
              </el-button>
              <template #tip>
                <span class="upload-tip-inline">支持 .doc / .docx 格式</span>
              </template>
            </el-upload>
          </el-form-item>
          <el-form-item label="合作公司人员考勤登记表">
            <el-upload ref="importAttUploadRef" :auto-upload="false" :limit="1"
              accept=".xls,.xlsx" :on-change="onImportAttChange" :on-remove="onImportAttRemove" class="inline-upload">
              <el-button type="primary">
                <el-icon><UploadFilled /></el-icon> 选择Excel文件
              </el-button>
              <template #tip>
                <span class="upload-tip-inline">支持 .xls / .xlsx 格式</span>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 2：数据预览 -->
      <div v-else-if="importStep === 2">
        <div style="margin-bottom:10px;font-size:13px;color:var(--c-text-2)">
          共解析工作记录 <strong>{{ importParseData.workRecords.length }}</strong> 条，
          考勤记录 <strong>{{ importParseData.attendances.length }}</strong> 条
          <template v-if="importValidated">
            ，考勤通过 <strong style="color:var(--el-color-success)">{{ importStat.pass }}</strong> 条，
            不通过 <strong style="color:var(--el-color-danger)">{{ importStat.fail }}</strong> 条，
            无考勤 <strong style="color:var(--c-text-3)">{{ importStat.noAtt }}</strong> 条
          </template>
        </div>

        <div style="margin-bottom:8px;font-size:14px;font-weight:600;color:var(--c-text-1)">
          合作公司人员工作内容及时间统计表
        </div>
        <el-table :data="importParseData.workRecords" border size="small" max-height="240" style="width:100%;margin-bottom:20px">
          <el-table-column prop="companyName" label="公司" min-width="110" show-overflow-tooltip />
          <el-table-column prop="name" label="姓名" width="80">
            <template #default="{ row }">
              <span class="name-link" @click="showImportAttendance(row)">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="projectName" label="项目" min-width="110" show-overflow-tooltip />
          <el-table-column prop="actualStartDate" label="开始" width="100" />
          <el-table-column prop="actualEndDate" label="结束" width="100" />
          <el-table-column prop="actualDays" label="实际人天" width="80" align="right" />
          <el-table-column prop="standardDays" label="标准人天" width="80" align="right" />
          <el-table-column prop="signedDays" label="签到天数" width="80" align="right" v-if="importValidated" />
          <el-table-column label="考勤校对" width="90" align="center" v-if="importValidated">
            <template #default="{ row }">
              <el-tag v-if="row.attendanceVerified === 1" type="success" size="small">通过</el-tag>
              <el-tag v-else-if="row.attendanceVerified === 0" type="danger" size="small">不通过</el-tag>
              <el-tag v-else type="info" size="small">无考勤</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="workContent" label="工作内容" min-width="140" show-overflow-tooltip />
          <el-table-column prop="sourceFile" label="来源文件" min-width="120" show-overflow-tooltip />
        </el-table>

        <div style="margin-bottom:8px;font-size:14px;font-weight:600;color:var(--c-text-1)">
          合作公司人员考勤登记表
        </div>
        <el-table :data="importAttPageData" border size="small" max-height="240" style="width:100%">
          <el-table-column prop="name" label="姓名" width="80" />
          <el-table-column prop="checkDate" label="考勤日期" width="120" />
          <el-table-column prop="morning" label="上午" width="60" align="center">
            <template #default="{ row }">
              <span :class="row.morning === '有' ? 'att-tag-yes' : 'att-tag-no'">{{ row.morning || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="afternoon" label="下午" width="60" align="center">
            <template #default="{ row }">
              <span :class="row.afternoon === '有' ? 'att-tag-yes' : 'att-tag-no'">{{ row.afternoon || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="sourceFile" label="来源文件" min-width="120" show-overflow-tooltip />
        </el-table>
        <div style="margin-top:8px;display:flex;justify-content:flex-end">
          <el-pagination
            v-model:current-page="importAttPage"
            v-model:page-size="importAttPageSize"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            :total="importParseData.attendances.length"
            size="small"
            background
          />
        </div>
      </div>

      <!-- Step 3：入库结果 -->
      <div v-else-if="importStep === 3">
        <el-alert
          :title="`入库完成，共入库 ${importSaveTotal} 条记录`"
          :type="importSaveTotal > 0 ? 'success' : 'warning'"
          show-icon :closable="false"
        />
      </div>

      <template #footer>
        <template v-if="importStep === 1">
          <el-button @click="importDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="importLoading"
            :disabled="!importWorkFile || !importAttFile || (isAdmin && !importOrgId)"
            @click="doImportParse">开始解析</el-button>
        </template>
        <template v-else-if="importStep === 2">
          <el-button @click="importStep = 1">上一步</el-button>
          <el-button type="warning" :loading="importLoading" @click="doImportValidate"
            v-if="!importValidated">校验</el-button>
          <span v-else style="margin-right:16px;color:var(--el-color-success);font-size:13px">校验已完成</span>
          <el-button type="primary" :loading="importLoading"
            :disabled="importParseData.workRecords.length === 0"
            @click="doImportSave">入库</el-button>
        </template>
        <template v-else>
          <el-button type="primary" @click="importDialogVisible = false">关闭</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, EditPen } from '@element-plus/icons-vue'
import { auth, baseLib, sysOrg } from '../api/index.js'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({ name: '', companyName: '', projectName: '', page: 1, size: 20 })

// 手工录入
const manualDialogVisible = ref(false)
const manualLoading = ref(false)
const manualFormRef = ref(null)
const manualForm = reactive({
  orgId: null, orgName: null,
  name: '', projectName: '',
  actualStartDate: null, actualEndDate: null,
  actualDays: null, standardDays: null,
  workContent: ''
})
const manualRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  actualStartDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  actualEndDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
  standardDays: [{ required: true, message: '请输入标准人天', trigger: 'blur' }]
}

function onManualOrgChange(val) {
  const found = orgList.value.find(o => o.id === val)
  manualForm.orgName = found ? found.orgName : null
}

function resetManual() {
  manualForm.orgId = null; manualForm.orgName = null
  manualForm.name = ''; manualForm.projectName = ''
  manualForm.actualStartDate = null; manualForm.actualEndDate = null
  manualForm.actualDays = null; manualForm.standardDays = null
  manualForm.workContent = ''
  manualFormRef.value?.resetFields()
}

async function doManualSave() {
  const valid = await manualFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (isAdmin && !manualForm.orgId) {
    ElMessage.warning('请选择机构/公司')
    return
  }
  manualLoading.value = true
  try {
    const payload = { ...manualForm }
    if (!isAdmin) {
      payload.orgId = currentUser.orgId
      payload.orgName = selectedOrgName.value
    }
    const res = await baseLib.manual(payload)
    if (res.data.code === 200) {
      ElMessage.success('录入成功')
      manualDialogVisible.value = false
      loadData()
    } else {
      ElMessage.error(res.data.message || '录入失败')
    }
  } catch (e) {
    ElMessage.error('录入失败：' + (e.response?.data?.message || e.message))
  } finally {
    manualLoading.value = false
  }
}

const batchDialogVisible = ref(false)
const batchLoading = ref(false)
const batchStep = ref(1)
const parseData = ref({ workRecords: [], attendances: [] })
const saveTotal = ref(0)
const fileList = ref([])
const uploadRef = ref()

// 导入初始化
const importDialogVisible = ref(false)
const importLoading = ref(false)
const importStep = ref(1)
const importOrgId = ref(null)
const importWorkFile = ref(null)
const importAttFile = ref(null)
const importWorkUploadRef = ref()
const importAttUploadRef = ref()
const importParseData = ref({ workRecords: [], attendances: [] })
const importValidated = ref(false)
const importSaveTotal = ref(0)
const importStat = reactive({ pass: 0, fail: 0, noAtt: 0 })
const importAttPage = ref(1)
const importAttPageSize = ref(20)

const importAttPageData = computed(() => {
  const list = importParseData.value.attendances || []
  const start = (importAttPage.value - 1) * importAttPageSize.value
  return list.slice(start, start + importAttPageSize.value)
})

function onImportWorkChange(file) { importWorkFile.value = file }
function onImportWorkRemove() { importWorkFile.value = null }
function onImportAttChange(file) { importAttFile.value = file }
function onImportAttRemove() { importAttFile.value = null }

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

function calcImportStat() {
  const records = importParseData.value.workRecords
  importStat.pass = records.filter(r => r.attendanceVerified === 1).length
  importStat.fail = records.filter(r => r.attendanceVerified === 0).length
  importStat.noAtt = records.filter(r => r.attendanceVerified == null).length
}

function resetImport() {
  importStep.value = 1
  importOrgId.value = null
  importWorkFile.value = null
  importAttFile.value = null
  importWorkUploadRef.value?.clearFiles()
  importAttUploadRef.value?.clearFiles()
  importParseData.value = { workRecords: [], attendances: [] }
  importAttPage.value = 1
  importValidated.value = false
  importSaveTotal.value = 0
}

async function doImportParse() {
  if (!importWorkFile.value || !importAttFile.value) return
  if (isAdmin && !importOrgId.value) {
    ElMessage.warning('请先选择项目所属公司')
    return
  }
  importLoading.value = true
  try {
    const formData = new FormData()
    formData.append('files', importWorkFile.value.raw)
    formData.append('files', importAttFile.value.raw)
    if (isAdmin) {
      const found = orgList.value.find(o => o.id === importOrgId.value)
      if (found) formData.append('companyName', found.orgName)
      formData.append('orgId', importOrgId.value)
    } else {
      formData.append('companyName', selectedOrgName.value)
      formData.append('orgId', String(currentUser.orgId))
    }
    const res = await baseLib.batchParse(formData)
    if (res.data.code === 200) {
      importParseData.value = { workRecords: res.data.workRecords || [], attendances: res.data.attendances || [] }
      importAttPage.value = 1
      importValidated.value = false
      importStep.value = 2
    } else {
      ElMessage.error('解析失败')
    }
  } catch (e) {
    ElMessage.error('解析失败：' + (e.response?.data?.message || e.message))
  } finally {
    importLoading.value = false
  }
}

async function doImportValidate() {
  importLoading.value = true
  try {
    const formData = new FormData()
    formData.append('files', importWorkFile.value.raw)
    formData.append('files', importAttFile.value.raw)
    if (isAdmin) {
      const found = orgList.value.find(o => o.id === importOrgId.value)
      if (found) formData.append('companyName', found.orgName)
      formData.append('orgId', importOrgId.value)
    } else {
      formData.append('companyName', selectedOrgName.value)
      formData.append('orgId', String(currentUser.orgId))
    }
    const res = await baseLib.batchParse(formData)
    if (res.data.code === 200) {
      importParseData.value = { workRecords: res.data.workRecords || [], attendances: res.data.attendances || [] }
      importAttPage.value = 1
      importValidated.value = true
      calcImportStat()
      ElMessage.success('校验完成')
    } else {
      ElMessage.error('校验失败')
    }
  } catch (e) {
    ElMessage.error('校验失败：' + (e.response?.data?.message || e.message))
  } finally {
    importLoading.value = false
  }
}

async function doImportSave() {
  importLoading.value = true
  try {
    const res = await baseLib.batchSave({
      workRecords: importParseData.value.workRecords,
      attendances: importParseData.value.attendances
    })
    if (res.data.code === 200) {
      importSaveTotal.value = res.data.total
      importStep.value = 3
      loadData()
    } else {
      ElMessage.error('入库失败')
    }
  } catch (e) {
    ElMessage.error('入库失败：' + (e.response?.data?.message || e.message))
  } finally {
    importLoading.value = false
  }
}

function showImportAttendance(row) {
  attRow.value = row
  attLoading.value = false
  attList.value = []
  attTotalSigned.value = 0

  const signedDates = new Set(
    (importParseData.value.attendances || [])
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

.inline-upload :deep(.el-upload-list) { display: inline-flex; align-items: center; margin-left: 12px; }
.upload-tip-inline { font-size: 12px; color: var(--c-text-3); margin-left: 12px; }
.name-link { color: var(--el-color-primary); cursor: pointer; }
.name-link:hover { color: var(--el-color-primary-light-3); }
.att-tag-yes { display:inline-block;padding:0 6px;font-size:12px;line-height:20px;border-radius:4px;color:#fff;background:var(--el-color-success); }
.att-tag-no  { display:inline-block;padding:0 6px;font-size:12px;line-height:20px;border-radius:4px;color:#909399;background:var(--el-color-info-light-5); }
</style>
