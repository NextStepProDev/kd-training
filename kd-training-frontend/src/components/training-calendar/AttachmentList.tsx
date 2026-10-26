import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ExternalLink, Play } from 'lucide-react'
import type { Attachment } from '../../types'

/**
 * Materials on a training.
 * <p>
 * Videos render as a poster that swaps to an iframe on click: a plan with three clips would
 * otherwise load three YouTube players nobody asked for.
 */
export function AttachmentList({ attachments }: { attachments: Attachment[] }) {
  const { t } = useTranslation('calendar')
  if (attachments.length === 0) return null

  return (
    <div className="space-y-2">
      <h4 className="text-sm font-medium text-surface-300">{t('materials.title')}</h4>
      <ul className="space-y-2">
        {attachments.map(attachment => (
          <li key={attachment.id}>
            {attachment.kind === 'VIDEO' && attachment.embedUrl
              ? <VideoAttachment attachment={attachment} playLabel={t('materials.play')} />
              : <LinkAttachment attachment={attachment} />}
          </li>
        ))}
      </ul>
    </div>
  )
}

function VideoAttachment({ attachment, playLabel }: { attachment: Attachment; playLabel: string }) {
  const [playing, setPlaying] = useState(false)
  const label = attachment.label ?? attachment.videoName ?? ''

  if (playing) {
    return (
      <div className="overflow-hidden rounded-lg border border-surface-800">
        <div className="aspect-video">
          <iframe
            src={attachment.embedUrl!}
            title={label}
            className="h-full w-full"
            loading="lazy"
            allow="accelerometer; encrypted-media; picture-in-picture"
            allowFullScreen
          />
        </div>
        <p className="px-2 py-1.5 text-sm text-surface-200">{label}</p>
      </div>
    )
  }

  return (
    <button
      type="button"
      onClick={() => setPlaying(true)}
      aria-label={`${playLabel}: ${label}`}
      className="group flex w-full items-center gap-3 rounded-lg border border-surface-800 bg-surface-800/50 p-2 text-left transition-colors hover:border-primary-600/50"
    >
      <span className="relative shrink-0">
        {attachment.thumbnailUrl && (
          <img src={attachment.thumbnailUrl} alt="" loading="lazy"
            className="h-12 w-20 rounded object-cover" />
        )}
        <span className="absolute inset-0 flex items-center justify-center">
          <Play className="h-5 w-5 text-white drop-shadow" />
        </span>
      </span>
      <span className="min-w-0 truncate text-sm text-surface-100">{label}</span>
    </button>
  )
}

function LinkAttachment({ attachment }: { attachment: Attachment }) {
  return (
    <a
      href={attachment.url ?? '#'}
      target="_blank"
      rel="noopener noreferrer"
      className="flex items-center gap-2 rounded-lg border border-surface-800 bg-surface-800/50 px-3 py-2 text-sm text-surface-200 transition-colors hover:border-primary-600/50 hover:text-primary-300"
    >
      <ExternalLink className="h-4 w-4 shrink-0" />
      <span className="truncate">{attachment.label ?? attachment.url}</span>
    </a>
  )
}
