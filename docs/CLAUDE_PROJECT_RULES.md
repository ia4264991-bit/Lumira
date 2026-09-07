# Claude Project Rules — Lumira

Rules for any Claude chat (Architecture, Backend, or Android) working on this
project. These exist because this project has already lost real work twice to
sandbox instability and once had a live credential pasted into a chat
transcript — these rules are direct responses to those specific incidents, not
generic advice.

## 0. Document hierarchy — these are not five competing sources of truth

1. **LPTS chapters / domain specification** (`Lumira.pdf`, and any drafted
   chapter markdown) — the authoritative architectural specification.
   Everything below exists to operationalize or describe this, not to
   compete with it.
2. **`docs/DECISIONS.md`** — the frozen ADR/decision register. It
   operationalizes the LPTS into binding, numbered decisions. Authoritative
   over any code, comment, or informal summary that contradicts it. Where an
   AD and its underlying LPTS chapter appear to disagree, treat that as a
   specification inconsistency requiring explicit resolution/revision — do
   not choose whichever is more convenient.
3. **`docs/LUMIRA_STATE.md`** — current implementation/project state only.
   Describes where the project *is*, never what it *should be*. It has no
   authority over architecture — if it ever reads like it's making an
   architectural decision, that's a wording bug in that file, not a decision.
4. **`API_CONTRACT.md`** — the authoritative API contract for the network
   boundary only (REST shapes, headers, error format). Does not govern domain
   architecture.
5. **`CLAUDE_PROJECT_RULES.md`** (this file) — governs *how* any chat works
   with the four documents above. It never overrides their content.

**The specific failure this prevents:** a future chat reads
`LUMIRA_STATE.md`, finds a stale or loosely-worded sentence, and treats it as
if it overrides something actually settled in the LPTS chapters or
`DECISIONS.md`. It doesn't. If `LUMIRA_STATE.md` and `DECISIONS.md` ever
appear to disagree, `DECISIONS.md` (and the LPTS chapters behind it) wins,
and `LUMIRA_STATE.md` gets corrected — never the reverse.

## 1. GitHub `master` is the source of truth for implementation state

The committed, pushed state of `master` is the source of truth for **what has
actually been built** — not a chat's memory of a prior session, not a zip
file, not a summary of "what's already there." **This does not override the
architectural document hierarchy in §0.** If GitHub's code ever appears to
contradict `DECISIONS.md` or the LPTS chapters, that is a bug in the code (or
a sign an AD needs a revision entry), not evidence that the code should be
treated as the new architecture. Before making changes, clone/pull fresh
rather than trusting a summary.

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

Each implementation session works in small, explicitly-scoped steps (see the
backend's own step tracker in `backend/README.md` and `docs/LUMIRA_STATE.md`).
Do not proceed to the next step without explicit approval. Do not expand
scope mid-step ("while I was at it, I also...") without flagging it first.

## 4. Do not silently invent fields or resolve ambiguity alone

If the specification (LPTS chapters, `DECISIONS.md`) is ambiguous or silent
on something, stop and ask rather than guessing and documenting the guess as
if it were settled. Precedent: the `updatedAt` question and the
`CourseOfferingMembership` scoping question were both correctly flagged
rather than silently resolved.

## 5. Frozen decisions (`DECISIONS.md`) are not renegotiated casually

An AD only changes via an explicit, reasoned revision entry in
`docs/DECISIONS.md` — never a silent rewrite, and never inferred implicitly
from an unrelated piece of code. If you believe an AD is wrong, say so
explicitly ("PROPOSED CHANGE TO AD-XXX") rather than building around it as if
it had already changed.

## 6. Whoever changes the contract updates it in the same commit

Any change to a REST endpoint, request/response shape, header, or error
format must update `API_CONTRACT.md` in the same commit. Any change to a
frozen architecture decision must update `docs/DECISIONS.md` in the same
commit. Docs and code drift apart the moment this rule is skipped once.

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

## 9. Role boundaries between the three chats

- **Architecture/Integration chat**: plans, audits, reconciles, maintains
  shared docs. Does not write or push code, except for the shared docs
  themselves (`DECISIONS.md`, `LUMIRA_STATE.md`, `API_CONTRACT.md`,
  `CLAUDE_PROJECT_RULES.md`) when a decision has actually been made and
  confirmed. It may read/clone the repository and review code or repository
  state as part of an audit — but reviewing is not the same as modifying
  implementation code, and it does not become a fourth implementation chat.
- **Backend chat**: implements backend steps only. Reads `docs/DECISIONS.md`
  and `API_CONTRACT.md` before starting any step. Does not redesign
  architecture — if something in the spec seems wrong or ambiguous while
  implementing, stop and flag it back to the Architecture chat rather than
  deciding unilaterally.
- **Android chat**: implements frontend features only. Same rule — reads
  `API_CONTRACT.md` before writing any network-facing code, flags contract
  questions back rather than guessing at backend shapes.

## 10. When in doubt about scope, under-build rather than over-build

Precedent: the CourseSpace/Community architecture question was resolved by
choosing the smaller, currently-justified concept (`CourseOfferingMembership`)
over a more flexible but unvalidated one (`CourseSpace`), while deliberately
leaving the sharing mechanism itself undecided (AD-018) rather than locking
in a schema or target model ahead of the Sharing chapter. Prefer the version
of a decision that's cheaper to extend later over the version that's more
"future-proof" but unproven — see `docs/DECISIONS.md`'s Future Extension
Points for the pattern to follow when a real need for more structure
actually shows up.

## 11. Report architectural discoveries and proposed changes — never resolve them silently

If implementation reveals that an existing specification, frozen AD, or API
contract cannot support the required behavior, do not silently modify the
architecture to make it fit. Stop at the boundary where the conflict was
discovered, explain it clearly, identify the specific affected document/AD/
contract, and propose the smallest change for Architecture/Integration
review. Implementation resumes only after the change is explicitly approved
and the authoritative document is updated — never before. This closes the
gap Rule 4 leaves open: Rule 4 says don't invent an answer to an ambiguity;
this rule says don't invent a fix to a genuine specification conflict either,
even when the fix seems obviously correct.

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
