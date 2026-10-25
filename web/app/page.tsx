'use client'

import { useState, type FormEvent } from 'react'
import { useRouter } from 'next/navigation'

export default function Home() {
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const url = String(new FormData(e.currentTarget).get('url') ?? '').trim()
    setBusy(true)
    setError(null)
    try {
      const res = await fetch('/api/scans', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url }),
      })
      if (!res.ok) {
        setError(res.status === 400 ? "Adresse invalide : saisissez une URL http(s) complète." : 'Le scan n\'a pas pu être lancé.')
        return
      }
      const { id } = await res.json()
      router.push(`/scan/${id}`)
    } catch {
      setError('Le serveur est injoignable.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main style={{ maxWidth: 640, margin: '15vh auto', padding: '0 16px', fontFamily: 'system-ui' }}>
      <h1>Votre app est-elle exposée ?</h1>
      <p>Collez l'adresse de votre app pour savoir, en quelques minutes, si elle a des problèmes de sécurité sérieux.</p>
      <form onSubmit={onSubmit}>
        <label htmlFor="url">Adresse de l'app</label>
        <input id="url" name="url" type="url" required placeholder="https://mon-app.com" style={{ display: 'block', width: '100%' }} />
        <button type="submit" disabled={busy}>Scanner mon app</button>
      </form>
      {error && <p role="alert">{error}</p>}
      <p role="status">Pour l'instant, aucune vérification de sécurité n'est encore implémentée : un scan le dira explicitement.</p>
    </main>
  )
}
