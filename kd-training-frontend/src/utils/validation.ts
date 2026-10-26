import i18n from '../i18n'

export function validateName(name: string): string | null {
  if (name.trim().length < 3) {
    return i18n.t('validation.nameTooShort', { ns: 'errors' })
  }
  return null
}

export function validatePhone(phone: string): string | null {
  const normalized = phone.replace(/\s/g, '')
  if (!/^(\d{9}|\+\d{1,4}\d{9})$/.test(normalized)) {
    return i18n.t('validation.phoneInvalid', { ns: 'errors' })
  }
  return null
}

export function validateEmail(email: string): string | null {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
    return i18n.t('validation.emailInvalid', { ns: 'errors' })
  }
  return null
}

export function validatePassword(password: string, confirmPassword: string): string | null {
  if (password !== confirmPassword) {
    return i18n.t('validation.passwordsMismatch', { ns: 'errors' })
  }
  if (password.length < 10) {
    return i18n.t('validation.passwordTooShort', { ns: 'errors' })
  }
  return null
}
