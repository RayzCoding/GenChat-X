interface UserBubbleProps {
  content: string
}

export function UserBubble({ content }: UserBubbleProps) {
  return (
    <div className="flex justify-end duration-500 animate-in fade-in slide-in-from-right-4">
      <div className="chat-bubble-user max-w-[80%] rounded-2xl border border-[#E7E2D8] bg-[#F3EFE6] px-6 py-4 shadow-sm">
        <p className="whitespace-pre-wrap font-body-md text-warm-900">{content}</p>
      </div>
    </div>
  )
}
