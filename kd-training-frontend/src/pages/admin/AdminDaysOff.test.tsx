import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminDaysOff } from './AdminDaysOff'

vi.mock('react-i18next', () => ({
  initReactI18next: { type: '3rdParty', init: () => {} },
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, string>) => opts?.range ? `${key}:${opts.range}` : key,
  }),
}))

vi.mock('../../api/admin', () => ({
  adminApi: {
    getDaysOff: vi.fn(),
    addDayOff: vi.fn(),
    deleteDayOff: vi.fn(),
  },
}))

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}))

const HOLIDAY = { id: 'd1', startDate: '2027-07-05', endDate: '2027-07-11', note: 'Urlop' }

async function renderDaysOff(existing = [HOLIDAY]) {
  const { adminApi } = await import('../../api/admin')
  vi.mocked(adminApi.getDaysOff).mockResolvedValue(existing)
  vi.mocked(adminApi.addDayOff).mockImplementation(async body => ({ id: 'new', note: null, ...body }))
  vi.mocked(adminApi.deleteDayOff).mockResolvedValue(undefined)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><AdminDaysOff /></QueryClientProvider>)
  return adminApi
}

describe('AdminDaysOff', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shouldSendTheRangeAndTheTrimmedNote', async () => {
    const api = await renderDaysOff([])

    fireEvent.change(screen.getByLabelText('dayOff.from'), { target: { value: '2027-07-05' } })
    fireEvent.change(screen.getByLabelText('dayOff.to'), { target: { value: '2027-07-11' } })
    await userEvent.type(screen.getByLabelText('dayOff.note'), '  Urlop ')
    await userEvent.click(screen.getByRole('button', { name: 'dayOff.add' }))

    await waitFor(() => expect(api.addDayOff).toHaveBeenCalledWith(
      { startDate: '2027-07-05', endDate: '2027-07-11', note: 'Urlop' }))
  })

  it('shouldMoveTheEndAlongWhenTheStartPassesIt', async () => {
    await renderDaysOff([])

    fireEvent.change(screen.getByLabelText('dayOff.from'), { target: { value: '2027-07-05' } })
    fireEvent.change(screen.getByLabelText('dayOff.from'), { target: { value: '2027-08-01' } })

    expect(screen.getByLabelText('dayOff.to')).toHaveValue('2027-08-01')
  })

  it('shouldNotSendAnEmptyEndDate', async () => {
    const api = await renderDaysOff([])

    fireEvent.change(screen.getByLabelText('dayOff.to'), { target: { value: '' } })
    await userEvent.click(screen.getByRole('button', { name: 'dayOff.add' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('dayOff.endBeforeStart')
    expect(api.addDayOff).not.toHaveBeenCalled()
  })

  it('shouldDeleteTheChosenDayOff', async () => {
    const api = await renderDaysOff()

    await userEvent.click(await screen.findByRole('button', { name: /^dayOff\.deleteFor:/ }))

    await waitFor(() => expect(api.deleteDayOff).toHaveBeenCalledWith('d1'))
  })

  it('shouldListTheNoteUnderTheDates', async () => {
    await renderDaysOff()
    expect(await screen.findByText('Urlop')).toBeInTheDocument()
  })
})
