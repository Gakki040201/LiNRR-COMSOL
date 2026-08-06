# M10A0.2 Audit Capture Hotfix v3

## Confirmed status from the previous run

The two source STEP solids imported successfully in `Strict` mode. COMSOL reported:

- current collector: 1 domain, 135 boundaries, 372 edges, 245 vertices;
- electrolyte chamber: 1 domain, 93 boundaries, 260 edges, 172 vertices;
- desired generated MPH existed;
- batch exit code was 0.

The remaining failure was orchestration-only: Java `System.out` records were visible in the parent PowerShell console but were not written into COMSOL's `-batchlog`, so the script found zero audit records.

## Corrections

- captures native COMSOL console output into `M10A0_2_comsol_console_output.txt`;
- extracts `CAD_AUDIT_CSV|...` records from captured console output;
- passes `-outputfile` to prevent COMSOL's default `_Model.mph` save path;
- moves any pre-existing or unexpected legacy `_Model.mph` into the run directory;
- scans both batch log and console transcript for fatal patterns.

No CAD source, import topology, dimensions, or scientific assumptions were changed.
