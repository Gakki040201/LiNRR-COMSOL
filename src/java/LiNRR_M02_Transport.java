import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.io.IOException;
import java.util.Locale;

/**
 * M02: stationary N2/NH3 convection-diffusion with a phenomenological
 * cathode boundary flux. All transport and kinetic values are provisional.
 */
public final class LiNRR_M02_Transport {
    private static final String PROVISIONAL =
        "PROVISIONAL \u2014 numerical smoke test only";
    private static final double CONSERVATION_TOL = 1e-4;
    // Numerical undershoots smaller than 1e-5 of the inlet concentration are
    // reported but classified as insignificant; concentrations are never clipped.
    private static final double NEGATIVE_REL_TOL = 1e-5;
    private static final double[] Q_SCAN = {0.25, 0.5, 1.0, 2.0, 4.0};
    private static final double[] K_SCAN = {1e-6, 3e-6, 1e-5, 3e-5, 1e-4};
    private static final int Q=0,K=1,N2IN=2,N2OUT=3,N2BOUND=4,NH3BOUND=5,
        NH3IN=6,NH3OUT=7,CONVERSION=8,NERR_BOUND=9,NERR_OVERALL=10,
        N2ERR=11,NH3ERR=12,MIN_N2=13,MAX_N2=14,MIN_NH3=15,MAX_NH3=16,
        MIN_CATHODE_N2=17,OUT_AVG_N2=18,OUT_AVG_NH3=19,N2_WALL=20,
        NH3_WALL=21,RE=22,PE_N2=23,PE_NH3=24,DA=25;

    private LiNRR_M02_Transport() {}

    public static Model run() throws Exception {
        Model model = ModelUtil.create("Model");
        model.label("LiNRR_M02_transport | " + PROVISIONAL);
        defineParameters(model);
        buildGeometry(model);
        createSelections(model);
        assignMaterials(model);
        addFlowPhysics(model);
        addSpeciesTransport(model);
        buildMesh(model);
        createStudies(model);

        model.param().set("reaction_on", "0", "Study switch: 0 for Study A, 1 for Study B");
        model.study("std_A").run();
        createNumericalEvaluations(model);
        setEvaluationDataset(model, "dset1");
        double[] studyA = evaluate(model, 1.0, 0.0);
        checkStudyA(studyA);
        printSummaryHeader();
        printMetrics("M02_BASE", "Study_A_no_reaction", studyA);

        model.param().set("reaction_on", "1");
        runContinuation(model, 1.0, 1e-5);
        setEvaluationDataset(model, "dset2");
        double[] studyB = evaluate(model, 1.0, 1e-5);
        printMetrics("M02_BASE", "Study_B_reactive", studyB);
        checkStudyB(studyB);

        printScanHeader();
        runParameterScan(model);

        // Restore the requested base case for the saved MPH and figures.
        runContinuation(model, 1.0, 1e-5);
        setEvaluationDataset(model, "dset2");
        double[] finalBase = evaluate(model, 1.0, 1e-5);
        checkStudyB(finalBase);
        createResults(model);
        exportFigures(model);
        saveModel(model);
        return model;
    }

    private static void defineParameters(Model model) {
        model.param().set("Lcell", "55[mm]", "Specified electrolyte-channel length");
        model.param().set("Hcell", "4[mm]", "Specified electrolyte-layer thickness");
        model.param().set("Wcell", "55[mm]", "Specified out-of-plane active width");
        model.param().set("T0", "298.15[K]", "Specified operating temperature");
        model.param().set("Qliq", "1[cm^3/min]", "Specified base liquid flow rate");
        model.param().set("rho_el", "900[kg/m^3]", PROVISIONAL);
        model.param().set("mu_el", "3[mPa*s]", PROVISIONAL);
        model.param().set("DN2", "2e-9[m^2/s]", PROVISIONAL);
        model.param().set("DNH3", "2e-9[m^2/s]", PROVISIONAL);
        model.param().set("cN2_in", "5[mol/m^3]", PROVISIONAL);
        model.param().set("kN2", "1e-5[m/s]", PROVISIONAL +
            "; phenomenological first-order wall-consumption coefficient");
        model.param().set("reaction_on", "0", "Dimensionless Study A/B switch");
        model.param().set("uin", "Qliq/(Hcell*Wcell)",
            "Cross-sectional volumetric mean velocity");
        model.param().set("Re", "rho_el*uin*Hcell/mu_el",
            "Reynolds number based on Hcell and uin");
        model.param().set("PeN2", "uin*Lcell/DN2",
            "Axial Peclet number based on Lcell and uin");
        model.param().set("PeNH3", "uin*Lcell/DNH3",
            "Axial Peclet number based on Lcell and uin");
        model.param().set("DaN2", "kN2*Lcell/(uin*Hcell)",
            "Wall-reaction Damkohler number kN2*Lcell/(uin*Hcell), one reactive wall");
        model.param().set("sel_tol", "1e-6[mm]", "Coordinate-selection tolerance");
    }

    private static void buildGeometry(Model model) {
        model.component().create("comp1", true);
        model.component("comp1").label("M02 2D liquid channel | " + PROVISIONAL);
        model.component("comp1").geom().create("geom1", 2);
        model.component("comp1").geom("geom1").lengthUnit("mm");
        model.component("comp1").geom("geom1").create("r_channel", "Rectangle");
        model.component("comp1").geom("geom1").feature("r_channel")
            .set("size", new String[] {"Lcell", "Hcell"});
        model.component("comp1").geom("geom1").run();
    }

    private static void createSelections(Model model) {
        createBoxSelection(model, "sel_electrolyte", "Electrolyte domain", 2,
            "-sel_tol", "Lcell+sel_tol", "-sel_tol", "Hcell+sel_tol");
        createBoxSelection(model, "sel_inlet", "Inlet boundary (left)", 1,
            "-sel_tol", "sel_tol", "-sel_tol", "Hcell+sel_tol");
        createBoxSelection(model, "sel_outlet", "Outlet boundary (right)", 1,
            "Lcell-sel_tol", "Lcell+sel_tol", "-sel_tol", "Hcell+sel_tol");
        createBoxSelection(model, "sel_anode_wall", "Anode wall (upper)", 1,
            "-sel_tol", "Lcell+sel_tol", "Hcell-sel_tol", "Hcell+sel_tol");
        createBoxSelection(model, "sel_cathode_wall", "Cathode wall (lower)", 1,
            "-sel_tol", "Lcell+sel_tol", "-sel_tol", "sel_tol");
    }

    private static void createBoxSelection(Model model, String tag, String label,
                                           int dimension, String xmin, String xmax,
                                           String ymin, String ymax) {
        model.component("comp1").selection().create(tag, "Box");
        model.component("comp1").selection(tag).label(label);
        model.component("comp1").selection(tag).set("entitydim", dimension);
        model.component("comp1").selection(tag).set("condition", "inside");
        model.component("comp1").selection(tag).set("xmin", xmin);
        model.component("comp1").selection(tag).set("xmax", xmax);
        model.component("comp1").selection(tag).set("ymin", ymin);
        model.component("comp1").selection(tag).set("ymax", ymax);
    }

    private static void assignMaterials(Model model) {
        model.component("comp1").material().create("mat_electrolyte", "Common");
        model.component("comp1").material("mat_electrolyte")
            .label("Electrolyte | " + PROVISIONAL);
        model.component("comp1").material("mat_electrolyte")
            .selection().named("sel_electrolyte");
        model.component("comp1").material("mat_electrolyte")
            .propertyGroup("def").set("density", "rho_el");
        model.component("comp1").material("mat_electrolyte")
            .propertyGroup("def").set("dynamicviscosity", "mu_el");
    }

    private static void addFlowPhysics(Model model) {
        model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
        model.component("comp1").physics("spf").selection().named("sel_electrolyte");
        model.component("comp1").physics("spf").create("wall_anode", "Wall", 1);
        model.component("comp1").physics("spf").feature("wall_anode")
            .selection().named("sel_anode_wall");
        model.component("comp1").physics("spf").create("wall_cathode", "Wall", 1);
        model.component("comp1").physics("spf").feature("wall_cathode")
            .selection().named("sel_cathode_wall");
        model.component("comp1").physics("spf").create("inlet", "Inlet", 1);
        model.component("comp1").physics("spf").feature("inlet")
            .selection().named("sel_inlet");
        model.component("comp1").physics("spf").feature("inlet")
            .set("BoundaryCondition", "LaminarInflow");
        model.component("comp1").physics("spf").feature("inlet").set("Uav", "uin");
        model.component("comp1").physics("spf").create("outlet", "Outlet", 1);
        model.component("comp1").physics("spf").feature("outlet")
            .selection().named("sel_outlet");
        model.component("comp1").physics("spf").feature("outlet").set("p0", "0[Pa]");
    }

    private static void addSpeciesTransport(Model model) {
        model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1",
            new String[] {"cN2", "cNH3"});
        model.component("comp1").physics("tds")
            .label("N2 and NH3 transport | " + PROVISIONAL);
        model.component("comp1").physics("tds").selection().named("sel_electrolyte");
        model.component("comp1").physics("tds").feature("cdm1")
            .set("D_cN2_mat", "userdef");
        model.component("comp1").physics("tds").feature("cdm1")
            .set("D_cN2", "DN2");
        model.component("comp1").physics("tds").feature("cdm1")
            .set("D_cNH3_mat", "userdef");
        model.component("comp1").physics("tds").feature("cdm1")
            .set("D_cNH3", "DNH3");
        model.component("comp1").physics("tds").feature("cdm1")
            .set("u", new String[][] {{"u"}, {"v"}, {"0"}});

        model.component("comp1").physics("tds").feature("init1")
            .set("initc", new String[] {"cN2_in", "0[mol/m^3]"});
        model.component("comp1").physics("tds").create("conc_in", "Concentration", 1);
        model.component("comp1").physics("tds").feature("conc_in")
            .selection().named("sel_inlet");
        model.component("comp1").physics("tds").feature("conc_in")
            .set("species", new int[] {1, 1});
        model.component("comp1").physics("tds").feature("conc_in")
            .set("c0", new String[] {"cN2_in", "0[mol/m^3]"});
        model.component("comp1").physics("tds").create("outflow", "Outflow", 1);
        model.component("comp1").physics("tds").feature("outflow")
            .selection().named("sel_outlet");
        model.component("comp1").physics("tds").create("noflux_anode", "NoFlux", 1);
        model.component("comp1").physics("tds").feature("noflux_anode")
            .selection().named("sel_anode_wall");

        model.component("comp1").variable().create("var_rxn");
        model.component("comp1").variable("var_rxn").label(
            "Phenomenological cathode rates | " + PROVISIONAL);
        model.component("comp1").variable("var_rxn").selection().named("sel_cathode_wall");
        model.component("comp1").variable("var_rxn").set("rN2", "kN2*cN2",
            "Positive N2 consumption rate per cathode area");
        model.component("comp1").variable("var_rxn").set("rNH3", "2*rN2",
            "NH3 generation rate; enforces N-atom stoichiometry");

        model.component("comp1").physics("tds").create("flux_cathode", "FluxBoundary", 1);
        model.component("comp1").physics("tds").feature("flux_cathode")
            .selection().named("sel_cathode_wall");
        model.component("comp1").physics("tds").feature("flux_cathode")
            .set("species", new int[] {1, 1});
        model.component("comp1").physics("tds").feature("flux_cathode")
            .set("FluxType", "GeneralInwardFlux");
        // J0 is positive INTO the modeled liquid. N2 consumption is therefore
        // negative inward flux, while NH3 generation is positive inward flux.
        model.component("comp1").physics("tds").feature("flux_cathode")
            .set("J0", new String[] {"-reaction_on*rN2", "reaction_on*rNH3"});
    }

    private static void buildMesh(Model model) {
        model.component("comp1").mesh().create("mesh1");
        model.component("comp1").mesh("mesh1")
            .label("M02 mapped mesh: 100 x 200 inherited from accepted M01");
        model.component("comp1").mesh("mesh1").create("map_channel", "Map");
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .selection().named("sel_electrolyte");
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .create("dist_height", "Distribution");
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .feature("dist_height").selection().named("sel_inlet");
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .feature("dist_height").set("numelem", 200);
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .create("dist_length", "Distribution");
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .feature("dist_length").selection().named("sel_cathode_wall");
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .feature("dist_length").set("numelem", 100);
        model.component("comp1").mesh("mesh1").run();
    }

    private static void createStudies(Model model) {
        model.study().create("std_A");
        model.study("std_A").label("Study A: no-reaction transport benchmark");
        model.study("std_A").create("stat", "Stationary");
        model.study("std_A").feature("stat").label("Stationary flow and no-reaction species");
        model.study("std_A").feature("stat").activate("spf", true);
        model.study("std_A").feature("stat").activate("tds", true);
        model.study().create("std_B");
        model.study("std_B").label("Study B: cathode N2 consumption and NH3 generation");
        model.study("std_B").create("stat", "Stationary");
        model.study("std_B").feature("stat").label("Stationary flow and reactive species");
        model.study("std_B").feature("stat").activate("spf", true);
        model.study("std_B").feature("stat").activate("tds", true);
    }

    private static void createNumericalEvaluations(Model model) {
        createIntegral(model, "nN2_in_signed", "tds.ntflux_cN2*Wcell", "sel_inlet");
        createIntegral(model, "nN2_out_signed", "tds.ntflux_cN2*Wcell", "sel_outlet");
        createIntegral(model, "nNH3_in_signed", "tds.ntflux_cNH3*Wcell", "sel_inlet");
        createIntegral(model, "nNH3_out_signed", "tds.ntflux_cNH3*Wcell", "sel_outlet");
        createIntegral(model, "nN2_boundary", "rN2*Wcell", "sel_cathode_wall");
        createIntegral(model, "nNH3_boundary", "rNH3*Wcell", "sel_cathode_wall");
        createIntegral(model, "nN2_wall_flux", "tds.ntflux_cN2*Wcell", "sel_cathode_wall");
        createIntegral(model, "nNH3_wall_flux", "tds.ntflux_cNH3*Wcell", "sel_cathode_wall");
        createExtremum(model, "min_cN2", "MinSurface", "cN2", "sel_electrolyte");
        createExtremum(model, "max_cN2", "MaxSurface", "cN2", "sel_electrolyte");
        createExtremum(model, "min_cNH3", "MinSurface", "cNH3", "sel_electrolyte");
        createExtremum(model, "max_cNH3", "MaxSurface", "cNH3", "sel_electrolyte");
        createExtremum(model, "min_cathode_cN2", "MinLine", "cN2", "sel_cathode_wall");
        createAverage(model, "avg_out_cN2", "cN2", "sel_outlet");
        createAverage(model, "avg_out_cNH3", "cNH3", "sel_outlet");
    }

    private static void createIntegral(Model model, String tag, String expr, String selection) {
        model.result().numerical().create(tag, "IntLine");
        model.result().numerical(tag).selection().named(selection);
        model.result().numerical(tag).set("expr", new String[] {expr});
        model.result().numerical(tag).set("unit", new String[] {"mol/s"});
        model.result().numerical(tag).set("intorderactive", true);
        model.result().numerical(tag).set("intorder", 8);
    }

    private static void createExtremum(Model model, String tag, String type,
                                       String expr, String selection) {
        model.result().numerical().create(tag, type);
        model.result().numerical(tag).selection().named(selection);
        model.result().numerical(tag).set("expr", new String[] {expr});
        model.result().numerical(tag).set("unit", new String[] {"mol/m^3"});
    }

    private static void createAverage(Model model, String tag, String expr, String selection) {
        model.result().numerical().create(tag, "AvLine");
        model.result().numerical(tag).selection().named(selection);
        model.result().numerical(tag).set("expr", new String[] {expr});
        model.result().numerical(tag).set("unit", new String[] {"mol/m^3"});
        model.result().numerical(tag).set("intorderactive", true);
        model.result().numerical(tag).set("intorder", 8);
    }

    private static void setEvaluationDataset(Model model, String dataset) {
        for (String tag : model.result().numerical().tags()) {
            model.result().numerical(tag).set("data", dataset);
        }
    }

    private static void runContinuation(Model model, double q, double targetK) {
        model.param().set("Qliq", format(q) + "[cm^3/min]");
        double[] continuation = targetK <= 1e-6 ? new double[] {targetK} :
            targetK <= 3e-6 ? new double[] {1e-6, targetK} :
            targetK <= 1e-5 ? new double[] {1e-6, 3e-6, targetK} :
            targetK <= 3e-5 ? new double[] {1e-6, 3e-6, 1e-5, targetK} :
            new double[] {1e-6, 3e-6, 1e-5, 3e-5, targetK};
        for (double k : continuation) {
            model.param().set("kN2", format(k) + "[m/s]");
            model.study("std_B").run();
        }
    }

    private static void runParameterScan(Model model) {
        for (double q : Q_SCAN) {
            for (double k : K_SCAN) {
                try {
                    // k is traversed monotonically for each flow rate, providing
                    // continuation without silently skipping any scan point.
                    model.param().set("Qliq", format(q) + "[cm^3/min]");
                    model.param().set("kN2", format(k) + "[m/s]");
                    model.study("std_B").run();
                    setEvaluationDataset(model, "dset2");
                    double[] m = evaluate(model, q, k);
                    String failure = scanFailure(m);
                    printScan(m, failure == null ? "PASS" : "FAILED:" + failure);
                } catch (Exception ex) {
                    printFailedScan(q, k, ex.getClass().getSimpleName() + ":" +
                        safe(ex.getMessage()));
                }
            }
        }
    }

    private static double[] evaluate(Model model, double q, double k) {
        double[] m = new double[26];
        m[Q] = q; m[K] = k;
        m[N2IN] = -scalar(model, "nN2_in_signed");
        m[N2OUT] = scalar(model, "nN2_out_signed");
        m[NH3IN] = -scalar(model, "nNH3_in_signed");
        m[NH3OUT] = scalar(model, "nNH3_out_signed");
        m[N2BOUND] = scalar(model, "nN2_boundary") * model.param().evaluate("reaction_on");
        m[NH3BOUND] = scalar(model, "nNH3_boundary") * model.param().evaluate("reaction_on");
        m[N2_WALL] = scalar(model, "nN2_wall_flux");
        m[NH3_WALL] = scalar(model, "nNH3_wall_flux");
        m[CONVERSION] = (m[N2IN] - m[N2OUT]) / Math.max(Math.abs(m[N2IN]), 1e-30);
        m[NERR_BOUND] = Math.abs(2*m[N2BOUND]-m[NH3BOUND]) / Math.max(Math.abs(m[NH3BOUND]),1e-30);
        m[NERR_OVERALL] = Math.abs(2*(m[N2IN]-m[N2OUT])-(m[NH3OUT]-m[NH3IN])) /
            Math.max(Math.abs(2*m[N2IN]),1e-30);
        m[N2ERR] = Math.abs(m[N2IN]-m[N2OUT]-m[N2BOUND]) / Math.max(Math.abs(m[N2IN]),1e-30);
        m[NH3ERR] = Math.abs(m[NH3OUT]-m[NH3IN]-m[NH3BOUND]) /
            Math.max(Math.max(Math.abs(m[NH3BOUND]),Math.abs(m[NH3OUT])),1e-30);
        m[MIN_N2]=scalar(model,"min_cN2"); m[MAX_N2]=scalar(model,"max_cN2");
        m[MIN_NH3]=scalar(model,"min_cNH3"); m[MAX_NH3]=scalar(model,"max_cNH3");
        m[MIN_CATHODE_N2]=scalar(model,"min_cathode_cN2");
        m[OUT_AVG_N2]=scalar(model,"avg_out_cN2");
        m[OUT_AVG_NH3]=scalar(model,"avg_out_cNH3");
        m[RE]=model.param().evaluate("Re"); m[PE_N2]=model.param().evaluate("PeN2");
        m[PE_NH3]=model.param().evaluate("PeNH3");
        m[DA]=k==0 ? 0 : model.param().evaluate("DaN2");
        return m;
    }

    private static double scalar(Model model, String tag) {
        double[][] values = model.result().numerical(tag).getReal();
        if (values == null || values.length == 0 || values[0].length == 0) {
            throw new IllegalStateException("No value from numerical node " + tag);
        }
        return values[0][0];
    }

    private static void checkStudyA(double[] m) {
        if (m[N2ERR] > CONSERVATION_TOL ||
            Math.abs(m[MIN_N2]-5.0)>5e-4 || Math.abs(m[MAX_N2]-5.0)>5e-4 ||
            Math.abs(m[MIN_NH3])>5e-8 || Math.abs(m[MAX_NH3])>5e-8) {
            throw new IllegalStateException("Study A no-reaction benchmark failed: " +
                "N2 conservation="+m[N2ERR]+", cN2 range="+m[MIN_N2]+".."+m[MAX_N2]+
                ", cNH3 range="+m[MIN_NH3]+".."+m[MAX_NH3]);
        }
    }

    private static void checkStudyB(double[] m) {
        String failure = scanFailure(m);
        if (failure != null) {
            throw new IllegalStateException("Study B base case failed: " + failure);
        }
        if (!(m[N2_WALL]>0) || !(m[NH3_WALL]<0)) {
            throw new IllegalStateException("Cathode total-flux signs are wrong: N2=" +
                m[N2_WALL] + ", NH3=" + m[NH3_WALL]);
        }
        double n2FluxError=Math.abs(m[N2_WALL]-m[N2BOUND])/Math.max(Math.abs(m[N2BOUND]),1e-30);
        double nh3FluxError=Math.abs(-m[NH3_WALL]-m[NH3BOUND])/Math.max(Math.abs(m[NH3BOUND]),1e-30);
        if (n2FluxError > CONSERVATION_TOL || nh3FluxError > CONSERVATION_TOL) {
            throw new IllegalStateException("Cathode flux implementation mismatch: N2=" +
                n2FluxError + ", NH3=" + nh3FluxError);
        }
    }

    private static String scanFailure(double[] m) {
        if (!isFinite(m)) return "NONFINITE";
        if (m[NERR_BOUND]>CONSERVATION_TOL) return "BOUNDARY_N_BALANCE";
        if (m[NERR_OVERALL]>CONSERVATION_TOL) return "OVERALL_N_BALANCE";
        if (m[MIN_N2]<-NEGATIVE_REL_TOL*5.0 || m[MIN_NH3]<-NEGATIVE_REL_TOL*5.0)
            return "NEGATIVE_CONCENTRATION";
        return null;
    }

    private static boolean isFinite(double[] m) {
        for (double value : m) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static void printSummaryHeader() {
        System.out.println("M02_BASE|study|Qliq_cm3_min|kN2_m_s|N2_in_mol_s|N2_out_mol_s|" +
            "N2_boundary_consumed_mol_s|NH3_boundary_generated_mol_s|NH3_in_mol_s|" +
            "NH3_out_mol_s|N2_conversion|N_error_boundary|N_error_overall|" +
            "N2_species_error|NH3_species_error|min_cN2_mol_m3|max_cN2_mol_m3|" +
            "min_cNH3_mol_m3|max_cNH3_mol_m3|min_cathode_cN2_mol_m3|" +
            "outlet_avg_cN2_mol_m3|outlet_avg_cNH3_mol_m3|N2_wall_outward_mol_s|" +
            "NH3_wall_outward_mol_s|Re|PeN2|PeNH3|DaN2|status");
    }

    private static void printMetrics(String prefix, String study, double[] m) {
        System.out.println(prefix + "|" + study + "|" + values(m) + "|PASS");
    }

    private static String values(double[] m) {
        return String.format(Locale.ROOT,
            "%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|" +
            "%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|" +
            "%.12g|%.12g|%.12g|%.12g|%.12g|%.12g",
            m[Q],m[K],m[N2IN],m[N2OUT],m[N2BOUND],m[NH3BOUND],m[NH3IN],m[NH3OUT],
            m[CONVERSION],m[NERR_BOUND],m[NERR_OVERALL],m[N2ERR],m[NH3ERR],
            m[MIN_N2],m[MAX_N2],m[MIN_NH3],m[MAX_NH3],m[MIN_CATHODE_N2],
            m[OUT_AVG_N2],m[OUT_AVG_NH3],m[N2_WALL],m[NH3_WALL],m[RE],m[PE_N2],
            m[PE_NH3],m[DA]);
    }

    private static void printScanHeader() {
        System.out.println("M02_SCAN|Qliq_cm3_min|kN2_m_s|N2_conversion|NH3_generation_mol_s|" +
            "outlet_avg_cN2_mol_m3|outlet_avg_cNH3_mol_m3|min_cathode_cN2_mol_m3|" +
            "N_error_boundary|N_error_overall|min_cN2_mol_m3|min_cNH3_mol_m3|status");
    }

    private static void printScan(double[] m, String status) {
        System.out.println(String.format(Locale.ROOT,
            "M02_SCAN|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%s",
            m[Q],m[K],m[CONVERSION],m[NH3BOUND],m[OUT_AVG_N2],m[OUT_AVG_NH3],
            m[MIN_CATHODE_N2],m[NERR_BOUND],m[NERR_OVERALL],m[MIN_N2],m[MIN_NH3],status));
    }

    private static void printFailedScan(double q, double k, String reason) {
        System.out.println(String.format(Locale.ROOT,
            "M02_SCAN|%.12g|%.12g|NaN|NaN|NaN|NaN|NaN|NaN|NaN|NaN|NaN|FAILED:%s",
            q, k, safe(reason)));
    }

    private static String safe(String value) {
        if (value == null) return "NO_MESSAGE";
        return value.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.12g", value);
    }

    private static void createResults(Model model) {
        model.result().create("pg_N2", "PlotGroup2D");
        model.result("pg_N2").label("M02 N2 concentration | " + PROVISIONAL);
        model.result("pg_N2").set("data", "dset2");
        model.result("pg_N2").create("surf", "Surface");
        model.result("pg_N2").feature("surf").set("expr", "cN2");
        model.result("pg_N2").feature("surf").set("unit", "mol/m^3");

        model.result().create("pg_NH3", "PlotGroup2D");
        model.result("pg_NH3").label("M02 NH3 concentration | " + PROVISIONAL);
        model.result("pg_NH3").set("data", "dset2");
        model.result("pg_NH3").create("surf", "Surface");
        model.result("pg_NH3").feature("surf").set("expr", "cNH3");
        model.result("pg_NH3").feature("surf").set("unit", "mol/m^3");

        model.result().create("pg_cathode", "PlotGroup1D");
        model.result("pg_cathode").label("M02 cathode concentration profiles");
        model.result("pg_cathode").set("data", "dset2");
        model.result("pg_cathode").create("ln_N2", "LineGraph");
        model.result("pg_cathode").feature("ln_N2").selection().named("sel_cathode_wall");
        model.result("pg_cathode").feature("ln_N2").set("expr", "cN2");
        model.result("pg_cathode").feature("ln_N2").set("unit", "mol/m^3");
        model.result("pg_cathode").create("ln_NH3", "LineGraph");
        model.result("pg_cathode").feature("ln_NH3").selection().named("sel_cathode_wall");
        model.result("pg_cathode").feature("ln_NH3").set("expr", "cNH3");
        model.result("pg_cathode").feature("ln_NH3").set("unit", "mol/m^3");
    }

    private static void exportFigures(Model model) {
        imageExport(model, "img_N2", "Image2D", "pg_N2",
            "results/figures/M02_N2_concentration.png");
        imageExport(model, "img_NH3", "Image2D", "pg_NH3",
            "results/figures/M02_NH3_concentration.png");
        imageExport(model, "img_cathode", "Image1D", "pg_cathode",
            "results/figures/M02_cathode_profiles.png");
    }

    private static void imageExport(Model model, String tag, String type,
                                    String source, String filename) {
        model.result().export().create(tag, type);
        model.result().export(tag).set("sourceobject", source);
        model.result().export(tag).set("target", "file");
        model.result().export(tag).set("filename", filename);
        model.result().export(tag).run();
    }

    private static void saveModel(Model model) throws IOException {
        model.save("models/generated/LiNRR_M02_transport.mph");
    }

    public static void main(String[] args) throws Exception {
        run();
    }

}
