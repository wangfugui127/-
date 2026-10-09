import axios from 'axios'
import { ElMessage } from 'element-plus'
export interface Row {
  [key: string]: unknown
  id: string
}
export interface Page {
  items: Row[]
  total: number
  page: number
  size: number
}
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public traceId: string,
  ) {
    super(message)
  }
}
export const http = axios.create({ baseURL: '/api/v1', timeout: 8000 })
http.interceptors.request.use((c) => {
  const token = sessionStorage.getItem('minimall.token')
  if (token) c.headers.Authorization = `Bearer ${token}`
  return c
})
export async function api<T = Row>(
  method: string,
  url: string,
  data?: unknown,
  key?: string,
): Promise<T> {
  try {
    const res = await http.request({
      method,
      url,
      data,
      headers: key ? { 'Idempotency-Key': key } : {},
    })
    return res.data.data as T
  } catch (e) {
    if (axios.isAxiosError(e)) {
      const body = e.response?.data
      const message = body?.message ?? '连接中断，操作结果暂未确认。请保留当前请求重试。'
      if (e.response?.status === 401) {
        sessionStorage.removeItem('minimall.token')
        sessionStorage.removeItem('minimall.user')
        window.dispatchEvent(new Event('minimall:unauthorized'))
      }
      ElMessage.error(message)
      throw new ApiError(
        e.response?.status ?? 0,
        body?.code ?? 'NETWORK_ERROR',
        message,
        body?.traceId ?? '',
      )
    }
    throw e
  }
}
export const freshKey = () => crypto.randomUUID().replaceAll('-', '')
export const money = (fen: unknown) => {
  const value = Number(fen)
  if (!Number.isSafeInteger(value) || value < 0) throw new Error('invalid amount')
  return `${Math.floor(value / 100)}.${String(value % 100).padStart(2, '0')}`
}
export const time = (value: unknown) =>
  value ? new Date(String(value)).toLocaleString('zh-CN', { hour12: false }) : '—'
export const orderLabels: Record<string, string> = {
  PENDING_PAYMENT: '待付款',
  PAID: '待发货',
  SHIPPED: '待收货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  CLOSED: '已关闭',
}
export const activityLabels: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ENDED: '已结束',
  DISABLED: '已停用',
  CLOSED: '已回收',
}
