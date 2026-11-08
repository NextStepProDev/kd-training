import { describe, expect, it, vi } from 'vitest'
import { adminApi } from '../../api/admin'
import { coachAdapter } from './adapter'

vi.mock('../../api/admin', () => ({
  adminApi: { pastePersonalTraining: vi.fn().mockResolvedValue({}) },
}))

describe('coachAdapter', () => {
  it('pastes into the calendar on screen, not the one the entry came from', async () => {
    // The clipboard survives a switch of client on purpose — copying a session from one person to
    // another is the whole point. Without the target id the server can only fall back to the
    // source's own athlete, which is where every cross-client paste used to land, unnoticed.
    await coachAdapter('basia-id').pasteTraining('t1', '2026-08-10', 'COPY')

    expect(adminApi.pastePersonalTraining).toHaveBeenCalledWith('t1', '2026-08-10', 'COPY', 'basia-id')
  })
})
