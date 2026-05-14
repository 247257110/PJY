import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router/index.js'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截：自动带 token
http.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截：401 跳登录
http.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401 || err.response?.status === 403) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
    }
    return Promise.reject(err)
  }
)

export const auth = {
  login: (data) => http.post('/auth/login', data),
  info: () => http.get('/auth/info')
}

export const baseLib = {
  list: (params) => http.get('/base-lib/list', { params }),
  delete: (id) => http.delete(`/base-lib/${id}`),
  manual: (data) => http.post('/base-lib/manual', data),
  batchParse: (formData) => http.post('/base-lib/batch-parse', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000
  }),
  batchSave: (data) => http.post('/base-lib/batch-save', data, { timeout: 60000 }),
  batchInit: (formData) => http.post('/base-lib/batch-init', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000
  }),
  attendance: (workRecordId) => http.get('/base-lib/attendance', { params: { workRecordId } })
}

export const verify = {
  upload: (formData) => http.post('/verify/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000
  }),
  check: (batchId) => http.get(`/verify/check/${batchId}`),
  confirm: (batchId) => http.post(`/verify/confirm/${batchId}`),
  cancel: (batchId) => http.delete(`/verify/cancel/${batchId}`),
  batches: () => http.get('/verify/batches'),
  preview: (batchId) => http.get(`/verify/preview/${batchId}`)
}

export const sysUser = {
  list: (params) => http.get('/sys/user/list', { params }),
  getById: (id) => http.get(`/sys/user/${id}`),
  add: (data) => http.post('/sys/user', data),
  update: (id, data) => http.put(`/sys/user/${id}`, data),
  delete: (id) => http.delete(`/sys/user/${id}`)
}

export const sysRole = {
  list: () => http.get('/sys/role/list'),
  add: (data) => http.post('/sys/role', data),
  update: (id, data) => http.put(`/sys/role/${id}`, data),
  delete: (id) => http.delete(`/sys/role/${id}`),
  getMenus: (id) => http.get(`/sys/role/${id}/menus`),
  updateMenus: (id, menuIds) => http.put(`/sys/role/${id}/menus`, menuIds)
}

export const sysMenu = {
  listAll: () => http.get('/sys/role/menus/all')
}

export const sysOrg = {
  list: (params) => http.get('/sys/org/list', { params }),
  add: (data) => http.post('/sys/org', data),
  update: (id, data) => http.put(`/sys/org/${id}`, data),
  delete: (id) => http.delete(`/sys/org/${id}`)
}

export const sysProject = {
  list: (params) => http.get('/sys/project/list', { params }),
  getById: (id) => http.get(`/sys/project/${id}`),
  add: (data) => http.post('/sys/project', data),
  update: (id, data) => http.put(`/sys/project/${id}`, data),
  delete: (id) => http.delete(`/sys/project/${id}`),
  listByOrg: (orgId) => http.get('/sys/project/by-org', { params: { orgId } })
}
