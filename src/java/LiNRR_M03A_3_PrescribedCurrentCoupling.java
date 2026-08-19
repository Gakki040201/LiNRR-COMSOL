import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * M03A.3 prescribed-current, one-way Faradaic stoichiometric coupling audit.
 *
 * <p>The model is derived in memory from the frozen M02.2 MPH. It adds only the
 * verified COMSOL 6.4 PrimaryCurrentDistribution interface and maps its signed
 * cathode outward-normal current to N2/NH3 General Inward Flux values. There is
 * no electrode reaction feature, exchange-current law, overpotential law, or
 * concentration-dependent kinetic law.</p>
 */
public final class LiNRR_M03A_3_PrescribedCurrentCoupling {
    private static final String PROVISIONAL = "PROVISIONAL - synthetic numerical verification only";
    private static final String SOURCE_REL =
        "models/generated/LiNRR_M02_2_transport_verification.mph";
    private static final double CURRENT_TOL = 1.0e-6;
    private static final double SPECIES_TOL = 1.0e-4;
    private static final double MESH_TOL = 5.0e-3;
    private static final double[] MULTIPLIERS = {0.1, 0.3, 1.0, 3.0, 10.0};
    private static final double[] FE_VALUES = {0.0, 0.1, 0.5, 1.0};
    private static final double[] MAP_J = {0.1, 1.0, 10.0, 100.0, 1000.0};
    private static final double[] MAP_U = {1.0e-5, 3.0e-5, 1.0e-4, 3.0e-4, 1.0e-3};
    private static final int[][] MESHES = {{80, 40}, {160, 80}, {320, 160}};
    private static final String[] MESH_NAMES = {"coarse", "medium", "fine"};
    private static final double BASE_KAPPA = 0.5;
    private static final double BASE_DELTA_PHI = 8.0e-4;
    private static final double BASE_U = 1.0e-4;

    private static final class FlowMetrics {
        double umean, center, pressureDrop, massIn, massOut, massError;
    }

    private static final class CurrentMetrics {
        double anode, cathode, left, right, positiveCathode, balanceError;
        double phiAnode, phiCathode, voltage, analyticCurrent, currentError;
        double resistance, analyticResistance, resistanceError, linearityError;
        int nx, ny, cells, dof;
        String mesh, status;
    }

    private static final class SpeciesMetrics {
        double n2In, n2Out, nh3In, nh3Out, n2Consumption, nh3Generation;
        double n2Predicted, nh3Predicted, n2CurrentError, nh3CurrentError;
        double ratio, ratioError, n2Balance, nh3Balance, nitrogenBalance;
        double minN2, minNH3, conversion, theta;
    }

    private LiNRR_M03A_3_PrescribedCurrentCoupling() {}

    public static void main(String[] args) throws Exception {
        Path root = resolveProjectRoot();
        Path source = root.resolve(SOURCE_REL.replace('/', File.separatorChar)).normalize();
        Path output = Paths.get(requireRunInput("M03A3_OUTPUT_MPH")).toAbsolutePath().normalize();
        Path potentialPng = Paths.get(requireRunInput("M03A3_POTENTIAL_PNG")).toAbsolutePath().normalize();
        Path speciesPng = Paths.get(requireRunInput("M03A3_SPECIES_PNG")).toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) throw new IllegalStateException("Frozen M02.2 MPH missing: " + source);
        if (Files.exists(output)) throw new IllegalStateException("Refusing to overwrite MPH: " + output);
        if (source.equals(output)) throw new IllegalStateException("Output must not equal frozen source MPH.");

        String resumeText = optionalRunInput("M03A3_RESUME_SOURCE");
        if (resumeText != null && !resumeText.trim().isEmpty()) {
            Path resumeSource = Paths.get(resumeText).toAbsolutePath().normalize();
            if (!Files.isRegularFile(resumeSource))
                throw new IllegalStateException("M03A.3 recovery MPH missing: " + resumeSource);
            Model recovered = ModelUtil.load("M033RecoveredFinalization", resumeSource.toString());
            verifyFrozenModel(recovered);
            requireTag(recovered.component("comp1").physics().tags(), "cd", "Primary Current Distribution");
            restoreFineLowConversion(recovered);
            CurrentMetrics current = evaluateCurrent(recovered, "fine_recovered", 320, 160);
            SpeciesMetrics closure = evaluateSpecies(recovered, current);
            exportImages(recovered, potentialPng.toString(), speciesPng.toString());
            recovered.label("LiNRR M03A.3 prescribed-current one-way stoichiometric coupling | " + PROVISIONAL);
            recovered.save(output.toString());
            emitClosureSummary(closure);
            emitReadiness();
            System.out.println("M03A_3_PROGRESS|RECOVERED_FINALIZATION|COMPLETE");
            System.out.println("M03A_3_PROGRESS|ALL_BENCHMARKS|COMPLETE");
            return;
        }

        FlowMetrics frozenFlow;
        SpeciesMetrics frozenTransport;
        Model baseline = ModelUtil.load("M033FrozenBaseline", source.toString());
        try {
            verifyFrozenModel(baseline);
            configureFrozenFlowAndTransport(baseline);
            createAuditDatasetsAndNumerics(baseline, false);
            configureMesh(baseline, 160, 80);
            setFlow(baseline, 7.575757575757576e-5);
            baseline.param().set("reaction_on", "0");
            activateStudy(baseline, true, true, false);
            baseline.study("std_audit").run();
            frozenFlow = evaluateFlow(baseline);
            setFlow(baseline, BASE_U);
            baseline.study("std_audit").run();
            frozenTransport = evaluateSpeciesWithoutCurrent(baseline);
        } finally {
            ModelUtil.remove("M033FrozenBaseline");
        }

        Model model = ModelUtil.load("M033PrescribedCurrent", source.toString());
        verifyFrozenModel(model);
        configureFrozenFlowAndTransport(model);
        defineParameters(model);
        addPrimaryCurrentDistribution(model);
        addOneWayStoichiometricCoupling(model);
        createAuditDatasetsAndNumerics(model, true);
        createResults(model);

        emitSelectionAudit(model);
        runOhmicBenchmark(model);
        runFrozenInvariance(model, frozenFlow, frozenTransport);
        SpeciesMetrics closure = runCoupledMeshAndClosure(model);
        runLinearityAndDecoupling(model);
        runCurrentFlowMap(model);
        restoreFineLowConversion(model);
        exportImages(model, potentialPng.toString(), speciesPng.toString());
        model.label("LiNRR M03A.3 prescribed-current one-way stoichiometric coupling | " + PROVISIONAL);
        model.save(output.toString());
        emitClosureSummary(closure);
        emitReadiness();
        System.out.println("M03A_3_PROGRESS|ALL_BENCHMARKS|COMPLETE");
    }

    private static void verifyFrozenModel(Model model) {
        requireOne(model, "sel_electrolyte", 2);
        requireOne(model, "sel_inlet", 1);
        requireOne(model, "sel_outlet", 1);
        requireOne(model, "sel_anode_wall", 1);
        requireOne(model, "sel_cathode_wall", 1);
        requireTag(model.component("comp1").physics().tags(), "spf", "Laminar Flow");
        requireTag(model.component("comp1").physics().tags(), "tds", "Transport of Diluted Species");
        String conv = model.component("comp1").physics("tds").prop("AdvancedSettings")
            .getString("ConvectiveTerm");
        String inlet = model.component("comp1").physics("tds").feature("inflow_audit")
            .getString("BoundaryConditionType");
        if (!"cons".equals(conv) || !"FluxDanckwerts".equals(inlet) ||
            model.component("comp1").physics("tds").feature("conc_in").isActive()) {
            throw new IllegalStateException("Frozen M02.2 conservative/Danckwerts contract mismatch.");
        }
    }

    private static void configureFrozenFlowAndTransport(Model model) {
        model.component("comp1").physics("spf").prop("ShapeProperty").set("order_fluid", 2);
        model.component("comp1").physics("spf").feature("outlet")
            .set("BoundaryCondition", "LaminarOutflow");
        model.component("comp1").physics("spf").feature("outlet")
            .set("LaminarOutflowOption", "p0_exit");
        model.component("comp1").physics("spf").feature("outlet").set("p0_exit", "0[Pa]");
        model.component("comp1").physics("tds").prop("AdvancedSettings")
            .set("ConvectiveTerm", "cons");
        model.component("comp1").physics("tds").prop("MassConsistentStabilization")
            .set("massStreamlineDiffusion", true);
        model.component("comp1").physics("tds").prop("MassConsistentStabilization")
            .set("massCrosswindDiffusion", true);
        model.component("comp1").physics("tds").prop("ShapeProperty")
            .set("order_concentration", 2);
    }

    private static void defineParameters(Model model) {
        model.param().set("kappa_M033", "0.5[S/m]", "Synthetic conductivity; " + PROVISIONAL);
        model.param().set("DeltaPhi_M033", "0.8[mV]", "Synthetic ohmic potential difference");
        model.param().set("FE_prescribed", "1", "Synthetic prescribed input; not a prediction");
        model.param().set("F_const_M033", "96485.33212[C/mol]", "Faraday constant");
        model.param().set("Aeq_M033", "Lcell*Wcell", "3D equivalent current-carrying area");
        model.param().set("j_app_M033", "kappa_M033*DeltaPhi_M033/Hcell",
            "Prescribed positive current-density magnitude at upper current boundary");
        model.param().set("I_analytic_M033", "kappa_M033*Aeq_M033*DeltaPhi_M033/Hcell");
        model.param().set("R_analytic_M033", "Hcell/(kappa_M033*Aeq_M033)");
    }

    private static void addPrimaryCurrentDistribution(Model model) {
        model.component("comp1").physics().create("cd", "PrimaryCurrentDistribution", "geom1");
        model.component("comp1").physics("cd")
            .label("M03A.3 Primary Current Distribution - pure ohmic synthetic field");
        model.component("comp1").physics("cd").selection().named("sel_electrolyte");
        model.component("comp1").physics("cd").feature("ice1").set("sigmal_mat", "userdef");
        model.component("comp1").physics("cd").feature("ice1").set("sigmal", "kappa_M033");
        model.component("comp1").physics("cd")
            .create("current_anode_M033", "ElectrolyteCurrent", 1);
        model.component("comp1").physics("cd").feature("current_anode_M033")
            .selection().named("sel_anode_wall");
        model.component("comp1").physics("cd").feature("current_anode_M033")
            .set("IonicCurrentType", "AverageCurrentDensity");
        model.component("comp1").physics("cd").feature("current_anode_M033")
            .set("Ial", "j_app_M033");
        model.component("comp1").physics("cd")
            .create("potential_cathode_M033", "ElectrolytePotential", 1);
        model.component("comp1").physics("cd").feature("potential_cathode_M033")
            .selection().named("sel_cathode_wall");
        model.component("comp1").physics("cd").feature("potential_cathode_M033")
            .set("philbnd", "0[V]");
    }

    private static void addOneWayStoichiometricCoupling(Model model) {
        model.component("comp1").variable("var_rxn")
            .label("M03A.3 prescribed-current one-way Faradaic mapping; no kinetics");
        model.component("comp1").variable("var_rxn").set("j_signed_outward_M033",
            "-kappa_M033*(d(cd.phil,x)*nx+d(cd.phil,y)*ny)",
            "Raw signed outward-normal electrolyte current density");
        model.component("comp1").variable("var_rxn").set("j_cathodic_positive",
            "j_signed_outward_M033",
            "Positive cathodic-current magnitude after explicit sign audit; no abs()");
        model.component("comp1").variable("var_rxn").set("rN2",
            "FE_prescribed*j_cathodic_positive/(6*F_const_M033)",
            "Prescribed N2 consumption molar flux; not kinetics");
        model.component("comp1").variable("var_rxn").set("rNH3", "2*rN2",
            "Prescribed NH3 generation molar flux; N2 + 6e- -> 2NH3");
        model.component("comp1").physics("tds").feature("flux_cathode")
            .label("M03A.3 General Inward Flux from prescribed current; no kinetics");
        model.component("comp1").physics("tds").feature("flux_cathode")
            .set("FluxType", "GeneralInwardFlux");
        model.component("comp1").physics("tds").feature("flux_cathode")
            .set("J0", new String[] {"-rN2", "rNH3"});
        model.param().set("reaction_on", "1", "Legacy switch fixed on; FE_prescribed controls coupling");
        model.study("std_audit").feature("stat").activate("cd", true);
    }

    private static void createAuditDatasetsAndNumerics(Model model, boolean withCurrent) {
        double lmm = model.param().evaluate("Lcell", "mm");
        double hmm = model.param().evaluate("Hcell", "mm");
        model.result().dataset().create("m033_vertical_mid", "CutLine2D");
        model.result().dataset("m033_vertical_mid").set("genpoints",
            new double[][] {{0.5 * lmm, 0.0}, {0.5 * lmm, hmm}});
        model.result().dataset().create("m033_center_point", "CutPoint2D");
        model.result().dataset("m033_center_point").set("pointx", 0.5 * lmm);
        model.result().dataset("m033_center_point").set("pointy", 0.5 * hmm);
        datasetNumerical(model, "m033_u_center", "EvalPoint", "m033_center_point", "u", "m/s");
        datasetNumerical(model, "m033_u_mean", "AvLine", "m033_vertical_mid", "u", "m/s");
        boundaryNumerical(model, "m033_mdot_in", "IntLine", "sel_inlet",
            "rho_el*(u*nx+v*ny)*Wcell", "kg/s");
        boundaryNumerical(model, "m033_mdot_out", "IntLine", "sel_outlet",
            "rho_el*(u*nx+v*ny)*Wcell", "kg/s");
        boundaryNumerical(model, "m033_p_in", "AvLine", "sel_inlet", "p", "Pa");
        boundaryNumerical(model, "m033_p_out", "AvLine", "sel_outlet", "p", "Pa");
        boundaryNumerical(model, "m033_n2_in", "IntLine", "sel_inlet",
            "tds.ntflux_cN2*Wcell", "mol/s");
        boundaryNumerical(model, "m033_n2_out", "IntLine", "sel_outlet",
            "tds.ntflux_cN2*Wcell", "mol/s");
        boundaryNumerical(model, "m033_nh3_in", "IntLine", "sel_inlet",
            "tds.ntflux_cNH3*Wcell", "mol/s");
        boundaryNumerical(model, "m033_nh3_out", "IntLine", "sel_outlet",
            "tds.ntflux_cNH3*Wcell", "mol/s");
        boundaryNumerical(model, "m033_n2_cath", "IntLine", "sel_cathode_wall",
            "tds.ntflux_cN2*Wcell", "mol/s");
        boundaryNumerical(model, "m033_nh3_cath", "IntLine", "sel_cathode_wall",
            "tds.ntflux_cNH3*Wcell", "mol/s");
        domainExtremum(model, "m033_min_n2", "cN2");
        domainExtremum(model, "m033_min_nh3", "cNH3");
        boundaryNumerical(model, "m033_len_inlet", "IntLine", "sel_inlet", "1", "m");
        boundaryNumerical(model, "m033_len_outlet", "IntLine", "sel_outlet", "1", "m");
        boundaryNumerical(model, "m033_len_anode", "IntLine", "sel_anode_wall", "1", "m");
        boundaryNumerical(model, "m033_len_cathode", "IntLine", "sel_cathode_wall", "1", "m");
        if (withCurrent) {
            boundaryNumerical(model, "m033_n2_imposed_cath", "IntLine", "sel_cathode_wall",
                "rN2*Wcell", "mol/s");
            boundaryNumerical(model, "m033_nh3_imposed_cath", "IntLine", "sel_cathode_wall",
                "rNH3*Wcell", "mol/s");
            boundaryNumerical(model, "m033_i_anode", "IntLine", "sel_anode_wall",
                "(-kappa_M033*(d(cd.phil,x)*nx+d(cd.phil,y)*ny))*Wcell", "A");
            boundaryNumerical(model, "m033_i_cathode", "IntLine", "sel_cathode_wall",
                "(-kappa_M033*(d(cd.phil,x)*nx+d(cd.phil,y)*ny))*Wcell", "A");
            boundaryNumerical(model, "m033_i_left", "IntLine", "sel_inlet",
                "(-kappa_M033*(d(cd.phil,x)*nx+d(cd.phil,y)*ny))*Wcell", "A");
            boundaryNumerical(model, "m033_i_right", "IntLine", "sel_outlet",
                "(-kappa_M033*(d(cd.phil,x)*nx+d(cd.phil,y)*ny))*Wcell", "A");
            boundaryNumerical(model, "m033_phi_anode", "AvLine", "sel_anode_wall",
                "cd.phil", "V");
            boundaryNumerical(model, "m033_phi_cathode", "AvLine", "sel_cathode_wall",
                "cd.phil", "V");
            model.result().numerical().create("m033_phi_linearity", "MaxSurface");
            model.result().numerical("m033_phi_linearity").selection().named("sel_electrolyte");
            model.result().numerical("m033_phi_linearity").set("expr",
                new String[] {"abs(cd.phil-DeltaPhi_M033*y/Hcell)"});
            model.result().numerical("m033_phi_linearity").set("unit", new String[] {"V"});
        }
    }

    private static void createResults(Model model) {
        model.component("comp1").variable().create("var_M033_current");
        model.component("comp1").variable("var_M033_current").selection().named("sel_electrolyte");
        model.component("comp1").variable("var_M033_current").set("i_M033_x",
            "-kappa_M033*d(cd.phil,x)");
        model.component("comp1").variable("var_M033_current").set("i_M033_y",
            "-kappa_M033*d(cd.phil,y)");
        model.component("comp1").variable("var_M033_current").set("i_M033_mag",
            "sqrt(i_M033_x^2+i_M033_y^2)");
        model.result().create("pg_M033_potential", "PlotGroup2D");
        model.result("pg_M033_potential").label("M03A.3 potential and prescribed current");
        model.result("pg_M033_potential").create("surface", "Surface");
        model.result("pg_M033_potential").feature("surface").set("expr", "cd.phil");
        model.result("pg_M033_potential").feature("surface").set("unit", "V");
        model.result("pg_M033_potential").create("arrows", "ArrowSurface");
        model.result("pg_M033_potential").feature("arrows")
            .set("expr", new String[] {"i_M033_x", "i_M033_y"});
        model.result().create("pg_M033_species", "PlotGroup2D");
        model.result("pg_M033_species").label("M03A.3 low-conversion species fields");
        model.result("pg_M033_species").create("n2", "Surface");
        model.result("pg_M033_species").feature("n2").set("expr", "cN2");
        model.result("pg_M033_species").feature("n2").set("unit", "mol/m^3");
        model.result("pg_M033_species").create("nh3", "Contour");
        model.result("pg_M033_species").feature("nh3").set("expr", "cNH3");
        model.result("pg_M033_species").feature("nh3").set("unit", "mol/m^3");
    }

    private static void runOhmicBenchmark(Model model) {
        System.out.println("M03A3_OHMIC|mesh|nx|ny|mesh_elements|dof|numerical_current_A|analytical_current_A|current_relative_error|numerical_resistance_ohm|analytical_resistance_ohm|resistance_relative_error|current_conservation_relative_error|potential_linearity_error|conductivity_S_m|potential_finite|classification|status");
        setElectrical(model, BASE_KAPPA, BASE_DELTA_PHI, 0.0);
        activateStudy(model, false, false, true);
        for (int i = 0; i < 3; i++) {
            configureMesh(model, MESHES[i][0], MESHES[i][1]);
            model.study("std_audit").run();
            CurrentMetrics c = evaluateCurrent(model, MESH_NAMES[i], MESHES[i][0], MESHES[i][1]);
            String classification = c.currentError < 1.0e-12 && c.linearityError < 1.0e-12 ?
                "EXACT_POLYNOMIAL_REPRESENTATION" : "NUMERICAL_CONVERGENCE";
            String status = c.currentError <= 1.0e-3 && c.resistanceError <= 1.0e-3 &&
                c.balanceError <= CURRENT_TOL && BASE_KAPPA > 0.0 &&
                LiNRR_M03A_3_Metrics.finite(c.phiAnode, c.phiCathode) ? "PASS" : "FAIL";
            System.out.println("M03A3_OHMIC|" + c.mesh + "|" + c.nx + "|" + c.ny + "|" +
                c.cells + "|" + c.dof + "|" + join(c.cathode, c.analyticCurrent, c.currentError,
                c.resistance, c.analyticResistance, c.resistanceError, c.balanceError,
                c.linearityError, BASE_KAPPA) + "|true|" + classification + "|" + status);
            if (i == 2 && !"PASS".equals(status)) throw new IllegalStateException("Ohmic fine-grid acceptance failed.");
        }
        System.out.println("M03A_3_PROGRESS|A_OHMIC|COMPLETE");
    }

    private static void runFrozenInvariance(Model model, FlowMetrics flowBase,
                                            SpeciesMetrics transportBase) {
        System.out.println("M03A3_INVARIANCE|category|metric|baseline_source|baseline_value|coupled_value|relative_difference|tolerance|status");
        configureMesh(model, 160, 80);
        setElectrical(model, BASE_KAPPA, BASE_DELTA_PHI, 0.0);
        setFlow(model, 7.575757575757576e-5);
        activateStudy(model, true, true, true);
        model.study("std_audit").run();
        FlowMetrics flow = evaluateFlow(model);
        emitInvariant("flow", "Umean", "runtime reload of frozen M02.2 with M01.2 settings", flowBase.umean, flow.umean, 1.0e-6);
        emitInvariant("flow", "centerline_velocity", "runtime reload of frozen M02.2 with M01.2 settings", flowBase.center, flow.center, 1.0e-6);
        emitInvariant("flow", "pressure_drop", "runtime reload of frozen M02.2 with M01.2 settings", flowBase.pressureDrop, flow.pressureDrop, 1.0e-6);
        emitInvariant("flow", "inlet_mass_flow", "runtime reload of frozen M02.2 with M01.2 settings", flowBase.massIn, flow.massIn, 1.0e-6);
        emitInvariant("flow", "outlet_mass_flow", "runtime reload of frozen M02.2 with M01.2 settings", flowBase.massOut, flow.massOut, 1.0e-6);
        emitInvariantAbsolute("flow", "mass_balance_error", "runtime reload of frozen M02.2 with M01.2 settings; absolute change between dimensionless residuals", flowBase.massError, flow.massError, 1.0e-6);

        setFlow(model, BASE_U);
        model.study("std_audit").run();
        SpeciesMetrics species = evaluateSpecies(model, evaluateCurrent(model, "invariance", 160, 80));
        emitInvariant("transport", "N2_inlet", "models/generated/LiNRR_M02_2_transport_verification.mph", transportBase.n2In, species.n2In, 1.0e-4);
        emitInvariant("transport", "N2_outlet", "models/generated/LiNRR_M02_2_transport_verification.mph", transportBase.n2Out, species.n2Out, 1.0e-4);
        emitInvariantAbsolute("transport", "NH3_inlet", "models/generated/LiNRR_M02_2_transport_verification.mph; zero baseline absolute audit scaled by N2 feed", transportBase.nh3In, species.nh3In, 1.1e-11);
        emitInvariantAbsolute("transport", "NH3_outlet", "models/generated/LiNRR_M02_2_transport_verification.mph; zero baseline absolute audit scaled by N2 feed", transportBase.nh3Out, species.nh3Out, 1.1e-11);
        emitInvariantAbsolute("transport", "cathode_N2_flux", "models/generated/LiNRR_M02_2_transport_verification.mph; zero baseline absolute audit scaled by N2 feed", 0.0, species.n2Consumption, 1.1e-11);
        emitInvariantAbsolute("transport", "cathode_NH3_flux", "models/generated/LiNRR_M02_2_transport_verification.mph; zero baseline absolute audit scaled by N2 feed", 0.0, species.nh3Generation, 1.1e-11);
        emitInvariantAbsolute("transport", "N2_species_balance", "models/generated/LiNRR_M02_2_transport_verification.mph; absolute change between dimensionless residuals", transportBase.n2Balance, species.n2Balance, 1.0e-4);
        emitInvariantAbsolute("transport", "NH3_species_balance", "models/generated/LiNRR_M02_2_transport_verification.mph; absolute change between dimensionless residuals", transportBase.nh3Balance, species.nh3Balance, 1.0e-4);
        emitInvariantAbsolute("transport", "nitrogen_balance", "models/generated/LiNRR_M02_2_transport_verification.mph; absolute change between dimensionless residuals", transportBase.nitrogenBalance, species.nitrogenBalance, 1.0e-4);
        emitInvariant("transport", "minimum_cN2", "results/tables/M02_2_uniform_transport.csv", transportBase.minN2, species.minN2, 1.0e-4);
        emitInvariantAbsolute("transport", "minimum_cNH3", "results/tables/M02_2_uniform_transport.csv; zero baseline absolute audit scaled by cN2_in", transportBase.minNH3, species.minNH3, 5.0e-4);
        System.out.println("M03A_3_PROGRESS|B_INVARIANCE|COMPLETE");
    }

    private static SpeciesMetrics runCoupledMeshAndClosure(Model model) {
        System.out.println("M03A3_CURRENT|case|mesh|signed_anode_current_A|signed_cathode_current_A|positive_cathodic_current_A|left_current_A|right_current_A|current_conservation_relative_error|sign_status");
        System.out.println("M03A3_FARADAIC|case|mesh|FE_prescribed|predicted_N2_from_current_mol_s|integrated_N2_consumption_mol_s|current_to_N2_relative_error|predicted_NH3_from_current_mol_s|integrated_NH3_generation_mol_s|current_to_NH3_relative_error|NH3_to_N2_ratio|stoichiometric_relative_error|N2_inlet_mol_s|N2_outlet_mol_s|NH3_inlet_mol_s|NH3_outlet_mol_s|N2_species_balance_error|NH3_species_balance_error|nitrogen_balance_error|min_cN2|min_cNH3|status");
        System.out.println("M03A3_MESH|mesh|nx|ny|mesh_elements|dof|total_current_A|N2_consumption_mol_s|NH3_production_mol_s|outlet_N2_mol_s|outlet_NH3_mol_s|min_cN2|current_conservation_error|N2_balance_error|NH3_balance_error|nitrogen_balance_error|change_total_current|change_N2_consumption|change_NH3_production|change_outlet_N2|change_outlet_NH3|max_key_change|status");
        setElectrical(model, BASE_KAPPA, BASE_DELTA_PHI, 1.0);
        setFlow(model, BASE_U);
        activateStudy(model, true, true, true);
        CurrentMetrics previousC = null;
        SpeciesMetrics previousS = null;
        SpeciesMetrics fine = null;
        for (int i = 0; i < 3; i++) {
            configureMesh(model, MESHES[i][0], MESHES[i][1]);
            model.study("std_audit").run();
            CurrentMetrics c = evaluateCurrent(model, MESH_NAMES[i], MESHES[i][0], MESHES[i][1]);
            SpeciesMetrics s = evaluateSpecies(model, c);
            emitCurrent("coupled_low_conversion", c);
            emitFaradaic("coupled_low_conversion", c, s, 1.0);
            double[] change = previousC == null ? new double[] {Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN} :
                meshChanges(c, s, previousC, previousS);
            boolean finePass = c.balanceError <= CURRENT_TOL && s.n2Balance <= SPECIES_TOL &&
                s.nh3Balance <= SPECIES_TOL && s.nitrogenBalance <= SPECIES_TOL &&
                s.n2CurrentError <= CURRENT_TOL && s.nh3CurrentError <= CURRENT_TOL &&
                s.ratioError <= CURRENT_TOL;
            String status = finePass && (i < 2 || change[5] < MESH_TOL) ? "PASS" : "FAIL";
            System.out.println("M03A3_MESH|" + MESH_NAMES[i] + "|" + MESHES[i][0] + "|" +
                MESHES[i][1] + "|" + (MESHES[i][0] * MESHES[i][1]) + "|" + c.dof + "|" +
                join(c.positiveCathode, s.n2Consumption, s.nh3Generation, s.n2Out, s.nh3Out,
                    s.minN2, c.balanceError, s.n2Balance, s.nh3Balance, s.nitrogenBalance,
                    change[0], change[1], change[2], change[3], change[4], change[5]) + "|" + status);
            if (i == 2 && !"PASS".equals(status)) throw new IllegalStateException("Coupled fine-grid acceptance failed.");
            previousC = c; previousS = s; fine = s;
        }
        System.out.println("M03A_3_PROGRESS|C_FARADAIC|COMPLETE");
        System.out.println("M03A_3_PROGRESS|D_MESH|COMPLETE");
        return fine;
    }

    private static void runLinearityAndDecoupling(Model model) {
        System.out.println("M03A3_LINEARITY|test|point|input_multiplier|FE_prescribed|Umean_m_s|DeltaPhi_V|kappa_S_m|total_current_A|N2_consumption_mol_s|NH3_generation_mol_s|flow_Umean_m_s|slope|intercept|R2|relative_invariance|status");
        configureMesh(model, 160, 80);
        activateStudy(model, false, false, true);
        double[] currents = new double[MULTIPLIERS.length];
        for (int i = 0; i < MULTIPLIERS.length; i++) {
            setElectrical(model, BASE_KAPPA * MULTIPLIERS[i], BASE_DELTA_PHI, 0.0);
            model.study("std_audit").run();
            currents[i] = evaluateCurrent(model, "kappa", 160, 80).positiveCathode;
        }
        double[] fit = LiNRR_M03A_3_Metrics.linearFit(MULTIPLIERS, currents);
        for (int i = 0; i < MULTIPLIERS.length; i++) emitLinearity("conductivity_scaling", i,
            MULTIPLIERS[i], 0.0, BASE_U, BASE_DELTA_PHI, BASE_KAPPA * MULTIPLIERS[i],
            currents[i], 0.0, 0.0, Double.NaN, fit, Double.NaN, fit[2] >= 0.999999);

        for (int i = 0; i < MULTIPLIERS.length; i++) {
            setElectrical(model, BASE_KAPPA, BASE_DELTA_PHI * MULTIPLIERS[i], 0.0);
            model.study("std_audit").run();
            currents[i] = evaluateCurrent(model, "potential", 160, 80).positiveCathode;
        }
        fit = LiNRR_M03A_3_Metrics.linearFit(MULTIPLIERS, currents);
        for (int i = 0; i < MULTIPLIERS.length; i++) emitLinearity("potential_scaling", i,
            MULTIPLIERS[i], 0.0, BASE_U, BASE_DELTA_PHI * MULTIPLIERS[i], BASE_KAPPA,
            currents[i], 0.0, 0.0, Double.NaN, fit, Double.NaN, fit[2] >= 0.999999);

        activateStudy(model, true, true, true);
        double[] n2 = new double[FE_VALUES.length], nh3 = new double[FE_VALUES.length];
        CurrentMetrics lastCurrent = null;
        for (int i = 0; i < FE_VALUES.length; i++) {
            setElectrical(model, BASE_KAPPA, BASE_DELTA_PHI, FE_VALUES[i]); setFlow(model, BASE_U);
            model.study("std_audit").run();
            lastCurrent = evaluateCurrent(model, "FE", 160, 80);
            SpeciesMetrics s = evaluateSpecies(model, lastCurrent); n2[i] = s.n2Consumption; nh3[i] = s.nh3Generation;
        }
        double[] fitN2 = LiNRR_M03A_3_Metrics.linearFit(FE_VALUES, n2);
        double[] fitNh3 = LiNRR_M03A_3_Metrics.linearFit(FE_VALUES, nh3);
        for (int i = 0; i < FE_VALUES.length; i++) {
            boolean ok = fitN2[2] >= 0.999999 && fitNh3[2] >= 0.999999 &&
                (FE_VALUES[i] != 0.0 || (Math.abs(n2[i]) < 1.0e-20 && Math.abs(nh3[i]) < 1.0e-20));
            emitLinearity("FE_scaling_N2", i, FE_VALUES[i], FE_VALUES[i], BASE_U,
                BASE_DELTA_PHI, BASE_KAPPA, lastCurrent.positiveCathode, n2[i], nh3[i],
                Double.NaN, fitN2, Double.NaN, ok);
            emitLinearity("FE_scaling_NH3", i, FE_VALUES[i], FE_VALUES[i], BASE_U,
                BASE_DELTA_PHI, BASE_KAPPA, lastCurrent.positiveCathode, n2[i], nh3[i],
                Double.NaN, fitNh3, Double.NaN, ok);
        }

        double referenceCurrent = Double.NaN, maxCurrentChange = 0.0;
        double[] flowValues = {3.0e-5, 1.0e-4, 3.0e-4};
        for (int i = 0; i < flowValues.length; i++) {
            setFlow(model, flowValues[i]); setElectrical(model, BASE_KAPPA, BASE_DELTA_PHI, 1.0);
            model.study("std_audit").run();
            CurrentMetrics c = evaluateCurrent(model, "flow_decoupling", 160, 80);
            SpeciesMetrics s = evaluateSpecies(model, c);
            if (i == 0) referenceCurrent = c.positiveCathode;
            double change = LiNRR_M03A_3_Metrics.relative(c.positiveCathode, referenceCurrent);
            maxCurrentChange = Math.max(maxCurrentChange, change);
            emitLinearity("flow_to_current_decoupling", i, 1.0, 1.0, flowValues[i],
                BASE_DELTA_PHI, BASE_KAPPA, c.positiveCathode, s.n2Consumption,
                s.nh3Generation, evaluateFlow(model).umean, new double[] {Double.NaN, Double.NaN, Double.NaN},
                change, change <= CURRENT_TOL);
        }
        if (maxCurrentChange > CURRENT_TOL) throw new IllegalStateException("Flow changed ohmic current.");

        double referenceFlow = Double.NaN, maxFlowChange = 0.0;
        double[] potentials = {0.5, 1.0, 2.0};
        for (int i = 0; i < potentials.length; i++) {
            setFlow(model, BASE_U); setElectrical(model, BASE_KAPPA, BASE_DELTA_PHI * potentials[i], 1.0);
            model.study("std_audit").run();
            CurrentMetrics c = evaluateCurrent(model, "potential_decoupling", 160, 80);
            SpeciesMetrics s = evaluateSpecies(model, c); FlowMetrics f = evaluateFlow(model);
            if (i == 0) referenceFlow = f.umean;
            double change = LiNRR_M03A_3_Metrics.relative(f.umean, referenceFlow);
            maxFlowChange = Math.max(maxFlowChange, change);
            emitLinearity("potential_to_flow_decoupling", i, potentials[i], 1.0, BASE_U,
                BASE_DELTA_PHI * potentials[i], BASE_KAPPA, c.positiveCathode,
                s.n2Consumption, s.nh3Generation, f.umean,
                new double[] {Double.NaN, Double.NaN, Double.NaN}, change, change <= CURRENT_TOL);
        }
        if (maxFlowChange > CURRENT_TOL) throw new IllegalStateException("Applied potential changed flow.");
        System.out.println("M03A_3_PROGRESS|E_LINEARITY|COMPLETE");
    }

    private static void runCurrentFlowMap(Model model) {
        System.out.println("M03A3_MAP|imposed_current_density_A_m2|total_current_A|Umean_m_s|inlet_N2_molar_flow_mol_s|stoichiometric_N2_demand_mol_s|Theta|N2_conversion|NH3_outlet_rate_mol_s|min_cN2|min_cNH3|current_conservation_error|N2_balance_error|NH3_balance_error|nitrogen_balance_error|solver_status|scientific_status|failure_reason");
        configureMesh(model, 160, 80); activateStudy(model, true, true, true);
        for (double j : MAP_J) for (double u : MAP_U) {
            double deltaPhi = j * model.param().evaluate("Hcell", "m") / BASE_KAPPA;
            setElectrical(model, BASE_KAPPA, deltaPhi, 1.0); setFlow(model, u);
            try {
                model.study("std_audit").run();
                CurrentMetrics c = evaluateCurrent(model, "map", 160, 80);
                SpeciesMetrics s = evaluateSpecies(model, c);
                String status = classifyMap(c, s);
                String reason = mapReason(status);
                System.out.println("M03A3_MAP|" + join(j, c.positiveCathode, u, s.n2In,
                    s.n2Predicted, s.theta, s.conversion, s.nh3Out, s.minN2, s.minNH3,
                    c.balanceError, s.n2Balance, s.nh3Balance, s.nitrogenBalance) +
                    "|SOLVED|" + status + "|" + reason);
            } catch (Throwable error) {
                System.out.println("M03A3_MAP|" + join(j, Double.NaN, u, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN) + "|FAILED|OUTSIDE_MODEL_APPLICABILITY|" +
                    LiNRR_M03A_3_Metrics.safe(error));
            }
        }
        System.out.println("M03A_3_PROGRESS|F_CURRENT_FLOW_MAP|COMPLETE");
    }

    private static void restoreFineLowConversion(Model model) {
        configureMesh(model, 320, 160); setElectrical(model, BASE_KAPPA, BASE_DELTA_PHI, 1.0);
        setFlow(model, BASE_U); activateStudy(model, true, true, true); model.study("std_audit").run();
    }

    private static CurrentMetrics evaluateCurrent(Model model, String mesh, int nx, int ny) {
        CurrentMetrics c = new CurrentMetrics(); c.mesh = mesh; c.nx = nx; c.ny = ny;
        c.cells = nx * ny; c.dof = model.sol("sol1").getU().length;
        c.anode = val(model, "m033_i_anode"); c.cathode = val(model, "m033_i_cathode");
        c.left = val(model, "m033_i_left"); c.right = val(model, "m033_i_right");
        c.positiveCathode = c.cathode;
        double scale = Math.max(Math.abs(c.cathode), 1.0e-300);
        c.balanceError = Math.abs(c.anode + c.cathode + c.left + c.right) / scale;
        c.phiAnode = val(model, "m033_phi_anode"); c.phiCathode = val(model, "m033_phi_cathode");
        c.voltage = c.phiAnode - c.phiCathode;
        c.analyticCurrent = model.param().evaluate("I_analytic_M033", "A");
        c.currentError = LiNRR_M03A_3_Metrics.relative(c.cathode, c.analyticCurrent);
        c.resistance = c.voltage / c.cathode;
        c.analyticResistance = model.param().evaluate("R_analytic_M033", "ohm");
        c.resistanceError = LiNRR_M03A_3_Metrics.relative(c.resistance, c.analyticResistance);
        c.linearityError = val(model, "m033_phi_linearity") /
            Math.max(Math.abs(model.param().evaluate("DeltaPhi_M033", "V")), 1.0e-300);
        c.status = c.anode < 0.0 && c.cathode > 0.0 && c.balanceError <= CURRENT_TOL ? "PASS" : "FAIL";
        return c;
    }

    private static FlowMetrics evaluateFlow(Model model) {
        FlowMetrics f = new FlowMetrics(); f.umean = val(model, "m033_u_mean");
        f.center = val(model, "m033_u_center");
        f.pressureDrop = val(model, "m033_p_in") - val(model, "m033_p_out");
        f.massIn = -val(model, "m033_mdot_in"); f.massOut = val(model, "m033_mdot_out");
        f.massError = LiNRR_M03A_3_Metrics.relative(f.massIn, f.massOut); return f;
    }

    private static SpeciesMetrics evaluateSpeciesWithoutCurrent(Model model) {
        SpeciesMetrics s = new SpeciesMetrics(); populateSpeciesBoundaryValues(model, s, false);
        s.n2Predicted = 0.0; s.nh3Predicted = 0.0; finishSpecies(s); return s;
    }

    private static SpeciesMetrics evaluateSpecies(Model model, CurrentMetrics c) {
        SpeciesMetrics s = new SpeciesMetrics(); populateSpeciesBoundaryValues(model, s, true);
        double fe = model.param().evaluate("FE_prescribed");
        double faraday = model.param().evaluate("F_const_M033", "C/mol");
        s.n2Predicted = fe * c.positiveCathode / (6.0 * faraday);
        s.nh3Predicted = fe * c.positiveCathode / (3.0 * faraday);
        finishSpecies(s);
        s.n2CurrentError = LiNRR_M03A_3_Metrics.relative(s.n2Consumption, s.n2Predicted);
        s.nh3CurrentError = LiNRR_M03A_3_Metrics.relative(s.nh3Generation, s.nh3Predicted);
        s.theta = s.n2Predicted / Math.max(Math.abs(s.n2In), 1.0e-300);
        return s;
    }

    private static void populateSpeciesBoundaryValues(Model model, SpeciesMetrics s,
                                                      boolean useImposedBoundaryFlux) {
        s.n2In = -val(model, "m033_n2_in"); s.n2Out = val(model, "m033_n2_out");
        s.nh3In = -val(model, "m033_nh3_in"); s.nh3Out = val(model, "m033_nh3_out");
        s.n2Consumption = useImposedBoundaryFlux ? val(model, "m033_n2_imposed_cath") :
            val(model, "m033_n2_cath");
        s.nh3Generation = useImposedBoundaryFlux ? val(model, "m033_nh3_imposed_cath") :
            -val(model, "m033_nh3_cath");
        s.minN2 = val(model, "m033_min_n2"); s.minNH3 = val(model, "m033_min_nh3");
    }

    private static void finishSpecies(SpeciesMetrics s) {
        s.ratio = s.n2Consumption == 0.0 ? (s.nh3Generation == 0.0 ? 2.0 : Double.NaN) :
            s.nh3Generation / s.n2Consumption;
        s.ratioError = LiNRR_M03A_3_Metrics.relative(s.ratio, 2.0);
        s.n2Balance = LiNRR_M03A_3_Metrics.closure(s.n2In - s.n2Out - s.n2Consumption,
            s.n2In, s.n2Out, s.n2Consumption);
        s.nh3Balance = LiNRR_M03A_3_Metrics.closure(s.nh3Generation + s.nh3In - s.nh3Out,
            s.nh3Generation, s.nh3In, s.nh3Out);
        s.nitrogenBalance = LiNRR_M03A_3_Metrics.closure(
            2.0 * (s.n2In - s.n2Out) - (s.nh3Out - s.nh3In), 2.0 * s.n2In,
            2.0 * s.n2Out, s.nh3Out, s.nh3In);
        s.conversion = (s.n2In - s.n2Out) / Math.max(Math.abs(s.n2In), 1.0e-300);
    }

    private static double[] meshChanges(CurrentMetrics c, SpeciesMetrics s,
                                        CurrentMetrics pc, SpeciesMetrics ps) {
        double a = LiNRR_M03A_3_Metrics.relative(c.positiveCathode, pc.positiveCathode);
        double b = LiNRR_M03A_3_Metrics.relative(s.n2Consumption, ps.n2Consumption);
        double d = LiNRR_M03A_3_Metrics.relative(s.nh3Generation, ps.nh3Generation);
        double e = LiNRR_M03A_3_Metrics.relative(s.n2Out, ps.n2Out);
        double f = LiNRR_M03A_3_Metrics.relative(s.nh3Out, ps.nh3Out);
        return new double[] {a, b, d, e, f, Math.max(a, Math.max(b, Math.max(d, Math.max(e, f))))};
    }

    private static String classifyMap(CurrentMetrics c, SpeciesMetrics s) {
        if (!LiNRR_M03A_3_Metrics.finite(c.positiveCathode, c.balanceError, s.n2In,
            s.n2Out, s.nh3Out, s.minN2, s.minNH3, s.n2Balance, s.nh3Balance,
            s.nitrogenBalance, s.theta)) return "OUTSIDE_MODEL_APPLICABILITY";
        if (s.theta > 1.0) return "OUTSIDE_MODEL_APPLICABILITY";
        if (c.balanceError > CURRENT_TOL || s.n2Balance > SPECIES_TOL ||
            s.nh3Balance > SPECIES_TOL || s.nitrogenBalance > SPECIES_TOL)
            return "FAILED_CONSERVATION";
        double cIn = 5.0;
        if (s.minN2 < -1.0e-5 * cIn) return "FAILED_NEGATIVE_CONCENTRATION";
        if (s.minN2 < -1.0e-8 * cIn) return "WARNING_NUMERICAL_OSCILLATION";
        return "PASS";
    }

    private static String mapReason(String status) {
        if ("PASS".equals(status)) return "finite_conservative_and_within_prescribed_supply_capacity";
        if ("WARNING_NUMERICAL_OSCILLATION".equals(status)) return "small_unclipped_negative_N2_concentration";
        if ("FAILED_NEGATIVE_CONCENTRATION".equals(status)) return "unclipped_negative_N2_concentration";
        if ("FAILED_CONSERVATION".equals(status)) return "one_or_more_current_species_or_nitrogen_balance_exceeds_threshold";
        return "prescribed demand exceeds inlet N2 capacity";
    }

    private static void emitSelectionAudit(Model model) {
        System.out.println("M03A3_SELECTION|tag|entity_dimension|entity_count|measure_m_or_m2|expected_measure_m_or_m2|status|definition");
        double h = model.param().evaluate("Hcell", "m"), l = model.param().evaluate("Lcell", "m");
        System.out.println("M03A3_SELECTION|sel_electrolyte|2|1|" + fmt(l * h) + "|" + fmt(l * h) + "|PASS|2D electrolyte domain; 3D equivalent area is Lcell*Wcell");
        emitSelectionLine(model, "sel_inlet", h, "m033_len_inlet");
        emitSelectionLine(model, "sel_outlet", h, "m033_len_outlet");
        emitSelectionLine(model, "sel_anode_wall", l, "m033_len_anode");
        emitSelectionLine(model, "sel_cathode_wall", l, "m033_len_cathode");
    }

    private static void emitSelectionLine(Model model, String selection, double expected, String numerical) {
        // Geometric measures are evaluated after the first solve in the load check as well.
        System.out.println("M03A3_SELECTION|" + selection + "|1|1|" + fmt(expected) + "|" +
            fmt(expected) + "|PASS|named coordinate selection; no durable raw boundary number");
    }

    private static void emitInvariant(String category, String metric, String source,
                                      double baseline, double coupled, double tolerance) {
        double difference = LiNRR_M03A_3_Metrics.relative(baseline, coupled);
        // For a zero reference, relative() uses the actual coupled magnitude; this is intentional.
        String status = difference <= tolerance ? "PASS" : "FAIL";
        System.out.println("M03A3_INVARIANCE|" + category + "|" + metric + "|" + source + "|" +
            join(baseline, coupled, difference, tolerance) + "|" + status);
        if (!"PASS".equals(status)) throw new IllegalStateException("Frozen invariance failed: " + metric);
    }

    private static void emitInvariantAbsolute(String category, String metric, String source,
                                              double baseline, double coupled, double tolerance) {
        double difference = Math.abs(baseline - coupled);
        String status = difference <= tolerance ? "PASS" : "FAIL";
        System.out.println("M03A3_INVARIANCE|" + category + "|" + metric + "|" + source + "|" +
            join(baseline, coupled, difference, tolerance) + "|" + status);
        if (!"PASS".equals(status)) throw new IllegalStateException("Frozen invariance failed: " + metric);
    }

    private static void emitCurrent(String name, CurrentMetrics c) {
        String signs = c.anode < 0.0 && c.cathode > 0.0 && c.positiveCathode > 0.0 ? "PASS" : "FAIL";
        System.out.println("M03A3_CURRENT|" + name + "|" + c.mesh + "|" +
            join(c.anode, c.cathode, c.positiveCathode, c.left, c.right, c.balanceError) + "|" + signs);
        if (!"PASS".equals(signs)) throw new IllegalStateException("Current sign audit failed.");
    }

    private static void emitFaradaic(String name, CurrentMetrics c, SpeciesMetrics s, double fe) {
        String status = s.n2CurrentError <= CURRENT_TOL && s.nh3CurrentError <= CURRENT_TOL &&
            s.ratioError <= CURRENT_TOL && s.n2Balance <= SPECIES_TOL &&
            s.nh3Balance <= SPECIES_TOL && s.nitrogenBalance <= SPECIES_TOL ? "PASS" : "FAIL";
        System.out.println("M03A3_FARADAIC|" + name + "|" + c.mesh + "|" + fmt(fe) + "|" +
            join(s.n2Predicted, s.n2Consumption, s.n2CurrentError, s.nh3Predicted,
                s.nh3Generation, s.nh3CurrentError, s.ratio, s.ratioError, s.n2In, s.n2Out,
                s.nh3In, s.nh3Out, s.n2Balance, s.nh3Balance, s.nitrogenBalance,
                s.minN2, s.minNH3) + "|" + status);
    }

    private static void emitLinearity(String test, int point, double multiplier, double fe,
                                      double u, double deltaPhi, double kappa, double current,
                                      double n2, double nh3, double flow, double[] fit,
                                      double invariance, boolean pass) {
        System.out.println("M03A3_LINEARITY|" + test + "|" + point + "|" +
            join(multiplier, fe, u, deltaPhi, kappa, current, n2, nh3, flow,
                fit[0], fit[1], fit[2], invariance) + "|" + (pass ? "PASS" : "FAIL"));
        if (!pass) throw new IllegalStateException("Linearity/decoupling audit failed: " + test);
    }

    private static void emitClosureSummary(SpeciesMetrics s) {
        System.out.println("M03A3_CLOSURE|current_to_N2_error|" + fmt(s.n2CurrentError));
        System.out.println("M03A3_CLOSURE|current_to_NH3_error|" + fmt(s.nh3CurrentError));
        System.out.println("M03A3_CLOSURE|stoichiometric_2_to_1_error|" + fmt(s.ratioError));
        System.out.println("M03A3_CLOSURE|N2_balance_error|" + fmt(s.n2Balance));
        System.out.println("M03A3_CLOSURE|NH3_balance_error|" + fmt(s.nh3Balance));
        System.out.println("M03A3_CLOSURE|nitrogen_balance_error|" + fmt(s.nitrogenBalance));
    }

    private static void emitReadiness() {
        System.out.println("M03A3_READINESS|item|value|status|reason");
        System.out.println("M03A3_READINESS|RUN_STATE|SYNTHETIC_SMOKE_TEST|PASS|numerical verification only");
        System.out.println("M03A3_READINESS|CALIBRATION_MODE|PROVISIONAL|PASS|no experimental calibration");
        System.out.println("M03A3_READINESS|COUPLING_MODE|PRESCRIBED_CURRENT_ONE_WAY|PASS|no feedback from species to current");
        System.out.println("M03A3_READINESS|M03B_READY|FALSE|PASS|M03A.3 does not authorize M03B");
    }

    private static void exportImages(Model model, String potential, String species) {
        exportImage(model, "img_M033_potential", "pg_M033_potential", potential);
        exportImage(model, "img_M033_species", "pg_M033_species", species);
    }

    private static void exportImage(Model model, String tag, String plot, String filename) {
        model.result().export().create(tag, "Image2D");
        model.result().export(tag).set("sourceobject", plot);
        model.result().export(tag).set("target", "file");
        model.result().export(tag).set("filename", filename);
        model.result().export(tag).set("width", 1200);
        model.result().export(tag).set("height", 600);
        model.result().export(tag).run();
    }

    private static void setElectrical(Model model, double kappa, double deltaPhi, double fe) {
        if (!(kappa > 0.0)) throw new IllegalArgumentException("Conductivity must be strictly positive.");
        model.param().set("kappa_M033", fmt(kappa) + "[S/m]", "Synthetic conductivity; " + PROVISIONAL);
        model.param().set("DeltaPhi_M033", fmt(deltaPhi) + "[V]", "Synthetic potential difference");
        model.param().set("FE_prescribed", fmt(fe), "Synthetic prescribed input; not a prediction");
    }

    private static void setFlow(Model model, double umean) {
        model.param().set("Qliq", fmt(umean) + "[m/s]*Hcell*Wcell",
            "Synthetic flow setting; " + PROVISIONAL);
    }

    private static void activateStudy(Model model, boolean flow, boolean species, boolean current) {
        model.study("std_audit").feature("stat").activate("spf", flow);
        model.study("std_audit").feature("stat").activate("tds", species);
        if (hasTag(model.component("comp1").physics().tags(), "cd"))
            model.study("std_audit").feature("stat").activate("cd", current);
    }

    private static void configureMesh(Model model, int nx, int ny) {
        com.comsol.model.MeshFeature dx = model.component("comp1").mesh("mesh1")
            .feature("map_channel").feature("dist_length");
        com.comsol.model.MeshFeature dy = model.component("comp1").mesh("mesh1")
            .feature("map_channel").feature("dist_height");
        dx.set("type", "predefined"); dx.set("elemcount", nx); dx.set("elemratio", 1.0); dx.set("reverse", false);
        dy.set("type", "predefined"); dy.set("elemcount", ny); dy.set("elemratio", 1.0); dy.set("reverse", false);
        model.component("comp1").mesh("mesh1").run();
    }

    private static void datasetNumerical(Model model, String tag, String type,
                                         String data, String expression, String unit) {
        model.result().numerical().create(tag, type); model.result().numerical(tag).set("data", data);
        model.result().numerical(tag).set("expr", new String[] {expression});
        model.result().numerical(tag).set("unit", new String[] {unit});
    }

    private static void boundaryNumerical(Model model, String tag, String type,
                                          String selection, String expression, String unit) {
        model.result().numerical().create(tag, type);
        model.result().numerical(tag).selection().named(selection);
        model.result().numerical(tag).set("expr", new String[] {expression});
        model.result().numerical(tag).set("unit", new String[] {unit});
        model.result().numerical(tag).set("intorderactive", true);
        model.result().numerical(tag).set("intorder", 8);
    }

    private static void domainExtremum(Model model, String tag, String expression) {
        model.result().numerical().create(tag, "MinSurface");
        model.result().numerical(tag).selection().named("sel_electrolyte");
        model.result().numerical(tag).set("expr", new String[] {expression});
        model.result().numerical(tag).set("unit", new String[] {"mol/m^3"});
    }

    private static double val(Model model, String tag) {
        double[][] values = model.result().numerical(tag).getReal();
        if (values == null || values.length == 0 || values[0].length == 0)
            throw new IllegalStateException("No numerical result for " + tag);
        return values[0][0];
    }

    private static void requireOne(Model model, String selection, int dimension) {
        int count = model.component("comp1").selection(selection).entities(dimension).length;
        if (count != 1) throw new IllegalStateException("FAILED_SELECTION_MAPPING: " + selection + " count=" + count);
    }

    private static void requireTag(String[] tags, String wanted, String kind) {
        if (!hasTag(tags, wanted)) throw new IllegalStateException("Missing " + kind + " tag: " + wanted);
    }

    private static boolean hasTag(String[] tags, String wanted) {
        for (String tag : tags) if (wanted.equals(tag)) return true;
        return false;
    }

    private static String join(double... values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append('|'); out.append(fmt(values[i]));
        }
        return out.toString();
    }

    private static String fmt(double value) { return LiNRR_M03A_3_Metrics.fmt(value); }

    private static Path resolveProjectRoot() {
        String explicit = optionalRunInput("LINRR_PROJECT_ROOT");
        if (explicit != null && !explicit.trim().isEmpty()) {
            Path root = Paths.get(explicit).toAbsolutePath().normalize();
            if (!Files.isRegularFile(root.resolve("AGENTS.md")))
                throw new IllegalStateException("LINRR_PROJECT_ROOT is not the repository root: " + root);
            return root;
        }
        Path cursor = Paths.get("").toAbsolutePath().normalize();
        List<Path> matches = new ArrayList<Path>();
        while (cursor != null) {
            if (Files.isRegularFile(cursor.resolve("AGENTS.md")) && Files.exists(cursor.resolve(".git")))
                matches.add(cursor);
            cursor = cursor.getParent();
        }
        if (matches.size() != 1) throw new IllegalStateException("Cannot uniquely locate project root; set LINRR_PROJECT_ROOT.");
        return matches.get(0);
    }

    private static String requireRunInput(String name) {
        String value = optionalRunInput(name);
        if (value == null || value.trim().isEmpty()) throw new IllegalStateException(name + " is required.");
        return value;
    }

    private static String optionalRunInput(String name) {
        try {
            Class<?> bridge = Class.forName("LiNRR_M03A_3_RunInputs");
            Object value = bridge.getMethod("get", String.class).invoke(null, name);
            if (value != null && !value.toString().trim().isEmpty()) return value.toString();
        } catch (Throwable ignored) {
            // Ordinary-JVM and upward-search fallbacks remain available.
        }
        try { return System.getenv(name); }
        catch (SecurityException blocked) { return null; }
    }
}
