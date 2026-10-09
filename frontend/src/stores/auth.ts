import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api, type Row } from '../api'
export const useAuth = defineStore('auth', () => {
  const user = ref<Row | null>(JSON.parse(sessionStorage.getItem('minimall.user') ?? 'null'))
  window.addEventListener('minimall:unauthorized', () => {
    user.value = null
  })
  const loggedIn = computed(
    () => user.value !== null && Boolean(sessionStorage.getItem('minimall.token')),
  )
  const isAdmin = computed(() => loggedIn.value && user.value?.role === 'ADMIN')
  async function login(username: string, password: string) {
    const res = await api<{ token: string; user: Row }>('POST', '/auth/login', {
      username,
      password,
    })
    sessionStorage.setItem('minimall.token', res.token)
    sessionStorage.setItem('minimall.user', JSON.stringify(res.user))
    user.value = res.user
  }
  async function hydrate() {
    if (!sessionStorage.getItem('minimall.token')) {
      user.value = null
      return
    }
    try {
      user.value = await api('GET', '/auth/me')
      sessionStorage.setItem('minimall.user', JSON.stringify(user.value))
    } catch (e) {
      if (!sessionStorage.getItem('minimall.token')) user.value = null
      throw e
    }
  }
  async function logout() {
    try {
      await api('POST', '/auth/logout')
    } finally {
      user.value = null
      sessionStorage.removeItem('minimall.token')
      sessionStorage.removeItem('minimall.user')
    }
  }
  return { user, loggedIn, isAdmin, login, hydrate, logout }
})
