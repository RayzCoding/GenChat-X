import { useEffect } from 'react'

interface ConfirmDialogProps {
  open: boolean
  title: string
  message: string
  confirmLabel: string
  cancelLabel: string
  loading?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  cancelLabel,
  loading = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  useEffect(() => {
    if (!open) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !loading) {
        onCancel()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [loading, onCancel, open])

  if (!open) return null

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center p-4">
      <button
        type="button"
        className="absolute inset-0 bg-[#231F19]/40 backdrop-blur-[2px]"
        aria-label={cancelLabel}
        disabled={loading}
        onClick={loading ? undefined : onCancel}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        className="relative w-full max-w-md rounded-2xl border border-[#E7E2D8] bg-[#FCFAF7] p-6 shadow-[0_24px_60px_rgba(35,31,25,0.18)]"
      >
        <h3
          id="confirm-dialog-title"
          className="font-headline-md text-xl font-semibold text-warm-900"
        >
          {title}
        </h3>
        <p className="mt-3 font-body-md text-sm leading-relaxed text-on-surface-variant">
          {message}
        </p>
        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            disabled={loading}
            onClick={onCancel}
            className="rounded-xl px-4 py-2 text-sm font-medium text-on-surface-variant transition-colors hover:bg-surface-container-low hover:text-on-surface disabled:opacity-50"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            disabled={loading}
            onClick={onConfirm}
            className="rounded-xl bg-error px-4 py-2 text-sm font-semibold text-on-error shadow-sm transition-colors hover:bg-[#9F1D14] disabled:opacity-60"
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
