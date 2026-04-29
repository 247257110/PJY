<template>
  <div class="login-page">
    <!-- Left panel -->
    <div class="left-panel">
      <div class="left-content">
        <div class="brand">
          <span class="brand-mark">✦</span>
          <span class="brand-name">核验系统</span>
        </div>
        <h1 class="system-name">项目验收工作量<br>核验系统</h1>
        <p class="system-desc">高效准确地核验项目验收工作量<br>智能检测人员重复投入问题</p>
        <div class="deco-lines">
          <span></span><span></span><span></span>
        </div>
      </div>
    </div>

    <!-- Right panel -->
    <div class="right-panel">
      <div class="form-wrap">
        <h2 class="form-title">用户登录</h2>
        <p class="form-subtitle">请输入您的账号信息</p>
        <el-form :model="form" :rules="rules" ref="formRef" @submit.prevent="handleLogin" class="login-form">
          <el-form-item prop="username" class="form-field">
            <label class="field-label">用户名</label>
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              size="large"
              prefix-icon="User"
            />
          </el-form-item>
          <el-form-item prop="password" class="form-field">
            <label class="field-label">密码</label>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              prefix-icon="Lock"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button
            class="login-btn"
            size="large"
            :loading="loading"
            @click="handleLogin"
          >
            登&ensp;录
          </el-button>
        </el-form>
      </div>
      <p class="copyright">© 2024 项目验收工作量核验系统</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { auth } from '../api/index.js'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await auth.login(form)
    if (res.data.code === 200) {
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('user', JSON.stringify(res.data.user))
      router.push('/base-lib')
    } else {
      ElMessage.error(res.data.message || '登录失败')
    }
  } catch {
    ElMessage.error('登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ── Left panel ──────────────────────────────────────── */
.left-panel {
  width: 42%;
  background: var(--c-ink);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  flex-shrink: 0;
  overflow: hidden;
}
.left-panel::before {
  content: '';
  position: absolute;
  top: -80px;
  right: -80px;
  width: 320px;
  height: 320px;
  border: 1px solid rgba(22,119,255,0.15);
  border-radius: 50%;
  pointer-events: none;
}
.left-panel::after {
  content: '';
  position: absolute;
  bottom: -100px;
  left: -60px;
  width: 280px;
  height: 280px;
  border: 1px solid rgba(22,119,255,0.10);
  border-radius: 50%;
  pointer-events: none;
}

.left-content {
  position: relative;
  z-index: 1;
  padding: 0 52px;
  max-width: 360px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 40px;
}
.brand-mark { font-size: 20px; color: var(--c-gold); }
.brand-name {
  font-family: 'Noto Serif SC', serif;
  font-size: 15px;
  font-weight: 600;
  color: rgba(255,255,255,0.6);
  letter-spacing: 1px;
}

.system-name {
  font-family: 'Noto Serif SC', serif;
  font-size: 30px;
  font-weight: 700;
  color: #fff;
  line-height: 1.5;
  margin-bottom: 20px;
  letter-spacing: 1px;
}

.system-desc {
  font-size: 14px;
  color: rgba(255,255,255,0.35);
  line-height: 1.9;
  margin-bottom: 40px;
}

.deco-lines { display: flex; gap: 6px; align-items: center; }
.deco-lines span { display: block; height: 2px; border-radius: 2px; background: var(--c-gold); }
.deco-lines span:nth-child(1) { width: 32px; }
.deco-lines span:nth-child(2) { width: 16px; opacity: 0.45; }
.deco-lines span:nth-child(3) { width: 8px; opacity: 0.2; }

/* ── Right panel ─────────────────────────────────────── */
.right-panel {
  flex: 1;
  background: var(--c-white);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
  position: relative;
  animation: fadeInRight 0.4s ease both;
}
@keyframes fadeInRight {
  from { opacity: 0; transform: translateX(20px); }
  to   { opacity: 1; transform: translateX(0); }
}

.form-wrap { width: 100%; max-width: 360px; }

.form-title {
  font-family: 'Noto Serif SC', serif;
  font-size: 26px;
  font-weight: 700;
  color: var(--c-text-1);
  margin-bottom: 6px;
}
.form-subtitle {
  font-size: 14px;
  color: var(--c-text-3);
  margin-bottom: 36px;
}

.login-form { width: 100%; }

.form-field { display: flex; flex-direction: column; margin-bottom: 24px !important; }
.field-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.8px;
  color: var(--c-text-2);
  margin-bottom: 8px;
  display: block;
}

/* Bottom-border inputs */
.form-field :deep(.el-input__wrapper) {
  box-shadow: 0 1px 0 0 var(--c-border) !important;
  border-radius: 0;
  background: transparent;
  padding: 4px 0 10px;
}
.form-field :deep(.el-input__wrapper:hover) {
  box-shadow: 0 1px 0 0 var(--c-text-3) !important;
}
.form-field :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 2px 0 0 var(--c-gold) !important;
}
.form-field :deep(.el-input__inner) {
  font-size: 15px;
  color: var(--c-text-1);
}
.form-field :deep(.el-input__prefix-inner .el-icon) {
  color: var(--c-text-3);
  margin-right: 8px;
}

.login-btn {
  width: 100%;
  height: 48px;
  margin-top: 8px;
  background: var(--c-gold) !important;
  border-color: var(--c-gold) !important;
  color: #ffffff !important;
  font-weight: 600;
  border-radius: 6px;
  letter-spacing: 2px;
  transition: background 0.2s, transform 0.1s;
}
.login-btn:hover {
  background: var(--c-gold-light) !important;
  border-color: var(--c-gold-light) !important;
  transform: translateY(-1px);
}
.login-btn:active { transform: translateY(0); }

.copyright {
  position: absolute;
  bottom: 24px;
  font-size: 12px;
  color: var(--c-text-3);
}
</style>
