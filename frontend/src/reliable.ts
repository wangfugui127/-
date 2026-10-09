import { api, ApiError, freshKey } from './api'

// Store the original body with its key until the server has given a definite outcome.
// sessionStorage also preserves uncertain operations across refreshes in this tab.
export async function reliable<T>(
  method: string,
  path: string,
  body: unknown,
  scope: string,
): Promise<T> {
  const slot = `minimall.pending.${scope}`
  const previous = sessionStorage.getItem(slot)
  const attempt = previous ? JSON.parse(previous) : { key: freshKey(), body }
  sessionStorage.setItem(slot, JSON.stringify(attempt))
  try {
    const result = await api<T>(method, path, attempt.body, attempt.key)
    sessionStorage.removeItem(slot)
    return result
  } catch (error) {
    if (
      error instanceof ApiError &&
      error.status >= 400 &&
      error.status < 500 &&
      error.status !== 429
    ) {
      sessionStorage.removeItem(slot)
    }
    throw error
  }
}
