import type { CSSProperties } from 'react'

// Fondo original de la pantalla de título (AC-55): rayos de escenario que giran, dos reflectores,
// guirnaldas de papel picado que ondean y destellos. Todo en CSS; con prefers-reduced-motion queda quieto (AC-60).

const FLAG_COLORS = ['#ff4f8b', '#ffd166', '#2ec4b6', '#ff6b35', '#9b5de5', '#06d6a0', '#f15bb5']
const FLAGS_TOP = 16
const FLAGS_LOW = 12

/** Posiciones fijas (en %) para que el fondo sea igual en cada carga. */
const SPARKLES = [
  [8, 22], [17, 64], [26, 38], [33, 82], [44, 14], [52, 72], [61, 30], [68, 88],
  [76, 18], [83, 58], [91, 36], [12, 86], [57, 52], [38, 58], [88, 78], [4, 48],
]

const vars = (v: Record<string, string | number>) => v as CSSProperties

function Garland({ count, row }: { count: number; row: 'top' | 'low' }) {
  return (
    <div className={`picado picado--${row}`}>
      {Array.from({ length: count }, (_, i) => (
        <span
          key={i}
          className="picado__flag"
          style={vars({ '--i': i, '--flag': FLAG_COLORS[(i + (row === 'low' ? 3 : 0)) % FLAG_COLORS.length] })}
        />
      ))}
    </div>
  )
}

export function TitleBackdrop() {
  return (
    <div className="title-backdrop" aria-hidden="true">
      <div className="title-backdrop__rays" />
      <div className="title-backdrop__spot title-backdrop__spot--left" />
      <div className="title-backdrop__spot title-backdrop__spot--right" />
      <div className="title-backdrop__floor" />
      <Garland count={FLAGS_LOW} row="low" />
      <Garland count={FLAGS_TOP} row="top" />
      {SPARKLES.map(([x, y], i) => (
        <span key={i} className="sparkle" style={vars({ '--x': `${x}%`, '--y': `${y}%`, '--i': i })} />
      ))}
    </div>
  )
}
