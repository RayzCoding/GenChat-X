import type { UnifiedStreamRequest } from '../types'
import { readAgentSseStream } from '../utils/agentStream'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

function buildUrl(path: string, params?: Record<string, string | number | undefined>): string {
  const url = new URL(`${API_BASE}${path}`, window.location.origin)
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') {
        url.searchParams.set(key, String(value))
      }
    })
  }
  return url.toString()
}

export async function streamAgent(
  request: UnifiedStreamRequest,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  const response = await fetch(buildUrl('/agent/stream'), {
    method: 'POST',
    signal,
    cache: 'no-store',
    headers: {
      Accept: 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      mode: request.mode,
      question: request.question,
      conversationId: request.conversationId,
      fileId: request.fileId,
    }),
  })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new Error(text || `HTTP ${response.status}`)
  }

  await readAgentSseStream(response, onChunk)
}

export async function streamChat(
  conversationId: string,
  question: string,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  await streamAgent({ mode: 'chat', conversationId, question }, signal, onChunk)
}

export async function stopAgent(conversationId: string): Promise<void> {
  const url = buildUrl('/agent/stop', { conversationId })
  await fetch(url)
}

export async function streamDeepResearch(
  conversationsId: string,
  question: string,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  await streamAgent({ mode: 'deep', conversationId: conversationsId, question }, signal, onChunk)
}

export async function streamPpt(
  conversationsId: string,
  question: string,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  await streamAgent({ mode: 'ppt', conversationId: conversationsId, question }, signal, onChunk)
}

export async function streamSkills(
  conversationsId: string,
  question: string,
  fileId: string | undefined,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  await streamAgent(
    { mode: 'skills', conversationId: conversationsId, question, fileId },
    signal,
    onChunk,
  )
}

export async function streamFileChat(
  conversationId: string,
  question: string,
  fileId: string,
  signal: AbortSignal,
  onChunk: (line: string) => void,
): Promise<void> {
  await streamAgent({ mode: 'file', conversationId, question, fileId }, signal, onChunk)
}
