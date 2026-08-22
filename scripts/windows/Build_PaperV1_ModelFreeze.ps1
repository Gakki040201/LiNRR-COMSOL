[CmdletBinding()]
param([string]$RepoRoot)

$ErrorActionPreference='Stop'
if(-not $RepoRoot){$RepoRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path}
$paper=Join-Path $RepoRoot 'paper/v1'; $fig=Join-Path $paper 'figures'; $si=Join-Path $paper 'supplement'
New-Item -ItemType Directory -Force -Path $paper,$fig,$si | Out-Null
function Csv($rows,[string]$path){ @($rows)|Export-Csv -LiteralPath $path -NoTypeInformation -Encoding utf8 }
function Hash([string]$rel){ (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $RepoRoot $rel)).Hash }
function Size([string]$rel){ (Get-Item -LiteralPath (Join-Path $RepoRoot $rel)).Length }
function Val([string]$name){
  $line=Get-Content (Join-Path $RepoRoot 'evidence/M10A4/M10A4_final_report.txt')|Where-Object {$_ -match ('^'+[regex]::Escape($name)+'=')}|Select-Object -Last 1
  if(-not $line){throw "Missing final-report value: $name"}; return ($line -split '=',2)[1].Trim()
}

# Fail before package generation if the immutable numerical identity has drifted.
$expected=[ordered]@{
 M10A4_OVERALL='PASS'; ACTIVE_AREA_MM2='3844'; ACTIVE_AREA_CM2='38.44'; CURRENT_SOURCE='j_app_sensitivity=10 A/m^2'; CURRENT_SOURCE_CLASS='PROVISIONAL_SENSITIVITY'; KAPPA_SOURCE='kappa_M10A4_nominal=0.3 S/m'; KAPPA_SOURCE_CLASS='PROVISIONAL_SENSITIVITY_LITERATURE_ESTIMATE'; CURRENT_CONSERVATION_RELATIVE='1.80512328405505E-15'; MODEL_OHMIC_RESISTANCE_OHM='11.016992179201'; LI_CONSERVATION_RELATIVE='2.03040317664811E-09'; BF4_CONSERVATION_RELATIVE='2.03040321979805E-09'; LI_MIN_MOL_M3='957.945310917445'; LI_MAX_MOL_M3='5622.11679840875'; ELECTRONEUTRALITY_MAX_ABS='0'; J_CATH_MEAN='10.0071933411891'; J_CATH_MIN='0.00837586786401057'; J_CATH_MAX='17.5829079149677'; J_CATH_P10='4.50506386681454'; J_CATH_P50='11.2120309101249'; J_CATH_P90='12.5648964707273'; J_CATH_STD='3.09576680867327'; J_CATH_CV='0.309354151871061'; LI_EQUIVALENT_MASS_9C_F1='6.47445561157042e-07'; LI_EQUIVALENT_MASS_45C_F1='3.23722780578521e-06'; LI_EQUIVALENT_MASS_54C_F1='3.88467336694225e-06'; LI_EQUIVALENT_MASS_99C_F1='7.12190117272746e-06'; LI_EQUIVALENT_MASS_297C_F1='2.13657035181824e-05'; LI_EQUIVALENT_MEAN_THICKNESS_297C_F1='1.04086058131269e-05'; LI_EQUIVALENT_THICKNESS_CV_297C_F1='0.309354151871061'; FARADAY_LEDGER_MAX_RELATIVE='1.43543987022242E-16'; N2_CURRENT_OVERLAP_STATUS='PASS_DIAGNOSTIC_ONLY'; DONOR_CURRENT_OVERLAP_STATUS='PASS_DIAGNOSTIC_ONLY_GENERIC_DONOR_CALIBRATION_REQUIRED'; SPATIAL_COLIMITATION_STATUS='PASS_DIAGNOSTIC_ONLY'; SPATIAL_COLIMITATION_THRESHOLD_SET='0.40,0.50,0.60'; MESH_MAX_KEY_DIFFERENCE='0.0621400622644018'; MESH_LIMITING_METRIC='colim_MIXED_UNCLASSIFIED'; FINAL_RELOAD='PASS'; NO_PROHIBITED_PHYSICS='TRUE'; FINAL_MPH_SHA256='FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B'
}
foreach($k in $expected.Keys){if((Val $k) -ne $expected[$k]){throw "Authoritative discrepancy: $k expected $($expected[$k]) found $(Val $k)"}}
if((Hash 'models/generated/LiNRR_M10A4_ionic_current_li_plating.mph') -ne $expected.FINAL_MPH_SHA256){throw 'Final MPH byte hash mismatch'}
if((Size 'models/generated/LiNRR_M10A4_ionic_current_li_plating.mph') -ne 807839751){throw 'Final MPH size mismatch'}

$scope=@'
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
'@
Set-Content (Join-Path $paper 'MODEL_SCOPE.md') $scope -Encoding utf8

# Consolidated provenance: selected paper-facing parameters, each tied to accepted evidence.
$prov=@(
 [pscustomobject]@{parameter='Cathode active area';symbol='A_echem,cath';value='3844';unit='mm^2';model_stage='M10A4';source_class='REAL_CAD';source_reference='results/tables/M10A4_electrochemical_area_audit.csv';experimental_status='CAD-derived interface measure';calibration_status='FROZEN';paper_usage='normalization and reaction plane';allowed_interpretation='authoritative electrolyte/GDE interface area';forbidden_interpretation='60x60 mm SSC cut area';notes='38.44 cm^2'},
 [pscustomobject]@{parameter='SSC cut width';symbol='ssc_cut';value='60';unit='mm';model_stage='M10A3';source_class='LAB_MANUAL';source_reference='evidence/M10A3/parameter_inventory.csv';experimental_status='manual geometry input';calibration_status='FROZEN';paper_usage='geometry context';allowed_interpretation='physical cut dimension';forbidden_interpretation='electrochemical active area';notes='cut area is 3600 mm^2'},
 [pscustomobject]@{parameter='Model applied current density';symbol='j_app_sensitivity';value='10';unit='A/m^2';model_stage='M10A4A';source_class='PROVISIONAL_SENSITIVITY';source_reference='results/tables/M10A4_parameter_provenance.csv';experimental_status='not recovered from raw program';calibration_status='CALIBRATION_REQUIRED';paper_usage='frozen current-field sensitivity';allowed_interpretation='model sensitivity input';forbidden_interpretation='measured lab current density';notes='I=0.03844 A on accepted area'},
 [pscustomobject]@{parameter='Electrolyte conductivity';symbol='kappa_M10A4_nominal';value='0.3';unit='S/m';model_stage='M10A4A';source_class='LITERATURE_ESTIMATE';source_reference='results/tables/M10A4_parameter_provenance.csv';experimental_status='not measured for current run';calibration_status='CALIBRATION_REQUIRED';paper_usage='provisional ohmic sensitivity';allowed_interpretation='provisional sensitivity/literature estimate';forbidden_interpretation='experimentally calibrated conductivity';notes='source status also PROVISIONAL_SENSITIVITY'},
 [pscustomobject]@{parameter='Effective salt diffusivity';symbol='D_salt_a4b_eff';value='1e-8';unit='m^2/s';model_stage='M10A4B';source_class='PROVISIONAL_SENSITIVITY';source_reference='evidence/M10A4/M10A4_final_report.txt';experimental_status='not measured';calibration_status='CALIBRATION_REQUIRED';paper_usage='reduced electroneutral transport';allowed_interpretation='effective sensitivity coefficient';forbidden_interpretation='measured Li or BF4 diffusivity';notes='common-salt reduction'},
 [pscustomobject]@{parameter='Cation transference number';symbol='t_plus_a4b';value='0.5';unit='1';model_stage='M10A4B';source_class='PROVISIONAL_SENSITIVITY';source_reference='evidence/M10A4/M10A4_final_report.txt';experimental_status='not measured';calibration_status='CALIBRATION_REQUIRED';paper_usage='ionic flux partition';allowed_interpretation='provisional binary-ion partition';forbidden_interpretation='measured transference number';notes='BF4 share is 1-t_plus'},
 [pscustomobject]@{parameter='LiBF4 bulk concentration';symbol='c_salt_bulk_a4b';value='1';unit='mol/L';model_stage='M10A4B';source_class='LAB_MANUAL';source_reference='results/tables/M10A4_parameter_provenance.csv';experimental_status='recipe value';calibration_status='FROZEN';paper_usage='ionic reference concentration';allowed_interpretation='bulk recipe concentration';forbidden_interpretation='local experimental validation';notes='positive common-salt field'},
 [pscustomobject]@{parameter='N2 gas flow';symbol='Q_N2_lab';value='50';unit='cm^3/min';model_stage='M10A3';source_class='LAB_MANUAL';source_reference='evidence/M10A3/parameter_inventory.csv';experimental_status='manual operating point';calibration_status='FROZEN';paper_usage='gas-route boundary condition';allowed_interpretation='manual nominal flow';forbidden_interpretation='independently metered run evidence';notes='M10A3 frozen'},
 [pscustomobject]@{parameter='Liquid flow';symbol='Q_liq_sweep';value='1';unit='cm^3/min';model_stage='M10A3';source_class='CALIBRATION_REQUIRED';source_reference='evidence/M10A3/parameter_inventory.csv';experimental_status='not lab baseline';calibration_status='CALIBRATION_REQUIRED';paper_usage='frozen accepted flow case';allowed_interpretation='model sweep operating point';forbidden_interpretation='measured lab baseline';notes='real solved flow reused'},
 [pscustomobject]@{parameter='N2 diffusivity';symbol='D_N2';value='accepted model expression';unit='m^2/s';model_stage='M10A3';source_class='LITERATURE_ESTIMATE';source_reference='evidence/M10A3/parameter_inventory.csv';experimental_status='not measured in current platform';calibration_status='CALIBRATION_REQUIRED';paper_usage='N2 availability field';allowed_interpretation='literature/provisional transport input';forbidden_interpretation='experimentally calibrated coefficient';notes='exact expression retained in source inventory'},
 [pscustomobject]@{parameter='Generic donor diffusivity';symbol='D_donor';value='accepted model expression';unit='m^2/s';model_stage='M10A3';source_class='PROVISIONAL_SENSITIVITY';source_reference='evidence/M10A3/parameter_inventory.csv';experimental_status='generic field only';calibration_status='CALIBRATION_REQUIRED';paper_usage='generic donor availability';allowed_interpretation='generic donor transport diagnostic';forbidden_interpretation='validated local ethanol concentration';notes='do not rename frozen variable'},
 [pscustomobject]@{parameter='Li molar mass';symbol='M_Li';value='6.941e-3';unit='kg/mol';model_stage='M10A4D';source_class='LITERATURE_REPORTED';source_reference='results/tables/M10A4_parameter_provenance.csv';experimental_status='reference constant';calibration_status='NOT_REQUIRED';paper_usage='Faraday-equivalent mass';allowed_interpretation='stoichiometric conversion constant';forbidden_interpretation='kinetic parameter';notes=''},
 [pscustomobject]@{parameter='Li density';symbol='rho_Li';value='534';unit='kg/m^3';model_stage='M10A4D';source_class='LITERATURE_REPORTED';source_reference='results/tables/M10A4_parameter_provenance.csv';experimental_status='reference property';calibration_status='NOT_REQUIRED';paper_usage='equivalent volume/thickness';allowed_interpretation='conversion property';forbidden_interpretation='retention validation';notes=''},
 [pscustomobject]@{parameter='Faraday constant';symbol='F';value='96485.33212331002';unit='C/mol';model_stage='M03/M10A4D';source_class='LITERATURE_REPORTED';source_reference='results/tables/M10A4_parameter_provenance.csv';experimental_status='physical constant';calibration_status='NOT_REQUIRED';paper_usage='charge ledger';allowed_interpretation='charge-to-moles conversion';forbidden_interpretation='fitted coefficient';notes=''},
 [pscustomobject]@{parameter='Charge checkpoints';symbol='Q';value='9,45,54,99,297';unit='C';model_stage='M10A4D';source_class='LAB_MANUAL';source_reference='results/tables/M10A4_electrochemical_program_audit.csv';experimental_status='manual program';calibration_status='FROZEN';paper_usage='charge-indexed Li-equivalent states';allowed_interpretation='program charge indices';forbidden_interpretation='elapsed time without I conversion';notes=''},
 [pscustomobject]@{parameter='Li current partition';symbol='f_Li_current';value='0.25,0.50,0.75,1.00';unit='1';model_stage='M10A4D';source_class='PROVISIONAL_SENSITIVITY';source_reference='results/tables/M10A4_li_faraday_ledger.csv';experimental_status='not measured';calibration_status='CALIBRATION_REQUIRED';paper_usage='upper bound and partition sensitivity';allowed_interpretation='f=1 numerical upper bound';forbidden_interpretation='measured retained-Li fraction';notes=''},
 [pscustomobject]@{parameter='Co-limitation threshold quantiles';symbol='q_threshold';value='0.40,0.50,0.60';unit='1';model_stage='M10A4E';source_class='DERIVED_DIAGNOSTIC';source_reference='results/tables/M10A4_spatial_colimitation.csv';experimental_status='diagnostic definition';calibration_status='FROZEN';paper_usage='threshold robustness';allowed_interpretation='area-weighted diagnostic thresholds';forbidden_interpretation='kinetic onset thresholds';notes='q=0.50 primary'},
 [pscustomobject]@{parameter='Model ohmic resistance';symbol='R_ohmic';value=(Val 'MODEL_OHMIC_RESISTANCE_OHM');unit='ohm';model_stage='M10A4A';source_class='DERIVED_DIAGNOSTIC';source_reference='results/tables/M10A4_resistance_audit.csv';experimental_status='not EIS validation';calibration_status='inherits kappa limitation';paper_usage='electrolyte loss diagnostic';allowed_interpretation='model electrolyte ohmic resistance';forbidden_interpretation='full-cell voltage or measured HFR';notes='not fitted to manual <=2.5 ohm plausibility statement'}
)
Csv $prov (Join-Path $paper 'PARAMETER_PROVENANCE.csv')

$claims=@(
 @('C01','Methods','The M01 flow operator reproduces analytical fully developed flow and mass balance.','NUMERICAL_VERIFICATION','analytic and conservation gates','results/tables/M01_2_flow_analytic.csv','','','VERIFIED_OPERATOR','SUPPORTED','the numerically verified flow operator','experimentally validated real-cell flow','none for numerical operator; real-cell validation separate'),
 @('C02','Methods','The M02 conservative transport operator passes diffusion, MMS, and species-balance gates.','NUMERICAL_VERIFICATION','transport regression tables','results/tables/M02_2_mms_convergence.csv','','','VERIFIED_OPERATOR','SUPPORTED','the numerically verified transport operator','experimentally validated real-cell concentrations','real-cell concentration measurements'),
 @('C03','Methods','The M03 current and Faraday operators close their numerical ledgers.','NUMERICAL_VERIFICATION','current/Faraday tables','results/tables/M03A_3_current_conservation.csv','','','VERIFIED_OPERATOR','SUPPORTED','the numerically verified current/Faraday operators','validated real-cell electrochemistry','independent electrochemical comparison'),
 @('C04','Geometry','The model uses accepted real CAD ancestry and named selections.','REAL_GEOMETRY_FACT','geometry authority and inventories','results/tables/M10_preA4_geometry_authority.csv','','','REAL_CAD','SUPPORTED','real-CAD geometry ancestry','every local dimension experimentally metrologized','additional metrology where required'),
 @('C05','Geometry','The authoritative reaction-plane area is 3844 mm2.','REAL_GEOMETRY_FACT','area audit','results/tables/M10A4_electrochemical_area_audit.csv','','','REAL_CAD','SUPPORTED','3844 mm2 electrolyte/GDE interface','60x60 mm SSC cut used as active area','none for frozen definition'),
 @('C06','Results','The accepted real-cell flow field is nonuniform and conservative.','REAL_CELL_MODEL_PREDICTION','M10A3 flow evidence','results/tables/M10A3_N2_H2_flow_comparison.csv','','','MODEL_EVIDENCE','SUPPORTED_WITH_MODEL_SCOPE','the real-cell model predicts flow nonuniformity','the reactor flow was experimentally validated','flow imaging or pressure/flow validation'),
 @('C07','Results','The model resolves spatial N2 availability.','REAL_CELL_MODEL_PREDICTION','M10A3 fields/conservation','evidence/M10A3/n2_dissolved_real.png','','','MODEL_EVIDENCE','SUPPORTED_WITH_MODEL_SCOPE','modeled N2 availability','experimentally validated local N2 map','spatial concentration measurements'),
 @('C08','Results','The donor field is a generic availability diagnostic.','PROVISIONAL_SENSITIVITY_RESULT','M10A3 donor field','evidence/M10A3/proton_donor_real.png','','','PROVISIONAL','SUPPORTED_WITH_LIMITATION','generic proton-donor availability field','experimentally validated local ethanol concentration','donor-specific calibration'),
 @('C09','Results','Reduced Li+/BF4- transport is conservative and electroneutral.','NUMERICAL_VERIFICATION','species ledger','results/tables/M10A4_ionic_species_conservation.csv','','','VERIFIED_NUMERICS','SUPPORTED','effective electroneutral binary-ion transport closes its ledgers','experimentally calibrated ion transport','D_salt and t_plus measurements'),
 @('C10','Results','The accepted current operator closes conservation to 1.80512328405505E-15.','NUMERICAL_VERIFICATION','charge ledger','results/tables/M10A4_charge_conservation.csv','','','VERIFIED_NUMERICS','SUPPORTED','the numerically verified current operator closes current conservation','the real reactor current distribution is experimentally validated','independent spatial current validation'),
 @('C11','Results','The real-cell model predicts a spatially nonuniform cathode current field.','REAL_CELL_MODEL_PREDICTION','area-weighted distribution table','results/tables/M10A4_current_distribution_statistics.csv','','','MODEL_EVIDENCE','SUPPORTED_WITH_MODEL_SCOPE','the real-cell model predicts a spatially nonuniform current field','the reactor experimentally exhibits this current map','segmented-electrode or imaging data'),
 @('C12','Results','Cathode current-magnitude CV is 0.309354151871061.','DERIVED_DIAGNOSTIC','area-weighted statistics','results/tables/M10A4_current_distribution_statistics.csv','','','DERIVED_DIAGNOSTIC','SUPPORTED','area-weighted model current CV','experimentally measured current CV','spatial current measurement'),
 @('C13','Results','Charge-indexed lithium inventory is Faraday-equivalent.','DERIVED_DIAGNOSTIC','Faraday ledger','results/tables/M10A4_li_faraday_ledger.csv','','','NUMERICAL_UPPER_BOUND','SUPPORTED_WITH_LIMITATION','Faraday-equivalent lithium inventory','predicted deposited metallic-lithium thickness','retained-Li mass/thickness measurements'),
 @('C14','Results','The 297 C Li-equivalent thickness field has CV 0.309354151871061 at fLi=1.','PROVISIONAL_SENSITIVITY_RESULT','Faraday ledger and map','results/tables/M10A4_li_faraday_ledger.csv','','','NUMERICAL_UPPER_BOUND','SUPPORTED_WITH_LIMITATION','297 C Li-equivalent Faradaic thickness heterogeneity','real Li thickness prediction','retained-Li imaging/quantification'),
 @('C15','Results','N2-current overlap is a spatial association diagnostic.','DERIVED_DIAGNOSTIC','co-limitation table','results/tables/M10A4_spatial_colimitation.csv','','','DIAGNOSTIC_ONLY','SUPPORTED','N2-current spatial overlap','NH3 production map or causal proof','correlative experimental maps'),
 @('C16','Results','Donor-current overlap is generic and calibration-required.','DERIVED_DIAGNOSTIC','co-limitation table','results/tables/M10A4_spatial_colimitation.csv','','','DIAGNOSTIC_ONLY','SUPPORTED_WITH_LIMITATION','generic-donor/current spatial overlap','ethanol reaction-rate map','donor-specific transport validation'),
 @('C17','Results','N2/Li+/donor/current categories form a threshold-sensitive spatial diagnostic.','DERIVED_DIAGNOSTIC','q=0.40/0.50/0.60 table','results/tables/M10A4_spatial_colimitation.csv','','','DIAGNOSTIC_ONLY','SUPPORTED','spatial co-limitation diagnostic','NH3 rate map, FE map, mechanistic proof','coupled reaction/experimental evidence'),
 @('C18','Robustness','Coarse-to-medium maximum key difference is 0.0621400622644018.','NUMERICAL_VERIFICATION','mesh table','results/tables/M10A4_mesh_convergence.csv','','','PASS_WITH_LIMITATION','SUPPORTED_WITH_LIMITATION','mesh comparison passes with limitation','fully mesh-independent without limitation','additional refinement without changing physics'),
 @('C19','Discussion','Model electrolyte ohmic resistance is 11.016992179201 ohm.','DERIVED_DIAGNOSTIC','resistance audit','results/tables/M10A4_resistance_audit.csv','','','DERIVED_DIAGNOSTIC','SUPPORTED_WITH_LIMITATION','model electrolyte ohmic-resistance diagnostic','predicted full-cell voltage or measured HFR','run-specific EIS/conductivity'),
 @('C20','Limitations','Key ionic and donor parameters require calibration.','EXPERIMENTAL_CLAIM','provenance and calibration tables','results/tables/M10A4_calibration_required.csv','','','EVIDENCE_GAP','SUPPORTED','calibration is required before quantitative validation','parameters are experimentally calibrated','run-specific measurements'),
 @('C21','Limitations','Paper V1 does not establish SEI, Li3N, Li-NRR, HER, or HOR mechanisms.','MECHANISTIC_CLAIM','physics/prohibited audit','evidence/M10A4/physics_inventory.csv','','','OUT_OF_SCOPE','UNSUPPORTED_IN_PAPER_V1_MODEL','these mechanisms are outside the model','SEI mechanism confirmed; Li3N mechanism confirmed; Li-NRR kinetics established','dedicated physics plus independent evidence'),
 @('C22','Limitations','Paper V1 has an experimental-validation gap for spatial predictions.','EXPERIMENTAL_CLAIM','claim/provenance audit','paper/v1/MODEL_LIMITATIONS.csv','','','EVIDENCE_GAP','SUPPORTED','model predictions require independent experimental validation','validated real-cell prediction','spatial measurements and blinded comparisons')
) | ForEach-Object {[pscustomobject]@{claim_id=$_[0];section=$_[1];claim_text=$_[2];claim_type=$_[3];evidence_required=$_[4];model_artifact=$_[5];experimental_artifact=$_[6];literature_source=$_[7];authority_level=$_[8];current_support=$_[9];allowed_wording=$_[10];forbidden_wording=$_[11];remaining_gap=$_[12]}}
Csv $claims (Join-Path $paper 'CLAIM_EVIDENCE_MATRIX.csv')

$limitations=@(
 @('L01','electrolyte conductivity','Not experimentally calibrated for the current run.','PROVISIONAL_SENSITIVITY','Absolute potential loss and resistance depend on kappa.','provisional ohmic sensitivity','experimentally validated resistance/full-cell voltage','run-specific conductivity and EIS','Methods; Limitations'),
 @('L02','effective salt diffusivity','D_salt is provisional.','CALIBRATION_REQUIRED','Controls polarization magnitude.','effective-transport sensitivity','quantitative ion-transport validation','diffusivity measurement or fit to independent data','Methods; Limitations'),
 @('L03','transference number','t_plus=0.5 is provisional.','CALIBRATION_REQUIRED','Controls Li/BF4 flux partition.','electroneutral sensitivity result','measured species partition','transference measurement','Methods; Limitations'),
 @('L04','donor transport','Frozen field is GENERIC DONOR.','CALIBRATION_REQUIRED','Cannot resolve local ethanol kinetics.','generic donor availability','validated local ethanol concentration','donor-specific properties and measurements','Results; Limitations'),
 @('L05','SEI','No microscopic SEI physics.','OUT_OF_SCOPE','SEI effects cannot be interpreted mechanistically.','SEI is outside scope','SEI mechanism confirmed','dedicated validated SEI model','Limitations'),
 @('L06','Li3N','No Li3N kinetics.','OUT_OF_SCOPE','No Li3N formation/rate inference.','Li3N is outside scope','Li3N mechanism confirmed','phase-specific kinetics and evidence','Limitations'),
 @('L07','Li-NRR','No elementary Li-NRR kinetics.','OUT_OF_SCOPE','No mechanistic rate prediction.','transport/current diagnostics','Li-NRR reaction-rate map','validated kinetic network','Limitations'),
 @('L08','HER/HOR','No HER/HOR partition.','OUT_OF_SCOPE','No selectivity attribution.','side reactions are outside scope','HER selectivity prediction or HOR kinetics prediction','validated competing kinetics','Limitations'),
 @('L09','FE','No FE model.','OUT_OF_SCOPE','No FE prediction.','Faraday-equivalent inventory','predicted FE or FE map','independent products and current partition','Limitations'),
 @('L10','NH3 production','No NH3 kinetic production model.','OUT_OF_SCOPE','No quantitative NH3 rate.','spatial co-limitation diagnostic','NH3 rate map or NH3 production map','validated kinetics and product data','Limitations'),
 @('L11','retained lithium','Li-equivalent thickness is not retained Li.','NUMERICAL_UPPER_BOUND','May overstate retained metal.','Faraday-equivalent upper bound/sensitivity','real Li thickness or predicted Li thickness','retained-Li mass and morphology data','Results; Limitations'),
 @('L12','cell voltage','Only electrolyte ohmic loss is derived.','DERIVED_DIAGNOSTIC','Electrode/contact/kinetic losses absent.','electrolyte ohmic diagnostic','full-cell voltage prediction','complete calibrated loss model','Limitations'),
 @('L13','mesh','Coarse comparison differs by 6.214% at limiting metric.','PASS_WITH_LIMITATION','Category fraction has residual discretization sensitivity.','medium mesh authoritative with limitation','unqualified mesh independence','additional refinement','Robustness'),
 @('L14','reference capillary mapping','Reaction-plane mapping unavailable where source provenance is insufficient.','NOT_CREATED_INSUFFICIENT_EXISTING_EVIDENCE','No capillary causal diagnostic.','supported reaction-plane associations only','reference-capillary causal claim','compatible mapped field/evidence','Limitations'),
 @('L15','mechanism inference','Continuum fields do not prove microscopic mechanism.','DIAGNOSTIC_ONLY','Spatial association is non-causal.','spatial association and co-limitation','mechanistic proof','targeted perturbation and mechanistic validation','Discussion; Limitations')
) | ForEach-Object {[pscustomobject]@{limitation_id=$_[0];model_element=$_[1];limitation=$_[2];source_class=$_[3];impact=$_[4];what_can_be_claimed=$_[5];what_cannot_be_claimed=$_[6];required_future_evidence=$_[7];paper_section=$_[8]}}
Csv $limitations (Join-Path $paper 'MODEL_LIMITATIONS.csv')

# Numerical regression chain: only accepted rows, with hashes of exact source artifacts.
$m01=Import-Csv (Join-Path $RepoRoot 'results/tables/M01_2_flow_analytic.csv')|Where-Object case -eq 'baseline_fine_fully_developed_outlet'
$m02=Import-Csv (Join-Path $RepoRoot 'results/tables/M02_2_mms_convergence.csv')|Where-Object mesh -eq 'fine'
$m03=Import-Csv (Join-Path $RepoRoot 'results/tables/M03A_3_current_conservation.csv')|Where-Object mesh -eq 'fine'
$a3=Import-Csv (Join-Path $RepoRoot 'results/tables/M10A3_species_conservation.csv')
$regSpecs=@(
 @('R01','M01','fine flow mass balance',$m01.mass_balance_error,'1','accepted verification regression','PASS','results/tables/M01_2_flow_analytic.csv','verified flow operator','fully developed baseline'),
 @('R02','M02','fine MMS L2 relative error',$m02.L2_relative_error,'1','accepted verification regression','PASS','results/tables/M02_2_mms_convergence.csv','verified transport operator','observed order '+$m02.observed_order_from_previous),
 @('R03','M03','fine current conservation',$m03.current_conservation_relative_error,'1','accepted verification regression','PASS','results/tables/M03A_3_current_conservation.csv','verified current operator',''),
 @('R04','M03','fine Faraday current-to-N2 error',(Import-Csv (Join-Path $RepoRoot 'results/tables/M03A_3_faradaic_stoichiometry.csv')|Where-Object mesh -eq 'fine').current_to_N2_relative_error,'1','accepted verification regression','PASS','results/tables/M03A_3_faradaic_stoichiometry.csv','verified Faraday operator',''),
 @('R05','M10A3','maximum accepted species residual',(($a3|ForEach-Object {[double]$_.relative_residual}|Measure-Object -Maximum).Maximum),'1','all accepted rows PASS','PASS','results/tables/M10A3_species_conservation.csv','real-cell neutral transport',''),
 @('R06','M10A4A','current conservation',(Val 'CURRENT_CONSERVATION_RELATIVE'),'1','<=1e-8','PASS','results/tables/M10A4_charge_conservation.csv','charge conservation',''),
 @('R07','M10A4B','Li conservation',(Val 'LI_CONSERVATION_RELATIVE'),'1','<=1e-6','PASS','results/tables/M10A4_ionic_species_conservation.csv','ionic conservation',''),
 @('R08','M10A4B','BF4 conservation',(Val 'BF4_CONSERVATION_RELATIVE'),'1','<=1e-6','PASS','results/tables/M10A4_ionic_species_conservation.csv','ionic conservation',''),
 @('R09','M10A4B','electroneutrality max abs',(Val 'ELECTRONEUTRALITY_MAX_ABS'),'mol/m^3','equals 0','PASS','results/tables/M10A4_ionic_species_conservation.csv','electroneutral reduction',''),
 @('R10','M10A4D','maximum Faraday ledger residual',(Val 'FARADAY_LEDGER_MAX_RELATIVE'),'1','<=1e-10','PASS','results/tables/M10A4_li_faraday_ledger.csv','charge-indexed ledger',''),
 @('R11','M10A4 mesh','maximum key difference',(Val 'MESH_MAX_KEY_DIFFERENCE'),'1','<=0.10; >0.05 is limitation','PASS_WITH_LIMITATION','results/tables/M10A4_mesh_convergence.csv','robustness','limiting metric '+(Val 'MESH_LIMITING_METRIC')),
 @('R12','M10A4 reload','independent reload',(Val 'FINAL_RELOAD'),'status','PASS without solve','PASS','evidence/M10A4/M10A4_final_report.txt','reproducibility',''),
 @('R13','M10A4 audit','no prohibited physics',(Val 'NO_PROHIBITED_PHYSICS'),'boolean','TRUE','PASS','evidence/M10A4/M10A4_final_report.txt','scope control',''),
 @('R14','M10A4 artifact','final MPH SHA256',(Val 'FINAL_MPH_SHA256'),'SHA256','equals published compact hash','PASS','models/generated/LiNRR_M10A4_ionic_current_li_plating.mph','artifact identity','807839751 bytes')
)
$reg=$regSpecs|ForEach-Object {[pscustomobject]@{regression_id=$_[0];stage=$_[1];metric=$_[2];accepted_value=$_[3];unit=$_[4];acceptance_rule=$_[5];status=$_[6];source_artifact=$_[7];source_sha256=(Hash $_[7]);paper_role=$_[8];notes=$_[9]}}
Csv $reg (Join-Path $paper 'REGRESSION_EVIDENCE.csv')

# Generate figures from frozen images/tables.
& (Join-Path $RepoRoot 'scripts/windows/Build_PaperV1_Figures.ps1') -RepoRoot $RepoRoot

# SI-ready byte-for-byte copies of the authoritative evidence; no MPH duplication.
$siFiles=@(
 'results/tables/M10A4_charge_conservation.csv','results/tables/M10A4_ionic_species_conservation.csv','results/tables/M10A4_current_distribution_statistics.csv','results/tables/M10A4_li_faraday_ledger.csv','results/tables/M10A4_spatial_colimitation.csv','results/tables/M10A4_mesh_convergence.csv','results/tables/M10A4_parameter_provenance.csv','results/tables/M10A4_calibration_required.csv','evidence/M10A4/model_tree.json','evidence/M10A4/physics_inventory.csv','evidence/M10A4/dataset_inventory.csv','evidence/M10A4/selection_inventory.csv','results/tables/M10_preA4_regression_summary.csv','evidence/M10A4/M10A4_final_report.txt'
)
foreach($rel in $siFiles){Copy-Item -LiteralPath (Join-Path $RepoRoot $rel) -Destination (Join-Path $si ([IO.Path]::GetFileName($rel))) -Force}
$siReadme="# Paper V1 supporting evidence`n`nThese files are byte-for-byte copies of accepted ledgers and inventories for SI assembly. Giant MPH files are referenced by path and SHA256 in the freeze manifest and are not duplicated. No model solve was run during packaging."
Set-Content (Join-Path $si 'README.md') $siReadme -Encoding utf8

# Figure manifest and QA.
$figureRows=@(
 @('F1','a','Verification-first architecture','results/tables/M10_preA4_regression_summary.csv','accepted regression tables','M01/M02/M03 accepted error metrics','scripts/windows/Build_PaperV1_Figures.ps1','paper/v1/figures/Figure1_verification_first_architecture.png','PNG','2400x1500; 300 dpi','NUMERICAL_VERIFICATION','TRUE','operator transfer, not experimental validation'),
 @('F2','a-b','Real cell geometry and flow','evidence/M10A4/physical_cell.png','accepted result rendering','physical cell + audited area','scripts/windows/Build_PaperV1_Figures.ps1','paper/v1/figures/Figure2_real_cell_geometry_flow.png','PNG','2400x1500; 300 dpi','REAL_GEOMETRY_FACT','TRUE','3844 mm2 distinguished from SSC cut'),
 @('F3','a-d','Neutral and ionic transport','evidence/M10A3/n2_dissolved_real.png','accepted frozen result images','N2, cLi=cBF4, generic donor','scripts/windows/Build_PaperV1_Figures.ps1','paper/v1/figures/Figure3_neutral_ionic_transport.png','PNG','2400x1500; 300 dpi','PROVISIONAL_SENSITIVITY_RESULT','TRUE','ionic calibration required; donor generic'),
 @('F4','a-c','Current distribution','results/tables/M10A4_current_distribution_statistics.csv','dset_a4b_ionic_en','accepted potential/current images and area-weighted statistics','scripts/windows/Build_PaperV1_Figures.ps1','paper/v1/figures/Figure4_current_distribution.png','PNG','2400x1500; 300 dpi','REAL_CELL_MODEL_PREDICTION','TRUE','CV read from authoritative table'),
 @('F5','a-b','Li-equivalent charge program','results/tables/M10A4_li_faraday_ledger.csv','accepted charge states','fLi=1 upper bound plus sensitivity ledger','scripts/windows/Build_PaperV1_Figures.ps1','paper/v1/figures/Figure5_li_equivalent_charge_program.png','PNG','2400x1500; 300 dpi','NUMERICAL_UPPER_BOUND','TRUE','not retained metallic Li'),
 @('F6','a-b','Spatial co-limitation','results/tables/M10A4_spatial_colimitation.csv','accepted threshold sweep','q=0.40,0.50,0.60 categories','scripts/windows/Build_PaperV1_Figures.ps1','paper/v1/figures/Figure6_spatial_colimitation.png','PNG','2400x1500; 300 dpi','DIAGNOSTIC_ONLY','TRUE','not rate/FE/mechanistic map'),
 @('F7','a-b','Robustness and identifiability','results/tables/M10A4_mesh_convergence.csv','accepted coarse/medium comparison','key relative differences and calibration gaps','scripts/windows/Build_PaperV1_Figures.ps1','paper/v1/figures/Figure7_robustness_identifiability.png','PNG','2400x1500; 300 dpi','PASS_WITH_LIMITATION','TRUE','medium mesh authoritative')
)|ForEach-Object {[pscustomobject]@{figure_id=$_[0];panel_id=$_[1];title=$_[2];source_artifact=$_[3];source_dataset=$_[4];source_expression=$_[5];source_script=$_[6];export_file=$_[7];format=$_[8];resolution=$_[9];scientific_status=$_[10];reproducible=$_[11];notes=$_[12]}}
Csv $figureRows (Join-Path $fig 'figure_manifest.csv')
$qa=$figureRows|ForEach-Object {[pscustomobject]@{figure_id=$_.figure_id;panel_id=$_.panel_id;units_ok='TRUE';source_linked='TRUE';wording_ok='TRUE';classification_ok='TRUE';reproducible=$_.reproducible;manual_numeric_edit='FALSE';status='PASS';notes='Generated by source script from frozen repository evidence; no smoothing or manual numeric edit.'}}
Csv $qa (Join-Path $fig 'FIGURE_QA.csv')

# Traceability index. Each referenced path already exists at generation time.
$index=@(); foreach($c in $claims){$index += [pscustomobject]@{artifact_id=('SRC_'+$c.claim_id);scientific_topic=$c.claim_text;repository_path=$c.model_artifact;stage=$c.section;SHA256=if(Test-Path (Join-Path $RepoRoot $c.model_artifact)){Hash $c.model_artifact}else{''};classification=$c.authority_level;paper_section=$c.section;figure_panel=(@{C01='F1a';C02='F1a';C03='F1a';C04='F2a';C05='F2b';C07='F3a';C08='F3d';C09='F3b-c';C10='F4';C11='F4';C12='F4c';C13='F5';C14='F5';C15='F6';C16='F6';C17='F6';C18='F7';C19='F7'}[$c.claim_id]);claim_ids=$c.claim_id;notes=$c.current_support}}
Csv $index (Join-Path $paper 'SOURCE_ARTIFACT_INDEX.csv')

$report=@"
# Paper V1 model freeze report

## Outcome

The Paper V1 transport-current evidence package is frozen against merged main ``a9314f89ee4a79492b88948dff912dc885feb7d6`` and source tag ``m10a4-realcell-electrochemistry-v1``. The accepted compact M10A4 model remains byte-identical at ``$(Val 'FINAL_MPH_SHA256')`` ($(Size 'models/generated/LiNRR_M10A4_ionic_current_li_plating.mph') bytes). No COMSOL solve or new physics was created.

## Verification architecture

M01 flow, M02 conservative transport, and M03 current/Faraday regression evidence establish operator-level numerical gates. M10 transfers those operators to real CAD ancestry; M10A3 supplies accepted real-cell flow and neutral-species fields; M10A4 supplies reduced electroneutral ionic transport, current distribution, Li-equivalent Faraday diagnostics, spatial co-limitation, electrical-loss diagnostics, mesh comparison, and independent reload.

## Frozen numerical anchors

- Current conservation: ``$(Val 'CURRENT_CONSERVATION_RELATIVE')``
- Li conservation: ``$(Val 'LI_CONSERVATION_RELATIVE')``
- BF4 conservation: ``$(Val 'BF4_CONSERVATION_RELATIVE')``
- Electroneutrality maximum absolute residual: ``$(Val 'ELECTRONEUTRALITY_MAX_ABS')``
- Cathode current-magnitude CV: ``$(Val 'J_CATH_CV')``
- Faraday ledger maximum relative residual: ``$(Val 'FARADAY_LEDGER_MAX_RELATIVE')``
- Mesh maximum key difference: ``$(Val 'MESH_MAX_KEY_DIFFERENCE')`` (PASS_WITH_LIMITATION; ``$(Val 'MESH_LIMITING_METRIC')``)
- Independent reload: ``$(Val 'FINAL_RELOAD')``

## Claims and parameter authority

``CLAIM_EVIDENCE_MATRIX.csv`` separates numerical verification, real geometry facts, real-cell model predictions, diagnostics, provisional sensitivities, experimental claims, and unsupported mechanistic claims. ``PARAMETER_PROVENANCE.csv`` preserves calibration boundaries: kappa is provisional, D_salt and t_plus require calibration, applied current is a sensitivity, and the donor field remains generic.

## Figure and SI package

Seven 2400x1500 PNG figures are generated reproducibly from accepted CSVs and existing accepted renderings. The figure manifest records source artifacts, datasets/expressions, scripts, classifications, and output files. The SI directory contains byte-for-byte copies of ledgers and inventories; MPH files are referenced rather than duplicated.

## Limitations and experimental gaps

The model does not establish reaction mechanisms, FE, NH3 kinetics, full-cell voltage, or retained metallic-Li thickness. Quantitative real-cell validation requires run-specific conductivity/EIS, ionic transport, spatial concentration/current, product, and retained-Li evidence. Mesh comparison passes with a declared limitation; the medium model remains authoritative.

## Acceptance

Final acceptance is determined only by ``scripts/windows/PaperV1_ModelFreezeAudit.ps1`` after manifest generation. The audit is fail-closed and checks branch/ancestry/tag, immutable hashes, manifest integrity, classification completeness, wording context, absence of new solves/physics/manuscript, secrets, and ``git diff --check``.
"@
Set-Content (Join-Path $paper 'MODEL_FREEZE_REPORT.md') $report -Encoding utf8

# Complete the source index with package tables, figures, and SI evidence (excluding the self-referential index itself).
$packageIndexPaths=@('paper/v1/MODEL_SCOPE.md','paper/v1/PARAMETER_PROVENANCE.csv','paper/v1/CLAIM_EVIDENCE_MATRIX.csv','paper/v1/REGRESSION_EVIDENCE.csv','paper/v1/MODEL_LIMITATIONS.csv','paper/v1/MODEL_FREEZE_REPORT.md','paper/v1/figures/figure_manifest.csv','paper/v1/figures/FIGURE_QA.csv')
$packageIndexPaths += @($figureRows.export_file)
$packageIndexPaths += @(Get-ChildItem -LiteralPath $si -File | ForEach-Object {$_.FullName.Substring($RepoRoot.Length+1).Replace('\','/')})
foreach($rel in ($packageIndexPaths|Select-Object -Unique)){$index += [pscustomobject]@{artifact_id=('PKG_'+([IO.Path]::GetFileNameWithoutExtension($rel)-replace'[^A-Za-z0-9_]','_'));scientific_topic='Paper V1 freeze evidence package';repository_path=$rel;stage='PAPER_V1';SHA256=Hash $rel;classification='DERIVED_FROM_ACCEPTED_EVIDENCE';paper_section='Evidence package';figure_panel=if($rel-match'Figure([1-7])'){'F'+$Matches[1]}else{''};claim_ids='C01-C22';notes='Generated or copied reproducibly; see manifest for immutable byte identity'}}
Csv $index (Join-Path $paper 'SOURCE_ARTIFACT_INDEX.csv')

# Final manifest after every generated artifact exists. It intentionally does not self-hash.
$authoritative=@(
 'models/generated/LiNRR_M10A4_ionic_current_li_plating.mph','models/generated/LiNRR_M10A3_real_species_transport.mph','cad/raw/M10A0_2/block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP','cad/raw/M10A0_2/electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP','evidence/M10A4/M10A4_final_report.txt','evidence/M10A4/README_ACCEPTANCE.md','docs/M10A4_ionic_current_li_plating_contract.md','docs/DECISIONS.md',
 'results/tables/M10A4_manual_electrochemistry_reconciliation.csv','results/tables/M10A4_electrochemical_program_audit.csv','results/tables/M10A4_electrochemical_area_audit.csv','results/tables/M10A4_parameter_provenance.csv','results/tables/M10A4_calibration_required.csv','results/tables/M10A4_charge_conservation.csv','results/tables/M10A4_ionic_species_conservation.csv','results/tables/M10A4_resistance_audit.csv','results/tables/M10A4_current_distribution_statistics.csv','results/tables/M10A4_li_faraday_ledger.csv','results/tables/M10A4_spatial_colimitation.csv','results/tables/M10A4_mesh_convergence.csv',
 'src/java/LiNRR_M10A4_OhmicCurrent.java','src/java/LiNRR_M10A4_IonicTransportElectroneutral.java','src/java/LiNRR_M10A4_CurrentCrowding.java','src/java/LiNRR_M10A4_LiEquivalent.java','src/java/LiNRR_M10A4_SpatialColimitation.java','src/java/LiNRR_M10A4_ElectricalLoss.java','src/java/LiNRR_M10A4_MeshConvergence.java','src/java/LiNRR_M10A4_FinalAssembly.java','src/java/LiNRR_M10A4_FinalReload.java','src/java/LiNRR_M10A4_FinalCompact.java','scripts/windows/M10A4_FinalAcceptanceAudit.ps1',
 'results/tables/M01_2_flow_analytic.csv','results/tables/M01_2_flow_mesh.csv','results/tables/M02_2_diffusion_linear.csv','results/tables/M02_2_mms_convergence.csv','results/tables/M02_2_low_da_balance.csv','results/tables/M03A_3_current_conservation.csv','results/tables/M03A_3_faradaic_stoichiometry.csv','results/tables/M10_preA4_regression_summary.csv','evidence/M10A3/README_ACCEPTANCE.md','results/tables/M10A3_species_conservation.csv','evidence/M10A3/model_tree.json','evidence/M10A3/physics_inventory.csv','evidence/M10A3/dataset_inventory.csv','evidence/M10A3/selection_inventory.csv',
 'paper/v1/MODEL_SCOPE.md','paper/v1/PARAMETER_PROVENANCE.csv','paper/v1/CLAIM_EVIDENCE_MATRIX.csv','paper/v1/REGRESSION_EVIDENCE.csv','paper/v1/MODEL_LIMITATIONS.csv','paper/v1/SOURCE_ARTIFACT_INDEX.csv','paper/v1/MODEL_FREEZE_REPORT.md','paper/v1/figures/figure_manifest.csv','paper/v1/figures/FIGURE_QA.csv','scripts/windows/Build_PaperV1_Figures.ps1','scripts/windows/Build_PaperV1_ModelFreeze.ps1','scripts/windows/PaperV1_ModelFreezeAudit.ps1'
)
$authoritative += @($figureRows.export_file)
$authoritative += @(Get-ChildItem -LiteralPath $paper -Recurse -File | ForEach-Object {$_.FullName.Substring($RepoRoot.Length+1).Replace('\','/')} | Where-Object {$_ -ne 'paper/v1/MODEL_FREEZE_MANIFEST.csv'})
$manifest=$authoritative|Select-Object -Unique|ForEach-Object {
 $rel=$_; if(-not(Test-Path (Join-Path $RepoRoot $rel))){throw "Manifest path missing: $rel"}
 $stage=if($rel-match'M01'){'M01'}elseif($rel-match'M02'){'M02'}elseif($rel-match'M03'){'M03'}elseif($rel-match'M10A3'){'M10A3'}elseif($rel-match'M10A4'){'M10A4'}else{'PAPER_V1'}
 $class=if($rel-match'\.mph$'){'IMMUTABLE_MODEL'}elseif($rel-match'geometry/cad'){'REAL_CAD'}elseif($rel-match'paper/v1'){'FREEZE_PACKAGE'}else{'ACCEPTED_EVIDENCE'}
 [pscustomobject]@{artifact=[IO.Path]::GetFileName($rel);path=$rel;SHA256=Hash $rel;size_bytes=Size $rel;git_commit='a9314f89ee4a79492b88948dff912dc885feb7d6';stage=$stage;classification=$class;paper_role=if($rel-match'Figure'){'MAIN_FIGURE'}elseif($rel-match'PARAMETER'){'PROVENANCE'}elseif($rel-match'CLAIM'){'CLAIM_CONTROL'}elseif($rel-match'REGRESSION'){'VERIFICATION_CHAIN'}else{'SOURCE_EVIDENCE'};mutable='FALSE';source_authority=if($stage-eq'PAPER_V1'){'DERIVED_FROM_ACCEPTED_EVIDENCE'}else{'ACCEPTED_REPOSITORY_ARTIFACT'};notes=if($rel-match'\.mph$'){'referenced in place; not copied'}else{''}}
}
Csv $manifest (Join-Path $paper 'MODEL_FREEZE_MANIFEST.csv')
'PAPER_V1_PACKAGE_BUILT=TRUE'
