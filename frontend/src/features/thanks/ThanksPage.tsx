import { useEffect, useRef, useState } from 'react'
import { GameLogo } from '../../shared/ui/GameLogo'

/** Cuánto se espera a que el navegador cierre la pestaña antes de mostrar el aviso. */
const CLOSE_CHECK_MS = 400

/**
 * Spec 007 (T1204, AC-113; T1208, AC-118): la sala se cerró desde "Menú principal". Presentador, TV y jugadores
 * llegan aquí con sus sesiones de esa sala ya borradas. "Cerrar ventana" intenta cerrar la pestaña; los navegadores
 * solo lo permiten en pestañas abiertas por la página, así que si sigue abierta se explica cómo cerrarla.
 */
export function ThanksPage() {
  const heading = useRef<HTMLHeadingElement>(null)
  const timer = useRef<number | undefined>(undefined)
  const [blocked, setBlocked] = useState(false)

  useEffect(() => {
    heading.current?.focus()
    return () => window.clearTimeout(timer.current)
  }, [])

  function closeWindow() {
    try {
      window.close()
    } catch {
      /* el navegador lo bloqueó */
    }
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => {
      if (!window.closed) setBlocked(true)
    }, CLOSE_CHECK_MS)
  }

  return (
    <main className="thanks">
      <GameLogo size="small" />
      <h2 className="thanks__title" ref={heading} tabIndex={-1}>
        ¡Gracias por jugar!
      </h2>
      <p className="thanks__text">La partida terminó. ¡Los esperamos en la próxima!</p>
      <button type="button" className="game-btn" onClick={closeWindow}>
        Cerrar ventana
      </button>
      <p className="thanks__hint" role="status">
        {blocked ? 'Tu navegador no permite cerrarla desde aquí: cierra esta pestaña o ventana.' : ''}
      </p>
    </main>
  )
}
