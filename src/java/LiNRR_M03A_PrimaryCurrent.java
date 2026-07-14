import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M03A: decoupled primary current distribution in a uniform electrolyte.
 *
 * Equation: div(i_l)=0, i_l=-kappa_el*grad(phi_l).
 * No electrode reaction, reaction kinetics, activation overpotential, species
 * transport, concentration overpotential, SEI, Li deposition, HOR, N2
 * chemistry, or Faradaic-efficiency law is present.
 *
 * Sign convention: all reported boundary currents are integrals of i_l dot n,
 * where n points outward from the electrolyte.  Positive Icell is imposed at
 * the upper anode as current injected into the electrolyte, so the anode
 * outward integral must be negative; current exits at the grounded lower
 * cathode, so the cathode outward integral must be positive.
 */
public final class LiNRR_M03A_PrimaryCurrent {
    private static final String PROVISIONAL =
        "PROVISIONAL \u2014 numerical smoke test only";
    private static final String PROJECT_ROOT =
        "F:\\LiNRR_COMSOL\\LiNRR_COMSOL_Codex_Starter";
    private static final String MPH = PROJECT_ROOT +
        "\\models\\generated\\LiNRR_M03A_primary_current.mph";
    private static final String POTENTIAL_PNG = PROJECT_ROOT +
        "\\results\\figures\\M03A_electrolyte_potential.png";
    private static final String CURRENT_PNG = PROJECT_ROOT +
        "\\results\\figures\\M03A_current_density.png";

    private static final double CURRENT_TOL = 1.0e-6;
    private static final double VOLTAGE_TOL = 1.0e-4;
    private static final double LEAKAGE_TOL = 1.0e-8;

    private static final int[][] MESHES = {
        {40, 20}, {80, 40}, {160, 80}
    };
    private static final String[] MESH_NAMES = {"coarse", "medium", "fine"};
    private static final double[] KAPPAS = {0.1, 0.2, 0.5, 1.0, 2.0};
    private static final double[] CURRENTS_MA = {10.0, 50.0, 100.0, 250.0, 500.0};

    private LiNRR_M03A_PrimaryCurrent() {}

    private static final class Metrics {
        String name;
        int nx;
        int ny;
        long dof;
        double seconds;
        double kappa;
        double current;
        double anode;
        double cathode;
        double left;
        double right;
        double balanceError;
        double leakage;
        double leakageError;
        double phiAnode;
        double phiCathode;
        double voltage;
        double analyticVoltage;
        double voltageError;
        double jMean;
        double jMax;
        double jMin;
        double jCv;
        double resistance;
        double analyticResistance;
        String status;
        String reason;
    }

    public static Model run() throws Exception {
        Model model = ModelUtil.create("Model");
        model.label("LiNRR_M03A_primary_current | " + PROVISIONAL);
        defineParameters(model);
        buildGeometry(model);
        createSelections(model);
        addPrimaryCurrentPhysics(model);
        definePostprocessingVariables(model);
        buildMesh(model);
        createStudies(model);
        // COMSOL creates the solution dataset on the first run.  Boundary
        // result selections are added only afterward so they bind to comp1,
        // matching the proven M02.1 initialization sequence.
        setParameters(model, 0.5, 100.0);
        model.study("std_primary").run();
        createResults(model);

        List<Metrics> meshRows = runMeshAudit(model);
        Metrics selected = chooseMesh(meshRows);
        List<Metrics> scanRows = runParameterScan(model, selected.nx, selected.ny);

        configureMesh(model, selected.nx, selected.ny);
        setParameters(model, 0.5, 100.0);
        Metrics base = solveAndEvaluate(model, "base", selected.nx, selected.ny);
        enforceBaseAcceptance(base);
        printStructuredResults(meshRows, scanRows, base, selected);

        exportFieldFigures(model);
        saveModel(model);
        return model;
    }

    private static void defineParameters(Model model) {
        model.param().set("Lcell", "55[mm]", "Specified geometry: electrolyte length");
        model.param().set("Hcell", "4[mm]", "Specified geometry: electrode spacing");
        model.param().set("Wcell", "55[mm]", "Specified geometry: out-of-plane active width");
        model.param().set("T0", "298.15[K]", "Specified operating temperature");
        model.param().set("kappa_el", "0.5[S/m]", PROVISIONAL);
        model.param().set("Icell", "100[mA]", PROVISIONAL);
        model.param().set("Aelec", "Lcell*Wcell", "Derived: full planar electrode area");
        model.param().set("j_app", "Icell/Aelec", "Derived: applied current density magnitude");
        model.param().set("R_analytic", "Hcell/(kappa_el*Aelec)",
            "Derived: uniform-electrolyte analytical resistance");
        model.param().set("V_analytic", "Icell*R_analytic",
            "Derived: analytical ohmic voltage drop");
        model.param().set("sel_tol", "1e-6[mm]", "Derived coordinate-selection tolerance");
    }

    private static void buildGeometry(Model model) {
        model.component().create("comp1", true);
        model.component("comp1").label("M03A uniform electrolyte | " + PROVISIONAL);
        model.component("comp1").geom().create("geom1", 2);
        model.component("comp1").geom("geom1").label("Parameterized 2D electrolyte rectangle");
        model.component("comp1").geom("geom1").lengthUnit("mm");
        model.component("comp1").geom("geom1").create("r_electrolyte", "Rectangle");
        model.component("comp1").geom("geom1").feature("r_electrolyte")
            .label("Electrolyte domain: Lcell x Hcell");
        model.component("comp1").geom("geom1").feature("r_electrolyte")
            .set("size", new String[] {"Lcell", "Hcell"});
        createGeometryBoxSelection(model, "sel_electrolyte", "Electrolyte domain", 2,
            "-sel_tol", "Lcell+sel_tol", "-sel_tol", "Hcell+sel_tol");
        createGeometryBoxSelection(model, "sel_inlet", "Left insulated boundary", 1,
            "-sel_tol", "sel_tol", "-sel_tol", "Hcell+sel_tol");
        createGeometryBoxSelection(model, "sel_outlet", "Right insulated boundary", 1,
            "Lcell-sel_tol", "Lcell+sel_tol", "-sel_tol", "Hcell+sel_tol");
        createGeometryBoxSelection(model, "sel_anode_wall", "Upper current-injection anode", 1,
            "-sel_tol", "Lcell+sel_tol", "Hcell-sel_tol", "Hcell+sel_tol");
        createGeometryBoxSelection(model, "sel_cathode_wall", "Lower grounded cathode", 1,
            "-sel_tol", "Lcell+sel_tol", "-sel_tol", "sel_tol");
        model.component("comp1").geom("geom1").run();
    }

    private static void createGeometryBoxSelection(Model model, String tag, String label,
                                                    int dim, String xmin, String xmax,
                                                    String ymin, String ymax) {
        model.component("comp1").geom("geom1").create(tag, "BoxSelection");
        model.component("comp1").geom("geom1").feature(tag).label(label);
        model.component("comp1").geom("geom1").feature(tag).set("entitydim", dim);
        model.component("comp1").geom("geom1").feature(tag).set("condition", "inside");
        model.component("comp1").geom("geom1").feature(tag).set("xmin", xmin);
        model.component("comp1").geom("geom1").feature(tag).set("xmax", xmax);
        model.component("comp1").geom("geom1").feature(tag).set("ymin", ymin);
        model.component("comp1").geom("geom1").feature(tag).set("ymax", ymax);
    }

    private static void createSelections(Model model) {
        createBoxSelection(model, "sel_electrolyte", "Electrolyte domain", 2,
            "-sel_tol", "Lcell+sel_tol", "-sel_tol", "Hcell+sel_tol");
        createBoxSelection(model, "sel_inlet", "Left insulated boundary", 1,
            "-sel_tol", "sel_tol", "-sel_tol", "Hcell+sel_tol");
        createBoxSelection(model, "sel_outlet", "Right insulated boundary", 1,
            "Lcell-sel_tol", "Lcell+sel_tol", "-sel_tol", "Hcell+sel_tol");
        createBoxSelection(model, "sel_anode_wall", "Upper current-injection anode", 1,
            "-sel_tol", "Lcell+sel_tol", "Hcell-sel_tol", "Hcell+sel_tol");
        createBoxSelection(model, "sel_cathode_wall", "Lower grounded cathode", 1,
            "-sel_tol", "Lcell+sel_tol", "-sel_tol", "sel_tol");
    }

    private static void createBoxSelection(Model model, String tag, String label,
                                            int dim, String xmin, String xmax,
                                            String ymin, String ymax) {
        model.component("comp1").selection().create(tag, "Box");
        model.component("comp1").selection(tag).label(label);
        model.component("comp1").selection(tag).set("entitydim", dim);
        model.component("comp1").selection(tag).set("condition", "inside");
        model.component("comp1").selection(tag).set("xmin", xmin);
        model.component("comp1").selection(tag).set("xmax", xmax);
        model.component("comp1").selection(tag).set("ymin", ymin);
        model.component("comp1").selection(tag).set("ymax", ymax);
    }

    private static void addPrimaryCurrentPhysics(Model model) {
        // Confirmed by an actual COMSOL 6.4 API probe against the installed
        // Electrochemistry Module.  No guessed feature identifiers are used.
        model.component("comp1").physics().create(
            "cd", "PrimaryCurrentDistribution", "geom1");
        model.component("comp1").physics("cd")
            .label("M03A Primary Current Distribution: pure ohmic electrolyte");
        model.component("comp1").physics("cd").selection().named("geom1_sel_electrolyte");

        model.component("comp1").physics("cd").feature("ice1")
            .label("Uniform electrolyte conductivity | " + PROVISIONAL);
        model.component("comp1").physics("cd").feature("ice1")
            .set("sigmal_mat", "userdef");
        model.component("comp1").physics("cd").feature("ice1")
            .set("sigmal", "kappa_el");
        model.component("comp1").physics("cd").feature("ins1")
            .label("Default electrical insulation: left and right remain active");

        model.component("comp1").physics("cd")
            .create("anode_current", "ElectrolyteCurrent", 1);
        model.component("comp1").physics("cd").feature("anode_current")
            .selection().named("geom1_sel_anode_wall");
        model.component("comp1").physics("cd").feature("anode_current")
            .label("Upper anode: uniform current density injected into electrolyte");
        model.component("comp1").physics("cd").feature("anode_current")
            .set("IonicCurrentType", "AverageCurrentDensity");
        model.component("comp1").physics("cd").feature("anode_current")
            .set("Ial", "j_app");

        model.component("comp1").physics("cd")
            .create("cathode_ground", "ElectrolytePotential", 1);
        model.component("comp1").physics("cd").feature("cathode_ground")
            .selection().named("geom1_sel_cathode_wall");
        model.component("comp1").physics("cd").feature("cathode_ground")
            .label("Lower cathode: electrolyte potential reference 0 V");
        model.component("comp1").physics("cd").feature("cathode_ground")
            .set("philbnd", "0[V]");
    }

    private static void definePostprocessingVariables(Model model) {
        model.component("comp1").variable().create("var_current");
        model.component("comp1").variable("var_current").label(
            "M03A explicit ohmic-current audit variables");
        model.component("comp1").variable("var_current").selection()
            .named("sel_electrolyte");
        model.component("comp1").variable("var_current").set(
            "j_l_x", "-kappa_el*d(cd.phil,x)", "Ohmic electrolyte current x component");
        model.component("comp1").variable("var_current").set(
            "j_l_y", "-kappa_el*d(cd.phil,y)", "Ohmic electrolyte current y component");
        model.component("comp1").variable("var_current").set(
            "j_l_mag", "sqrt(j_l_x^2+j_l_y^2)", "Ohmic electrolyte current magnitude");
    }

    private static void buildMesh(Model model) {
        model.component("comp1").mesh().create("mesh1");
        model.component("comp1").mesh("mesh1").label("M03A independent mapped-mesh audit");
        model.component("comp1").mesh("mesh1").create("map_channel", "Map");
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .selection().named("sel_electrolyte");
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .create("dist_height", "Distribution");
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .feature("dist_height").selection().named("sel_inlet");
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .create("dist_length", "Distribution");
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .feature("dist_length").selection().named("sel_cathode_wall");
        configureMesh(model, 40, 20);
    }

    private static void configureMesh(Model model, int nx, int ny) {
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .feature("dist_length").set("numelem", nx);
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .feature("dist_length").label(nx + " elements along Lcell");
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .feature("dist_height").set("numelem", ny);
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .feature("dist_height").label(ny + " elements through Hcell");
        model.component("comp1").mesh("mesh1").label(
            "Selected mapped mesh: " + nx + " x " + ny);
        model.component("comp1").mesh("mesh1").run();
    }

    private static void createStudies(Model model) {
        model.study().create("std_primary");
        model.study("std_primary").label("M03A stationary primary-current baseline");
        model.study("std_primary").create("stat", "Stationary");
        model.study("std_primary").feature("stat").label("Pure ohmic stationary solution");
        model.study("std_primary").feature("stat").activate("cd", true);
    }

    private static void createResults(Model model) {
        model.result().create("pg_potential", "PlotGroup2D");
        model.result("pg_potential").label("M03A electrolyte potential (V)");
        model.result("pg_potential").set("titletype", "manual");
        model.result("pg_potential").set("title", "M03A electrolyte potential (V)");
        model.result("pg_potential").set("showlegends", true);
        model.result("pg_potential").set("showlegendsunit", true);
        model.result("pg_potential").create("surf_potential", "Surface");
        model.result("pg_potential").feature("surf_potential").set("expr", "cd.phil");
        model.result("pg_potential").feature("surf_potential").set("unit", "V");
        model.result("pg_potential").feature("surf_potential").set("colorlegend", true);
        model.result("pg_potential").feature("surf_potential").set("showlegendtitle", true);
        model.result("pg_potential").feature("surf_potential").set("legendtitle", "Electrolyte potential");
        model.result("pg_potential").feature("surf_potential").set("legendunit", "V");
        model.result("pg_potential").feature("surf_potential").label("Electrolyte potential");

        model.result().create("pg_current", "PlotGroup2D");
        model.result("pg_current").label("M03A electrolyte current density (A/m^2)");
        model.result("pg_current").set("titletype", "manual");
        model.result("pg_current").set("title", "M03A current density magnitude (A/m^2)");
        model.result("pg_current").set("showlegends", true);
        model.result("pg_current").set("showlegendsunit", true);
        model.result("pg_current").create("surf_current", "Surface");
        model.result("pg_current").feature("surf_current").set("expr", "j_l_mag");
        model.result("pg_current").feature("surf_current").set("unit", "A/m^2");
        model.result("pg_current").feature("surf_current").set("colorlegend", true);
        model.result("pg_current").feature("surf_current").set("showlegendtitle", true);
        model.result("pg_current").feature("surf_current").set("legendtitle", "Current density magnitude");
        model.result("pg_current").feature("surf_current").set("legendunit", "A/m^2");
        model.result("pg_current").feature("surf_current").label("Current-density magnitude");
        model.result("pg_current").create("arrow_current", "ArrowSurface");
        model.result("pg_current").feature("arrow_current")
            .set("expr", new String[] {"j_l_x", "j_l_y"});
        model.result("pg_current").feature("arrow_current").label("Current direction");

        // The required 25-point multi-kappa chart is exported from the retained
        // structured scan without interpolation.  This stable plot node exposes
        // the analytical expression for interactive GUI parameter edits.
        model.result().create("pg_voltage_scaling", "PlotGroup1D");
        model.result("pg_voltage_scaling").label(
            "M03A voltage scaling: numerical and analytical scan");
        model.result("pg_voltage_scaling").create("glob_voltage", "Global");
        model.result("pg_voltage_scaling").feature("glob_voltage").set("expr",
            new String[] {"V_analytic"});
        model.result("pg_voltage_scaling").feature("glob_voltage").set("unit",
            new String[] {"V"});

        model.result().dataset().create("cln_bulk", "CutLine2D");
        model.result().dataset("cln_bulk").label(
            "Bulk centerline: x/Lcell 0.02 to 0.98, y/Hcell 0.50");
        // CutLine2D coordinates use the geometry unit (mm).  These values are
        // exactly the requested base geometry fractions and exclude all corners.
        model.result().dataset("cln_bulk").set("genpoints",
            new double[][] {{1.1, 2.0}, {53.9, 2.0}});

        createLineIntegral(model, "int_anode", "sel_anode_wall");
        createLineIntegral(model, "int_cathode", "sel_cathode_wall");
        createLineIntegral(model, "int_left", "sel_inlet");
        createLineIntegral(model, "int_right", "sel_outlet");
        createLineAverage(model, "avg_phi_anode", "sel_anode_wall", "cd.phil", "V");
        createLineAverage(model, "avg_phi_cathode", "sel_cathode_wall", "cd.phil", "V");
        createSurfaceExtremum(model, "max_j", "MaxSurface");
        createSurfaceExtremum(model, "min_j", "MinSurface");
        createDatasetAverage(model, "bulk_mean_j", "j_l_mag", "A/m^2");
        createDatasetAverage(model, "bulk_mean_j2", "j_l_mag^2", "A^2/m^4");
    }

    private static void createLineIntegral(Model model, String tag, String selection) {
        model.result().numerical().create(tag, "IntLine");
        model.result().numerical(tag).selection().named(selection);
        model.result().numerical(tag).set("expr", new String[] {
            "(-kappa_el*(d(cd.phil,x)*nx+d(cd.phil,y)*ny))*Wcell"});
        model.result().numerical(tag).set("unit", new String[] {"A"});
        model.result().numerical(tag).set("intorderactive", true);
        model.result().numerical(tag).set("intorder", 8);
    }

    private static void createLineAverage(Model model, String tag, String selection,
                                          String expression, String unit) {
        model.result().numerical().create(tag, "AvLine");
        model.result().numerical(tag).selection().named(selection);
        model.result().numerical(tag).set("expr", new String[] {expression});
        model.result().numerical(tag).set("unit", new String[] {unit});
        model.result().numerical(tag).set("intorderactive", true);
        model.result().numerical(tag).set("intorder", 8);
    }

    private static void createSurfaceExtremum(Model model, String tag, String type) {
        model.result().numerical().create(tag, type);
        model.result().numerical(tag).selection().named("sel_electrolyte");
        model.result().numerical(tag).set("expr", new String[] {"j_l_mag"});
        model.result().numerical(tag).set("unit", new String[] {"A/m^2"});
    }

    private static void createDatasetAverage(Model model, String tag,
                                             String expression, String unit) {
        model.result().numerical().create(tag, "AvLine");
        model.result().numerical(tag).set("data", "cln_bulk");
        model.result().numerical(tag).set("expr", new String[] {expression});
        model.result().numerical(tag).set("unit", new String[] {unit});
    }

    private static List<Metrics> runMeshAudit(Model model) {
        List<Metrics> rows = new ArrayList<Metrics>();
        setParameters(model, 0.5, 100.0);
        for (int i = 0; i < MESHES.length; i++) {
            int nx = MESHES[i][0];
            int ny = MESHES[i][1];
            configureMesh(model, nx, ny);
            rows.add(solveAndEvaluate(model, MESH_NAMES[i], nx, ny));
        }
        return rows;
    }

    private static Metrics chooseMesh(List<Metrics> rows) {
        for (Metrics row : rows) {
            if ("PASS".equals(row.status)) return row;
        }
        throw new IllegalStateException("No M03A mesh passes all acceptance thresholds.");
    }

    private static List<Metrics> runParameterScan(Model model, int nx, int ny) {
        configureMesh(model, nx, ny);
        List<Metrics> rows = new ArrayList<Metrics>();
        for (double kappa : KAPPAS) {
            for (double currentMa : CURRENTS_MA) {
                setParameters(model, kappa, currentMa);
                rows.add(solveAndEvaluate(model,
                    String.format(Locale.ROOT, "k%.3g_I%.3g", kappa, currentMa), nx, ny));
            }
        }
        return rows;
    }

    private static void setParameters(Model model, double kappa, double currentMa) {
        model.param().set("kappa_el", fmt(kappa) + "[S/m]", PROVISIONAL);
        model.param().set("Icell", fmt(currentMa) + "[mA]", PROVISIONAL);
    }

    private static Metrics solveAndEvaluate(Model model, String name, int nx, int ny) {
        long start = System.nanoTime();
        model.study("std_primary").run();
        double seconds = (System.nanoTime() - start) / 1.0e9;
        Metrics m = new Metrics();
        m.name = name;
        m.nx = nx;
        m.ny = ny;
        m.seconds = seconds;
        m.dof = model.sol("sol1").getU().length;
        m.kappa = model.param().evaluate("kappa_el", "S/m");
        m.current = model.param().evaluate("Icell", "A");
        m.anode = scalar(model, "int_anode");
        m.cathode = scalar(model, "int_cathode");
        m.left = scalar(model, "int_left");
        m.right = scalar(model, "int_right");
        double scale = Math.max(Math.abs(m.current), 1.0e-30);
        m.balanceError = Math.abs(m.anode + m.cathode + m.left + m.right) / scale;
        m.leakage = Math.abs(m.left) + Math.abs(m.right);
        m.leakageError = m.leakage / scale;
        m.phiAnode = scalar(model, "avg_phi_anode");
        m.phiCathode = scalar(model, "avg_phi_cathode");
        m.voltage = m.phiAnode - m.phiCathode;
        m.analyticVoltage = model.param().evaluate("V_analytic", "V");
        m.voltageError = Math.abs(m.voltage - m.analyticVoltage) /
            Math.max(Math.abs(m.analyticVoltage), 1.0e-30);
        m.jMean = scalar(model, "bulk_mean_j");
        double jMean2 = scalar(model, "bulk_mean_j2");
        m.jMax = scalar(model, "max_j");
        m.jMin = scalar(model, "min_j");
        m.jCv = Math.sqrt(Math.max(jMean2 - m.jMean * m.jMean, 0.0)) /
            Math.max(Math.abs(m.jMean), 1.0e-30);
        m.resistance = m.voltage / m.current;
        m.analyticResistance = model.param().evaluate("R_analytic", "ohm");
        classify(m);
        return m;
    }

    private static double scalar(Model model, String tag) {
        double[][] values = model.result().numerical(tag).getReal();
        if (values == null || values.length == 0 || values[0].length == 0) {
            throw new IllegalStateException("No numerical result for " + tag);
        }
        return values[0][0];
    }

    private static void classify(Metrics m) {
        List<String> failures = new ArrayList<String>();
        if (!finite(m.balanceError) || m.balanceError > CURRENT_TOL)
            failures.add("current_balance");
        if (!finite(m.voltageError) || m.voltageError > VOLTAGE_TOL)
            failures.add("voltage_analytic");
        if (!finite(m.leakageError) || m.leakageError > LEAKAGE_TOL)
            failures.add("side_leakage");
        if (!(m.anode < 0.0 && m.cathode > 0.0)) failures.add("current_sign");
        m.status = failures.isEmpty() ? "PASS" : "FAILED";
        m.reason = failures.isEmpty() ? "all_thresholds_and_signs_pass" : join(failures, "+");
    }

    private static void enforceBaseAcceptance(Metrics m) {
        if (!"PASS".equals(m.status)) {
            throw new IllegalStateException("M03A base failed: " + m.reason +
                ", Ianode=" + m.anode + ", Icathode=" + m.cathode +
                ", balance=" + m.balanceError + ", voltage error=" + m.voltageError +
                ", leakage=" + m.leakageError);
        }
    }

    private static boolean finite(double x) {
        return !Double.isNaN(x) && !Double.isInfinite(x);
    }

    private static String join(List<String> values, String delimiter) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(delimiter);
            out.append(value);
        }
        return out.toString();
    }

    private static void printStructuredResults(List<Metrics> meshRows,
                                               List<Metrics> scanRows,
                                               Metrics base,
                                               Metrics selected) {
        System.out.println("M03A_MESH|mesh|nx|ny|dof|solve_s|anode_current_A|cathode_current_A|" +
            "current_balance_relative_error|simulated_voltage_V|analytic_voltage_V|" +
            "voltage_relative_error|bulk_mean_current_density_A_m2|bulk_current_density_cv|" +
            "side_leakage_relative_error|status|reason");
        for (Metrics m : meshRows) {
            System.out.println(String.format(Locale.ROOT,
                "M03A_MESH|%s|%d|%d|%d|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%s|%s",
                m.name, m.nx, m.ny, m.dof, m.seconds, m.anode, m.cathode,
                m.balanceError, m.voltage, m.analyticVoltage, m.voltageError,
                m.jMean, m.jCv, m.leakageError, m.status, m.reason));
        }

        System.out.println("M03A_SCAN|kappa_el_S_m|Icell_mA|j_app_A_m2|simulated_voltage_V|" +
            "analytic_voltage_V|voltage_relative_error|effective_resistance_ohm|" +
            "current_balance_relative_error|side_leakage_relative_error|status|reason");
        for (Metrics m : scanRows) {
            double jApp = m.current / (0.055 * 0.055);
            System.out.println(String.format(Locale.ROOT,
                "M03A_SCAN|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%s|%s",
                m.kappa, m.current * 1000.0, jApp, m.voltage, m.analyticVoltage,
                m.voltageError, m.resistance, m.balanceError, m.leakageError,
                m.status, m.reason));
        }

        System.out.println("M03A_SUMMARY|metric|value|unit|status|definition");
        emitSummary("anode_total_current", base.anode, "A",
            base.anode < 0 ? "PASS" : "FAILED", "outward-normal integral; injection must be negative");
        emitSummary("cathode_total_current", base.cathode, "A",
            base.cathode > 0 ? "PASS" : "FAILED", "outward-normal integral; exit must be positive");
        emitSummary("left_insulation_current", base.left, "A", "AUDIT", "signed outward current");
        emitSummary("right_insulation_current", base.right, "A", "AUDIT", "signed outward current");
        emitSummary("side_leakage_current", base.leakage, "A",
            base.leakageError <= LEAKAGE_TOL ? "PASS" : "FAILED", "abs(left)+abs(right)");
        emitSummary("side_leakage_relative_error", base.leakageError, "1",
            base.leakageError <= LEAKAGE_TOL ? "PASS" : "FAILED", "leakage/max(abs(Icell),1e-30 A)");
        emitSummary("current_balance_relative_error", base.balanceError, "1",
            base.balanceError <= CURRENT_TOL ? "PASS" : "FAILED", "abs(sum signed boundary currents)/abs(Icell)");
        emitSummary("simulated_voltage_drop", base.voltage, "V", "COMPUTED", "average anode phi_l minus average cathode phi_l");
        emitSummary("analytic_voltage_drop", base.analyticVoltage, "V", "DERIVED", "Icell*Hcell/(kappa_el*Aelec)");
        emitSummary("voltage_analytic_relative_error", base.voltageError, "1",
            base.voltageError <= VOLTAGE_TOL ? "PASS" : "FAILED", "abs(Vsim-Vanalytic)/abs(Vanalytic)");
        emitSummary("bulk_mean_current_density", base.jMean, "A/m^2", "COMPUTED",
            "centerline y/H=0.5 over x/L=0.02..0.98; corners excluded");
        emitSummary("maximum_current_density", base.jMax, "A/m^2", "COMPUTED", "whole electrolyte domain");
        emitSummary("minimum_current_density", base.jMin, "A/m^2", "COMPUTED", "whole electrolyte domain");
        emitSummary("bulk_current_density_cv", base.jCv, "1", "COMPUTED", "interior standard deviation divided by mean");
        emitSummary("effective_resistance", base.resistance, "ohm", "COMPUTED", "simulated voltage/Icell");
        emitSummary("analytic_resistance", base.analyticResistance, "ohm", "DERIVED", "Hcell/(kappa_el*Aelec)");
        System.out.println("M03A_SELECTED|" + selected.name + "|" + selected.nx + "|" + selected.ny);
    }

    private static void emitSummary(String metric, double value, String unit,
                                    String status, String definition) {
        System.out.println("M03A_SUMMARY|" + metric + "|" + fmt(value) + "|" + unit +
            "|" + status + "|" + definition);
    }

    private static void exportFieldFigures(Model model) {
        imageExport(model, "img_potential", "pg_potential", POTENTIAL_PNG);
        imageExport(model, "img_current", "pg_current", CURRENT_PNG);
    }

    private static void imageExport(Model model, String tag, String source, String path) {
        model.result().export().create(tag, "Image2D");
        model.result().export(tag).set("sourceobject", source);
        model.result().export(tag).set("target", "file");
        model.result().export(tag).set("filename", path);
        model.result().export(tag).set("width", 1200);
        model.result().export(tag).set("height", 500);
        model.result().export(tag).run();
    }

    private static void saveModel(Model model) throws IOException {
        model.save(MPH);
    }

    private static String fmt(double x) {
        return String.format(Locale.ROOT, "%.12g", x);
    }

    public static void main(String[] args) throws Exception {
        run();
    }
}
