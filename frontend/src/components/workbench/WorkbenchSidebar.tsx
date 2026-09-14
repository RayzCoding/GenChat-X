import { useTranslation } from 'react-i18next'
import type { AgentMode, SessionSummary } from '../../types'
import { modeFromAgentType } from '../../utils/agentMode'
import { formatRelativeTime, groupSessionsByTime } from '../../utils/sessionTime'
import { IconPlus, IconTrash } from './icons'

const MODE_LABEL: Record<AgentMode, string> = {
  chat: 'workbench.mode.chatShort',
  file: 'workbench.mode.fileShort',
  deep: 'workbench.mode.deepShort',
  ppt: 'workbench.mode.pptShort',
  skills: 'workbench.mode.skillsShort',
}

interface WorkbenchSidebarProps {
  open: boolean
  sessions: SessionSummary[]
  activeConversationId?: string
  onNewChat: () => void
  onSelectSession: (conversationId: string, mode: AgentMode) => void
  onDeleteSession?: (conversationId: string) => void
}

export function WorkbenchSidebar({
  open,
  sessions,
  activeConversationId,
  onNewChat,
  onSelectSession,
  onDeleteSession,
}: WorkbenchSidebarProps) {
  const { t, i18n } = useTranslation()
  const grouped = groupSessionsByTime(sessions)
  const isMac = navigator.platform.toUpperCase().includes('MAC')

  return (
    <aside
      className={`flex shrink-0 select-none flex-col border-r border-outline-variant bg-[#F8F5EF]/90 transition-all duration-300 ease-out ${
        open ? 'w-sidebar-width' : 'w-0 overflow-hidden border-r-0'
      }`}
    >
      <div className="p-3">
        <button
          type="button"
          onClick={onNewChat}
          className="group flex w-full items-center justify-between rounded-xl border border-[#E7E2D8] bg-white px-3 py-2.5 text-xs font-semibold text-warm-800 shadow-sm transition-all hover:border-[#D5CEBF] hover:bg-[#FAF8F5] active:scale-[0.98]"
        >
          <span className="flex items-center gap-2">
            <IconPlus className="h-4 w-4 stroke-[2.2] text-[#8C7E72] transition-colors group-hover:text-warm-800" />
            {t('nav.newChat')}
          </span>
          <span className="rounded border border-[#E2DDD3] bg-[#F4F0E8] px-1.5 py-0.5 font-mono text-[10px] font-normal text-[#8C7E72]">
            {isMac ? '⌘K' : 'Ctrl+K'}
          </span>
        </button>
      </div>

      <div className="flex-1 space-y-4 overflow-y-auto px-2 py-1">
        {sessions.length === 0 && (
          <p className="px-2.5 py-6 text-center text-xs text-warm-400">{t('session.noHistory')}</p>
        )}
        <SessionGroup
          title={t('workbench.sidebar.today')}
          sessions={grouped.today}
          activeConversationId={activeConversationId}
          onSelectSession={onSelectSession}
          onDeleteSession={onDeleteSession}
          locale={i18n.language}
        />
        <SessionGroup
          title={t('workbench.sidebar.week')}
          sessions={grouped.week}
          activeConversationId={activeConversationId}
          onSelectSession={onSelectSession}
          onDeleteSession={onDeleteSession}
          locale={i18n.language}
        />
        <SessionGroup
          title={t('workbench.sidebar.older')}
          sessions={grouped.older}
          activeConversationId={activeConversationId}
          onSelectSession={onSelectSession}
          onDeleteSession={onDeleteSession}
          locale={i18n.language}
        />
      </div>
    </aside>
  )
}

function SessionGroup({
  title,
  sessions,
  activeConversationId,
  onSelectSession,
  onDeleteSession,
  locale,
}: {
  title: string
  sessions: SessionSummary[]
  activeConversationId?: string
  onSelectSession: (conversationId: string, mode: AgentMode) => void
  onDeleteSession?: (conversationId: string) => void
  locale: string
}) {
  const { t } = useTranslation()
  if (sessions.length === 0) return null

  return (
    <div>
      <div className="flex items-center justify-between px-2.5 py-1.5 text-[11px] font-medium tracking-wide text-warm-400">
        <span>{title}</span>
      </div>
      <div className="space-y-1.5">
        {sessions.map((session) => {
          const mode = modeFromAgentType(session.agentType)
          const active = session.conversationId === activeConversationId
          return (
            <div
              key={session.conversationId}
              className={`group relative w-full rounded-xl border transition-all ${
                active
                  ? 'border-[#E7E2D8] bg-white shadow-sm'
                  : 'border-transparent hover:border-[#E7E2D8] hover:bg-white/70'
              }`}
            >
              <button
                type="button"
                onClick={() => onSelectSession(session.conversationId, mode)}
                className={`w-full px-3 py-2.5 text-left ${onDeleteSession ? 'pr-9' : ''}`}
              >
                <p
                  className={`truncate text-xs font-medium transition-colors ${
                    active
                      ? 'text-warm-900 group-hover:text-accent-amber'
                      : 'text-[#57534E] group-hover:text-warm-900'
                  }`}
                >
                  {session.title || session.lastQuestion || session.conversationId}
                </p>
                <div className="mt-0.5 flex items-center gap-2 text-[10px] text-warm-400">
                  <span>
                    {t(MODE_LABEL[mode])} · {formatRelativeTime(session.updatedAt, locale)}
                  </span>
                </div>
              </button>
              {onDeleteSession && (
                <button
                  type="button"
                  title={t('history.actions.delete')}
                  onClick={(event) => {
                    event.preventDefault()
                    event.stopPropagation()
                    onDeleteSession(session.conversationId)
                  }}
                  className="absolute right-2 top-1/2 -translate-y-1/2 rounded-lg p-1 text-warm-400 opacity-100 transition-all hover:bg-error-container hover:text-error md:opacity-0 md:group-hover:opacity-100"
                >
                  <IconTrash className="h-3.5 w-3.5" />
                </button>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
