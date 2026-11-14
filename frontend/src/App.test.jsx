import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App.jsx'

afterEach(() => vi.unstubAllGlobals())

describe('App placeholder', () => {
  it('shows backend status UP when health check succeeds', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ status: 'UP' }),
    }))
    render(<App />)
    expect(screen.getByRole('heading', { name: 'BiteAndByte Restaurant' })).toBeInTheDocument()
    await waitFor(() => expect(screen.getByTestId('backend-status')).toHaveTextContent('UP'))
  })

  it('shows DOWN when backend is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    render(<App />)
    await waitFor(() => expect(screen.getByTestId('backend-status')).toHaveTextContent('DOWN'))
  })
})
