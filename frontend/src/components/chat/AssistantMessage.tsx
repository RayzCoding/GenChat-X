import type { ChatTurn } from '../../types'
import { MarkdownContent } from './MarkdownContent'
import { RecommendChips } from './RecommendChips'
import { ReferenceTags } from './ReferenceTags'
import { ThinkingBlock } from './ThinkingBlock'

interface AssistantMessageProps {
  turn: ChatTurn
  onRecommendSelect?: (question: string) => void
  recommendDisabled?: boolean
}

export function AssistantMessage({
  turn,
  onRecommendSelect,
  recommendDisabled,
}: AssistantMessageProps) {
  const isStreaming = turn.status === 'streaming'
  const hasRecommendations = (turn.recommendations?.length ?? 0) > 0

  return (
    <div className="flex justify-start gap-4 duration-700 animate-in fade-in slide-in-from-left-4">
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-[#48413B]/20 bg-gradient-to-tr from-[#3D3834] via-[#574F49] to-[#8C7E72] font-serif text-sm font-bold text-[#FBF9F5] shadow-sm">
        G
      </div>

      <div className="max-w-[90%] flex-1 space-y-6">
        <ThinkingBlock
          thinking={turn.thinking ?? ''}
          toolCalls={turn.toolCalls}
          isStreaming={isStreaming && !turn.content}
        />

        {(turn.content || isStreaming) && (
          <div className="rounded-2xl border border-[#EFECE6] bg-white p-6 shadow-warm-card md:p-8">
            <MarkdownContent content={turn.content} isStreaming={isStreaming} />
            {turn.references && turn.references.length > 0 && (
              <ReferenceTags references={turn.references} />
            )}
          </div>
        )}

        {hasRecommendations && onRecommendSelect && (
          <RecommendChips
            recommendations={turn.recommendations ?? []}
            onSelect={onRecommendSelect}
            disabled={recommendDisabled}
          />
        )}
      </div>
    </div>
  )
}
