import { useState, useRef, useLayoutEffect, type ImgHTMLAttributes } from 'react'

interface FadeImageProps extends ImgHTMLAttributes<HTMLImageElement> {
  src: string
}

/**
 * Image that fades in once decoded instead of popping over its placeholder.
 * Already-cached images are `complete` before paint, so they show instantly
 * with no fade — only the first (uncached) load animates.
 */
export function FadeImage({ src, className = '', ...rest }: FadeImageProps) {
  const ref = useRef<HTMLImageElement>(null)
  const [loaded, setLoaded] = useState(false)

  useLayoutEffect(() => {
    setLoaded(ref.current?.complete ?? false)
  }, [src])

  return (
    <img
      ref={ref}
      src={src}
      onLoad={() => setLoaded(true)}
      className={`${className} transition-opacity duration-300 ${loaded ? 'opacity-100' : 'opacity-0'}`}
      {...rest}
    />
  )
}
