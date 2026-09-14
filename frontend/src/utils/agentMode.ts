import type { AgentMode } from '../types'

const AGENT_TYPE_TO_MODE: Record<string, AgentMode> = {
  webSearchReactAgent: 'chat',
  fileReactAgent: 'file',
  deepResearchAgent: 'deep',
  pptBuilderAgent: 'ppt',
  skillsReactAgent: 'skills',
}

export function modeFromAgentType(agentType?: string): AgentMode {
  if (agentType && AGENT_TYPE_TO_MODE[agentType]) {
    return AGENT_TYPE_TO_MODE[agentType]
  }
  return 'chat'
}

export function parseAgentMode(raw: string | null | undefined): AgentMode {
  switch ((raw ?? '').trim().toLowerCase()) {
    case 'file':
    case 'rag':
      return 'file'
    case 'deep':
    case 'deep_research':
      return 'deep'
    case 'ppt':
      return 'ppt'
    case 'skills':
    case 'skill':
      return 'skills'
    default:
      return 'chat'
  }
}

export function workbenchPath(conversationId?: string | null, mode?: AgentMode): string {
  const params = new URLSearchParams()
  if (mode && mode !== 'chat') {
    params.set('mode', mode)
  }
  const query = params.toString()
  const suffix = query ? `?${query}` : ''
  if (conversationId) {
    return `/c/${conversationId}${suffix}`
  }
  return `/c${suffix}`
}
