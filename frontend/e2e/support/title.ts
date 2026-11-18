import { expect, type Locator, type Page } from '@playwright/test'

/**
 * Spec 003 (T822) — Pantalla de título `/`: menú EMPEZAR / AJUSTES y modal de ajustes.
 * Todos los e2e crean la sala con estas funciones; si cambian los textos o roles de
 * `src/features/title/**`, se ajustan solo aquí.
 */

export const GAME_TITLE = 'Lo que 100 Mexicanos dicen'
/** El logotipo puede partir el título en varios elementos; se tolera el espaciado. */
export const GAME_TITLE_RE = /Lo que\s*100\s*Mexicanos\s*dicen/i
export const ROOM_URL = /\/screen\/[A-HJ-NP-Z]{4}$/

export type Settings = {
  rounds?: number
  targetScore?: number
  /** 0 = Sin temporizador (AC-46, AC-58). */
  turnSeconds?: number
  autoStrikeOnTimeout?: boolean
  /** Spec 004 (AC-73): modo "Prueba local". */
  localTest?: boolean
}

/** Logotipo con el nombre del juego (AC-55, AC-61). */
export function logo(page: Page): Locator {
  return page.getByRole('heading', { name: GAME_TITLE_RE }).first()
}

/** Opción del menú de título; acepta botones o `menuitem`. */
export function menuItem(page: Page, label: 'EMPEZAR' | 'AJUSTES'): Locator {
  const name = new RegExp(`^\\s*${label}\\s*$`, 'i')
  return page.getByRole('menuitem', { name }).or(page.getByRole('button', { name }))
}

export function settingsDialog(page: Page): Locator {
  return page.getByRole('dialog', { name: /ajustes/i })
}

/** AC-56: la opción está seleccionada (foco, aria-selected/current o data-selected). */
export async function expectSelected(item: Locator) {
  await expect
    .poll(() =>
      item.evaluate((el) => {
        const flag = (v: string | null) => v !== null && v !== 'false'
        return (
          el === document.activeElement ||
          el.getAttribute('aria-selected') === 'true' ||
          flag(el.getAttribute('aria-current')) ||
          flag(el.getAttribute('data-selected')) ||
          /\b(is-)?(selected|active)\b|--selected|--active/.test(el.className)
        )
      }),
    )
    .toBe(true)
}

export async function openTitle(page: Page) {
  await page.goto('/')
  await expect(logo(page)).toBeVisible()
  await expect(menuItem(page, 'EMPEZAR')).toBeVisible()
}

/** Abre el modal de AJUSTES con el mouse/toque. */
export async function openSettings(page: Page): Promise<Locator> {
  await menuItem(page, 'AJUSTES').click()
  const dialog = settingsDialog(page)
  await expect(dialog).toBeVisible()
  return dialog
}

export async function closeSettings(page: Page) {
  const dialog = settingsDialog(page)
  await dialog.getByRole('button', { name: /^\s*volver\s*$/i }).click()
  await expect(dialog).toBeHidden()
}

/**
 * Selector "◄ valor ►" (role=spinbutton): usa ←/→ como en un menú de juego hasta llegar a `target`.
 * (Las flechas ◄ ► de la fila seleccionada "laten" sin parar, así que Playwright nunca las ve estables
 * para hacer clic; el teclado es además la forma principal de manejar el modal, AC-58.)
 */
async function setSpin(dialog: Locator, label: string, target: number) {
  const spin = dialog.getByRole('spinbutton', { name: label })
  await spin.focus()
  for (let i = 0; i < 40; i++) {
    const now = Number(await spin.getAttribute('aria-valuenow'))
    if (now === target) return
    await spin.press(now < target ? 'ArrowRight' : 'ArrowLeft')
  }
  throw new Error(`${label} no llegó a ${target}`)
}

async function spinValue(dialog: Locator, label: string): Promise<number> {
  return Number(await dialog.getByRole('spinbutton', { name: label }).getAttribute('aria-valuenow'))
}

function autoStrikeSwitch(dialog: Locator): Locator {
  return dialog.getByRole('switch', { name: 'X automática' })
}

/** Fija los ajustes en el modal (AC-58) y lo cierra con "Volver". */
export async function applySettings(page: Page, s: Settings) {
  const dialog = await openSettings(page)
  if (s.rounds !== undefined) await setSpin(dialog, 'Rondas', s.rounds)
  if (s.targetScore !== undefined) await setSpin(dialog, 'Meta de puntos', s.targetScore)
  if (s.turnSeconds !== undefined) await setSpin(dialog, 'Temporizador', s.turnSeconds)
  if (s.autoStrikeOnTimeout !== undefined) {
    const sw = autoStrikeSwitch(dialog)
    if ((await sw.getAttribute('aria-checked')) !== String(s.autoStrikeOnTimeout)) await sw.click()
    await expect(sw).toHaveAttribute('aria-checked', String(s.autoStrikeOnTimeout))
  }
  if (s.localTest !== undefined) {
    const sw = dialog.getByRole('switch', { name: 'Prueba local' })
    if ((await sw.getAttribute('aria-checked')) !== String(s.localTest)) await sw.click()
    await expect(sw).toHaveAttribute('aria-checked', String(s.localTest))
  }
  await closeSettings(page)
}

/** Lee los valores del modal (abre y cierra). */
export async function readSettings(page: Page): Promise<Required<Settings>> {
  const dialog = await openSettings(page)
  const values = {
    rounds: await spinValue(dialog, 'Rondas'),
    targetScore: await spinValue(dialog, 'Meta de puntos'),
    turnSeconds: await spinValue(dialog, 'Temporizador'),
    autoStrikeOnTimeout: (await autoStrikeSwitch(dialog).getAttribute('aria-checked')) === 'true',
  }
  await closeSettings(page)
  return values
}

export { spinValue }

/**
 * Flujo de la spec 003: `/` → (AJUSTES) → EMPEZAR → `/screen/<código>` (AC-57).
 * Devuelve el código de sala.
 */
export async function createRoomFromTitle(page: Page, s: Settings = {}): Promise<string> {
  await openTitle(page)
  if (Object.keys(s).length > 0) await applySettings(page, s)
  await menuItem(page, 'EMPEZAR').click()
  await expect(page).toHaveURL(ROOM_URL)
  return new URL(page.url()).pathname.split('/').pop()!
}
