# SentinelScan (nom de travail)

Scanner de sécurité pour les apps construites avec l'IA et le no-code (Lovable, Bolt, Cursor, v0)
sur Supabase / Firebase : il dit en langage clair si l'app est exposée, à quel point, et quoi corriger.

**État : première version légère.** Seuls le planning GSD (`.planning/`) et un squelette Next.js existent.
Aucune vérification de sécurité n'est encore implémentée ; l'interface l'indique explicitement.
Règle du projet : aucun constat simulé, jamais (voir `.planning/BRIEF.md`, section 6).

## Structure prévue

- `web/` : Next.js (landing, app, rapports)
- `api/` : Spring Boot (comptes, domaines, scans, rapports)
- `worker/` : Spring Boot + moteurs open source (Nuclei, Retire.js, sslyze, OSV.dev), file de jobs Postgres
- `test-targets/` : apps de test volontairement vulnérables puis corrigées (Supabase local en Docker)

## Développement

```bash
cd web && npm install && npm run dev
```
