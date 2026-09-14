import type { AgentMode } from '../../types'

export interface ModeMeta {
  id: AgentMode
  iconKey: 'globe' | 'doc' | 'ppt' | 'flask' | 'bolt'
  titleKey: string
  descKey: string
  actionKey: string
  accent: string
  iconBg: string
  hoverBorder: string
  badgeKey?: string
  span?: string
}

export const MODE_CARDS: ModeMeta[] = [
  {
    id: 'chat',
    iconKey: 'globe',
    titleKey: 'workbench.cards.chat.title',
    descKey: 'workbench.cards.chat.desc',
    actionKey: 'workbench.cards.chat.action',
    accent: 'text-accent-amber group-hover:text-accent-amber',
    iconBg: 'bg-[#F5F2EB] text-[#8C7A68] group-hover:bg-[#C97A3E] group-hover:text-white',
    hoverBorder: 'hover:border-[#DFCDBB]',
  },
  {
    id: 'file',
    iconKey: 'doc',
    titleKey: 'workbench.cards.file.title',
    descKey: 'workbench.cards.file.desc',
    actionKey: 'workbench.cards.file.action',
    accent: 'text-accent-sage group-hover:text-accent-sage',
    iconBg: 'bg-[#EFF5F0] text-[#487355] group-hover:bg-[#487355] group-hover:text-white',
    hoverBorder: 'hover:border-[#BFD8C7]',
  },
  {
    id: 'ppt',
    iconKey: 'ppt',
    titleKey: 'workbench.cards.ppt.title',
    descKey: 'workbench.cards.ppt.desc',
    actionKey: 'workbench.cards.ppt.action',
    accent: 'text-accent-ppt group-hover:text-accent-ppt',
    iconBg: 'bg-[#FAF3EA] text-[#B87132] group-hover:bg-[#B87132] group-hover:text-white',
    hoverBorder: 'hover:border-[#E8CEAF]',
  },
  {
    id: 'deep',
    iconKey: 'flask',
    titleKey: 'workbench.cards.deep.title',
    descKey: 'workbench.cards.deep.desc',
    actionKey: 'workbench.cards.deep.action',
    badgeKey: 'workbench.cards.deep.badge',
    accent: 'text-accent-plum group-hover:text-accent-plum',
    iconBg: 'bg-[#F4EFF6] text-[#7E5788] group-hover:bg-[#7E5788] group-hover:text-white',
    hoverBorder: 'hover:border-[#DFCDBB]',
    span: 'md:col-span-2',
  },
  {
    id: 'skills',
    iconKey: 'bolt',
    titleKey: 'workbench.cards.skills.title',
    descKey: 'workbench.cards.skills.desc',
    actionKey: 'workbench.cards.skills.action',
    accent: 'text-accent-teal group-hover:text-accent-teal',
    iconBg: 'bg-[#EEF7F7] text-[#3D7879] group-hover:bg-[#3D7879] group-hover:text-white',
    hoverBorder: 'hover:border-[#C4DFDE]',
  },
]

export interface PromptPill {
  id: string
  mode: AgentMode
  labelKey: string
  promptKey: string
}

export const PROMPT_PILLS: PromptPill[] = [
  { id: 'deep-ai', mode: 'deep', labelKey: 'workbench.pills.deepAi.label', promptKey: 'workbench.pills.deepAi.prompt' },
  { id: 'ppt-pitch', mode: 'ppt', labelKey: 'workbench.pills.pptPitch.label', promptKey: 'workbench.pills.pptPitch.prompt' },
  { id: 'chat-tech', mode: 'chat', labelKey: 'workbench.pills.chatTech.label', promptKey: 'workbench.pills.chatTech.prompt' },
  { id: 'file-pdf', mode: 'file', labelKey: 'workbench.pills.filePdf.label', promptKey: 'workbench.pills.filePdf.prompt' },
  { id: 'chat-copy', mode: 'chat', labelKey: 'workbench.pills.chatCopy.label', promptKey: 'workbench.pills.chatCopy.prompt' },
  { id: 'skills-run', mode: 'skills', labelKey: 'workbench.pills.skillsRun.label', promptKey: 'workbench.pills.skillsRun.prompt' },
]
