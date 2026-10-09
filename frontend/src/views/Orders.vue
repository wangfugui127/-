<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api, money, time, orderLabels, type Row, type Page } from '../api'
const items = ref<Row[]>([]),
  status = ref(''),
  page = ref(1),
  total = ref(0),
  error = ref(''),
  loading = ref(false)
async function load() {
  loading.value = true
  error.value = ''
  try {
    const p = await api<Page>('GET', `/orders?page=${page.value}&size=10&status=${status.value}`)
    items.value = p.items
    total.value = p.total
  } catch {
    error.value = '订单暂时无法加载，请重试。'
  } finally {
    loading.value = false
  }
}
function filter(s: string) {
  status.value = s
  page.value = 1
  void load()
}
onMounted(load)
</script>
<template>
  <div class="page-title">
    <h1>我的订单</h1>
    <p>从付款到收货，每一步都看得见。</p>
  </div>
  <div class="category-tabs">
    <button :class="{ active: status === '' }" @click="filter('')">全部</button
    ><button
      v-for="(label, s) in orderLabels"
      :key="s"
      :class="{ active: status === s }"
      @click="filter(s)"
    >
      {{ label }}
    </button>
  </div>
  <p v-if="loading" class="state">正在加载订单…</p>
  <p v-else-if="error" class="state">{{ error }} <button @click="load">重试</button></p>
  <div v-else-if="!items.length" class="state">
    暂无订单。<RouterLink to="/products">去看看好物</RouterLink>
  </div>
  <article v-for="o in items" v-else :key="o.id" class="order-list-item panel">
    <div class="order-list-head">
      <span>{{ time(o.createdAt) }}</span
      ><span class="order-number">订单 {{ String(o.orderNo).slice(0, 16) }}</span
      ><el-tag :type="o.status === 'CANCELLED' || o.status === 'CLOSED' ? 'info' : 'primary'">
        {{ orderLabels[String(o.status)] }}
      </el-tag>
    </div>
    <div class="order-list-content">
      <div class="order-goods">
        <div v-for="(item, i) in o.items as Row[]" :key="i" class="summary-line">
          <img :src="String(item.imageUrl)" :alt="String(item.productName)" />
          <div>
            <strong>{{ item.productName }}</strong
            ><small>数量 {{ item.quantity }}</small>
          </div>
        </div>
      </div>
      <div class="order-list-amount">
        <strong>¥ {{ money(o.payableFen) }}</strong
        ><RouterLink :to="`/orders/${o.id}`" class="secondary" data-testid="order-link">
          查看订单
        </RouterLink>
      </div>
    </div>
  </article>
  <el-pagination
    v-if="total > 10"
    v-model:current-page="page"
    :total="total"
    :page-size="10"
    layout="prev, pager, next"
    @current-change="load"
  />
</template>
