import type { ReactNode } from 'react'
import type { AgentMode, SessionSummary } from '../../types'
import { WorkbenchHeader } from './WorkbenchHeader'
import { WorkbenchSidebar } from './WorkbenchSidebar'

interface WorkbenchShellProps {
  children: ReactNode
  sidebarOpen: boolean
  onToggleSidebar: () => void
  sessions: SessionSummary[]
  activeConversationId?: string
  onNewChat: () => void
  onSelectSession: (conversationId: string, mode: AgentMode) => void
  onDeleteSession?: (conversationId: string) => void
}

export function WorkbenchShell({
  children,
  sidebarOpen,
  onToggleSidebar,
  sessions,
  activeConversationId,
  onNewChat,
  onSelectSession,
  onDeleteSession,
}: WorkbenchShellProps) {
  return (
    <div className="chat-bg-warm flex h-screen w-screen flex-col overflow-hidden font-sans text-warm-800 antialiased">
      <WorkbenchHeader onToggleSidebar={onToggleSidebar} onNewChat={onNewChat} />
      <div className="flex flex-1 overflow-hidden">
        <WorkbenchSidebar
          open={sidebarOpen}
          sessions={sessions}
          activeConversationId={activeConversationId}
          onNewChat={onNewChat}
          onSelectSession={onSelectSession}
          onDeleteSession={onDeleteSession}
        />
        <main className="relative flex flex-1 flex-col overflow-hidden">{children}</main>
      </div>
    </div>
  )
}
