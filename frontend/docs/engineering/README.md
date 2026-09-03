# Engineering Reports

This directory is permanent project documentation, maintained alongside the
source code it describes — not a scratch space. Every completed Selection
Engine milestone has a corresponding report here, named after the
milestone.

## Convention

- One file per milestone: `docs/engineering/<MILESTONE_NAME>.md`
  (e.g. `M1.0_Foundation_Layer.md`, `M1.1_Text_Layout_Engine.md`).
- A report is written **at the close of its milestone**, after
  implementation and the corresponding audit are both complete — it
  reflects what was actually built and verified, not what was planned.
- Each report covers, in order:
  1. Objectives
  2. Architectural Decisions
  3. Files Created
  4. Files Modified
  5. Verification Performed
  6. Known Limitations
  7. Risks
  8. Next Milestone
- If a later milestone changes something a prior report described (e.g. a
  file gets modified again, a documented limitation gets resolved), update
  the **current** milestone's report to note the change and, where useful,
  cross-reference the prior report — don't silently rewrite history in an
  old report. Prior reports are a record of what was true when they were
  written.
- These files are reviewed and corrected the same way code is: if an audit
  finds a report describing something the code no longer does (see
  `M1.1_Text_Layout_Engine.md`'s note about correcting a stale
  `PACKAGE_STATUS.md`), fix the documentation in the same pass.

## Index

| Milestone | Report | Status |
|---|---|---|
| M1.0 | [M1.0_Foundation_Layer.md](./M1.0_Foundation_Layer.md) | Complete |
| M1.1 | [M1.1_Text_Layout_Engine.md](./M1.1_Text_Layout_Engine.md) | Complete |
| M1.2 | [M1.2_Selection_Resolution_Engine.md](./M1.2_Selection_Resolution_Engine.md) | Complete |
| M1.3 | [M1.3_Selection_Integration.md](./M1.3_Selection_Integration.md) | Complete |
| M1.4 | [M1.4_Performance_Optimization.md](./M1.4_Performance_Optimization.md) | Complete |
