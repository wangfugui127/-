<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, money, time, orderLabels, activityLabels, type Row, type Page } from '../api'
import { ElMessage } from 'element-plus'
import { reliable } from '../reliable'
import { useAuth } from '../stores/auth'
const auth = useAuth()
const route = useRoute(),
  section = computed(() => String(route.params.section)),
  rows = ref<Row[]>([]),
  products = ref<Row[]>([]),
  total = ref(0),
  page = ref(1),
  keyword = ref(''),
  status = ref(''),
  loading = ref(false),
  error = ref(''),
  saving = ref(false),
  productDialog = ref(false),
  stockDialog = ref(false),
  stockPending = ref(false),
  activityDialog = ref(false),
  orderDialog = ref(false),
  detail = ref<Row | null>(null),
  stockProduct = ref<Row | null>(null)
const productForm = ref({
  id: '',
  name: '',
  categoryCode: 'LIFE',
  specification: '',
  description: '',
  imageUrl: '/images/cup.svg',
  priceFen: 3490,
  initialStock: 10,
  version: 0,
})
const stockForm = ref({ delta: 1, reason: '' }),
  activityForm = ref({
    id: '',
    productId: '',
    title: '',
    activityPriceFen: 1990,
    plannedStock: 10,
    startAt: '',
    endAt: '',
    version: 0,
  }),
  shipment = ref({ logisticsCompany: '顺丰速运', trackingNo: '' })
const titles: Record<string, string> = {
  products: '商品管理',
  orders: '订单管理',
  activities: '活动管理',
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    const path = section.value === 'activities' ? 'seckill-activities' : section.value
    const data = await api<Page>(
      'GET',
      `/admin/${path}?page=${page.value}&size=10&status=${status.value}&keyword=${encodeURIComponent(keyword.value)}&orderNo=${encodeURIComponent(keyword.value)}`,
    )
    rows.value = data.items
    total.value = data.total
    if (section.value === 'activities')
      products.value = (await api<Page>('GET', '/admin/products?size=50')).items
  } catch {
    error.value = '管理数据加载失败，请重试。'
  } finally {
    loading.value = false
  }
}
function editProduct(p?: Row) {
  productForm.value = p
    ? {
        id: p.id,
        name: String(p.name),
        categoryCode: String(p.categoryCode),
        specification: String(p.specification),
        description: String(p.description),
        imageUrl: String(p.imageUrl),
        priceFen: Number(p.priceFen),
        initialStock: 0,
        version: Number(p.version),
      }
    : {
        id: '',
        name: '',
        categoryCode: 'LIFE',
        specification: '',
        description: '',
        imageUrl: '/images/cup.svg',
        priceFen: 3490,
        initialStock: 10,
        version: 0,
      }
  productDialog.value = true
}
async function saveProduct() {
  saving.value = true
  try {
    const p = productForm.value
    await api(p.id ? 'PUT' : 'POST', p.id ? `/admin/products/${p.id}` : '/admin/products', p)
    productDialog.value = false
    ElMessage.success('商品已保存')
    await load()
  } finally {
    saving.value = false
  }
}
async function statusProduct(p: Row) {
  try {
    await api('POST', `/admin/products/${p.id}/status`, {
      action: p.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE',
      version: p.version,
    })
    await load()
  } catch {
    await load()
  }
}
function openStock(p: Row) {
  stockProduct.value = p
  const pending = sessionStorage.getItem(`minimall.pending.stock.${auth.user?.id}.${p.id}`)
  stockPending.value = Boolean(pending)
  stockForm.value = pending ? JSON.parse(pending).body : { delta: 1, reason: '' }
  if (pending) ElMessage.warning('存在结果未确认的库存调整。本次重试将使用原来的数量和原因。')
  stockDialog.value = true
}
async function saveStock() {
  saving.value = true
  stockPending.value = true
  try {
    await reliable(
      'POST',
      `/admin/products/${stockProduct.value?.id}/stock-adjustments`,
      stockForm.value,
      `stock.${auth.user?.id}.${stockProduct.value?.id}`,
    )
    stockDialog.value = false
    ElMessage.success('库存已调整')
    await load()
  } finally {
    saving.value = false
    stockPending.value = Boolean(
      sessionStorage.getItem(`minimall.pending.stock.${auth.user?.id}.${stockProduct.value?.id}`),
    )
  }
}
function editActivity(a?: Row) {
  const now = new Date(),
    later = new Date(now.getTime() + 86400000)
  activityForm.value = a
    ? {
        id: a.id,
        productId: String(a.productId),
        title: String(a.title),
        activityPriceFen: Number(a.activityPriceFen),
        plannedStock: Number(a.plannedStock),
        startAt: String(a.startAt),
        endAt: String(a.endAt),
        version: Number(a.version),
      }
    : {
        id: '',
        productId: products.value[0]?.id ?? '',
        title: '',
        activityPriceFen: 1990,
        plannedStock: 10,
        startAt: now.toISOString(),
        endAt: later.toISOString(),
        version: 0,
      }
  activityDialog.value = true
}
async function saveActivity() {
  saving.value = true
  try {
    const a = activityForm.value
    const body = {
      ...a,
      startAt: new Date(a.startAt).toISOString(),
      endAt: new Date(a.endAt).toISOString(),
    }
    await api(
      a.id ? 'PUT' : 'POST',
      a.id ? `/admin/seckill-activities/${a.id}` : '/admin/seckill-activities',
      body,
    )
    activityDialog.value = false
    ElMessage.success('活动草稿已保存')
    await load()
  } finally {
    saving.value = false
  }
}
async function actionActivity(a: Row, action: string) {
  saving.value = true
  try {
    await reliable(
      'POST',
      `/admin/seckill-activities/${a.id}/status`,
      { action, version: a.version },
      `activity.${auth.user?.id}.${a.id}.${action}`,
    )
    ElMessage.success(
      action === 'PUBLISH'
        ? '活动已发布并划拨库存'
        : action === 'CLOSE'
          ? '剩余活动库存已回收'
          : '活动已停用',
    )
    await load()
  } catch {
    await load()
  } finally {
    saving.value = false
  }
}
async function openOrder(o: Row) {
  detail.value = await api('GET', `/admin/orders/${o.id}`)
  shipment.value = { logisticsCompany: '顺丰速运', trackingNo: '' }
  orderDialog.value = true
}
async function ship() {
  saving.value = true
  try {
    detail.value = await api('POST', `/admin/orders/${detail.value?.id}/shipments`, shipment.value)
    ElMessage.success('已发货')
    await load()
  } finally {
    saving.value = false
  }
}
watch(
  section,
  () => {
    page.value = 1
    status.value = ''
    keyword.value = ''
    void load()
  },
  { immediate: true },
)
function search() {
  page.value = 1
  void load()
}
</script>
<template>
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <h2>商店管理</h2>
      <RouterLink to="/admin/products">商品与库存</RouterLink
      ><RouterLink to="/admin/orders">订单与发货</RouterLink
      ><RouterLink to="/admin/activities">限量活动</RouterLink>
      <div class="admin-hint">
        普通库存与活动库存分开管理。发布活动会划拨库存，关闭活动会回收剩余量。
      </div>
    </aside>
    <section class="admin-main">
      <div class="page-title admin-title">
        <div>
          <h1>{{ titles[section] ?? '管理后台' }}</h1>
          <p>维护好每一件商品，也照顾好每一笔订单。</p>
        </div>
        <button v-if="section === 'products'" class="primary" @click="editProduct()">
          新建商品</button
        ><button v-if="section === 'activities'" class="primary" @click="editActivity()">
          新建活动
        </button>
      </div>
      <div class="admin-toolbar">
        <input
          v-model="keyword"
          :placeholder="section === 'orders' ? '按完整订单号查询' : '搜索商品名称'"
          aria-label="管理搜索"
        /><el-select
          v-if="section !== 'activities'"
          v-model="status"
          placeholder="全部状态"
          clearable
          style="width: 160px"
        >
          <template v-if="section === 'products'">
            <el-option label="草稿" value="DRAFT" /><el-option
              label="上架"
              value="ON_SALE"
            /><el-option label="下架" value="OFF_SALE" /> </template
          ><template v-else>
            <el-option v-for="(label, s) in orderLabels" :key="s" :label="label" :value="s" />
          </template> </el-select
        ><button class="secondary" @click="search">查询</button
        ><span class="muted">共 {{ total }} 条</span>
      </div>
      <p v-if="error" class="state">{{ error }} <button @click="load">重新加载</button></p>
      <div v-else class="admin-table panel">
        <el-table v-if="section === 'products'" v-loading="loading" :data="rows">
          <el-table-column label="商品" min-width="230">
            <template #default="{ row }">
              <div class="table-product">
                <img :src="row.imageUrl" :alt="row.name" />
                <div>
                  <strong>{{ row.name }}</strong
                  ><small>{{ row.specification }}</small>
                </div>
              </div>
            </template> </el-table-column
          ><el-table-column label="单价" width="100">
            <template #default="{ row }">¥ {{ money(row.priceFen) }}</template> </el-table-column
          ><el-table-column prop="stockAvailable" label="普通库存" width="100" /><el-table-column
            label="状态"
            width="90"
          >
            <template #default="{ row }">
              <el-tag :type="row.status === 'ON_SALE' ? 'success' : 'info'">
                {{ row.status === 'ON_SALE' ? '上架' : row.status === 'DRAFT' ? '草稿' : '下架' }}
              </el-tag>
            </template> </el-table-column
          ><el-table-column label="操作" min-width="190">
            <template #default="{ row }">
              <el-button link type="primary" @click="editProduct(row)">编辑</el-button
              ><el-button link type="primary" @click="openStock(row)">调库存</el-button
              ><el-button link @click="statusProduct(row)">
                {{ row.status === 'ON_SALE' ? '下架' : '上架' }}
              </el-button>
            </template>
          </el-table-column> </el-table
        ><el-table v-else-if="section === 'orders'" v-loading="loading" :data="rows">
          <el-table-column label="订单" min-width="220">
            <template #default="{ row }">
              <strong>{{ String(row.orderNo).slice(0, 16) }}</strong
              ><small>{{ time(row.createdAt) }}</small>
            </template> </el-table-column
          ><el-table-column prop="userId" label="用户" width="80" /><el-table-column
            label="金额"
            width="100"
          >
            <template #default="{ row }">¥ {{ money(row.payableFen) }}</template> </el-table-column
          ><el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag>{{ orderLabels[row.status] }}</el-tag>
            </template> </el-table-column
          ><el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button link type="primary" @click="openOrder(row)">
                {{ row.status === 'PAID' ? '查看并发货' : '查看详情' }}
              </el-button>
            </template>
          </el-table-column> </el-table
        ><el-table v-else v-loading="loading" :data="rows">
          <el-table-column label="活动" min-width="200">
            <template #default="{ row }">
              <strong>{{ row.title }}</strong
              ><small>{{ row.productName }}</small>
            </template> </el-table-column
          ><el-table-column label="活动价" width="100">
            <template #default="{ row }">
              ¥ {{ money(row.activityPriceFen) }}
            </template> </el-table-column
          ><el-table-column label="活动库存" width="110">
            <template #default="{ row }">
              {{ row.stockAvailable }} / {{ row.stockTotal }}
            </template> </el-table-column
          ><el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag>{{ activityLabels[row.status] }}</el-tag>
            </template> </el-table-column
          ><el-table-column label="操作" min-width="180">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'DRAFT'"
                link
                type="primary"
                @click="editActivity(row)"
              >
                编辑 </el-button
              ><el-button
                v-if="row.status === 'DRAFT'"
                link
                type="primary"
                :disabled="saving"
                @click="actionActivity(row, 'PUBLISH')"
              >
                发布并划拨 </el-button
              ><el-button
                v-if="row.status === 'PUBLISHED'"
                link
                :disabled="saving"
                @click="actionActivity(row, 'DISABLE')"
              >
                停用 </el-button
              ><el-button
                v-if="['DISABLED', 'ENDED'].includes(row.status)"
                link
                type="primary"
                :disabled="saving"
                @click="actionActivity(row, 'CLOSE')"
              >
                关闭并回收
              </el-button>
            </template>
          </el-table-column> </el-table
        ><el-pagination
          v-model:current-page="page"
          :total="total"
          :page-size="10"
          layout="prev, pager, next"
          @current-change="load"
        />
      </div>
    </section>
  </div>
  <el-dialog
    v-model="productDialog"
    :title="productForm.id ? '编辑商品' : '新建商品'"
    width="560px"
  >
    <el-form label-position="top">
      <el-form-item label="商品名称">
        <el-input v-model="productForm.name" maxlength="100" />
      </el-form-item>
      <div class="form-grid">
        <el-form-item label="分类">
          <el-select v-model="productForm.categoryCode">
            <el-option label="生活" value="LIFE" /><el-option
              label="数码"
              value="DIGITAL"
            /><el-option label="阅读" value="BOOK" /><el-option label="其他" value="OTHER" />
          </el-select> </el-form-item
        ><el-form-item label="价格（分）">
          <el-input-number v-model="productForm.priceFen" :min="1" :max="100000000" />
        </el-form-item>
      </div>
      <el-form-item label="规格">
        <el-input v-model="productForm.specification" maxlength="128" /> </el-form-item
      ><el-form-item label="描述">
        <el-input
          v-model="productForm.description"
          type="textarea"
          maxlength="5000"
        /> </el-form-item
      ><el-form-item label="图片URL或站内路径">
        <el-input v-model="productForm.imageUrl" /> </el-form-item
      ><el-form-item v-if="!productForm.id" label="初始库存">
        <el-input-number v-model="productForm.initialStock" :min="0" :max="1000000" />
      </el-form-item> </el-form
    ><template #footer>
      <el-button @click="productDialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="saveProduct"> 保存商品 </el-button>
    </template>
  </el-dialog>
  <el-dialog v-model="stockDialog" title="调整普通库存" width="440px">
    <p>{{ stockProduct?.name }} · 当前 {{ stockProduct?.stockAvailable }} 件</p>
    <el-form label-position="top">
      <el-form-item label="变化数量（正数入库，负数扣减）">
        <el-input-number
          v-model="stockForm.delta"
          :min="-1000000"
          :max="1000000"
          :disabled="saving || stockPending"
        /> </el-form-item
      ><el-form-item label="调整原因">
        <el-input v-model="stockForm.reason" maxlength="128" :disabled="saving || stockPending" />
      </el-form-item> </el-form
    ><template #footer>
      <el-button @click="stockDialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="saveStock">确认调整</el-button>
    </template>
  </el-dialog>
  <el-dialog v-model="activityDialog" title="活动草稿" width="580px">
    <el-form label-position="top">
      <el-form-item label="绑定商品">
        <el-select v-model="activityForm.productId" style="width: 100%">
          <el-option v-for="p in products" :key="p.id" :label="String(p.name)" :value="p.id" />
        </el-select> </el-form-item
      ><el-form-item label="活动标题">
        <el-input v-model="activityForm.title" maxlength="100" />
      </el-form-item>
      <div class="form-grid">
        <el-form-item label="活动价格（分）">
          <el-input-number v-model="activityForm.activityPriceFen" :min="1" /> </el-form-item
        ><el-form-item label="计划划拨库存">
          <el-input-number v-model="activityForm.plannedStock" :min="1" />
        </el-form-item>
      </div>
      <el-form-item label="开始时间">
        <el-date-picker
          v-model="activityForm.startAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ssZ"
          style="width: 100%"
        /> </el-form-item
      ><el-form-item label="结束时间">
        <el-date-picker
          v-model="activityForm.endAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ssZ"
          style="width: 100%"
        />
      </el-form-item>
      <p class="rule-note">
        保存草稿不占库存。发布后划拨商品库存，价格、额度与时间冻结。
      </p> </el-form
    ><template #footer>
      <el-button @click="activityDialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="saveActivity"> 保存草稿 </el-button>
    </template>
  </el-dialog>
  <el-dialog v-model="orderDialog" title="订单详情与发货" width="650px">
    <template v-if="detail">
      <el-tag>{{ orderLabels[String(detail.status)] }}</el-tag>
      <p class="break">订单号：{{ detail.orderNo }}</p>
      <p>
        {{ detail.receiverName }} · {{ detail.receiverPhone }}<br />{{ detail.receiverAddress }}
      </p>
      <div v-for="p in detail.items as Row[]" :key="p.id" class="summary-line">
        <span>{{ p.productName }} × {{ p.quantity }}</span
        ><strong>¥ {{ money(p.subtotalFen) }}</strong>
      </div>
      <el-form v-if="detail.status === 'PAID'" label-position="top">
        <el-form-item label="物流公司">
          <el-input v-model="shipment.logisticsCompany" /> </el-form-item
        ><el-form-item label="运单号">
          <el-input v-model="shipment.trackingNo" data-testid="tracking-no" /> </el-form-item
        ><el-button type="primary" :loading="saving" @click="ship">确认发货</el-button>
      </el-form>
      <p v-else-if="detail.trackingNo">{{ detail.logisticsCompany }} / {{ detail.trackingNo }}</p>
      <h3>状态记录</h3>
      <p v-for="(log, i) in detail.logs as Row[]" :key="i" class="muted">
        {{ time(log.createdAt) }} · {{ log.action }}
      </p>
    </template>
  </el-dialog>
</template>
