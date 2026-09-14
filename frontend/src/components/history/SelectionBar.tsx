import { useTranslation } from 'react-i18next'
import { Icon } from '../ui/Icon'

interface SelectionBarProps {
  visible: boolean
  selectedCount: number
  deleting?: boolean
  onDelete: () => void
  onCancel: () => void
}

export function SelectionBar({
  visible,
  selectedCount,
  deleting = false,
  onDelete,
  onCancel,
}: SelectionBarProps) {
  const { t } = useTranslation()
  const canDelete = selectedCount > 0 && !deleting

  return (
    <div
      className={`fixed bottom-8 left-1/2 z-[100] flex -translate-x-1/2 items-center gap-8 rounded-full border border-primary/30 bg-surface-container-highest/90 px-8 py-4 shadow-2xl backdrop-blur-2xl transition-all md:left-[calc(280px+(100%-280px)/2)] ${
        visible ? 'translate-y-0' : 'pointer-events-none translate-y-32 opacity-0'
      }`}
    >
      <div className="flex items-center gap-3 border-r border-outline-variant/30 pr-8">
        <span className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-sm font-bold text-on-primary">
          {selectedCount}
        </span>
        <span className="font-label-md text-label-md text-on-surface">{t('history.selected')}</span>
      </div>
      <div className="flex items-center gap-4">
        <button
          type="button"
          className="flex items-center gap-2 rounded-xl px-4 py-2 text-on-surface transition-colors hover:bg-surface-container-low"
        >
          <Icon name="archive" className="text-[20px]" />
          {t('history.archive')}
        </button>
        <button
          type="button"
          disabled={!canDelete}
          onClick={onDelete}
          className="flex items-center gap-2 rounded-xl px-4 py-2 text-error transition-colors hover:bg-error-container/20 disabled:cursor-not-allowed disabled:opacity-40"
        >
          <Icon name="delete" className="text-[20px]" />
          {deleting ? t('history.deleting') : t('history.delete')}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="rounded-xl px-4 py-2 text-on-surface-variant transition-colors hover:text-on-surface"
        >
          {t('history.cancel')}
        </button>
      </div>
    </div>
  )
}
