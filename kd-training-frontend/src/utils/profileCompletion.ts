import type { User } from '../types'

export type ProfileField = 'firstName' | 'lastName' | 'phone'

// Profile fields required to enroll in an event (the organizer needs contact details).
// Adding a new required field in the future = appending it here — the completion form
// and enrollment gates will compute the missing fields automatically.
export const REQUIRED_PROFILE_FIELDS: ProfileField[] = ['firstName', 'lastName', 'phone']

/** Returns the list of required profile fields the user has not yet filled in. */
export function getMissingProfileFields(
  user: Pick<User, 'firstName' | 'lastName' | 'phone'> | null | undefined
): ProfileField[] {
  if (!user) return []
  return REQUIRED_PROFILE_FIELDS.filter((field) => !(user[field] ?? '').trim())
}

// Google accounts also land here to finalize the privacy policy consent (GDPR) —
// email/password registration enforces it immediately, OAuth does not. So we show the
// completion screen when required fields are missing OR the privacy policy consent has not yet been given.
export function needsProfileCompletion(
  user: Pick<User, 'firstName' | 'lastName' | 'phone' | 'privacyAccepted'> | null | undefined
): boolean {
  if (!user) return false
  return getMissingProfileFields(user).length > 0 || !user.privacyAccepted
}
