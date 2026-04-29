import { createRouter, createWebHistory } from 'vue-router'
import BaseLib from '../views/BaseLib.vue'
import Verify from '../views/Verify.vue'
import Login from '../views/Login.vue'
import UserManage from '../views/sys/UserManage.vue'
import RoleManage from '../views/sys/RoleManage.vue'
import OrgManage from '../views/sys/OrgManage.vue'

const routes = [
  { path: '/login', component: Login, meta: { public: true } },
  { path: '/', redirect: '/base-lib' },
  { path: '/base-lib', component: BaseLib, meta: { title: '验收材料基础库', menuKey: 'base-lib' } },
  { path: '/verify', component: Verify, meta: { title: '项目验收材料校验', menuKey: 'verify' } },
  { path: '/sys/user', component: UserManage, meta: { title: '用户管理', menuKey: 'sys:user' } },
  { path: '/sys/role', component: RoleManage, meta: { title: '角色管理', menuKey: 'sys:role' } },
  { path: '/sys/org', component: OrgManage, meta: { title: '机构管理', menuKey: 'sys:org' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (!to.meta.public && !token) {
    return '/login'
  }
  if (token && to.meta.menuKey) {
    const u = localStorage.getItem('user')
    const menuKeys = u ? (JSON.parse(u).menuKeys || []) : []
    if (!menuKeys.includes(to.meta.menuKey)) {
      return false
    }
  }
})

export default router
