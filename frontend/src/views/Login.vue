<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'
import { api } from '../api'
import { ElMessage } from 'element-plus'
const route = useRoute(),
  router = useRouter(),
  auth = useAuth(),
  username = ref(''),
  password = ref(''),
  busy = ref(false),
  error = ref('')
const registering = computed(() => route.path === '/register')
async function submit() {
  busy.value = true
  error.value = ''
  try {
    if (registering.value) {
      await api('POST', '/auth/register', { username: username.value, password: password.value })
      ElMessage.success('注册成功，请登录')
      await router.push('/login')
    } else {
      await auth.login(username.value, password.value)
      const dest = String(route.query.redirect ?? '/products')
      await router.push(dest.startsWith('/') && !dest.startsWith('//') ? dest : '/products')
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="auth-layout">
    <div class="auth-intro">
      <span class="brand-mark large">拾</span>
      <h1>下一件喜欢，<br />从这里开始。</h1>
      <p>登录后收藏日常、参与限量上新，<br />让每一笔订单都有清晰的去向。</p>
    </div>
    <form class="auth-card" @submit.prevent="submit">
      <h2>{{ registering ? '创建账号' : '欢迎回来' }}</h2>
      <p>{{ registering ? '用一个账号开启你的好物清单。' : '登录，继续你的好物之旅。' }}</p>
      <label
        >用户名<input
          v-model="username"
          required
          minlength="4"
          maxlength="32"
          autocomplete="username"
          placeholder="4–32 位字母、数字或下划线"
          data-testid="username" /></label
      ><label
        >密码<input
          v-model="password"
          required
          minlength="8"
          type="password"
          :autocomplete="registering ? 'new-password' : 'current-password'"
          placeholder="至少 8 位"
          data-testid="password"
      /></label>
      <p v-if="error" class="error-text" role="alert">{{ error }}</p>
      <button class="primary wide" :disabled="busy" data-testid="auth-submit">
        {{ busy ? '正在处理…' : registering ? '注册账号' : '登录' }}
      </button>
      <p class="auth-switch">
        <RouterLink :to="registering ? '/login' : '/register'">
          {{ registering ? '已有账号？去登录' : '还没有账号？创建账号' }}
        </RouterLink>
      </p>
    </form>
  </div>
</template>
