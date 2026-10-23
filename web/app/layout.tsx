import type { ReactNode } from 'react'

export const metadata = { title: 'SentinelScan', description: 'Votre app est-elle exposée ?' }

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="fr">
      <body>{children}</body>
    </html>
  )
}
