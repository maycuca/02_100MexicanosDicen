import { vi } from 'vitest'
import { SOUNDS, resetAudio, type SoundName } from '../shared/audio/synth'

class FakeParam {
  value = 0
  setValueAtTime = vi.fn()
  exponentialRampToValueAtTime = vi.fn()
}

type FakeNode = { connect: (n: unknown) => unknown }
export type FakeOscillator = FakeNode & {
  type: string
  frequency: FakeParam
  start: ReturnType<typeof vi.fn>
  stop: ReturnType<typeof vi.fn>
}

/** AudioContext falso: registra los osciladores para saber qué sonó (AC-51). */
export class FakeAudioContext {
  static instances: FakeAudioContext[] = []
  state: 'suspended' | 'running' | 'closed' = 'suspended'
  currentTime = 0
  sampleRate = 8000
  destination = {}
  oscillators: FakeOscillator[] = []

  constructor() {
    FakeAudioContext.instances.push(this)
  }

  resume = vi.fn(async () => {
    this.state = 'running'
  })

  createGain() {
    return { gain: new FakeParam(), connect: (n: unknown) => n }
  }

  createBuffer(_channels: number, length: number, sampleRate: number) {
    const data = new Float32Array(length)
    return { sampleRate, getChannelData: () => data }
  }

  private lastSource: { start: ReturnType<typeof vi.fn>; stop: ReturnType<typeof vi.fn> } | null = null

  createBufferSource() {
    const src = { buffer: null as unknown, loop: false, connect: (n: unknown) => n, start: vi.fn(), stop: vi.fn() }
    this.lastSource = src
    return src
  }

  /** El filtro del ruido (spec 005) cuenta como una "nota" más: su frecuencia identifica el sonido. */
  createBiquadFilter(): FakeOscillator & { Q: FakeParam } {
    // Comparte start/stop con la fuente de ruido que filtra (se crea justo antes).
    const src = this.lastSource ?? { start: vi.fn(), stop: vi.fn() }
    const f = { type: 'bandpass', frequency: new FakeParam(), Q: new FakeParam(), connect: (n: unknown) => n, start: src.start, stop: src.stop }
    this.oscillators.push(f)
    return f
  }

  createOscillator(): FakeOscillator {
    const o: FakeOscillator = { type: 'sine', frequency: new FakeParam(), connect: (n) => n, start: vi.fn(), stop: vi.fn() }
    this.oscillators.push(o)
    return o
  }
}

/** Instala el AudioContext falso (jsdom no trae Web Audio). */
export function installFakeAudio() {
  FakeAudioContext.instances.length = 0
  resetAudio()
  vi.stubGlobal('AudioContext', FakeAudioContext)
}

export const audioContext = () => FakeAudioContext.instances.at(-1)

/** Sonidos reproducidos, reconocidos por su secuencia de frecuencias iniciales. */
export function playedSounds(): SoundName[] {
  const ctx = audioContext()
  if (!ctx) return []
  const freqs = ctx.oscillators.map((o) => o.frequency.setValueAtTime.mock.calls[0]?.[0] as number)
  const out: SoundName[] = []
  let i = 0
  while (i < freqs.length) {
    const match = (Object.keys(SOUNDS) as SoundName[]).find((name) =>
      SOUNDS[name].every((n, k) => freqs[i + k] === n.freq),
    )
    if (!match) throw new Error(`sonido desconocido en el oscilador ${i}`)
    out.push(match)
    i += SOUNDS[match].length
  }
  return out
}
