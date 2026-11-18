export const GAME_TITLE = 'Lo que 100 Mexicanos dicen'

type Props = {
  /** hero: pantalla de título · small: cabecera de la TV · mini: celulares. */
  size?: 'hero' | 'small' | 'mini'
  /** Elemento contenedor; por defecto el título de la página. */
  as?: 'h1' | 'p' | 'div'
  className?: string
}

/**
 * Logotipo original de "Lo que 100 Mexicanos dicen" (spec 003, AC-55, AC-61; constitución §VII).
 * Solo CSS y fuentes del sistema: relieve con sombras en capas, contorno y "100" sobre un sello
 * de rayos. El texto accesible es el nombre completo; las piezas visuales son decorativas.
 */
export function GameLogo({ size = 'hero', as: Tag = 'h1', className = '' }: Props) {
  return (
    <Tag className={`game-logo game-logo--${size} ${className}`.trim()}>
      <span className="sr-only">{GAME_TITLE}</span>
      <span className="game-logo__art" aria-hidden="true">
        <span className="game-logo__ribbon game-logo__ribbon--top">Lo que</span>
        <span className="game-logo__badge">
          <span className="game-logo__burst" />
          <span className="game-logo__num">100</span>
        </span>
        <span className="game-logo__word">Mexicanos</span>
        <span className="game-logo__ribbon game-logo__ribbon--bottom">dicen</span>
      </span>
    </Tag>
  )
}
