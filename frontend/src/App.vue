<template>
  <router-view v-if="isLoginPage" />
  <div v-else class="layout">
    <!-- 左侧导航 -->
    <aside class="sidebar">
      <div class="logo">
        <span class="logo-mark">✦</span>
        <span class="logo-text">核验系统</span>
      </div>
      <nav class="nav">
        <div class="nav-section-label">核心功能</div>
        <router-link v-if="hasMenu('base-lib')" to="/base-lib" class="nav-item" active-class="active">
          <el-icon><DataBoard /></el-icon>
          <span>验收材料基础库</span>
        </router-link>
        <router-link v-if="hasMenu('verify')" to="/verify" class="nav-item" active-class="active">
          <el-icon><DocumentChecked /></el-icon>
          <span>项目验收材料校验</span>
        </router-link>
        <template v-if="hasMenu('sys:user') || hasMenu('sys:role') || hasMenu('sys:org')">
          <div class="nav-section-label">系统管理</div>
          <router-link v-if="hasMenu('sys:user')" to="/sys/user" class="nav-item" active-class="active">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </router-link>
          <router-link v-if="hasMenu('sys:role')" to="/sys/role" class="nav-item" active-class="active">
            <el-icon><UserFilled /></el-icon>
            <span>角色管理</span>
          </router-link>
          <router-link v-if="hasMenu('sys:org')" to="/sys/org" class="nav-item" active-class="active">
            <el-icon><OfficeBuilding /></el-icon>
            <span>机构管理</span>
          </router-link>
        </template>
      </nav>
      <div class="sidebar-footer">
        <div class="avatar">{{ avatarChar }}</div>
        <span class="footer-name">{{ user?.realName || user?.username }}</span>
        <button class="logout-btn" @click="logout" title="退出登录">
          <el-icon><SwitchButton /></el-icon>
        </button>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="main">
      <header class="header">
        <span class="header-title">项目验收工作量核验系统</span>
      </header>
      <div class="content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const isLoginPage = computed(() => route.path === '/login')
const user = computed(() => {
  const u = localStorage.getItem('user')
  return u ? JSON.parse(u) : null
})
const menuKeys = computed(() => user.value?.menuKeys || [])
const hasMenu = (key) => menuKeys.value.includes(key)
const avatarChar = computed(() => {
  const name = user.value?.realName || user.value?.username || '?'
  return name[0]
})

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.push('/login')
}
</script>

<style>
/* ── Design Tokens ───────────────────────────────────── */
:root {
  --c-ink:         #0d2260;
  --c-ink-2:       #1a3578;
  --c-ink-3:       #1e4099;
  --c-gold:        #1677ff;
  --c-gold-light:  #4096ff;
  --c-surface:     #f0f5ff;
  --c-white:       #ffffff;
  --c-border:      #e0e8f8;
  --c-text-1:      #1a1f2e;
  --c-text-2:      #5c6b82;
  --c-text-3:      #9aa5b4;
  --c-success:     #16a34a;
  --c-danger:      #dc2626;

  /* Element Plus primary → blue */
  --el-color-primary:         #1677ff;
  --el-color-primary-light-3: #4096ff;
  --el-color-primary-light-5: #69b1ff;
  --el-color-primary-light-7: #91caff;
  --el-color-primary-light-8: #bae0ff;
  --el-color-primary-light-9: #e6f4ff;
  --el-color-primary-dark-2:  #0958d9;
  --el-color-primary-rgb:     22, 119, 255;
}

/* ── Global Reset ────────────────────────────────────── */
*, *::before, *::after { margin: 0; padding: 0; box-sizing: border-box; }

body {
  font-family: 'DM Sans', 'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', sans-serif;
  background: var(--c-surface);
  color: var(--c-text-1);
  -webkit-font-smoothing: antialiased;
}

/* ── Element Plus Global Overrides ───────────────────── */
/* Primary button: blue bg → white text */
.el-button--primary { color: #ffffff !important; font-weight: 500; }
.el-button--primary:hover,
.el-button--primary:focus { color: #ffffff !important; }

/* Table header */
.el-table th.el-table__cell {
  background-color: var(--c-surface) !important;
  color: var(--c-text-2);
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.4px;
}
/* Table row hover: light blue tint */
.el-table--enable-row-hover .el-table__body tr:hover > td.el-table__cell {
  background-color: #eff6ff !important;
}

/* Dialog */
.el-dialog { border-radius: 10px !important; overflow: hidden; }
.el-dialog__header {
  padding: 20px 24px 16px !important;
  border-bottom: 1px solid var(--c-border);
  margin-right: 0 !important;
}
.el-dialog__title {
  font-family: 'Noto Serif SC', serif;
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text-1);
}
.el-dialog__body { padding: 24px !important; }
.el-dialog__footer {
  padding: 14px 24px !important;
  border-top: 1px solid var(--c-border);
}

/* Card */
.el-card { border-color: var(--c-border) !important; border-radius: 8px !important; }
.el-card__header {
  border-bottom-color: var(--c-border) !important;
  padding: 14px 20px !important;
  font-weight: 500;
  color: var(--c-text-1);
}
.el-card__body { padding: 20px !important; }

/* Status tags */
.el-tag--success {
  background-color: #dcfce7 !important;
  border-color: #bbf7d0 !important;
  color: #15803d !important;
}
.el-tag--danger {
  background-color: #fee2e2 !important;
  border-color: #fecaca !important;
  color: #b91c1c !important;
}

/* Pagination */
.el-pagination.is-background .el-pager li.is-active {
  background-color: var(--c-gold) !important;
  color: #ffffff !important;
}
</style>

<style scoped>
.layout { display: flex; height: 100vh; overflow: hidden; }

/* ── Sidebar ─────────────────────────────────────────── */
.sidebar {
  width: 240px;
  background: var(--c-ink);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}
.logo-mark {
  font-size: 18px;
  color: var(--c-gold);
  line-height: 1;
  flex-shrink: 0;
}
.logo-text {
  font-family: 'Noto Serif SC', serif;
  font-weight: 600;
  font-size: 16px;
  color: #fff;
  letter-spacing: 1px;
}

.nav {
  flex: 1;
  padding: 8px 0;
  overflow-y: auto;
}
.nav::-webkit-scrollbar { width: 0; }

.nav-section-label {
  padding: 14px 20px 5px;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 1.2px;
  color: rgba(255,255,255,0.25);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 20px;
  color: rgba(255,255,255,0.5);
  text-decoration: none;
  font-size: 14px;
  font-weight: 400;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
  border-left: 3px solid transparent;
}
.nav-item:hover {
  background: var(--c-ink-2);
  color: rgba(255,255,255,0.8);
  border-left-color: rgba(22,119,255,0.35);
}
.nav-item.active {
  background: var(--c-ink-2);
  color: #fff;
  border-left-color: var(--c-gold);
  font-weight: 500;
}
.nav-item .el-icon { font-size: 16px; flex-shrink: 0; }

.sidebar-footer {
  border-top: 1px solid rgba(255,255,255,0.06);
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--c-ink-3);
  border: 1px solid rgba(22,119,255,0.4);
  color: var(--c-gold);
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.footer-name {
  flex: 1;
  font-size: 13px;
  color: rgba(255,255,255,0.55);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.logout-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: rgba(255,255,255,0.3);
  padding: 4px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  font-size: 16px;
  transition: color 0.15s;
  flex-shrink: 0;
}
.logout-btn:hover { color: var(--c-gold); }

/* ── Main Area ───────────────────────────────────────── */
.main { flex: 1; display: flex; flex-direction: column; overflow: hidden; min-width: 0; }

.header {
  height: 56px;
  background: var(--c-white);
  border-bottom: 1px solid var(--c-border);
  display: flex;
  align-items: center;
  padding: 0 28px;
  flex-shrink: 0;
}
.header-title {
  font-family: 'Noto Serif SC', serif;
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text-1);
  letter-spacing: 0.5px;
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background: var(--c-surface);
}
.content::-webkit-scrollbar { width: 5px; }
.content::-webkit-scrollbar-track { background: transparent; }
.content::-webkit-scrollbar-thumb { background: var(--c-border); border-radius: 3px; }
</style>
