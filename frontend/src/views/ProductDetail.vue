<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'
import { api, money, type Row } from '../api'
import { ElMessage } from 'element-plus'
const route = useRoute(),
  router = useRouter(),
  auth = useAuth(),
  product = ref<Row | null>(null),
  quantity = ref(1),
  busy = ref(false),
  error = ref('')
async function load() {
  try {
    product.value = await api('GET', `/products/${route.params.id}`)
  } catch {
    error.value = '商品不可用，返回列表重新选择。'
  }
}
async function add() {
  if (!auth.loggedIn) {
    await router.push('/login')
    return
  }
  busy.value = true
  try {
    await api('POST', '/cart/items', { productId: product.value?.id, quantity: quantity.value })
    ElMessage.success('已加入购物车')
  } finally {
    busy.value = false
  }
}
function buy() {
  sessionStorage.setItem(
    'minimall.checkout',
    JSON.stringify({
      source: 'DIRECT',
      items: [{ productId: product.value?.id, quantity: quantity.value }],
      cartItemIds: [],
    }),
  )
  void router.push('/checkout')
}
onMounted(load)
</script>
<template>
  <RouterLink to="/products" class="back-link">返回商品列表</RouterLink>
  <p v-if="error" class="state">{{ error }}</p>
  <section v-else-if="product" class="detail-layout">
    <div class="detail-art">
      <img :src="String(product.imageUrl)" :alt="String(product.name)" />
    </div>
    <div class="detail-copy">
      <p class="product-spec">{{ product.specification }}</p>
      <h1>{{ product.name }}</h1>
      <p class="detail-description">{{ product.description }}</p>
      <p class="detail-price">¥ {{ money(product.priceFen) }}</p>
      <div class="availability">
        普通库存 {{ product.stockAvailable }} 件<span>提交订单时确认最新价格和库存</span>
      </div>
      <div class="quantity-line">
        <span>购买数量</span><el-input-number v-model="quantity" :min="1" :max="99" />
      </div>
      <div class="button-row">
        <button
          class="primary"
          :disabled="Number(product.stockAvailable) < quantity || busy"
          data-testid="buy-now"
          @click="buy"
        >
          立即购买</button
        ><button class="secondary" :disabled="busy" @click="add">加入购物车</button>
      </div>
      <div class="detail-service">未付款可取消 · 模拟支付 · 发货后确认收货</div>
    </div>
  </section>
  <p v-else class="state">正在加载商品…</p>
</template>
