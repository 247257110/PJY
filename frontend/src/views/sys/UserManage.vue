<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
      <el-button type="primary" @click="openAdd">新增用户</el-button>
    </div>

    <div class="search-bar">
      <el-input v-model="query.username" placeholder="用户名" clearable style="width:160px" @keyup.enter="load" />
      <el-input v-model="query.realName" placeholder="姓名" clearable style="width:160px" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <div class="table-card">
      <el-table :data="list" border>
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <span :class="['status-pill', row.status === 1 ? 'on' : 'off']">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="150">
          <template #default="{ row }">
            <span v-for="r in row.roles" :key="r.id" class="role-tag">{{ r.roleName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="机构" width="120">
          <template #default="{ row }">
            {{ orgs.find(o => o.id === row.orgId)?.orgName || '-' }}
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
        <el-pagination background layout="total, prev, pager, next"
          :total="total" :page-size="query.size"
          v-model:current-page="query.page" @current-change="load" />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新增用户'" width="480px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="密码" :prop="form.id ? '' : 'password'">
          <el-input v-model="form.password" type="password" :placeholder="form.id ? '不填则不修改' : '请输入密码'" show-password />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="角色">
          <el-checkbox-group v-model="form.roleIds">
            <el-checkbox v-for="r in roles" :key="r.id" :value="r.id">{{ r.roleName }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="机构">
          <el-select v-model="form.orgId" placeholder="请选择机构" clearable style="width:100%">
            <el-option v-for="o in orgs" :key="o.id" :label="o.orgName" :value="o.id" />
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
import { sysUser, sysRole, sysOrg } from '../../api/index.js'

const list = ref([])
const total = ref(0)
const roles = ref([])
const orgs = ref([])
const query = reactive({ username: '', realName: '', page: 1, size: 20 })
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({ id: null, username: '', password: '', realName: '', email: '', phone: '', status: 1, roleIds: [], orgId: null })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function load() {
  const res = await sysUser.list({ ...query })
  list.value = res.data.data.list
  total.value = res.data.data.total
}

async function loadRoles() {
  const res = await sysRole.list()
  roles.value = res.data.data
}

async function loadOrgs() {
  const res = await sysOrg.list({})
  orgs.value = res.data.data?.list || res.data.data || []
}

function openAdd() {
  Object.assign(form, { id: null, username: '', password: '', realName: '', email: '', phone: '', status: 1, roleIds: [], orgId: null })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id, username: row.username, password: '', realName: row.realName,
    email: row.email, phone: row.phone, status: row.status,
    roleIds: (row.roles || []).map(r => r.id),
    orgId: row.orgId || null
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  if (form.id) {
    await sysUser.update(form.id, { ...form })
  } else {
    await sysUser.add({ ...form })
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function del(id) {
  await sysUser.delete(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(() => { load(); loadRoles(); loadOrgs() })
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

.role-tag {
  display: inline-block;
  padding: 1px 8px;
  background: #f0f2f5;
  border-radius: 4px;
  font-size: 12px;
  color: var(--c-text-2);
  margin-right: 4px;
}

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

.pagination {
  padding: 14px 20px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--c-border);
}
</style>
