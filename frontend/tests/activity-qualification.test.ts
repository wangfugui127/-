import { beforeEach, expect, test, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ActivityDetail from '../src/views/ActivityDetail.vue'
import { api } from '../src/api'

const { push } = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '5' }, fullPath: '/activities/5' }),
  useRouter: () => ({ push }),
}))
vi.mock('../src/stores/auth', () => ({
  useAuth: () => ({ loggedIn: true }),
}))
vi.mock('../src/api', async (original) => ({
  ...(await original<typeof import('../src/api')>()),
  api: vi.fn(),
}))

const activity = (hasPurchased = false, pendingOrderId: string | null = null) => ({
  id: '5',
  productId: '8',
  title: '测试限量活动',
  productName: '测试随行杯',
  specification: '450ml',
  imageUrl: '/images/cup.svg',
  activityPriceFen: 990,
  originalPriceFen: 1990,
  status: 'PUBLISHED',
  stockTotal: 3,
  stockAvailable: 2,
  startAt: new Date(Date.now() - 60000).toISOString(),
  endAt: new Date(Date.now() + 60000).toISOString(),
  serverNow: new Date().toISOString(),
  hasPurchased,
  pendingOrderId,
})
const render = () =>
  mount(ActivityDetail, {
    global: {
      stubs: {
        RouterLink: {
          props: ['to'],
          template: '<a :href="to"><slot /></a>',
        },
      },
    },
  })
beforeEach(() => {
  vi.mocked(api).mockReset()
  push.mockReset()
})

test('已有待付款订单时直接继续支付，活动结束或无库存也不会另建订单', async () => {
  vi.mocked(api).mockResolvedValue({
    ...activity(true, '42'),
    status: 'ENDED',
    stockAvailable: 0,
  })
  const wrapper = render()
  try {
    await flushPromises()
    const link = wrapper.get('[data-testid="continue-payment"]')
    expect(link.text()).toBe('继续支付')
    expect(link.attributes('href')).toBe('/orders/42')
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.find('[data-testid="seckill-buy"]').exists()).toBe(false)
    expect(vi.mocked(api).mock.calls.some((call) => call[0] === 'POST')).toBe(false)
  } finally {
    wrapper.unmount()
  }
})

test('取消或超时恢复资格后，可以用新请求键重新抢购', async () => {
  vi.mocked(api).mockResolvedValueOnce(activity()).mockResolvedValueOnce({ id: '43' })
  const wrapper = render()
  try {
    await flushPromises()
    expect(wrapper.find('[data-testid="continue-payment"]').exists()).toBe(false)
    const button = wrapper.get('[data-testid="seckill-buy"]')
    expect(button.text()).toBe('参与抢购')
    expect(button.attributes('disabled')).toBeUndefined()
    await wrapper.get('input[maxlength="64"]').setValue('测试用户')
    await wrapper.get('input[maxlength="32"]').setValue('13800000001')
    await wrapper.get('input[maxlength="256"]').setValue('测试地址一号楼')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const call = vi.mocked(api).mock.calls.find((entry) => entry[0] === 'POST')!
    expect(call[1]).toBe('/seckill-activities/5/orders')
    expect(call[2]).toEqual({
      receiver: { name: '测试用户', phone: '13800000001', address: '测试地址一号楼' },
      expectedTotalFen: 990,
    })
    expect(call[3]).toBeTruthy()
    expect(push).toHaveBeenCalledWith('/orders/43')
  } finally {
    wrapper.unmount()
  }
})

test('已付款订单继续占用资格，不显示继续支付或允许再次抢购', async () => {
  vi.mocked(api).mockResolvedValue(activity(true))
  const wrapper = render()
  try {
    await flushPromises()
    expect(wrapper.find('[data-testid="continue-payment"]').exists()).toBe(false)
    const button = wrapper.get('[data-testid="seckill-buy"]')
    expect(button.text()).toBe('本场资格已使用')
    expect(button.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).not.toContain('个人资格不恢复')
    expect(vi.mocked(api).mock.calls.some((call) => call[0] === 'POST')).toBe(false)
  } finally {
    wrapper.unmount()
  }
})
