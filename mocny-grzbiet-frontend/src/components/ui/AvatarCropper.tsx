import { useCallback, useState } from 'react'
import Cropper, { type Area } from 'react-easy-crop'
import { ZoomIn, ZoomOut } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Modal } from './Modal'
import { Button } from './Button'
import { getCroppedBlob } from '../../utils/cropImage'

interface AvatarCropperProps {
  /** Object URL of the selected file (crop source). */
  imageSrc: string
  onCancel: () => void
  onSave: (blob: Blob) => Promise<void> | void
}

const MIN_ZOOM = 1
const MAX_ZOOM = 3

export function AvatarCropper({ imageSrc, onCancel, onSave }: AvatarCropperProps) {
  const { t } = useTranslation('settings')
  const [crop, setCrop] = useState({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(1)
  const [croppedAreaPixels, setCroppedAreaPixels] = useState<Area | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const onCropComplete = useCallback((_: Area, areaPixels: Area) => {
    setCroppedAreaPixels(areaPixels)
  }, [])

  const handleSave = async () => {
    if (!croppedAreaPixels) return
    setSaving(true)
    setError(null)
    try {
      const blob = await getCroppedBlob(imageSrc, croppedAreaPixels)
      await onSave(blob)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setSaving(false)
    }
  }

  const clampZoom = (value: number) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, value))

  return (
    <Modal isOpen onClose={saving ? () => {} : onCancel} title={t('avatar.cropTitle')}>
      <div className="space-y-5">
        <p className="text-sm text-surface-400">{t('avatar.cropHint')}</p>

        <div className="relative w-full h-64 sm:h-72 bg-surface-950 rounded-lg overflow-hidden touch-none">
          <Cropper
            image={imageSrc}
            crop={crop}
            zoom={zoom}
            aspect={1}
            cropShape="round"
            showGrid={false}
            minZoom={MIN_ZOOM}
            maxZoom={MAX_ZOOM}
            onCropChange={setCrop}
            onZoomChange={setZoom}
            onCropComplete={onCropComplete}
          />
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setZoom(z => clampZoom(z - 0.2))}
            aria-label={t('avatar.zoomOut')}
            className="p-1.5 text-surface-300 hover:text-surface-100 transition-colors"
          >
            <ZoomOut className="w-5 h-5" />
          </button>
          <input
            type="range"
            min={MIN_ZOOM}
            max={MAX_ZOOM}
            step={0.01}
            value={zoom}
            onChange={e => setZoom(Number(e.target.value))}
            aria-label={t('avatar.zoom')}
            className="flex-1 accent-primary-500"
          />
          <button
            type="button"
            onClick={() => setZoom(z => clampZoom(z + 0.2))}
            aria-label={t('avatar.zoomIn')}
            className="p-1.5 text-surface-300 hover:text-surface-100 transition-colors"
          >
            <ZoomIn className="w-5 h-5" />
          </button>
        </div>

        {error && <p className="text-sm text-rose-400/80">{error}</p>}

        <div className="flex gap-3 justify-end">
          <Button type="button" variant="secondary" onClick={onCancel} disabled={saving}>
            {t('profile.cancel')}
          </Button>
          <Button type="button" onClick={handleSave} loading={saving} disabled={!croppedAreaPixels}>
            {t('avatar.save')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
