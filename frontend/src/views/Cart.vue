<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { api, money, type Row } from '../api'
const router = useRouter(),
  items = ref<Row[]>([]),
  selected = ref<string[]>([]),
  busy = ref(false),
  error = ref('')
const total = computed(() =>
  items.value
    .filter((p) => selected.value.includes(p.id))
    .reduce((s, p) => s + Number(p.priceFen) * Number(p.quantity), 0),
)
async function load() {
  try {
    items.value = await api<Row[]>('GET', '/cart')
    selected.value = selected.value.filter((id) => items.value.some((p) => p.id === id))
  } catch {
    error.value = '购物车加载失败'
  }
}
async function change(p: Row, n: number | undefined) {
  if (!n) return
  busy.value = true
  try {
    await api('PATCH', `/cart/items/${p.id}`, { quantity: n })
    await load()
  } finally {
    busy.value = false
  }
}
async function remove(id: string) {
  await api('DELETE', `/cart/items/${id}`)
  await load()
}
function checkout() {
  sessionStorage.setItem(
    'minimall.checkout',
    JSON.stringify({ source: 'CART', items: [], cartItemIds: selected.value }),
  )
  void router.push('/checkout')
}
onMounted(load)
</script>
<template>
  <div class="page-title">
    <h1>购物车</h1>
    <p>选好数量，再把喜欢带回家。</p>
  </div>
  <p v-if="error" class="state">{{ error }}</p>
  <div v-else-if="!items.length" class="state">
    购物车还空着。<RouterLink to="/products">去挑选好物</RouterLink>
  </div>
  <div v-else class="panel">
    <div v-for="p in items" :key="p.id" class="cart-row">
      <input
        v-model="selected"
        type="checkbox"
        :value="p.id"
        :disabled="!p.available"
        :aria-label="`选择${p.name}`"
      /><img :src="String(p.imageUrl)" :alt="String(p.name)" />
      <div class="cart-name">
        <RouterLink :to="`/products/${p.productId}`">{{ p.name }}</RouterLink
        ><small>{{ p.specification }}</small
        ><small v-if="!p.available" class="error-text">商品下架或库存不足</small>
      </div>
      <span>¥ {{ money(p.priceFen) }}</span
      ><el-input-number
        :model-value="Number(p.quantity)"
        :min="1"
        :max="99"
        :disabled="busy"
        @change="(n: number | undefined) => change(p, n)"
      /><strong>¥ {{ money(Number(p.priceFen) * Number(p.quantity)) }}</strong
      ><button class="plain" @click="remove(p.id)">删除</button>
    </div>
    <div class="cart-total">
      <span>已选 {{ selected.length }} 件商品</span><strong>合计 ¥ {{ money(total) }}</strong
      ><button class="primary" :disabled="!selected.length || busy" @click="checkout">
        确认下单
      </button>
    </div>
  </div>
</template>
