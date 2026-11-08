// Renders the cropped fragment of a photo into a square JPEG image (output OUTPUT_SIZE×OUTPUT_SIZE),
// so the saved avatar is exactly what the user cropped within the circle.

export interface PixelCrop {
  x: number
  y: number
  width: number
  height: number
}

const OUTPUT_SIZE = 512
const OUTPUT_QUALITY = 0.9

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.addEventListener('load', () => resolve(image))
    image.addEventListener('error', () => reject(new Error('Nie udało się wczytać obrazu')))
    image.src = src
  })
}

export async function getCroppedBlob(imageSrc: string, crop: PixelCrop): Promise<Blob> {
  const image = await loadImage(imageSrc)

  const canvas = document.createElement('canvas')
  canvas.width = OUTPUT_SIZE
  canvas.height = OUTPUT_SIZE
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Brak kontekstu canvas')

  // Background in case of transparency (JPEG has no alpha channel).
  ctx.fillStyle = '#1a1a1a'
  ctx.fillRect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE)
  ctx.imageSmoothingQuality = 'high'

  ctx.drawImage(
    image,
    crop.x, crop.y, crop.width, crop.height,
    0, 0, OUTPUT_SIZE, OUTPUT_SIZE,
  )

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      blob => (blob ? resolve(blob) : reject(new Error('Nie udało się wygenerować obrazu'))),
      'image/jpeg',
      OUTPUT_QUALITY,
    )
  })
}
