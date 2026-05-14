<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">项目管理维护</h2>
      <el-button type="primary" @click="openAdd">新增项目</el-button>
    </div>

    <div class="search-bar">
      <el-select v-if="isAdmin" v-model="query.orgId" placeholder="机构/公司" clearable style="width:200px" @change="load">
        <el-option v-for="org in orgList" :key="org.id" :label="org.orgName" :value="org.id" />
      </el-select>
      <el-input v-model="query.projectName" placeholder="项目名称" clearable style="width:200px" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <div class="table-card">
      <el-table :data="list" border v-loading="loading">
        <el-table-column prop="companyName" label="公司名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="orderNo" label="订单编号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="projectNo" label="项目编号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="projectName" label="项目名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="orderPersonMonths" label="订单人月数" width="100" align="right" />
        <el-table-column prop="orderAmount" label="订单金额" width="120" align="right" />
        <el-table-column prop="orderStartDate" label="订单开始" width="110" />
        <el-table-column prop="orderEndDate" label="订单结束" width="110" />
        <el-table-column prop="bankResponsible" label="行方负责人" width="100" />
        <el-table-column prop="companyResponsible" label="公司方负责人" width="110" />
        <el-table-column label="订单状态" width="110">
          <template #default="{ row }">
            <span :class="['status-pill', row.orderStatus === '验收已核对' ? 'on' : 'off']">
              {{ row.orderStatus || '验收未核对' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <button class="act-link" @click="openEdit(row)">编辑</button>
            <el-popconfirm title="确认删除？" @confirm="del(row.id)">
              <template #reference>
                <button class="act-link danger">删除</button>
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
          @change="load"
        />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑项目' : '新增项目'" width="620px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-form-item label="机构/公司" prop="orgId">
          <el-select v-model="form.orgId" placeholder="请选择机构" style="width:100%" @change="onOrgChange">
            <el-option v-for="org in orgList" :key="org.id" :label="org.orgName" :value="org.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单编号" prop="orderNo">
          <el-input v-model="form.orderNo" placeholder="请输入订单编号" />
        </el-form-item>
        <el-form-item label="项目编号">
          <el-input v-model="form.projectNo" placeholder="请输入项目编号" />
        </el-form-item>
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="form.projectName" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="订单人月数">
          <el-input-number v-model="form.orderPersonMonths" :min="0" :precision="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="订单金额">
          <el-input-number v-model="form.orderAmount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="订单开始时间">
          <el-date-picker v-model="form.orderStartDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
        </el-form-item>
        <el-form-item label="订单结束时间">
          <el-date-picker v-model="form.orderEndDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
        </el-form-item>
        <el-form-item label="行方负责人">
          <el-input v-model="form.bankResponsible" placeholder="请输入行方负责人" />
        </el-form-item>
        <el-form-item label="公司方负责人">
          <el-input v-model="form.companyResponsible" placeholder="请输入公司方负责人" />
        </el-form-item>
        <el-form-item label="订单状态">
          <el-select v-model="form.orderStatus" style="width:100%">
            <el-option label="验收未核对" value="验收未核对" />
            <el-option label="验收已核对" value="验收已核对" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { sysProject, sysOrg } from '../../api/index.js'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ orgId: null, projectName: '', page: 1, size: 20 })

const currentUser = JSON.parse(localStorage.getItem('user') || '{}')
const isAdmin = (currentUser.roles || []).includes('ADMIN')
const orgList = ref([])

const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({
  id: null, orgId: null, companyName: '',
  orderNo: '', projectNo: '', projectName: '',
  orderPersonMonths: null, orderAmount: null,
  orderStartDate: null, orderEndDate: null,
  bankResponsible: '', companyResponsible: '',
  orderStatus: '验收未核对'
})
const rules = {
  orgId: [{ required: true, message: '请选择机构', trigger: 'change' }],
  orderNo: [{ required: true, message: '请输入订单编号', trigger: 'blur' }],
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
}

function onOrgChange(val) {
  const found = orgList.value.find(o => o.id === val)
  form.companyName = found ? found.orgName : ''
}

async function loadOrgs() {
  try {
    const res = await sysOrg.list({})
    if (res.data.code === 200) {
      orgList.value = res.data.data.filter(org => org.orgCode !== 'HBJH' && org.id !== 1)
    }
  } catch { /* ignore */ }
}

async function load() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.orgId) params.orgId = query.orgId
    if (query.projectName) params.projectName = query.projectName
    const res = await sysProject.list(params)
    if (res.data.code === 200) {
      list.value = res.data.data.list
      total.value = res.data.data.total
    }
  } catch { ElMessage.error('加载失败') }
  finally { loading.value = false }
}

function openAdd() {
  Object.assign(form, {
    id: null, orgId: null, companyName: '',
    orderNo: '', projectNo: '', projectName: '',
    orderPersonMonths: null, orderAmount: null,
    orderStartDate: null, orderEndDate: null,
    bankResponsible: '', companyResponsible: '',
    orderStatus: '验收未核对'
  })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id, orgId: row.orgId, companyName: row.companyName,
    orderNo: row.orderNo, projectNo: row.projectNo, projectName: row.projectName,
    orderPersonMonths: row.orderPersonMonths, orderAmount: row.orderAmount,
    orderStartDate: row.orderStartDate, orderEndDate: row.orderEndDate,
    bankResponsible: row.bankResponsible, companyResponsible: row.companyResponsible,
    orderStatus: row.orderStatus || '验收未核对'
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate().catch(() => {})
  if (form.id) {
    await sysProject.update(form.id, form)
  } else {
    await sysProject.add(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function del(id) {
  await sysProject.delete(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(() => {
  loadOrgs()
  if (!isAdmin) query.orgId = currentUser.orgId
  load()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; align-items: center; justify-content: space-between; }
.page-title {
  font-family: 'Noto Serif SC', serif;
  font-size: 20px;
  font-weight: 600;
  color: var(--c-text-1);
}
.search-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  background: var(--c-white);
  border: 1px solid var(--c-border);
  border-radius: 8px;
  padding: 14px 20px;
}
.table-card {
  background: var(--c-white);
  border: 1px solid var(--c-border);
  border-radius: 8px;
  overflow: hidden;
}
.table-card :deep(.el-table__inner-wrapper::before) { display: none; }
.pagination {
  padding: 14px 20px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--c-border);
}
.status-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}
.status-pill.on { background: #dcfce7; color: #15803d; }
.status-pill.off { background: #fef3c7; color: #92400e; }
.act-link {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 13px;
  font-family: inherit;
  padding: 0 8px 0 0;
  color: var(--c-gold);
  opacity: 0.9;
  transition: opacity 0.15s;
}
.act-link:hover { opacity: 1; }
.act-link.danger { color: var(--c-danger); }
</style>
