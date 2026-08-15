import { useCallback, useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { Component } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import {
  ArrowRight,
  BellRing,
  Check,
  ChevronDown,
  CircleHelp,
  Eye,
  EyeOff,
  KeyRound,
  LogOut,
  LockKeyhole,
  Mail,
  Moon,
  ShieldCheck,
  Send,
  Sun,
  TrendingDown,
  TrendingUp,
  UserRound,
  X,
} from 'lucide-react'
import { enablePushNotifications, disablePushNotifications, pushReady } from './main.jsx'

class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null }
  }
  static getDerivedStateFromError(error) {
    return { error }
  }
  render() {
    if (this.state.error) {
      return <div className="banner error" onClick={() => this.setState({ error: null })}>Something went wrong — tap to dismiss</div>
    }
    return this.props.children
  }
}

const DAYS = [7, 30, 90]
const fmtPrice = (n, currency = 'INR') =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency, maximumFractionDigits: 2 }).format(n)
const fmtThreshold = (n, currency = 'INR') => {
  const value = Number(n)
  if (!Number.isFinite(value) || Math.abs(value) < 1_000_000) return fmtPrice(value, currency)

  const locale = currency === 'INR' ? 'en-IN' : 'en-US'
  return new Intl.NumberFormat(locale, {
    notation: 'compact',
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(value)
}
const fmtTime = (iso) => new Date(iso).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
const timeAgo = (iso) => {
  if (!iso) return ''
  const s = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  if (s < 8) return 'just now'
  if (s < 60) return `${s}s ago`
  const m = Math.floor(s / 60)
  if (m < 90) return `${m}m ago`
  return fmtTime(iso)
}

function BrandLogo({ className = '' }) {
  return <img className={`brand-logo ${className}`} src="/logo.svg" alt="" aria-hidden="true" />
}

function MarketIcon({ market, className = '' }) {
  const isCrypto = market === 'CRYPTO'
  return (
    <span className={`market-icon ${isCrypto ? 'crypto' : 'stock'} ${className}`} aria-hidden="true">
      <svg viewBox="0 0 24 24" focusable="false">
        {isCrypto ? (
          <>
            <circle cx="12" cy="12" r="8.25" fill="none" stroke="currentColor" strokeWidth="1.8" />
            <path d="M9.5 8.5h3.25a2 2 0 0 1 0 4H10.5a2 2 0 0 0 0 4h3.75M12 6.75v10.5" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" />
          </>
        ) : (
          <>
            <path d="M5 18.5V12M10 18.5V8M15 18.5v-5M20 18.5V5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
            <path d="m4 9 5-4 5 3 6-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          </>
        )}
      </svg>
    </span>
  )
}

function AssetLogo({ market, symbol, className = '' }) {
  const [loaded, setLoaded] = useState(false)
  const [failed, setFailed] = useState(false)
  const normalizedSymbol = (symbol || '').trim().toUpperCase()
  const source = market === 'CRYPTO'
    ? `https://assets.coincap.io/assets/icons/${normalizedSymbol.toLowerCase()}@2x.png`
    : `/api/logo?market=${encodeURIComponent(market)}&symbol=${encodeURIComponent(normalizedSymbol)}`

  useEffect(() => {
    setLoaded(false)
    setFailed(false)
  }, [market, normalizedSymbol])

  return (
    <span className={`asset-logo ${className}`} aria-hidden="true">
      <MarketIcon market={market} />
      {!failed && normalizedSymbol && (
        <img
          src={source}
          alt=""
          className={loaded ? 'is-loaded' : ''}
          onLoad={() => setLoaded(true)}
          onError={() => { setFailed(true); setLoaded(false) }}
        />
      )}
    </span>
  )
}

async function api(path, options = {}) {
  const res = await fetch(path, {
    ...options,
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...options.headers },
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    const error = new Error(body.error || `Request failed (${res.status})`)
    error.status = res.status
    throw error
  }
  return res.status === 204 ? null : res.json()
}

const THEME_KEY = 'trailify-theme'
const TOUR_KEY = 'trailify-tour-v1'
const LEGACY_VISITOR_KEY = 'trailify-visitor-id'
function legacyOwnerId() {
  if (['localhost', '127.0.0.1'].includes(window.location.hostname)) return 'legacy'
  return localStorage.getItem(LEGACY_VISITOR_KEY)
}
function initialTheme() {
  const saved = localStorage.getItem(THEME_KEY)
  if (saved === 'light' || saved === 'dark') return saved
  return 'light'
}

const fromBase64Url = (value) => {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - value.length % 4) % 4)
  return Uint8Array.from(atob(base64), (character) => character.charCodeAt(0))
}
const toBase64Url = (value) => {
  const bytes = value instanceof ArrayBuffer ? new Uint8Array(value) : new Uint8Array(value.buffer, value.byteOffset, value.byteLength)
  let binary = ''
  bytes.forEach((byte) => { binary += String.fromCharCode(byte) })
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}
const credentialOptions = (options) => ({
  ...options,
  challenge: fromBase64Url(options.challenge),
  user: options.user ? { ...options.user, id: fromBase64Url(options.user.id) } : undefined,
  allowCredentials: options.allowCredentials?.map((item) => ({ ...item, id: fromBase64Url(item.id) })),
  excludeCredentials: options.excludeCredentials?.map((item) => ({ ...item, id: fromBase64Url(item.id) })),
})

export default function App() {
  const [authUser, setAuthUser] = useState(undefined)
  const [authLoading, setAuthLoading] = useState(true)
  const [theme, setTheme] = useState(initialTheme)
  const [items, setItems] = useState([])
  const [alerts, setAlerts] = useState([])
  const [tab, setTab] = useState('watch')
  const [notificationOn, setNotificationOn] = useState(false)
  const [error, setError] = useState('')
  const [claimOpen, setClaimOpen] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const [tourOpen, setTourOpen] = useState(false)
  const refreshEnabled = useRef(false)
  const refreshRunning = useRef(false)
  const refreshQueued = useRef(false)

  useEffect(() => {
    api('/api/auth/me')
      .then(setAuthUser)
      .catch((e) => { if (e.status !== 401) setError(e.message); setAuthUser(null) })
      .finally(() => setAuthLoading(false))
  }, [])

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem(THEME_KEY, theme)
  }, [theme])

  useEffect(() => {
    if (!authUser) return
    const key = `${TOUR_KEY}:${authUser.id}`
    if (!localStorage.getItem(key)) {
      setTab('watch')
      setTourOpen(true)
    }
  }, [authUser])

  useEffect(() => {
    if (!profileOpen) return undefined
    const closeProfile = (event) => {
      if (event.type === 'keydown' && event.key !== 'Escape') return
      if (event.type === 'pointerdown' && event.target.closest('.profile-menu')) return
      setProfileOpen(false)
    }
    document.addEventListener('pointerdown', closeProfile)
    document.addEventListener('keydown', closeProfile)
    return () => {
      document.removeEventListener('pointerdown', closeProfile)
      document.removeEventListener('keydown', closeProfile)
    }
  }, [profileOpen])

  useEffect(() => {
    const onPush = () => setNotificationOn(pushReady())
    window.addEventListener('pushstate', onPush)
    setNotificationOn(pushReady())
    return () => window.removeEventListener('pushstate', onPush)
  }, [])

  const refresh = useCallback(async () => {
    if (refreshRunning.current) {
      refreshQueued.current = true
      return
    }
    refreshRunning.current = true
    setError('')
    try {
      const [w, a] = await Promise.all([api('/api/watchlist'), api('/api/alerts')])
      setItems(w)
      setAlerts(a)
    } catch (e) {
      setError(e.message)
    } finally {
      refreshRunning.current = false
      if (refreshEnabled.current && refreshQueued.current) {
        refreshQueued.current = false
        setTimeout(refresh, 0)
      }
    }
  }, [])

  useEffect(() => {
    if (!authUser) return undefined
    refreshEnabled.current = true
    const refreshWhenVisible = () => {
      if (!document.hidden) refresh()
    }
    refreshWhenVisible()
    const timer = setInterval(refreshWhenVisible, 30000)
    document.addEventListener('visibilitychange', refreshWhenVisible)
    window.addEventListener('focus', refreshWhenVisible)
    return () => {
      refreshEnabled.current = false
      refreshQueued.current = false
      clearInterval(timer)
      document.removeEventListener('visibilitychange', refreshWhenVisible)
      window.removeEventListener('focus', refreshWhenVisible)
    }
  }, [refresh, authUser])

  const togglePush = async () => {
    setError('')
    try {
      if (pushReady()) await disablePushNotifications()
      else await enablePushNotifications()
      setNotificationOn(pushReady())
    } catch (e) {
      setError(e.message)
      setNotificationOn(false)
    }
  }

  const removeItem = async (id) => {
    await api(`/api/watchlist/${id}`, { method: 'DELETE' })
    refresh()
  }

  const setItemActive = async (item, active) => {
    await api(`/api/watchlist/${item.id}/active`, { method: 'PATCH', body: JSON.stringify({ active }) })
    refresh()
  }

  const deleteAlert = async (id) => {
    await api(`/api/alerts/${id}`, { method: 'DELETE' })
    refresh()
  }

  const clearAlerts = async () => {
    await api('/api/alerts', { method: 'DELETE' })
    refresh()
  }

  const logout = async () => {
    if (authUser?.guest && !window.confirm('Leave guest mode? Claim your account first if you want to keep access to this watchlist.')) return
    setError('')
    try {
      await disablePushNotifications().catch(() => {})
      await api('/api/auth/logout', { method: 'POST' })
      window.__pushEnabled = false
      window.dispatchEvent(new Event('pushstate'))
      setProfileOpen(false)
      setAuthUser(null)
    } catch (e) { setError(e.message) }
  }

  const closeTour = () => {
    if (authUser) localStorage.setItem(`${TOUR_KEY}:${authUser.id}`, 'done')
    setTourOpen(false)
  }

  const startTour = () => {
    setProfileOpen(false)
    setTab('watch')
    setTourOpen(true)
  }

  if (authLoading) {
    return <div className="app auth-loading"><BrandLogo className="logo" /><p>Loading your alerts...</p></div>
  }
  if (!authUser) {
    return <AuthScreen onAuthenticated={setAuthUser} theme={theme} setTheme={setTheme} />
  }

  return (
    <div className="app">
      <ErrorBoundary>
        <header className="header">
        <div className="brand" data-tour="brand">
          <BrandLogo className="logo" />
          <div>
            <h1>Trailify</h1>
            <p>NSE · BSE · Crypto</p>
          </div>
        </div>
        <div className="header-actions">
          <label className="theme-switch" title="Toggle theme">
            <input type="checkbox" checked={theme === 'dark'}
                   onChange={() => setTheme(theme === 'dark' ? 'light' : 'dark')} />
            <span className="tslider"><span className="knob">{theme === 'dark' ? '☾' : '☀'}</span></span>
          </label>
           <button className={`bell ${notificationOn ? 'on' : ''}`} onClick={togglePush} aria-label="Mobile notifications">
             {notificationOn ? 'ON' : 'OFF'}
           </button>
          <div className="profile-menu">
            <button className={`profile-trigger ${profileOpen ? 'open' : ''}`} type="button" onClick={() => setProfileOpen((current) => !current)} aria-expanded={profileOpen} aria-haspopup="menu" aria-label="Open profile menu" data-tour="profile">
              <UserRound size={17} /><span>{authUser.guest ? 'Guest' : 'Profile'}</span><ChevronDown size={14} />
            </button>
            <AnimatePresence>
            {profileOpen && (
              <motion.div className="profile-popover" role="menu" data-lenis-prevent initial={{ opacity: 0, y: -8, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: -6, scale: 0.98 }} transition={{ duration: 0.18, ease: 'easeOut' }}>
                <div className="profile-summary">
                  <span className="profile-avatar"><UserRound size={19} /></span>
                  <div><strong>{authUser.guest ? 'Guest profile' : 'Your profile'}</strong><span>{authUser.guest ? 'Temporary account' : authUser.email}</span></div>
                </div>
                {authUser.guest && <button className="profile-primary" type="button" role="menuitem" onClick={() => { setProfileOpen(false); setClaimOpen(true) }}><ShieldCheck size={17} /><span><strong>Claim account</strong><small>Keep access on every device</small></span></button>}
                <button className="profile-action" type="button" role="menuitem" onClick={() => { setProfileOpen(false); setTab('notifications') }}><BellRing size={17} /><span>Notification settings</span></button>
                <button className="profile-action" type="button" role="menuitem" onClick={startTour}><CircleHelp size={17} /><span>Show product tour</span></button>
                <button className="profile-action danger" type="button" role="menuitem" onClick={logout}><LogOut size={17} /><span>{authUser.guest ? 'Leave guest mode' : 'Log out'}</span></button>
              </motion.div>
            )}
            </AnimatePresence>
          </div>
        </div>
      </header>

      <AnimatePresence initial={false}>
      {authUser.guest && (
        <motion.section className="guest-banner" data-tour="account" aria-label="Guest account status" initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, height: 0, margin: 0, paddingTop: 0, paddingBottom: 0 }} transition={{ duration: 0.24, ease: 'easeOut' }}>
          <span className="guest-banner-icon"><UserRound size={18} /></span>
          <div><strong>Browsing as a guest</strong><span>Claim this account to keep access on other devices.</span></div>
          <button type="button" onClick={() => setClaimOpen(true)}>Claim account</button>
        </motion.section>
      )}
      </AnimatePresence>

      {error && <div className="banner error" onClick={() => setError('')}>{error}</div>}

      <nav className="tabs" data-tour="tabs">
        <button className={tab === 'watch' ? 'active' : ''} onClick={() => setTab('watch')}>Watchlist</button>
        <button className={tab === 'alerts' ? 'active' : ''} onClick={() => setTab('alerts')}>Alerts {alerts.length > 0 && <span className="count">{alerts.length}</span>}</button>
      </nav>

      <AnimatePresence mode="wait" initial={false}>
      {tab === 'watch' ? (
        <motion.div key="watch" className="tab-panel" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: 0.22, ease: 'easeOut' }}>
          <AddForm onAdded={refresh} />
          <WatchList items={items} onDelete={removeItem} onToggle={setItemActive} />
        </motion.div>
      ) : tab === 'alerts' ? (
        <motion.div key="alerts" className="tab-panel" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: 0.22, ease: 'easeOut' }}><AlertList alerts={alerts} onDelete={deleteAlert} onClearAll={clearAlerts} /></motion.div>
        ) : (
          <motion.div key="notifications" className="tab-panel" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: 0.22, ease: 'easeOut' }}><NotificationSettings user={authUser} notificationOn={notificationOn} onTogglePush={togglePush} /></motion.div>
        )}
      </AnimatePresence>
      <AnimatePresence>{claimOpen && <ClaimAccountDialog onClose={() => setClaimOpen(false)} onClaimed={setAuthUser} />}</AnimatePresence>
      <AnimatePresence>{tourOpen && <ProductTour user={authUser} onClose={closeTour} />}</AnimatePresence>
      </ErrorBoundary>
    </div>
  )
}

function AuthScreen({ onAuthenticated, theme, setTheme }) {
  const [mode, setMode] = useState('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [config, setConfig] = useState({ google: false, emailLinks: false, passkeys: false })
  const googleButton = useRef(null)

  useEffect(() => {
    api('/api/auth/config').then(setConfig).catch(() => {})
  }, [])

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const magicToken = params.get('magicToken')
    const resetToken = params.get('resetToken')
    if (resetToken) setMode('reset')
    if (!magicToken) return
    setBusy(true)
    api('/api/auth/magic-link/consume', {
      method: 'POST', body: JSON.stringify({ token: magicToken, legacyOwnerId: legacyOwnerId() }),
    }).then((user) => {
      window.history.replaceState({}, '', window.location.pathname)
      onAuthenticated(user)
    }).catch((e) => setError(e.message)).finally(() => setBusy(false))
  }, [onAuthenticated])

  useEffect(() => {
    if (!config.google || !config.googleClientId || !googleButton.current) return undefined
    const render = () => {
      if (!window.google?.accounts?.id || !googleButton.current) return
      googleButton.current.replaceChildren()
      window.google.accounts.id.initialize({
        client_id: config.googleClientId,
        callback: async ({ credential }) => {
          setError(''); setMessage(''); setBusy(true)
          try {
            const user = await api('/api/auth/google', {
              method: 'POST', body: JSON.stringify({ credential, legacyOwnerId: legacyOwnerId() }),
            })
            onAuthenticated(user)
          } catch (e) { setError(e.message) } finally { setBusy(false) }
        },
      })
      window.google.accounts.id.renderButton(googleButton.current, {
        theme: theme === 'dark' ? 'filled_black' : 'outline', size: 'large', width: 320, text: 'continue_with',
      })
    }
    if (window.google?.accounts?.id) { render(); return undefined }
    let script = document.querySelector('script[data-google-identity]')
    if (!script) {
      script = document.createElement('script')
      script.src = 'https://accounts.google.com/gsi/client'
      script.async = true
      script.dataset.googleIdentity = 'true'
      document.head.appendChild(script)
    }
    script.addEventListener('load', render)
    return () => script.removeEventListener('load', render)
  }, [config, onAuthenticated, theme])

  const changeMode = (nextMode) => {
    if (busy || nextMode === mode) return
    setMode(nextMode)
    setPassword('')
    setShowPassword(false)
    setError('')
    setMessage('')
  }

  const submit = async (event) => {
    event.preventDefault()
    setError('')
    setMessage('')
    setBusy(true)
    try {
      if (mode === 'magic' || mode === 'forgot') {
        const result = await api(`/api/auth/${mode === 'magic' ? 'magic-link' : 'password-reset'}/request`, {
          method: 'POST', body: JSON.stringify({ email }),
        })
        setMessage(result.message)
      } else if (mode === 'reset') {
        const token = new URLSearchParams(window.location.search).get('resetToken')
        const result = await api('/api/auth/password-reset/consume', {
          method: 'POST', body: JSON.stringify({ token, password }),
        })
        window.history.replaceState({}, '', window.location.pathname)
        setMessage(result.message)
        setMode('login')
        setPassword('')
      } else {
        const user = await api(`/api/auth/${mode === 'login' ? 'login' : 'register'}`, {
          method: 'POST', body: JSON.stringify({ email, password, legacyOwnerId: legacyOwnerId() }),
        })
        onAuthenticated(user)
      }
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const loginWithPasskey = async () => {
    setError(''); setMessage(''); setBusy(true)
    try {
      if (!window.PublicKeyCredential || !navigator.credentials) throw new Error('Passkeys are not supported in this browser')
      if (!email) throw new Error('Enter your email address first')
      const options = await api('/api/auth/passkeys/login/options', {
        method: 'POST', body: JSON.stringify({ email }),
      })
      const credential = await navigator.credentials.get({ publicKey: credentialOptions(options.publicKey) })
      const body = {
        challengeId: options.challengeId,
        rawId: toBase64Url(credential.rawId),
        clientDataJSON: toBase64Url(credential.response.clientDataJSON),
        authenticatorData: toBase64Url(credential.response.authenticatorData),
        signature: toBase64Url(credential.response.signature),
      }
      const owner = legacyOwnerId()
      const user = await api(`/api/auth/passkeys/login/finish${owner ? `?legacyOwnerId=${encodeURIComponent(owner)}` : ''}`, {
        method: 'POST', body: JSON.stringify(body),
      })
      onAuthenticated(user)
    } catch (e) {
      setError(e.name === 'NotAllowedError' ? 'Passkey sign-in was cancelled' : e.message)
    } finally { setBusy(false) }
  }

  const continueAsGuest = async () => {
    setError(''); setMessage(''); setBusy(true)
    try {
      const user = await api('/api/auth/guest', { method: 'POST' })
      onAuthenticated(user)
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }

  const emailOnly = mode === 'magic' || mode === 'forgot'
  const resetMode = mode === 'reset'

  return (
    <main className="auth-shell">
      <header className="auth-topbar">
        <div className="auth-brand">
          <BrandLogo className="auth-logo" />
          <div>
            <strong>Trailify</strong>
            <span>NSE / BSE / Crypto</span>
          </div>
        </div>
        <button
          className="auth-icon-button"
          type="button"
          onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
          aria-label={`Use ${theme === 'dark' ? 'light' : 'dark'} theme`}
          title={`Use ${theme === 'dark' ? 'light' : 'dark'} theme`}
        >
          {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
        </button>
      </header>

      <div className="auth-stage">
        <section className="auth-market" aria-label="Market alert preview">
          <div className="auth-market-copy">
            <span className="market-live"><i /> Markets monitored live</span>
            <h1>Trailify</h1>
            <p>Keep every move that matters in one focused watchlist.</p>
          </div>

          <div className="market-window">
            <div className="market-window-bar">
              <div>
                <span className="market-window-title">Watchlist</span>
                <span className="market-window-status"><i /> Live</span>
              </div>
              <span className="market-window-time">09:41</span>
            </div>

            <div className="market-focus">
              <div className="market-focus-head">
                <div className="market-symbol">
                  <span className="market-symbol-mark">B</span>
                  <div><strong>BTC</strong><span>Bitcoin</span></div>
                </div>
                <span className="market-change positive"><TrendingUp size={14} /> 4.18%</span>
              </div>
              <div className="market-price-row">
                <strong>$67,842.10</strong>
                <span>+$2,716.42 today</span>
              </div>
              <div className="market-chart" aria-hidden="true">
                <svg viewBox="0 0 520 170" preserveAspectRatio="none">
                  <defs>
                    <linearGradient id="authChartFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
                      <stop offset="100%" stopColor="currentColor" stopOpacity="0" />
                    </linearGradient>
                  </defs>
                  <path className="market-chart-fill" d="M0,145 C35,133 54,144 83,118 C112,92 135,108 166,96 C198,83 214,101 247,72 C280,43 308,67 337,52 C367,36 392,49 420,28 C452,5 477,28 520,12 L520,170 L0,170 Z" />
                  <path className="market-chart-line" d="M0,145 C35,133 54,144 83,118 C112,92 135,108 166,96 C198,83 214,101 247,72 C280,43 308,67 337,52 C367,36 392,49 420,28 C452,5 477,28 520,12" />
                </svg>
                <span className="market-chart-pulse" />
              </div>
            </div>

            <div className="market-rows">
              <div className="market-row">
                <span className="market-row-icon reliance">R</span>
                <div><strong>RELIANCE</strong><span>NSE</span></div>
                <div className="market-row-price"><strong>INR 1,402.30</strong><span className="positive"><TrendingUp size={12} /> 1.26%</span></div>
              </div>
              <div className="market-row">
                <span className="market-row-icon nifty">50</span>
                <div><strong>NIFTY 50</strong><span>NSE Index</span></div>
                <div className="market-row-price"><strong>24,334.20</strong><span className="negative"><TrendingDown size={12} /> 0.31%</span></div>
              </div>
            </div>

            <div className="market-alert-toast">
              <span><BellRing size={16} /></span>
              <div><strong>Target crossed</strong><small>BTC moved above $67,500</small></div>
              <i><Check size={13} /></i>
            </div>
          </div>
        </section>

        <section className="auth-panel">
          <div className="auth-mode" role="tablist" aria-label="Account access">
            <span className={mode === 'register' ? 'register' : ''} aria-hidden="true" />
            <button type="button" role="tab" aria-selected={mode === 'login'} onClick={() => changeMode('login')}>Sign in</button>
            <button type="button" role="tab" aria-selected={mode === 'register'} onClick={() => changeMode('register')}>Create account</button>
          </div>

          <form className={`auth-form auth-form-${mode}`} onSubmit={submit} key={mode}>
            <div className="auth-heading">
              <span className="auth-eyebrow">{mode === 'register' ? 'Start your watchlist' : resetMode ? 'Account recovery' : 'Account access'}</span>
              <h2>{mode === 'register' ? 'Create your account' : mode === 'magic' ? 'Email me a sign-in link' : mode === 'forgot' ? 'Reset your password' : resetMode ? 'Choose a new password' : 'Welcome back'}</h2>
              <p>{mode === 'register' ? 'One account keeps your alerts synced across devices.' : emailOnly ? 'We will send a secure, single-use link to your inbox.' : resetMode ? 'Use at least eight characters for your new password.' : 'Choose the fastest way back to your watchlist.'}</p>
            </div>

            {mode === 'login' && config.google && <div ref={googleButton} className="google-signin" aria-label="Continue with Google" />}
            {mode === 'login' && config.google && <div className="auth-divider"><span>or</span></div>}

            <div className="auth-fields">
              {!resetMode && <label className="auth-field">
                <span>Email address</span>
                <div className="auth-input-wrap">
                  <Mail size={18} aria-hidden="true" />
                  <input
                    type="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    autoComplete="email"
                    placeholder="you@example.com"
                    disabled={busy}
                    required
                  />
                </div>
              </label>}

              {!emailOnly && <label className="auth-field">
                <span>Password</span>
                <div className="auth-input-wrap">
                  <LockKeyhole size={18} aria-hidden="true" />
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    minLength={8}
                    maxLength={128}
                    autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                    placeholder={mode === 'login' ? 'Enter your password' : 'At least 8 characters'}
                    disabled={busy}
                    required
                  />
                  <button
                    className="password-toggle"
                    type="button"
                    onClick={() => setShowPassword((current) => !current)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    title={showPassword ? 'Hide password' : 'Show password'}
                    tabIndex={-1}
                  >
                    {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                  </button>
                </div>
              </label>}
            </div>

            {(mode === 'register' || resetMode) && (
              <div className={`password-rule ${password.length >= 8 ? 'valid' : ''}`} aria-live="polite">
                <span><Check size={12} /></span>
                Use 8 or more characters
              </div>
            )}

            {error && <div className="auth-error" role="alert">{error}</div>}
            {message && <div className="auth-success" role="status">{message}</div>}

            <button className="auth-submit" disabled={busy}>
              <span>{busy ? 'Please wait' : mode === 'login' ? 'Sign in' : mode === 'register' ? 'Create account' : resetMode ? 'Update password' : 'Send secure link'}</span>
              {busy ? <i className="auth-spinner" aria-hidden="true" /> : emailOnly ? <Send size={17} /> : <ArrowRight size={18} />}
            </button>

            {mode === 'login' && config.passkeys && <button className="auth-secondary" type="button" onClick={loginWithPasskey} disabled={busy}><KeyRound size={17} /> Sign in with a passkey</button>}
            {mode === 'login' && <div className="auth-divider"><span>or explore first</span></div>}
            {mode === 'login' && <button className="auth-guest" type="button" onClick={continueAsGuest} disabled={busy}><UserRound size={17} /> Continue as guest</button>}
            {mode === 'login' && <p className="auth-guest-note">No account needed. Claim your watchlist later to use it on other devices.</p>}
            {mode === 'login' && <div className="auth-links">
              {config.emailLinks && <button type="button" onClick={() => changeMode('magic')}>Email me a sign-in link</button>}
              {config.emailLinks && <button type="button" onClick={() => changeMode('forgot')}>Forgot password?</button>}
            </div>}
            {(emailOnly || resetMode) && <button className="auth-back" type="button" onClick={() => changeMode('login')}>Back to sign in</button>}

            <div className="auth-session-note">
              <ShieldCheck size={16} />
              <span>Protected with an encrypted, HttpOnly session.</span>
            </div>
          </form>
        </section>
      </div>
    </main>
  )
}

function ClaimAccountDialog({ onClose, onClaimed }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const onKeyDown = (event) => { if (event.key === 'Escape' && !busy) onClose() }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [busy, onClose])

  const submit = async (event) => {
    event.preventDefault()
    setError('')
    if (password !== confirmPassword) {
      setError('Passwords do not match')
      return
    }
    setBusy(true)
    try {
      const user = await api('/api/auth/claim', {
        method: 'POST', body: JSON.stringify({ email, password }),
      })
      onClaimed(user)
      onClose()
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }

  return (
    <motion.div className="modal-layer" role="presentation" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.18 }}>
      <button className="modal-backdrop" type="button" onClick={onClose} disabled={busy} aria-label="Close claim account dialog" />
      <motion.section className="claim-dialog" role="dialog" aria-modal="true" aria-labelledby="claim-title" data-lenis-prevent initial={{ opacity: 0, y: 18, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: 10, scale: 0.98 }} transition={{ type: 'spring', stiffness: 360, damping: 28 }}>
        <div className="modal-heading">
          <div><span className="auth-eyebrow">Save your progress</span><h2 id="claim-title">Claim your account</h2></div>
          <button className="modal-close" type="button" onClick={onClose} disabled={busy} aria-label="Close"><X size={19} /></button>
        </div>
        <p className="claim-intro">Your watchlist and alert history stay exactly as they are. You will also be able to sign in on another device.</p>
        <form className="claim-form" onSubmit={submit}>
          <label className="auth-field"><span>Email address</span><div className="auth-input-wrap"><Mail size={18} /><input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" placeholder="you@example.com" autoFocus required disabled={busy} /></div></label>
          <label className="auth-field"><span>Create a password</span><div className="auth-input-wrap"><LockKeyhole size={18} /><input type={showPassword ? 'text' : 'password'} value={password} onChange={(event) => setPassword(event.target.value)} minLength={8} maxLength={128} autoComplete="new-password" placeholder="At least 8 characters" required disabled={busy} /><button className="password-toggle" type="button" onClick={() => setShowPassword((current) => !current)} aria-label={showPassword ? 'Hide password' : 'Show password'}><span>{showPassword ? <EyeOff size={18} /> : <Eye size={18} />}</span></button></div></label>
          <label className="auth-field"><span>Confirm password</span><div className="auth-input-wrap"><ShieldCheck size={18} /><input type={showPassword ? 'text' : 'password'} value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} minLength={8} maxLength={128} autoComplete="new-password" placeholder="Enter it again" required disabled={busy} /></div></label>
          {error && <div className="auth-error" role="alert">{error}</div>}
          <button className="auth-submit" disabled={busy || password.length < 8}><span>{busy ? 'Saving your account' : 'Claim account'}</span>{busy ? <i className="auth-spinner" /> : <ArrowRight size={18} />}</button>
        </form>
      </motion.section>
    </motion.div>
  )
}

function ProductTour({ user, onClose }) {
  const steps = useMemo(() => [
    { target: '[data-tour="brand"]', label: 'Welcome to Trailify', title: 'Your market alerts, in one place', copy: 'Track Indian stocks and crypto without switching between apps.' },
    { target: '[data-tour="add"]', label: 'Build your watchlist', title: 'Search, choose a trigger, and add', copy: 'Pick a market, find an asset, then set the price or percentage move that matters to you.' },
    { target: '[data-tour="tabs"]', label: 'Stay organized', title: 'Everything is one tap away', copy: 'Review live prices and triggered alerts from the two main views. Notification channels are available from Profile.' },
    user.guest
      ? { target: '[data-tour="account"]', label: 'Guest mode', title: 'Save this watchlist when you are ready', copy: 'Claiming keeps everything you add and lets you sign in from another device.' }
      : { target: '[data-tour="profile"]', label: 'Your profile', title: 'Manage notifications from Profile', copy: 'Open Profile anytime to manage browser alerts, email delivery, passkeys, and your session.' },
  ], [user.guest])
  const [index, setIndex] = useState(0)
  const [position, setPosition] = useState(null)
  const panel = useRef(null)

  const moveTo = (nextIndex) => {
    setPosition(null)
    setIndex(nextIndex)
  }

  useLayoutEffect(() => {
    const target = document.querySelector(steps[index].target)
    if (!target) return undefined
    target.classList.add('tour-focus')

    const place = () => {
      const viewportPadding = 12
      const targetGap = 14
      let targetRect = target.getBoundingClientRect()
      if (targetRect.bottom < viewportPadding || targetRect.top > window.innerHeight - viewportPadding) {
        target.scrollIntoView({ block: 'center', behavior: 'auto' })
        targetRect = target.getBoundingClientRect()
      }

      const panelElement = panel.current
      const panelRect = panelElement?.getBoundingClientRect()
      const width = panelRect?.width || Math.min(360, window.innerWidth - viewportPadding * 2)
      const naturalHeight = panelElement?.scrollHeight || panelRect?.height || 210
      const centeredLeft = Math.max(viewportPadding, Math.min(window.innerWidth - width - viewportPadding,
        targetRect.left + targetRect.width / 2 - width / 2))
      const belowSpace = window.innerHeight - viewportPadding - targetRect.bottom - targetGap
      const aboveSpace = targetRect.top - targetGap - viewportPadding

      if (belowSpace >= naturalHeight) {
        setPosition({ top: targetRect.bottom + targetGap, left: centeredLeft, maxHeight: naturalHeight })
        return
      }
      if (aboveSpace >= naturalHeight) {
        setPosition({ top: targetRect.top - targetGap - naturalHeight, left: centeredLeft, maxHeight: naturalHeight })
        return
      }

      const rightSpace = window.innerWidth - viewportPadding - targetRect.right - targetGap
      const leftSpace = targetRect.left - targetGap - viewportPadding
      const availableHeight = window.innerHeight - viewportPadding * 2
      const sideTop = Math.max(viewportPadding, Math.min(window.innerHeight - Math.min(naturalHeight, availableHeight) - viewportPadding,
        targetRect.top + targetRect.height / 2 - naturalHeight / 2))
      if (rightSpace >= width) {
        setPosition({ top: sideTop, left: targetRect.right + targetGap, maxHeight: availableHeight })
        return
      }
      if (leftSpace >= width) {
        setPosition({ top: sideTop, left: targetRect.left - targetGap - width, maxHeight: availableHeight })
        return
      }

      const placeBelow = belowSpace >= aboveSpace
      const maxHeight = Math.max(0, placeBelow ? belowSpace : aboveSpace)
      setPosition({
        top: placeBelow ? targetRect.bottom + targetGap : viewportPadding,
        left: centeredLeft,
        maxHeight,
      })
    }

    place()
    window.addEventListener('resize', place)
    window.addEventListener('scroll', place, true)
    return () => {
      target.classList.remove('tour-focus')
      window.removeEventListener('resize', place)
      window.removeEventListener('scroll', place, true)
    }
  }, [index, steps])

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === 'Escape') onClose()
      if (event.key === 'ArrowRight' && index < steps.length - 1) moveTo(index + 1)
      if (event.key === 'ArrowLeft' && index > 0) moveTo(index - 1)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [index, onClose, steps.length])

  const step = steps[index]
  return (
    <motion.div className="tour-layer" role="presentation" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.2 }}>
      <div className="tour-shade" />
      <motion.section ref={panel} className="tour-popover" style={position || { top: 0, left: 0, visibility: 'hidden' }} role="dialog" aria-modal="true" aria-labelledby="tour-title" data-lenis-prevent initial={{ opacity: 0, y: 8, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }} transition={{ type: 'spring', stiffness: 380, damping: 30 }}>
        <div className="tour-topline"><span>{step.label}</span><button type="button" onClick={onClose}>Skip tour</button></div>
        <h2 id="tour-title">{step.title}</h2>
        <p>{step.copy}</p>
        <div className="tour-footer"><div className="tour-progress" aria-label={`Step ${index + 1} of ${steps.length}`}>{steps.map((_, itemIndex) => <i key={itemIndex} className={itemIndex === index ? 'active' : ''} />)}</div><div className="tour-actions">{index > 0 && <button className="tour-back" type="button" onClick={() => moveTo(index - 1)}>Back</button>}<button className="tour-next" type="button" onClick={() => index === steps.length - 1 ? onClose() : moveTo(index + 1)}>{index === steps.length - 1 ? 'Done' : 'Next'}{index < steps.length - 1 && <ArrowRight size={16} />}</button></div></div>
      </motion.section>
    </motion.div>
  )
}

function NotificationSettings({ user, notificationOn, onTogglePush }) {
  const [channels, setChannels] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const refresh = useCallback(() => {
    setLoading(true)
    api('/api/notification-preferences')
      .then(setChannels)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { refresh() }, [refresh, notificationOn])

  const update = async (channel, enabled, destination) => {
    setError('')
    try {
      const next = await api(`/api/notification-preferences/${channel}`, {
        method: 'PUT', body: JSON.stringify({ enabled, destination }),
      })
      setChannels((current) => current.map((item) => item.channel === channel ? next : item))
    } catch (e) { setError(e.message) }
  }

  return (
    <section className="notification-settings">
      <div className="card settings-intro"><h2>Notification channels</h2><p>Choose one or more destinations. Every alert is delivered independently, with automatic retries for temporary outages.</p></div>
      {error && <div className="banner error">{error}</div>}
      {loading ? <div className="hint">Loading channels...</div> : channels.map((channel) => (
        <NotificationChannelCard key={channel.channel} channel={channel} user={user} onUpdate={update} onTogglePush={onTogglePush} notificationOn={notificationOn} />
      ))}
      {!user.guest && <PasskeySettings />}
    </section>
  )
}

function PasskeySettings() {
  const [passkeys, setPasskeys] = useState([])
  const [available, setAvailable] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const refresh = useCallback(() => {
    api('/api/auth/config').then((value) => {
      const supported = value.passkeys && Boolean(window.PublicKeyCredential && navigator.credentials)
      setAvailable(supported)
      if (supported) return api('/api/auth/passkeys').then(setPasskeys)
      return undefined
    }).catch((e) => setError(e.message))
  }, [])

  useEffect(() => { refresh() }, [refresh])

  const addPasskey = async () => {
    setError(''); setBusy(true)
    try {
      const options = await api('/api/auth/passkeys/register/options', { method: 'POST' })
      const credential = await navigator.credentials.create({ publicKey: credentialOptions(options.publicKey) })
      const response = credential.response
      await api('/api/auth/passkeys/register/finish', {
        method: 'POST', body: JSON.stringify({
          challengeId: options.challengeId,
          rawId: toBase64Url(credential.rawId),
          clientDataJSON: toBase64Url(response.clientDataJSON),
          attestationObject: toBase64Url(response.attestationObject),
          transports: response.getTransports?.() || [],
          name: `Passkey ${passkeys.length + 1}`,
        }),
      })
      refresh()
    } catch (e) {
      setError(e.name === 'NotAllowedError' ? 'Passkey setup was cancelled' : e.message)
    } finally { setBusy(false) }
  }

  const removePasskey = async (id) => {
    setError(''); setBusy(true)
    try {
      await api(`/api/auth/passkeys/${id}`, { method: 'DELETE' })
      setPasskeys((current) => current.filter((item) => item.id !== id))
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }

  return (
    <div className={`card channel-card ${available ? '' : 'unavailable'}`}>
      <div className="channel-heading"><div><h3>Passkeys</h3><p>Use your fingerprint, face, screen lock, or security key for phishing-resistant sign-in.</p></div><span className={`channel-status ${passkeys.length ? 'enabled' : ''}`}>{passkeys.length ? `${passkeys.length} saved` : 'Off'}</span></div>
      {passkeys.map((passkey) => <div className="passkey-row" key={passkey.id}><span><KeyRound size={16} />{passkey.name}</span><button className="ghost danger" type="button" disabled={busy} onClick={() => removePasskey(passkey.id)}>Remove</button></div>)}
      {error && <p className="channel-help">{error}</p>}
      <div className="channel-actions"><button className="ghost" type="button" onClick={addPasskey} disabled={!available || busy}>{busy ? 'Please wait...' : 'Add a passkey'}</button></div>
      {!available && <p className="channel-help">Passkeys require HTTPS and a supported browser.</p>}
    </div>
  )
}

function NotificationChannelCard({ channel, user, onUpdate, onTogglePush, notificationOn }) {
  const [destination, setDestination] = useState(channel.destination || '')
  const [busy, setBusy] = useState(false)
  useEffect(() => setDestination(channel.destination || ''), [channel.destination])
  const isPush = channel.channel === 'WEB_PUSH'
  const label = { WEB_PUSH: 'Mobile / browser', EMAIL: 'Email', TELEGRAM: 'Telegram', DISCORD: 'Discord' }[channel.channel]
  const description = {
    WEB_PUSH: 'Instant notifications on your phone or desktop browser.',
    EMAIL: user.guest ? 'Claim your account to receive alerts by email.' : `Alerts will be sent to ${user.email}.`,
    TELEGRAM: 'Enter the chat ID after starting the configured Telegram bot.',
    DISCORD: 'Paste an HTTPS incoming webhook URL for your Discord channel.',
  }[channel.channel]

  const save = async (enabled) => {
    setBusy(true)
    try { await onUpdate(channel.channel, enabled, destination) } finally { setBusy(false) }
  }

  return (
    <div className={`card channel-card ${!channel.available ? 'unavailable' : ''}`}>
      <div className="channel-heading"><div><h3>{label}</h3><p>{description}</p></div><span className={`channel-status ${channel.enabled ? 'enabled' : ''}`}>{channel.enabled ? 'Enabled' : 'Off'}</span></div>
      {!isPush && channel.channel !== 'EMAIL' && <input value={destination} onChange={(e) => setDestination(e.target.value)} placeholder={channel.channel === 'TELEGRAM' ? 'Chat ID or @channel' : 'https://discord.com/api/webhooks/...'} disabled={!channel.available || busy} />}
      <div className="channel-actions">
        {isPush ? <button className={`ghost ${notificationOn ? 'active-action' : ''}`} onClick={onTogglePush} disabled={!channel.available}>{notificationOn ? 'Turn off mobile notifications' : 'Enable mobile notifications'}</button> :
          <button className="ghost" onClick={() => save(!channel.enabled)} disabled={!channel.available || busy}>{channel.enabled ? 'Disable' : 'Enable'}</button>}
      </div>
      {!channel.available && <p className="channel-help">{channel.help}</p>}
    </div>
  )
}

function AddForm({ onAdded }) {
  const [market, setMarket] = useState('NSE')
  const [query, setQuery] = useState('')
  const [name, setName] = useState('')
  const [triggerMode, setTriggerMode] = useState('BELOW')
  const [threshold, setThreshold] = useState('')
  const currency = market === 'CRYPTO' ? 'USD' : 'INR'
  const [suggestions, setSuggestions] = useState([])
  const [open, setOpen] = useState(false)
  const [searching, setSearching] = useState(false)
  const [searchMessage, setSearchMessage] = useState('')
  const [picked, setPicked] = useState(null)
  const [preview, setPreview] = useState(null)
  const [quoteLoading, setQuoteLoading] = useState(false)
  const focused = useRef(false)
  const blurTimer = useRef(null)
  const quoteRequestId = useRef(0)
  const [err, setErr] = useState('')

  useEffect(() => {
    const q = query.trim()
    if (!q) {
      setSuggestions([])
      setOpen(false)
      setSearching(false)
      setSearchMessage('')
      return
    }
    const pickedSym = picked && picked.symbol
    if (pickedSym && q.toUpperCase() === pickedSym) {
      setSuggestions([])
      setOpen(false)
      setSearching(false)
      setSearchMessage('')
      return
    }
    let alive = true
    const controller = new AbortController()
    setSearching(true)
    setSearchMessage('')
    const t = setTimeout(() => {
      api(`/api/search?q=${encodeURIComponent(q)}&market=${market}`, {
        signal: controller.signal,
      })
        .then((s) => {
          if (!alive) return
          const next = Array.isArray(s) ? s : []
          setSuggestions(next)
          setSearchMessage(next.length ? '' : 'No matching symbols found')
          if (focused.current) setOpen(true)
        })
        .catch(() => {
          if (!alive) return
          setSuggestions([])
          setSearchMessage('Suggestions are temporarily unavailable')
          if (focused.current) setOpen(true)
        })
        .finally(() => alive && setSearching(false))
    }, 250)
    return () => {
      alive = false
      controller.abort()
      clearTimeout(t)
      setSearching(false)
    }
  }, [query, market, picked])

  const closeSoon = () => {
    clearTimeout(blurTimer.current)
    blurTimer.current = setTimeout(() => setOpen(false), 150)
  }

  const pick = async (s) => {
    const requestId = ++quoteRequestId.current
    setPicked(s)
    setQuery(s.symbol)
    setSuggestions([])
    setOpen(false)
    setPreview(null)
    setQuoteLoading(true)
    setErr('')
    try {
      const q = await api(`/api/quote?market=${market}&symbol=${encodeURIComponent(s.symbol)}&currency=${currency.toLowerCase()}`)
      if (quoteRequestId.current === requestId) setPreview(q)
    } catch (e) {
      if (quoteRequestId.current === requestId) setErr(e.message)
    } finally {
      if (quoteRequestId.current === requestId) setQuoteLoading(false)
    }
  }

  const submit = async (e) => {
    e.preventDefault()
    setErr('')
    const val = parseFloat(threshold)
    if (!picked) {
      setErr('Select a stock or coin from the suggestion list')
      return
    }
    const sym = picked.symbol
    if (!Number.isFinite(val) || val <= 0) {
      setErr('Enter a valid threshold')
      return
    }
    try {
      await api('/api/watchlist', {
        method: 'POST',
        body: JSON.stringify({
          symbol: sym, name: name.trim() || (picked ? picked.name : null), market,
          triggerType: triggerMode === 'PERCENT' || triggerMode === 'PERCENT_UP' ? 'PERCENT' : 'PRICE',
          direction: triggerMode === 'ABOVE' || triggerMode === 'PERCENT_UP' ? 'ABOVE' : 'BELOW',
          thresholdValue: val, currency,
        }),
      })
      setQuery('')
      setName('')
      setThreshold('')
      setPicked(null)
      setPreview(null)
      setQuoteLoading(false)
      setSuggestions([])
      setOpen(false)
      setSearchMessage('')
      onAdded()
    } catch (e2) {
      setErr(e2.message)
    }
  }

  return (
    <form className="card add-form" onSubmit={submit}>
      <h2 data-tour="add">Add watch item</h2>
      <div className="searchbox">
        <select className="market-select" value={market} onChange={(e) => {
          quoteRequestId.current += 1
          setMarket(e.target.value)
          setQuery('')
          setName('')
          setPicked(null)
          setPreview(null)
          setQuoteLoading(false)
          setSuggestions([])
          setOpen(false)
          setSearchMessage('')
          setErr('')
        }}>
          <option value="NSE">Stock · NSE (₹)</option>
          <option value="BSE">Stock · BSE (₹)</option>
          <option value="CRYPTO">Crypto ($)</option>
        </select>
        <div className="search-input-wrap">
          <MarketIcon market={market} />
          <input placeholder={market === 'CRYPTO' ? 'Search coins... (BTC, ETH, DOGE)' : 'Search stocks... (RELIANCE, TCS)'} value={query}
               onChange={(e) => {
                 quoteRequestId.current += 1
                 setQuery(e.target.value)
                 setPicked(null)
                 setPreview(null)
                 setQuoteLoading(false)
                 setErr('')
               }}
               onFocus={() => { focused.current = true; if (query.trim()) setOpen(true) }}
               onBlur={() => { focused.current = false; closeSoon() }}
               onKeyDown={(e) => {
                 if (e.key === 'Escape') setOpen(false)
                 if (e.key === 'Enter' && open && suggestions.length > 0) {
                   e.preventDefault()
                   pick(suggestions[0])
                 }
               }}
               autoComplete="off" />
        </div>
        {open && (searching || suggestions.length > 0 || searchMessage) && (
          <ul className="suggest" data-lenis-prevent onMouseDown={(e) => e.preventDefault()}>
            {searching && <li className="suggest-status">Searching {market === 'CRYPTO' ? 'coins' : 'stocks'}...</li>}
            {!searching && suggestions.map((s, i) => (
              <li key={`${s.symbol}-${i}`} onMouseDown={() => pick(s)}>
                <AssetLogo market={s.market || market} symbol={s.symbol} className="suggest-icon" />
                <span className="suggest-copy">
                  <span className="s-sym">{s.symbol}</span>
                  <span className="s-name">{s.name}</span>
                </span>
              </li>
            ))}
            {!searching && suggestions.length === 0 && searchMessage && <li className="suggest-status">{searchMessage}</li>}
          </ul>
        )}
      </div>
      {quoteLoading && <div className="hint">Fetching live price...</div>}
      {preview && (
        <div className="preview">
          <AssetLogo market={market} symbol={preview.symbol} className="preview-logo" />
          <span className="pv-name">{preview.displayName || preview.symbol}</span>
          <strong>{fmtPrice(preview.price, preview.currency)}</strong>
          <span className="src-badge">
            {preview.source}{preview.fetchedAt ? ` · ${timeAgo(preview.fetchedAt)}` : ''}
          </span>
        </div>
      )}
      <input placeholder="Display name (optional)" value={name} onChange={(e) => setName(e.target.value)} />
      <select className="threshold" value={triggerMode} onChange={(e) => setTriggerMode(e.target.value)}>
        <option value="BELOW">Alert when price drops below</option>
        <option value="ABOVE">Alert when price rises above</option>
        <option value="PERCENT">Alert when price drops by %</option>
        <option value="PERCENT_UP">Alert when price rises by %</option>
      </select>
      <input type="number" step="any" min="0" className="threshold"
             placeholder={triggerMode === 'PERCENT' ? 'Drop percent (%)' : triggerMode === 'PERCENT_UP' ? 'Rise percent (%)' : `Threshold price (${currency})`}
             value={threshold} onChange={(e) => setThreshold(e.target.value)} />
      {err && <div className="banner error">{err}</div>}
      <button className="primary" type="submit">Add to watchlist</button>
    </form>
  )
}

function WatchList({ items, onDelete, onToggle }) {
  if (items.length === 0) {
    return <p className="empty">Nothing watched yet. Search a coin or stock above — you'll be alerted the moment it drops.</p>
  }
  return (
    <motion.ul className="watch-list" initial="hidden" animate="visible" variants={{ hidden: {}, visible: { transition: { staggerChildren: 0.06 } } }}>
      <AnimatePresence initial={false}>
        {items.map((item) => (
          <WatchCard key={item.id} item={item} onDelete={onDelete} onToggle={onToggle} />
        ))}
      </AnimatePresence>
    </motion.ul>
  )
}

function WatchCard({ item, onDelete, onToggle }) {
  const [expanded, setExpanded] = useState(false)
  const cur = item.currency || 'INR'
  const rises = (item.direction || 'BELOW') === 'ABOVE'
  const isPct = item.triggerType === 'PERCENT'
  const hit = item.lastPrice != null && !isPct &&
    (rises ? item.lastPrice >= item.thresholdValue : item.lastPrice <= item.thresholdValue)
  const movePct = item.lastPrice != null && item.previousPrice != null && item.previousPrice > 0
    ? ((item.lastPrice - item.previousPrice) / item.previousPrice) * 100
    : null
  return (
    <motion.li className={`card watch-card ${!item.active ? 'paused' : ''}`} variants={{ hidden: { opacity: 0, y: 12 }, visible: { opacity: 1, y: 0, transition: { duration: 0.28, ease: 'easeOut' } } }} exit={{ opacity: 0, scale: 0.97 }} layout>
      <button className="card-main" onClick={() => setExpanded((v) => !v)}>
        <div className="item-top">
          <AssetLogo market={item.market} symbol={item.symbol} className="watch-market-icon" />
          <span className="mk">{item.market}</span>
          <div className="sym-col">
            <strong className="sym">{item.symbol}</strong>
            <span className="item-sub">{item.name}</span>
          </div>
          <div className="price-col">
            {item.lastPrice != null
              ? <>
                  <span className="price">{fmtPrice(item.lastPrice, cur)}
                    {movePct != null && (
                      <span className={`move ${movePct >= 0 ? 'up' : 'down'}`}>{movePct >= 0 ? '▲' : '▼'} {Math.abs(movePct).toFixed(2)}%</span>
                    )}
                  </span>
                  <span className="price-sub">{item.lastSource}{item.lastFetchedAt ? ` · ${timeAgo(item.lastFetchedAt)}` : ''}</span>
                </>
              : <span className="noprice">fetching...</span>}
          </div>
          <span className="chevron">{expanded ? '⌃' : '⌄'}</span>
        </div>
        <div className="item-meta">
          <span className="tag">
            {isPct
              ? (rises ? `rise >= ${item.thresholdValue}%` : `drop >= ${item.thresholdValue}%`)
              : (rises ? `above ${fmtThreshold(item.thresholdValue, cur)}` : `below ${fmtThreshold(item.thresholdValue, cur)}`)}
          </span>
          {hit && <span className="tag tag-warn">hit</span>}
          {!item.active && item.lastAlertedAt && <span className="tag tag-warn">triggered · paused</span>}
        </div>
      </button>
      <AnimatePresence initial={false}>{expanded && <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }} transition={{ duration: 0.24, ease: 'easeOut' }}><ChartPanel item={item} /></motion.div>}</AnimatePresence>
      <div className="item-actions">
        <label className="switch">
          <input type="checkbox" checked={item.active} onChange={(e) => onToggle(item, e.target.checked)} />
          <span className="slider" />
          <span className="switch-label">{item.active ? 'On' : 'Paused'}</span>
        </label>
        <button className="ghost danger" onClick={() => onDelete(item.id)}>Remove</button>
      </div>
    </motion.li>
  )
}

function ChartPanel({ item }) {
  const [days, setDays] = useState(30)
  const [chart, setChart] = useState(null)
  const [err, setErr] = useState('')
  const cache = useRef(new Map())
  const cur = (item.currency || 'INR').toLowerCase()
  const pts = chart?.points

  useEffect(() => {
    setErr('')
    if (cache.current.has(days)) { setChart(cache.current.get(days)); return }
    let alive = true
    api(`/api/chart?market=${item.market}&symbol=${encodeURIComponent(item.symbol)}&days=${days}&currency=${cur}`)
      .then((c) => { cache.current.set(days, c); if (alive) setChart(c) })
      .catch((e) => alive && setErr(e.message))
    return () => { alive = false }
  }, [days, item.market, item.symbol, cur])

  return (
    <div className="chart-panel">
      <div className="chart-tabs">
        {DAYS.map((d) => (
          <button key={d} className={days === d ? 'active' : ''} onClick={() => setDays(d)}>{d}D</button>
        ))}
      </div>
      {Array.isArray(pts) && pts.length > 1 && (
        <div className="chart-wrap">
          <Sparkline points={pts} height={110} />
          <div className="chart-stat">
            <span>{fmtPrice(pts[pts.length - 1][1], chart.currency)}</span>
            <ChangeBadge from={pts[0][1]} to={pts[pts.length - 1][1]} />
          </div>
        </div>
      )}
      {err && <div className="banner error">{err}</div>}
      {chart && !err && (!Array.isArray(pts) || pts.length <= 1) && (
        <div className="hint">No chart data for this symbol</div>
      )}
    </div>
  )
}

function Sparkline({ points, height = 110 }) {
  const [w, setW] = useState(null)
  const ref = useRef(null)
  const gradId = 'spark' + useId().replace(/[^a-zA-Z0-9]/g, '')
  useEffect(() => {
    if (!ref.current) return
    const ro = new ResizeObserver(() => setW(ref.current.clientWidth))
    ro.observe(ref.current)
    return () => ro.disconnect()
  }, [])
  if (!w || points.length < 2) return <div ref={ref} style={{ height }} className="spark-empty" />
  const min = Math.min(...points.map((p) => p[1]))
  const max = Math.max(...points.map((p) => p[1]))
  const span = max - min || 1
  const pad = 4
  const x = (i) => pad + (i / (points.length - 1)) * (w - pad * 2)
  const y = (v) => height - pad - ((v - min) / span) * (height - pad * 2)
  const d = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p[1]).toFixed(1)}`).join(' ')
  const up = points[points.length - 1][1] >= points[0][1]
  const color = up ? 'var(--up)' : 'var(--down)'
  return (
    <div ref={ref} className="spark">
      <svg viewBox={`0 0 ${w} ${height}`} preserveAspectRatio="none" width="100%" height={height}>
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity="0.3" />
            <stop offset="100%" stopColor={color} stopOpacity="0" />
          </linearGradient>
        </defs>
        <path d={`${d} L${x(points.length - 1)},${height} L${x(0)},${height} Z`} fill={`url(#${gradId})`} />
        <path d={d} fill="none" stroke={color} strokeWidth="1.8" strokeLinejoin="round" strokeLinecap="round" />
        <circle cx={x(points.length - 1)} cy={y(points[points.length - 1][1])} r="3" fill={color} />
      </svg>
    </div>
  )
}

function ChangeBadge({ from, to }) {
  if (!from || !to) return null
  const p = ((to - from) / from) * 100
  const up = p >= 0
  return <span className={`change ${up ? 'up' : 'down'}`}>{up ? '▲' : '▼'} {Math.abs(p).toFixed(1)}%</span>
}

function AlertList({ alerts, onDelete, onClearAll }) {
  if (alerts.length === 0) return <p className="empty">No alerts yet.</p>
  return (
    <>
      <div className="list-bar">
        <span className="muted-sm">{alerts.length} alert{alerts.length === 1 ? '' : 's'}</span>
        <button className="ghost" onClick={onClearAll}>Clear all</button>
      </div>
      <motion.ul className="alert-list" initial="hidden" animate="visible" variants={{ hidden: {}, visible: { transition: { staggerChildren: 0.06 } } }}>
        <AnimatePresence initial={false}>{alerts.map((a) => (
          <motion.li key={a.id} className="card alert-item" variants={{ hidden: { opacity: 0, x: -10 }, visible: { opacity: 1, x: 0 } }} exit={{ opacity: 0, x: 12 }} layout>
            <div className="alert-top">
              <AssetLogo market={a.market} symbol={a.symbol} className="alert-logo" />
              <span className="badge">{a.market}</span>
              <strong>{a.symbol}</strong>
              <button className="x" onClick={() => onDelete(a.id)} aria-label="Delete alert">×</button>
            </div>
            <p>{a.message}</p>
            <time>{fmtTime(a.createdAt)}</time>
          </motion.li>
        ))}</AnimatePresence>
      </motion.ul>
    </>
  )
}
