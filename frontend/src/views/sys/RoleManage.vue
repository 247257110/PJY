<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">角色管理</h2>
      <el-button type="primary" @click="openAdd">新增角色</el-button>
    </div>

    <div class="table-card">
      <el-table :data="list" border>
        <el-table-column prop="roleName" label="角色名称" width="150" />
        <el-table-column prop="roleCode" label="角色编码" width="150" />
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <button class="act-link" @click="openEdit(row)">编辑</button>
            <button class="act-link" @click="openMenuDialog(row)">权限配置</button>
            <el-popconfirm title="确认删除？" @confirm="del(row.id)">
              <template #reference>
                <button class="act-link danger">删除</button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑角色' : '新增角色'" width="420px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" />
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
    <el-dialog v-model="menuDialogVisible" title="权限配置" width="420px">
      <el-checkbox-group v-model="roleMenuIds">
        <el-checkbox v-for="m in allMenus" :key="m.id" :value="m.id" style="display:block;margin:6px 0">
          {{ m.menuName }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMenus">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { sysRole, sysMenu } from '../../api/index.js'

const list = ref([])
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({ id: null, roleName: '', roleCode: '', remark: '' })
const rules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
}

const menuDialogVisible = ref(false)
const currentRoleId = ref(null)
const allMenus = ref([])
const roleMenuIds = ref([])

async function load() {
  const res = await sysRole.list()
  list.value = res.data.data
}

function openAdd() {
  Object.assign(form, { id: null, roleName: '', roleCode: '', remark: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, { id: row.id, roleName: row.roleName, roleCode: row.roleCode, remark: row.remark })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  if (form.id) {
    await sysRole.update(form.id, form)
  } else {
    await sysRole.add(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function del(id) {
  await sysRole.delete(id)
  ElMessage.success('删除成功')
  load()
}

async function openMenuDialog(row) {
  currentRoleId.value = row.id
  const [allRes, roleRes] = await Promise.all([
    sysMenu.listAll(),
    sysRole.getMenus(row.id)
  ])
  allMenus.value = allRes.data.data
  roleMenuIds.value = roleRes.data.data.map(m => m.id)
  menuDialogVisible.value = true
}

async function saveMenus() {
  await sysRole.updateMenus(currentRoleId.value, roleMenuIds.value)
  ElMessage.success('权限保存成功')
  menuDialogVisible.value = false
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

.table-card {
  background: var(--c-white);
  border: 1px solid var(--c-border);
  border-radius: 8px;
  overflow: hidden;
}
.table-card :deep(.el-table__inner-wrapper::before) { display: none; }

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
