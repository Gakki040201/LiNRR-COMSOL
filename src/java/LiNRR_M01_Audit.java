import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.io.IOException;
import java.util.Locale;

/** Independent numerical audit of the accepted M01 baseline. */
public final class LiNRR_M01_Audit {
    private static final String SOURCE = "models/generated/LiNRR_M01_flow.mph";
    private static final String COPY = "models/generated/LiNRR_M01_flow_audit.mph";
    private static final int[][] MESHES = {{40, 20}, {80, 80}, {160, 200}};
    private static final String[] NAMES = {"coarse", "medium", "fine"};

    private LiNRR_M01_Audit() {}

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.load("M01Audit", SOURCE);
        model.label("LiNRR_M01_flow_audit | independent derivative; M01 baseline unchanged");
        verifyMeanVelocityDefinition(model);
        createAuditDatasets(model);
        createAuditNumerics(model);
        System.out.println("M01_AUDIT|mesh|n_length|n_height|cells|pressure_drop_Pa|" +
            "inlet_u_center_m_s|inlet_u_mean_m_s|inlet_ratio|" +
            "mid_u_center_m_s|mid_u_mean_m_s|mid_ratio|" +
            "outlet_u_center_m_s|outlet_u_mean_m_s|outlet_ratio|" +
            "mdot_in_kg_s|mdot_out_kg_s|mass_error|" +
            "mdot_in_default_kg_s|mdot_out_default_kg_s|mass_error_default|" +
            "max_speed_m_s|max_x_m|max_y_m|max_region|uin_m_s|max_over_uin");

        for (int i = 0; i < MESHES.length; i++) {
            configureMesh(model, MESHES[i][0], MESHES[i][1]);
            model.component("comp1").mesh("mesh1").run();
            model.study("std_flow").run();
            double[] values = evaluate(model);
            emit(NAMES[i], MESHES[i][0], MESHES[i][1], values);
        }
        save(model);
    }

    private static void verifyMeanVelocityDefinition(Model model) {
        String expression = model.param().get("uin");
        String description = model.param().descr("uin");
        if (!expression.replace(" ", "").equals("Qliq/(Hcell*Wcell)")) {
            throw new IllegalStateException("M01 uin is not Qliq/(Hcell*Wcell): " + expression);
        }
        String boundaryCondition = model.component("comp1").physics("spf")
            .feature("inlet").getString("BoundaryCondition");
        String averageVelocity = model.component("comp1").physics("spf")
            .feature("inlet").getString("Uav");
        if (!"LaminarInflow".equals(boundaryCondition) || !"uin".equals(averageVelocity)) {
            throw new IllegalStateException("M01 inlet is not fully developed with mean velocity uin.");
        }
        System.out.println("M01_AUDIT_META|uin_expression|" + expression);
        System.out.println("M01_AUDIT_META|uin_description|" + description);
        System.out.println("M01_AUDIT_META|inlet_boundary_condition|" + boundaryCondition);
        System.out.println("M01_AUDIT_META|inlet_average_velocity_field|" + averageVelocity);
    }

    private static void configureMesh(Model model, int nLength, int nHeight) {
        model.component("comp1").mesh("mesh1").label(
            "M01 audit mapped mesh: " + nLength + " x " + nHeight);
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .feature("dist_length").set("numelem", nLength);
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .feature("dist_height").set("numelem", nHeight);
    }

    private static void createAuditDatasets(Model model) {
        createCutLine(model, "cln_in", 0.01);
        createCutLine(model, "cln_mid", 0.50);
        createCutLine(model, "cln_out", 0.99);
        createCutPoint(model, "cpt_in", 0.01);
        createCutPoint(model, "cpt_mid", 0.50);
        createCutPoint(model, "cpt_out", 0.99);
    }

    private static void createCutLine(Model model, String tag, double xFraction) {
        model.result().dataset().create(tag, "CutLine2D");
        model.result().dataset(tag).label(String.format(Locale.ROOT,
            "Cross-section at x = %.2f Lcell", xFraction));
        // Result cut coordinates use the geometry unit (mm), not the SI base unit.
        double x = 55.0 * xFraction;
        model.result().dataset(tag).set("genpoints",
            new double[][] {{x, 0.0}, {x, 4.0}});
    }

    private static void createCutPoint(Model model, String tag, double xFraction) {
        model.result().dataset().create(tag, "CutPoint2D");
        model.result().dataset(tag).label(String.format(Locale.ROOT,
            "Centerline point at x = %.2f Lcell", xFraction));
        model.result().dataset(tag).set("pointx", 55.0 * xFraction);
        model.result().dataset(tag).set("pointy", 2.0);
    }

    private static void createAuditNumerics(Model model) {
        createOnDataset(model, "u_center_in", "EvalPoint", "cpt_in", "u", "m/s");
        createOnDataset(model, "u_center_mid", "EvalPoint", "cpt_mid", "u", "m/s");
        createOnDataset(model, "u_center_out", "EvalPoint", "cpt_out", "u", "m/s");
        createOnDataset(model, "u_mean_in", "AvLine", "cln_in", "u", "m/s");
        createOnDataset(model, "u_mean_mid", "AvLine", "cln_mid", "u", "m/s");
        createOnDataset(model, "u_mean_out", "AvLine", "cln_out", "u", "m/s");

        model.result().numerical().create("mdot_in_hi", "IntLine");
        model.result().numerical("mdot_in_hi").selection().named("sel_inlet");
        model.result().numerical("mdot_in_hi").set("expr",
            new String[] {"rho_el*(u*nx+v*ny)*Wcell"});
        model.result().numerical("mdot_in_hi").set("unit", new String[] {"kg/s"});
        model.result().numerical("mdot_in_hi").set("intorderactive", true);
        model.result().numerical("mdot_in_hi").set("intorder", 8);

        model.result().numerical().create("mdot_out_hi", "IntLine");
        model.result().numerical("mdot_out_hi").selection().named("sel_outlet");
        model.result().numerical("mdot_out_hi").set("expr",
            new String[] {"rho_el*(u*nx+v*ny)*Wcell"});
        model.result().numerical("mdot_out_hi").set("unit", new String[] {"kg/s"});
        model.result().numerical("mdot_out_hi").set("intorderactive", true);
        model.result().numerical("mdot_out_hi").set("intorder", 8);

        model.result().numerical().create("max_speed_pos", "MaxSurface");
        model.result().numerical("max_speed_pos").selection().named("sel_electrolyte");
        model.result().numerical("max_speed_pos").set("expr", new String[] {"spf.U"});
        model.result().numerical("max_speed_pos").set("unit", new String[] {"m/s"});
        model.result().numerical("max_speed_pos").set("includepos", true);
    }

    private static void createOnDataset(Model model, String tag, String type,
                                        String dataset, String expression, String unit) {
        model.result().numerical().create(tag, type);
        model.result().numerical(tag).set("data", dataset);
        model.result().numerical(tag).set("expr", new String[] {expression});
        model.result().numerical(tag).set("unit", new String[] {unit});
        if ("AvLine".equals(type)) {
            model.result().numerical(tag).set("intorderactive", true);
            model.result().numerical(tag).set("intorder", 8);
        }
    }

    private static double[] evaluate(Model model) {
        double pDrop = scalar(model.result().numerical("p_in").getReal()) -
                       scalar(model.result().numerical("p_out").getReal());
        double ucIn = scalar(model.result().numerical("u_center_in").getReal());
        double umIn = scalar(model.result().numerical("u_mean_in").getReal());
        double ucMid = scalar(model.result().numerical("u_center_mid").getReal());
        double umMid = scalar(model.result().numerical("u_mean_mid").getReal());
        double ucOut = scalar(model.result().numerical("u_center_out").getReal());
        double umOut = scalar(model.result().numerical("u_mean_out").getReal());
        double mdotIn = Math.abs(scalar(model.result().numerical("mdot_in_hi").getReal()));
        double mdotOut = Math.abs(scalar(model.result().numerical("mdot_out_hi").getReal()));
        double mdotInDefault = Math.abs(scalar(model.result().numerical("mdot_in").getReal()));
        double mdotOutDefault = Math.abs(scalar(model.result().numerical("mdot_out").getReal()));
        double[][] maximum = model.result().numerical("max_speed_pos").getReal();
        double maxSpeed = maximum[0][0];
        double maxX = maximum.length > 1 ? coordinateToMeters(maximum[1][0]) : Double.NaN;
        double maxY = maximum.length > 2 ? coordinateToMeters(maximum[2][0]) : Double.NaN;
        double uin = model.param().evaluate("uin", "m/s");
        return new double[] {pDrop, ucIn, umIn, ucMid, umMid, ucOut, umOut,
            mdotIn, mdotOut, relative(mdotIn, mdotOut),
            mdotInDefault, mdotOutDefault, relative(mdotInDefault, mdotOutDefault),
            maxSpeed, maxX, maxY, uin};
    }

    private static void emit(String mesh, int nLength, int nHeight, double[] v) {
        String region = classify(v[14]);
        System.out.println(String.format(Locale.ROOT,
            "M01_AUDIT|%s|%d|%d|%d|%.12g|%.12g|%.12g|%.12g|" +
            "%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|" +
            "%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|" +
            "%.12g|%.12g|%.12g|%s|%.12g|%.12g",
            mesh, nLength, nHeight, nLength * nHeight, v[0],
            v[1], v[2], v[1] / v[2],
            v[3], v[4], v[3] / v[4],
            v[5], v[6], v[5] / v[6],
            v[7], v[8], v[9], v[10], v[11], v[12],
            v[13], v[14], v[15], region, v[16], v[13] / v[16]));
    }

    private static String classify(double x) {
        if (!Double.isFinite(x)) return "position_unavailable";
        if (x <= 0.05 * 0.055) return "inlet_region";
        if (x >= 0.95 * 0.055) return "outlet_region";
        return "channel_interior";
    }

    private static double relative(double a, double b) {
        return Math.abs(a - b) / Math.max(Math.max(Math.abs(a), Math.abs(b)), 1e-30);
    }

    private static double coordinateToMeters(double coordinate) {
        return Math.abs(coordinate) > 1.0 ? coordinate * 1e-3 : coordinate;
    }

    private static double scalar(double[][] values) {
        if (values == null || values.length == 0 || values[0].length == 0) {
            throw new IllegalStateException("COMSOL numerical evaluation returned no values.");
        }
        return values[0][0];
    }

    private static void save(Model model) throws IOException {
        model.save(COPY);
    }
}
