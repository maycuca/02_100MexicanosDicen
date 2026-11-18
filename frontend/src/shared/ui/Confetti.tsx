import { useMemo, type CSSProperties } from 'react'

const COLORS = ['var(--cm-gold)', 'var(--cm-accent)', 'var(--cm-team-a)', 'var(--cm-team-b)', '#ff6fb5', '#8fd3ff']

/** Pseudoaleatorio determinista: el confeti no cambia entre renders. */
function rand(seed: number): number {
  const x = Math.sin(seed * 9301 + 49297) * 233280
  return x - Math.floor(x)
}

/**
 * Spec 007 (AC-114, AC-116): confeti original hecho con CSS. Decorativo (aria-hidden);
 * con `prefers-reduced-motion` no se muestra (lo oculta el CSS).
 */
export function Confetti({ pieces = 80 }: { pieces?: number }) {
  const items = useMemo(
    () =>
      Array.from({ length: pieces }, (_, i) => ({
        left: `${(rand(i + 1) * 100).toFixed(2)}%`,
        delay: `${(rand(i + 101) * 2.5).toFixed(2)}s`,
        duration: `${(2.8 + rand(i + 201) * 2.8).toFixed(2)}s`,
        drift: `${Math.round((rand(i + 301) - 0.5) * 160)}px`,
        spin: `${Math.round(360 + rand(i + 401) * 720)}deg`,
        color: COLORS[i % COLORS.length],
        round: rand(i + 501) > 0.6,
      })),
    [pieces],
  )
  return (
    <div className="confetti" aria-hidden="true">
      {items.map((it, i) => (
        <span
          key={i}
          className={`confetti__piece${it.round ? ' confetti__piece--round' : ''}`}
          style={
            {
              left: it.left,
              background: it.color,
              animationDelay: it.delay,
              animationDuration: it.duration,
              '--drift': it.drift,
              '--spin': it.spin,
            } as CSSProperties
          }
        />
      ))}
    </div>
  )
}
