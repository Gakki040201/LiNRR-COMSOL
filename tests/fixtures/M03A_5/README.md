# M03A.5 synthetic contract fixtures

Everything in this directory is `SYNTHETIC` and exists only to exercise
negative paths and structural contracts. No row is experimental evidence and no
fixture may satisfy an experimental readiness gate.

`negative_cases.csv` is the canonical mutation catalog. The regression test
builds a complete synthetic in-memory intake from the base files, applies
exactly one declared mutation, and verifies the expected blocking state. The raw
and normalized base files provide actual size/hash provenance checks.
