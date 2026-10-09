import { test, expect } from '@playwright/test'
import { readFileSync, mkdirSync } from 'node:fs'
const env = Object.fromEntries(
  readFileSync(new URL('../../deploy/.env', import.meta.url), 'utf8')
    .split(/\r?\n/)
    .filter((l) => l.includes('='))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]),
)
const adminPassword = process.env.ADMIN_PASSWORD ?? env.BOOTSTRAP_ADMIN_PASSWORD
const unique = () => `web_${Date.now()}_${Math.floor(Math.random() * 10000)}`
test('用户从浏览器下单付款，管理员发货，用户确认收货', async ({ page, request }) => {
  const username = unique(),
    password = 'WebTest!2026'
  const register = await request.post('/api/v1/auth/register', { data: { username, password } })
  expect(register.ok()).toBeTruthy()
  await page.goto('/login')
  await page.getByTestId('username').fill(username)
  await page.getByTestId('password').fill(password)
  await page.getByTestId('auth-submit').click()
  await expect(page).toHaveURL(/products$/)
  await page.getByTestId('product-card').filter({ hasText: '日常灵感手账' }).click()
  await page.getByTestId('buy-now').click()
  await page.getByTestId('receiver-name').fill('浏览器测试用户')
  await page.getByTestId('receiver-phone').fill('13800000001')
  await page.getByTestId('receiver-address').fill('测试地址二号楼')
  await page.getByTestId('create-order').click()
  await expect(page).toHaveURL(/orders\/\d+$/)
  const orderId = page.url().split('/').at(-1)!
  await expect(page.getByTestId('order-status')).toHaveText('待付款')
  await page.getByTestId('pay-order').click()
  await expect(page.getByTestId('order-status')).toHaveText('待发货', { timeout: 30000 })
  const login = await request.post('/api/v1/auth/login', {
    data: { username: 'admin', password: adminPassword },
  })
  const token = (await login.json()).data.token
  const ship = await request.post(`/api/v1/admin/orders/${orderId}/shipments`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { logisticsCompany: '浏览器测试快递', trackingNo: `WEB${Date.now()}` },
  })
  expect(ship.ok()).toBeTruthy()
  await expect(page.getByTestId('confirm-order')).toBeVisible()
  await page.getByTestId('confirm-order').click()
  await expect(page.getByTestId('order-status')).toHaveText('已完成')
})
test('管理员可以创建商品并调整库存', async ({ page }) => {
  await page.goto('/login')
  await page.getByTestId('username').fill('admin')
  await page.getByTestId('password').fill(adminPassword)
  await page.getByTestId('auth-submit').click()
  await expect(page).toHaveURL(/products$/)
  await page.goto('/admin/products')
  await page.getByRole('button', { name: '新建商品', exact: true }).click()
  const dialog = page.getByRole('dialog')
  const name = `后台测试商品${Date.now()}`
  await dialog.locator('input').first().fill(name)
  await dialog.getByRole('button', { name: '保存商品' }).click()
  await expect(dialog).not.toBeVisible()
  const row = page.locator('tr').filter({ hasText: name })
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '调库存' }).click()
  const stock = page.getByRole('dialog')
  await stock
    .locator('.el-form-item')
    .filter({ hasText: '调整原因' })
    .locator('input')
    .fill('浏览器验收补货')
  await stock.getByRole('button', { name: '确认调整' }).click()
  await expect(stock).not.toBeVisible()
  await expect(row).toContainText('11')
})
test('桌面和手机商品页面没有横向溢出', async ({ page }) => {
  mkdirSync('../docs/screenshots', { recursive: true })
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/products')
  await expect(page.getByTestId('product-card').first()).toBeVisible()
  await page.screenshot({ path: '../docs/screenshots/catalog-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({ path: '../docs/screenshots/catalog-mobile.png', fullPage: true })
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy()
})

test('购物车修改数量、结算后清理，订单可以取消', async ({ page, request }) => {
  const username = unique(),
    password = 'WebTest!2026'
  expect(
    (await request.post('/api/v1/auth/register', { data: { username, password } })).ok(),
  ).toBeTruthy()
  await page.goto('/login')
  await page.getByTestId('username').fill(username)
  await page.getByTestId('password').fill(password)
  await page.getByTestId('auth-submit').click()
  await expect(page).toHaveURL(/products$/)
  await page.getByTestId('product-card').filter({ hasText: '日常灵感手账' }).click()
  await page.getByRole('button', { name: '加入购物车', exact: true }).click()
  await expect(page.getByText('已加入购物车', { exact: true })).toBeVisible()
  await page.goto('/cart')
  await page.locator('input[type=checkbox]').first().check()
  await page.getByRole('spinbutton').fill('2')
  await page.getByRole('spinbutton').press('Tab')
  await expect(page.getByRole('spinbutton')).toHaveValue('2')
  await page.getByRole('button', { name: '确认下单', exact: true }).click()
  await page.getByTestId('receiver-name').fill('购物车验收')
  await page.getByTestId('receiver-phone').fill('13800000001')
  await page.getByTestId('receiver-address').fill('购物车验收测试地址')
  await page.getByTestId('create-order').click()
  await expect(page).toHaveURL(/orders\/\d+$/)
  await page.getByRole('button', { name: '取消订单', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '取消订单', exact: true }).click()
  await expect(page.getByTestId('order-status')).toHaveText('已取消')
  await page.goto('/cart')
  await expect(page.getByText('购物车还空着。', { exact: false })).toBeVisible()
})

test('限量活动可以抢购，取消后个人资格仍已使用', async ({ page, request }) => {
  const username = unique(),
    password = 'WebTest!2026'
  expect(
    (await request.post('/api/v1/auth/register', { data: { username, password } })).ok(),
  ).toBeTruthy()
  await page.goto('/login')
  await page.getByTestId('username').fill(username)
  await page.getByTestId('password').fill(password)
  await page.getByTestId('auth-submit').click()
  await expect(page).toHaveURL(/products$/)
  await page.goto('/activities')
  await page.locator('.activity-card').filter({ hasText: '随行杯' }).first().click()
  await expect(page).toHaveURL(/activities\/\d+$/)
  const activityUrl = page.url()
  await page.locator('form input').nth(0).fill('限量验收')
  await page.locator('form input').nth(1).fill('13800000001')
  await page.locator('form input').nth(2).fill('限量验收测试地址')
  await page.getByTestId('seckill-buy').click()
  await expect(page).toHaveURL(/orders\/\d+$/)
  await page.getByRole('button', { name: '取消订单', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '取消订单', exact: true }).click()
  await expect(page.getByTestId('order-status')).toHaveText('已取消')
  await page.goto(activityUrl)
  await expect(page.getByTestId('seckill-buy')).toHaveText('本场资格已使用')
  await expect(page.getByTestId('seckill-buy')).toBeDisabled()
})
