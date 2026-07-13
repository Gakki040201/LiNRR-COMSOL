import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.io.IOException;
import java.util.Locale;

/**
 * M01: two-dimensional stationary electrolyte flow.
 *
 * PROVISIONAL — numerical smoke test only
 * rho_el and mu_el are placeholders, not measured, fitted, or
 * literature-validated material properties.
 */
public final class LiNRR_M01_Flow {
    private static final String PROVISIONAL =
        "PROVISIONAL \u2014 numerical smoke test only";
    private static final double MASS_BALANCE_TOLERANCE = 1.0e-4;

    private LiNRR_M01_Flow() {}

    public static Model run() throws Exception {
        Model model = ModelUtil.create("Model");
        model.label("LiNRR_M01_flow | " + PROVISIONAL);
        defineParameters(model);
        buildGeometry(model);
        createSelections(model);
        assignMaterials(model);
        addFlowPhysics(model);
        buildMesh(model);
        createStudies(model);
        model.study("std_flow").run();
        createResults(model);
        double[] summary = evaluateChecks(model);
        printSummary(summary);
        runChecks(summary);
        exportFigures(model);
        saveModel(model);
        return model;
    }

    private static void defineParameters(Model model) {
        model.param().set("Lcell", "55[mm]", "Specified electrolyte-channel length");
        model.param().set("Hcell", "4[mm]", "Specified electrolyte-layer thickness");
        model.param().set("Wcell", "55[mm]", "Specified out-of-plane active width");
        model.param().set("T0", "298.15[K]", "Specified operating temperature");
        model.param().set("Qliq", "1[cm^3/min]",
                          "Specified flow rate: exactly 1 mL/min; cm^3 is COMSOL-compatible");
        // PROVISIONAL — numerical smoke test only: replace with traceable data.
        model.param().set("rho_el", "900[kg/m^3]", PROVISIONAL);
        // PROVISIONAL — numerical smoke test only: replace with traceable data.
        model.param().set("mu_el", "3[mPa*s]", PROVISIONAL);
        model.param().set("uin", "Qliq/(Hcell*Wcell)",
                          "Mean inlet velocity derived from flow rate");
        model.param().set("tau_nominal", "Lcell*Hcell*Wcell/Qliq",
                          "Nominal channel-volume residence time");
        model.param().set("sel_tol", "1e-6[mm]", "Coordinate-selection tolerance");
    }

    private static void buildGeometry(Model model) {
        model.component().create("comp1", true);
        model.component("comp1").label("M01 2D liquid channel | " + PROVISIONAL);
        model.component("comp1").geom().create("geom1", 2);
        model.component("comp1").geom("geom1").label("Parameterized 2D channel geometry");
        model.component("comp1").geom("geom1").lengthUnit("mm");
        model.component("comp1").geom("geom1").create("r_channel", "Rectangle");
        model.component("comp1").geom("geom1").feature("r_channel")
             .label("Electrolyte channel 55 mm x 4 mm");
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
        model.component("comp1").physics("spf")
             .label("Stationary laminar electrolyte flow");
        model.component("comp1").physics("spf").selection().named("sel_electrolyte");

        model.component("comp1").physics("spf").create("wall_anode", "Wall", 1);
        model.component("comp1").physics("spf").feature("wall_anode")
             .selection().named("sel_anode_wall");
        model.component("comp1").physics("spf").feature("wall_anode")
             .label("No slip: anode wall");
        model.component("comp1").physics("spf").create("wall_cathode", "Wall", 1);
        model.component("comp1").physics("spf").feature("wall_cathode")
             .selection().named("sel_cathode_wall");
        model.component("comp1").physics("spf").feature("wall_cathode")
             .label("No slip: cathode wall");

        model.component("comp1").physics("spf").create("inlet", "Inlet", 1);
        model.component("comp1").physics("spf").feature("inlet")
             .selection().named("sel_inlet");
        model.component("comp1").physics("spf").feature("inlet")
             .label("Fully developed inlet with mean velocity uin (+x)");
        model.component("comp1").physics("spf").feature("inlet")
             .set("BoundaryCondition", "LaminarInflow");
        model.component("comp1").physics("spf").feature("inlet")
             .set("Uav", "uin");

        model.component("comp1").physics("spf").create("outlet", "Outlet", 1);
        model.component("comp1").physics("spf").feature("outlet")
             .selection().named("sel_outlet");
        model.component("comp1").physics("spf").feature("outlet")
             .label("Zero-gauge-pressure outlet");
        model.component("comp1").physics("spf").feature("outlet").set("p0", "0[Pa]");
    }

    private static void buildMesh(Model model) {
        model.component("comp1").mesh().create("mesh1");
        model.component("comp1").mesh("mesh1")
             .label("Auditable mapped mesh: 100 x 200");
        model.component("comp1").mesh("mesh1").create("map_channel", "Map");
        model.component("comp1").mesh("mesh1").feature("map_channel")
             .selection().named("sel_electrolyte");
        model.component("comp1").mesh("mesh1").feature("map_channel")
             .create("dist_height", "Distribution");
        model.component("comp1").mesh("mesh1").feature("map_channel")
             .feature("dist_height").label("200 elements through Hcell");
        model.component("comp1").mesh("mesh1").feature("map_channel")
             .feature("dist_height").selection().named("sel_inlet");
        model.component("comp1").mesh("mesh1").feature("map_channel")
             .feature("dist_height").set("numelem", 200);
        model.component("comp1").mesh("mesh1").feature("map_channel")
             .create("dist_length", "Distribution");
        model.component("comp1").mesh("mesh1").feature("map_channel")
             .feature("dist_length").label("100 elements along Lcell");
        model.component("comp1").mesh("mesh1").feature("map_channel")
             .feature("dist_length").selection().named("sel_cathode_wall");
        model.component("comp1").mesh("mesh1").feature("map_channel")
             .feature("dist_length").set("numelem", 100);
        model.component("comp1").mesh("mesh1").run();
    }

    private static void createStudies(Model model) {
        model.study().create("std_flow");
        model.study("std_flow").label("M01 stationary laminar-flow study");
        model.study("std_flow").create("stat", "Stationary");
        model.study("std_flow").feature("stat").label("Stationary flow solution");
        model.study("std_flow").feature("stat").activate("spf", true);
    }

    private static void createResults(Model model) {
        model.result().create("pg_velocity", "PlotGroup2D");
        model.result("pg_velocity").label("M01 velocity magnitude and arrows");
        model.result("pg_velocity").create("surf_speed", "Surface");
        model.result("pg_velocity").feature("surf_speed").set("expr", "spf.U");
        model.result("pg_velocity").feature("surf_speed").label("Velocity magnitude");
        model.result("pg_velocity").create("arrow_velocity", "ArrowSurface");
        model.result("pg_velocity").feature("arrow_velocity")
             .set("expr", new String[] {"u", "v"});
        model.result("pg_velocity").feature("arrow_velocity").label("Velocity arrows");

        model.result().create("pg_pressure", "PlotGroup2D");
        model.result("pg_pressure").label("M01 pressure field");
        model.result("pg_pressure").create("surf_pressure", "Surface");
        model.result("pg_pressure").feature("surf_pressure").set("expr", "p");
        model.result("pg_pressure").feature("surf_pressure").label("Gauge pressure");

        createNumerical(model, "eval_uin", "EvalGlobal", "Inlet mean velocity",
                        "uin/(1[m/s])", "1", null);
        createNumerical(model, "max_speed", "MaxSurface", "Maximum velocity magnitude",
                        "spf.U/(1[m/s])", "1", "sel_electrolyte");
        createNumerical(model, "mdot_in", "IntLine", "Signed inlet mass flow",
                        "rho_el*(u*nx+v*ny)*Wcell/(1[kg/s])", "1", "sel_inlet");
        createNumerical(model, "mdot_out", "IntLine", "Signed outlet mass flow",
                        "rho_el*(u*nx+v*ny)*Wcell/(1[kg/s])", "1", "sel_outlet");
        createNumerical(model, "p_in", "AvLine", "Average inlet pressure",
                        "p/(1[Pa])", "1", "sel_inlet");
        createNumerical(model, "p_out", "AvLine", "Average outlet pressure",
                        "p/(1[Pa])", "1", "sel_outlet");
        createNumerical(model, "tau", "EvalGlobal", "Nominal residence time",
                        "tau_nominal/(1[s])", "1", null);
    }

    private static void createNumerical(Model model, String tag, String type,
                                        String label, String expression,
                                        String unit, String selectionTag) {
        model.result().numerical().create(tag, type);
        model.result().numerical(tag).label(label);
        model.result().numerical(tag).set("expr", new String[] {expression});
        model.result().numerical(tag).set("unit", new String[] {unit});
        if (selectionTag != null) {
            model.result().numerical(tag).selection().named(selectionTag);
        }
    }

    private static double[] evaluateChecks(Model model) {
        double uin = model.param().evaluate("uin", "m/s");
        double maxSpeed = scalar(model.result().numerical("max_speed").getReal());
        double mdotIn = Math.abs(scalar(model.result().numerical("mdot_in").getReal()));
        double mdotOut = Math.abs(scalar(model.result().numerical("mdot_out").getReal()));
        double pIn = scalar(model.result().numerical("p_in").getReal());
        double pOut = scalar(model.result().numerical("p_out").getReal());
        double residenceTime = model.param().evaluate("tau_nominal", "s");
        double denominator = Math.max(Math.max(mdotIn, mdotOut), 1.0e-30);
        double relativeError = Math.abs(mdotIn - mdotOut) / denominator;
        return new double[] {uin, maxSpeed, mdotIn, mdotOut,
                             pIn - pOut, residenceTime, relativeError};
    }

    private static double scalar(double[][] values) {
        if (values == null || values.length == 0 || values[0].length == 0) {
            throw new IllegalStateException("COMSOL numerical evaluation returned no values.");
        }
        return values[0][0];
    }

    private static void printSummary(double[] s) {
        System.out.println("M01_RESULT|parameter_name|value|unit|status|provenance");
        emit("Lcell", 55.0, "mm", "specified", "user requirement");
        emit("Hcell", 4.0, "mm", "specified", "user requirement");
        emit("Wcell", 55.0, "mm", "specified", "user requirement");
        emit("Qliq", 1.0, "mL/min", "specified", "user requirement");
        emit("rho_el", 900.0, "kg/m^3", PROVISIONAL,
             "assumed placeholder; no scientific provenance assigned");
        emit("mu_el", 3.0, "mPa*s", PROVISIONAL,
             "assumed placeholder; no scientific provenance assigned");
        emit("uin", s[0], "m/s", "computed", "Qliq/(Hcell*Wcell)");
        emit("max_velocity", s[1], "m/s", "computed",
             "COMSOL stationary Laminar Flow solution");
        emit("mdot_in", s[2], "kg/s", "computed",
             "boundary integral of rho*(u*n_x+v*n_y)*Wcell; absolute value");
        emit("mdot_out", s[3], "kg/s", "computed",
             "boundary integral of rho*(u*n_x+v*n_y)*Wcell; absolute value");
        emit("pressure_drop", s[4], "Pa", "computed",
             "average inlet pressure minus average outlet pressure");
        emit("residence_time", s[5], "s", "computed",
             "Lcell*Hcell*Wcell/Qliq nominal channel-volume residence time");
        emit("mass_balance_relative_error", s[6], "1",
             s[6] <= MASS_BALANCE_TOLERANCE ? "PASS" : "FAIL",
             "abs(abs(mdot_in)-abs(mdot_out))/max(abs(mdot_in),abs(mdot_out),1e-30[kg/s])");
    }

    private static void emit(String name, double value, String unit,
                             String status, String provenance) {
        System.out.println("M01_RESULT|" + name + "|" +
            String.format(Locale.ROOT, "%.12g", value) + "|" + unit + "|" +
            status + "|" + provenance);
    }

    private static void exportFigures(Model model) {
        imageExport(model, "img_velocity", "pg_velocity",
                    "results/figures/M01_velocity.png");
        imageExport(model, "img_pressure", "pg_pressure",
                    "results/figures/M01_pressure.png");
    }

    private static void imageExport(Model model, String tag, String plotTag, String path) {
        model.result().export().create(tag, "Image2D");
        model.result().export(tag).set("sourceobject", plotTag);
        model.result().export(tag).set("target", "file");
        model.result().export(tag).set("filename", path);
        model.result().export(tag).run();
    }

    private static void runChecks(double[] s) {
        if (!Double.isFinite(s[6])) {
            throw new IllegalStateException("Mass-balance relative error is not finite.");
        }
        if (s[6] > MASS_BALANCE_TOLERANCE) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                "Mass-balance relative error %.12g exceeds limit %.12g.",
                s[6], MASS_BALANCE_TOLERANCE));
        }
        if (Math.abs(s[0] - 7.575757575757576e-5) > 1.0e-12) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                "Dimensional check failed: uin=%.12g m/s, expected 7.57575757576e-5 m/s.",
                s[0]));
        }
        if (Math.abs(s[5] - 726.0) > 1.0e-8) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                "Dimensional check failed: residence time=%.12g s, expected 726 s.", s[5]));
        }
    }

    private static void saveModel(Model model) throws IOException {
        model.save("models/generated/LiNRR_M01_flow.mph");
    }

    public static void main(String[] args) throws Exception {
        run();
    }

}
