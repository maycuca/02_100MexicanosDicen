import NoSleep from 'nosleep.js'
import { useEffect } from 'react'
import { GESTURES } from './gestures'

/**
 * Spec 010 (T1501, AC-129…AC-132): mantiene encendida la pantalla mientras la página está montada.
 * `nosleep.js` usa `navigator.wakeLock` si existe (HTTPS o localhost) y, si no, un video mudo en
 * bucle, que necesita un gesto del usuario para arrancar. Si nada funciona, no pasa nada.
 */
export function useKeepScreenOn(active = true): void {
  useEffect(() => {
    if (!active) return
    let noSleep: NoSleep
    try {
      noSleep = new NoSleep()
    } catch {
      return
    }
    let disposed = false

    const enable = () => {
      if (disposed || document.visibilityState !== 'visible') return
      try {
        void Promise.resolve(noSleep.enable()).catch(() => {
          /* sin permiso o sin gesto: se reintenta con el siguiente gesto */
        })
      } catch {
        /* navegador sin soporte */
      }
    }
    const onVisibility = () => {
      if (document.visibilityState === 'visible') enable()
    }

    // AC-130: un intento inmediato (sirve con Wake Lock nativo) y otro con cada gesto.
    enable()
    for (const g of GESTURES) window.addEventListener(g, enable)
    document.addEventListener('visibilitychange', onVisibility)

    return () => {
      disposed = true
      for (const g of GESTURES) window.removeEventListener(g, enable)
      document.removeEventListener('visibilitychange', onVisibility)
      try {
        noSleep.disable()
      } catch {
        /* ya estaba liberado */
      }
    }
  }, [active])
}
