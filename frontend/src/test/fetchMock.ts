import { vi } from 'vitest'

export type Route = {
  method?: 'GET' | 'POST' | 'PUT'
  path: string | RegExp
  status?: number
  body: unknown
}

export type FetchCall = { method: string; path: string; headers: Record<string, string>; body: unknown }

/** Instala un `fetch` falso que responde según la tabla de rutas (la primera que coincide). */
export function mockFetch(routes: Route[]) {
  const calls: FetchCall[] = []
  const fn = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const path = typeof input === 'string' ? input : input.toString()
    const method = (init.method ?? 'GET').toUpperCase()
    const headers = (init.headers ?? {}) as Record<string, string>
    calls.push({ method, path, headers, body: init.body ? JSON.parse(String(init.body)) : undefined })
    const route = routes.find(
      (r) => (r.method ?? 'GET') === method && (typeof r.path === 'string' ? r.path === path : r.path.test(path)),
    )
    if (!route) return new Response(JSON.stringify({ status: 404, code: 'NOT_MOCKED' }), { status: 599 })
    return new Response(JSON.stringify(route.body), { status: route.status ?? 200 })
  })
  vi.stubGlobal('fetch', fn)
  return { calls, fn }
}

export const networkInfo = { lanAddress: '192.168.1.42', backendPort: 8080, frontendPort: 5173 }
