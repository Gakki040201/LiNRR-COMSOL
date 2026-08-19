# M03A.6 external evidence directory contract

## Location and immutability

The package root must be explicitly supplied and must be outside the Git
repository. Path comparison is case-insensitive and uses normalized full paths,
so slash changes, `..`, case changes, and similar-prefix names cannot bypass the
boundary. Every existing segment from the volume root through the repository,
package parent, package root, and accessed child is audited. A reparse point,
junction, symbolic link, or mount point blocks the operation. An external link
that targets the repository is therefore rejected before it can be followed.

The initializer rejects every pre-existing destination, including an empty
directory; it never merges or overwrites. It builds the complete package in a
sibling `.M03A6_STAGING_<GUID>` directory, validates its exact directory/file
manifest, CSV schemas, RFC 4180 syntax, and UTF-8-without-BOM encoding, and only
then performs a same-volume directory rename. On failure it deletes only its
own staging directory (or its own just-renamed destination) and leaves no
destination package. The package initially contains no experimental values.

```text
<package-root>/
  ACQUISITION_PACKAGE_README.md
  FILE_INVENTORY.csv
  HASH_LEDGER.csv
  OPERATOR_CHECKLIST.csv
  raw/eis/
  raw/series_resistance/
  raw/geometry_area/
  raw/direct_conductivity/
  normalized/eis/
  metadata/EIS_METADATA.csv
  metadata/SERIES_RESISTANCE_EVIDENCE.csv
  metadata/GEOMETRY_AREA_EVIDENCE.csv
  metadata/DIRECT_CONDUCTIVITY_EVIDENCE.csv
  hashes/
  reports/
```

`HASH_LEDGER.csv` is an empty schema placeholder. Every freeze invocation needs
an explicit unused version and creates `hashes/HASH_LEDGER_<version>.csv`; an
existing version is never overwritten. Its matching verification report is
`reports/FREEZE_VERIFICATION_<version>.md` and is marked read-only.

## Evidence roles and paths

`FILE_INVENTORY.csv` accepts only these evidence roles and binds each role to
one directory:

| Evidence role | Required directory |
| --- | --- |
| `EIS_RAW` | `raw/eis/` |
| `EIS_NORMALIZED` | `normalized/eis/` |
| `SERIES_RESISTANCE` | `raw/series_resistance/` |
| `GEOMETRY_AREA` | `raw/geometry_area/` |
| `DIRECT_CONDUCTIVITY` | `raw/direct_conductivity/` |

Files under `hashes/` are ledgers only and files under `reports/` are reports
only; neither directory can supply experimental evidence. Unknown roles,
duplicate role/path rows, conflicting roles, directory mismatches, and
unregistered evidence files block preflight.

Every evidence row declares `sample_id`, `cell_id`, `replicate_id`,
`data_origin`, and `status`. A raw and normalized role may never resolve to the
same file. Duplicate basenames in different directories are allowed because
identity is the complete package-relative path. Absolute paths, `..` escape,
symlinks, junctions, and other reparse points are forbidden.

Evidence identity is the canonical full path resolved under the package root,
compared with `StringComparer.OrdinalIgnoreCase`. Its canonical relative form
uses `/`, removes `.`/`..` and repeated separators, and has no leading or
trailing separator. Noncanonical evidence paths are blocked; maps and joins
still use canonical physical identity. Consequently case, separator, `.`/`..`,
alias, and canonical spellings cannot create a second identity. One physical
file may have only one evidence role, and duplicate/conflicting inventory or
ledger aliases are blocked.

EIS binding uses the composite key
`(sample_id, cell_id, replicate_id)`. Each key must have exactly one raw file
and one normalized table. `replicate_id` is also required to be globally unique
within one package, which is stricter than uniqueness within one sample/cell.
Cross-sample, cross-cell, and orphan assembly are blocked.

Series, geometry/area, and direct-conductivity worksheets point to their raw
evidence with `raw_evidence_path`. Required component names are:

- series resistance: `fixture`, `contact`, `membrane`, `other_series`;
- geometry/area: `electrode_spacing`, `out_of_plane_depth`,
  `geometric_electrode_area`, `EIS_area`,
  `current_density_reporting_area`.

Each EIS sample must have exactly one row for each permitted series component,
exactly one row for each permitted geometry/area quantity, and exactly one
direct-conductivity row. Extra or unknown components/quantities and orphan
samples block preflight. Each geometry/area row must state its definition.

## Normalized EIS conversion

The only implemented profile is `EXPLICIT_RFC4180_COLUMN_MAP`. The operator must
explicitly name the source columns for frequency, real impedance, and imaginary
impedance. Unknown or instrument-specific profiles return
`UNSUPPORTED_PROFILE`; the tool never guesses an instrument layout.

Output is UTF-8 without BOM, RFC 4180, and has exactly:

```text
frequency,z_real,z_imag
```

The normalized output, row mapping, and provenance output are three distinct
files and all four full paths (including the raw input) must be pairwise
different after normalization. Existing outputs are never overwritten. Three
independent temporary files are validated before rename; a failed conversion
removes its temporary and already-renamed outputs. Output parents undergo the
same all-segment reparse audit.

Every source data row maps one-to-one to an output row using `source_row` and
`output_row`. Provenance records tool name/version, parser profile, all three
column mappings, input/output/row-map full paths, exact sizes and SHA-256,
source/output row counts, a `DateTimeOffset` ISO 8601 execution time and offset,
and explicit `FALSE` flags for smoothing, interpolation, outlier removal,
sorting, and HFR calculation. This is conversion time, not measurement time.
No first-point selection, row deletion, duplicate removal, inductive-point
removal, smoothing, interpolation, outlier removal, sorting, or HFR calculation
occurs.

## Freeze ledger

Each ledger row contains repository-independent package-relative path, exact
base-10 `Int64` size, lowercase SHA-256, local freeze timestamp with explicit
offset, offset field, and tool version. The freeze tool enumerates actual files
without renaming or converting them, blocks every reparse point, writes a new
versioned ledger, then verifies every recorded size and hash.

Evidence enumeration is ordinal and culture-independent. Ledger versions match
`^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`; case-equivalent versions conflict. The new
ledger and report are written to temporary files after enumeration and renamed
only after verification. No current ledger self-reference exists, and ledger
or report files never acquire an experimental role. All non-ledger/report
package files must match the selected ledger exactly. Raw size, SHA-256, and
`LastWriteTimeUtc` are verified unchanged before and after freezing.

## Worksheet-to-M03A.5 mapping

The four acquisition worksheets use the exact ordered columns of the unchanged
M03A.5 canonical files. Every listed name maps identically; no automatic write
to `config/M03A_5_*` is performed.

| M03A.6 worksheet | M03A.5 canonical file | Identity-mapped columns |
| --- | --- | --- |
| `metadata/EIS_METADATA.csv` | `config/M03A_5_experimental_manifest.csv` | `sample_id`, `cell_id`, `replicate_id`, `operator`, `measurement_datetime`, `instrument`, `instrument_software`, `raw_file_path`, `raw_file_size_bytes`, `raw_file_sha256`, `normalized_table_path`, `normalized_table_size_bytes`, `normalized_table_sha256`, `frequency_unit`, `impedance_unit`, `frequency_min_Hz`, `frequency_max_Hz`, `points_per_decade`, `perturbation_amplitude_V`, `dc_condition`, `dc_bias_V`, `temperature_K`, `electrolyte_composition`, `electrolyte_batch`, `salt_concentration_mol_L`, `water_content`, `water_content_unit`, `water_content_method`, `gas_atmosphere`, `pressure_Pa`, `flow_rate_m3_s`, `stabilization_time_s`, `eis_area_basis`, `eis_area_m2`, `source_notebook_reference`, `hfr_method`, `hfr_ohm`, `hfr_uncertainty_ohm`, `hfr_status`, `hfr_source_reference`, `conductivity_transfer_selection`, `conductivity_transfer_authorization_reference`, `data_origin`, `status`, `notes` |
| `metadata/SERIES_RESISTANCE_EVIDENCE.csv` | `config/M03A_5_resistance_ledger.csv` | `sample_id`, `component_name`, `value_ohm`, `uncertainty_ohm`, `unit`, `method`, `replicate_count`, `temperature_K`, `source_reference`, `raw_evidence_path`, `raw_evidence_size_bytes`, `raw_evidence_sha256`, `data_origin`, `status`, `notes` |
| `metadata/GEOMETRY_AREA_EVIDENCE.csv` | `config/M03A_5_area_measurements.csv` | `sample_id`, `quantity_name`, `value_SI`, `uncertainty_SI`, `unit`, `measurement_method`, `source_reference`, `raw_evidence_path`, `raw_evidence_size_bytes`, `raw_evidence_sha256`, `data_origin`, `status`, `definition`, `notes` |
| `metadata/DIRECT_CONDUCTIVITY_EVIDENCE.csv` | `config/M03A_5_direct_conductivity.csv` | `sample_id`, `conductivity_S_m`, `uncertainty_S_m`, `unit`, `temperature_K`, `method`, `cell_constant`, `cell_constant_unit`, `calibration_standard`, `instrument`, `raw_evidence_path`, `raw_evidence_size_bytes`, `raw_evidence_sha256`, `source_reference`, `data_origin`, `status`, `notes` |

Initial worksheet rows contain only controlled names plus
`data_origin=EXPERIMENTAL` and `status=MISSING`; measurement values, file paths,
hashes, people, instruments, dates, HFR results, and authorizations are blank.

## Pre-intake states

- `ACQUISITION_PACKAGE_READY`: the initialized empty contract exists; no real
  evidence is claimed.
- `EXPERIMENTAL_EVIDENCE_RECEIVED`: evidence, metadata, file mappings, and the
  selected immutable ledger satisfy M03A.6 preflight only.
- `PRE_INTAKE_BLOCKED`: at least one structural, provenance, metadata, path,
  replicate, resistance, area, direct-conductivity, size, or hash requirement
  failed.

The header-only `ACQUISITION_PACKAGE_READY` return is allowed only when all five
evidence directories are empty, no active metadata or inventory row exists,
the ledger has no evidence record, and no inventory row is `MEASURED` or
`PRESENT`. Normalized EIS files are read and checked for UTF-8 without BOM,
RFC 4180, the exact three-column header, at least one row, finite numeric
impedance, and finite positive frequency. Duplicate frequency and inductive
rows remain valid and remain in their original order.

M03A.6 never outputs `EXPERIMENTAL_INPUT_COMPLETE=TRUE`,
`M03B_CANDIDATE=TRUE`, or `M03B_READY=TRUE`. Only the published M03A.5 validator
can decide formal experimental completeness.

## Regression-output isolation

`-OnlyTestId` is a focused debugging mode. It requires a valid test ID and an
explicit output path outside the repository; it can never use or overwrite the
durable regression table. Only a complete, continuous, duplicate-free full run
may atomically replace `results/tables/M03A_6_tool_regression.csv`. Failed,
zero-case, incomplete, or focused runs do not replace the last full result.
