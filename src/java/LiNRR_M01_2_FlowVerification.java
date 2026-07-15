import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.io.IOException;
import java.util.Locale;

/**
 * M01.2 independent derivative of the frozen M01 laminar-flow MPH.
 *
 * Performs numerical closure against the fully developed two-dimensional
 * parallel-plate solution. All inputs remain PROVISIONAL, and passing means
 * numerical verification only, never experimental validation.
 */
public final class LiNRR_M01_2_FlowVerification {
    private static final String SOURCE = "runs/latest/M01_2_frozen_source.mph";
    private static final String PROJECT_ROOT =
        "F:/LiNRR_COMSOL/worktrees/LiNRR_PreGeometry/";
    private static final String OUTPUT =
        "models/generated/LiNRR_M01_2_flow_verification.mph";
    private static final String PROVISIONAL =
        "PROVISIONAL - numerical verification only";
    private static final int[][] MESHES = {{40, 20}, {80, 40}, {160, 80}};
    private static final String[] MESH_NAMES = {"coarse", "medium", "fine"};
    private static final double[] FLOW_SCALES = {0.25, 0.5, 1.0, 2.0, 4.0};

    private static final double MASS_TOL = 1.0e-4;
    private static final double RATIO_TOL = 5.0e-3;
    private static final double GRADIENT_TOL = 1.0e-2;
    private static final double SHEAR_TOL = 1.0e-2;
    private static final double MESH_TOL = 5.0e-3;

    private LiNRR_M01_2_FlowVerification() {}

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.load("M01_2_Verification", SOURCE);
        model.label("LiNRR_M01_2_flow_verification | frozen M01 derivative | " +
            PROVISIONAL);
        verifyFrozenM01(model);
        configureAuditDiscretization(model);
        createDatasets(model);
        createNumerics(model);
        createPlots(model);

        // Quantify the frozen pressure-outlet truncation behavior before the
        // analytical-closure outlet is applied. This row is diagnostic only;
        // acceptance is based on the fully developed outlet derivative below.
        setFlow(model, 1.0);
        configureMesh(model, 160, 80);
        solve(model);
        LiNRR_M01_2_Metrics pressureOutletReference = evaluate(model, 1.0);
        configureFullyDevelopedOutlet(model);

        System.out.println(meshHeader());
        LiNRR_M01_2_Metrics[] meshMetrics = new LiNRR_M01_2_Metrics[MESHES.length];
        for (int i = 0; i < MESHES.length; i++) {
            setFlow(model, 1.0);
            configureMesh(model, MESHES[i][0], MESHES[i][1]);
            solve(model);
            meshMetrics[i] = evaluate(model, 1.0);
            LiNRR_M01_2_Metrics previous = i == 0 ? null : meshMetrics[i - 1];
            System.out.println(meshLine(MESH_NAMES[i], MESHES[i][0],
                MESHES[i][1], meshMetrics[i], previous));
        }

        LiNRR_M01_2_Metrics medium = meshMetrics[1];
        LiNRR_M01_2_Metrics fine = meshMetrics[2];
        checkFineAcceptance(fine, medium);
        System.out.println(analyticHeader());
        System.out.println(analyticLine("frozen_pressure_outlet_reference",
            pressureOutletReference));
        System.out.println(analyticLine("baseline_fine_fully_developed_outlet", fine));

        System.out.println(sweepHeader());
        for (double flowScale : FLOW_SCALES) {
            setFlow(model, flowScale);
            configureMesh(model, 160, 80);
            solve(model);
            LiNRR_M01_2_Metrics result = evaluate(model, flowScale);
            checkAnalyticAcceptance(result);
            System.out.println(sweepLine(flowScale, result));
        }

        // Retain the baseline fine-grid solution in the editable audit MPH and
        // export only baseline figures so scan ordering cannot mislabel plots.
        setFlow(model, 1.0);
        configureMesh(model, 160, 80);
        solve(model);
        exportFigures(model);
        saveModel(model);
        System.out.println("M01_2_META|SOURCE_MODEL|frozen M01 isolated MPH");
        System.out.println("M01_2_META|RUN_STATE|SYNTHETIC_SMOKE_TEST");
        System.out.println("M01_2_META|CALIBRATION_MODE|PROVISIONAL");
        System.out.println("M01_2_META|M03B_READY|FALSE");
    }

    private static void verifyFrozenM01(Model model) {
        String uin = model.param().get("uin").replace(" ", "");
        String boundaryCondition = model.component("comp1").physics("spf")
            .feature("inlet").getString("BoundaryCondition");
        String averageVelocity = model.component("comp1").physics("spf")
            .feature("inlet").getString("Uav");
        if (!"Qliq/(Hcell*Wcell)".equals(uin) ||
            !"LaminarInflow".equals(boundaryCondition) ||
            !"uin".equals(averageVelocity)) {
            throw new IllegalStateException("Frozen M01 inlet definition mismatch.");
        }
        requireSelection(model, "sel_electrolyte", 2);
        requireSelection(model, "sel_inlet", 1);
        requireSelection(model, "sel_outlet", 1);
        requireSelection(model, "sel_anode_wall", 1);
        requireSelection(model, "sel_cathode_wall", 1);
    }

    private static void requireSelection(Model model, String tag, int dim) {
        int count = model.component("comp1").selection(tag).entities(dim).length;
        if (count != 1) {
            throw new IllegalStateException("FAILED_SELECTION_MAPPING: " + tag +
                " count=" + count);
        }
    }

    private static void configureAuditDiscretization(Model model) {
        // The frozen M01 source uses order_fluid=1. M01.2 raises only the
        // element order, retaining the same Laminar Flow physics and the exact
        // requested mapped element counts. This is a numerical audit setting,
        // not a new physical field or calibration.
        model.component("comp1").physics("spf").prop("ShapeProperty")
            .set("order_fluid", 2);
    }

    private static void configureFullyDevelopedOutlet(Model model) {
        model.component("comp1").physics("spf").feature("outlet")
            .set("BoundaryCondition", "LaminarOutflow");
        model.component("comp1").physics("spf").feature("outlet")
            .set("LaminarOutflowOption", "p0_exit");
        model.component("comp1").physics("spf").feature("outlet")
            .set("p0_exit", "0[Pa]");
    }

    private static void createDatasets(Model model) {
        double lmm = model.param().evaluate("Lcell", "mm");
        double hmm = model.param().evaluate("Hcell", "mm");
        model.result().dataset().create("m012_vertical_mid", "CutLine2D");
        model.result().dataset("m012_vertical_mid")
            .label("M01.2 mid-channel cross-section x=Lcell/2");
        model.result().dataset("m012_vertical_mid").set("genpoints",
            new double[][] {{0.5 * lmm, 0.0}, {0.5 * lmm, hmm}});

        model.result().dataset().create("m012_center_point", "CutPoint2D");
        model.result().dataset("m012_center_point")
            .label("M01.2 mid-channel center point");
        model.result().dataset("m012_center_point").set("pointx", 0.5 * lmm);
        model.result().dataset("m012_center_point").set("pointy", 0.5 * hmm);

        model.result().dataset().create("m012_centerline", "CutLine2D");
        model.result().dataset("m012_centerline")
            .label("M01.2 centerline excluding inlet/outlet end regions");
        model.result().dataset("m012_centerline").set("genpoints",
            new double[][] {{0.02 * lmm, 0.5 * hmm},
                            {0.98 * lmm, 0.5 * hmm}});
    }

    private static void createNumerics(Model model) {
        createDatasetNumerical(model, "m012_u_center", "EvalPoint",
            "m012_center_point", "u", "m/s");
        createDatasetNumerical(model, "m012_u_mean", "AvLine",
            "m012_vertical_mid", "u", "m/s");
        createDatasetNumerical(model, "m012_dpdx_mid", "AvLine",
            "m012_vertical_mid", "d(p,x)", "Pa/m");
        createBoundaryNumerical(model, "m012_mdot_in", "IntLine", "sel_inlet",
            "rho_el*(u*nx+v*ny)*Wcell", "kg/s");
        createBoundaryNumerical(model, "m012_mdot_out", "IntLine", "sel_outlet",
            "rho_el*(u*nx+v*ny)*Wcell", "kg/s");
        createBoundaryNumerical(model, "m012_p_in", "AvLine", "sel_inlet",
            "p", "Pa");
        createBoundaryNumerical(model, "m012_p_out", "AvLine", "sel_outlet",
            "p", "Pa");
        createBoundaryNumerical(model, "m012_tau_upper", "AvLine",
            "sel_anode_wall", "mu_el*(uy+vx)", "Pa");
        createBoundaryNumerical(model, "m012_tau_lower", "AvLine",
            "sel_cathode_wall", "mu_el*(uy+vx)", "Pa");
    }

    private static void createDatasetNumerical(Model model, String tag, String type,
                                               String dataset, String expr,
                                               String unit) {
        model.result().numerical().create(tag, type);
        model.result().numerical(tag).set("data", dataset);
        model.result().numerical(tag).set("expr", new String[] {expr});
        model.result().numerical(tag).set("unit", new String[] {unit});
        if ("AvLine".equals(type)) {
            model.result().numerical(tag).set("intorderactive", true);
            model.result().numerical(tag).set("intorder", 8);
        }
    }

    private static void createBoundaryNumerical(Model model, String tag, String type,
                                                String selection, String expr,
                                                String unit) {
        model.result().numerical().create(tag, type);
        model.result().numerical(tag).selection().named(selection);
        model.result().numerical(tag).set("expr", new String[] {expr});
        model.result().numerical(tag).set("unit", new String[] {unit});
        model.result().numerical(tag).set("intorderactive", true);
        model.result().numerical(tag).set("intorder", 8);
    }

    private static void createPlots(Model model) {
        model.result().create("m012_pg_velocity", "PlotGroup1D");
        model.result("m012_pg_velocity").label("M01.2 mid-channel velocity profile");
        model.result("m012_pg_velocity").set("data", "m012_vertical_mid");
        model.result("m012_pg_velocity").create("line", "LineGraph");
        model.result("m012_pg_velocity").feature("line").set("expr", "u");
        model.result("m012_pg_velocity").feature("line").set("unit", "m/s");

        model.result().create("m012_pg_gradient", "PlotGroup1D");
        model.result("m012_pg_gradient").label("M01.2 centerline pressure gradient");
        model.result("m012_pg_gradient").set("data", "m012_centerline");
        model.result("m012_pg_gradient").create("line", "LineGraph");
        model.result("m012_pg_gradient").feature("line").set("expr", "d(p,x)");
        model.result("m012_pg_gradient").feature("line").set("unit", "Pa/m");

        model.result().create("m012_pg_shear", "PlotGroup1D");
        model.result("m012_pg_shear").label("M01.2 signed upper and lower wall shear");
        model.result("m012_pg_shear").create("upper", "LineGraph");
        model.result("m012_pg_shear").feature("upper")
            .selection().named("sel_anode_wall");
        model.result("m012_pg_shear").feature("upper")
            .set("expr", "mu_el*(uy+vx)");
        model.result("m012_pg_shear").feature("upper").set("unit", "Pa");
        model.result("m012_pg_shear").feature("upper").label("Upper wall (signed)");
        model.result("m012_pg_shear").create("lower", "LineGraph");
        model.result("m012_pg_shear").feature("lower")
            .selection().named("sel_cathode_wall");
        model.result("m012_pg_shear").feature("lower")
            .set("expr", "mu_el*(uy+vx)");
        model.result("m012_pg_shear").feature("lower").set("unit", "Pa");
        model.result("m012_pg_shear").feature("lower").label("Lower wall (signed)");
    }

    private static void setFlow(Model model, double scale) {
        model.param().set("Qliq", format(scale) + "[cm^3/min]",
            "M01.2 flow sweep; baseline 1 cm^3/min; " + PROVISIONAL);
    }

    private static void configureMesh(Model model, int nLength, int nHeight) {
        model.component("comp1").mesh("mesh1").label(String.format(Locale.ROOT,
            "M01.2 mapped mesh %d x %d", nLength, nHeight));
        com.comsol.model.MeshFeature dx = model.component("comp1").mesh("mesh1")
            .feature("map_channel").feature("dist_length");
        dx.set("type", "number");
        dx.set("numelem", nLength);
        com.comsol.model.MeshFeature dy = model.component("comp1").mesh("mesh1")
            .feature("map_channel").feature("dist_height");
        dy.set("type", "number");
        dy.set("numelem", nHeight);
    }

    private static void solve(Model model) {
        model.component("comp1").mesh("mesh1").run();
        model.study("std_flow").run();
    }

    private static LiNRR_M01_2_Metrics evaluate(Model model, double flowScale) {
        LiNRR_M01_2_Metrics m = new LiNRR_M01_2_Metrics();
        m.flowScale = flowScale;
        m.umeanAnalytic = model.param().evaluate("Qliq/(Hcell*Wcell)", "m/s");
        double mu = model.param().evaluate("mu_el", "Pa*s");
        double h = model.param().evaluate("Hcell", "m");
        double l = model.param().evaluate("Lcell", "m");
        m.centerAnalytic = 1.5 * m.umeanAnalytic;
        m.dpdxAnalytic = -12.0 * mu * m.umeanAnalytic / (h * h);
        m.pdropAnalytic = 12.0 * mu * l * m.umeanAnalytic / (h * h);
        m.shearAnalytic = 6.0 * mu * m.umeanAnalytic / h;

        m.center = scalar(model, "m012_u_center");
        m.mean = scalar(model, "m012_u_mean");
        m.ratio = m.center / m.mean;
        m.dpdx = scalar(model, "m012_dpdx_mid");
        double signedIn = scalar(model, "m012_mdot_in");
        double signedOut = scalar(model, "m012_mdot_out");
        if (!(signedIn < 0.0 && signedOut > 0.0)) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                "Mass-flow sign convention failed: inlet=%.12g outlet=%.12g",
                signedIn, signedOut));
        }
        m.mdotIn = -signedIn;
        m.mdotOut = signedOut;
        m.massError = relative(m.mdotIn, m.mdotOut);
        m.pdrop = scalar(model, "m012_p_in") - scalar(model, "m012_p_out");
        m.tauUpper = scalar(model, "m012_tau_upper");
        m.tauLower = scalar(model, "m012_tau_lower");

        m.centerError = relative(m.center, m.centerAnalytic);
        m.meanError = relative(m.mean, m.umeanAnalytic);
        m.ratioError = relative(m.ratio, 1.5);
        m.dpdxError = relative(m.dpdx, m.dpdxAnalytic);
        m.pdropError = relative(m.pdrop, m.pdropAnalytic);
        m.upperShearError = relative(Math.abs(m.tauUpper), m.shearAnalytic);
        m.lowerShearError = relative(Math.abs(m.tauLower), m.shearAnalytic);
        return m;
    }

    private static double scalar(Model model, String tag) {
        double[][] values = model.result().numerical(tag).getReal();
        if (values == null || values.length == 0 || values[0].length == 0) {
            throw new IllegalStateException("No numerical result for " + tag);
        }
        return values[0][0];
    }

    private static void checkFineAcceptance(LiNRR_M01_2_Metrics fine,
                                            LiNRR_M01_2_Metrics medium) {
        checkAnalyticAcceptance(fine);
        double[] changes = changes(fine, medium);
        String[] names = {"center_velocity", "mean_velocity", "center_mean_ratio",
            "mid_pressure_gradient", "global_pressure_drop", "upper_wall_shear",
            "lower_wall_shear", "inlet_mass_flow", "outlet_mass_flow"};
        for (int i = 0; i < changes.length; i++) {
            if (!Double.isFinite(changes[i]) || changes[i] > MESH_TOL) {
                throw new IllegalStateException(String.format(Locale.ROOT,
                    "Medium-to-fine change for %s is %.12g, limit %.12g",
                    names[i], changes[i], MESH_TOL));
            }
        }
    }

    private static void checkAnalyticAcceptance(LiNRR_M01_2_Metrics m) {
        if (m.massError > MASS_TOL) fail("mass balance", m.massError, MASS_TOL);
        if (m.ratioError > RATIO_TOL) fail("center/mean ratio", m.ratioError, RATIO_TOL);
        if (m.dpdxError > GRADIENT_TOL) fail("mid pressure gradient", m.dpdxError,
            GRADIENT_TOL);
        if (m.upperShearError > SHEAR_TOL) fail("upper wall shear",
            m.upperShearError, SHEAR_TOL);
        if (m.lowerShearError > SHEAR_TOL) fail("lower wall shear",
            m.lowerShearError, SHEAR_TOL);
    }

    private static void fail(String name, double value, double limit) {
        throw new IllegalStateException(String.format(Locale.ROOT,
            "%s relative error %.12g exceeds %.12g", name, value, limit));
    }

    private static String meshHeader() {
        return "M01_2_MESH|mesh|n_length|n_height|cells|flow_scale|" + commonHeader() +
            "|change_center|change_mean|change_ratio|change_dpdx|change_pdrop|" +
            "change_upper_shear|change_lower_shear|change_mdot_in|change_mdot_out";
    }

    private static String analyticHeader() {
        return "M01_2_ANALYTIC|case|" + commonHeader();
    }

    private static String sweepHeader() {
        return "M01_2_SWEEP|flow_scale|Q_cm3_min|" + commonHeader();
    }

    private static String commonHeader() {
        return "umean_analytic_m_s|center_velocity_m_s|center_analytic_m_s|" +
            "center_error|cross_mean_m_s|mean_error|center_mean_ratio|ratio_error|" +
            "dpdx_mid_Pa_m|dpdx_analytic_Pa_m|dpdx_error|global_pdrop_Pa|" +
            "pdrop_analytic_Pa|pdrop_error|tau_upper_Pa|tau_lower_Pa|" +
            "tau_abs_analytic_Pa|tau_upper_error|tau_lower_error|" +
            "inlet_mass_flow_kg_s|outlet_mass_flow_kg_s|mass_balance_error";
    }

    private static String meshLine(String name, int nx, int ny,
                                   LiNRR_M01_2_Metrics m,
                                   LiNRR_M01_2_Metrics previous) {
        double[] c = previous == null ? nanChanges() : changes(m, previous);
        return String.format(Locale.ROOT,
            "M01_2_MESH|%s|%d|%d|%d|%.12g|%s|%s",
            name, nx, ny, nx * ny, m.flowScale, commonValues(m), join(c));
    }

    private static String analyticLine(String name, LiNRR_M01_2_Metrics m) {
        return "M01_2_ANALYTIC|" + name + "|" + commonValues(m);
    }

    private static String sweepLine(double scale, LiNRR_M01_2_Metrics m) {
        return String.format(Locale.ROOT, "M01_2_SWEEP|%.12g|%.12g|%s",
            scale, scale, commonValues(m));
    }

    private static String commonValues(LiNRR_M01_2_Metrics m) {
        return String.format(Locale.ROOT,
            "%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|" +
            "%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|" +
            "%.12g|%.12g|%.12g|%.12g|%.12g|%.12g",
            m.umeanAnalytic, m.center, m.centerAnalytic, m.centerError,
            m.mean, m.meanError, m.ratio, m.ratioError,
            m.dpdx, m.dpdxAnalytic, m.dpdxError, m.pdrop,
            m.pdropAnalytic, m.pdropError, m.tauUpper, m.tauLower,
            m.shearAnalytic, m.upperShearError, m.lowerShearError,
            m.mdotIn, m.mdotOut, m.massError);
    }

    private static double[] changes(LiNRR_M01_2_Metrics current,
                                    LiNRR_M01_2_Metrics previous) {
        return new double[] {
            relative(current.center, previous.center),
            relative(current.mean, previous.mean),
            relative(current.ratio, previous.ratio),
            relative(current.dpdx, previous.dpdx),
            relative(current.pdrop, previous.pdrop),
            relative(Math.abs(current.tauUpper), Math.abs(previous.tauUpper)),
            relative(Math.abs(current.tauLower), Math.abs(previous.tauLower)),
            relative(current.mdotIn, previous.mdotIn),
            relative(current.mdotOut, previous.mdotOut)
        };
    }

    private static double[] nanChanges() {
        double[] values = new double[9];
        for (int i = 0; i < values.length; i++) values[i] = Double.NaN;
        return values;
    }

    private static String join(double[] values) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) text.append('|');
            text.append(format(values[i]));
        }
        return text.toString();
    }

    private static double relative(double a, double b) {
        return Math.abs(a - b) / Math.max(Math.max(Math.abs(a), Math.abs(b)), 1e-30);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.12g", value);
    }

    private static void exportFigures(Model model) {
        imageExport(model, "m012_img_velocity", "m012_pg_velocity",
            "results/figures/M01_2_velocity_profile.png");
        imageExport(model, "m012_img_gradient", "m012_pg_gradient",
            "results/figures/M01_2_pressure_gradient.png");
        imageExport(model, "m012_img_shear", "m012_pg_shear",
            "results/figures/M01_2_wall_shear.png");
    }

    private static void imageExport(Model model, String tag, String plot,
                                    String filename) {
        model.result().export().create(tag, "Image1D");
        model.result().export(tag).set("sourceobject", plot);
        model.result().export(tag).set("target", "file");
        // A loaded MPH otherwise resolves relative export paths against the
        // source MPH directory (runs/latest), not the required project root.
        model.result().export(tag).set("filename", PROJECT_ROOT + filename);
        model.result().export(tag).run();
    }

    private static void saveModel(Model model) throws IOException {
        model.save(OUTPUT);
    }

}
