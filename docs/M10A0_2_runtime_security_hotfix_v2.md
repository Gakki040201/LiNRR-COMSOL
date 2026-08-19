# M10A0.2 Runtime Security Hotfix v2

## Confirmed root cause

COMSOL started the class, but its Runtime security policy rejected environment-variable access for `M10A0_2_CC_STEP`.

The failure occurred at time 0, before either STEP file reached the CAD kernel. It is therefore not evidence of broken CAD, a missing CAD license, or a strict geometry-import failure.

## Corrected transport of runtime values

PowerShell now verifies both STEP hashes, generates and compiles a transparent run-specific Java class containing the paths and metadata, and supplies its directory through `comsolbatch -classpathadd`.

The durable builder loads only those constants by reflection. It does not read environment variables or runtime properties files.

## Audit handling

The Java builder emits ASCII-prefixed CSV records into the COMSOL batch log. PowerShell extracts them into `M10A0_2_comsol_cad_audit.csv`.

## Files to overwrite

- `src/java/LiNRR_M10A0_2_RealCAD_Intake.java`
- `scripts/windows/24_build_M10A0_2_real_cad_intake.ps1`

Run `Strict` again. `AutoRepair` remains a controlled fallback only after a geometry-specific strict-import failure.
