import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import './index.css'
import { visitorHeaders } from './visitor.js'

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
    await fetch('/api/push/unsubscribe', {
      method: 'DELETE',
      headers: visitorHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ endpoint: sub.endpoint }),
    })
    await sub.unsubscribe()
    window.dispatchEvent(new Event('pushstate'))
  }
}

async function subscribePush(reg) {
  let sub = await reg.pushManager.getSubscription()
  if (!sub) {
    const res = await fetch('/api/push/vapid-key')
    const { publicKey } = await res.json()
    sub = await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    })
  }
  await fetch('/api/push/subscribe', {
    method: 'POST',
    headers: visitorHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({
      endpoint: sub.endpoint,
      p256dh: base64(sub.getKey('p256dh')),
      auth: base64(sub.getKey('auth')),
    }),
  })
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
