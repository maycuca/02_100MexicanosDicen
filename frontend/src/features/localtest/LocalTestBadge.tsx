import { useLocalTestMode } from './mode'

/** AC-74: distintivo en la pantalla de título cuando el modo está encendido. */
export function LocalTestBadge() {
  const { enabled } = useLocalTestMode()
  if (!enabled) return null
  return <span className="local-test-badge">PRUEBA LOCAL</span>
}

/** AC-75: aviso de la pantalla de QR. */
export function LocalTestBanner() {
  return (
    <p className="local-test-banner" role="note">
      <strong>Prueba local:</strong> pega cada URL en una pestaña distinta de este navegador
    </p>
  )
}
