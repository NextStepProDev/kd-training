import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import commonPl from './locales/pl/common.json'
import authPl from './locales/pl/auth.json'
import settingsPl from './locales/pl/settings.json'
import errorsPl from './locales/pl/errors.json'
import adminPl from './locales/pl/admin.json'
import accountPl from './locales/pl/account.json'
import marketingPl from './locales/pl/marketing.json'
import calendarPl from './locales/pl/calendar.json'

i18n
  .use(initReactI18next)
  .init({
    resources: {
      pl: { common: commonPl, auth: authPl, settings: settingsPl, errors: errorsPl, admin: adminPl, account: accountPl, marketing: marketingPl, calendar: calendarPl },
    },
    lng: 'pl',
    fallbackLng: 'pl',
    defaultNS: 'common',
    ns: ['common', 'auth', 'settings', 'errors', 'admin', 'account', 'marketing', 'calendar'],
    interpolation: { escapeValue: false },
  })

export default i18n
