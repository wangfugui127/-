<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { api, money, time, orderLabels, type Row } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'
const route = useRoute(),
  order = ref<Row | null>(null),
  busy = ref(false),
  error = ref(''),
  scenario = ref('SUCCESS'),
  now = ref(Date.now()),
  clockOffset = ref(0)
let timer: ReturnType<typeof setInterval> | undefined,
  polling = false
const payment = computed(() => order.value?.payment as Row | null)
const remaining = computed(() =>
  order.value
    ? Math.max(
        0,
        Math.floor(
          (new Date(String(order.value.expiresAt)).getTime() - now.value - clockOffset.value) /
            1000,
        ),
      )
    : 0,
)
const countdown = computed(
  () => `${Math.floor(remaining.value / 60)}:${String(remaining.value % 60).padStart(2, '0')}`,
)
async function load() {
  try {
    order.value = await api('GET', `/orders/${route.params.id}`)
    clockOffset.value = new Date(String(order.value?.serverNow)).getTime() - Date.now()
    error.value = ''
  } catch {
    error.value = '订单暂时无法加载。'
  }
}
async function pay() {
  busy.value = true
  try {
    await api('POST', `/orders/${route.params.id}/mock-payments`, { scenario: scenario.value })
    ElMessage.success('模拟支付已发起，正在等待结果')
    await load()
  } finally {
    busy.value = false
  }
}
async function cancel() {
  try {
    await ElMessageBox.confirm('取消后释放本订单库存，确定取消吗？', '取消订单', {
      confirmButtonText: '取消订单',
      cancelButtonText: '继续付款',
    })
    busy.value = true
    await api('POST', `/orders/${route.params.id}/cancel`, { reason: '用户取消' })
    await load()
  } catch {
    await load()
  } finally {
    busy.value = false
  }
}
async function confirm() {
  busy.value = true
  try {
    await api('POST', `/orders/${route.params.id}/confirm`, {})
    await load()
    ElMessage.success('已确认收货')
  } finally {
    busy.value = false
  }
}
onMounted(async () => {
  await load()
  timer = setInterval(async () => {
    now.value = Date.now()
    if (!polling) {
      polling = true
      try {
        if (
          (order.value &&
            ['PENDING_PAYMENT', 'PAID', 'SHIPPED'].includes(String(order.value.status))) ||
          payment.value?.status === 'REFUND_PENDING'
        )
          await load()
      } finally {
        polling = false
      }
    }
  }, 2000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>
<template>
  <RouterLink class="back-link" to="/orders">返回我的订单</RouterLink>
  <p v-if="error" class="state">{{ error }} <button @click="load">重新加载</button></p>
  <section v-if="order" class="order-detail">
    <div class="status-banner">
      <div>
        <span class="status-dot"></span>
        <h1 data-testid="order-status">{{ orderLabels[String(order.status)] }}</h1>
        <p v-if="order.status === 'PENDING_PAYMENT'">
          付款剩余 {{ countdown }}，超时后库存将释放。
        </p>
        <p v-else-if="order.status === 'PAID'">付款已确认，等待商家发货。</p>
        <p v-else-if="order.status === 'SHIPPED'">商品已发出，收到后请确认收货。</p>
        <p v-else>订单状态已更新，记录会保留。</p>
      </div>
      <strong class="detail-price">¥ {{ money(order.payableFen) }}</strong>
    </div>
    <div class="order-detail-grid">
      <section class="panel">
        <h2>订单商品</h2>
        <div v-for="p in order.items as Row[]" :key="p.id" class="summary-line">
          <img :src="String(p.imageUrl)" :alt="String(p.productName)" />
          <div>
            <strong>{{ p.productName }}</strong
            ><small>{{ p.specification }} × {{ p.quantity }}</small>
          </div>
          <span>¥ {{ money(p.subtotalFen) }}</span>
        </div>
        <dl class="info-list">
          <dt>订单号</dt>
          <dd class="break">{{ order.orderNo }}</dd>
          <dt>创建时间</dt>
          <dd>{{ time(order.createdAt) }}</dd>
          <dt>订单类型</dt>
          <dd>{{ order.orderType === 'SECKILL' ? '限量活动' : '普通购买' }}</dd>
          <dt>支付状态</dt>
          <dd>{{ payment?.status ?? '尚未发起' }}</dd>
          <dt v-if="order.trackingNo">物流</dt>
          <dd v-if="order.trackingNo">{{ order.logisticsCompany }} / {{ order.trackingNo }}</dd>
        </dl>
      </section>
      <section class="panel">
        <h2>收货信息</h2>
        <p>{{ order.receiverName }} · {{ order.receiverPhone }}</p>
        <p>{{ order.receiverAddress }}</p>
        <div v-if="order.status === 'PENDING_PAYMENT'" class="order-actions">
          <label
            >模拟支付场景<el-select v-model="scenario" :disabled="Boolean(payment)"
              ><el-option label="支付成功" value="SUCCESS" /><el-option
                label="支付失败"
                value="FAILURE" /><el-option
                label="超时后成功（退款演示）"
                value="LATE_SUCCESS" /></el-select></label
          ><button
            class="primary wide"
            :disabled="busy || remaining === 0 || Boolean(payment)"
            data-testid="pay-order"
            @click="pay"
          >
            模拟付款</button
          ><button
            class="secondary wide"
            :disabled="busy"
            data-testid="cancel-order"
            @click="cancel"
          >
            取消订单
          </button>
        </div>
        <button
          v-if="order.status === 'SHIPPED'"
          class="primary wide"
          :disabled="busy"
          data-testid="confirm-order"
          @click="confirm"
        >
          确认收货
        </button>
        <p class="muted">请以最新订单状态为准。支付与取消同时发生时，系统会保留最终处理记录。</p>
      </section>
    </div>
  </section>
</template>
