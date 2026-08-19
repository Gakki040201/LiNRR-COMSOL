# M03A.6 experimental evidence acquisition report

## Outcome

The current formal state records `ACQUISITION_PACKAGE_READY=TRUE`, meaning that
the acquisition workflow and its blank external-package structure are available
for controlled use. No real experimental file or measurement result was
supplied or created. This state is not experimental calibration or a
`READY_FOR_PRECOMMIT` determination. It does not indicate that parameter
transfer ran or that M03B was authorized.

## Scientific state

- `RUN_STATE=EXPERIMENTAL_INPUT_WAIT`
- `ACQUISITION_PACKAGE_READY=TRUE`
- `EXPERIMENTAL_EVIDENCE_RECEIVED=FALSE`
- `EXPERIMENTAL_INPUT_COMPLETE=FALSE`
- `PARAMETER_TRANSFER_MODE=NOT_RUN`
- `M03B_CANDIDATE=FALSE`
- `M03B_READY=FALSE`

## Delivered controls

- A bench-ordered runbook, operator checklist, and external evidence-directory
  contract contain no experimental values.
- The initializer requires an explicit external directory, rejects repository
  destinations and every existing target, audits all existing path segments,
  and transactionally renames a validated sibling staging directory.
- Its worksheets provide the columns required for controlled transcription to
  the four unchanged M03A.5 canonical configuration files. Initial records
  contain no measurement values; only controlled template fields such as
  `data_origin=EXPERIMENTAL` and `status=MISSING` are prefilled.
- The freeze tool blocks reparse points in the root, ancestors, and descendants,
  preserves raw bytes and `LastWriteTimeUtc`, records exact `Int64` sizes and
  SHA-256 with ordinal ordering, and refuses case-equivalent version overwrite.
- The normalized-EIS tool requires explicit column mapping, preserves source
  row order, emits separate normalized/row-map/provenance files transactionally,
  records complete execution provenance, and blocks unknown profiles and every
  input/output path collision. Regression independently recomputes all three
  file sizes and hashes and compares exact profile, mapping, path, time, flag,
  normalized-row, and row-mapping values.
- Preflight reopens actual files; recalculates exact sizes and SHA-256; enforces
  role-directory binding and the evidence-role allowlist; parses normalized EIS
  content; binds raw/normalized evidence by composite sample/cell/replicate key;
  and blocks orphan, cross-sample, cross-cell, unknown, duplicate, and
  unregistered evidence.
- Inventory, metadata, ledger, role binding, collision detection, and actual
  file registration use normalized package-path identities. The regression
  suite exercises noncanonical relative-path, case-alias, duplicate-identity,
  and conflicting-role rejection. These tests document the covered software
  behavior; they are not a general proof against every filesystem race or path
  representation.
- Each high-level operation resolves the package root from disk once and reuses
  that operation-local context; no canonical-root result is cached across
  independent operations. Every existing path segment must have exactly one
  case-insensitive filesystem match. A missing tail is accepted only by an
  explicit creation mode after confirming that it is truly absent.
- Preflight requires the selected hash ledger and its rows to resolve to package
  files and recomputes `Int64` byte sizes and SHA-256 values rather than treating
  ledger declarations as measurements. Missing-file, size-mismatch, and
  hash-mismatch regressions cover the reported failure codes. A086 isolates the
  explicit selected-ledger row loop with `SELECTED_LEDGER_A086.csv`, which does
  not match the automatic `HASH_LEDGER_*.csv` candidate scan. A089 verifies that
  actual size and hash recomputation precede semantic map checks and that an
  invalid row is not inserted. Explicit and automatically selected ledger paths
  now use the same required-existing resolver, and the caller's original full
  path spelling must equal the disk-resolved canonical full path by an ordinal
  comparison. A090 and A091 cover root-prefix case aliases plus `.`, `..`, and
  forward-separator lexical aliases. This report does not elevate that software
  coverage into an unconditional security guarantee.
- Synthetic fixtures are regression-only. Their passing results are software
  test results, not real experimental evidence, experimental authenticity, or
  scientific validation.
- Focused `-OnlyTestId` runs require an explicit repository-external output;
  only a complete continuous full run may atomically replace the durable table.
  The publisher uses a same-directory temporary file and a recoverable backup;
  injected failures before replacement preserve the target, and failures after
  replacement restore its exact prior bytes before returning failure. The
  required-file disappearance window is covered by deterministic software fault
  injection; this is not evidence of an actual filesystem fault.
- The durable regression table records 91 PASS rows and 0 FAIL rows for A001
  through A091. This is a software-regression result, not an experimental
  calibration result or a precommit-readiness decision.

## Gate ownership

M03A.6 preflight can report only `ACQUISITION_PACKAGE_READY`,
`EXPERIMENTAL_EVIDENCE_RECEIVED`, or `PRE_INTAKE_BLOCKED`. Only the unchanged
published M03A.5 validator can determine formal
`EXPERIMENTAL_INPUT_COMPLETE`. No M03A.5 intake was run to simulate real
completion. M03A.6 preflight checks package structure, metadata, paths, and
declared file provenance; it does not prove experimental authenticity or
scientific validity.

## Execution and file record

Created files are the three `docs/M03A_6_*` acquisition documents; PowerShell
entry points 19 through 22 and `LiNRR_M03A6_Acquisition.psm1`; the M03A.6 test
and explicitly synthetic fixture directory; two readiness/evidence tables, the
tool-regression table, and this report. The only baseline file changed is the
append-only D0017 block in `docs/DECISIONS.md`.

Executed audits included the Windows PowerShell 5.1 parser, the 91-case
self-contained regression suite, CLI entry-point smoke tests, UTF-8 without BOM,
RFC 4180, exact adjacent `Int64` values above 2^53, all 282 non-decision
baseline paths, D0017 byte-prefix append-only, `git diff --check`,
`git diff --stat`, and `git status`. These recorded implementation audits do not
replace an independent precommit review.

No Java source changed, so `comsolcompile` was not applicable. In accordance
with the explicit M03A.6 boundary, COMSOL and `comsolbatch` were not run and no
MPH was created. No real intake or parameter transfer was run, and no M03B
authorization occurred. Nothing was staged, committed, or pushed when this
report was produced.

## Assumptions

No physical-model or experimental-value assumption was introduced. The only
conversion profile implemented is a generic, explicit RFC 4180 column map; it
assumes no instrument format and blocks every unknown profile. The acquisition
preflight evaluates file and metadata readiness only. It neither authenticates
the experimental origin of supplied content nor establishes scientific
validity. All formal experimental-completeness decisions remain exclusively
controlled by the unchanged M03A.5 validator.

## Remaining uncertainty

All measured values, uncertainties, instruments, dates, source references,
byte sizes, hashes, replicate compatibility, HFR review, direct-versus-derived
conductivity comparison, and transfer authorization remain unknown until real
controlled evidence is received. The temporary positive software structure
used to exercise `EXPERIMENTAL_EVIDENCE_RECEIVED` was synthetic regression
material outside the repository and did not change this formal state. It tests
only the positive state-machine branch: M03A.6 preflight does not prove
experimental authenticity or scientific validity. Formal experimental
completeness remains exclusively owned by the unchanged M03A.5 validator. This
report makes no `READY_FOR_PRECOMMIT` claim; submission readiness requires a
separate review of the current worktree.
