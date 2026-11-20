import { devices, expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test'
import { createRoomFromTitle, logo } from './support/title'

/**
 * T601 — Partida corta de punta a punta (spec 001-juego-base).
 * Pantalla (TV 1920×1080) + celular del presentador + 2 jugadores móviles (Pixel 7 y viewport de iPhone).
 * Requiere backend (:8080) y frontend (:5173) arriba: `npm run e2e`.
 *
 * Las URL de los QR usan la IP de la LAN; aquí se toma su ruta (`/host/…?claim=…`, `/join/…`) y se abre
 * contra el baseURL, que es el mismo frontend.
 */

const ROOM_CODE = /^[A-HJ-NP-Z]{4}$/
const TEAM_A = 'Equipo Rojo'
const TEAM_B = 'Equipo Verde'

// El viewport de iPhone se emula en Chromium: se descarta defaultBrowserType del descriptor.
const { defaultBrowserType: _ignored, ...iPhone } = devices['iPhone 14']

type Actor = { context: BrowserContext; page: Page }

async function open(browser: Browser, options: Parameters<Browser['newContext']>[0]): Promise<Actor> {
  const context = await browser.newContext(options)
  return { context, page: await context.newPage() }
}

/** Ruta + query de una URL absoluta mostrada en `.qr-card__url`. */
function pathOf(url: string): string {
  const u = new URL(url)
  return `${u.pathname}${u.search}`
}

async function qrUrl(tv: Page, title: 'Presentador' | 'Jugadores'): Promise<string> {
  const card = tv.getByRole('region', { name: `QR ${title}` })
  const url = card.locator('.qr-card__url')
  await expect(url).toHaveText(/^http:\/\/.+/)
  return (await url.textContent())!.trim()
}

async function joinAs(player: Page, joinPath: string, name: string, teamName: string, code: string) {
  await player.goto(joinPath)
  await expect(player.getByRole('heading', { name: `Sala ${code}` })).toBeVisible()
  await player.getByLabel('Tu nombre').fill(name)
  await player.getByRole('button', { name: teamName }).click()
  await expect(player.getByRole('button', { name: teamName })).toHaveAttribute('aria-pressed', 'true')
  await player.getByRole('button', { name: 'Entrar' }).click()
  await expect(player).toHaveURL(new RegExp(`/play/${code}$`))
  await expect(player.getByText('Espera a que el presentador inicie la partida…')).toBeVisible()
}

/** Textos de las respuestas aún ocultas, leídos del celular del presentador. */
async function hiddenAnswers(host: Page): Promise<string[]> {
  const buttons = host.getByRole('region', { name: 'Respuestas' }).getByRole('button', { name: /^Revelar / })
  return (await buttons.locator('.answer__text').allTextContents()).map((t) => t.trim()).filter(Boolean)
}

/** AC-25: ningún texto de respuesta oculta aparece en la página. */
async function expectNoHiddenAnswers(page: Page, hidden: string[]) {
  const body = await page.locator('body').innerText()
  for (const text of hidden) expect(body, `"${text}" no debe verse antes de revelarse`).not.toContain(text)
}

async function bote(host: Page): Promise<number> {
  // spec 007 AC-112 · spec 008 AC-120: ficha "Revelados"
  const txt = await host.locator('.host__stats [data-stat="revelados"] dd').textContent()
  return Number(/(\d+)/.exec(txt ?? '')?.[1] ?? NaN)
}

async function scoreOnTv(tv: Page, team: string): Promise<number> {
  return Number((await tv.getByLabel(`Marcador de ${team}`).textContent())?.trim())
}

test.describe('T601 partida completa: TV + presentador + 2 jugadores', () => {
  test.skip(({ browserName }) => browserName !== 'chromium', 'contextos emulados en Chromium')

  test('AC-29…AC-34 · AC-05 · AC-08 · AC-09 · AC-13…AC-25 una partida de 3 rondas', async ({ browser }, testInfo) => {
    test.skip(testInfo.project.name !== 'presenter-desktop', 'la prueba abre sus propios contextos; basta un proyecto')
    test.setTimeout(120_000)

    const tv = await open(browser, { viewport: { width: 1920, height: 1080 } })
    const host = await open(browser, { ...devices['Pixel 7'] })
    const p1 = await open(browser, { ...devices['Pixel 7'] })
    const p2 = await open(browser, { ...iPhone })

    try {
      // --- AC-02 / AC-29 crear sala y abrir la pantalla de inicio
      // T822 (spec 003): la sala se crea desde la pantalla de título (AJUSTES → EMPEZAR), sin nombres de equipo.
      // T727: desde la spec 002 el temporizador viene activo (10 s desde la spec 010) con X automática; esta prueba no es del temporizador.
      const code = await createRoomFromTitle(tv.page, { rounds: 3, turnSeconds: 0 })
      expect(code).toMatch(ROOM_CODE)
      await expect(logo(tv.page)).toBeVisible() // AC-61 logotipo en la cabecera de /screen

      // AC-68: el código va dentro de la URL del QR (ya no en texto grande).
      await expect(tv.page.getByRole('region', { name: 'QR Jugadores' }).locator('.qr-card__url')).toContainText(`/join/${code}`)
      await expect(tv.page.getByRole('region', { name: 'QR Presentador' }).locator('svg')).toBeVisible()
      await expect(tv.page.getByRole('region', { name: 'QR Jugadores' }).locator('svg')).toBeVisible()

      // --- AC-03 / AC-30 URLs de los QR
      const hostUrl = await qrUrl(tv.page, 'Presentador')
      const joinUrl = await qrUrl(tv.page, 'Jugadores')
      expect(hostUrl).toMatch(new RegExp(`^http://[^/]+:\\d+/host/${code}\\?claim=.+`))
      expect(joinUrl).toMatch(new RegExp(`^http://[^/]+:\\d+/join/${code}$`))

      // --- AC-30 el celular del presentador canjea el QR; AC-32 la TV lo refleja
      await host.page.goto(pathOf(hostUrl))
      await expect(host.page).toHaveURL(new RegExp(`/host/${code}$`)) // el claim se quita de la URL
      await expect(host.page.getByRole('heading', { name: `Sala ${code}` })).toBeVisible()
      await expect(host.page.getByText('Sala de espera')).toBeVisible()
      await expect(tv.page.getByText('Presentador conectado ✓')).toBeVisible()

      // --- AC-30 el mismo QR ya no sirve en otro dispositivo (un solo uso)
      const intruder = await open(browser, { ...devices['Pixel 7'] })
      await intruder.page.goto(pathOf(hostUrl))
      await expect(intruder.page.getByRole('alert')).toBeVisible()
      await expect(intruder.page.getByRole('button', { name: 'Iniciar partida' })).toHaveCount(0)
      await intruder.context.close()

      // --- AC-10 sin jugadores no se puede iniciar
      await expect(host.page.getByRole('button', { name: 'Iniciar partida' })).toBeDisabled()

      // --- AC-05 / AC-08 dos jugadores se unen a equipos distintos; la TV los ve en vivo
      await joinAs(p1.page, pathOf(joinUrl), 'Lupita', TEAM_A, code)
      await joinAs(p2.page, pathOf(joinUrl), 'Pancho', TEAM_B, code)
      const lobby = tv.page.getByRole('region', { name: 'Jugadores por equipo' })
      await expect(lobby.getByRole('list', { name: `Jugadores de ${TEAM_A}` })).toContainText('Lupita', { timeout: 1_000 })
      await expect(lobby.getByRole('list', { name: `Jugadores de ${TEAM_B}` })).toContainText('Pancho', { timeout: 1_000 })
      await expect(tv.page.getByRole('region', { name: 'QR Jugadores' })).toBeVisible() // AC-31

      // --- AC-09 recargar conserva la identidad del jugador
      await p1.page.reload()
      await expect(p1.page).toHaveURL(new RegExp(`/play/${code}$`))
      await expect(p1.page.getByText('Lupita', { exact: true })).toBeVisible()

      // --- AC-11 / AC-34 iniciar: la TV pasa al tablero en la misma ruta
      await host.page.getByRole('button', { name: 'Iniciar partida' }).click()
      await expect(tv.page).toHaveURL(new RegExp(`/screen/${code}$`))
      await expect(tv.page.getByText('Ronda 1 de 3')).toBeVisible()
      await expect(tv.page.getByRole('region', { name: 'QR Jugadores' })).toHaveCount(0)
      await expect(tv.page.getByRole('list', { name: 'Respuestas' })).toBeVisible()

      // ================= Ronda 1: Lupita gana el buzzer, Rojo juega, robo fallido
      // AC-13 los designados ven el buzzer
      const buzzer = (p: Page) => p.getByRole('button', { name: 'Buzzer: presiona para responder primero' })
      await expect(buzzer(p1.page)).toBeVisible()
      await expect(buzzer(p2.page)).toBeVisible()

      // AC-25 los celulares y la TV no muestran respuestas ocultas
      const hidden1 = await hiddenAnswers(host.page)
      expect(hidden1.length).toBeGreaterThanOrEqual(3)
      await expectNoHiddenAnswers(p1.page, hidden1)
      await expectNoHiddenAnswers(p2.page, hidden1)
      await expectNoHiddenAnswers(tv.page, hidden1)

      // AC-14 primer buzz gana; todos lo ven
      await buzzer(p1.page).click()
      await expect(p1.page.getByText('¡Ganaste el buzzer! Di tu respuesta en voz alta.')).toBeVisible()
      await expect(p2.page.getByText('Lupita ganó el buzzer.')).toBeVisible({ timeout: 1_000 })
      await expect(tv.page.getByText('¡Lupita ganó el buzzer!')).toBeVisible({ timeout: 1_000 })
      await expect(host.page.getByText('Ganó el buzzer:')).toContainText('Lupita')
      await expect(buzzer(p2.page)).toHaveCount(0)

      // AC-15 / spec 006 AC-92 el presentador revela la #1 y Rojo se queda con el control
      const revealButtons = host.page.getByRole('region', { name: 'Respuestas' }).getByRole('button', { name: /^Revelar / })
      await revealButtons.first().click()
      await expect(tv.page.locator('.slot--revealed')).toHaveCount(1)
      // spec 006 AC-92: la #1 del equipo del buzzer le da el control sin "Asignar control"

      // AC-24 cada celular muestra su estado
      await expect(p1.page.getByText('¡Tu equipo responde!')).toBeVisible()
      await expect(p2.page.getByText('Responde el otro equipo. Prepárense para robar.')).toBeVisible()
      await expect(tv.page.getByText(`Responde ${TEAM_A}`)).toBeVisible()

      // AC-16/AC-17 revelar otra suma al bote, no al marcador
      await revealButtons.first().click()
      await expect(tv.page.locator('.slot--revealed')).toHaveCount(2)
      // El presentador y la TV reciben el snapshot por separado: se espera a que ambos muestren el mismo bote.
      const tvPot = tv.page.getByLabel('Puntos de la ronda') // spec 005 AC-87: sin la etiqueta "Bote"
      await expect.poll(async () => (await bote(host.page)) === Number(await tvPot.textContent())).toBe(true)
      const pot1 = await bote(host.page)
      expect(pot1).toBeGreaterThan(0)
      await expect(tvPot).toHaveText(String(pot1))
      expect(await scoreOnTv(tv.page, TEAM_A)).toBe(0)

      // AC-18 tres X → robo. T727: el botón dice "X para <equipo en turno>" (spec 002 AC-45) y la TV muestra
      // las X de cada equipo (AC-44) en lugar del contador único "Strikes: n de 3".
      const strike = host.page.getByRole('button', { name: /X para / })
      for (let i = 1; i <= 3; i++) {
        await strike.click()
        await expect(tv.page.getByLabel(`X de ${TEAM_A}: ${i}`)).toBeVisible()
      }
      await expect(p2.page.getByText('¡Robo! Tu equipo tiene una sola oportunidad.')).toBeVisible()
      await expect(p1.page.getByText('El otro equipo intenta robar el bote.')).toBeVisible()

      // AC-19 robo fallido → bote para Rojo; AC-20 tablero completo y marcador
      await strike.click()
      await expect(host.page.getByText('Fin de la ronda', { exact: true })).toBeVisible()
      expect(await bote(host.page)).toBe(pot1)
      await expect(tv.page.getByText(`Fin de la ronda: ${TEAM_A} se lleva ${pot1} puntos`)).toBeVisible()
      await expect(tv.page.locator('.slot--revealed')).toHaveCount(hidden1.length)
      await expect(tv.page.getByLabel(`Marcador de ${TEAM_A}`)).toHaveText(String(pot1))
      await expect(p1.page.getByText('Fin de la ronda')).toBeVisible()

      // ================= Ronda 2 (×2): Pancho gana el buzzer, Verde revela todo
      await host.page.getByRole('button', { name: 'Siguiente ronda' }).click()
      await expect(tv.page.getByText('Ronda 2 de 3')).toBeVisible()
      await expect(tv.page.getByLabel('Puntos ×2')).toBeVisible() // AC-21
      await buzzer(p2.page).click()
      await expect(tv.page.getByText('¡Pancho ganó el buzzer!')).toBeVisible()
      await revealButtons.first().click()
      // spec 006 AC-92: la #1 del equipo del buzzer le da el control sin "Asignar control"
      await expect(p2.page.getByText('¡Tu equipo responde!')).toBeVisible()
      // AC-18 revelar todas termina la ronda (los botones de revelar se deshabilitan al salir de PLAYING)
      await expect(host.page.getByText('Ronda en juego')).toBeVisible()
      while (await host.page.getByText('Ronda en juego').isVisible()) {
        const before = await revealButtons.count()
        await revealButtons.first().click()
        await expect(revealButtons).toHaveCount(before - 1)
      }
      await expect(host.page.getByText('Fin de la ronda', { exact: true })).toBeVisible()
      const pot2 = await bote(host.page)
      const won2 = pot2 * 2 // AC-20/AC-21 bote × multiplicador
      await expect(tv.page.getByText(`Fin de la ronda: ${TEAM_B} se lleva ${won2} puntos`)).toBeVisible()
      await expect(tv.page.getByLabel(`Marcador de ${TEAM_B}`)).toHaveText(String(won2))

      // ================= Ronda 3 (×3, última): Lupita gana, robo acertado de Verde
      await host.page.getByRole('button', { name: 'Siguiente ronda' }).click()
      await expect(tv.page.getByText('Ronda 3 de 3')).toBeVisible()
      await expect(tv.page.getByLabel('Puntos ×3')).toBeVisible() // AC-21
      await buzzer(p1.page).click()
      await expect(tv.page.getByText('¡Lupita ganó el buzzer!')).toBeVisible()
      await revealButtons.first().click()
      // spec 006 AC-92: la #1 del equipo del buzzer le da el control sin "Asignar control"
      for (let i = 0; i < 3; i++) await strike.click()
      await expect(p2.page.getByText('¡Robo! Tu equipo tiene una sola oportunidad.')).toBeVisible()
      await revealButtons.first().click() // Verde acierta el robo (AC-19)
      await expect(host.page.getByText('Fin de la ronda', { exact: true })).toBeVisible()
      const pot3 = await bote(host.page)
      await expect(tv.page.getByText(`Fin de la ronda: ${TEAM_B} se lleva ${pot3 * 3} puntos`)).toBeVisible()
      await expect(tv.page.getByLabel(`Marcador de ${TEAM_B}`)).toHaveText(String(won2 + pot3 * 3))

      // ================= AC-22 fin de partida
      await host.page.getByRole('button', { name: 'Ver resultado final' }).click()
      const scoreA = pot1
      const scoreB = won2 + pot3 * 3
      const expected = scoreA === scoreB ? '¡Empate!' : `¡Gana ${scoreA > scoreB ? TEAM_A : TEAM_B}!`
      await expect(tv.page.getByRole('status').getByText(expected)).toBeVisible()
      // spec 007 AC-116: cada celular celebra (o consuela) con los puntos finales
      const phoneText = (mine: number, theirs: number) => (mine === theirs ? '¡Empate!' : mine > theirs ? '¡Ganaron!' : '¡Buen juego!')
      await expect(p1.page.getByRole('status', { name: 'Resultado final' })).toContainText(phoneText(scoreA, scoreB))
      await expect(p2.page.getByRole('status', { name: 'Resultado final' })).toContainText(phoneText(scoreB, scoreA))
      await expect(tv.page.getByLabel(`${TEAM_B}: ${scoreB} puntos`)).toBeVisible() // AC-114 marcador final
      await expect(host.page.getByText(expected)).toBeVisible()

      // ================= AC-23 revancha: vuelve a LOBBY con los mismos jugadores
      await host.page.getByRole('button', { name: 'Revancha' }).click()
      await expect(host.page.getByText('Sala de espera')).toBeVisible()
      await expect(tv.page.getByRole('region', { name: 'QR Jugadores' })).toBeVisible()
      await expect(lobby.getByRole('list', { name: `Jugadores de ${TEAM_A}` })).toContainText('Lupita')
      await expect(lobby.getByRole('list', { name: `Jugadores de ${TEAM_B}` })).toContainText('Pancho')
      await expect(p1.page.getByText('Espera a que el presentador inicie la partida…')).toBeVisible()
    } finally {
      await Promise.all([tv, host, p1, p2].map((a) => a.context.close()))
    }
  })

  test('AC-33 la TV genera un QR de presentador nuevo e invalida el celular anterior', async ({ browser }, testInfo) => {
    test.skip(testInfo.project.name !== 'presenter-desktop', 'la prueba abre sus propios contextos; basta un proyecto')

    const tv = await open(browser, { viewport: { width: 1920, height: 1080 } })
    const oldPhone = await open(browser, { ...devices['Pixel 7'] })
    const newPhone = await open(browser, { ...iPhone })
    try {
      const code = await createRoomFromTitle(tv.page)

      const first = await qrUrl(tv.page, 'Presentador')
      await oldPhone.page.goto(pathOf(first))
      await expect(oldPhone.page.getByText('Sala de espera')).toBeVisible()
      await expect(tv.page.getByText('Presentador conectado ✓')).toBeVisible()

      await tv.page.getByRole('button', { name: 'Nuevo QR de presentador' }).click()
      await expect(tv.page.getByText('Presentador conectado ✓')).toHaveCount(0)
      // La TV recibe el nuevo QR y el snapshot por caminos distintos: se espera a que la URL cambie.
      await expect.poll(() => qrUrl(tv.page, 'Presentador')).not.toBe(first)
      const second = await qrUrl(tv.page, 'Presentador')

      // El celular anterior pierde la sesión (su presenterToken → 403).
      await oldPhone.page.reload()
      await expect(oldPhone.page.getByText('Tu sesión de presentador ya no es válida. Escanea el QR de presentador de la pantalla.')).toBeVisible()

      await newPhone.page.goto(pathOf(second))
      await expect(newPhone.page.getByRole('heading', { name: `Sala ${code}` })).toBeVisible()
      await expect(newPhone.page.getByText('Sala de espera')).toBeVisible()
      await expect(tv.page.getByText('Presentador conectado ✓')).toBeVisible()
    } finally {
      await Promise.all([tv, oldPhone, newPhone].map((a) => a.context.close()))
    }
  })

  test('AC-05 · AC-06 errores claros al unirse', async ({ browser }, testInfo) => {
    test.skip(testInfo.project.name !== 'presenter-desktop', 'la prueba abre sus propios contextos; basta un proyecto')

    const tv = await open(browser, { viewport: { width: 1920, height: 1080 } })
    const p1 = await open(browser, { ...devices['Pixel 7'] })
    const p2 = await open(browser, { ...iPhone })
    try {
      const code = await createRoomFromTitle(tv.page)
      const joinPath = pathOf(await qrUrl(tv.page, 'Jugadores'))

      await joinAs(p1.page, joinPath, 'Lupita', TEAM_A, code)

      // AC-05 validación de longitud en el cliente
      await p2.page.goto(joinPath)
      await p2.page.getByLabel('Tu nombre').fill('L')
      await p2.page.getByRole('button', { name: TEAM_B }).click()
      await expect(p2.page.getByRole('button', { name: 'Entrar' })).toBeDisabled()

      // AC-06 nombre repetido sin distinguir mayúsculas
      await p2.page.getByLabel('Tu nombre').fill('LUPITA')
      await p2.page.getByRole('button', { name: 'Entrar' }).click()
      await expect(p2.page.getByLabel('Tu nombre')).toHaveAttribute('aria-invalid', 'true')
      await expect(p2.page).toHaveURL(new RegExp(`/join/${code}$`))
    } finally {
      await Promise.all([tv, p1, p2].map((a) => a.context.close()))
    }
  })
})
