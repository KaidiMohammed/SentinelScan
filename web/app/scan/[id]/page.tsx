'use client'

import { useEffect, useState } from 'react'
import { useParams } from 'next/navigation'

type CheckStatus = 'running' | 'finding' | 'clean' | 'not_checked'
type Check = { checkKey: string; status: CheckStatus; reason: string | null }
type Job = { status: 'queued' | 'running' | 'done' | 'failed'; targetUrl: string; error: string | null; note: string | null }
type Snapshot = { job: Job; checks: Check[] }

// Libellés fidèles aux statuts réels ; « not_checked » n'est jamais présenté comme un succès.
const CHECK_LABEL: Record<CheckStatus, string> = {
  running: 'En cours…',
  finding: 'Problème trouvé',
  clean: 'Aucun problème trouvé',
  not_checked: "n'a pas pu être vérifié",
}

const JOB_LABEL: Record<Job['status'], string> = {
  queued: "En attente d'un worker",
  running: 'Scan en cours',
  done: 'Scan terminé',
  failed: 'Le scan a échoué',
}

export default function ScanPage() {
  const { id } = useParams<{ id: string }>()
  const [snap, setSnap] = useState<Snapshot | null>(null)
  const [streamError, setStreamError] = useState<string | null>(null)

  useEffect(() => {
    const source = new EventSource(`/api/scans/${id}/events`)
    let finished = false
    source.addEventListener('progress', (e) => {
      const s: Snapshot = JSON.parse((e as MessageEvent).data)
      setSnap(s)
      if (s.job.status === 'done' || s.job.status === 'failed') {
        finished = true
        source.close()
      }
    })
    source.addEventListener('stream-error', () => {
      finished = true
      setStreamError('La progression n\'a pas pu être lue. Rechargez la page pour réessayer.')
      source.close()
    })
    source.onerror = () => {
      // Fermeture normale après la fin du scan : pas une erreur.
      if (!finished) setStreamError('Connexion au suivi en direct interrompue.')
    }
    return () => source.close()
  }, [id])

  return (
    <main style={{ maxWidth: 640, margin: '10vh auto', padding: '0 16px', fontFamily: 'system-ui' }}>
      <h1>Analyse en cours</h1>
      {snap && <p>{snap.job.targetUrl}</p>}
      <p role="status">{snap ? JOB_LABEL[snap.job.status] : 'Connexion au suivi en direct…'}</p>
      {streamError && <p role="alert">{streamError}</p>}
      {snap?.job.status === 'failed' && <p role="alert">{snap.job.error ?? "Raison non communiquée par le worker."}</p>}

      {snap && snap.checks.length > 0 && (
        <ul>
          {snap.checks.map((c) => (
            <li key={c.checkKey}>
              <strong>{c.checkKey}</strong> : {CHECK_LABEL[c.status]}
              {c.status === 'not_checked' && <> — raison : {c.reason ?? 'non précisée'}</>}
            </li>
          ))}
        </ul>
      )}

      {snap?.job.status === 'done' && snap.checks.length === 0 && (
        <p>Aucune vérification n'a été exécutée{snap.job.note ? ` (${snap.job.note})` : ''}. Ce n'est pas un résultat « tout est bon ».</p>
      )}
    </main>
  )
}
