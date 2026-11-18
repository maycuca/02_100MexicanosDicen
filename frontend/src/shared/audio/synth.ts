// D15 / AC-51: sonidos originales sintetizados con Web Audio API (osciladores y envolventes).
// Sin archivos de audio ni CDNs: funcionan en una LAN sin internet (constitución §IV, §VII).

export type SoundName =
  | 'buzz'
  | 'reveal'
  | 'strike'
  | 'miss'
  | 'steal'
  | 'roundEnd'
  | 'winner'
  | 'tick'
  | 'menuMove'
  | 'menuSelect'

/**
 * Una nota: empieza en `at` (s, relativo al sonido), dura `dur` y puede deslizarse a `to` Hz.
 * Con `wave: 'noise'` es ruido filtrado alrededor de `freq` (aplausos, gritos del público; spec 005, AC-89);
 * `attack` alarga la entrada (s) para que suene como una multitud que crece.
 */
type Note = {
  at: number
  dur: number
  freq: number
  to?: number
  wave?: OscillatorType | 'noise'
  gain?: number
  attack?: number
  /** Solo ruido: ancho del filtro (Q); menor = más ancho. */
  q?: number
}

const notes = (wave: OscillatorType, gain: number, step: number, dur: number, freqs: number[]): Note[] =>
  freqs.map((freq, i) => ({ at: i * step, dur, freq, wave, gain }))

/** Composiciones propias del proyecto (no copian melodías de ningún programa). */
export const SOUNDS: Record<SoundName, Note[]> = {
  // Buzzer ganado: dos chirridos ascendentes.
  buzz: [
    { at: 0, dur: 0.12, freq: 392, to: 784, wave: 'square', gain: 0.22 },
    { at: 0.14, dur: 0.24, freq: 523, to: 1046, wave: 'square', gain: 0.22 },
  ],
  // Spec 005 (AC-89) — júbilo al revelar una respuesta correcta: campanazo y fanfarria mayor ascendente
  // sobre un público que aplaude y grita (ruido filtrado en capas) con dos silbidos que suben.
  reveal: [
    { at: 0, dur: 0.4, freq: 880, wave: 'triangle', gain: 0.32 },
    { at: 0.05, dur: 0.5, freq: 1318.5, wave: 'triangle', gain: 0.24 },
    ...notes('square', 0.1, 0.09, 0.16, [523, 659, 784]),
    { at: 0.27, dur: 0.55, freq: 1047, wave: 'square', gain: 0.12 },
    { at: 0.27, dur: 0.55, freq: 523, wave: 'triangle', gain: 0.26 },
    // Público: grave (voces), medio (gritos) y agudo (aplausos), con entrada lenta.
    { at: 0.04, dur: 1.5, freq: 450, wave: 'noise', gain: 0.22, attack: 0.18, q: 0.8 },
    { at: 0.08, dur: 1.4, freq: 1300, wave: 'noise', gain: 0.18, attack: 0.15, q: 1.2 },
    { at: 0.1, dur: 1.3, freq: 3200, wave: 'noise', gain: 0.14, attack: 0.08, q: 0.7 },
    // Silbidos de festejo.
    { at: 0.35, dur: 0.35, freq: 1800, to: 2700, wave: 'sine', gain: 0.07 },
    { at: 0.75, dur: 0.4, freq: 2000, to: 3000, wave: 'sine', gain: 0.06 },
  ],
  // X: zumbido grave que cae, con dos osciladores desafinados.
  strike: [
    { at: 0, dur: 0.7, freq: 140, to: 85, wave: 'sawtooth', gain: 0.32 },
    { at: 0, dur: 0.7, freq: 147, to: 90, wave: 'square', gain: 0.16 },
  ],
  // Spec 005 (AC-86): fallo en el cara a cara (no es X): dos notas suaves que bajan.
  miss: [
    { at: 0, dur: 0.16, freq: 587, wave: 'triangle', gain: 0.28 },
    { at: 0.17, dur: 0.3, freq: 440, to: 392, wave: 'triangle', gain: 0.28 },
  ],
  // Inicio de robo: trino de tensión y subida.
  steal: [
    ...notes('sine', 0.25, 0.09, 0.09, [330, 349, 330, 349, 330, 349]),
    { at: 0.56, dur: 0.35, freq: 440, to: 880, wave: 'triangle', gain: 0.28 },
  ],
  // Fin de ronda: arpegio mayor ascendente.
  roundEnd: [
    ...notes('triangle', 0.3, 0.12, 0.3, [392, 494, 587]),
    { at: 0.36, dur: 0.6, freq: 784, wave: 'triangle', gain: 0.32 },
  ],
  // Spec 007 (AC-115) — música de dicha al terminar la partida (~6 s, composición original):
  // melodía alegre en Do mayor sobre un bajo saltarín, acordes de cierre, aplausos del público y silbidos.
  winner: [
    // Melodía (cuadrada, brillante)
    ...notes('square', 0.12, 0.2, 0.18, [523, 659, 784, 659, 698, 880, 784, 659]),
    ...notes('square', 0.12, 0.2, 0.18, [587, 698, 880, 698, 784, 988, 1047, 988]).map((n) => ({ ...n, at: n.at + 1.6 })),
    ...notes('square', 0.13, 0.2, 0.18, [1047, 988, 880, 784, 880, 988]).map((n) => ({ ...n, at: n.at + 3.2 })),
    { at: 4.4, dur: 1.4, freq: 1047, wave: 'square', gain: 0.14 },
    // Bajo (triángulo) en negras
    ...notes('triangle', 0.3, 0.4, 0.3, [131, 196, 175, 196, 147, 220, 196, 247, 131, 196, 175]),
    // Acorde final
    { at: 4.4, dur: 1.5, freq: 523, wave: 'triangle', gain: 0.28 },
    { at: 4.4, dur: 1.5, freq: 659, wave: 'triangle', gain: 0.22 },
    { at: 4.4, dur: 1.5, freq: 784, wave: 'triangle', gain: 0.2 },
    // Público que aplaude y grita durante toda la música
    { at: 0.05, dur: 5.6, freq: 500, wave: 'noise', gain: 0.12, attack: 0.6, q: 0.8 },
    { at: 0.1, dur: 5.5, freq: 3000, wave: 'noise', gain: 0.1, attack: 0.4, q: 0.7 },
    // Silbidos de festejo
    { at: 1.5, dur: 0.4, freq: 1800, to: 2800, wave: 'sine', gain: 0.06 },
    { at: 3.1, dur: 0.4, freq: 2000, to: 3000, wave: 'sine', gain: 0.06 },
    { at: 4.5, dur: 0.5, freq: 1900, to: 3200, wave: 'sine', gain: 0.07 },
  ],
  // Tic de los últimos 5 s.
  tick: [{ at: 0, dur: 0.05, freq: 1200, wave: 'sine', gain: 0.25 }],
  // Spec 003 (AC-59): cursor del menú de título, un "blip" corto que sube.
  menuMove: [{ at: 0, dur: 0.07, freq: 740, to: 988, wave: 'square', gain: 0.1 }],
  // Opción activada: tres notas rápidas y brillantes.
  menuSelect: notes('square', 0.12, 0.06, 0.1, [587, 880, 1175]),
}

/** Sonidos que suenan en el momento, sin encolarse detrás de otros. */
const IMMEDIATE: ReadonlySet<SoundName> = new Set(['tick', 'menuMove', 'menuSelect'])

export const soundDuration = (name: SoundName) => Math.max(...SOUNDS[name].map((n) => n.at + n.dur))

type AudioContextCtor = new () => AudioContext

function audioContextCtor(): AudioContextCtor | undefined {
  const g = globalThis as { AudioContext?: AudioContextCtor; webkitAudioContext?: AudioContextCtor }
  return g.AudioContext ?? g.webkitAudioContext
}

/** Si un sonido se encola más allá de este retraso se toca de inmediato. */
const MAX_QUEUE_S = 1.5
const MASTER_GAIN = 0.7

let ctx: AudioContext | null = null
let master: GainNode | null = null
let busyUntil = 0

export function isAudioSupported(): boolean {
  return audioContextCtor() !== undefined
}

/** true si el navegador ya permite reproducir (hubo interacción). */
export function isAudioUnlocked(): boolean {
  return ctx?.state === 'running'
}

/**
 * Crea/reanuda el AudioContext. Debe llamarse desde una interacción del usuario (AC-52):
 * los navegadores bloquean el audio hasta entonces.
 */
export async function unlockAudio(): Promise<boolean> {
  const Ctor = audioContextCtor()
  if (!Ctor) return false
  try {
    if (!ctx) {
      ctx = new Ctor()
      master = ctx.createGain()
      master.gain.value = MASTER_GAIN
      master.connect(ctx.destination)
    }
    if (ctx.state !== 'running') await ctx.resume()
    return isAudioUnlocked()
  } catch {
    return false
  }
}

let noiseBuffer: AudioBuffer | null = null

/** Dos segundos de ruido blanco reutilizable (se crea una sola vez por AudioContext). */
function noise(audio: AudioContext): AudioBuffer {
  if (noiseBuffer && noiseBuffer.sampleRate === audio.sampleRate) return noiseBuffer
  const length = Math.floor(audio.sampleRate * 2)
  const buffer = audio.createBuffer(1, length, audio.sampleRate)
  const data = buffer.getChannelData(0)
  for (let i = 0; i < length; i++) data[i] = Math.random() * 2 - 1
  noiseBuffer = buffer
  return buffer
}

function scheduleNote(audio: AudioContext, out: GainNode, n: Note, start: number) {
  const t0 = start + n.at
  const t1 = t0 + n.dur
  const peak = n.gain ?? 0.3
  const env = audio.createGain()
  let source: AudioScheduledSourceNode
  if (n.wave === 'noise') {
    const src = audio.createBufferSource()
    src.buffer = noise(audio)
    src.loop = true
    const band = audio.createBiquadFilter()
    band.type = 'bandpass'
    band.frequency.setValueAtTime(n.freq, t0)
    band.Q.value = n.q ?? 1
    src.connect(band)
    band.connect(env)
    source = src
  } else {
    const osc = audio.createOscillator()
    osc.type = n.wave ?? 'sine'
    osc.frequency.setValueAtTime(n.freq, t0)
    if (n.to) osc.frequency.exponentialRampToValueAtTime(n.to, t1)
    osc.connect(env)
    source = osc
  }
  // Envolvente: ataque (corto por defecto) y caída exponencial (sin clics).
  env.gain.setValueAtTime(0.0001, t0)
  env.gain.exponentialRampToValueAtTime(peak, t0 + (n.attack ?? Math.min(0.015, n.dur / 4)))
  env.gain.exponentialRampToValueAtTime(0.0001, t1)
  env.connect(out)
  source.start(t0)
  source.stop(t1 + 0.02)
}

/**
 * Reproduce un sonido. Los sonidos se encolan para no encimarse (p. ej. X seguida de robo),
 * salvo el tic y los del menú, que suenan en el momento. Devuelve false si el audio no está activo.
 */
export function playSound(name: SoundName): boolean {
  if (!ctx || !master || ctx.state !== 'running') return false
  const now = ctx.currentTime
  let start = now
  if (!IMMEDIATE.has(name)) {
    start = busyUntil > now && busyUntil - now <= MAX_QUEUE_S ? busyUntil : now
    busyUntil = start + soundDuration(name)
  }
  try {
    for (const n of SOUNDS[name]) scheduleNote(ctx, master, n, start)
    return true
  } catch {
    return false
  }
}

/** Solo para pruebas: olvida el AudioContext actual. */
export function resetAudio(): void {
  noiseBuffer = null
  ctx = null
  master = null
  busyUntil = 0
}
