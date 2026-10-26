import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RpeInput } from './RpeInput'

describe('RpeInput', () => {
  it('shouldReportValueWhenUnratedNumberClicked', async () => {
    const onChange = vi.fn()
    render(<RpeInput value={null} onChange={onChange} label="Jak było?" />)

    await userEvent.click(screen.getByRole('button', { name: 'RPE 7' }))

    expect(onChange).toHaveBeenCalledWith(7)
  })

  it('shouldClearValueWhenSelectedNumberClickedAgain', async () => {
    const onChange = vi.fn()
    render(<RpeInput value={7} onChange={onChange} label="Jak było?" />)

    await userEvent.click(screen.getByRole('button', { name: 'RPE 7' }))

    expect(onChange).toHaveBeenCalledWith(null)
  })

  it('shouldSwitchValueWhenOtherNumberClicked', async () => {
    const onChange = vi.fn()
    render(<RpeInput value={7} onChange={onChange} label="Jak było?" />)

    await userEvent.click(screen.getByRole('button', { name: 'RPE 3' }))

    expect(onChange).toHaveBeenCalledWith(3)
  })

  it('shouldMarkOnlySelectedNumberAsPressed', () => {
    render(<RpeInput value={7} onChange={vi.fn()} label="Jak było?" />)

    expect(screen.getByRole('button', { name: 'RPE 7' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'RPE 3' })).toHaveAttribute('aria-pressed', 'false')
  })
})
