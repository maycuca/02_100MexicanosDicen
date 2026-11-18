// AC-52: la TV recuerda en ese navegador si se activó el sonido (localStorage con try/catch).
const KEY = 'cm:sound'

export function getSoundPreference(): boolean {
  try {
    return localStorage.getItem(KEY) === 'on'
  } catch {
    return false
  }
}

export function setSoundPreference(on: boolean): void {
  try {
    if (on) localStorage.setItem(KEY, 'on')
    else localStorage.removeItem(KEY)
  } catch {
    /* modo privado: la preferencia no se recordará */
  }
}
