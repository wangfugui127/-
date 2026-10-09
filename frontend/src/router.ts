import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from './stores/auth'
import Catalog from './views/Catalog.vue'
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/products' },
    { path: '/products', component: Catalog },
    { path: '/products/:id', component: () => import('./views/ProductDetail.vue') },
    { path: '/login', component: () => import('./views/Login.vue') },
    { path: '/register', component: () => import('./views/Login.vue') },
    { path: '/cart', component: () => import('./views/Cart.vue'), meta: { auth: true } },
    { path: '/checkout', component: () => import('./views/Checkout.vue'), meta: { auth: true } },
    { path: '/orders', component: () => import('./views/Orders.vue'), meta: { auth: true } },
    {
      path: '/orders/:id',
      component: () => import('./views/OrderDetail.vue'),
      meta: { auth: true },
    },
    { path: '/activities', component: () => import('./views/Activities.vue') },
    { path: '/activities/:id', component: () => import('./views/ActivityDetail.vue') },
    {
      path: '/admin/:section',
      component: () => import('./views/Admin.vue'),
      meta: { auth: true, admin: true },
    },
    { path: '/:pathMatch(.*)*', redirect: '/products' },
  ],
})
router.beforeEach(async (to) => {
  const auth = useAuth()
  if (to.meta.auth) {
    try {
      await auth.hydrate()
    } catch {
      if (sessionStorage.getItem('minimall.token')) return false
    }
    if (!auth.loggedIn) return { path: '/login', query: { redirect: to.fullPath } }
    if (to.meta.admin && !auth.isAdmin) return '/products'
  }
  return true
})
