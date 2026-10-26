import './i18n'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider, keepPreviousData } from '@tanstack/react-query'
import { HelmetProvider } from 'react-helmet-async'
import { AuthProvider } from './context/AuthContext'
import { ToastProvider } from './context/ToastContext'
import App from './App'
import { shouldRetryQuery } from './utils/queryFreshness'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,
      // One retry, except on 429 — see shouldRetryQuery: retrying into a full bucket cannot work
      // and only makes the burst worse.
      retry: shouldRetryQuery,
      // Smooth query-key transitions (pagination, month/filter switches): keep the previous
      // result on screen while the next one loads instead of blanking to a spinner. Opt out
      // per-query with `placeholderData: undefined` where the key change means a different
      // entity (e.g. switching category tabs), so stale data of the wrong thing isn't shown —
      // or with `keepWithinEntity` (utils/queryEntity) where the key carries both, an entity
      // and a page of it, and only the page may be smoothed over.
      placeholderData: keepPreviousData,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <HelmetProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <ToastProvider>
            <AuthProvider>
              <App />
            </AuthProvider>
          </ToastProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </HelmetProvider>
  </StrictMode>,
)
