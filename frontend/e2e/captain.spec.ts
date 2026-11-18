import { devices, expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test'
import { createRoomFromTitle } from './support/title'

/**
 * T822 — Equipos nombrados por los jugadores (spec 003, US-17: AC-62, AC-65, AC-66).
 * TV (1920×1080) + 3 celulares: capitán y otro jugador de Rojo, capitán de Verde.
 * Requiere backend (:8080) y frontend (:5173) arriba: `npm run e2e`.
 */

const TEAM_A = 'Equipo Rojo'
const TEAM_B = 'Equipo Verde'

const { defaultBrowserType: _ignored, ...iPhone } = devices['iPhone 14']

type Actor = { context: BrowserContext; page: Page }

async function open(browser: Browser, options: Parameters<Browser['newContext']>[0]): Promise<Actor> {
  const context = await browser.newContext(options)
  return { context, page: await context.newPage() }
}

async function joinPath(tv: Page): Promise<string> {
  const url = tv.getByRole('region', { name: 'QR Jugadores' }).locator('.qr-card__url')
  await expect(url).toHaveText(/^http:\/\/.+/)
  return new URL((await url.textContent())!.trim()).pathname
}

async function joinAs(player: Page, path: string, name: string, teamName: string, code: string) {
  await player.goto(path)
  await expect(player.getByRole('heading', { name: `Sala ${code}` })).toBeVisible()
  await player.getByLabel('Tu nombre').fill(name)
  await player.getByRole('button', { name: teamName }).click()
  await player.getByRole('button', { name: 'Entrar' }).click()
  await expect(player).toHaveURL(new RegExp(`/play/${code}$`))
  await expect(player.getByText('Espera a que el presentador inicie la partida…')).toBeVisible()
}

/** Campo del capitán para nombrar al equipo (AC-66). */
function teamNameField(player: Page) {
  return player.getByLabel('Ponle nombre a tu equipo')
}

function saveButton(player: Page) {
  return player.getByRole('button', { name: 'Guardar nombre' })
}

test.describe('T822 capitanes y nombres de equipo', () => {
  test.skip(({ browserName }) => browserName !== 'chromium', 'contextos emulados en Chromium')

  test('AC-62 · AC-65 · AC-66 el capitán renombra desde el celular y la TV y los demás celulares lo muestran', async ({ browser }, testInfo) => {
    test.skip(testInfo.project.name !== 'presenter-desktop', 'la prueba abre sus propios contextos; basta un proyecto')
    test.setTimeout(60_000)

    const tv = await open(browser, { viewport: { width: 1920, height: 1080 } })
    const captainA = await open(browser, { ...devices['Pixel 7'] })
    const memberA = await open(browser, { ...iPhone })
    const captainB = await open(browser, { ...devices['Pixel 7'] })
    const late = await open(browser, { ...iPhone })

    try {
      const code = await createRoomFromTitle(tv.page)
      const path = await joinPath(tv.page)

      // AC-66 al unirse, los equipos se muestran con su nombre actual.
      await captainA.page.goto(path)
      await expect(captainA.page.getByRole('button', { name: TEAM_A })).toBeVisible()
      await expect(captainA.page.getByRole('button', { name: TEAM_B })).toBeVisible()

      // AC-62 el primero de cada equipo es su capitán.
      await joinAs(captainA.page, path, 'Lupita', TEAM_A, code)
      await joinAs(memberA.page, path, 'Chuy', TEAM_A, code)
      await joinAs(captainB.page, path, 'Pancho', TEAM_B, code)

      await expect(captainA.page.getByText(`Eres el capitán de ${TEAM_A}`)).toBeVisible()
      await expect(teamNameField(captainA.page)).toBeVisible()
      await expect(saveButton(captainA.page)).toBeVisible()
      await expect(captainB.page.getByText(`Eres el capitán de ${TEAM_B}`)).toBeVisible()

      // AC-66 los demás jugadores no pueden nombrar; ven el nombre actual.
      const memberTeam = memberA.page.getByRole('region', { name: 'Tu equipo' })
      await expect(memberA.page.getByText(/Eres el capitán/)).toHaveCount(0)
      await expect(saveButton(memberA.page)).toHaveCount(0)
      await expect(memberTeam).toContainText(TEAM_A)
      await expect(memberTeam).toContainText('Lupita es el capitán')

      // AC-65 el capitán renombra; TV y celulares lo muestran en ≤ 1 s.
      await teamNameField(captainA.page).fill('Los Tlacuaches')
      await saveButton(captainA.page).click()
      const lobby = tv.page.getByRole('region', { name: 'Jugadores por equipo' })
      await expect(lobby.getByRole('list', { name: 'Jugadores de Los Tlacuaches' })).toContainText('Lupita', { timeout: 1_000 })
      await expect(lobby.getByRole('list', { name: 'Jugadores de Los Tlacuaches' })).toContainText('Chuy')
      await expect(memberTeam).toContainText('Los Tlacuaches', { timeout: 1_000 })
      await expect(captainB.page.getByText('Los Tlacuaches').first()).toBeVisible({ timeout: 1_000 })
      await expect(captainA.page.getByText('Eres el capitán de Los Tlacuaches')).toBeVisible()

      // AC-63/AC-64 el nombre del otro equipo (sin distinguir mayúsculas) no se puede guardar.
      await teamNameField(captainB.page).fill('los tlacuaches')
      await expect(teamNameField(captainB.page)).toHaveAttribute('aria-invalid', 'true')
      await expect(saveButton(captainB.page)).toBeDisabled()
      // Y un nombre válido sí: Verde también se renombra y la TV lo muestra.
      await teamNameField(captainB.page).fill('Las Ajolotas')
      await saveButton(captainB.page).click()
      await expect(captainB.page.getByRole('status').filter({ hasText: '¡Nombre guardado!' })).toBeVisible()
      await expect(lobby.getByRole('list', { name: 'Jugadores de Las Ajolotas' })).toContainText('Pancho', { timeout: 1_000 })

      // AC-66 quien llega después ve el nombre nuevo al unirse.
      await late.page.goto(path)
      await expect(late.page.getByRole('button', { name: 'Los Tlacuaches' })).toBeVisible()
      await expect(late.page.getByRole('button', { name: 'Las Ajolotas' })).toBeVisible()
      await expect(late.page.getByText('Capitán: Lupita')).toBeVisible()

      // El nombre sobrevive a la recarga del capitán (AC-09) y del jugador.
      await memberA.page.reload()
      await expect(memberA.page.getByRole('region', { name: 'Tu equipo' })).toContainText('Los Tlacuaches')
    } finally {
      await Promise.all([tv, captainA, memberA, captainB, late].map((a) => a.context.close()))
    }
  })
})
