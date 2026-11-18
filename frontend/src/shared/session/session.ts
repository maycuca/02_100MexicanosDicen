// AC-09: los tokens viven en localStorage para recuperar la sesión al recargar.
// Spec 004 (T901, AC-76): en "Prueba local" cada pestaña usa su propia sesión (sessionStorage).
type Role = 'player' | 'presenter' | 'claim'
const key = (role: Role, code: string) => `cm:${role}:${code}`

export type SessionScope = 'local' | 'tab'
let scope: SessionScope = 'local'

/** Dónde se guardan los tokens: `local` = todo el navegador (por defecto), `tab` = solo esta pestaña. */
export function setSessionScope(next: SessionScope): void {
  scope = next
}

export function getSessionScope(): SessionScope {
  return scope
}

const store = (): Storage => (scope === 'tab' ? sessionStorage : localStorage)

function safeGet(k: string): string | null {
  try {
    return store().getItem(k)
  } catch {
    return null
  }
}

function safeSet(k: string, v: string): void {
  try {
    store().setItem(k, v)
  } catch {
    /* modo privado: la sesión no se recordará */
  }
}

function safeRemove(k: string): void {
  try {
    store().removeItem(k)
  } catch {
    /* sin almacenamiento disponible */
  }
}

export const session = {
  getPlayerToken: (code: string) => safeGet(key('player', code)),
  setPlayerToken: (code: string, token: string) => safeSet(key('player', code), token),
  clearPlayerToken: (code: string) => safeRemove(key('player', code)),
  getPresenterToken: (code: string) => safeGet(key('presenter', code)),
  setPresenterToken: (code: string, token: string) => safeSet(key('presenter', code), token),
  clearPresenterToken: (code: string) => safeRemove(key('presenter', code)),
  /** Token de un solo uso del QR de presentador (AC-30); solo lo guarda la pantalla. */
  getPresenterClaim: (code: string) => safeGet(key('claim', code)),
  setPresenterClaim: (code: string, token: string) => safeSet(key('claim', code), token),
  /** Spec 007 (AC-113): borra todas las sesiones de la sala en esta pestaña/navegador. */
  clearRoom: (code: string) => {
    for (const role of ['player', 'presenter', 'claim'] as const) safeRemove(key(role, code))
  },
}
