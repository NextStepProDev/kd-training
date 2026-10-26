import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminUsers } from './AdminUsers'
import type { AdminUser } from '../../types'

const tMap: Record<string, string> = {
  'users.title': 'Użytkownicy',
  'users.promote': 'Nadaj admina',
  'users.demote': 'Odbierz admina',
  'users.compose': 'Wyślij e-mail',
  'users.emailTitle': 'Wyślij wiadomość',
  'users.search': 'Szukaj',
  'users.nextPage': 'Następna',
  'users.prevPage': 'Poprzednia',
  'users.name': 'Imię i nazwisko',
  'users.email': 'E-mail',
  'users.role': 'Rola',
  'users.created': 'Dołączył',
  'users.phone': 'Telefon',
  'users.detail.back': 'Wróć do listy',
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => tMap[key] ?? key }),
}))

vi.mock('../../api/admin', () => ({
  adminApi: {
    getUsers: vi.fn(),
    getUser: vi.fn(),
    getEvents: vi.fn(),
    deleteEnrollment: vi.fn(),
    adminEnroll: vi.fn(),
    promoteUser: vi.fn(),
    demoteUser: vi.fn(),
    deleteUser: vi.fn(),
    sendUserEmail: vi.fn(),
  },
}))

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}))

let mockUser: { id: string; superAdmin: boolean }
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ user: mockUser }),
}))

const regular: AdminUser = {
  id: 'u1', email: 'jan@test.com', firstName: 'Jan', lastName: 'Kowalski', phone: '123456789',
  role: 'USER', isAdmin: false, superAdmin: false, emailVerified: true,
  marketingConsent: true, isAthlete: false, createdAt: '2026-01-01T00:00:00Z',
}
const plainAdmin: AdminUser = {
  id: 'a1', email: 'admin@test.com', firstName: 'Adam', lastName: 'Adminowski', phone: null,
  role: 'ADMIN', isAdmin: true, superAdmin: false, emailVerified: true,
  marketingConsent: false, isAthlete: false, createdAt: '2026-02-01T00:00:00Z',
}

async function renderPage(users: AdminUser[], meta?: { totalElements?: number; totalPages?: number }) {
  const { adminApi } = await import('../../api/admin')
  vi.mocked(adminApi.getUsers).mockResolvedValue({
    content: users,
    page: 0,
    size: 50,
    totalElements: meta?.totalElements ?? users.length,
    totalPages: meta?.totalPages ?? 1,
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <AdminUsers />
    </QueryClientProvider>
  )
  return adminApi
}

describe('AdminUsers', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUser = { id: 'me', superAdmin: false }
  })

  it('renders the user list', async () => {
    await renderPage([regular, plainAdmin])
    expect(await screen.findByText('Jan Kowalski')).toBeInTheDocument()
    expect(screen.getByText('Adam Adminowski')).toBeInTheDocument()
  })

  it('hides the demote action for a non-super-admin caller', async () => {
    mockUser = { id: 'me', superAdmin: false }
    await renderPage([plainAdmin])
    await screen.findByText('Adam Adminowski')
    expect(screen.queryByText('Odbierz admina')).not.toBeInTheDocument()
  })

  it('shows the demote action for a super-admin caller', async () => {
    mockUser = { id: 'me', superAdmin: true }
    await renderPage([plainAdmin])
    await screen.findByText('Adam Adminowski')
    expect(screen.getByText('Odbierz admina')).toBeInTheDocument()
  })

  it('hides the promote action for a non-super-admin caller', async () => {
    mockUser = { id: 'me', superAdmin: false }
    await renderPage([regular])
    await screen.findByText('Jan Kowalski')
    expect(screen.queryByText('Nadaj admina')).not.toBeInTheDocument()
  })

  it('shows the promote action for a super-admin caller', async () => {
    mockUser = { id: 'me', superAdmin: true }
    await renderPage([regular])
    await screen.findByText('Jan Kowalski')
    expect(screen.getByText('Nadaj admina')).toBeInTheDocument()
  })

  it('opens the email modal when composing a message', async () => {
    const user = userEvent.setup()
    await renderPage([regular])
    await screen.findByText('Jan Kowalski')

    await user.click(screen.getByText('Wyślij e-mail'))

    expect(screen.getByText('Wyślij wiadomość')).toBeInTheDocument()
  })

  it('requests the next page when clicking next', async () => {
    const user = userEvent.setup()
    const adminApi = await renderPage([regular], { totalElements: 60, totalPages: 2 })
    await screen.findByText('Jan Kowalski')

    await user.click(screen.getByText('Następna'))

    await waitFor(() =>
      expect(adminApi.getUsers).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
    )
  })

  it('opens the user profile when clicking a name', async () => {
    const user = userEvent.setup()
    const adminApi = await renderPage([regular])
    vi.mocked(adminApi.getUser).mockResolvedValue({
      ...regular, preferredLanguage: 'pl', hasPassword: true, oauthLinked: false,
      avatarUrl: null,
    })
    await screen.findByText('Jan Kowalski')

    await user.click(screen.getByText('Jan Kowalski'))

    expect(await screen.findByText('Wróć do listy')).toBeInTheDocument()
    expect(adminApi.getUser).toHaveBeenCalledWith('u1')
  })

  it('requests sorted data when clicking a column header', async () => {
    const user = userEvent.setup()
    const adminApi = await renderPage([regular])
    await screen.findByText('Jan Kowalski')

    await user.click(screen.getByText('E-mail'))

    await waitFor(() =>
      expect(adminApi.getUsers).toHaveBeenLastCalledWith(
        expect.objectContaining({ sort: 'email', direction: 'asc' })
      )
    )
  })
})
