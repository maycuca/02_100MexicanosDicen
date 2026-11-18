import { useEffect, useState } from 'react'
import { api } from '../../shared/api/client'

/** Base `http://<IP-LAN>:<puerto>` para los QR (AC-04). La IP la decide el servidor (`app.lan-address` si se configuró; AC-69). */
export function useJoinBaseUrl() {
  const [lanAddress, setLanAddress] = useState('')
  const [port, setPort] = useState<number>(Number(window.location.port) || 5173)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .getNetworkInfo()
      .then((info) => {
        setLanAddress(info.lanAddress)
        setPort(info.frontendPort)
      })
      .catch(() => setError('No se pudo detectar la IP de la red. Revisa que el servidor esté encendido.'))
  }, [])

  const baseUrl = lanAddress ? `http://${lanAddress}:${port}` : ''
  return { baseUrl, lanAddress, error }
}
