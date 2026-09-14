import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ToolCallStep } from '../../types'
import { Icon } from '../ui/Icon'
import { MarkdownContent } from './MarkdownContent'
import { ToolStepsList } from './ToolStepsList'

interface ThinkingBlockProps {
  thinking: string
  toolCalls?: ToolCallStep[]
  isStreaming?: boolean
}

function normalizeThinkingMarkdown(text: string): string {
  return text
    .replace(/<\/?think>/gi, '')
    .replace(/^\s+/, '')
}

export function ThinkingBlock({ thinking, toolCalls, isStreaming }: ThinkingBlockProps) {
  const { t } = useTranslation()
  const hasToolCalls = (toolCalls?.length ?? 0) > 0
  const [open, setOpen] = useState(() => Boolean(isStreaming))
  const markdown = normalizeThinkingMarkdown(thinking)

  useEffect(() => {
    setOpen(Boolean(isStreaming))
  }, [isStreaming])

  if (!thinking && !hasToolCalls && !isStreaming) return null

  return (
    <details
      className="group"
      open={open}
      onToggle={(event) => {
        setOpen(event.currentTarget.open)
      }}
    >
      <summary className="flex cursor-pointer list-none items-center gap-2 font-label-md text-primary transition-opacity hover:opacity-80 [&::-webkit-details-marker]:hidden">
        <Icon name="cyclone" className="text-sm" spin={isStreaming} />
        <span>{t('chat.thinkingProcess')}</span>
        <Icon
          name="expand_more"
          className="text-sm transition-transform group-open:rotate-180"
        />
      </summary>
      <div className="thinking-shimmer ml-6 mt-3 max-h-64 space-y-2 overflow-y-auto border-l-2 border-primary/20 pl-4">
        {hasToolCalls && <ToolStepsList steps={toolCalls!} />}
        {markdown ? (
          <MarkdownContent
            content={markdown}
            isStreaming={isStreaming}
            className="markdown-thinking"
          />
        ) : (
          isStreaming && !hasToolCalls && <p className="text-sm text-on-surface-variant">...</p>
        )}
      </div>
    </details>
  )
}

