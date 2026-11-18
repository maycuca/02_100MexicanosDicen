import { expect, test } from '@playwright/test'
import { createRoomFromTitle, GAME_TITLE_RE, logo, menuItem } from './support/title'

// Prueba de humo (T010; T822: pantalla de título de la spec 003). Requiere el backend en :8080 (crear sala y QR).
test('la página principal carga con el título del juego (AC-55 · AC-61)', async ({ page }) => {
  await page.goto('/')
  await expect(logo(page)).toBeVisible()
  await expect(page).toHaveTitle(GAME_TITLE_RE)
  await expect(menuItem(page, 'EMPEZAR')).toBeVisible()
  await expect(menuItem(page, 'AJUSTES')).toBeVisible()
})

test('un jugador puede abrir la página de unirse', async ({ page }) => {
  await page.goto('/join/KDMZ')
  await expect(page.getByRole('heading', { name: 'Sala KDMZ' })).toBeVisible()
})

test('la pantalla de inicio muestra los QR de presentador y jugadores (AC-29 · AC-57)', async ({ page }) => {
  // Código real emitido por POST /api/rooms (AC-01): 4 letras sin I ni O.
  const code = await createRoomFromTitle(page)
  // AC-68: el código ya no se muestra en texto; va dentro de la URL del QR de jugadores.
  await expect(page.getByRole('region', { name: 'QR Jugadores' }).locator('.qr-card__url')).toContainText(`/join/${code}`)
  await expect(page.getByRole('region', { name: 'QR Presentador' }).locator('svg')).toBeVisible()
  await expect(page.getByRole('region', { name: 'QR Jugadores' }).locator('svg')).toBeVisible()
})
