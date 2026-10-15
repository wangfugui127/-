<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'
import { api, money, time, freshKey, ApiError, type Row } from '../api'
const route = useRoute(),
  router = useRouter(),
  auth = useAuth(),
  a = ref<Row | null>(null),
  busy = ref(false),
  error = ref(''),
  receiver = ref({ name: '', phone: '', address: '' }),
  now = ref(Date.now()),
  offset = ref(0)
let timer: ReturnType<typeof setInterval> | undefined
const key = ref(freshKey()),
  body = ref<Record<string, unknown> | null>(null)
const active = computed(
  () =>
    a.value &&
    a.value.status === 'PUBLISHED' &&
    now.value + offset.value >= new Date(String(a.value.startAt)).getTime() &&
    now.value + offset.value < new Date(String(a.value.endAt)).getTime() &&
    Number(a.value.stockAvailable) > 0 &&
    !a.value.hasPurchased,
)
async function load() {
  try {
    a.value = await api('GET', `/seckill-activities/${route.params.id}`)
    offset.value = new Date(String(a.value?.serverNow)).getTime() - Date.now()
  } catch {
    error.value = '活动暂时无法加载'
  }
}
async function buy() {
  if (!auth.loggedIn) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  busy.value = true
  error.value = ''
  body.value ??= { receiver: { ...receiver.value }, expectedTotalFen: a.value?.activityPriceFen }
  try {
    const order = await api(
      'POST',
      `/seckill-activities/${route.params.id}/orders`,
      body.value,
      key.value,
    )
    await router.push(`/orders/${order.id}`)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '结果未确认'
    if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 429) {
      key.value = freshKey()
      body.value = null
    }
    await load()
  } finally {
    busy.value = false
  }
}
onMounted(async () => {
  await load()
  timer = setInterval(() => (now.value = Date.now()), 1000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>
<template>
  <RouterLink to="/activities" class="back-link">返回限量上新</RouterLink>
  <p v-if="!a" class="state">{{ error || '正在加载活动…' }}</p>
  <section v-else class="detail-layout">
    <div class="detail-art activity">
      <img :src="String(a.imageUrl)" :alt="String(a.productName)" />
    </div>
    <div class="detail-copy">
      <span class="activity-badge">限量 {{ a.stockTotal }} 件</span>
      <h1>{{ a.title }}</h1>
      <p>{{ a.productName }} · {{ a.specification }}</p>
      <p class="detail-price">
        ¥ {{ money(a.activityPriceFen) }} <del>¥ {{ money(a.originalPriceFen) }}</del>
      </p>
      <p>活动时间：{{ time(a.startAt) }} 至 {{ time(a.endAt) }}</p>
      <div class="stock-progress">
        <span
          :style="{
            width: `${(Number(a.stockAvailable) / Math.max(1, Number(a.stockTotal))) * 100}%`,
          }"
        ></span>
      </div>
      <p>剩余 {{ a.stockAvailable }} 件</p>
      <p class="rule-note">每场活动每人限购一次。取消或超时关闭后可重新抢购，支付失败请在原订单重试。</p>
      <RouterLink
        v-if="a.pendingOrderId"
        :to="`/orders/${a.pendingOrderId}`"
        class="primary wide"
        data-testid="continue-payment"
        >继续支付</RouterLink
      >
      <form v-else @submit.prevent="buy">
        <label
          >收货人<input
            v-model="receiver.name"
            required
            maxlength="64"
            :disabled="Boolean(body)" /></label
        ><label
          >联系电话<input
            v-model="receiver.phone"
            required
            minlength="8"
            maxlength="32"
            :disabled="Boolean(body)" /></label
        ><label
          >收货地址<input
            v-model="receiver.address"
            required
            minlength="5"
            maxlength="256"
            :disabled="Boolean(body)"
        /></label>
        <p v-if="error" class="error-text">{{ error }}</p>
        <button
          class="primary wide"
          :disabled="busy || (!active && !body)"
          data-testid="seckill-buy"
        >
          {{
            busy
              ? '正在抢购…'
              : body
                ? '重试确认结果'
                : a.hasPurchased
                  ? '本场资格已使用'
                  : active
                    ? '参与抢购'
                    : '暂不可购买'
          }}
        </button>
      </form>
    </div>
  </section>
</template>
