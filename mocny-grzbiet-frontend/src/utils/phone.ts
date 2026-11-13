/**
 * The phone rule, kept free of i18n so any form can import it: nine digits, or + country code and nine
 * digits — the same pattern as the server's phone fields (RegisterRequest, UpdateUserRequest).
 */

/** Spaces are how people type phone numbers; the server stores and checks the digits. */
export function normalizePhone(phone: string): string {
  return phone.replace(/\s/g, '')
}

export function isValidPhone(phone: string): boolean {
  return /^(\d{9}|\+\d{1,4}\d{9})$/.test(normalizePhone(phone))
}
