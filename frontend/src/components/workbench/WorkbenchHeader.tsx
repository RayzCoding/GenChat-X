import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { LanguageSwitcher } from '../layout/LanguageSwitcher'
import { IconBell, IconChevronDown, IconHistory, IconMenu, IconUser } from './icons'

interface WorkbenchHeaderProps {
  onToggleSidebar: () => void
  onNewChat: () => void
}

export function WorkbenchHeader({ onToggleSidebar, onNewChat }: WorkbenchHeaderProps) {
  const { t } = useTranslation()

  return (
    <header className="z-30 flex h-header-height shrink-0 select-none items-center justify-between border-b border-outline-variant bg-warm-50/90 px-4 backdrop-blur-md">
      <div className="flex items-center gap-4">
        <button
          type="button"
          onClick={onToggleSidebar}
          title={t('workbench.toggleSidebar')}
          className="rounded-xl p-1.5 text-warm-500 transition-colors hover:bg-warm-100 hover:text-warm-900"
        >
          <IconMenu className="h-5 w-5" />
        </button>
        <button type="button" onClick={onNewChat} className="flex items-center gap-2.5">
          <div className="flex h-8 w-8 select-none items-center justify-center rounded-xl border border-[#48413B]/20 bg-gradient-to-tr from-[#3D3834] via-[#574F49] to-[#8C7E72] font-serif text-base font-bold text-[#FBF9F5] shadow-sm">
            G
          </div>
          <div className="flex items-center gap-1.5">
            <span className="text-[15px] font-semibold tracking-tight text-warm-900">{t('app.name')}</span>
            <span className="rounded-full border border-[#E7E2D8] bg-warm-100 px-2 py-0.5 text-[10px] font-medium text-warm-500">
              {t('workbench.badge')}
            </span>
          </div>
        </button>
      </div>

      <div className="flex items-center gap-3">
        <LanguageSwitcher />
        <Link
          to="/history"
          title={t('topbar.history')}
          className="rounded-xl p-1.5 text-warm-500 transition-colors hover:bg-warm-100 hover:text-warm-900"
        >
          <IconHistory className="h-4 w-4" />
        </Link>
        <button
          type="button"
          title={t('topbar.notifications')}
          className="relative rounded-xl p-1.5 text-warm-500 transition-colors hover:bg-warm-100 hover:text-warm-900"
        >
          <IconBell className="h-4 w-4" />
          <span className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full bg-accent-amber ring-2 ring-warm-50" />
        </button>
        <div className="group flex cursor-pointer items-center gap-2 border-l border-outline-variant pl-2">
          <div className="flex h-7 w-7 items-center justify-center rounded-full border border-[#DDD6C8] bg-[#EAE4D9] text-[#574F49] shadow-sm">
            <IconUser className="h-3.5 w-3.5" />
          </div>
          <div className="hidden items-center gap-1 sm:flex">
            <span className="text-xs font-medium text-warm-800 transition-colors group-hover:text-accent-amber">
              {t('user.name')}
            </span>
            <IconChevronDown className="h-3.5 w-3.5 text-warm-400" />
          </div>
        </div>
      </div>
    </header>
  )
}
