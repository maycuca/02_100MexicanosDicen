import { useCallback, useState } from 'react'
import { getSoundPreference, setSoundPreference } from './preference'
import { isAudioSupported, playSound, unlockAudio } from './synth'

export type MenuSound = 'menuMove' | 'menuSelect'

/**
 * Sonidos del menú de título (spec 003, AC-59). Solo suenan si la preferencia de sonido de este
 * navegador (AC-52) está activa, y siempre desde una interacción (tecla, clic o toque): el
 * AudioContext se crea/reanuda en ese momento, así que sin interacción previa no suena nada.
 */
export function useMenuSounds() {
  const [enabled, setEnabled] = useState(getSoundPreference)

  const play = useCallback(
    (name: MenuSound) => {
      if (!enabled) return
      void unlockAudio().then((ok) => {
        if (ok) playSound(name)
      })
    },
    [enabled],
  )

  const setSound = useCallback((on: boolean) => {
    setSoundPreference(on)
    setEnabled(on)
    if (on) {
      void unlockAudio().then((ok) => {
        if (ok) playSound('menuSelect')
      })
    }
  }, [])

  return { supported: isAudioSupported(), enabled, play, setSound }
}
