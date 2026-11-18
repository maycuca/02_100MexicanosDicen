import { api, type ClaimPresenterResponse } from '../../shared/api/client'

// El claim es de un solo uso (AC-30): se canjea una sola vez por página aunque React
// monte el efecto dos veces (StrictMode) o el componente se vuelva a montar.
const pending = new Map<string, Promise<ClaimPresenterResponse>>()

export function redeemClaim(code: string, claimToken: string): Promise<ClaimPresenterResponse> {
  const key = `${code}:${claimToken}`
  let p = pending.get(key)
  if (!p) {
    p = api.claimPresenter(code, claimToken)
    pending.set(key, p)
  }
  return p
}
