import { useState } from 'react'
import clsx from 'clsx'
import { FadeImage } from './FadeImage'

interface PhotoSlotProps {
  /** Path under public/, e.g. "/images/hero.jpg". Missing file → the placeholder, not a broken image. */
  src?: string
  alt: string
  className?: string
  /** Dark gradient over the bottom, so white text laid on the photo stays readable. */
  shade?: boolean
  /** Lazy for anything below the fold; the hero passes "eager". */
  loading?: 'lazy' | 'eager'
  /** The KD monogram on the placeholder. Off where a headline already sits on top of the slot. */
  monogram?: boolean
}

/**
 * A photo in the "cinematic" treatment: grayscale with lifted contrast, film grain, optional shade.
 *
 * The client's photos have not arrived yet, so every slot also has to look finished without one: a
 * deep gradient with the KD monogram. Dropping a file at the slot's path is the whole swap — a
 * missing file falls back to the same placeholder instead of a broken-image icon.
 */
export function PhotoSlot({ src, alt, className, shade = false, loading = 'lazy', monogram = true }: PhotoSlotProps) {
  const [failed, setFailed] = useState(false)
  const showPhoto = src && !failed

  return (
    <div className={clsx('grain overflow-hidden bg-surface-900', className)}>
      {showPhoto ? (
        <FadeImage
          src={src}
          alt={alt}
          loading={loading}
          decoding="async"
          onError={() => setFailed(true)}
          className="absolute inset-0 h-full w-full object-cover grayscale contrast-125 brightness-90"
        />
      ) : (
        // Decoration, not a stand-in photo: announcing it with the photo's description would tell a
        // screen-reader user about a picture that is not there.
        <div aria-hidden="true" className="absolute inset-0 flex items-center justify-center bg-[radial-gradient(120%_90%_at_30%_20%,#3a3a3a_0%,#1b1b1b_55%,#141414_100%)]">
          {monogram && (
            <span aria-hidden="true" className="text-display select-none text-[clamp(5rem,22vw,14rem)] text-transparent [-webkit-text-stroke:1px_rgb(255_255_255/0.12)]">
              KD
            </span>
          )}
        </div>
      )}
      {shade && <div aria-hidden="true" className="absolute inset-0 bg-gradient-to-t from-surface-950 via-surface-950/40 to-transparent" />}
    </div>
  )
}
