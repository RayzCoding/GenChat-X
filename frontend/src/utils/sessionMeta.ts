export interface SessionTypeMeta {
  labelKey: string
  icon: string
  badgeClass: string
  iconWrapClass: string
  titleHoverClass: string
  actionHoverClass: string
  actionIcon: string
  actionKey: string
}

const AGENT_META: Record<string, SessionTypeMeta> = {
  webSearchReactAgent: {
    labelKey: 'history.types.aiChat',
    icon: 'chat_bubble',
    badgeClass: 'text-accent-amber',
    iconWrapClass: 'bg-[#F5F2EB] text-[#8C7A68]',
    titleHoverClass: 'group-hover/card:text-accent-amber',
    actionHoverClass: 'hover:text-accent-amber',
    actionIcon: 'chat_paste_go',
    actionKey: 'history.continueChat',
  },
  fileReactAgent: {
    labelKey: 'history.types.fileQa',
    icon: 'description',
    badgeClass: 'text-accent-sage',
    iconWrapClass: 'bg-[#EFF5F0] text-[#487355]',
    titleHoverClass: 'group-hover/card:text-accent-sage',
    actionHoverClass: 'hover:text-accent-sage',
    actionIcon: 'file_open',
    actionKey: 'history.viewDetail',
  },
  deepResearchAgent: {
    labelKey: 'history.types.research',
    icon: 'biotech',
    badgeClass: 'text-accent-plum',
    iconWrapClass: 'bg-[#F4EFF6] text-[#7E5788]',
    titleHoverClass: 'group-hover/card:text-accent-plum',
    actionHoverClass: 'hover:text-accent-plum',
    actionIcon: 'arrow_forward',
    actionKey: 'history.viewDetail',
  },
  pptBuilderAgent: {
    labelKey: 'history.types.ppt',
    icon: 'present_to_all',
    badgeClass: 'text-accent-ppt',
    iconWrapClass: 'bg-[#FAF3EA] text-[#B87132]',
    titleHoverClass: 'group-hover/card:text-accent-ppt',
    actionHoverClass: 'hover:text-accent-ppt',
    actionIcon: 'download',
    actionKey: 'history.exportPpt',
  },
  skillsReactAgent: {
    labelKey: 'history.types.skills',
    icon: 'extension',
    badgeClass: 'text-accent-teal',
    iconWrapClass: 'bg-[#EEF7F7] text-[#3D7879]',
    titleHoverClass: 'group-hover/card:text-accent-teal',
    actionHoverClass: 'hover:text-accent-teal',
    actionIcon: 'arrow_forward',
    actionKey: 'history.continueChat',
  },
}

const DEFAULT_META = AGENT_META.webSearchReactAgent

export function getSessionTypeMeta(agentType?: string): SessionTypeMeta {
  if (agentType && AGENT_META[agentType]) {
    return AGENT_META[agentType]
  }
  return DEFAULT_META
}

export function truncateText(text: string | undefined, maxLength: number): string {
  if (!text) return ''
  if (text.length <= maxLength) return text
  return `${text.slice(0, maxLength)}...`
}

export function formatSessionDate(iso: string, locale: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat(locale.startsWith('zh') ? 'zh-CN' : 'en-US', {
    year: 'numeric',
    month: locale.startsWith('zh') ? 'long' : 'short',
    day: 'numeric',
  }).format(date)
}
