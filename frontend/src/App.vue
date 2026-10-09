<script setup lang="ts">
import { useAuth } from './stores/auth'
import { useRouter } from 'vue-router'
const auth = useAuth(),
  router = useRouter()
async function logout() {
  await auth.logout()
  await router.push('/products')
}
</script>
<template>
  <header class="topbar">
    <div class="nav-inner">
      <RouterLink class="brand" to="/products">
        <span class="brand-mark">拾</span><span>拾物<small>MiniMall</small></span>
      </RouterLink>
      <nav aria-label="主导航">
        <RouterLink to="/products">日常好物</RouterLink
        ><RouterLink to="/activities">限量上新</RouterLink
        ><RouterLink v-if="auth.loggedIn" to="/orders">我的订单</RouterLink>
      </nav>
      <div class="nav-actions">
        <RouterLink v-if="auth.loggedIn" to="/cart" class="nav-cart">购物车</RouterLink
        ><RouterLink v-if="auth.isAdmin" to="/admin/products">管理后台</RouterLink
        ><RouterLink v-if="!auth.loggedIn" to="/login" class="login-link">登录 / 注册</RouterLink
        ><button v-else class="plain" @click="logout">{{ auth.user?.username }} · 退出</button>
      </div>
    </div>
  </header>
  <main class="main-shell"><RouterView /></main>
  <footer class="footer">
    <span>拾物 MiniMall</span><span>普通购买 · 限量上新 · 订单履约</span
    ><span>学习项目，支付为模拟体验</span>
  </footer>
</template>
