import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import Catalog from '../src/views/Catalog.vue'
import Checkout from '../src/views/Checkout.vue'
import { money, api, ApiError } from '../src/api'
const { replace } = vi.hoisted(() => ({ replace: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ replace, push: vi.fn() }) }))
vi.mock('../src/stores/auth', () => ({
  useAuth: () => ({ user: { id: 'buyer' }, loggedIn: true }),
}))
vi.mock('../src/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../src/api')>()
  return { ...actual, api: vi.fn() }
})
const product = {
  id: '1',
  name: '测试随行杯',
  priceFen: 1990,
  imageUrl: '/images/cup.svg',
  quantity: 1,
  stockAvailable: 10,
  specification: '450ml',
}
beforeEach(() => {
  vi.mocked(api).mockReset()
  replace.mockReset()
  sessionStorage.clear()
})
describe('金额展示', () => {
  it('正确显示一分和不足一元金额', () => {
    expect(money(1)).toBe('0.01')
    expect(money(99)).toBe('0.99')
    expect(money(1990)).toBe('19.90')
  })
  it('拒绝负数和非安全整数', () => {
    expect(() => money(-1)).toThrow()
    expect(() => money(0.5)).toThrow()
    expect(() => money(Number.MAX_SAFE_INTEGER + 1)).toThrow()
  })
})
describe('商品页面', () => {
  it('展示服务端商品和价格', async () => {
    vi.mocked(api).mockResolvedValue({ items: [product], total: 1 })
    const wrapper = mount(Catalog, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, ElPagination: true } },
    })
    await flushPromises()
    expect(wrapper.text()).toContain('测试随行杯')
    expect(wrapper.text()).toContain('19.90')
    wrapper.unmount()
  })
  it('失败后提供重试且重试可恢复', async () => {
    vi.mocked(api)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ items: [product], total: 1 })
    const wrapper = mount(Catalog, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, ElPagination: true } },
    })
    await flushPromises()
    expect(wrapper.text()).toContain('暂时无法加载')
    await wrapper.get('.state button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('测试随行杯')
    wrapper.unmount()
  })
})
describe('下单重试', () => {
  it('网络失败后沿用同一请求键和原始正文，确认后跳转', async () => {
    sessionStorage.setItem(
      'minimall.checkout',
      JSON.stringify({
        source: 'DIRECT',
        items: [{ productId: '1', quantity: 1 }],
        cartItemIds: [],
      }),
    )
    vi.mocked(api)
      .mockResolvedValueOnce(product)
      .mockRejectedValueOnce(new ApiError(0, 'NETWORK_ERROR', '结果未确认', 'trace'))
      .mockResolvedValueOnce({ id: 'order-1' })
    const wrapper = mount(Checkout)
    await flushPromises()
    await wrapper.get('[data-testid="receiver-name"]').setValue('测试用户')
    await wrapper.get('[data-testid="receiver-phone"]').setValue('13800000001')
    await wrapper.get('[data-testid="receiver-address"]').setValue('测试地址一号楼')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('重试确认结果')
    expect(wrapper.get('[data-testid="receiver-name"]').attributes()).toHaveProperty('disabled')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const calls = vi.mocked(api).mock.calls.filter((c) => c[1] === '/orders')
    expect(calls).toHaveLength(2)
    expect(calls[0]?.[3]).toBe(calls[1]?.[3])
    expect(calls[0]?.[2]).toEqual(calls[1]?.[2])
    expect(replace).toHaveBeenCalledWith('/orders/order-1')
    expect(sessionStorage.getItem('minimall.attempt.buyer')).toBeNull()
    wrapper.unmount()
  })
})
