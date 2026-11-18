import { expect, test, type Page } from '@playwright/test'
import { createRoomFromTitle } from './support/title'

/**
 * T912 — Spec 004 "Prueba local" (AC-73…AC-76): sin celulares, todo en pestañas de UN mismo
 * contexto del navegador (comparten localStorage, como un navegador real con varias pestañas).
 * Requiere backend (:8080) y frontend (:5173) arriba: `npm run e2e`.
 */

async function linkOf(tv: Page, card: 'Presentador' | 'Jugadores'): Promise<string> {
  const url = tv.getByRole('region', { name: `QR ${card}` }).getByLabel(`URL de ${card}`)
  await expect(url).toHaveText(/^http:\/\/localhost:\d+\//)
  return (await url.textContent())!.trim()
}

async function joinAs(player: Page, url: string, name: string, team: string, code: string) {
  await player.goto(url)
  await player.getByLabel('Tu nombre').fill(name)
  await player.getByRole('button', { name: team }).click()
  await player.getByRole('button', { name: 'Entrar' }).click()
  await expect(player).toHaveURL(new RegExp(`/play/${code}$`))
  await expect(player.getByText('Espera a que el presentador inicie la partida…')).toBeVisible()
}

test.describe('T912 prueba local con pestañas', () => {
  test.skip(({ browserName }) => browserName !== 'chromium', 'basta Chromium')

  test('AC-75 · AC-76 presentador y dos jugadores en pestañas del mismo navegador', async ({ browser }, testInfo) => {
    test.skip(testInfo.project.name !== 'presenter-desktop', 'la prueba abre su propio contexto; basta un proyecto')
    test.setTimeout(60_000)

    const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } })
    try {
      const tv = await context.newPage()
      const code = await createRoomFromTitle(tv, { localTest: true })

      // AC-75: URL con el origen del navegador, aviso, sin QR.
      await expect(tv.getByRole('note')).toContainText('pega cada URL en una pestaña distinta')
      const hostUrl = await linkOf(tv, 'Presentador')
      const joinUrl = await linkOf(tv, 'Jugadores')
      expect(hostUrl).toMatch(new RegExp(`/host/${code}\\?claim=.+`))
      expect(joinUrl).toMatch(new RegExp(`/join/${code}$`))
      await expect(tv.getByRole('region', { name: 'QR Jugadores' }).locator('svg')).toHaveCount(0)

      // AC-75: "Copiar" pone la URL en el portapapeles.
      await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: new URL(joinUrl).origin })
      const players = tv.getByRole('region', { name: 'QR Jugadores' })
      await players.getByRole('button', { name: 'Copiar' }).click()
      await expect(players.getByRole('status')).toHaveText('¡Copiada!')
      expect(await tv.evaluate(() => navigator.clipboard.readText())).toBe(joinUrl)

      // Presentador en otra pestaña.
      const host = await context.newPage()
      await host.goto(hostUrl)
      await expect(host).toHaveURL(new RegExp(`/host/${code}$`))
      await expect(host.getByText('Sala de espera')).toBeVisible()
      await expect(tv.getByText('Presentador conectado ✓')).toBeVisible()

      // AC-76: dos jugadores distintos en dos pestañas del mismo navegador.
      const lupita = await context.newPage()
      const pancho = await context.newPage()
      await joinAs(lupita, joinUrl, 'Lupita', 'Equipo Rojo', code)
      await joinAs(pancho, joinUrl, 'Pancho', 'Equipo Verde', code)
      await expect(tv.getByRole('list', { name: 'Jugadores de Equipo Rojo' })).toContainText('Lupita')
      await expect(tv.getByRole('list', { name: 'Jugadores de Equipo Verde' })).toContainText('Pancho')

      // AC-76: recargar una pestaña conserva SU sesión.
      await lupita.reload()
      await expect(lupita).toHaveURL(new RegExp(`/play/${code}$`))
      await expect(lupita.getByText('Lupita')).toBeVisible()
      await expect(lupita.getByText('Pancho')).toHaveCount(0)

      // El presentador ya puede iniciar con un jugador por equipo.
      await expect(host.getByRole('button', { name: 'Iniciar partida' })).toBeEnabled()
    } finally {
      await context.close()
    }
  })
})
