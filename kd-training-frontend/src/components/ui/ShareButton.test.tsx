import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ShareButton } from './ShareButton'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'share.label': 'Udostępnij',
        'share.copyLink': 'Kopiuj link',
        'share.copied': 'Skopiowano!',
      }
      return translations[key] ?? key
    },
  }),
}))

describe('ShareButton', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('should render share button', () => {
    render(<ShareButton url="/treningi" title="Trening" />)
    expect(screen.getByRole('button', { name: 'Udostępnij' })).toBeInTheDocument()
  })

  it('should open dropdown on click', async () => {
    const user = userEvent.setup()
    render(<ShareButton url="/treningi" title="Trening" />)

    await user.click(screen.getByRole('button', { name: 'Udostępnij' }))

    expect(screen.getByText('Facebook')).toBeInTheDocument()
    expect(screen.getByText('WhatsApp')).toBeInTheDocument()
    expect(screen.getByText('Kopiuj link')).toBeInTheDocument()
  })

  it('should close dropdown on second click', async () => {
    const user = userEvent.setup()
    render(<ShareButton url="/treningi" title="Trening" />)

    const button = screen.getByRole('button', { name: 'Udostępnij' })
    await user.click(button)
    expect(screen.getByText('Facebook')).toBeInTheDocument()

    await user.click(button)
    expect(screen.queryByText('Facebook')).not.toBeInTheDocument()
  })

  it('should open Facebook share in new window', async () => {
    const windowOpen = vi.spyOn(window, 'open').mockImplementation(() => null)
    const user = userEvent.setup()
    render(<ShareButton url="/treningi" title="Trening" />)

    await user.click(screen.getByRole('button', { name: 'Udostępnij' }))
    await user.click(screen.getByText('Facebook'))

    expect(windowOpen).toHaveBeenCalledWith(
      expect.stringContaining('facebook.com/sharer'),
      '_blank',
      expect.any(String)
    )
  })

  it('should open WhatsApp share', async () => {
    const windowOpen = vi.spyOn(window, 'open').mockImplementation(() => null)
    const user = userEvent.setup()
    render(<ShareButton url="/treningi" title="Trening" />)

    await user.click(screen.getByRole('button', { name: 'Udostępnij' }))
    await user.click(screen.getByText('WhatsApp'))

    expect(windowOpen).toHaveBeenCalledWith(
      expect.stringContaining('wa.me'),
      '_blank'
    )
  })

  it('should show copy link option in dropdown', async () => {
    const user = userEvent.setup()
    render(<ShareButton url="/treningi" title="Trening" />)

    await user.click(screen.getByRole('button', { name: 'Udostępnij' }))

    expect(screen.getByText('Kopiuj link')).toBeInTheDocument()
  })

  /**
   * The trigger has to say whether the menu is open. Without aria-expanded a screen reader announces
   * the same "Udostępnij, button" whether the menu is showing or not, so there is no way to tell that
   * pressing it did anything.
   */
  it('should announce whether the menu is open', async () => {
    const user = userEvent.setup()
    render(<ShareButton url="/treningi" title="Trening" />)
    const button = screen.getByRole('button', { name: 'Udostępnij' })

    expect(button).toHaveAttribute('aria-expanded', 'false')

    await user.click(button)

    expect(button).toHaveAttribute('aria-expanded', 'true')
  })

  /**
   * Escape closes the menu and gives the focus back. Without the second half the focus lands on
   * <body> and the next Tab restarts at the top of the page — the menu is gone and so is the
   * reader's place in it.
   */
  it('should return focus to the trigger when closed with Escape', async () => {
    const user = userEvent.setup()
    render(<ShareButton url="/treningi" title="Trening" />)
    const button = screen.getByRole('button', { name: 'Udostępnij' })

    await user.click(button)
    // Move INTO the menu first. Without this the focus never leaves the trigger — clicking a button
    // focuses it — so the assertion below would hold with or without the fix, which is a test that
    // cannot fail.
    await user.tab()
    expect(button).not.toHaveFocus()

    await user.keyboard('{Escape}')

    expect(screen.queryByText('Facebook')).not.toBeInTheDocument()
    expect(button).toHaveFocus()
  })
})
