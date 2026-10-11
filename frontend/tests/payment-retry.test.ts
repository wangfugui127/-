import { beforeEach, expect, test, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import OrderDetail from '../src/views/OrderDetail.vue'
import { api } from '../src/api'

vi.mock('vue-router', () => ({ useRoute: () => ({ params: { id: '42' } }) }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn() }, ElMessageBox: {} }))
vi.mock('../src/api', async (original) => ({
  ...(await original<typeof import('../src/api')>()),
  api: vi.fn(),
}))

const order = (status = 'FAILED', requestKey?: string) => ({
  id: '42',
  status: 'PENDING_PAYMENT',
  payableFen: 1990,
  items: [],
  serverNow: new Date().toISOString(),
  expiresAt: new Date(Date.now() + 60000).toISOString(),
  payment: { id: '1', status, requestKey },
})
const render = () =>
  mount(OrderDetail, { global: { stubs: { RouterLink: true, ElSelect: true, ElOption: true } } })
beforeEach(() => {
  vi.mocked(api).mockReset()
  sessionStorage.clear()
})

test('失败后在原订单发起新支付，处理中禁止重复点击', async () => {
  vi.mocked(api)
    .mockResolvedValueOnce(order())
    .mockResolvedValueOnce({ status: 'CREATED' })
    .mockResolvedValueOnce(order('CREATED'))
  const wrapper = render()
  try {
    await flushPromises()
    expect(wrapper.get('[data-testid="pay-order"]').text()).toBe('重新支付')
    expect(wrapper.get('[data-testid="pay-order"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="pay-order"]').trigger('click')
    await flushPromises()
    const call = vi.mocked(api).mock.calls.find((c) => c[0] === 'POST')!
    expect(call[1]).toBe('/orders/42/mock-payments')
    expect(call[2]).toEqual({ scenario: 'SUCCESS' })
    expect(call[3]).toBeTruthy()
    expect(wrapper.get('[data-testid="pay-order"]').attributes('disabled')).toBeDefined()
  } finally {
    wrapper.unmount()
  }
})

test('响应丢失后根据服务端请求键确认旧尝试，再生成新的支付键', async () => {
  vi.mocked(api).mockResolvedValueOnce(order()).mockRejectedValueOnce(new Error('response lost'))
  const wrapper = render()
  try {
    await flushPromises()
    await wrapper.get('[data-testid="pay-order"]').trigger('click')
    await flushPromises()
    const slot = 'minimall.pending.payment.42'
    const key = JSON.parse(sessionStorage.getItem(slot)!).key
    expect(wrapper.text()).toContain('支付结果尚未确认')
    vi.mocked(api).mockResolvedValueOnce(order('FAILED', key))
    await wrapper.get('.state button').trigger('click')
    await flushPromises()
    expect(sessionStorage.getItem(slot)).toBeNull()
    vi.mocked(api)
      .mockResolvedValueOnce({ status: 'CREATED' })
      .mockResolvedValueOnce(order('CREATED'))
    await wrapper.get('[data-testid="pay-order"]').trigger('click')
    await flushPromises()
    const payments = vi.mocked(api).mock.calls.filter((c) => c[0] === 'POST')
    expect(payments).toHaveLength(2)
    expect(payments[1]![3]).not.toBe(payments[0]![3])
  } finally {
    wrapper.unmount()
  }
})
