import { useCallback, useEffect, useRef, useState } from 'react'
import type { GameEvent, GameEventType } from '../realtime/messages'
import { URGENT_SECONDS, secondsLeft, useCountdown } from '../ui/useCountdown'
import { GESTURES } from '../device/gestures'
import { getSoundPreference, setSoundPreference } from './preference'
import { isAudioSupported, isAudioUnlocked, playSound, unlockAudio, type SoundName } from './synth'

/** D16: cada sonido lo dispara un evento de /topic/rooms/{code}/events (AC-51). */
export const SOUND_FOR_EVENT: Partial<Record<GameEventType, SoundName>> = {
  BUZZ_WON: 'buzz',
  /** Spec 005, AC-89: júbilo. */
  ANSWER_REVEALED: 'reveal',
  STRIKE: 'strike',
  /** Spec 005, AC-86: fallo en el cara a cara, sin X. */
  FACE_OFF_MISS: 'miss',
  STEAL_STARTED: 'steal',
  ROUND_ENDED: 'roundEnd',
  GAME_OVER: 'winner',
}

const MAX_SEEN = 200

/**
 * Sonidos de la TV (T713). `enabled` = preferencia local Y `RoomState.soundEnabled` (AC-53).
 * Devuelve `handleEvent` para conectarlo al `onEvent` de `useRoomState`. Cada evento suena una
 * sola vez (por tipo y `version`). Además toca un tic en cada uno de los últimos 5 s de `deadline`.
 */
export function useGameSounds({ enabled, deadline }: { enabled: boolean; deadline: string | null | undefined }) {
  const enabledRef = useRef(enabled)
  useEffect(() => {
    enabledRef.current = enabled
  })
  const seen = useRef(new Set<string>())

  const handleEvent = useCallback((e: GameEvent) => {
    const sound = SOUND_FOR_EVENT[e.type]
    if (!sound) return
    const key = `${e.type}:${e.version}`
    if (seen.current.has(key)) return
    seen.current.add(key)
    if (seen.current.size > MAX_SEEN) seen.current = new Set([key])
    if (enabledRef.current) playSound(sound)
  }, [])

  const ms = useCountdown(deadline)
  const secs = ms === null ? null : secondsLeft(ms)
  const lastTick = useRef<string | null>(null)
  useEffect(() => {
    if (!enabled || !deadline || secs === null || secs < 1 || secs > URGENT_SECONDS) return
    const key = `${deadline}:${secs}`
    if (lastTick.current === key) return
    lastTick.current = key
    playSound('tick')
  }, [enabled, deadline, secs])

  return { handleEvent }
}


/**
 * Botón "Activar sonido" de la TV (AC-52). La preferencia se recuerda en localStorage;
 * tras recargar, el navegador vuelve a exigir una interacción: el primer toque/tecla reanuda el audio.
 */
export function useSoundActivation() {
  const [wanted, setWanted] = useState(getSoundPreference)
  const [unlocked, setUnlocked] = useState(isAudioUnlocked)

  useEffect(() => {
    if (!wanted || unlocked) return
    const onGesture = () => {
      void unlockAudio().then(setUnlocked)
    }
    for (const g of GESTURES) window.addEventListener(g, onGesture, { once: true })
    return () => {
      for (const g of GESTURES) window.removeEventListener(g, onGesture)
    }
  }, [wanted, unlocked])

  const activate = useCallback(async () => {
    setSoundPreference(true)
    setWanted(true)
    setUnlocked(await unlockAudio())
  }, [])

  const deactivate = useCallback(() => {
    setSoundPreference(false)
    setWanted(false)
  }, [])

  return { supported: isAudioSupported(), active: wanted && unlocked, wanted, activate, deactivate }
}
