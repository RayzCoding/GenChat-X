import { useCallback, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useSessions } from '../../hooks/useSessions'
import { getRouteForAgentType } from '../../utils/agentRoutes'
import { ConfirmDialog } from '../ui/ConfirmDialog'
import { WorkbenchShell } from '../workbench/WorkbenchShell'
import { HistoryCard } from './HistoryCard'
import { HistoryFilters, type HistoryFilterKey } from './HistoryFilters'
import { SelectionBar } from './SelectionBar'
import { Icon } from '../ui/Icon'

export function HistoryPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { sessions, loading, removeSessions } = useSessions(100)

  const [sidebarOpen, setSidebarOpen] = useState(() =>
    typeof window === 'undefined' ? true : window.innerWidth >= 768,
  )
  const [activeFilter, setActiveFilter] = useState<HistoryFilterKey>('all')
  const [multiSelectMode, setMultiSelectMode] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [pendingIds, setPendingIds] = useState<string[]>([])
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleNewChat = useCallback(() => {
    navigate('/c')
  }, [navigate])

  const handleOpenSession = useCallback(
    (conversationId: string) => {
      const session = sessions.find((s) => s.conversationId === conversationId)
      navigate(getRouteForAgentType(session?.agentType, conversationId))
    },
    [navigate, sessions],
  )

  const toggleMultiSelect = useCallback(() => {
    setMultiSelectMode((prev) => {
      if (prev) {
        setSelectedIds(new Set())
      }
      return !prev
    })
  }, [])

  const handleSelectChange = useCallback((conversationId: string, checked: boolean) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (checked) {
        next.add(conversationId)
      } else {
        next.delete(conversationId)
      }
      return next
    })
  }, [])

  const requestDelete = useCallback((conversationIds: string[]) => {
    const uniqueIds = [...new Set(conversationIds.filter((id) => id.trim().length > 0))]
    if (uniqueIds.length === 0) return
    setError(null)
    setPendingIds(uniqueIds)
  }, [])

  const handleConfirmDelete = useCallback(async () => {
    if (pendingIds.length === 0 || deleting) return
    setDeleting(true)
    setError(null)
    try {
      const { deleted, failed } = await removeSessions(pendingIds)
      setSelectedIds((prev) => {
        const next = new Set(prev)
        deleted.forEach((id) => next.delete(id))
        return next
      })
      setPendingIds([])
      if (failed.length === 0) {
        if (multiSelectMode && deleted.length > 0) {
          setMultiSelectMode(false)
          setSelectedIds(new Set())
        }
      } else {
        setError(
          deleted.length > 0 ? t('history.deletePartialFailed') : t('history.deleteFailed'),
        )
      }
    } catch {
      setError(t('history.deleteFailed'))
    } finally {
      setDeleting(false)
    }
  }, [deleting, multiSelectMode, pendingIds, removeSessions, t])

  const displayedSessions = useMemo(() => sessions, [sessions])
  const pendingCount = pendingIds.length

  return (
    <WorkbenchShell
      sidebarOpen={sidebarOpen}
      onToggleSidebar={() => setSidebarOpen((open) => !open)}
      sessions={sessions}
      onNewChat={handleNewChat}
      onSelectSession={(id) => handleOpenSession(id)}
      onDeleteSession={(id) => requestDelete([id])}
    >
      <div className="flex min-h-0 flex-1 flex-col overflow-y-auto pb-28">
        <div className="mx-auto w-full max-w-7xl p-8">
          <div className="mb-10 flex flex-col justify-between gap-6 md:flex-row md:items-end">
            <div>
              <h2 className="mb-2 font-headline-lg text-headline-lg font-bold">
                {t('history.title')}
              </h2>
              <p className="font-body-md text-on-surface-variant">{t('history.subtitle')}</p>
            </div>
            <HistoryFilters
              activeFilter={activeFilter}
              onFilterChange={setActiveFilter}
              multiSelectMode={multiSelectMode}
              onToggleMultiSelect={toggleMultiSelect}
            />
          </div>

          {error && (
            <p className="mb-6 rounded-xl border border-error/20 bg-error-container px-4 py-2 text-sm text-on-error-container">
              {error}
            </p>
          )}

          {loading && (
            <p className="py-12 text-center text-on-surface-variant">{t('history.loading')}</p>
          )}

          {!loading && (
            <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
              {displayedSessions.map((session) => (
                <HistoryCard
                  key={session.conversationId}
                  session={session}
                  multiSelectMode={multiSelectMode}
                  selected={selectedIds.has(session.conversationId)}
                  deleting={deleting}
                  onSelectChange={(checked) =>
                    handleSelectChange(session.conversationId, checked)
                  }
                  onOpen={() => handleOpenSession(session.conversationId)}
                  onDelete={() => requestDelete([session.conversationId])}
                  locale={i18n.language}
                />
              ))}

              <button
                type="button"
                onClick={handleNewChat}
                className="group flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed border-outline-variant/20 p-8 opacity-40 transition-opacity hover:opacity-100"
              >
                <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-surface-container-low transition-transform group-hover:scale-110">
                  <Icon name="add" className="text-[32px]" />
                </div>
                <p className="font-label-md text-label-md font-bold">{t('history.newExplore')}</p>
              </button>
            </div>
          )}

          {!loading && displayedSessions.length === 0 && (
            <p className="py-8 text-center text-on-surface-variant">{t('session.noHistory')}</p>
          )}
        </div>
      </div>

      <SelectionBar
        visible={multiSelectMode}
        selectedCount={selectedIds.size}
        deleting={deleting}
        onDelete={() => requestDelete([...selectedIds])}
        onCancel={toggleMultiSelect}
      />

      <ConfirmDialog
        open={pendingCount > 0}
        title={t('history.confirmTitle')}
        message={
          pendingCount > 1
            ? t('history.confirmMany', { count: pendingCount })
            : t('history.confirmOne')
        }
        confirmLabel={deleting ? t('history.deleting') : t('history.confirmAction')}
        cancelLabel={t('history.cancel')}
        loading={deleting}
        onConfirm={() => void handleConfirmDelete()}
        onCancel={() => {
          if (!deleting) setPendingIds([])
        }}
      />
    </WorkbenchShell>
  )
}
