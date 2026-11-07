import { Link, useLocation, useNavigate } from 'react-router-dom'
import { LogOut, LogIn, Menu, User, X, ChevronDown, LayoutGrid } from 'lucide-react'
import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../context/AuthContext'
import { Avatar } from '../ui/Avatar'
import clsx from 'clsx'
import { Brand } from './Brand'
import { buttonClass } from '../ui/buttonClass'

interface NavbarProps {
  /** Laid over a full-bleed hero (the home page): transparent until the page scrolls. */
  overlay?: boolean
}

export function Navbar({ overlay = false }: NavbarProps) {
  const { t } = useTranslation('common')
  const { user, isAuthenticated, isAdmin, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const [navHidden, setNavHidden] = useState(false)
  // Read once on mount: a reload halfway down the home page must not start with a see-through bar
  // over the content until the first scroll event.
  const [scrolled, setScrolled] = useState(() => window.scrollY > 24)
  const lastScrollY = useRef(0)
  const userMenuRef = useRef<HTMLDivElement>(null)

  const isLinkActive = (path: string) =>
    path === '/' ? location.pathname === '/' : location.pathname.startsWith(path)

  // A logged-in user sees their menu (avatar + settings + logout) across the whole site; a guest sees
  // the login entry point on every page.
  const showUserArea = isAuthenticated
  const showLoginEntry = !isAuthenticated

  // We highlight "Settings" in the avatar menu when the user is on that subpage ("you are here").
  const isSettingsActive = location.pathname.startsWith('/settings')
  // Admins reach "My account" through the avatar menu (their navbar slot holds the admin panel
  // instead), so they can also use the client side.
  const isAccountActive = location.pathname.startsWith('/moje-konto')

  const navLinks = [
    { to: '/', label: t('nav.home') },
    // "My account" — the main destination for a regular user — permanently in the navbar. Admins
    // get the admin panel in this slot instead and reach "My account" from the avatar menu.
    ...(isAuthenticated && !isAdmin ? [{ to: '/moje-konto', label: t('nav.myAccount') }] : []),
    ...(isAdmin ? [{ to: '/admin', label: t('nav.admin') }] : []),
  ]

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target as Node)) {
        setUserMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  useEffect(() => {
    function onScroll() {
      const y = window.scrollY
      setScrolled(y > 24)
      if (y < 60) {
        setNavHidden(false)
      } else if (y > lastScrollY.current + 5) {
        setNavHidden(true)
      } else if (y < lastScrollY.current - 5) {
        setNavHidden(false)
      }
      lastScrollY.current = y
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  // The phone menu covers the whole screen; the page underneath must not scroll with it.
  useEffect(() => {
    if (!mobileMenuOpen) return
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previous }
  }, [mobileMenuOpen])

  const [prevPathname, setPrevPathname] = useState(location.pathname)
  if (prevPathname !== location.pathname) {
    setPrevPathname(location.pathname)
    setUserMenuOpen(false)
    setMobileMenuOpen(false)
  }

  const transparent = overlay && !scrolled && !mobileMenuOpen
  const menuItemClass = (active: boolean) => clsx(
    'w-full flex items-center gap-3 px-4 py-2.5 text-sm transition-colors',
    active ? 'bg-white/[0.06] text-white cursor-default' : 'text-surface-300 hover:bg-white/5 hover:text-white'
  )

  return (
    <>
      <nav
        className={clsx(
          // `translate`, not `transform`: Tailwind 4 moves the bar with the translate property, and
          // a transition that does not list it makes the bar jump instead of slide.
          'z-50 transition-[translate,background-color,border-color] duration-300',
          overlay ? 'fixed inset-x-0 top-0' : 'sticky top-0',
          transparent
            ? 'border-b border-transparent bg-transparent'
            : 'border-b border-white/[0.06] bg-surface-950/75 backdrop-blur-xl',
          navHidden && !mobileMenuOpen && '-translate-y-full'
        )}
      >
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <Link to="/" className="hover:opacity-80 transition-opacity" aria-label={t('nav.brandHome')}>
              <Brand />
            </Link>

            <div className="hidden md:flex items-center gap-8">
              {navLinks.map((link) => (
                <Link
                  key={link.to}
                  to={link.to}
                  aria-current={isLinkActive(link.to) ? 'page' : undefined}
                  className={clsx(
                    'relative py-1 text-sm font-medium tracking-wide transition-colors',
                    'after:absolute after:inset-x-0 after:-bottom-0.5 after:h-px after:origin-left after:bg-white after:transition-transform after:duration-300',
                    isLinkActive(link.to)
                      ? 'text-white after:scale-x-100'
                      : 'text-surface-400 hover:text-white after:scale-x-0 hover:after:scale-x-100'
                  )}
                >
                  {link.label}
                </Link>
              ))}
            </div>

            {showUserArea && (
              <div className="hidden md:flex items-center gap-2">
                <div className="relative" ref={userMenuRef}>
                  <button
                    onClick={() => setUserMenuOpen(!userMenuOpen)}
                    aria-expanded={userMenuOpen}
                    aria-haspopup="menu"
                    aria-controls="user-menu"
                    className="flex items-center gap-3 rounded-full py-1 pl-1 pr-3 hover:bg-white/5 active:scale-95 transition-all duration-150"
                  >
                    <Avatar src={user?.avatarUrl} name={user?.firstName} className="w-9 h-9" />

                    <span className="text-sm font-medium text-surface-200">{user?.firstName}</span>
                    <ChevronDown className={clsx('w-4 h-4 text-surface-400 transition-transform', userMenuOpen && 'rotate-180')} />
                  </button>

                  {userMenuOpen && (
                    <div id="user-menu" className="absolute right-0 mt-2 w-60 overflow-hidden rounded-2xl border border-white/10 bg-surface-900/95 shadow-glow backdrop-blur-xl animate-rise">
                      <div className="px-4 py-3 border-b border-white/[0.07]">
                        <p className="text-sm font-medium text-surface-100">{user?.firstName} {user?.lastName}</p>
                        <p className="text-xs text-surface-500 mt-0.5 truncate">{user?.email}</p>
                      </div>
                      <div className="py-1">
                        {isAdmin && (
                          <button
                            onClick={() => { setUserMenuOpen(false); if (!isAccountActive) navigate('/moje-konto') }}
                            aria-current={isAccountActive ? 'page' : undefined}
                            className={menuItemClass(isAccountActive)}
                          >
                            <LayoutGrid className="w-4 h-4" />
                            {t('nav.myAccount')}
                          </button>
                        )}
                        <button
                          onClick={() => { setUserMenuOpen(false); if (!isSettingsActive) navigate('/settings') }}
                          aria-current={isSettingsActive ? 'page' : undefined}
                          className={menuItemClass(isSettingsActive)}
                        >
                          <User className="w-4 h-4" />
                          {t('nav.settings')}
                        </button>
                        <button
                          onClick={() => { setUserMenuOpen(false); logout() }}
                          className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-rose-300/80 hover:bg-white/5 hover:text-rose-300 transition-colors"
                        >
                          <LogOut className="w-4 h-4" />
                          {t('nav.logout')}
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            )}

            {showLoginEntry && (
              // Hidden through a wrapper: `hidden` and the button's own `inline-flex` on one element
              // would resolve by stylesheet order, and on a phone the pill showed next to the menu.
              <div className="hidden md:block">
                <Link to="/logowanie" className={buttonClass('secondary', 'sm')}>
                  <LogIn className="w-4 h-4" />
                  {t('nav.login')}
                </Link>
              </div>
            )}

            <button
              className="md:hidden -mr-2 p-2 text-surface-100 active:scale-90 transition-transform duration-150"
              aria-label={mobileMenuOpen ? t('nav.closeMenu') : t('nav.openMenu')}
              aria-expanded={mobileMenuOpen}
              aria-controls="mobile-menu"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            >
              {mobileMenuOpen ? <X className="w-6 h-6 pointer-events-none" /> : <Menu className="w-6 h-6 pointer-events-none" />}
            </button>
          </div>
        </div>
      </nav>

      {mobileMenuOpen && (
        // Full screen under the bar: big targets for a thumb, nothing of the page behind to misread.
        // ⚠️ A sibling of <nav>, never a child: the bar's backdrop-filter makes it the containing block
        // of any position: fixed descendant, and the menu collapsed to the bar's own 0px below it.
        <nav id="mobile-menu" aria-label={t('nav.menu')} className="md:hidden fixed inset-x-0 top-16 bottom-0 z-40 overflow-y-auto bg-surface-950 animate-rise">
          <div className="flex min-h-full flex-col px-6 pb-10 pt-8">
            <div className="space-y-1">
              {navLinks.map((link, i) => (
                <Link
                  key={link.to}
                  to={link.to}
                  onClick={() => setMobileMenuOpen(false)}
                  aria-current={isLinkActive(link.to) ? 'page' : undefined}
                  className={clsx(
                    'flex items-baseline gap-4 py-2 text-display text-4xl',
                    isLinkActive(link.to) ? 'text-white' : 'text-surface-500'
                  )}
                >
                  <span aria-hidden="true" className="kicker !tracking-[0.2em]">{String(i + 1).padStart(2, '0')}</span>
                  {link.label}
                </Link>
              ))}
            </div>

            {showUserArea && (
              <div className="mt-10 pt-6 border-t border-white/[0.07] space-y-1">
                <div className="flex items-center gap-3 py-2">
                  <Avatar src={user?.avatarUrl} name={user?.firstName} className="w-10 h-10" />

                  <div className="min-w-0">
                    <p className="text-sm font-medium text-surface-100">{user?.firstName} {user?.lastName}</p>
                    <p className="text-xs text-surface-500 truncate">{user?.email}</p>
                  </div>
                </div>
                {isAdmin && (
                  <Link
                    to="/moje-konto"
                    onClick={() => setMobileMenuOpen(false)}
                    aria-current={isAccountActive ? 'page' : undefined}
                    className={clsx('flex items-center gap-3 py-3 text-base', isAccountActive ? 'text-white font-medium' : 'text-surface-300')}
                  >
                    <LayoutGrid className="w-5 h-5" />
                    {t('nav.myAccount')}
                  </Link>
                )}
                <Link
                  to="/settings"
                  onClick={() => setMobileMenuOpen(false)}
                  aria-current={isSettingsActive ? 'page' : undefined}
                  className={clsx('flex items-center gap-3 py-3 text-base', isSettingsActive ? 'text-white font-medium' : 'text-surface-300')}
                >
                  <User className="w-5 h-5" />
                  {t('nav.settings')}
                </Link>
                <button
                  onClick={() => { setMobileMenuOpen(false); logout() }}
                  className="flex items-center gap-3 py-3 text-base text-rose-300/80"
                >
                  <LogOut className="w-5 h-5" />
                  {t('nav.logout')}
                </button>
              </div>
            )}

            {showLoginEntry && (
              <Link
                to="/logowanie"
                onClick={() => setMobileMenuOpen(false)}
                className={clsx('mt-auto w-full', buttonClass('primary', 'lg'))}
              >
                <LogIn className="w-5 h-5" />
                {t('nav.login')}
              </Link>
            )}
          </div>
        </nav>
      )}
    </>
  )
}
