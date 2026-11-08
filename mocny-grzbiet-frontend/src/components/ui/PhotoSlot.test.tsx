import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PhotoSlot } from './PhotoSlot'

describe('PhotoSlot', () => {
  it('shows the photo with its description when there is one', () => {
    render(<PhotoSlot src="/images/hero.jpg" alt="Kacper Dąbrowski podczas treningu" />)
    expect(screen.getByRole('img', { name: 'Kacper Dąbrowski podczas treningu' })).toBeInTheDocument()
  })

  // The placeholder is decoration. Announcing it with the photo's description would tell a
  // screen-reader user about a picture of Kacper that is not there.
  it('does not announce a photo that does not exist yet', () => {
    render(<PhotoSlot alt="Kacper Dąbrowski podczas treningu" />)
    expect(screen.queryByRole('img')).toBeNull()
  })
})
