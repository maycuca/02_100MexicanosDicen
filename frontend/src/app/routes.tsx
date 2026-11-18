import type { RouteObject } from 'react-router'
import { TitleScreenPage } from '../features/title/TitleScreenPage'
import { HostPage } from '../features/presenter/HostPage'
import { StartScreenPage } from '../features/screen/StartScreenPage'
import { JoinPage } from '../features/player/JoinPage'
import { PlayPage } from '../features/player/PlayPage'
import { NotFoundPage } from '../shared/ui/NotFoundPage'
import { ThanksPage } from '../features/thanks/ThanksPage'

// plan.md §5 · spec 003: `/` es la pantalla de título (T811)
export const routes: RouteObject[] = [
  { path: '/', element: <TitleScreenPage /> },
  { path: '/screen/:code', element: <StartScreenPage /> },
  { path: '/host/:code', element: <HostPage /> },
  { path: '/join/:code', element: <JoinPage /> },
  { path: '/play/:code', element: <PlayPage /> },
  // spec 007 (AC-113)
  { path: '/gracias', element: <ThanksPage /> },
  { path: '*', element: <NotFoundPage /> },
]
