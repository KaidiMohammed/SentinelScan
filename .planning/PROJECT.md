# SentinelScan

> Brief complet et faisant foi : `.planning/BRIEF.md`.

## What This Is

Scanner de sécurité en ligne pour les fondateurs qui construisent leur app avec l'IA et le no-code
(Lovable, Bolt, Cursor, v0) sur Supabase ou Firebase. On colle une URL ; il dit en langage clair si l'app
est exposée, à quel point, quoi corriger d'abord, avec un correctif à copier adapté à la stack.
Cible : France et UE d'abord, rapports en français et en anglais.

## Core Value

Des résultats vrais : chaque constat vient d'une vérification réellement exécutée sur la cible, avec sa
preuve brute consultable ; jamais de constat simulé, inventé ou embelli.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Scan rapide passif sur n'importe quelle URL, progression en direct reflétant les vraies vérifications
- [ ] Secrets exposés dans les bundles JS (clés Supabase service_role, Stripe live, OpenAI…), toujours masqués
- [ ] Supabase : tables lisibles sans authentification (RLS absente), domaine vérifié uniquement
- [ ] Carte de constat : titre, gravité, explication simple, impact réel, preuve masquée, correctif copier-coller
- [ ] Note A–F calculée par une formule documentée ; « corrigez ces 3 points d'abord »
- [ ] Vérification de propriété du domaine (TXT DNS ou fichier), revérifiée avant chaque scan actif
- [ ] Re-scan avec comparaison avant/après

### Out of Scope

- Scan authentifié, logique métier, IDOR, pentest IA autonome — hors v1 (brief §9)
- Pentest réseau/infrastructure, apps mobiles — hors v1
- Scan actif de domaines non vérifiés — interdit (brief §7)
- Garanties de conformité ou certifications — hors v1

## Constraints

- **Vérité** : aucun constat simulé ; preuve brute stockée ; « n'a pas pu être vérifié » plutôt qu'un faux résultat ; l'IA n'explique que des constats réels et ne fixe jamais la gravité
- **Sécurité et légal** : vérifications non destructives, en lecture seule ; actif seulement sur domaine vérifié ; secrets jamais stockés en clair ni envoyés à l'IA ; journal de qui scanne quoi ; hébergement UE
- **Stack** : front Next.js (complet) ; API et worker Spring Boot ; Postgres ; worker Docker avec moteurs open source (Nuclei, Retire.js, sslyze, OSV.dev) et file de jobs Postgres ; IA Claude via une région UE
- **Preuve** : une détection n'est terminée qu'après exécution sur une vraie cible de test (cas vulnérable puis corrigé) ; cibles : Supabase local en Docker, puis un vrai projet
- **Rythme** : projet solo, soirs et week-ends ; petites itérations vérifiables

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Front entièrement en Next.js | Landing référencée et app dans un même front | — Pending |
| Worker Docker + file de jobs Postgres | Isolation de l'API, simple à opérer seul | — Pending |
| Claude via région UE pour les explications | Qualité des explications, données en UE, aucun secret envoyé | — Pending |
| Cibles de test : Supabase local Docker puis vrai projet | Preuve réelle sans dépendre d'un compte dès le début | — Pending |
| Commencer par 2 vérifications phares (secrets, RLS Supabase) de bout en bout | Exigence du brief §12 | — Pending |

---
*Last updated: 2026-10-23 after initialization (version légère)*
