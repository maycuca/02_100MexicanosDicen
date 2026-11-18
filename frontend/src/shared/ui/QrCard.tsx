import { QRCodeSVG } from 'qrcode.react'
import type { ReactNode } from 'react'

type Props = {
  title: string
  subtitle: string
  url: string
  accent: string
  /** Contenido que reemplaza al QR (p. ej. "Presentador conectado ✓"). */
  replacement?: ReactNode
  footer?: ReactNode
}

export function QrCard({ title, subtitle, url, accent, replacement, footer }: Props) {
  return (
    <section className="qr-card" style={{ borderColor: accent }} aria-label={`QR ${title}`}>
      <h2 style={{ color: accent }}>{title}</h2>
      <p className="muted">{subtitle}</p>
      <div className="qr-card__code">
        {replacement ??
          (url ? <QRCodeSVG value={url} size={256} marginSize={2} title={`QR ${title}: ${url}`} /> : <span>Detectando red…</span>)}
      </div>
      {!replacement && url && <p className="qr-card__url">{url}</p>}
      {footer}
    </section>
  )
}
