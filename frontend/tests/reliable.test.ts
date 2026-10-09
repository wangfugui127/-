import { beforeEach, expect, test, vi } from 'vitest'
import { reliable } from '../src/reliable'
import { api, ApiError } from '../src/api'
vi.mock('../src/api', () => ({
  api: vi.fn(),
  freshKey: () => crypto.randomUUID(),
  ApiError: class extends Error {
    constructor(public status: number) {
      super('failure')
    }
  },
}))
beforeEach(() => {
  sessionStorage.clear()
  vi.mocked(api).mockReset()
})
test('库存调整网络失败后复用原始数量和幂等键，成功后允许新调整', async () => {
  vi.mocked(api).mockRejectedValueOnce(new ApiError(0, 'NETWORK', '', ''))
  await expect(reliable('POST', '/stock', { delta: 2 }, 'stock.1')).rejects.toThrow()
  const saved = sessionStorage.getItem('minimall.pending.stock.1')
  expect(saved).toBeTruthy()
  vi.mocked(api).mockResolvedValueOnce({ id: '1' })
  await reliable('POST', '/stock', { delta: 9 }, 'stock.1')
  expect(vi.mocked(api).mock.calls[1]?.slice(2)).toEqual(vi.mocked(api).mock.calls[0]?.slice(2))
  expect(sessionStorage.getItem('minimall.pending.stock.1')).toBeNull()
})
test('业务校验失败允许修正输入后重新提交', async () => {
  vi.mocked(api).mockRejectedValueOnce(new ApiError(400, 'VALIDATION', '', ''))
  await expect(reliable('POST', '/stock', { delta: 2 }, 'stock.1')).rejects.toThrow()
  expect(sessionStorage.getItem('minimall.pending.stock.1')).toBeNull()
})
