import { useEffect, useState } from 'react'

function reducedMotion(): boolean {
  try {
    return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  } catch {
    return false
  }
}

/** Spec 007 (AC-114): cuenta de 0 a `target` en `ms`; con movimiento reducido devuelve `target` directo. */
export function useCountUp(target: number, ms = 1600): number {
  const instant = reducedMotion() || typeof requestAnimationFrame !== 'function'
  const [value, setValue] = useState(0)

  useEffect(() => {
    if (instant) return
    let frame = 0
    const start = performance.now()
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / ms)
      setValue(Math.round(target * (1 - (1 - t) ** 3)))
      if (t < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [target, ms, instant])

  return instant ? target : value
}
