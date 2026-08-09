import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { Component } from 'react'
import { enablePushNotifications, disablePushNotifications, pushReady } from './main.jsx'
import { visitorHeaders } from './visitor.js'

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

async function api(path, options = {}) {
  const res = await fetch(path, {
    ...options,
    headers: visitorHeaders({ 'Content-Type': 'application/json', ...options.headers }),
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.error || `Request failed (${res.status})`)
  }
  return res.status === 204 ? null : res.json()
}

const THEME_KEY = 'price-change-alert-theme'
function initialTheme() {
  const saved = localStorage.getItem(THEME_KEY)
  if (saved === 'light' || saved === 'dark') return saved
  return 'light'
}

export default function App() {
  const [theme, setTheme] = useState(initialTheme)
  const [items, setItems] = useState([])
  const [alerts, setAlerts] = useState([])
  const [tab, setTab] = useState('watch')
  const [notificationOn, setNotificationOn] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem(THEME_KEY, theme)
  }, [theme])

  useEffect(() => {
    const onPush = () => setNotificationOn(pushReady())
    window.addEventListener('pushstate', onPush)
    setNotificationOn(pushReady())
    return () => window.removeEventListener('pushstate', onPush)
  }, [])

  const refresh = useCallback(async () => {
    setError('')
    try {
      const [w, a] = await Promise.all([api('/api/watchlist'), api('/api/alerts')])
      setItems(w)
      setAlerts(a)
    } catch (e) {
      setError(e.message)
    }
  }, [])

  useEffect(() => {
    refresh()
    const t = setInterval(refresh, 30000)
    return () => clearInterval(t)
  }, [refresh])

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

  return (
    <div className="app">
      <ErrorBoundary>
        <header className="header">
        <div className="brand">
          <span className="logo">PC</span>
          <div>
            <h1>Price Change Alert</h1>
            <p>NSE · BSE · Crypto</p>
          </div>
        </div>
        <div className="header-actions">
          <label className="theme-switch" title="Toggle theme">
            <input type="checkbox" checked={theme === 'dark'}
                   onChange={() => setTheme(theme === 'dark' ? 'light' : 'dark')} />
            <span className="tslider"><span className="knob">{theme === 'dark' ? '☾' : '☀'}</span></span>
          </label>
          <button className={`bell ${notificationOn ? 'on' : ''}`} onClick={togglePush} aria-label="Notifications">
            {notificationOn ? 'ON' : 'OFF'}
          </button>
        </div>
      </header>

      {error && <div className="banner error" onClick={() => setError('')}>{error}</div>}

      <nav className="tabs">
        <button className={tab === 'watch' ? 'active' : ''} onClick={() => setTab('watch')}>Watchlist</button>
        <button className={tab === 'alerts' ? 'active' : ''} onClick={() => setTab('alerts')}>Alerts {alerts.length > 0 && <span className="count">{alerts.length}</span>}</button>
      </nav>

      {tab === 'watch' ? (
        <>
          <AddForm onAdded={refresh} />
          <WatchList items={items} onDelete={removeItem} onToggle={setItemActive} />
        </>
      ) : (
        <AlertList alerts={alerts} onDelete={deleteAlert} onClearAll={clearAlerts} />
        )}
      </ErrorBoundary>
    </div>
  )
}

function AddForm({ onAdded }) {
  const [market, setMarket] = useState('NSE')
  const [query, setQuery] = useState('')
  const [triggerMode, setTriggerMode] = useState('BELOW')
  const [threshold, setThreshold] = useState('')
  const currency = market === 'CRYPTO' ? 'USD' : 'INR'
  const [suggestions, setSuggestions] = useState([])
  const [open, setOpen] = useState(false)
  const [picked, setPicked] = useState(null)
  const [preview, setPreview] = useState(null)
  const focused = useRef(false)
  const blurTimer = useRef(null)
  const [err, setErr] = useState('')

  useEffect(() => {
    const q = query.trim()
    if (!q) { setSuggestions([]); setOpen(false); return }
    const pickedSym = picked && picked.symbol
    if (pickedSym && q.toUpperCase() === pickedSym) { setSuggestions([]); setOpen(false); return }
    let alive = true
    const t = setTimeout(() => {
      api(`/api/search?q=${encodeURIComponent(q)}&market=${market}`)
        .then((s) => {
          if (!alive) return
          setSuggestions(s)
          if (focused.current) setOpen(true)
        })
        .catch(() => alive && setOpen(false))
    }, 250)
    return () => { alive = false; clearTimeout(t) }
  }, [query, market])

  const closeSoon = () => {
    clearTimeout(blurTimer.current)
    blurTimer.current = setTimeout(() => setOpen(false), 150)
  }

  const pick = async (s) => {
    setPicked(s)
    setQuery(s.symbol)
    setSuggestions([])
    setOpen(false)
    setPreview(null)
    setErr('')
    try {
      const q = await api(`/api/quote?market=${market}&symbol=${encodeURIComponent(s.symbol)}&currency=${currency.toLowerCase()}`)
      setPreview(q)
    } catch (e) {
      setErr(e.message)
    }
  }

  const submit = async (e) => {
    e.preventDefault()
    setErr('')
    const val = parseFloat(threshold)
    const sym = (query || '').trim()
    if (!sym || !Number.isFinite(val) || val <= 0) {
      setErr('Pick a symbol and enter a threshold')
      return
    }
    try {
      await api('/api/watchlist', {
        method: 'POST',
        body: JSON.stringify({
          symbol: sym, name: picked ? picked.name : null, market,
          triggerType: triggerMode === 'PERCENT' || triggerMode === 'PERCENT_UP' ? 'PERCENT' : 'PRICE',
          direction: triggerMode === 'ABOVE' || triggerMode === 'PERCENT_UP' ? 'ABOVE' : 'BELOW',
          thresholdValue: val, currency,
        }),
      })
      setQuery('')
      setThreshold('')
      setPicked(null)
      setPreview(null)
      setSuggestions([])
      setOpen(false)
      onAdded()
    } catch (e2) {
      setErr(e2.message)
    }
  }

  return (
    <form className="card add-form" onSubmit={submit}>
      <h2>Add watch item</h2>
      <div className="searchbox">
        <select className="market-select" value={market} onChange={(e) => { setMarket(e.target.value); setPicked(null); setPreview(null); setErr('') }}>
          <option value="NSE">Stock · NSE (₹)</option>
          <option value="BSE">Stock · BSE (₹)</option>
          <option value="CRYPTO">Crypto ($)</option>
        </select>
        <input placeholder={market === 'CRYPTO' ? 'Search coins... (BTC, ETH, DOGE)' : 'Search stocks... (RELIANCE, TCS)'} value={query}
               onChange={(e) => { setQuery(e.target.value); setPicked(null); setPreview(null) }}
               onFocus={() => { focused.current = true; if (suggestions.length) setOpen(true) }}
               onBlur={() => { focused.current = false; closeSoon() }}
               onKeyDown={(e) => { if (e.key === 'Escape') setOpen(false) }}
               autoComplete="off" />
        {open && suggestions.length > 0 && (
          <ul className="suggest" onMouseDown={(e) => e.preventDefault()}>
            {suggestions.map((s, i) => (
              <li key={`${s.symbol}-${i}`} onMouseDown={() => pick(s)}>
                <span className="s-sym">{s.symbol}</span>
                <span className="s-name">{s.name}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
      {picked && !preview && <div className="hint">Fetching live price...</div>}
      {preview && (
        <div className="preview">
          <span className="dot" />
          <span className="pv-name">{preview.displayName || preview.symbol}</span>
          <strong>{fmtPrice(preview.price, preview.currency)}</strong>
          <span className="src-badge">{preview.source}</span>
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
    <ul className="watch-list">
      {items.map((item) => (
        <WatchCard key={item.id} item={item} onDelete={onDelete} onToggle={onToggle} />
      ))}
    </ul>
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
    <li className={`card watch-card ${!item.active ? 'paused' : ''}`}>
      <button className="card-main" onClick={() => setExpanded((v) => !v)}>
        <div className="item-top">
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
        </div>
      </button>
      {expanded && <ChartPanel item={item} />}
      <div className="item-actions">
        <label className="switch">
          <input type="checkbox" checked={item.active} onChange={(e) => onToggle(item, e.target.checked)} />
          <span className="slider" />
          <span className="switch-label">{item.active ? 'On' : 'Paused'}</span>
        </label>
        <button className="ghost danger" onClick={() => onDelete(item.id)}>Remove</button>
      </div>
    </li>
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
      <ul className="alert-list">
        {alerts.map((a) => (
          <li key={a.id} className="card alert-item">
            <div className="alert-top">
              <span className="badge">{a.market}</span>
              <strong>{a.symbol}</strong>
              <button className="x" onClick={() => onDelete(a.id)} aria-label="Delete alert">×</button>
            </div>
            <p>{a.message}</p>
            <time>{fmtTime(a.createdAt)}</time>
          </li>
        ))}
      </ul>
    </>
  )
}
