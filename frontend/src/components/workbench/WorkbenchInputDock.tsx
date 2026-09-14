import { useCallback, useEffect, useRef, useState, type ChangeEvent, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { AgentMode } from '../../types'
import { uploadFile } from '../../services/fileApi'
import {
  IconBolt,
  IconChart,
  IconClose,
  IconFlask,
  IconGlobe,
  IconMic,
  IconPaperclip,
  IconSend,
  IconShield,
  IconStop,
} from './icons'
import { PromptPills } from './PromptPills'

interface WorkbenchInputDockProps {
  mode: AgentMode
  onModeChange: (mode: AgentMode) => void
  onSend: (question: string, extras?: { fileId?: string }) => void
  onStop?: () => void
  isStreaming?: boolean
  disabled?: boolean
  showPills?: boolean
  attachedFileName?: string | null
  onFileUploaded?: (fileId: string, fileName: string) => void
  onClearFile?: () => void
  draft: string
  onDraftChange: (value: string) => void
  requestFilePick?: number
}

const MODE_BUTTONS: Array<{
  id: AgentMode
  icon: typeof IconGlobe
  activeClass: string
  hoverClass: string
  labelKey: string
  activeLabelKey: string
}> = [
  {
    id: 'chat',
    icon: IconGlobe,
    activeClass: 'bg-[#FAF3EA] text-[#A85A24] border-[#E8D4BE]',
    hoverClass: 'hover:text-warm-900 hover:bg-warm-100',
    labelKey: 'workbench.mode.chat',
    activeLabelKey: 'workbench.mode.chatOn',
  },
  {
    id: 'file',
    icon: IconPaperclip,
    activeClass: 'bg-[#EFF5F0] text-[#487355] border-[#BFD8C7]',
    hoverClass: 'hover:text-warm-900 hover:bg-warm-100 hover:border-[#E8E4DA]',
    labelKey: 'workbench.mode.file',
    activeLabelKey: 'workbench.mode.fileOn',
  },
  {
    id: 'deep',
    icon: IconFlask,
    activeClass: 'bg-[#FBF6FC] text-[#7E5788] border-[#EADEEB]',
    hoverClass: 'hover:text-accent-plum hover:bg-[#FBF6FC] hover:border-[#EADEEB]',
    labelKey: 'workbench.mode.deep',
    activeLabelKey: 'workbench.mode.deep',
  },
  {
    id: 'ppt',
    icon: IconChart,
    activeClass: 'bg-[#FAF5EF] text-[#B87132] border-[#ECDDCB]',
    hoverClass: 'hover:text-accent-ppt hover:bg-[#FAF5EF] hover:border-[#ECDDCB]',
    labelKey: 'workbench.mode.ppt',
    activeLabelKey: 'workbench.mode.pptOn',
  },
  {
    id: 'skills',
    icon: IconBolt,
    activeClass: 'bg-[#F2F8F8] text-[#3D7879] border-[#D0E5E5]',
    hoverClass: 'hover:text-accent-teal hover:bg-[#F2F8F8] hover:border-[#D0E5E5]',
    labelKey: 'workbench.mode.skills',
    activeLabelKey: 'workbench.mode.skillsOn',
  },
]

export function WorkbenchInputDock({
  mode,
  onModeChange,
  onSend,
  onStop,
  isStreaming,
  disabled,
  showPills,
  attachedFileName,
  onFileUploaded,
  onClearFile,
  draft,
  onDraftChange,
  requestFilePick = 0,
}: WorkbenchInputDockProps) {
  const { t } = useTranslation()
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)

  useEffect(() => {
    const el = textareaRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`
  }, [draft])

  useEffect(() => {
    if (requestFilePick > 0) {
      fileInputRef.current?.click()
    }
  }, [requestFilePick])

  const handleSend = useCallback(() => {
    const trimmed = draft.trim()
    if (!trimmed || disabled || isStreaming) return
    onSend(trimmed)
    onDraftChange('')
  }, [draft, disabled, isStreaming, onSend, onDraftChange])

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const uploadSelectedFile = useCallback(
    async (file: File) => {
      setUploading(true)
      try {
        const uploaded = await uploadFile(file)
        onFileUploaded?.(String(uploaded.id), uploaded.name)
        onModeChange('file')
      } catch {
        // keep current file
      } finally {
        setUploading(false)
        if (fileInputRef.current) fileInputRef.current.value = ''
      }
    },
    [onFileUploaded, onModeChange],
  )

  const handleFileChange = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    await uploadSelectedFile(file)
  }

  const handleModeClick = (next: AgentMode) => {
    onModeChange(next)
    if (next === 'file' && !attachedFileName) {
      fileInputRef.current?.click()
    }
  }

  const placeholder =
    mode === 'file' && !attachedFileName
      ? t('workbench.placeholderFile')
      : t('workbench.placeholder')

  return (
    <footer className="z-20 mx-auto w-full max-w-4xl select-none px-4 pb-4">
      {showPills && (
        <PromptPills
          onSelect={(nextMode, prompt) => {
            onModeChange(nextMode)
            onDraftChange(prompt)
            textareaRef.current?.focus()
          }}
        />
      )}

      {attachedFileName && (
        <div className="mb-2 flex items-center gap-2 rounded-2xl border border-[#BFD8C7] bg-[#EFF5F0] px-3 py-2 text-xs text-[#487355]">
          <IconPaperclip className="h-3.5 w-3.5 shrink-0" />
          <span className="min-w-0 truncate font-medium">{attachedFileName}</span>
          <button
            type="button"
            onClick={onClearFile}
            className="ml-auto rounded-md p-0.5 hover:bg-white/70"
            aria-label={t('workbench.removeFile')}
          >
            <IconClose className="h-3.5 w-3.5" />
          </button>
        </div>
      )}

      <div
        className="rounded-3xl border border-[#EFECE6] bg-white shadow-warm-input transition-all duration-300 focus-within:border-[#C97A3E]/60 focus-within:ring-2 focus-within:ring-[#C97A3E]/15"
        onDragOver={(e) => {
          e.preventDefault()
          e.dataTransfer.dropEffect = 'copy'
        }}
        onDrop={(e) => {
          e.preventDefault()
          const file = e.dataTransfer.files?.[0]
          if (file) void uploadSelectedFile(file)
        }}
      >
        <div className="relative px-5 pb-1 pt-3.5">
          <textarea
            ref={textareaRef}
            value={draft}
            onChange={(e) => onDraftChange(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={disabled}
            rows={2}
            placeholder={placeholder}
            className="w-full resize-none bg-transparent p-0 text-sm leading-relaxed text-warm-900 outline-none placeholder:text-warm-400 disabled:opacity-60"
          />
          <div className="pointer-events-none absolute right-5 top-3.5 flex items-center gap-1.5 text-[#C4BDAF]">
            <span className="rounded border border-[#EBE6DC] bg-warm-100 px-1.5 py-0.5 font-mono text-[10px] text-[#8C8276]">
              {t('workbench.shiftEnter')}
            </span>
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-[#F5F2EA] px-3.5 pb-3 pt-2">
          <div className="flex flex-wrap items-center gap-1.5">
            <input ref={fileInputRef} type="file" className="hidden" onChange={(e) => void handleFileChange(e)} />
            {MODE_BUTTONS.map((btn) => {
              const active = mode === btn.id
              const Icon = btn.icon
              return (
                <button
                  key={btn.id}
                  type="button"
                  disabled={uploading && btn.id === 'file'}
                  onClick={() => handleModeClick(btn.id)}
                  className={`flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-medium transition-all ${
                    active
                      ? btn.activeClass
                      : `border-transparent text-warm-500 ${btn.hoverClass}`
                  }`}
                >
                  {btn.id === 'chat' && active && (
                    <span className="h-1.5 w-1.5 rounded-full bg-accent-amber" />
                  )}
                  <Icon className={`h-3.5 w-3.5 ${active ? '' : ''}`} />
                  <span>{t(active ? btn.activeLabelKey : btn.labelKey)}</span>
                </button>
              )
            })}
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled
              title={t('workbench.voice')}
              className="flex h-8 w-8 items-center justify-center rounded-xl text-[#8C8276] opacity-50"
            >
              <IconMic className="h-4 w-4" />
            </button>
            {isStreaming ? (
              <button
                type="button"
                onClick={onStop}
                title={t('chat.stopGenerating')}
                className="flex h-8 w-8 items-center justify-center rounded-xl bg-warm-800 text-warm-50 shadow-sm ring-2 ring-[#EFEAE2] transition-all active:scale-95"
              >
                <IconStop />
              </button>
            ) : (
              <button
                type="button"
                onClick={handleSend}
                disabled={disabled || !draft.trim() || (mode === 'file' && !attachedFileName)}
                title={t('input.send')}
                className="flex h-8 w-8 items-center justify-center rounded-xl bg-gradient-to-tr from-[#38322B] to-[#5C534A] text-[#FCFAF7] shadow-sm ring-2 ring-[#EFEAE2] transition-all hover:from-[#2B2620] hover:to-[#4A423B] active:scale-95 disabled:opacity-40"
              >
                <IconSend />
              </button>
            )}
          </div>
        </div>
      </div>

      <div className="mt-2.5 flex items-center justify-center gap-2 text-center text-[11px] text-[#A89F91]">
        <span className="flex items-center gap-1.5">
          <IconShield className="h-3 w-3 shrink-0 text-accent-amber" />
          {t('workbench.disclaimer')}
        </span>
      </div>
    </footer>
  )
}
