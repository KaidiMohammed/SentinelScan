# Requirements: SentinelScan

**Core Value:** Des résultats vrais, chaque constat prouvé par une vérification réelle.

## v1 Requirements (milestone v0.1 : vérifications phares)

### Socle (CORE)
- [ ] **CORE-01**: Un scan est un job en tâche de fond exécuté par un worker isolé, avec statut par vérification (en cours, constat, aucun problème, n'a pas pu être vérifié + raison)
- [ ] **CORE-02**: La progression en direct affiche chaque vérification réellement en cours
- [ ] **CORE-03**: Chaque constat stocke sa preuve brute (requête, extrait de réponse ou sortie d'outil), secrets masqués
- [ ] **CORE-04**: Journal de qui a scanné quoi et quand

### Secrets exposés (SEC)
- [ ] **SEC-01**: Le scan passif télécharge les bundles JS publics de l'URL et y détecte les secrets connus (Supabase service_role, Stripe live, OpenAI, etc.)
- [ ] **SEC-02**: Un secret n'est jamais stocké en clair ni affiché ; seule une empreinte et un extrait masqué sont conservés
- [ ] **SEC-03**: Prouvé sur une cible de test : clé exposée détectée, puis plus rien après correction

### Domaine et Supabase RLS (DOM, SUPA)
- [ ] **DOM-01**: Un compte prouve la propriété d'un domaine par TXT DNS ou fichier, avec étapes guidées ; revérification avant chaque scan actif
- [ ] **DOM-02**: L'utilisateur accepte des conditions confirmant qu'il est autorisé à tester la cible
- [ ] **SUPA-01**: Sur domaine vérifié, le scan teste en lecture seule, avec la clé anonyme publique, si des tables Supabase sont lisibles sans authentification
- [ ] **SUPA-02**: Prouvé sur Supabase local Docker : table sans RLS détectée, plus rien après ajout de la politique RLS

### Rapport (REP)
- [ ] **REP-01**: Note A–F calculée par une formule documentée et constante à partir des constats réels
- [ ] **REP-02**: Liste « corrigez ces 3 points d'abord »
- [ ] **REP-03**: Carte de constat dépliable : titre, gravité, explication simple, impact, preuve masquée, correctif copier-coller adapté à la stack (ex. politique SQL RLS)
- [ ] **REP-04**: Re-scan avec comparaison avant/après
- [ ] **REP-05**: Rapport en français et en anglais

## v2 Requirements (milestones suivantes)

- **STACK-01**: Détection de la stack et des versions, correspondance OSV.dev (CVE)
- **HDR-01**: En-têtes de sécurité, cookies, TLS (sslyze)
- **SRC-01**: Source maps publiques
- **FIRE-01**: Firebase Firestore / Realtime DB / Storage lisibles publiquement
- **EXP-01**: Fichiers sensibles exposés (.env, .git, sauvegardes, /actuator, admin) via Nuclei
- **CORS-01**: Mauvaise configuration CORS
- **AI-01**: Explications et correctifs rédigés par Claude (UE) pour les constats réels uniquement
- **PDF-01**: Export PDF ; correspondance RGPD / NIS2 (informative)
- **MON-01**: Abonnement : re-scans planifiés et alertes e-mail ; offres audit ponctuel et agence (marque blanche)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Scan authentifié, IDOR, logique métier | Hors v1 (brief §9) |
| Pentest réseau, mobile | Hors v1 |
| Constats simulés ou de démo | Interdit (brief §6) |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| CORE-01 | Phase 1 | Pending |
| CORE-02 | Phase 1 | Pending |
| CORE-03 | Phase 1 | Pending |
| CORE-04 | Phase 1 | Pending |
| SEC-01 | Phase 2 | Pending |
| SEC-02 | Phase 2 | Pending |
| SEC-03 | Phase 2 | Pending |
| DOM-01 | Phase 3 | Pending |
| DOM-02 | Phase 3 | Pending |
| SUPA-01 | Phase 3 | Pending |
| SUPA-02 | Phase 3 | Pending |
| REP-01 | Phase 4 | Pending |
| REP-02 | Phase 4 | Pending |
| REP-03 | Phase 4 | Pending |
| REP-04 | Phase 4 | Pending |
| REP-05 | Phase 4 | Pending |
