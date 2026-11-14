import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { addDays, format } from 'date-fns'
import { MyAccountPage } from './MyAccountPage'
import type { MyTrainingSummary } from '../types'

const getSummary = vi.fn<() => Promise<MyTrainingSummary>>()
vi.mock('../api/user', () => ({ myTrainingApi: { getSummary: () => getSummary() } }))
vi.mock('../components/seo/Seo', () => ({ Seo: () => null }))
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: {
      id: 'u1', email: 'ania@test.pl', firstName: 'Ania', lastName: 'Kowalska', phone: '500100100',
      isAthlete: true, avatarUrl: null,
    },
  }),
}))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, string>) => (params?.date ? `${key}:${params.date}` : key),
  }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

function renderPage() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter><MyAccountPage /></MemoryRouter>
    </QueryClientProvider>,
  )
}

const iso = (offset: number) => format(addDays(new Date(), offset), 'yyyy-MM-dd')

describe('MyAccountPage — plan tile', () => {
  beforeEach(() => getSummary.mockReset())

  it('shouldNameTheDayOfTheNextTraining', async () => {
    // Found in manual testing: the server worked the date out and the tile never showed it.
    getSummary.mockResolvedValue({ unreadCount: 0, deletedCount: 0, nextTrainingDate: '2030-01-17' })
    renderPage()

    expect(await screen.findByText('my.tileNext:czwartek, 17 stycznia')).toBeInTheDocument()
  })

  it('shouldSayTodayAndTomorrowInWords', async () => {
    getSummary.mockResolvedValue({ unreadCount: 0, deletedCount: 0, nextTrainingDate: iso(1) })
    renderPage()

    expect(await screen.findByText('my.tileNext:my.tileTomorrow')).toBeInTheDocument()
  })

  it('shouldSayNothingWhenNoTrainingIsPlanned', async () => {
    getSummary.mockResolvedValue({ unreadCount: 0, deletedCount: 0, nextTrainingDate: null })
    renderPage()

    await screen.findByText('my.tileTitle')
    expect(screen.queryByText(/my\.tileNext/)).toBeNull()
  })
})
