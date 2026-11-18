import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './app/App'
import './shared/ui/theme.css'
import { installLocalTest } from './features/localtest'

// Spec 004 (AC-77): la pestaña fija al cargar si su sesión es propia (prueba local) o compartida.
installLocalTest()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
