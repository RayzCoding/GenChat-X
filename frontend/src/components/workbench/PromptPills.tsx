import { useTranslation } from 'react-i18next'
import type { AgentMode } from '../../types'
import { PROMPT_PILLS } from './workbenchConfig'

interface PromptPillsProps {
  onSelect: (mode: AgentMode, prompt: string) => void
}

export function PromptPills({ onSelect }: PromptPillsProps) {
  const { t } = useTranslation()

  return (
    <div className="no-scrollbar mb-2.5 flex w-full items-center gap-2 overflow-x-auto py-1">
      <span className="shrink-0 pl-1 text-[11px] font-medium text-[#A89F91]">
        {t('workbench.recommended')}
      </span>
      {PROMPT_PILLS.map((pill) => (
        <button
          key={pill.id}
          type="button"
          onClick={() => onSelect(pill.mode, t(pill.promptKey))}
          className="flex shrink-0 items-center gap-1.5 rounded-full border border-warm-200 bg-white px-3 py-1.5 text-xs font-medium text-[#574F45] shadow-pill-soft transition-all hover:border-warm-400 hover:bg-[#FAF7F2] hover:text-[#2E2822] active:scale-95"
        >
          {t(pill.labelKey)}
        </button>
      ))}
    </div>
  )
}
