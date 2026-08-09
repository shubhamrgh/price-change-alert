const VISITOR_KEY = 'pca-visitor-id'

export function visitorId() {
  if (['localhost', '127.0.0.1'].includes(window.location.hostname)) return 'legacy'

  let id = localStorage.getItem(VISITOR_KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(VISITOR_KEY, id)
  }
  return id
}

export function visitorHeaders(headers = {}) {
  return { ...headers, 'X-Visitor-Id': visitorId() }
}
