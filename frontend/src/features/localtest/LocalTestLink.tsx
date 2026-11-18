import { useEffect, useRef, useState } from 'react'

/** Copia al portapapeles; si la API no existe o falla, intenta con `execCommand` (spec 004 §4). */
async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    /* sigue con el respaldo */
  }
  try {
    const area = document.createElement('textarea')
    area.value = text
    area.setAttribute('readonly', '')
    area.style.position = 'fixed'
    area.style.opacity = '0'
    document.body.appendChild(area)
    area.select()
    const ok = document.execCommand('copy')
    area.remove()
    return ok
  } catch {
    return false
  }
}

type Props = { label: string; url: string }

/** T902 · AC-75: URL para pegar en otra pestaña, con "Copiar" y "Abrir en pestaña nueva". */
export function LocalTestLink({ label, url }: Props) {
  const [status, setStatus] = useState<'idle' | 'copied' | 'failed'>('idle')
  const timer = useRef<number | undefined>(undefined)

  useEffect(() => () => window.clearTimeout(timer.current), [])

  if (!url) return <span>Preparando URL…</span>

  async function copy() {
    const ok = await copyText(url)
    setStatus(ok ? 'copied' : 'failed')
    window.clearTimeout(timer.current)
    if (ok) timer.current = window.setTimeout(() => setStatus('idle'), 2000)
  }

  return (
    <div className="local-link">
      <code className="local-link__url" aria-label={`URL de ${label}`}>{url}</code>
      <div className="local-link__actions">
        <button type="button" className="btn btn--small" onClick={() => void copy()}>
          Copiar
        </button>
        <button
          type="button"
          className="btn btn--ghost btn--small"
          // noopener: la pestaña nueva empieza con su propia sesión (AC-76).
          onClick={() => window.open(url, '_blank', 'noopener,noreferrer')}
        >
          Abrir en pestaña nueva
        </button>
      </div>
      <p className="local-link__status" role="status">
        {status === 'copied' ? '¡Copiada!' : status === 'failed' ? 'Selecciona la URL y cópiala' : ''}
      </p>
    </div>
  )
}
