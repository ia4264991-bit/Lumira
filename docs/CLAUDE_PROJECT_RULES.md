# Claude Project Rules — Lumira

> ⚠️ **§0's document hierarchy is RETIRED — 2026-09-12.** It named the LPTS/
> `DECISIONS.md`/`API_CONTRACT.md` as authoritative for a domain architecture
> that has since been retired (see the retirement notices in those files
> themselves). **A new hierarchy for the prototype-first architecture has
> not been created yet** — this is deliberate; it's the next task, not part
> of this cleanup. Rules 1–11 below are general engineering/process
> discipline, not tied to the old domain model — they remain in force.

Rules for any chat or agent (Architecture, Backend, or Android — including
Antigravity, Claude Code, or Gemini in Android Studio) working on this
project. These exist because this project has already lost real work twice
to sandbox instability and once had a live credential pasted into a chat
transcript — these rules are direct responses to those specific incidents,
not generic advice.

## 0. Document hierarchy — RETIRED, pending replacement

The old hierarchy (LPTS chapters → `docs/DECISIONS.md` → `docs/LUMIRA_STATE.md`
→ `API_CONTRACT.md` → this file) governed the retired academic-hierarchy
architecture. Until a new hierarchy is defined: `docs/LUMIRA_STATE.md`'s
"current repository state" section is the most reliable single source for
what's actually true right now, since it was rewritten as part of this
retirement to be accurate. Do not assume `docs/DECISIONS.md` or
`API_CONTRACT.md` are authoritative for anything new — both now carry their
own explicit retirement notices and are preserved for historical reference
only.

## 1. GitHub `master` is the source of truth for implementation state

The committed, pushed state of `master` is the source of truth for **what has
actually been built** — not a chat's memory of a prior session, not a zip
file, not a summary of "what's already there." Before making changes,
clone/pull fresh rather than trusting a summary.

## 2. Once approved, commit and push promptly — never rely on zip delivery alone

A prior backend session lost significant work twice because it treated
zip-file delivery as its checkpoint, and the sandbox reset silently between
turns, taking uncommitted work (and local git commits that were never pushed)
with it. Rule going forward: **once an approved implementation unit is
complete, commit and push it promptly rather than accumulating unpushed
work** — not just at "step" boundaries. This is not permission to push ahead
of approval; it does not override Rule 3's approval gate. **A commit that
exists only locally is not a durable checkpoint** — that is the exact
failure mode that caused the earlier data loss (local commits sitting on a
sandbox filesystem that reset without warning). A zip is a convenience for
the person to inspect, never the only copy of the work.

## 3. One step at a time, approval-gated

Each implementation session works in small, explicitly-scoped steps. Do not
proceed to the next step without explicit approval. Do not expand scope
mid-step ("while I was at it, I also...") without flagging it first.

## 4. Do not silently invent fields or resolve ambiguity alone

If the specification is ambiguous or silent on something, stop and ask
rather than guessing and documenting the guess as if it were settled.

## 5. Frozen decisions are not renegotiated casually

Once a new decisions register exists, an entry only changes via an explicit,
reasoned revision — never a silent rewrite, and never inferred implicitly
from an unrelated piece of code.

## 6. Whoever changes the contract updates it in the same commit

Any change to a REST endpoint, request/response shape, header, or error
format must update the API contract document in the same commit. Any change
to a frozen architecture decision must update the decisions register in the
same commit. Docs and code drift apart the moment this rule is skipped once.

## 7. Disclose sandbox/tooling limitations honestly, don't imply false confidence

If something couldn't actually be verified (e.g. no network access to Maven
Central, no live compile, no real test run), say so plainly rather than
presenting manual review as equivalent to execution. "I reviewed this
carefully" and "I ran this and it passed" are different claims — don't blur
them.

## 8. Never paste credentials into a chat message

A GitHub PAT was pasted in plaintext into a chat transcript earlier in this
project. Treat any credential that has ever appeared in a chat as compromised
— rotate it immediately, regardless of whether anything went wrong. Going
forward, prefer environment-level credential handling over pasting tokens
directly into a conversation. Never ask the person to paste a token,
password, or private key into chat when an environment variable, credential
manager, Git credential helper, or interactive authentication mechanism can
be used instead — including phrasing that would prompt someone to paste one
(e.g. "send me the PAT and I'll configure it").

## 9. Role boundaries between implementation surfaces

- **Architecture/Integration**: plans, audits, reconciles, maintains shared
  docs. Does not write or push implementation code except the shared docs
  themselves, when a decision has actually been made and confirmed. May
  read/clone the repository and review code as part of an audit — reviewing
  is not modifying.
- **Backend implementation** (Antigravity, Claude Code, or a dedicated
  backend chat): implements backend work only. Reads the current governing
  docs before starting. Does not redesign architecture — flags ambiguity or
  conflict back rather than deciding unilaterally.
- **Android implementation** (Android Studio / Gemini, or a dedicated
  frontend chat): implements frontend features only. Same rule.

## 10. When in doubt about scope, under-build rather than over-build

Precedent from the retired architecture, worth keeping as a principle even
though the specific example is retired: the CourseSpace/Community question
was resolved by choosing the smaller, currently-justified concept over a
more flexible but unvalidated one, and deliberately leaving mechanism
details undecided rather than locking them in ahead of need. Prefer the
version of a decision that's cheaper to extend later over the version
that's more "future-proof" but unproven.

## 11. Report architectural discoveries and proposed changes — never resolve them silently

If implementation reveals that an existing specification, frozen decision,
or API contract cannot support the required behavior, do not silently
modify the architecture to make it fit. Stop at the boundary where the
conflict was discovered, explain it clearly, identify the specific affected
document, and propose the smallest change for Architecture/Integration
review. Implementation resumes only after the change is explicitly approved
and the authoritative document is updated — never before.

---

## Revisions

- 2026-09-06 — **One-time, explicit exception to Rule 9.** The product owner
  authorized the specific chat session that had been serving as
  Architecture/Integration for this project to also carry out the Backend
  Step 3 implementation (University/Semester through read-only controllers),
  rather than opening a separate Backend chat as Rule 9 otherwise requires.
  This was flagged explicitly by that same session before proceeding, and
  the product owner confirmed the override knowingly rather than it being
  applied silently. **This is not a permanent change to the three-chat
  structure** — Rule 9's role separation remains the default for all future
  work. If this exception is invoked again, add another dated entry here
  rather than treating this one as a standing precedent.

- 2026-09-07 — **Second exception, this time invoked in this same chat.**
  The product owner authorized this Architecture/Integration chat session
  to begin backend Slices 1 onward directly (University/Semester through
  read-only controllers), for cost reasons — Claude Code requires a paid
  tier not yet purchased. **Critical limitation, disclosed at the time per
  Rule 7:** this chat's sandbox has no network access to Maven Central
  (`repo.maven.apache.org` returns `403 host_not_allowed`, confirmed by a
  direct test), so nothing built here can be compiled or tested — only
  manually reviewed. This is the same category of gap that made the
  original lost Step 3 work also unverified by execution. Everything
  committed under this exception is labeled manually-reviewed-only in
  `LUMIRA_STATE.md`, and running a real `mvn compile && mvn test` against
  it is the first priority once Claude Code is available — not optional
  follow-up cleanup.

- 2026-09-07 — **Explicit, informed exception to Rule 8.** The product
  owner directed this session to reuse the GitHub PAT already embedded in
  this sandbox's git remote (originally pasted into this chat earlier in
  the project) rather than generating a fresh one, after Rule 8's guidance
  was raised directly and twice. This is a deliberate, acknowledged
  deviation, not a silent one. Scope/blast-radius note for the record: this
  token was created as a fine-grained PAT scoped to only the `Lumira`
  repository with Contents read/write — not an account-wide credential.
  Rule 8 remains the default going forward; this is a one-time, logged
  exception, not a change to the rule.
