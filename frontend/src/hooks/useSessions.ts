import { useCallback, useEffect, useState } from 'react'
import {
  deleteSession,
  getSessionDetail,
  listSessions,
  searchSessions,
} from '../services/sessionApi'
import type { SessionSummary } from '../types'
import { sessionMessagesToTurns } from '../utils/chat'
import type { ChatTurn } from '../types'

export interface RemoveSessionsResult {
  deleted: string[]
  failed: string[]
}

export function useSessions(pageSize = 30) {
  const [sessions, setSessions] = useState<SessionSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const result = searchQuery.trim()
        ? await searchSessions(searchQuery.trim(), 1, pageSize)
        : await listSessions(1, pageSize)
      setSessions(result.items)
    } catch {
      setSessions([])
    } finally {
      setLoading(false)
    }
  }, [searchQuery, pageSize])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const loadSessionTurns = useCallback(async (conversationId: string): Promise<ChatTurn[]> => {
    const detail = await getSessionDetail(conversationId)
    return sessionMessagesToTurns(detail.messages)
  }, [])

  const removeSessions = useCallback(async (conversationIds: string[]): Promise<RemoveSessionsResult> => {
    const uniqueIds = [...new Set(conversationIds.filter((id) => id.trim().length > 0))]
    if (uniqueIds.length === 0) {
      return { deleted: [], failed: [] }
    }

    const results = await Promise.allSettled(uniqueIds.map((id) => deleteSession(id)))
    const deleted: string[] = []
    const failed: string[] = []
    uniqueIds.forEach((id, index) => {
      if (results[index].status === 'fulfilled') {
        deleted.push(id)
      } else {
        failed.push(id)
      }
    })

    if (deleted.length > 0) {
      setSessions((prev) => prev.filter((session) => !deleted.includes(session.conversationId)))
    }

    return { deleted, failed }
  }, [])

  return {
    sessions,
    loading,
    searchQuery,
    setSearchQuery,
    refresh,
    loadSessionTurns,
    removeSessions,
  }
}
