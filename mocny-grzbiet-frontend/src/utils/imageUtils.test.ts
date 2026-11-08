import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { compressImage, TRAINING_PHOTO_COMPRESSION, validateImageFile } from './imageUtils'

function createFile(name: string, size: number, type: string): File {
  const buffer = new ArrayBuffer(size)
  return new File([buffer], name, { type })
}

describe('validateImageFile', () => {
  it('should accept valid JPEG under 50MB', () => {
    const file = createFile('photo.jpg', 5 * 1024 * 1024, 'image/jpeg')
    expect(validateImageFile(file)).toBeNull()
  })

  it('should accept valid PNG under 50MB', () => {
    const file = createFile('image.png', 1 * 1024 * 1024, 'image/png')
    expect(validateImageFile(file)).toBeNull()
  })

  it('should accept valid WebP under 50MB', () => {
    const file = createFile('image.webp', 2 * 1024 * 1024, 'image/webp')
    expect(validateImageFile(file)).toBeNull()
  })

  it('should accept a large photo that used to exceed the old 10MB limit', () => {
    // Big camera/phone photos must pass validation — they get compressed client-side afterwards.
    const file = createFile('camera.jpg', 25 * 1024 * 1024, 'image/jpeg')
    expect(validateImageFile(file)).toBeNull()
  })

  it('should reject file exceeding 50MB', () => {
    const file = createFile('huge.jpg', 51 * 1024 * 1024, 'image/jpeg')
    const error = validateImageFile(file)
    expect(error).not.toBeNull()
    expect(error).toContain('50 MB')
  })

  it('should reject file exactly at boundary', () => {
    const file = createFile('edge.jpg', 50 * 1024 * 1024 + 1, 'image/jpeg')
    expect(validateImageFile(file)).not.toBeNull()
  })

  it('should accept file exactly at 50MB', () => {
    const file = createFile('exact.jpg', 50 * 1024 * 1024, 'image/jpeg')
    expect(validateImageFile(file)).toBeNull()
  })

  it('should reject GIF format', () => {
    const file = createFile('anim.gif', 500 * 1024, 'image/gif')
    const error = validateImageFile(file)
    expect(error).not.toBeNull()
    expect(error).toContain('JPG')
  })

  it('should reject PDF format', () => {
    const file = createFile('doc.pdf', 500 * 1024, 'application/pdf')
    expect(validateImageFile(file)).not.toBeNull()
  })

  it('should reject SVG format', () => {
    const file = createFile('icon.svg', 10 * 1024, 'image/svg+xml')
    expect(validateImageFile(file)).not.toBeNull()
  })

  it('should include file size in error message', () => {
    const file = createFile('big.jpg', 55 * 1024 * 1024, 'image/jpeg')
    const error = validateImageFile(file)
    expect(error).toContain('55.0')
  })

  it('should accept HEIC when the caller opts into it', () => {
    // Straight off an iPhone. The canvas pass turns it into the JPEG the server expects, so the
    // input format only has to be something the browser can decode.
    const file = createFile('IMG_0001.heic', 3 * 1024 * 1024, 'image/heic')

    expect(validateImageFile(file)).not.toBeNull()
    expect(validateImageFile(file, ['image/jpeg', 'image/heic'])).toBeNull()
  })
})

describe('compressImage', () => {
  // jsdom decodes nothing and has no canvas, so the pieces compressImage leans on are stubbed and
  // the assertions are about WHAT it asks the browser for: the format, the quality, the size.
  let toBlobCalls: Array<{ type: string; quality: number }>
  let canvasSize: { width: number; height: number }
  let sourceSize: { width: number; height: number }
  let blobSize: number

  beforeEach(() => {
    toBlobCalls = []
    canvasSize = { width: 0, height: 0 }
    sourceSize = { width: 1179, height: 2556 }
    blobSize = 110 * 1024

    vi.stubGlobal('Image', class {
      onload: (() => void) | null = null
      onerror: (() => void) | null = null
      width = 0
      height = 0
      set src(_value: string) {
        this.width = sourceSize.width
        this.height = sourceSize.height
        queueMicrotask(() => this.onload?.())
      }
      get src() { return 'blob:stub' }
    })

    URL.createObjectURL = vi.fn(() => 'blob:stub')
    URL.revokeObjectURL = vi.fn()

    // jsdom encodes nothing, so the WebP probe answers "no" — same as an old Safari.
    HTMLCanvasElement.prototype.toDataURL = vi.fn(() => 'data:image/png;base64,')

    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({
      drawImage: vi.fn(), fillRect: vi.fn(), fillStyle: '',
    })) as unknown as typeof HTMLCanvasElement.prototype.getContext

    HTMLCanvasElement.prototype.toBlob = function (
      callback: BlobCallback, type?: string, quality?: number,
    ) {
      canvasSize = { width: this.width, height: this.height }
      toBlobCalls.push({ type: type ?? '', quality: quality ?? 0 })
      callback(new Blob([new ArrayBuffer(blobSize)], { type }))
    }
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('should encode a training photo as JPEG at 1280px and 0.75 quality', async () => {
    const file = createFile('garmin.png', 2.4 * 1024 * 1024, 'image/png')

    const result = await compressImage(file, TRAINING_PHOTO_COMPRESSION)

    expect(toBlobCalls).toEqual([{ type: 'image/jpeg', quality: 0.75 }])
    // 1179x2556 fits inside 1280 on the long side, aspect ratio kept
    expect(canvasSize).toEqual({ width: 590, height: 1280 })
    expect(result.type).toBe('image/jpeg')
    expect(result.name).toBe('garmin.jpg')
  })

  /**
   * The trap this guards. Without `force`, a small screenshot would be returned untouched — and
   * untouched means it keeps its EXIF (GPS included) and stays whatever format the phone chose,
   * which the server has no reader for.
   */
  it('should re-encode a small photo anyway when forced', async () => {
    const file = createFile('small.heic', 80 * 1024, 'image/heic')
    blobSize = 120 * 1024 // the encode came out BIGGER than the original

    const result = await compressImage(file, TRAINING_PHOTO_COMPRESSION)

    expect(toBlobCalls).toHaveLength(1)
    expect(result.type).toBe('image/jpeg')
    expect(result.size).toBe(blobSize)
  })

  it('should leave a small file alone by default', async () => {
    sourceSize = { width: 800, height: 600 }
    const file = createFile('logo.png', 500 * 1024, 'image/png')

    const result = await compressImage(file)

    expect(toBlobCalls).toHaveLength(0)
    expect(result).toBe(file)
  })

  /** Regression guard for the existing callers: defaults are still 1920px at 0.85. */
  it('should keep the default profile for catalog images', async () => {
    sourceSize = { width: 4000, height: 3000 }
    const file = createFile('gallery.jpg', 6 * 1024 * 1024, 'image/jpeg')

    await compressImage(file)

    expect(toBlobCalls[0].quality).toBe(0.85)
    expect(canvasSize).toEqual({ width: 1920, height: 1440 })
  })

  it('should keep the original when a default-profile encode came out bigger', async () => {
    sourceSize = { width: 4000, height: 3000 }
    blobSize = 9 * 1024 * 1024
    const file = createFile('gallery.jpg', 6 * 1024 * 1024, 'image/jpeg')

    const result = await compressImage(file)

    expect(result).toBe(file)
  })
})
