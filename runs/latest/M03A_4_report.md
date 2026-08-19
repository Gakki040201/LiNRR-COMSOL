# M03A.4 full synthetic coupled parameter-transfer dry-run

- Baseline: 9a20c3486d7c832ef23f87e7fd1b1d6e1f7674f8
- Run directory: F:\LiNRR_COMSOL\worktrees\LiNRR_M03A_4\runs\20260719_114249_M03A_4
- Canonical resolved-input SHA-256: c76b81c8d375653c6713e5c39ece809c575af6a660cce9154e98ef0af475f538
- Derived MPH SHA-256: 556e6b3af11c225c67668cd99a3a92c2be3dcd10f5e5a0488efb548d359793b7
- Frozen M03A.3 MPH SHA-256 before/after: f0242adc3493af1933b695253275a1d25d7de03b8c49e99276a470c304fea211 / f0242adc3493af1933b695253275a1d25d7de03b8c49e99276a470c304fea211
- Parser module 40 preflight regressions and C037 real integration regression: PASS.
- Parent-provenance digest mutation missing-parent conductivity-uncertainty fixed-seed Monte Carlo and resolved-source consistency gates: PASS.
- Seven nonidentity transfer cases Java compile geometry rebuild named-selection audit mesh rebuild fresh spf+tds+cd solves actual current integration actual TDS cathode integration inlet/outlet balances nitrogen closure consumer response derivative save and independent reload: PASS.
- Runtime process exit and completion/reload markers: PASS; fatal count 0; warnings classified and retained.

The resolved synthetic EIS chain is raw EIS to replicate estimates to accepted consensus to de-embedding to conductivity. The formal combined nonidentity derivative uses a current-run full coupled solution. Algebraic imposed stoichiometry is reported only as ALGEBRAIC_STOICHIOMETRIC_MAPPING; formal Faraday closure uses actual TDS cathode flux and inlet/outlet balances.

The earlier 20260718_144705 and 20260718_145057 LU memory failures are preserved failed attempts and are not part of the final successful-run acceptance. Experimental calibration remains incomplete. There are no electrode kinetics and no M03B authorization.

RUN_STATE = SYNTHETIC_SMOKE_TEST
CALIBRATION_MODE = PROVISIONAL
PARAMETER_TRANSFER_MODE = SYNTHETIC_DRY_RUN
EXPERIMENTAL_INPUT_COMPLETE = FALSE
M03B_READY = FALSE