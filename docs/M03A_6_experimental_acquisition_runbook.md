# M03A.6 real experimental evidence acquisition runbook

## Scope and gate boundary

This runbook controls acquisition of real evidence for the published M03A.5
intake validator. Initializing a package or passing software regression means
only `ACQUISITION_PACKAGE_READY`. It is not experimental calibration. Do not
copy values from literature, prior synthetic fixtures, model outputs, or example
files into an experimental record. Unknown information remains blank and
`MISSING`.

The acquisition package must be outside the Git repository. Preserve raw files
exactly as emitted by the instrument. All identifiers and metadata below are
declared by the responsible operator; they are not inferred from filenames.
The initializer rejects any existing destination and creates a validated
staging directory before one same-volume rename. Do not create the destination
in advance. Repository containment and every existing path segment are checked
for case/slash/`..` bypasses and for reparse points, junctions, symbolic links,
and mount points.

## Ordered execution

1. Define one stable `sample_id` for the physical electrolyte/condition and one
   `cell_id` for the cell. Record the definitions in the laboratory notebook.
2. Record the operator, local experimental date/time, explicit UTC offset or
   `Z`, and laboratory notebook page or controlled electronic-record reference.
3. Acquire at least three independent EIS replicates. Give each a unique
   `replicate_id`; identify technical repeats separately from independent
   preparations. The package uses `(sample_id, cell_id, replicate_id)` as its
   binding key and additionally requires `replicate_id` to be package-unique.
4. Retain every raw instrument file unchanged under `raw/eis/`. Do not rename,
   edit, normalize, or replace it after acquisition.
5. Export a normalized EIS table without deleting any source row. Use
   `21_prepare_M03A_6_normalized_eis.ps1` only with a user-selected supported
   parser profile and explicit column mapping. The normalized header is exactly
   `frequency,z_real,z_imag`. Retain duplicate frequencies, inductive points,
   and insufficient-high-frequency information. Do not smooth, interpolate,
   remove outliers, or promote the first frequency point to HFR.
   Input, normalized output, row map, and provenance must resolve to four
   different paths. Review the provenance size/hash fields, column mapping,
   row counts, ISO 8601 conversion time/offset, and all `FALSE` processing
   flags. The timestamp is tool execution time, not measurement time.
6. Measure fixture resistance and retain its independent raw evidence.
7. Measure contact resistance and retain its independent raw evidence.
8. Measure membrane resistance and retain its independent raw evidence.
9. Measure other-series resistance and retain its independent raw evidence. A
   missing contribution is never entered as zero.
10. Measure electrode spacing and document the physical endpoints.
11. Measure out-of-plane depth and document its definition.
12. Measure geometric electrode area and document its definition.
13. Measure EIS area and its area basis independently.
14. Measure current-density reporting area and document its definition. Do not
    assume any two area definitions are equal without measurement or mapping
    evidence.
15. Complete an independent direct-conductivity measurement. Retain method,
    temperature, cell constant, calibration standard, instrument, uncertainty,
    and raw evidence for the later M03A.5 configuration.
16. Finish all files and metadata. Register evidence in `FILE_INVENTORY.csv` and
    the worksheets under `metadata/`; then stop editing the frozen version.
    Use the role-directory bindings in the directory contract. Do not place
    evidence in `hashes/` or `reports/`, and do not leave evidence files
    unregistered. Check that no series, area, or direct-conductivity row belongs
    to an orphan sample and no EIS evidence crosses a sample or cell.
17. Run `20_freeze_M03A_6_experimental_evidence.ps1` with an explicit unused
    `LedgerVersion`. It records exact `Int64` byte sizes and SHA-256 without
    converting size through floating point. Review the versioned read-only
    verification report.
18. Populate the unchanged M03A.5 formal files from the reviewed external
    package: experimental manifest, resistance ledger, area measurements, and
    direct conductivity. The M03A.6 worksheet headers are exact ordered copies
    of those four canonical schemas; transcribe same-name columns according to
    the mapping in the directory contract. Preserve units and provenance
    exactly and never let an acquisition tool modify M03A.5 configuration.
19. Change a formal row from `MISSING` to `MEASURED` only after every required
    value, uncertainty, method, path, exact size, SHA-256, and source reference
    is present and reviewed.
20. Run the published M03A.5 intake validator. M03A.6 preflight cannot set
    `EXPERIMENTAL_INPUT_COMPLETE=TRUE` and must not be used as a substitute.
21. Submit the M03A.5 outputs, external package ledger, and laboratory records
    for strict review. Do not run parameter transfer, COMSOL, or M03B without a
    later explicit authorization.

## Command examples without experimental values

```powershell
powershell.exe -NoProfile -File scripts\windows\19_initialize_M03A_6_acquisition_package.ps1 `
  -DestinationRoot D:\LiNRR_Experimental_Data\M03A_6\EXP001

powershell.exe -NoProfile -File scripts\windows\20_freeze_M03A_6_experimental_evidence.ps1 `
  -PackageRoot D:\LiNRR_Experimental_Data\M03A_6\EXP001 -LedgerVersion v001

powershell.exe -NoProfile -File scripts\windows\22_preflight_M03A_6_experimental_evidence.ps1 `
  -PackageRoot D:\LiNRR_Experimental_Data\M03A_6\EXP001 `
  -HashLedgerPath D:\LiNRR_Experimental_Data\M03A_6\EXP001\hashes\HASH_LEDGER_v001.csv
```

These paths illustrate invocation only. They contain no observations and do not
assert that a package or experiment exists.

Before accepting preflight, independently confirm that each normalized table
has the exact `frequency,z_real,z_imag` schema, at least one data row, finite
positive frequency, and finite impedance. Duplicate frequencies and inductive
rows are retained in source order. A preflight result of
`EXPERIMENTAL_EVIDENCE_RECEIVED` establishes controlled receipt only; it is not
`EXPERIMENTAL_INPUT_COMPLETE`, calibration, parameter-transfer permission, or
M03B authorization.
