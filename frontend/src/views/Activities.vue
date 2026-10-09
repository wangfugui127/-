<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api, money, time, activityLabels, type Row, type Page } from '../api'
const items = ref<Row[]>([]),
  error = ref('')
async function load() {
  try {
    items.value = (await api<Page>('GET', '/seckill-activities?size=50')).items
  } catch {
    error.value = '活动暂时无法加载'
  }
}
onMounted(load)
</script>
<template>
  <div class="activity-heading">
    <h1>限量上新</h1>
    <p>为喜欢留一个位置。每场活动，每人一次。</p>
  </div>
  <p v-if="error" class="state">{{ error }} <button @click="load">重试</button></p>
  <p v-else-if="!items.length" class="state">新活动还在准备中，先看看日常好物。</p>
  <div class="activity-grid">
    <RouterLink v-for="a in items" :key="a.id" :to="`/activities/${a.id}`" class="activity-card">
      <div class="activity-card-art">
        <img :src="String(a.imageUrl)" :alt="String(a.productName)" /><el-tag>
          {{ activityLabels[String(a.status)] }}
        </el-tag>
      </div>
      <div class="activity-card-info">
        <h2>{{ a.title }}</h2>
        <p>{{ a.productName }}</p>
        <span class="price">¥ {{ money(a.activityPriceFen) }}</span
        ><del>¥ {{ money(a.originalPriceFen) }}</del>
        <div class="activity-window">{{ time(a.startAt) }} 开始<br />{{ time(a.endAt) }} 结束</div>
        <span>剩余 {{ a.stockAvailable }} / {{ a.stockTotal }} 件</span>
      </div>
    </RouterLink>
  </div>
</template>
