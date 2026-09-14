import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useAgentStream } from '../../hooks/useAgentStream'
import { useDeepResearchStream } from '../../hooks/useDeepResearchStream'
import { useSessions } from '../../hooks/useSessions'
import type { AgentMode, ChatTurn } from '../../types'
import { createConversationId, mergeSessionTurns } from '../../utils/chat'
import { parseAgentMode, workbenchPath } from '../../utils/agentMode'
import { MessageList } from '../chat/MessageList'
import { AssistantMessage } from '../chat/AssistantMessage'
import { PptAssistantMessage } from '../ppt/PptAssistantMessage'
import { SkillsAssistantMessage } from '../skills/SkillsAssistantMessage'
import { DeepResearchWorkspace } from '../deep-research/DeepResearchWorkspace'
import { FeatureCards } from './FeatureCards'
import { WorkbenchInputDock } from './WorkbenchInputDock'
import { WorkbenchShell } from './WorkbenchShell'
import { ConfirmDialog } from '../ui/ConfirmDialog'

export function WorkbenchPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { conversationId: routeConversationId } = useParams<{ conversationId?: string }>()
  const [searchParams, setSearchParams] = useSearchParams()
  const mode = parseAgentMode(searchParams.get('mode'))

  const [conversationId, setConversationId] = useState(
    () => routeConversationId ?? createConversationId(),
  )
  const [sidebarOpen, setSidebarOpen] = useState(() =>
    typeof window === 'undefined' ? true : window.innerWidth >= 768,
  )
  const [draft, setDraft] = useState('')
  const [fileId, setFileId] = useState<string | null>(null)
  const [fileName, setFileName] = useState<string | null>(null)
  const [filePickSignal, setFilePickSignal] = useState(0)
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const skipHistoryLoadRef = useRef(false)
  const turnsRef = useRef<ChatTurn[]>([])

  const { sessions, refresh, loadSessionTurns, removeSessions } = useSessions()

  const {
    turns,
    isStreaming: agentStreaming,
    sendMessage,
    stopGeneration: stopAgentGeneration,
    setTurns,
    error: agentError,
  } = useAgentStream({
    conversationId,
    onSessionUpdated: () => void refresh(),
    onStreamComplete: () => {
      skipHistoryLoadRef.current = true
    },
  })
  turnsRef.current = turns

  const {
    state: deepState,
    isStreaming: deepStreaming,
    error: deepError,
    sendQuestion: sendDeepQuestion,
    stopGeneration: stopDeepGeneration,
    reset: resetDeep,
  } = useDeepResearchStream(conversationId)

  const isStreaming = mode === 'deep' ? deepStreaming : agentStreaming
  const showDeepWorkspace = mode === 'deep' && deepState.phase !== 'idle'
  const isEmpty = !showDeepWorkspace && turns.length === 0

  const setMode = useCallback(
    (next: AgentMode) => {
      const params = new URLSearchParams(searchParams)
      if (next === 'chat') {
        params.delete('mode')
      } else {
        params.set('mode', next)
      }
      setSearchParams(params, { replace: true })
    },
    [searchParams, setSearchParams],
  )

  const handleSelectMode = useCallback(
    (next: AgentMode) => {
      setMode(next)
      if (next === 'file' && !fileName) {
        setFilePickSignal((n) => n + 1)
      }
    },
    [fileName, setMode],
  )

  useEffect(() => {
    if (routeConversationId && routeConversationId !== conversationId) {
      setConversationId(routeConversationId)
      resetDeep()
    }
  }, [routeConversationId, conversationId, resetDeep])

  useEffect(() => {
    if (!routeConversationId || isStreaming) return
    if (skipHistoryLoadRef.current) {
      skipHistoryLoadRef.current = false
      return
    }

    let cancelled = false
    void loadSessionTurns(routeConversationId)
      .then((loaded) => {
        if (!cancelled) {
          setTurns(mergeSessionTurns(turnsRef.current, loaded))
        }
      })
      .catch(() => {
        if (!cancelled) setTurns([])
      })

    return () => {
      cancelled = true
    }
  }, [routeConversationId, isStreaming, loadSessionTurns, setTurns])

  const handleNewChat = useCallback(() => {
    const newId = createConversationId()
    setConversationId(newId)
    setTurns([])
    setDraft('')
    setFileId(null)
    setFileName(null)
    resetDeep()
    navigate('/c', { replace: true })
  }, [navigate, resetDeep, setTurns])

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        handleNewChat()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [handleNewChat])

  const handleSelectSession = useCallback(
    (id: string, nextMode: AgentMode) => {
      if (isStreaming) return
      setConversationId(id)
      setTurns([])
      resetDeep()
      navigate(workbenchPath(id, nextMode))
    },
    [isStreaming, navigate, resetDeep, setTurns],
  )

  const handleConfirmDelete = useCallback(async () => {
    if (!pendingDeleteId || deleting) return
    setDeleting(true)
    setDeleteError(null)
    try {
      const { failed } = await removeSessions([pendingDeleteId])
      const deletedCurrent =
        pendingDeleteId === conversationId || pendingDeleteId === routeConversationId
      setPendingDeleteId(null)
      if (failed.length > 0) {
        setDeleteError(t('history.deleteFailed'))
        return
      }
      if (deletedCurrent) {
        handleNewChat()
      }
    } catch {
      setDeleteError(t('history.deleteFailed'))
    } finally {
      setDeleting(false)
    }
  }, [
    conversationId,
    deleting,
    handleNewChat,
    pendingDeleteId,
    removeSessions,
    routeConversationId,
    t,
  ])

  const ensureConversationRoute = useCallback(() => {
    if (!routeConversationId) {
      navigate(workbenchPath(conversationId, mode), { replace: true })
    }
  }, [conversationId, mode, navigate, routeConversationId])

  const handleSend = useCallback(
    (question: string) => {
      ensureConversationRoute()
      if (mode === 'deep') {
        void sendDeepQuestion(question, t)
        return
      }
      void sendMessage(question, t, {
        mode,
        fileId: fileId ?? undefined,
      })
    },
    [ensureConversationRoute, fileId, mode, sendDeepQuestion, sendMessage, t],
  )

  const handleStop = useCallback(() => {
    if (mode === 'deep') {
      void stopDeepGeneration()
      return
    }
    void stopAgentGeneration()
  }, [mode, stopAgentGeneration, stopDeepGeneration])

  const handleRecommendSelect = useCallback(
    (question: string) => {
      handleSend(question)
    },
    [handleSend],
  )

  return (
    <WorkbenchShell
      sidebarOpen={sidebarOpen}
      onToggleSidebar={() => setSidebarOpen((open) => !open)}
      sessions={sessions}
      activeConversationId={routeConversationId}
      onNewChat={handleNewChat}
      onSelectSession={handleSelectSession}
      onDeleteSession={(id) => {
        setDeleteError(null)
        setPendingDeleteId(id)
      }}
    >
      {showDeepWorkspace ? (
        <DeepResearchWorkspace state={deepState} error={deepError} />
      ) : isEmpty ? (
        <FeatureCards onSelectMode={handleSelectMode} />
      ) : (
        <MessageList
          turns={turns}
          isStreaming={isStreaming}
          onRecommendSelect={handleRecommendSelect}
          layout="embedded"
          maxWidthClass="max-w-4xl"
          renderAssistant={(turn) => {
            if (mode === 'ppt') {
              const userQuestion = [...turns]
                .reverse()
                .find((item) => item.role === 'user')?.content
              return (
                <PptAssistantMessage
                  turn={turn}
                  userQuestion={userQuestion}
                  showProgress={turn.status === 'streaming'}
                />
              )
            }
            if (mode === 'skills') {
              return <SkillsAssistantMessage turn={turn} />
            }
            return (
              <AssistantMessage
                turn={turn}
                onRecommendSelect={handleRecommendSelect}
                recommendDisabled={isStreaming}
              />
            )
          }}
        />
      )}

      {deleteError && (
        <div className="mx-auto w-full max-w-4xl px-4">
          <p className="rounded-xl border border-error/20 bg-error-container px-3 py-2 text-xs text-on-error-container">
            {deleteError}
          </p>
        </div>
      )}

      {agentError && mode !== 'deep' && (
        <div className="mx-auto w-full max-w-4xl px-4">
          <p className="rounded-xl border border-error/20 bg-error-container px-3 py-2 text-xs text-on-error-container">
            {agentError}
          </p>
        </div>
      )}

      <WorkbenchInputDock
        mode={mode}
        onModeChange={setMode}
        onSend={handleSend}
        onStop={handleStop}
        isStreaming={isStreaming}
        disabled={isStreaming}
        showPills={isEmpty}
        attachedFileName={fileName}
        onFileUploaded={(id, name) => {
          setFileId(id)
          setFileName(name)
        }}
        onClearFile={() => {
          setFileId(null)
          setFileName(null)
        }}
        draft={draft}
        onDraftChange={setDraft}
        requestFilePick={filePickSignal}
      />

      <ConfirmDialog
        open={Boolean(pendingDeleteId)}
        title={t('history.confirmTitle')}
        message={t('history.confirmOne')}
        confirmLabel={deleting ? t('history.deleting') : t('history.confirmAction')}
        cancelLabel={t('history.cancel')}
        loading={deleting}
        onConfirm={() => void handleConfirmDelete()}
        onCancel={() => {
          if (!deleting) setPendingDeleteId(null)
        }}
      />
    </WorkbenchShell>
  )
}
