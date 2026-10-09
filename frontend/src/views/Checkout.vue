<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'
import { api, money, freshKey, ApiError, type Row } from '../api'
const router = useRouter(),
  auth = useAuth(),
  lines = ref<Row[]>([]),
  total = ref(0),
  receiver = ref({ name: '', phone: '', address: '' }),
  busy = ref(false),
  error = ref(''),
  selection = ref<Record<string, unknown>>({})
const storageKey = `minimall.attempt.${auth.user?.id}`
const pending = ref<{ key: string; body: Record<string, unknown> } | null>(
  JSON.parse(sessionStorage.getItem(storageKey) ?? 'null'),
)
async function load() {
  try {
    selection.value = JSON.parse(sessionStorage.getItem('minimall.checkout') ?? 'null') ?? {}
    if (selection.value.source === 'CART') {
      const cart = await api<Row[]>('GET', '/cart')
      lines.value = cart.filter((p) => (selection.value.cartItemIds as string[]).includes(p.id))
    } else if (selection.value.source === 'DIRECT') {
      for (const item of selection.value.items as { productId: string; quantity: number }[]) {
        const p = await api('GET', `/products/${item.productId}`)
        lines.value.push({ ...p, quantity: item.quantity })
      }
    } else {
      error.value = '还没有选择商品，请返回商品页。'
      return
    }
    total.value = lines.value.reduce((s, p) => s + Number(p.priceFen) * Number(p.quantity), 0)
    if (pending.value) {
      receiver.value = pending.value.body.receiver as typeof receiver.value
      error.value = '上次提交结果未确认，请用原请求重试。'
    }
  } catch {
    error.value = '确认页加载失败，请返回购物车重新选择。'
  }
}
async function submit() {
  busy.value = true
  error.value = ''
  if (!pending.value) {
    pending.value = {
      key: freshKey(),
      body: { ...selection.value, expectedTotalFen: total.value, receiver: { ...receiver.value } },
    }
    sessionStorage.setItem(storageKey, JSON.stringify(pending.value))
  }
  try {
    const order = await api('POST', '/orders', pending.value.body, pending.value.key)
    sessionStorage.removeItem(storageKey)
    pending.value = null
    await router.replace(`/orders/${order.id}`)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '结果未确认'
    if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 429) {
      pending.value = null
      sessionStorage.removeItem(storageKey)
    }
  } finally {
    busy.value = false
  }
}
onMounted(load)
</script>
<template>
  <div class="page-title">
    <h1>确认下单</h1>
    <p>确认商品和地址，订单创建后开始计时。</p>
  </div>
  <div class="checkout-layout">
    <section class="panel">
      <h2>收货信息</h2>
      <form id="checkout-form" @submit.prevent="submit">
        <label
          >收货人<input
            v-model="receiver.name"
            required
            maxlength="64"
            :disabled="Boolean(pending)"
            data-testid="receiver-name" /></label
        ><label
          >联系电话<input
            v-model="receiver.phone"
            required
            minlength="8"
            maxlength="32"
            :disabled="Boolean(pending)"
            data-testid="receiver-phone" /></label
        ><label
          >收货地址<textarea
            v-model="receiver.address"
            required
            minlength="5"
            maxlength="256"
            :disabled="Boolean(pending)"
            data-testid="receiver-address"
          ></textarea>
        </label>
      </form>
      <p class="muted">此项目不连接真实支付渠道。下单后可体验支付、取消和收货流程。</p>
    </section>
    <section class="panel order-summary">
      <h2>商品清单</h2>
      <div v-for="p in lines" :key="p.id" class="summary-line">
        <img :src="String(p.imageUrl)" :alt="String(p.name)" />
        <div>
          <strong>{{ p.name }}</strong
          ><small>{{ p.specification }} × {{ p.quantity }}</small>
        </div>
        <span>¥ {{ money(Number(p.priceFen) * Number(p.quantity)) }}</span>
      </div>
      <div class="summary-total">
        <span>应付金额</span><strong>¥ {{ money(total) }}</strong>
      </div>
      <p v-if="error" class="error-text" role="alert">{{ error }}</p>
      <button
        class="primary wide"
        type="submit"
        form="checkout-form"
        :disabled="busy || !lines.length"
        data-testid="create-order"
      >
        {{ busy ? '正在确认…' : pending ? '重试确认结果' : '提交订单' }}
      </button>
      <p class="muted">重复点击或网络重试会确认同一笔订单。</p>
    </section>
  </div>
</template>
