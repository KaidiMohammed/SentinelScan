# Brief initial — SentinelScan

> Brief fourni par le porteur le 2026-10-23, conservé tel quel. Source de référence pour toutes les phases.

# PROJECT BRIEF — SentinelScan (working name)

## 1. THE PROBLEM
Thousands of web apps are now built quickly with AI and no-code tools (Lovable, Bolt,
Cursor, v0) on top of BaaS platforms (Supabase, Firebase). Their creators ship fast but
don't know security. The same serious mistakes repeat constantly:
- secret keys (Supabase service_role, Stripe live keys, OpenAI keys) exposed in frontend JS
- Supabase tables readable by anyone because Row Level Security is missing
- Firebase databases or storage left publicly readable
- exposed .env files, .git folders, source maps, admin routes
- outdated libraries with known CVEs, missing security headers, weak TLS

These founders have no affordable way to know they're exposed:
- free tools (ZAP, Burp, securityheaders.com) are too technical or too narrow
- professional pentests and AI pentest platforms cost €1,000+ and target enterprises
- existing scanners output CVE lists and jargon a non-developer can't act on

The result: real user data leaks from apps whose owners had no idea.

## 2. WHO IT'S FOR
Primary user — "the vibe-coding founder":
- built a SaaS or app with AI/no-code tools, often solo, limited security knowledge
- has real users and data, fears a leak but doesn't know where to start
- wants a clear answer: "am I exposed, how badly, and what do I do right now?"

Secondary user — "the freelancer / small agency":
- builds apps for clients, wants to prove security before delivery
- needs a clean report to hand to a client

Market focus: France and EU first. Reports available in French and English.

## 3. WHAT THE USER MUST BE ABLE TO DO (jobs to be done)
1. Paste a URL and, within minutes, learn whether the app has serious security problems —
   without installing anything or understanding security.
2. Understand each problem in plain language: what it is, what an attacker could actually
   do with it, how urgent it is.
3. Know exactly what to fix first ("fix these 3 things first").
4. Fix each problem with a copy-paste solution adapted to their stack (e.g. the exact SQL
   RLS policy, the Firebase rules file, the header config for Vercel/Netlify).
5. Re-scan after fixing and see the problem disappear and the grade improve.
6. Export a professional report (PDF) to share with a client, investor, or auditor.
7. (Paid) Be alerted automatically when a new problem appears after a deploy or when a new
   CVE affects their stack.

## 4. USER JOURNEY
1. Landing page: one URL field, "Scan my app".
2. Free quick scan runs immediately on any URL — passive checks only (what any visitor's
   browser can see). Live progress shows each check running.
3. Result: overall grade A–F, number of issues by severity, top issues partially shown.
4. To unlock the full audit (deeper active checks), the user creates an account and proves
   they own the domain (DNS TXT record or verification file). Clear, guided steps.
5. Full report: top-3 fixes, then every finding as an expandable card with explanation,
   impact, evidence, and copy-paste fix.
6. User fixes, clicks "Re-scan", sees the before/after.
7. Optional subscription: continuous monitoring and alerts.

## 5. FUNCTIONAL REQUIREMENTS
Detection — passive (any URL):
- Detect the technology stack: framework, hosting, BaaS, JS libraries, with versions.
- Match detected versions against a real vulnerability database (OSV.dev) for known CVEs.
- Find secrets exposed in frontend JS bundles; always redact them in the UI.
- Analyse security headers, cookie flags, TLS configuration.
- Detect publicly accessible source maps.

Detection — active (verified domains only):
- Supabase: test whether tables are readable without authentication (missing RLS).
- Firebase: test whether Firestore / Realtime DB / Storage are publicly readable.
- Probe for exposed sensitive files and paths (.env, .git, backups, /actuator, admin).
- Detect CORS misconfiguration.

Report:
- Overall grade A–F computed from real findings with a documented, consistent formula.
- Prioritized "fix these first" list.
- Per finding: title, severity, plain-language explanation, real-world impact, redacted
  evidence, stack-specific copy-paste fix.
- Mapping of findings to GDPR / NIS2 obligations (informational, not legal advice).
- PDF export. French and English.

Accounts & monitoring:
- Accounts, domain ownership verification, scan history with before/after comparison.
- Paid plan: scheduled re-scans and email alerts on new findings.

## 6. NON-NEGOTIABLE REQUIREMENT — TRUTHFUL RESULTS
The entire value of this product is trust. Therefore:
- Every finding must come from a real check executed against the live target at scan
  time. Never mock, hardcode, simulate, or seed findings — not even for demos.
- Every finding stores the raw evidence that proves it (the request and response excerpt,
  or the real tool output), viewable by the user.
- If a check fails or can't run, show it as "could not be checked" with the reason. Never
  show a fake pass or a fake finding.
- The AI is used only to explain and suggest fixes for findings that really exist. It must
  never create, infer, or embellish findings. Severity comes from the check, not the AI.
- Prefer proven open-source engines (Nuclei, Retire.js, sslyze, OSV.dev) over
  reimplementing detection logic.
- A feature involving a security check is only "done" once it has been run against a real
  test target and returned real evidence — including a vulnerable case and a fixed case.
- No false alarms over noise: prefer fewer, verified findings to many speculative ones.

## 7. SAFETY & LEGAL REQUIREMENTS
- Active checks run only on domains whose ownership has been verified, re-verified before
  each active scan. Passive checks only read what is publicly served.
- All checks are strictly non-destructive: read-only, never write, modify, or delete data
  on the target. Respect rate limits.
- Users must accept terms confirming they are authorized to test the target.
- Log who scanned what and when.
- Secrets found are never stored in clear, never sent to the AI, always redacted.
- Hosting and data in the EU.

## 8. EXPERIENCE REQUIREMENTS
- The user is not technical: zero jargon without explanation, every screen answers
  "what does this mean for me and what do I do?"
- Modern, clean, friendly UI — spacious layout, rounded cards, calm palette with one
  accent color, smooth transitions, light and dark mode. Feels like Linear/Vercel/Stripe,
  never like a dated enterprise security dashboard.
- Reassuring tone, not alarmist: problems are serious but fixable, and the product guides
  the fix.
- Live scan progress that reflects real check execution — no fake spinners.
- Fully responsive and accessible (contrast, keyboard navigation, screen readers).

## 9. OUT OF SCOPE (v1)
- Authenticated scanning (logging into the target app).
- Business-logic testing, IDOR testing, autonomous AI pentesting.
- Network / infrastructure pentesting, mobile apps.
- Scanning domains the user hasn't verified (beyond passive checks).
- Certifications or legal compliance guarantees.

## 10. SUCCESS CRITERIA
- A non-technical founder understands their result and applies a fix within 15 minutes of
  first scan, without outside help.
- Zero fabricated findings: every finding reproducible from its stored evidence.
- On a test app with known planted vulnerabilities (exposed key, RLS off, open Firebase,
  exposed .env), the scan detects all of them; on the fixed version, it reports none.
- Full scan completes in under 10 minutes.
- Beta: 15–20 real founders scan their apps; a meaningful share find at least one real
  issue and say they'd pay for monitoring.

## 11. CONSTRAINTS
- Built solo, evenings and weekends: favour simplicity, proven tools, and a small first
  release that does the flagship checks (exposed secrets, Supabase RLS) perfectly before
  adding breadth.
- Developer stack preference: Java/Spring Boot backend, React/Next.js frontend, Postgres.
  Scans run as asynchronous background jobs in isolated workers.
- Pricing to support: free quick scan, one-time full audit, monthly monitoring, agency
  plan with multiple domains and white-label reports.

## 12. HOW TO BUILD THIS
- Before writing any code, ask me the questions needed to remove ambiguity in this brief,
  then propose a short plan (milestones and their order) and wait for my approval.
- Build in small, verifiable increments. Start with the two flagship checks — exposed
  secrets in frontend bundles and Supabase RLS — built end-to-end (scan → evidence →
  report card) and proven against a real test target before adding anything else.
- For every detection feature, show me the proof it works: the test target used, the
  vulnerable result with its evidence, and the clean result after the fix.
- Never mark a feature as finished if any part of it is stubbed, mocked, or simulated.
  If something isn't implemented yet, say so explicitly and show it as "not implemented"
  in the UI.
- At the end of each increment, summarise what was built, what was verified, and what
  remains.
