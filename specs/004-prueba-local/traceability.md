# Trazabilidad — 004 Prueba local (T913)

Matriz `AC → prueba(s) → nivel → estado`. Contrato de referencia: `specs/001-juego-base/contracts/` **v0.5.0** (sin cambios).

**Última verificación (2026-11-18):** Vitest 118/118 · Playwright 33 pasan / 15 omitidas a propósito · lint y build limpios. El backend no cambió.

Ubicaciones:
- **UNIT** = `frontend/src/features/localtest/localtest.test.tsx`.
- **E2E-L** = `frontend/e2e/localtest.spec.ts`. Usa un solo contexto de navegador con varias pestañas, que comparten `localStorage` igual que un navegador real.

| AC | Prueba(s) | Nivel | Estado |
|---|---|---|---|
| AC-73 | UNIT `AC-73 está apagado por defecto y la bandera se recuerda…` · `AC-73 · AC-74 la fila "Prueba local" enciende el modo…; Restablecer no lo cambia` · E2E-L (`applySettings({ localTest: true })`) | Unit · E2E | ✅ |
| AC-74 | UNIT `AC-73 · AC-74 …aparece el distintivo` | Unit | ✅ |
| AC-75 | UNIT `AC-75 muestra las URL con el origen del navegador, sin QR…` · `AC-75 "Copiar" copia la URL y confirma; "Abrir en pestaña nueva" usa noopener` · `AC-75 con el presentador conectado…` · E2E-L (URL `localhost`, aviso, sin QR, portapapeles real) | Unit · E2E | ✅ |
| AC-76 | UNIT `AC-76 …sessionStorage` · E2E-L (presentador y dos jugadores en pestañas distintas; recargar conserva la sesión) | Unit · E2E | ✅ |
| AC-77 | UNIT `AC-77 una pestaña nueva hereda el modo al cargar` · E2E-L (las pestañas pegadas usan la sesión por pestaña) | Unit · E2E | ✅ |
| AC-78 | UNIT `AC-78 con el modo apagado la sesión sigue en localStorage` · `modo apagado (AC-78) …QR con la IP de la LAN` · todas las suites e2e de las specs 001–003 (modo apagado) | Unit · E2E | ✅ |
