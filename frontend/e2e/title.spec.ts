import { expect, test, type Page } from '@playwright/test'
import {
  applySettings,
  createRoomFromTitle,
  expectSelected,
  GAME_TITLE_RE,
  logo,
  menuItem,
  openTitle,
  readSettings,
  ROOM_URL,
  settingsDialog,
} from './support/title'

/**
 * T822 — Pantalla de título estilo videojuego (spec 003-pantalla-de-titulo, US-15/US-16).
 * Requiere backend (:8080) y frontend (:5173) arriba: `npm run e2e`.
 */

/** Valores por defecto del contrato v0.5.0 (CreateRoomRequest). */
const DEFAULTS = { rounds: 5, targetScore: 300, turnSeconds: 10, autoStrikeOnTimeout: true } // spec 010, AC-134

type RoomSnapshot = { totalRounds: number; targetScore: number; turnSeconds: number; autoStrikeOnTimeout: boolean; teams: { id: string; name: string }[] }

/** Snapshot público de la sala por el proxy de Vite (GET /api/rooms/{code}). */
async function roomState(page: Page, code: string): Promise<RoomSnapshot> {
  const res = await page.request.get(`/api/rooms/${code}`)
  expect(res.ok()).toBe(true)
  return (await res.json()) as RoomSnapshot
}

test.describe('T822 pantalla de título', () => {
  test('AC-55 · AC-61 logotipo, menú EMPEZAR/AJUSTES y ningún campo de formulario visible', async ({ page }) => {
    await openTitle(page)
    await expect(page).toHaveTitle(GAME_TITLE_RE)
    await expect(logo(page)).toBeVisible()
    await expect(menuItem(page, 'EMPEZAR')).toBeVisible()
    await expect(menuItem(page, 'AJUSTES')).toBeVisible()
    await expect(page.locator('input:visible, select:visible, textarea:visible')).toHaveCount(0)
    await expect(page.getByText('Cien Mexicanos', { exact: true })).toHaveCount(0)
    // Sin nombres de equipo en la pantalla de título (US-17).
    await expect(page.getByLabel(/Nombre del equipo/)).toHaveCount(0)
  })

  test('AC-56 al cargar la selección está en EMPEZAR; ↓/↑ y S/W la mueven', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'presenter-desktop', 'teclado físico: escritorio')
    await openTitle(page)
    await expectSelected(menuItem(page, 'EMPEZAR'))

    await page.keyboard.press('ArrowDown')
    await expectSelected(menuItem(page, 'AJUSTES'))
    await page.keyboard.press('ArrowUp')
    await expectSelected(menuItem(page, 'EMPEZAR'))
    await page.keyboard.press('s')
    await expectSelected(menuItem(page, 'AJUSTES'))
    await page.keyboard.press('w')
    await expectSelected(menuItem(page, 'EMPEZAR'))
  })

  test('AC-56 · AC-58 ↓ + Enter abre AJUSTES; Esc lo cierra; Espacio también activa', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'presenter-desktop', 'teclado físico: escritorio')
    await openTitle(page)
    await page.keyboard.press('ArrowDown')
    await expectSelected(menuItem(page, 'AJUSTES'))
    await page.keyboard.press('Enter')
    const dialog = settingsDialog(page)
    await expect(dialog).toBeVisible()
    await expect(dialog).toHaveAttribute('aria-modal', 'true')

    // Dentro del modal, →/← cambian el valor de la opción enfocada (la primera: Rondas).
    await expect(dialog.getByRole('spinbutton', { name: 'Rondas' })).toBeFocused()
    await page.keyboard.press('ArrowRight')
    await expect(dialog.getByRole('spinbutton', { name: 'Rondas' })).toHaveAttribute('aria-valuenow', '6')
    await page.keyboard.press('ArrowLeft')
    await expect(dialog.getByRole('spinbutton', { name: 'Rondas' })).toHaveAttribute('aria-valuenow', '5')

    // Foco atrapado: tabular muchas veces no saca el foco del diálogo.
    for (let i = 0; i < 12; i++) {
      await page.keyboard.press('Tab')
      expect(await dialog.evaluate((d) => d.contains(document.activeElement))).toBe(true)
    }
    for (let i = 0; i < 3; i++) {
      await page.keyboard.press('Shift+Tab')
      expect(await dialog.evaluate((d) => d.contains(document.activeElement))).toBe(true)
    }

    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
    await expectSelected(menuItem(page, 'AJUSTES'))

    // Espacio activa la opción seleccionada.
    await page.keyboard.press('ArrowUp')
    await expectSelected(menuItem(page, 'EMPEZAR'))
    await page.keyboard.press('ArrowDown')
    await expectSelected(menuItem(page, 'AJUSTES'))
    await page.keyboard.press(' ')
    await expect(dialog).toBeVisible()
    await dialog.getByRole('button', { name: /^\s*volver\s*$/i }).click()
    await expect(dialog).toBeHidden()
  })

  test('AC-56 Enter sobre EMPEZAR crea la sala y abre /screen/<código>', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'presenter-desktop', 'teclado físico: escritorio')
    await openTitle(page)
    await expectSelected(menuItem(page, 'EMPEZAR'))
    await page.keyboard.press('Enter')
    await expect(page).toHaveURL(ROOM_URL)
  })

  test('AC-56 el menú funciona con toque', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'player-android', 'pantalla táctil')
    await openTitle(page)
    await menuItem(page, 'AJUSTES').tap()
    await expect(settingsDialog(page)).toBeVisible()
    await settingsDialog(page).getByRole('button', { name: /^\s*volver\s*$/i }).tap()
    await expect(settingsDialog(page)).toBeHidden()
    await menuItem(page, 'EMPEZAR').tap()
    await expect(page).toHaveURL(ROOM_URL)
  })

  test('AC-59 con sonido activado, mover y activar el menú suena; sin interacción ni con sonido apagado, no', async ({ browser }, testInfo) => {
    test.skip(testInfo.project.name !== 'presenter-desktop', 'abre sus propios contextos; basta un proyecto')
    for (const soundOn of [false, true]) {
      const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } })
      await context.addInitScript((on: boolean) => {
        try {
          if (on) localStorage.setItem('cm:sound', 'on') // preferencia de AC-52
          else localStorage.removeItem('cm:sound')
        } catch {
          /* sin almacenamiento */
        }
        const w = window as unknown as { __notes: number; AudioContext?: { prototype: AudioContext } }
        w.__notes = 0
        const proto = w.AudioContext?.prototype
        if (!proto) return
        const original = proto.createOscillator
        proto.createOscillator = function (this: AudioContext) {
          w.__notes++
          return original.call(this)
        }
      }, soundOn)
      const page = await context.newPage()
      const notes = () => page.evaluate(() => (window as unknown as { __notes: number }).__notes)
      try {
        await openTitle(page)
        expect(await notes(), 'sin interacción previa no suena nada').toBe(0)
        await page.keyboard.press('ArrowDown')
        await expectSelected(menuItem(page, 'AJUSTES'))
        await page.keyboard.press('Enter')
        await expect(settingsDialog(page)).toBeVisible()
        if (soundOn) await expect.poll(notes).toBeGreaterThan(0)
        else expect(await notes(), 'con el sonido apagado el menú no suena').toBe(0)
      } finally {
        await context.close()
      }
    }
  })

  test('AC-58 los ajustes tienen los valores por defecto del contrato', async ({ page }) => {
    await openTitle(page)
    expect(await readSettings(page)).toEqual(DEFAULTS)
  })

  test('AC-58 · AC-57 los ajustes persisten tras recargar y se aplican al EMPEZAR', async ({ page }) => {
    await openTitle(page)
    await applySettings(page, { rounds: 4, targetScore: 500, turnSeconds: 0 })
    await page.reload()
    await expect(logo(page)).toBeVisible()
    const saved = await readSettings(page)
    expect(saved).toMatchObject({ rounds: 4, targetScore: 500, turnSeconds: 0 })

    await menuItem(page, 'EMPEZAR').click()
    await expect(page).toHaveURL(ROOM_URL)
    const code = new URL(page.url()).pathname.split('/').pop()!
    const state = await roomState(page, code)
    expect(state).toMatchObject({ totalRounds: 4, targetScore: 500, turnSeconds: 0 })
    // AC-57 sin nombres de equipo: el servidor usa los de siempre.
    expect(state.teams.map((t) => t.name).sort()).toEqual(['Equipo Rojo', 'Equipo Verde'])
  })

  test('AC-58 temporizador de 30 s sin X automática se aplica a la sala', async ({ page }) => {
    const code = await createRoomFromTitle(page, { turnSeconds: 30, autoStrikeOnTimeout: false })
    expect(await roomState(page, code)).toMatchObject({ turnSeconds: 30, autoStrikeOnTimeout: false })
  })

  test('AC-57 si crear la sala falla se muestra un aviso y se puede reintentar', async ({ page }) => {
    await openTitle(page)
    let fail = true
    await page.route('**/api/rooms', async (route) => {
      if (route.request().method() !== 'POST') return route.continue()
      if (fail) {
        await new Promise((r) => setTimeout(r, 800))
        return route.fulfill({
          status: 500,
          contentType: 'application/problem+json',
          body: JSON.stringify({ status: 500, title: 'Error', code: 'INTERNAL', detail: 'falla simulada' }),
        })
      }
      return route.continue()
    })

    await menuItem(page, 'EMPEZAR').click()
    // Estado de carga mientras se crea.
    await expect(page.getByRole('button', { name: 'Preparando…' })).toHaveAttribute('aria-busy', 'true')
    const toast = page.getByRole('alert')
    await expect(toast).toContainText('No se pudo empezar')
    await expect(page).toHaveURL(/\/$/)

    fail = false
    await toast.getByRole('button', { name: 'Reintentar' }).click()
    await expect(page).toHaveURL(ROOM_URL)
  })

  test('AC-61 la pantalla de inicio /screen muestra el logotipo y el título del documento', async ({ page }) => {
    await createRoomFromTitle(page)
    await expect(logo(page)).toBeVisible()
    await expect(page).toHaveTitle(GAME_TITLE_RE)
    await expect(page.getByText('Cien Mexicanos', { exact: true })).toHaveCount(0)
  })

  test('AC-60 a 360 px de ancho no hay scroll horizontal y el menú es visible', async ({ browser }, testInfo) => {
    test.skip(testInfo.project.name !== 'presenter-desktop', 'abre su propio contexto; basta un proyecto')
    const context = await browser.newContext({ viewport: { width: 360, height: 740 } })
    const page = await context.newPage()
    try {
      await openTitle(page)
      await expect(menuItem(page, 'AJUSTES')).toBeInViewport()
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
      expect(overflow).toBeLessThanOrEqual(0)
    } finally {
      await context.close()
    }
  })

  test('AC-60 con prefers-reduced-motion no hay animaciones de entrada ni de fondo', async ({ browser }, testInfo) => {
    test.skip(testInfo.project.name !== 'presenter-desktop', 'abre su propio contexto; basta un proyecto')
    const context = await browser.newContext({ reducedMotion: 'reduce', viewport: { width: 1920, height: 1080 } })
    const page = await context.newPage()
    try {
      await openTitle(page)
      // Solo se tolera el resaltado de la opción del menú; el logotipo y el fondo deben estar quietos.
      // Las animaciones de 0.01 ms del tema pueden tardar un cuadro en terminar: se espera por condición.
      await expect
        .poll(() =>
          page.evaluate(() =>
            document
              .getAnimations()
              .filter((a) => a.playState === 'running')
              .map((a) => (a.effect as KeyframeEffect | null)?.target)
              .filter((t): t is Element => t instanceof Element && !t.closest('button, [role="menuitem"]'))
              .map((t) => t.outerHTML.slice(0, 80)),
          ),
        )
        .toEqual([])
    } finally {
      await context.close()
    }
  })
})
