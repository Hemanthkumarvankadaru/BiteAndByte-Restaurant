import { useEffect, useState } from 'react'

// Phase 0 placeholder: proves the frontend runs and can reach the backend.
// Replaced by the real router and pages in Phase 5.
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

function App() {
  const [backend, setBackend] = useState('checking')

  useEffect(() => {
    let cancelled = false
    fetch(`${API_BASE}/actuator/health`)
      .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
      .then((body) => !cancelled && setBackend(body.status ?? 'UNKNOWN'))
      .catch(() => !cancelled && setBackend('DOWN'))
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <main className="placeholder">
      <h1>BiteAndByte Restaurant</h1>
      <p>Phase 0 scaffold. Menu, ordering and admin arrive in later phases.</p>
      <p>
        Backend status: <strong data-testid="backend-status">{backend}</strong>
      </p>
    </main>
  )
}

export default App
