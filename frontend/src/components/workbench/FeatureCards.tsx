import { useTranslation } from 'react-i18next'
import type { AgentMode } from '../../types'
import {
  IconArrowRight,
  IconBolt,
  IconChart,
  IconDocument,
  IconFlask,
  IconGlobe,
} from './icons'
import { MODE_CARDS } from './workbenchConfig'

const ICONS = {
  globe: IconGlobe,
  doc: IconDocument,
  ppt: IconChart,
  flask: IconFlask,
  bolt: IconBolt,
}

interface FeatureCardsProps {
  onSelectMode: (mode: AgentMode) => void
}

export function FeatureCards({ onSelectMode }: FeatureCardsProps) {
  const { t } = useTranslation()

  return (
    <div className="flex flex-1 flex-col items-center justify-center overflow-y-auto px-4 py-6">
      <div className="mb-8 max-w-2xl animate-fade-in-up text-center">
        <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-[#E7E2D8] bg-white px-3 py-1 text-xs font-medium text-[#786D5F] shadow-sm">
          <span className="h-2 w-2 rounded-full bg-accent-amber" />
          {t('workbench.greeting')}
        </div>
        <h1 className="font-serif text-3xl font-bold leading-tight tracking-tight text-warm-900 sm:text-4xl">
          {t('workbench.heroTitle')}
        </h1>
        <p className="mx-auto mt-2.5 max-w-lg text-sm font-normal text-warm-500">
          {t('workbench.heroSubtitle')}
        </p>
      </div>

      <div className="mb-5 grid w-full max-w-4xl grid-cols-1 gap-4 md:grid-cols-3">
        {MODE_CARDS.map((card) => {
          const Icon = ICONS[card.iconKey]
          return (
            <button
              key={card.id}
              type="button"
              onClick={() => onSelectMode(card.id)}
              className={`group flex flex-col justify-between rounded-2xl border border-[#EFECE6] bg-white p-6 text-left shadow-warm-card transition-all duration-300 ease-out hover:-translate-y-1.5 hover:shadow-warm-hover md:rounded-3xl ${card.hoverBorder} ${card.span ?? ''}`}
            >
              <div className="flex items-start gap-3.5">
                <div
                  className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl transition-all duration-300 group-hover:scale-110 ${card.iconBg}`}
                >
                  <Icon className="h-5 w-5" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <h4 className={`text-sm font-semibold text-warm-900 transition-colors ${card.accent}`}>
                      {t(card.titleKey)}
                    </h4>
                    {card.badgeKey && (
                      <span className="rounded-full border border-[#E7DBEB] bg-[#F5EFF7] px-2.5 py-0.5 text-[10px] font-medium text-accent-plum">
                        {t(card.badgeKey)}
                      </span>
                    )}
                  </div>
                  <p className="mt-1 text-xs leading-relaxed text-[#8C827A]">{t(card.descKey)}</p>
                </div>
              </div>
              <div
                className={`mt-4 flex items-center gap-1.5 text-xs font-semibold transition-transform group-hover:translate-x-1 ${card.accent}`}
              >
                <span>{t(card.actionKey)}</span>
                <IconArrowRight className="h-3.5 w-3.5" />
              </div>
            </button>
          )
        })}
      </div>
    </div>
  )
}
