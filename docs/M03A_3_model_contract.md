# M03A.3 prescribed-current stoichiometric coupling contract

M03A.3 is a synthetic numerical verification model. It combines the frozen
two-dimensional channel geometry, incompressible Laminar Flow, conservative
N2/NH3 Transport of Diluted Species, Primary Current Distribution, and a
prescribed one-way current-to-species stoichiometric mapping. It is not an
experimental calibration, electrode-kinetics model, Faradaic-efficiency
prediction, microscopic-mechanism model, or authorization for M03B.

## Frozen physical layers

- Geometry and named selections are inherited from the frozen M02.2 MPH; no
  geometry is redrawn and durable code uses no raw boundary or domain numbers.
- Flow retains the M01.2 incompressible Laminar Flow definition, fully developed
  inlet/outlet interpretation, frozen density and viscosity, and mapped mesh.
- Transport retains conservative convection, Danckwerts inflow, Outflow,
  streamline and crosswind consistent stabilization, and quadratic
  concentration shape functions. The physical flux is
  `N_i = u*c_i - D_i*grad(c_i)` and the equation is `div(N_i) = R_i`.
- Electrical conduction uses COMSOL 6.4 `PrimaryCurrentDistribution`, with
  `i_l = -kappa_M033*grad(phi_l)` and `div(i_l) = 0`. No Secondary or Tertiary
  Current Distribution and no electrode-reaction feature is present.

All geometry, fluid, transport, conductivity, applied potential, current and FE
inputs are provisional synthetic numerical inputs unless explicitly identified
as a physical constant. `kappa_M033` is synthetic conductivity, not measured
electrolyte conductivity. `FE_prescribed` is an input, not a prediction.

## 2D/3D area and electrical boundaries

The 2D electrode boundary length is `Lcell`. The out-of-plane thickness is
`Wcell`; therefore the three-dimensional equivalent current-carrying area is
`Aeq_M033 = Lcell*Wcell`. A 2D line length is never reported as square-metre
area. The upper named electrode boundary is the prescribed-current boundary,
with `j_app_M033 = kappa_M033*DeltaPhi_M033/Hcell`. The lower named electrode
boundary fixes electrolyte potential to 0 V. Left and right boundaries are
electrically insulating.

The raw electrical quantity is the signed outward-normal current density

`j_signed_outward_M033 = -kappa_M033*grad(phi_l) dot n`.

For the lower cathode, the verified sign is positive. Only after that sign audit
is it assigned to `j_cathodic_positive`; no `abs()` is used. The signed boundary
current and the derived positive cathodic magnitude are both exported.

## Prescribed one-way stoichiometric mapping

The mapping is `N2 + 6e- -> 2NH3`. For positive cathodic-current magnitude
`I_cath`, N2 consumption is `FE_prescribed*I_cath/(6*F)` and NH3 generation is
`FE_prescribed*I_cath/(3*F)`.

COMSOL General Inward Flux is positive into the liquid. The cathode N2 inward
flux is negative and the NH3 inward flux is positive. Conservation is checked
by three independent paths: electrical boundary-current integration, species
boundary total-flux integration, and inlet/outlet total-flux balances. The
nitrogen-atom balance never substitutes for either single-species balance.

The coupling is one-way: constant conductivity means flow and concentrations
cannot alter the ohmic current. Changing electrical input cannot alter the flow
solution. There is no reaction-rate, exchange-current, concentration-dependent,
or overpotential-dependent law.

## Applicability and failure retention

The current-flow map uses the full 25-point Cartesian product specified in
`config/M03A_3_current_flow_sweep.csv`. Negative concentrations are never
clipped. No failed, warning, nonconservative, or outside-applicability row is
deleted. `Theta > 1` means only that prescribed stoichiometric demand exceeds
the inlet N2 capacity of this one-way model; it is not a physical performance
or limiting-current prediction.

RUN_STATE = SYNTHETIC_SMOKE_TEST

CALIBRATION_MODE = PROVISIONAL

COUPLING_MODE = PRESCRIBED_CURRENT_ONE_WAY

M03B_READY = FALSE
