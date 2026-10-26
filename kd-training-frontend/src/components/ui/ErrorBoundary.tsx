import { Component, type ErrorInfo, type ReactNode } from 'react'
import i18n from '../../i18n'
import { Button } from './Button'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    if (import.meta.env.DEV) {
      console.error('ErrorBoundary caught error:', error, errorInfo)
    }
  }

  render() {
    if (this.state.hasError) {
      const t = i18n.t.bind(i18n)
      return (
        <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
          <div className="bg-surface-900 rounded-xl p-8 max-w-md w-full border border-surface-800 text-center">
            <h2 className="text-xl font-bold text-surface-100 mb-2">
              {t('boundary.title', { ns: 'errors' })}
            </h2>
            <p className="text-surface-400 mb-6">
              {t('boundary.message', { ns: 'errors' })}
            </p>
            <Button
              variant="primary"
              onClick={() => {
                this.setState({ hasError: false, error: null })
                window.location.href = '/'
              }}
            >
              {t('boundary.backHome', { ns: 'errors' })}
            </Button>
          </div>
        </div>
      )
    }

    return this.props.children
  }
}
