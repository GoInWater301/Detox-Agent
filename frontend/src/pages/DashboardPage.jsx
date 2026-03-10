import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  clearAuth,
  getDohUrl,
  getDomainUsage,
  getTimeline,
  getUsageStats,
  getUsername,
  streamAiReview,
} from '../api/client'

// ── helpers ───────────────────────────────────────────────────────────────────

function fmtTime(us) {
  if (!us) return '—'
  const ms = us / 1000
  if (ms < 1000) return `${Math.round(ms)} ms`
  return `${(ms / 1000).toFixed(1)} s`
}

function fmtDuration(minutes) {
  if (!minutes) return '—'
  if (minutes < 60) return `${Math.round(minutes)}분`
  return `${Math.floor(minutes / 60)}시간 ${Math.round(minutes % 60)}분`
}

function fmtTs(us) {
  if (!us) return '—'
  return new Date(us / 1000).toLocaleString()
}

function calcRisk(topDomains) {
  const totalMin = (topDomains ?? []).reduce((s, d) => s + (d.totalDuration || 0), 0)
  if (totalMin >= 120) return { label: '위험', color: '#ef4444', desc: `${Math.round(totalMin)}분 — 일 2시간 초과` }
  if (totalMin >= 60)  return { label: '주의', color: '#f59e0b', desc: `${Math.round(totalMin)}분 — 모니터링 필요` }
  if (totalMin >= 20)  return { label: '관찰', color: '#eab308', desc: `${Math.round(totalMin)}분 — 양호` }
  if (totalMin > 0)    return { label: '정상', color: '#6b7280', desc: `${Math.round(totalMin)}분` }
  return { label: '—', color: '#6b7280', desc: '데이터 없음' }
}

const PERIOD_LABELS = { daily: '오늘', weekly: '이번 주', monthly: '이번 달' }

const DOMAIN_COLORS = [
  '#6366f1', '#8b5cf6', '#64748b', '#475569', '#94a3b8',
  '#7c3aed', '#6b7280', '#4b5563', '#9333ea', '#334155',
]

function pad(v) {
  return String(v).padStart(2, '0')
}

function toDateString(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function getDateRange(period) {
  const now = new Date()
  const end = new Date(now)
  const start = new Date(now)

  if (period === 'weekly') {
    const day = start.getDay()
    const diff = day === 0 ? 6 : day - 1
    start.setDate(start.getDate() - diff)
  } else if (period === 'monthly') {
    start.setDate(1)
  }

  return {
    startDate: toDateString(start),
    endDate: toDateString(end),
  }
}

// ── stat card ─────────────────────────────────────────────────────────────────

function StatCard({ label, value, sub, icon }) {
  return (
    <div className="stat-card">
      <div className="stat-icon">{icon}</div>
      <div className="stat-body">
        <span className="stat-label">{label}</span>
        <span className="stat-value">{value ?? '—'}</span>
        {sub && <span className="stat-sub">{sub}</span>}
      </div>
    </div>
  )
}

// ── empty state ───────────────────────────────────────────────────────────────

function Empty({ message }) {
  return (
    <div className="empty-state">
      <span>📭</span>
      <p>{message}</p>
    </div>
  )
}

// ── main component ────────────────────────────────────────────────────────────

export default function DashboardPage() {
  const navigate = useNavigate()
  const username = getUsername() ?? 'user'
  const dohUrl = getDohUrl()

  const [period, setPeriod] = useState('daily')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const [usageStats, setUsageStats] = useState(null)
  const [domainUsage, setDomainUsage] = useState([])
  const [timeline, setTimeline] = useState([])
  const [lastUpdated, setLastUpdated] = useState(null)

  // AI review state
  const [aiPrompt, setAiPrompt] = useState('')
  const [aiText, setAiText] = useState('')
  const [aiStreaming, setAiStreaming] = useState(false)
  const [aiError, setAiError] = useState('')
  const aiBoxRef = useRef(null)

  // ── data fetching ────────────────────────────────────────────────────────────

  const fetchAll = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const { startDate, endDate } = getDateRange(period)
      const [stats, domains, tl] = await Promise.all([
        getUsageStats(username, period),
        getDomainUsage(username, startDate, endDate),
        getTimeline(username, period),
      ])
      setUsageStats(stats)
      setDomainUsage(Array.isArray(domains) ? domains : [])
      setTimeline(Array.isArray(tl) ? tl : [])
      setLastUpdated(new Date())
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [username, period])

  useEffect(() => {
    fetchAll()
  }, [fetchAll])

  useEffect(() => {
    const timer = window.setInterval(() => {
      fetchAll()
    }, 5000)
    return () => window.clearInterval(timer)
  }, [fetchAll])

  // auto-scroll AI box
  useEffect(() => {
    if (aiBoxRef.current) {
      aiBoxRef.current.scrollTop = aiBoxRef.current.scrollHeight
    }
  }, [aiText])

  // ── AI review ────────────────────────────────────────────────────────────────

  async function handleAiReview() {
    if (!usageStats) return
    setAiStreaming(true)
    setAiText('')
    setAiError('')
    try {
      for await (const event of streamAiReview(
        usageStats,
        aiPrompt || '오늘 DNS 사용 패턴을 분석하고 중독 위험이 있는 도메인과 개선 방법을 알려줘.',
        String(Date.now()),
      )) {
        if (event.type === 'error') {
          setAiError(event.error || 'AI review failed')
          break
        }
        if (event.token) {
          setAiText((prev) => prev + event.token)
        }
        if (event.done) break
      }
    } catch (err) {
      setAiError(err.message)
    } finally {
      setAiStreaming(false)
    }
  }

  // ── chart data ────────────────────────────────────────────────────────────────

  const risk = calcRisk(usageStats?.topDomains)

  const topDomainChartData = (usageStats?.topDomains ?? []).map((d) => ({
    name: d.domain.replace(/^www\./, ''),
    requests: d.requestCount,
    duration: d.totalDuration,
  }))

  const timelineChartData = timeline.map((t) => ({
    bucket: t.bucket,
    queries: t.totalQueries,
    domains: t.uniqueDomains,
  }))

  // ── render ───────────────────────────────────────────────────────────────────

  return (
    <div className="dashboard">
      {/* ── header ── */}
      <header className="dash-header">
        <div className="dash-header-left">
          <span className="dash-logo">🛡️</span>
          <span className="dash-title">Detox Agent</span>
          {dohUrl && (
            <code
              className="doh-url-badge"
              title="클릭하여 복사"
              onClick={() => navigator.clipboard.writeText(dohUrl)}
            >
              {dohUrl}
            </code>
          )}
        </div>

        <div className="period-tabs">
          {Object.entries(PERIOD_LABELS).map(([key, label]) => (
            <button
              key={key}
              className={`period-tab${period === key ? ' active' : ''}`}
              onClick={() => setPeriod(key)}
            >
              {label}
            </button>
          ))}
        </div>

        <div className="dash-header-right">
          <span className="dash-user">@{username}</span>
          <button
            className="btn-logout"
            onClick={() => {
              clearAuth()
              navigate('/login')
            }}
          >
            Sign Out
          </button>
        </div>
      </header>

      {lastUpdated && (
        <div className="dash-main" style={{ paddingTop: 0, paddingBottom: 0 }}>
          <p style={{ margin: 0, color: '#64748b', fontSize: 13 }}>
            마지막 갱신: {lastUpdated.toLocaleTimeString()}
          </p>
        </div>
      )}

      {/* ── main ── */}
      <main className="dash-main">
        {error && (
          <div className="dash-error">
            ⚠️ {error}
            <button onClick={fetchAll}>Retry</button>
          </div>
        )}

        {loading && !usageStats && (
          <div className="loading-overlay">
            <div className="spinner" />
            <p>Loading analytics…</p>
          </div>
        )}

        {/* ── stat cards ── */}
        <section className="stats-row">
          <StatCard
            icon="📡"
            label="총 DNS 질의"
            value={usageStats?.totalQueries?.toLocaleString() ?? '0'}
            sub={PERIOD_LABELS[period]}
          />
          <StatCard
            icon="🌐"
            label="접속 도메인 수"
            value={usageStats?.uniqueDomains?.toLocaleString() ?? '0'}
            sub="개 도메인"
          />
          <StatCard
            icon="⏱️"
            label="추정 총 사용 시간"
            value={fmtDuration(
              (usageStats?.topDomains ?? []).reduce((s, d) => s + (d.totalDuration || 0), 0)
            )}
            sub="오늘 기준 (DNS 추정)"
          />
          <StatCard
            icon={risk.label === '위험' ? '🔴' : risk.label === '주의' ? '🟡' : '🟢'}
            label="사용 위험 수준"
            value={risk.label}
            sub={risk.desc}
          />
        </section>

        {/* ── charts row ── */}
        <section className="charts-row">
          {/* Domain bar chart */}
          <div className="chart-card">
            <h3 className="chart-title">도메인별 질의 횟수</h3>
            {topDomainChartData.length === 0 ? (
              <Empty message="No domain data for this period" />
            ) : (
              <ResponsiveContainer width="100%" height={260}>
                <BarChart
                  data={topDomainChartData}
                  layout="vertical"
                  margin={{ top: 4, right: 24, left: 0, bottom: 4 }}
                >
                  <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#e5e7eb" />
                  <XAxis type="number" tick={{ fontSize: 11 }} />
                  <YAxis
                    type="category"
                    dataKey="name"
                    width={110}
                    tick={{ fontSize: 11 }}
                  />
                  <Tooltip
                    formatter={(v) => [v.toLocaleString(), 'Requests']}
                    contentStyle={{ borderRadius: 8, fontSize: 12 }}
                  />
                  <Bar dataKey="requests" radius={[0, 4, 4, 0]}>
                    {topDomainChartData.map((_, i) => (
                      <Cell key={i} fill={DOMAIN_COLORS[i % DOMAIN_COLORS.length]} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>

          {/* Timeline line chart */}
          <div className="chart-card">
            <h3 className="chart-title">시간대별 질의 추이</h3>
            {timelineChartData.length === 0 ? (
              <Empty message="No timeline data for this period" />
            ) : (
              <ResponsiveContainer width="100%" height={260}>
                <LineChart
                  data={timelineChartData}
                  margin={{ top: 4, right: 16, left: 0, bottom: 4 }}
                >
                  <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                  <XAxis dataKey="bucket" tick={{ fontSize: 10 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip
                    formatter={(v, name) => [
                      v.toLocaleString(),
                      name === 'queries' ? 'Total Queries' : 'Unique Domains',
                    ]}
                    contentStyle={{ borderRadius: 8, fontSize: 12 }}
                  />
                  <Line
                    type="monotone"
                    dataKey="queries"
                    stroke="#1f63e9"
                    strokeWidth={2}
                    dot={{ r: 3 }}
                    activeDot={{ r: 5 }}
                  />
                  <Line
                    type="monotone"
                    dataKey="domains"
                    stroke="#7c3aed"
                    strokeWidth={2}
                    dot={{ r: 3 }}
                    strokeDasharray="4 2"
                  />
                </LineChart>
              </ResponsiveContainer>
            )}
          </div>
        </section>

        {/* ── domain table ── */}
        <section className="table-card">
          <h3 className="chart-title">도메인별 사용 상세 — 높을수록 주의 필요</h3>
          {domainUsage.length === 0 ? (
            <Empty message="No domain records found" />
          ) : (
            <div className="table-wrap">
              <table className="domain-table">
                <thead>
                  <tr>
                    <th>도메인</th>
                    <th>질의 수</th>
                    <th>추정 사용 시간</th>
                    <th>평균 응답</th>
                    <th>첫 접속</th>
                    <th>마지막 접속</th>
                  </tr>
                </thead>
                <tbody>
                  {domainUsage.map((d, i) => (
                    <tr key={i}>
                      <td>
                        <span
                          className="domain-dot"
                          style={{ background: DOMAIN_COLORS[i % DOMAIN_COLORS.length] }}
                        />
                        {d.domain}
                      </td>
                      <td>{d.requestCount?.toLocaleString()}</td>
                      <td>{fmtDuration(d.totalDuration)}</td>
                      <td>{d.averageResponseTimeMs} ms</td>
                      <td>{fmtTs(d.firstAccess)}</td>
                      <td>{fmtTs(d.lastAccess)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        {/* ── AI review ── */}
        <section className="ai-card">
          <div className="ai-header">
            <div>
              <h3 className="chart-title" style={{ marginBottom: 2 }}>
                AI 사용 패턴 분석
              </h3>
              <p className="ai-subtitle">
                현재 DNS 데이터를 기반으로 중독 위험 요인을 분석합니다
              </p>
            </div>
            <button
              className="btn-primary"
              onClick={handleAiReview}
              disabled={aiStreaming || !usageStats}
            >
              {aiStreaming ? (
                <>
                  <span className="btn-spinner" /> 분석 중…
                </>
              ) : (
                '분석 시작'
              )}
            </button>
          </div>

          <div className="ai-prompt-row">
            <input
              className="ai-prompt-input"
              placeholder="예: 가장 오래 접속한 도메인과 줄이는 방법을 알려줘"
              value={aiPrompt}
              onChange={(e) => setAiPrompt(e.target.value)}
              disabled={aiStreaming}
            />
          </div>

          {aiError && <p className="ai-error">⚠️ {aiError}</p>}

          {(aiText || aiStreaming) && (
            <div className="ai-output" ref={aiBoxRef}>
              <MarkdownText text={aiText} />
              {aiStreaming && <span className="ai-cursor" />}
            </div>
          )}

          {!aiText && !aiStreaming && (
            <div className="ai-placeholder">
              <p>
                <strong>분석 시작</strong> 버튼을 눌러 AI 진단을 시작하세요.
              </p>
            </div>
          )}
        </section>
      </main>
    </div>
  )
}

// ── simple markdown renderer ───────────────────────────────────────────────────

function MarkdownText({ text }) {
  // Very lightweight markdown: bold, code, headers, line breaks
  const lines = text.split('\n')
  return (
    <div className="markdown">
      {lines.map((line, i) => {
        if (line.startsWith('## '))
          return <h4 key={i}>{line.slice(3)}</h4>
        if (line.startsWith('# '))
          return <h3 key={i}>{line.slice(2)}</h3>
        if (line.startsWith('---'))
          return <hr key={i} />
        if (line.startsWith('- ') || line.startsWith('* '))
          return <li key={i}><InlineText text={line.slice(2)} /></li>
        if (line.match(/^\d+\. /))
          return <li key={i}><InlineText text={line.replace(/^\d+\. /, '')} /></li>
        if (line.trim() === '')
          return <br key={i} />
        return <p key={i}><InlineText text={line} /></p>
      })}
    </div>
  )
}

function InlineText({ text }) {
  // Bold (**text**), inline code (`code`)
  const parts = text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g)
  return (
    <>
      {parts.map((part, i) => {
        if (part.startsWith('**') && part.endsWith('**'))
          return <strong key={i}>{part.slice(2, -2)}</strong>
        if (part.startsWith('`') && part.endsWith('`'))
          return <code key={i}>{part.slice(1, -1)}</code>
        return part
      })}
    </>
  )
}
