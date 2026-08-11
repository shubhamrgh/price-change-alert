import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)

// ---- PWA: service worker + web push ----
export const pushReady = () => window.__pushEnabled === true

export async function enablePushNotifications() {
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
    throw new Error('Notifications not supported on this browser')
  }
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') throw new Error('Permission denied')
  const reg = await navigator.serviceWorker.ready
  window.__swReg = reg
  await subscribePush(reg)
}

export async function disablePushNotifications() {
  const reg = window.__swReg || (await navigator.serviceWorker.ready)
  const sub = await reg.pushManager.getSubscription()
  if (sub) {
    let serverError
    try {
      const response = await fetch('/api/push/unsubscribe', {
        method: 'DELETE',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ endpoint: sub.endpoint }),
      })
      await ensureOk(response)
    } catch (error) {
      serverError = error
    } finally {
      await sub.unsubscribe()
      window.__pushEnabled = false
      window.dispatchEvent(new Event('pushstate'))
    }
    if (serverError) throw serverError
  }
}

async function subscribePush(reg) {
  let sub = await reg.pushManager.getSubscription()
  if (!sub) {
    const res = await fetch('/api/push/vapid-key', { credentials: 'same-origin' })
    await ensureOk(res)
    const { publicKey } = await res.json()
    sub = await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    })
  }
  const response = await fetch('/api/push/subscribe', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      endpoint: sub.endpoint,
      p256dh: base64(sub.getKey('p256dh')),
      auth: base64(sub.getKey('auth')),
    }),
  })
  await ensureOk(response)
  window.__pushEnabled = true
  window.dispatchEvent(new Event('pushstate'))
}

function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const raw = atob(base64)
  return Uint8Array.from([...raw].map((c) => c.charCodeAt(0)))
}

function base64(buf) {
  return btoa(String.fromCharCode(...new Uint8Array(buf)))
}

async function ensureOk(response) {
  if (response.ok) return
  const body = await response.json().catch(() => ({}))
  throw new Error(body.error || `Notification request failed (${response.status})`)
}

// Register SW on load; auto-resubscribe if permission already granted.
if ('serviceWorker' in navigator) {
  window.addEventListener('load', async () => {
    try {
      const reg = await navigator.serviceWorker.register('/sw.js')
      window.__swReg = reg
      if (Notification.permission === 'granted') subscribePush(reg)
    } catch (err) {
      console.warn('SW registration failed:', err)
    }
  })
}
