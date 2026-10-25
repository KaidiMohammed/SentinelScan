# Roadmap: SentinelScan

## Overview

v0.1 construit de bout en bout les deux vérifications phares du brief (secrets exposés dans les bundles JS,
RLS Supabase), prouvées sur de vraies cibles de test, avant toute autre vérification. Les milestones
suivantes ajoutent la largeur passive, l'IA et le PDF, puis la surveillance payante.

## Milestones

- 🚧 **v0.1 Vérifications phares** - Phases 1-4 (in progress)
- 📋 **v0.2 Largeur passive** - stack + OSV, en-têtes/TLS, source maps, Firebase, fichiers exposés, CORS (planned)
- 📋 **v0.3 IA, PDF et conformité** - explications Claude UE, export PDF, RGPD/NIS2 (planned)
- 📋 **v0.4 Surveillance et offres** - re-scans planifiés, alertes, abonnements, agence (planned)

## Phases

- [ ] **Phase 1: Socle des scans** - Jobs en tâche de fond, worker isolé, progression en direct, preuves
- [ ] **Phase 2: Secrets exposés dans les bundles** - Détection passive, masquage, preuve sur cible de test
- [ ] **Phase 3: Domaine vérifié et RLS Supabase** - Propriété du domaine, test RLS en lecture seule
- [ ] **Phase 4: Rapport et note** - Note A–F, top 3, cartes de constat, re-scan avant/après, FR/EN

## Phase Details

### Phase 1: Socle des scans

**Goal**: Un scan lancé sur une URL devient un job exécuté par un worker isolé, dont chaque vérification remonte en direct un statut vrai et une preuve.
**Depends on**: Nothing (first phase)
**Requirements**: CORE-01, CORE-02, CORE-03, CORE-04
**Success Criteria** (what must be TRUE):

  1. Un scan lancé depuis le front crée un job Postgres pris par le worker Docker
  2. La progression affiche chaque vérification réellement en cours, sans faux spinner
  3. Une vérification qui échoue apparaît « n'a pas pu être vérifié » avec la raison

**Plans**: 2 plans
**UI hint**: yes

### Phase 2: Secrets exposés dans les bundles

**Goal**: Le scan passif trouve les secrets connus dans les bundles JS publics et les affiche toujours masqués, preuve à l'appui.
**Depends on**: Phase 1
**Requirements**: SEC-01, SEC-02, SEC-03
**Success Criteria** (what must be TRUE):

  1. Une clé Supabase service_role placée dans le bundle d'une cible de test est détectée avec sa preuve masquée
  2. Après retrait de la clé, le re-scan ne rapporte plus rien
  3. Aucun secret en clair en base ni dans les logs

**Plans**: 1 plan

### Phase 3: Domaine vérifié et RLS Supabase

**Goal**: Après preuve de propriété du domaine, le scan actif détecte en lecture seule les tables Supabase lisibles sans authentification.
**Depends on**: Phase 1
**Requirements**: DOM-01, DOM-02, SUPA-01, SUPA-02
**Success Criteria** (what must be TRUE):

  1. L'utilisateur vérifie son domaine par TXT DNS ou fichier, et la vérification est refaite avant chaque scan actif
  2. Sur Supabase local Docker, une table sans RLS est signalée avec la requête et l'extrait de réponse en preuve
  3. Après ajout de la politique RLS, le re-scan ne signale plus la table

**Plans**: 1 plan

### Phase 4: Rapport et note

**Goal**: Le fondateur comprend son résultat et applique un correctif en moins de 15 minutes.
**Depends on**: Phase 2, Phase 3
**Requirements**: REP-01, REP-02, REP-03, REP-04, REP-05
**Success Criteria** (what must be TRUE):

  1. La note A–F suit une formule documentée et constante
  2. Chaque carte donne explication, impact, preuve masquée et correctif à copier adapté à la stack
  3. Le re-scan montre l'avant/après ; le rapport existe en français et en anglais

**Plans**: TBD
**UI hint**: yes

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Socle des scans | 1/2 | In Progress|  |
| 2. Secrets exposés dans les bundles | 0/1 | Not started | - |
| 3. Domaine vérifié et RLS Supabase | 0/1 | Not started | - |
| 4. Rapport et note | 0/? | Not started | - |
