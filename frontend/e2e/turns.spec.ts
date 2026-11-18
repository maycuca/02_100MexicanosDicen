import { devices, expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test'
import { createRoomFromTitle } from './support/title'

/**
 * T725 — Experiencia de juego (spec 002-experiencia-de-juego).
 * Pantalla (TV 1920×1080) + celular del presentador + 2 jugadores móviles (Pixel 7 y viewport de iPhone).
 * Requiere backend (:8080) y frontend (:5173) arriba: `npm run e2e`.
 */

const TEAM_A = 'Equipo Rojo'
const TEAM_B = 'Equipo Verde'

const { defaultBrowserType: _ignored, ...iPhone } = devices['iPhone 14']

type Actor = { context: BrowserContext; page: Page }
type Table = { tv: Actor; host: Actor; p1: Actor; p2: Actor; code: string }

async function open(browser: Browser, options: Parameters<Browser['newContext']>[0]): Promise<Actor> {
  const context = await browser.newContext(options)
  return { context, page: await context.newPage() }
}

function pathOf(url: string): string {
  const u = new URL(url)
  return `${u.pathname}${u.search}`
}

async function qrUrl(tv: Page, title: 'Presentador' | 'Jugadores'): Promise<string> {
  const url = tv.getByRole('region', { name: `QR ${title}` }).locator('.qr-card__url')
  await expect(url).toHaveText(/^http:\/\/.+/)
  return (await url.textContent())!.trim()
}

async function joinAs(player: Page, joinPath: string, name: string, teamName: string, code: string) {
  await player.goto(joinPath)
  await player.getByLabel('Tu nombre').fill(name)
  await player.getByRole('button', { name: teamName }).click()
  await player.getByRole('button', { name: 'Entrar' }).click()
  await expect(player).toHaveURL(new RegExp(`/play/${code}$`))
  await expect(player.getByText('Espera a que el presentador inicie la partida…')).toBeVisible()
}

/** Crea la sala desde la TV con el temporizador indicado (AC-46; spec 003: en el modal AJUSTES) y devuelve el código. */
async function createRoom(tv: Page, turnSeconds: number): Promise<string> {
  return createRoomFromTitle(tv, { rounds: 3, turnSeconds })
}

/** TV + presentador + Lupita (Rojo) + Pancho (Verde), partida iniciada en el cara a cara de la ronda 1. */
async function startTable(browser: Browser, turnSeconds: number): Promise<Table> {
  const tv = await open(browser, { viewport: { width: 1920, height: 1080 } })
  const host = await open(browser, { ...devices['Pixel 7'] })
  const p1 = await open(browser, { ...devices['Pixel 7'] })
  const p2 = await open(browser, { ...iPhone })
  await recordVibrations(p1)
  await recordVibrations(p2)
  const code = await createRoom(tv.page, turnSeconds)
  await host.page.goto(pathOf(await qrUrl(tv.page, 'Presentador')))
  await expect(host.page.getByText('Sala de espera')).toBeVisible()
  const joinPath = pathOf(await qrUrl(tv.page, 'Jugadores'))
  await joinAs(p1.page, joinPath, 'Lupita', TEAM_A, code)
  await joinAs(p2.page, joinPath, 'Pancho', TEAM_B, code)
  await host.page.getByRole('button', { name: 'Iniciar partida' }).click()
  await expect(tv.page.getByText('Ronda 1 de 3')).toBeVisible()
  return { tv, host, p1, p2, code }
}

type VibrationProbe = { __vibrations: unknown[] }

/** AC-54: registra las llamadas a `navigator.vibrate` del celular (Chromium emulado no vibra). */
async function recordVibrations(actor: Actor) {
  await actor.context.addInitScript(() => {
    const w = window as unknown as VibrationProbe
    w.__vibrations = []
    Object.defineProperty(navigator, 'vibrate', {
      configurable: true,
      value: (pattern: unknown) => {
        w.__vibrations.push(pattern)
        return true
      },
    })
  })
}

const vibrations = (p: Page) => p.evaluate(() => (window as unknown as VibrationProbe).__vibrations.length)

async function closeAll(t: Partial<Table>) {
  await Promise.all([t.tv, t.host, t.p1, t.p2].filter((a): a is Actor => !!a).map((a) => a.context.close()))
}

const panel = (tv: Page, team: 'A' | 'B') => tv.getByTestId(`team-panel-${team}`)
const buzzer = (p: Page) => p.getByRole('button', { name: 'Buzzer: presiona para responder primero' })
const strikeFor = (host: Page, teamName: string) => host.getByRole('button', { name: `✕ X para ${teamName}` })
/** Spec 005 (AC-86): en el cara a cara el botón no marca X, pasa el turno. */
const missFor = (host: Page, teamName: string) =>
  host.getByRole('button', { name: new RegExp(`^Falló ${teamName} · (pasa el turno|termina el cara a cara)$`) })
const hostTurn = (host: Page) => host.locator('.turn-banner__text')

/** AC-37: el panel de `team` está resaltado con "EN TURNO" y el otro, atenuado. */
async function expectTurnOnTv(tv: Page, team: 'A' | 'B') {
  const other = team === 'A' ? 'B' : 'A'
  await expect(panel(tv, team).getByText('EN TURNO', { exact: true })).toBeVisible()
  await expect(panel(tv, other).getByText('EN TURNO', { exact: true })).toHaveCount(0)
  await expect(panel(tv, team)).toHaveClass(/board__side--turn/)
  await expect(panel(tv, other)).toHaveClass(/board__side--dim/)
}

/** AC-38: banner del jugador según el equipo en turno. */
async function expectTurnOnPhones(t: Table, team: 'A' | 'B') {
  const [mine, theirs] = team === 'A' ? [t.p1.page, t.p2.page] : [t.p2.page, t.p1.page]
  await expect(mine.getByText('¡Le toca a tu equipo!')).toBeVisible()
  await expect(theirs.getByText(`Turno de ${team === 'A' ? TEAM_A : TEAM_B}`)).toBeVisible()
}

/** AC-39 / AC-45: el presentador ve el equipo en turno y su botón: "Falló …" en el cara a cara (spec 005) o "X para …". */
async function expectTurnOnHost(host: Page, teamName: string) {
  await expect(hostTurn(host)).toHaveText(`En turno: ${teamName}`)
  await expect(strikeFor(host, teamName).or(missFor(host, teamName))).toBeEnabled()
}

test.describe('T725 experiencia de juego: TV + presentador + 2 jugadores', () => {
  test.skip(({ browserName }) => browserName !== 'chromium', 'contextos emulados en Chromium')

  test('AC-35 · AC-37…AC-45 · AC-54 resalte "EN TURNO" cara a cara → juego → robo y X en el equipo correcto', async ({ browser }, testInfo) => {
    test.skip(testInfo.project.name !== 'presenter-desktop', 'la prueba abre sus propios contextos; basta un proyecto')
    test.setTimeout(90_000)
    let t: Table | undefined
    try {
      t = await startTable(browser, 0)
      const { tv, host, p1, p2 } = t

      // --- FACE_OFF sin buzz: nadie en turno (AC-35, AC-37); la X está deshabilitada (AC-40, AC-45)
      await expect(tv.page.getByText('EN TURNO', { exact: true })).toHaveCount(0)
      await expect(panel(tv.page, 'A')).not.toHaveClass(/board__side--dim/)
      await expect(panel(tv.page, 'B')).not.toHaveClass(/board__side--dim/)
      await expect(hostTurn(host.page)).toHaveText('Ningún equipo en turno')
      await expect(host.page.getByRole('button', { name: 'Falló (sin equipo en turno)' })).toBeDisabled()
      await expect(p1.page.getByText('¡Le toca a tu equipo!')).toHaveCount(0)
      await expect(p2.page.getByText('¡Le toca a tu equipo!')).toHaveCount(0)

      // --- Lupita gana el buzzer → turno de Rojo; su celular vibra (AC-54)
      const p2Before = await vibrations(p2.page)
      await buzzer(p1.page).click()
      await expect.poll(() => vibrations(p1.page), { message: 'AC-54 vibra al ganar el buzzer' }).toBeGreaterThan(0)
      await expectTurnOnTv(tv.page, 'A')
      await expectTurnOnPhones(t, 'A')
      await expectTurnOnHost(host.page, TEAM_A)

      // --- Spec 005 AC-85/AC-86: Lupita falla en el cara a cara → no es X; "¡Pasa a Verde!" y el turno pasa a Verde
      await missFor(host.page, TEAM_A).click()
      await expect(tv.page.getByText(`¡Pasa a ${TEAM_B}!`)).toBeVisible()
      await expect(tv.page.getByRole('img', { name: /^X para/ })).toHaveCount(0)
      await expectTurnOnTv(tv.page, 'B')
      await expectTurnOnPhones(t, 'B')
      await expectTurnOnHost(host.page, TEAM_B)
      await expect(tv.page.getByLabel(`X de ${TEAM_A}: 0`)).toBeVisible()
      await expect.poll(() => vibrations(p2.page), { message: 'AC-54 vibra cuando el turno pasa a su equipo' }).toBeGreaterThan(p2Before)

      // --- Spec 006 AC-95: Rojo no abrió ninguna; Pancho abre la #1 → Verde se queda con el control (PLAYING)
      await expect(host.page.getByText(`${TEAM_A} falló: cualquier respuesta de ${TEAM_B} le da el control`)).toBeVisible()
      await host.page.getByRole('region', { name: 'Respuestas' }).getByRole('button', { name: /^Revelar / }).first().click()
      await expect(tv.page.locator('.slot--revealed')).toHaveCount(1)
      await expect(tv.page.getByText(`Responde ${TEAM_B}`)).toBeVisible()
      await expectTurnOnTv(tv.page, 'B')
      await expectTurnOnPhones(t, 'B')
      await expectTurnOnHost(host.page, TEAM_B)

      // --- Tres X para Verde: se ven debajo del panel de Verde (AC-44), Rojo sigue en 0
      for (let i = 1; i <= 3; i++) {
        await strikeFor(host.page, TEAM_B).click()
        if (i === 1) {
          // La X gigante dura ~1,4 s: se verifica justo después del clic.
          await expect(tv.page.getByRole('img', { name: `X para ${TEAM_B}` })).toHaveAttribute('data-team', 'B')
        }
        await expect(tv.page.getByLabel(`X de ${TEAM_B}: ${i}`)).toBeVisible()
        await expect(tv.page.getByLabel(`X de ${TEAM_A}: 0`)).toBeVisible()
        if (i < 3) await expectTurnOnTv(tv.page, 'B')
      }

      // --- Robo: el turno pasa a Rojo (AC-35)
      await expect(tv.page.getByText(`¡Robo! ${TEAM_A} tiene una oportunidad`)).toBeVisible()
      await expectTurnOnTv(tv.page, 'A')
      await expectTurnOnPhones(t, 'A')
      await expectTurnOnHost(host.page, TEAM_A)

      // --- Rojo falla el robo: la X es de Rojo (AC-40, AC-41) y al cerrar la ronda nadie queda en turno
      await strikeFor(host.page, TEAM_A).click()
      await expect(host.page.getByText('Fin de la ronda', { exact: true })).toBeVisible()
      await expect(tv.page.getByLabel(`X de ${TEAM_A}: 1`)).toBeVisible()
      await expect(tv.page.getByLabel(`X de ${TEAM_B}: 3`)).toBeVisible()
      await expect(tv.page.getByText('EN TURNO', { exact: true })).toHaveCount(0)
      await expect(p1.page.getByText('¡Le toca a tu equipo!')).toHaveCount(0)
      await expect(host.page.getByRole('button', { name: /X para / })).toHaveCount(0)
    } finally {
      if (t) await closeAll(t)
    }
  })

  test('spec 005 AC-79…AC-83 · AC-48 · AC-50 temporizador manual de 10 s: Iniciar, Parar, Reanudar, X automática y Restablecer', async ({ browser }, testInfo) => {
    test.skip(testInfo.project.name !== 'presenter-desktop', 'la prueba abre sus propios contextos; basta un proyecto')
    test.setTimeout(90_000)
    let t: Table | undefined
    try {
      t = await startTable(browser, 10)
      const { tv, host, p1, p2 } = t
      await expect(host.page.locator('.host__stats .stat', { hasText: 'Temporizador' })).toHaveCount(0) // spec 008 AC-120 (ajuste)
      // AC-120: tres fichas y ningún título o valor se sale de su ficha
      await expect(host.page.locator('.host__stats .stat')).toHaveCount(3)
      for (const el of await host.page.locator('.host__stats .stat, .host__stats dt, .host__stats dd').all()) {
        expect(await el.evaluate((n) => n.scrollWidth <= n.clientWidth + 1), (await el.textContent()) ?? '').toBe(true)
      }
      const tvTimer = tv.page.getByRole('timer')
      const controls = host.page.getByRole('group', { name: 'Temporizador' })

      // AC-79: ni el buzzer ni el control lo arrancan; se ve detenido y completo.
      // Spec 006 AC-94 · AC-97: Rojo (buzzer) falla, Verde también → termina el cara a cara con el control para Rojo.
      await buzzer(p1.page).click()
      await missFor(host.page, TEAM_A).click()
      await expect(host.page.getByRole('button', { name: `Falló ${TEAM_B} · termina el cara a cara` })).toBeVisible()
      await missFor(host.page, TEAM_B).click()
      await expect(tv.page.getByText(`Responde ${TEAM_A}`)).toBeVisible()
      await expect(tv.page.getByLabel(`X de ${TEAM_A}: 0`)).toBeVisible()
      await expect(tv.page.getByLabel(`X de ${TEAM_B}: 0`)).toBeVisible()
      await expect(tvTimer).toHaveAccessibleName('Tiempo del turno: 10 segundos, detenido')
      await tv.page.waitForTimeout(1500)
      await expect(tvTimer).toHaveAccessibleName('Tiempo del turno: 10 segundos, detenido')
      await expect(controls.getByRole('button', { name: /Parar/ })).toBeDisabled()

      // AC-80: Iniciar → corre en TV y celulares (AC-50)
      await controls.getByRole('button', { name: /Iniciar/ }).click()
      await expect(tvTimer).toHaveAccessibleName(/^Tiempo del turno: [1-9] segundos$/)
      await expect(p1.page.getByRole('timer')).toBeVisible()
      await expect(p2.page.getByRole('timer')).toBeVisible()

      // AC-80: Parar congela; Reanudar sigue desde ahí
      await controls.getByRole('button', { name: /Parar/ }).click()
      await expect(tvTimer).toHaveAccessibleName(/segundos, detenido$/)
      const frozen = await tvTimer.getAttribute('aria-label')
      await tv.page.waitForTimeout(1500)
      await expect(tvTimer).toHaveAttribute('aria-label', frozen!)
      await controls.getByRole('button', { name: /Reanudar/ }).click()
      await expect(tvTimer).toHaveClass(/countdown--urgent/, { timeout: 12_000 })

      // AC-48/AC-83: al vencer, X automática para Rojo y el temporizador se queda en 0 (no se reinicia)
      await expect(tv.page.getByLabel(`X de ${TEAM_A}: 1`)).toBeVisible({ timeout: 15_000 })
      await expect(tv.page.getByLabel(`X de ${TEAM_B}: 0`)).toBeVisible()
      await expect(tvTimer).toHaveAccessibleName('Tiempo del turno: 0 segundos, detenido')
      await tv.page.waitForTimeout(1500)
      await expect(tvTimer).toHaveAccessibleName('Tiempo del turno: 0 segundos, detenido')
      await expectTurnOnTv(tv.page, 'A')

      // AC-80: Restablecer lo deja completo para el siguiente concursante
      await controls.getByRole('button', { name: /Restablecer/ }).click()
      await expect(tvTimer).toHaveAccessibleName('Tiempo del turno: 10 segundos, detenido')
      await expect(controls.getByRole('button', { name: /Iniciar/ })).toBeEnabled()
    } finally {
      if (t) await closeAll(t)
    }
  })

  test('spec 005 AC-88 · AC-90 · AC-91 tablero sin sonido ni QR; "Menú principal" confirma, cierra la sala y todos salen', async ({ browser }, testInfo) => {
    test.skip(testInfo.project.name !== 'presenter-desktop', 'la prueba abre sus propios contextos; basta un proyecto')
    test.setTimeout(60_000)
    let t: Table | undefined
    try {
      t = await startTable(browser, 0)
      const { tv, host, p1 } = t
      await expect(tv.page.getByRole('button', { name: /sonido/i })).toHaveCount(0)
      await expect(tv.page.getByRole('button', { name: 'QR de presentador' })).toHaveCount(0)

      // spec 008 AC-121 · AC-122: cambiar la pregunta en el cara a cara; la TV muestra la nueva
      const before = (await tv.page.locator('.board__question').textContent())!.trim()
      await host.page.getByRole('button', { name: '↻ Cambiar pregunta' }).click()
      await expect(tv.page.locator('.board__question')).not.toHaveText(before)
      await expect(host.page.locator('.host__question')).toHaveText((await tv.page.locator('.board__question').textContent())!.trim())

      await host.page.getByRole('button', { name: 'Menú principal' }).click()
      const dialog = host.page.getByRole('dialog', { name: '¿Regresar al menú principal?' })
      await expect(dialog).toBeVisible()
      await expect(dialog.getByRole('button', { name: 'Cancelar' })).toBeFocused()
      await host.page.keyboard.press('Escape') // el <dialog> nativo se cierra con Esc
      await expect(dialog).toBeHidden()
      await expect(tv.page.getByTestId('team-panel-A')).toBeVisible()

      await host.page.getByRole('button', { name: 'Menú principal' }).click()
      await dialog.getByRole('button', { name: 'Sí, regresar' }).click()
      // spec 007 AC-113: todos ven "¡Gracias por jugar!" y sus sesiones de la sala se borran
      for (const page of [host.page, p1.page]) {
        await expect(page).toHaveURL(/\/gracias$/)
        await expect(page.getByRole('heading', { name: '¡Gracias por jugar!' })).toBeVisible()
      }
      expect(await p1.page.evaluate((c) => localStorage.getItem(`cm:player:${c}`), t.code)).toBeNull()
      // AC-123: la TV vuelve al menú principal, sin "¡Gracias por jugar!"
      await expect(tv.page).toHaveURL(/\/$/)
      await expect(tv.page.getByRole('button', { name: /empezar/i })).toBeVisible()
      expect(await tv.page.evaluate((c) => localStorage.getItem(`cm:presenter:${c}`), t.code)).toBeNull()
      // spec 007 AC-118: "Cerrar ventana" en el presentador (y jugadores) en lugar de "Menú principal"
      await expect(host.page.getByRole('link', { name: 'Menú principal' })).toHaveCount(0)
      await host.page.getByRole('button', { name: 'Cerrar ventana' }).click()
      await expect(host.page.getByText(/cierra esta pestaña o ventana/)).toBeVisible() // pestaña no abierta por script
    } finally {
      if (t) await closeAll(t)
    }
  })

  test('AC-70 · AC-53 la pantalla de QR no tiene botón de sonido; el presentador silencia y reactiva la TV', async ({ browser }, testInfo) => {
    test.skip(testInfo.project.name !== 'presenter-desktop', 'la prueba abre sus propios contextos; basta un proyecto')
    const tv = await open(browser, { viewport: { width: 1920, height: 1080 } })
    const host = await open(browser, { ...devices['Pixel 7'] })
    try {
      const code = await createRoom(tv.page, 0)
      await expect(tv.page.getByRole('region', { name: 'QR Jugadores' })).toBeVisible()

      // AC-68 · AC-69 · AC-70 (spec 003 US-18): sin código de sala, IP ni botón de sonido en la pantalla de QR.
      await expect(tv.page.getByRole('button', { name: /sonido/i })).toHaveCount(0)
      await expect(tv.page.getByLabel('Código de sala')).toHaveCount(0)
      await expect(tv.page.getByLabel('IP de la red')).toHaveCount(0)

      // AC-53 el presentador silencia y reactiva el sonido de la TV (se verifica en el estado de la sala;
      // el aviso visual "Silenciado por el presentador" vive en el tablero y lo cubre Vitest).
      const soundEnabled = async () =>
        (await (await tv.page.request.get(`http://localhost:5173/api/rooms/${code}`)).json()).soundEnabled as boolean
      await host.page.goto(pathOf(await qrUrl(tv.page, 'Presentador')))
      await expect(host.page.getByText('Sala de espera')).toBeVisible()
      // AC-107: switch "Sonido de la TV"
      const sw = host.page.getByRole('switch', { name: 'Sonido de la TV' })
      await expect(sw).toBeChecked()
      await sw.click()
      await expect(sw).not.toBeChecked()
      await expect.poll(soundEnabled).toBe(false)
      await sw.click()
      await expect(sw).toBeChecked()
      await expect.poll(soundEnabled).toBe(true)
    } finally {
      await Promise.all([tv, host].map((a) => a.context.close()))
    }
  })
})
