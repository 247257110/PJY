<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">机构管理</h2>
      <el-button type="primary" @click="openAdd">新增机构</el-button>
    </div>

    <div class="search-bar">
      <el-input v-model="query.orgName" placeholder="机构名称" clearable style="width:200px" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <div class="table-card">
      <el-table :data="list" border>
        <el-table-column prop="orgName" label="机构名称" min-width="150" />
        <el-table-column prop="orgCode" label="机构编码" width="150" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <span :class="['status-pill', row.status === 1 ? 'on' : 'off']">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column prop="remark" label="备注" />
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
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑机构' : '新增机构'" width="440px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="机构名称" prop="orgName">
          <el-input v-model="form.orgName" />
        </el-form-item>
        <el-form-item label="机构编码">
          <el-input v-model="form.orgCode" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
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
import { sysOrg } from '../../api/index.js'

const list = ref([])
const query = reactive({ orgName: '' })
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({ id: null, orgName: '', orgCode: '', sort: 0, status: 1, remark: '' })
const rules = {
  orgName: [{ required: true, message: '请输入机构名称', trigger: 'blur' }]
}

async function load() {
  const res = await sysOrg.list({ orgName: query.orgName })
  list.value = res.data.data
}

function openAdd() {
  Object.assign(form, { id: null, orgName: '', orgCode: '', sort: 0, status: 1, remark: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, { id: row.id, orgName: row.orgName, orgCode: row.orgCode, sort: row.sort, status: row.status, remark: row.remark })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  if (form.id) {
    await sysOrg.update(form.id, form)
  } else {
    await sysOrg.add(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function del(id) {
  await sysOrg.delete(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
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

.status-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}
.status-pill.on { background: #dcfce7; color: #15803d; }
.status-pill.off { background: #fee2e2; color: #b91c1c; }

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
