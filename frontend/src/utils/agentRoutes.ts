import { modeFromAgentType, workbenchPath } from './agentMode'

export function getRouteForAgentType(agentType?: string, conversationId?: string): string {
  return workbenchPath(conversationId, modeFromAgentType(agentType))
}
