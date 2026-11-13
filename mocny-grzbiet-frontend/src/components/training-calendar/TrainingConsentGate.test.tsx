import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { createInstance } from 'i18next'
import { I18nextProvider, initReactI18next } from 'react-i18next'
import calendar from '../../locales/pl/calendar.json'
import { CONTACT_EMAIL } from '../../config/site'
import { TrainingConsentGate } from './TrainingConsentGate'

vi.mock('../../api/client', () => ({ authApi: {} }))

/**
 * A real i18next with the real translations, not a mocked t(): the point is the interpolation. A
 * missed `email` argument renders the literal "{{email}}" and a mocked t() would never notice.
 */
async function renderGate() {
  const i18n = createInstance()
  await i18n.use(initReactI18next).init({
    lng: 'pl',
    resources: { pl: { calendar } },
    interpolation: { escapeValue: false },
  })
  render(
    <I18nextProvider i18n={i18n}>
      <MemoryRouter>
        <TrainingConsentGate onAccepted={async () => {}} />
      </MemoryRouter>
    </I18nextProvider>,
  )
}

describe('TrainingConsentGate', () => {
  it('shouldNotPromiseThatOnlyTheCoachSeesThePlanWhenPartnersDoToo', async () => {
    // Found in manual testing: the screen said nobody but the client and the coach can see the plan,
    // while a group training shows the session and the client's first name to the other participants.
    await renderGate()

    const note = screen.getByText(/trener prowadzący/)
    expect(note.textContent).toMatch(/trening(u)? wspóln/)
    expect(note.textContent).toMatch(/imię/)
  })

  it('shouldTellWhereToWriteToWithdrawTheConsent', async () => {
    await renderGate()

    const note = screen.getByText(/Zgodę możesz wycofać/)
    expect(note).toHaveTextContent(CONTACT_EMAIL)
    expect(note.textContent).not.toContain('{{')
  })
})
