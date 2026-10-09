<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api, money, type Row, type Page } from '../api'
const items = ref<Row[]>([]),
  total = ref(0),
  page = ref(1),
  category = ref(''),
  keyword = ref(''),
  loading = ref(false),
  error = ref('')
async function load() {
  loading.value = true
  error.value = ''
  try {
    const p = await api<Page>(
      'GET',
      `/products?page=${page.value}&size=12&categoryCode=${category.value}&keyword=${encodeURIComponent(keyword.value)}`,
    )
    items.value = p.items
    total.value = p.total
  } catch {
    error.value = '商品暂时无法加载，请重试。'
  } finally {
    loading.value = false
  }
}
function choose(c: string) {
  category.value = c
  page.value = 1
  void load()
}
onMounted(load)
function search() {
  page.value = 1
  void load()
}
</script>
<template>
  <section class="hero">
    <div>
      <p class="hero-note">把喜欢带进日常</p>
      <h1>好用的东西，<br />值得认真挑选。</h1>
      <p>从书桌到随行，为日常留一点新鲜感。<br />常规好物随时选，限量上新别错过。</p>
      <RouterLink to="/activities" class="hero-button">看看限量上新</RouterLink>
    </div>
    <div class="hero-art">
      <img src="/images/cup.svg" alt="随行保温杯" />
      <div class="hero-caption">随行保温杯<span>轻装出门，自在随行</span></div>
    </div>
  </section>
  <section class="catalog">
    <div class="section-title">
      <div>
        <h2>日常好物</h2>
        <p>实用、简单，刚刚好。</p>
      </div>
      <form class="search" @submit.prevent="search">
        <input v-model="keyword" aria-label="搜索商品" placeholder="寻找一件好物" /><button>
          搜索
        </button>
      </form>
    </div>
    <div class="category-tabs">
      <button
        v-for="c in [
          ['', '全部'],
          ['LIFE', '生活'],
          ['DIGITAL', '数码'],
          ['BOOK', '阅读'],
          ['OTHER', '其他'],
        ]"
        :key="c[0]"
        :class="{ active: category === c[0] }"
        @click="choose(c[0]!)"
      >
        {{ c[1] }}
      </button>
    </div>
    <p v-if="loading" class="state">正在挑选好物…</p>
    <div v-else-if="error" class="state">{{ error }} <button @click="load">重新加载</button></div>
    <div v-else-if="!items.length" class="state">还没有符合条件的商品，试试其他分类。</div>
    <div v-else class="product-grid">
      <RouterLink
        v-for="p in items"
        :key="p.id"
        :to="`/products/${p.id}`"
        class="product-card"
        data-testid="product-card"
      >
        <div class="product-art">
          <img :src="String(p.imageUrl || '/images/notebook.svg')" :alt="String(p.name)" />
        </div>
        <div class="product-info">
          <span class="product-spec">{{ p.specification }}</span>
          <h3>{{ p.name }}</h3>
          <div class="price-row">
            <span class="price">¥ {{ money(p.priceFen) }}</span
            ><span class="stock-text">{{
              Number(p.stockAvailable) > 0 ? '有货' : '暂时售罄'
            }}</span>
          </div>
        </div>
      </RouterLink>
    </div>
    <el-pagination
      v-if="total > 12"
      v-model:current-page="page"
      :total="total"
      :page-size="12"
      layout="prev, pager, next"
      @current-change="load"
    />
  </section>
</template>
