import { useCallback, useRef, useState } from 'react'
import { stopAgent, streamAgent } from '../services/agentApi'
import type { AgentChunk, AgentMode, ChatTurn } from '../types'
import { applyChunks, createId, expandStreamChunk, parseAgentChunk } from '../utils/chat'
import { createStreamUiPump } from '../utils/streamUiPump'

interface UseAgentStreamOptions {
  conversationId: string
  onSessionUpdated?: () => void
  onStreamComplete?: () => void
}

export function useAgentStream({
  conversationId,
  onSessionUpdated,
  onStreamComplete,
}: UseAgentStreamOptions) {
  const [turns, setTurns] = useState<ChatTurn[]>([])
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const pumpRef = useRef<ReturnType<typeof createStreamUiPump<AgentChunk>> | null>(null)

  const setTurnsExternal = useCallback((next: ChatTurn[]) => {
    setTurns(next)
    setError(null)
  }, [])

  const sendMessage = useCallback(
    async (
      question: string,
      t: (key: string) => string,
      options: { mode: Exclude<AgentMode, 'deep'>; fileId?: string },
    ) => {
      const trimmed = question.trim()
      if (!trimmed || isStreaming) return
      if (options.mode === 'file' && !options.fileId) return

      setError(null)
      const userTurn: ChatTurn = { id: createId(), role: 'user', content: trimmed }
      const assistantId = createId()
      const assistantTurn: ChatTurn = {
        id: assistantId,
        role: 'assistant',
        content: '',
        thinking: '',
        status: 'streaming',
      }

      setTurns((prev) => [...prev, userTurn, assistantTurn])
      setIsStreaming(true)

      const controller = new AbortController()
      abortRef.current = controller

      const pump = createStreamUiPump<AgentChunk>((batch) => {
        if (batch.length === 0) return
        setTurns((prev) =>
          prev.map((turn) => (turn.id === assistantId ? applyChunks(turn, batch) : turn)),
        )
      })
      pumpRef.current = pump

      try {
        await streamAgent(
          {
            mode: options.mode,
            question: trimmed,
            conversationId,
            fileId: options.fileId,
          },
          controller.signal,
          (line) => {
            const chunk = parseAgentChunk(line)
            if (!chunk) return
            expandStreamChunk(chunk).forEach((piece) => pump.push(piece))
          },
        )

        if (controller.signal.aborted) {
          pump.flush()
        } else {
          await pump.whenIdle()
        }

        if (controller.signal.aborted) {
          setTurns((prev) =>
            prev.map((turn) =>
              turn.id === assistantId ? { ...turn, status: 'stopped' } : turn,
            ),
          )
          return
        }

        setTurns((prev) =>
          prev.map((turn) =>
            turn.id === assistantId && turn.status === 'streaming'
              ? { ...turn, status: 'complete' }
              : turn,
          ),
        )
        onSessionUpdated?.()
      } catch (err) {
        pump.flush()
        if (controller.signal.aborted) {
          setTurns((prev) =>
            prev.map((turn) =>
              turn.id === assistantId ? { ...turn, status: 'stopped' } : turn,
            ),
          )
        } else {
          const message =
            err instanceof Error && err.message.includes('in progress')
              ? t('chat.errorInProgress')
              : t('chat.errorGeneric')
          setError(message)
          setTurns((prev) =>
            prev.map((turn) =>
              turn.id === assistantId
                ? { ...turn, content: message, status: 'error' }
                : turn,
            ),
          )
        }
      } finally {
        pumpRef.current = null
        onStreamComplete?.()
        setIsStreaming(false)
        abortRef.current = null
      }
    },
    [conversationId, isStreaming, onSessionUpdated, onStreamComplete],
  )

  const stopGeneration = useCallback(async () => {
    pumpRef.current?.flush()
    abortRef.current?.abort()
    try {
      await stopAgent(conversationId)
    } catch {
      // ignore stop errors
    }
    setIsStreaming(false)
  }, [conversationId])

  return {
    turns,
    isStreaming,
    error,
    sendMessage,
    stopGeneration,
    setTurns: setTurnsExternal,
    clearError: () => setError(null),
  }
}
