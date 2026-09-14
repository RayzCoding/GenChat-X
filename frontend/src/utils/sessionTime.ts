import type { SessionSummary } from '../types'

export type SessionTimeGroup = 'today' | 'week' | 'older'

export function sessionTimeGroup(iso: string, now = new Date()): SessionTimeGroup {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return 'older'

  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const startOfWeek = new Date(startOfToday)
  startOfWeek.setDate(startOfWeek.getDate() - 6)

  if (date >= startOfToday) return 'today'
  if (date >= startOfWeek) return 'week'
  return 'older'
}

export function groupSessionsByTime(sessions: SessionSummary[]) {
  const groups: Record<SessionTimeGroup, SessionSummary[]> = {
    today: [],
    week: [],
    older: [],
  }
  for (const session of sessions) {
    groups[sessionTimeGroup(session.updatedAt)].push(session)
  }
  return groups
}

export function formatRelativeTime(iso: string, locale: string, now = new Date()): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''

  const diffMs = now.getTime() - date.getTime()
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  const isZh = locale.startsWith('zh')

  if (diffMs < minute) return isZh ? '刚刚' : 'just now'
  if (diffMs < hour) {
    const n = Math.max(1, Math.floor(diffMs / minute))
    return isZh ? `${n}分钟前` : `${n}m ago`
  }
  if (diffMs < day) {
    const n = Math.max(1, Math.floor(diffMs / hour))
    return isZh ? `${n}小时前` : `${n}h ago`
  }
  if (diffMs < 2 * day) return isZh ? '昨天' : 'yesterday'
  const n = Math.max(2, Math.floor(diffMs / day))
  if (n < 7) return isZh ? `${n}天前` : `${n}d ago`

  return new Intl.DateTimeFormat(isZh ? 'zh-CN' : 'en-US', {
    month: isZh ? 'numeric' : 'short',
    day: 'numeric',
  }).format(date)
}
