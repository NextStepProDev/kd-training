import { useTranslation } from 'react-i18next'
import { Button } from './Button'

interface QueryErrorProps {
  error: Error
  onRetry?: () => void
}

export function QueryError({ error, onRetry }: QueryErrorProps) {
  const { t } = useTranslation('errors')
  return (
    <div className="flex flex-col items-center justify-center py-12 px-4">
      <p className="text-surface-400 mb-4">{error.message || t('generic')}</p>
      {onRetry && (
        <Button variant="secondary" onClick={onRetry}>{t('retry')}</Button>
      )}
    </div>
  )
}
