// ── Auth helpers ──────────────────────────────────────────────────────────────

export function getToken() {
  return localStorage.getItem('detox_token')
}

export function getUsername() {
  return localStorage.getItem('detox_username')
}

export function getDohUrl() {
  return localStorage.getItem('detox_doh_url')
}

export function saveAuth(token, username, dohUrl) {
  localStorage.setItem('detox_token', token)
  localStorage.setItem('detox_username', username)
  if (dohUrl) localStorage.setItem('detox_doh_url', dohUrl)
}

export function clearAuth() {
  localStorage.removeItem('detox_token')
  localStorage.removeItem('detox_username')
  localStorage.removeItem('detox_doh_url')
}

// ── Base request ──────────────────────────────────────────────────────────────

async function request(path, options = {}) {
  const token = getToken()
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  }

  const res = await fetch(path, { ...options, headers })
  if (!res.ok) {
    let msg = `HTTP ${res.status}`
    try {
      const body = await res.text()
      if (body) msg = body
    } catch {
      /* ignore */
    }
    throw new Error(msg)
  }
  return res
}

// ── Auth API ──────────────────────────────────────────────────────────────────

export async function login(username, password) {
  const res = await request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  return res.json()
}

export async function register(username, email, password) {
  const res = await request('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, email, password }),
  })
  return res.json()
}

// ── Dashboard API ─────────────────────────────────────────────────────────────

export async function getUsageStats(userId, period) {
  const res = await request(
    `/api/dashboard/users/${encodeURIComponent(userId)}/usage?period=${period}`,
  )
  return res.json()
}

export async function getDomainUsage(userId, startDate, endDate) {
  const params = new URLSearchParams()
  if (startDate) params.set('startDate', startDate)
  if (endDate) params.set('endDate', endDate)
  const qs = params.toString()
  const url = `/api/dashboard/users/${encodeURIComponent(userId)}/domains${qs ? '?' + qs : ''}`
  const res = await request(url)
  return res.json()
}

export async function getTimeline(userId, period) {
  const res = await request(
    `/api/dashboard/users/${encodeURIComponent(userId)}/timeline?period=${period}`,
  )
  return res.json()
}

// ── AI Review SSE ─────────────────────────────────────────────────────────────

/**
 * Streams AI review tokens via SSE.
 * Yields parsed event objects: { type, token, done, model, messageId, error }
 */
export async function* streamAiReview(usage, prompt, sessionId) {
  const token = getToken()
  const res = await fetch('/api/ai/review/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({
      sessionId: sessionId ?? String(Date.now()),
      prompt,
      usage,
    }),
  })

  if (!res.ok) throw new Error(`HTTP ${res.status}`)

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const line of lines) {
      if (line.startsWith('data:')) {
        const json = line.slice(5).trim()
        if (!json) continue
        try {
          yield JSON.parse(json)
        } catch {
          /* skip malformed lines */
        }
      }
    }
  }
}
