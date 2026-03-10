import { useMemo, useState } from 'react'
import './App.css'

function App() {
  const [userId, setUserId] = useState('testuser')
  const [period, setPeriod] = useState('daily')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)

  const endpoint = useMemo(
    () => `/api/dashboard/users/${encodeURIComponent(userId)}/usage?period=${period}`,
    [userId, period],
  )

  const fetchUsage = async () => {
    setLoading(true)
    setError('')
    try {
      const response = await fetch(endpoint)
      if (!response.ok) {
        throw new Error(`Request failed: ${response.status}`)
      }
      const data = await response.json()
      setResult(data)
    } catch (e) {
      setResult(null)
      setError(e instanceof Error ? e.message : 'Unknown error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="page">
      <section className="panel">
        <h1>Detox-Agent Frontend</h1>
        <p>React + Vite 개발 서버가 백엔드 WebServer API로 프록시 연결됩니다.</p>

        <div className="controls">
          <label>
            User ID
            <input value={userId} onChange={(e) => setUserId(e.target.value)} />
          </label>
          <label>
            Period
            <select value={period} onChange={(e) => setPeriod(e.target.value)}>
              <option value="daily">daily</option>
              <option value="weekly">weekly</option>
              <option value="monthly">monthly</option>
            </select>
          </label>
          <button type="button" onClick={fetchUsage} disabled={loading || !userId.trim()}>
            {loading ? 'Loading...' : 'Fetch Usage'}
          </button>
        </div>

        <p className="endpoint">
          API: <code>{endpoint}</code>
        </p>

        {error && <p className="error">{error}</p>}
        {result && (
          <pre className="result">{JSON.stringify(result, null, 2)}</pre>
        )}
      </section>
    </main>
  )
}

export default App
