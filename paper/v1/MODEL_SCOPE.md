# Paper V1 model scope

## Frozen contribution

Paper V1 is a verification-first, real-cell transport + ionic + current model of a continuous-flow lithium-mediated nitrogen-reduction reactor. Independently verified flow, conservative species-transport, current-conservation, and Faraday operators are transferred to the accepted real CAD geometry. The frozen evidence supports analysis of flow nonuniformity, N2 availability, effective ionic polarization, current crowding, Li-equivalent Faradaic inventory heterogeneity, and spatial co-limitation.

## Scientific boundary

The model is a continuum diagnostic scaffold, not an experimentally validated kinetic reactor model. It does not add or infer SEI, Li3N, elementary Li-NRR, HER, HOR, Butler-Volmer, heat-transfer, or thermal-feedback physics. It does not predict FE, NH3 kinetic production, full-cell voltage, or real retained metallic-Li thickness. The donor field remains **GENERIC DONOR**; it is not a validated local ethanol-concentration field.

The accepted conductivity is a provisional sensitivity/literature estimate. Effective salt diffusivity and transference number require calibration. Li-equivalent thickness is a Faraday-equivalent numerical upper bound or current-partition sensitivity, not a retained-Li prediction. Co-limitation maps are spatial diagnostics, not rate, selectivity, probability, or mechanistic maps.

## Immutable model identity

- Base main and M10A4 tag target: `a9314f89ee4a79492b88948dff912dc885feb7d6`
- Accepted M10A4 feature commit: `f3b4d24cf82dd0e26b769a99fe5fb061f84b9c3e`
- Final compact M10A4 MPH SHA256: `FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B`
- Accepted M10A3 SHA256: `03612FDB08D993595ABDA41D5873CBBCAA97580DB432ADCD2FBCD2CA06260C00`
- New COMSOL solves in this phase: **0**

## Traceability rule

Every Paper V1 statement must trace as claim -> figure/table -> source artifact -> model stage -> byte hash -> authority class. Unsupported gaps remain explicit limitations.
