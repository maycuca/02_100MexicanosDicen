// AC-52: la TV recuerda en ese navegador si se desactivó el sonido (localStorage con try/catch).
// Spec 010 (AC-133): el sonido está activado por defecto; solo `'off'` lo apaga.
const KEY = 'cm:sound'

export function getSoundPreference(): boolean {
  try {
    return localStorage.getItem(KEY) !== 'off'
  } catch {
    return true
  }
}

export function setSoundPreference(on: boolean): void {
  try {
    localStorage.setItem(KEY, on ? 'on' : 'off')
  } catch {
    /* modo privado: la preferencia no se recordará */
  }
}
