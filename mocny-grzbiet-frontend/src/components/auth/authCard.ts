/**
 * The account screens (login, register, password reset, verification, error fallback) share one
 * stage: a card centred under a faint light from above. The strings live here because they were
 * pasted into nine places and had already drifted (one `py-8`, one `text-center`, one without
 * `px-4`).
 */
export const authStageClass =
  'flex min-h-[calc(100dvh-4rem)] flex-col items-center justify-center px-4 py-12 sm:py-16 bg-[radial-gradient(60%_45%_at_50%_0%,rgb(255_255_255/0.07),transparent)]'

export const authCardClass =
  'w-full max-w-md rounded-3xl border border-white/[0.07] bg-surface-900/80 p-7 sm:p-9 shadow-glow backdrop-blur animate-rise'
