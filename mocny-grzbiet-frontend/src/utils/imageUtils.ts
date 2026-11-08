// Input-side guard only (protects the browser from decoding absurd files) — after client-side
// compression the file sent to the server is small; the server's own 10 MB limit stays as defense in depth.
const MAX_FILE_SIZE = 50 * 1024 * 1024 // 50 MB
const MAX_DIMENSION = 1920
const OUTPUT_QUALITY = 0.85
const COMPRESS_THRESHOLD = 2 * 1024 * 1024 // 2 MB

const DEFAULT_TYPES = ['image/jpeg', 'image/png', 'image/webp']

export function validateImageFile(file: File, allowedTypes: string[] = DEFAULT_TYPES): string | null {
  if (file.size > MAX_FILE_SIZE) {
    const sizeMB = (file.size / (1024 * 1024)).toFixed(1)
    return `Plik jest za duży (${sizeMB} MB). Maksymalny rozmiar to 50 MB.`
  }
  if (!allowedTypes.includes(file.type)) {
    return 'Niedozwolony format pliku. Dozwolone: JPG, PNG, WebP.'
  }
  return null
}

// WebP output shrinks photographic PNGs too (PNG ignores the quality parameter) and keeps alpha
// (logos/badges). Old Safari can't encode WebP — detect once and fall back to JPEG there.
let webpSupported: boolean | null = null
function canEncodeWebp(): boolean {
  if (webpSupported === null) {
    // A canvas with no encoder can hand back null rather than a data URL — treat that as "no WebP"
    // instead of throwing halfway through someone's upload.
    const probe = document.createElement('canvas').toDataURL('image/webp') ?? ''
    webpSupported = probe.startsWith('data:image/webp')
  }
  return webpSupported
}

export interface CompressOptions {
  maxDimension?: number
  quality?: number
  /** `auto` picks WebP where supported. Pin a type when the server has to decode the result. */
  mimeType?: 'auto' | 'image/jpeg' | 'image/webp'
  /**
   * Re-encode even when the file is already small enough, and keep the result even if it grew.
   *
   * Off by default, because for catalog artwork a small original is simply better than a
   * re-encoded copy. On where the canvas pass is the point rather than a size optimisation: it is
   * what strips EXIF (GPS included) and what guarantees the format the server was promised.
   */
  force?: boolean
}

/**
 * Screenshots of health data: small enough that a month of them is a rounding error on disk,
 * still large enough to read heart rate and pace off a watch face without zooming.
 *
 * JPEG is pinned rather than left to `auto` because the server re-encodes this format itself —
 * the JDK ships no WebP reader, so a WebP upload would reach the disk with nothing but its
 * signature ever checked, which is not a trade to make for health data.
 */
export const TRAINING_PHOTO_COMPRESSION: CompressOptions = {
  maxDimension: 1280,
  quality: 0.75,
  mimeType: 'image/jpeg',
  force: true,
}

export async function compressImage(file: File, options: CompressOptions = {}): Promise<File> {
  const maxDimension = options.maxDimension ?? MAX_DIMENSION
  const quality = options.quality ?? OUTPUT_QUALITY
  const force = options.force ?? false

  if (!force && file.size <= COMPRESS_THRESHOLD && !(await exceedsDimension(file, maxDimension))) {
    return file
  }

  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(img.src)

      let { width, height } = img
      if (width > maxDimension || height > maxDimension) {
        const ratio = Math.min(maxDimension / width, maxDimension / height)
        width = Math.round(width * ratio)
        height = Math.round(height * ratio)
      }

      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const ctx = canvas.getContext('2d')!
      // JPEG has no alpha: without a fill, transparent pixels come out black. Screenshots are
      // opaque, but a PNG with rounded corners is not.
      if (resolveMimeType(options) === 'image/jpeg') {
        ctx.fillStyle = '#ffffff'
        ctx.fillRect(0, 0, width, height)
      }
      ctx.drawImage(img, 0, 0, width, height)

      const mimeType = resolveMimeType(options)
      canvas.toBlob(
        blob => {
          if (!blob) {
            resolve(file)
            return
          }
          // Keep the smaller of the two — never "compress" a file into a bigger one. Skipped under
          // `force`, where the canvas pass is doing something the original cannot do for us.
          if (!force && blob.size >= file.size) {
            resolve(file)
            return
          }
          const ext = mimeType === 'image/webp' ? '.webp' : '.jpg'
          const name = file.name.replace(/\.[^.]+$/, ext)
          resolve(new File([blob], name, { type: mimeType }))
        },
        mimeType,
        quality,
      )
    }
    img.onerror = () => reject(new Error('Nie udało się wczytać obrazu'))
    img.src = URL.createObjectURL(file)
  })
}

function resolveMimeType(options: CompressOptions): 'image/jpeg' | 'image/webp' {
  const requested = options.mimeType ?? 'auto'
  if (requested !== 'auto') {
    return requested
  }
  return canEncodeWebp() ? 'image/webp' : 'image/jpeg'
}

function exceedsDimension(file: File, maxDimension: number): Promise<boolean> {
  return new Promise(resolve => {
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(img.src)
      resolve(img.width > maxDimension || img.height > maxDimension)
    }
    img.onerror = () => resolve(false)
    img.src = URL.createObjectURL(file)
  })
}
