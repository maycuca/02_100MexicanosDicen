import { useSyncExternalStore } from 'react'
import { setSessionScope } from '../../shared/session/session'

/**
 * Spec 004 (T902): modo "Prueba local". La bandera vive en localStorage (la heredan las pestañas
 * abiertas con las URL copiadas, mismo origen; AC-77) y cada pestaña guarda su sesión aparte (AC-76).
 */
const KEY = 'cm:local-test'
const listeners = new Set<() => void>()

export function isLocalTestMode(): boolean {
  try {
    return localStorage.getItem(KEY) === 'on'
  } catch {
    return false
  }
}

function applyScope() {
  setSessionScope(isLocalTestMode() ? 'tab' : 'local')
}

/** Se llama una vez al cargar la pestaña (`main.tsx`): fija dónde vive la sesión (AC-77). */
export function installLocalTest(): void {
  applyScope()
}

/** Cambia el modo desde AJUSTES (AC-73); solo esta pestaña cambia de sesión. */
export function setLocalTestMode(on: boolean): void {
  try {
    if (on) localStorage.setItem(KEY, 'on')
    else localStorage.removeItem(KEY)
  } catch {
    /* modo privado: el modo no se puede recordar */
  }
  applyScope()
  listeners.forEach((l) => l())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function useLocalTestMode(): { enabled: boolean; setEnabled: (on: boolean) => void } {
  const enabled = useSyncExternalStore(subscribe, isLocalTestMode, () => false)
  return { enabled, setEnabled: setLocalTestMode }
}
