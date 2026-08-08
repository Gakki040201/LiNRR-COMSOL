import com.comsol.model.Model;
import com.comsol.model.physics.Physics;
import com.comsol.model.util.ModelUtil;

import java.util.Locale;

/** M10A1.1: replace brittle gradient-based wall-shear plots with native traction variables. */
public final class LiNRR_M10A1_1_ResultRepair {
    private static final String RUNTIME_CLASS = "LiNRR_M10A1_1_RuntimeInputs";
    private static int serial = 0;

    private LiNRR_M10A1_1_ResultRepair() {}

    public static void main(String[] args) throws Exception {
        final String input = runtime("INPUT_MPH");
        final String output = runtime("OUTPUT_MPH");
        final Model source = ModelUtil.load("M10A1Repair", input);
        final double[] before;
        try {
            before = fieldSignature(source);
            repairWallShear(source, "comp_electrolyte_flow", "spf_liq", "var_liq_derived",
                "tauw_liq_test", "pg_liq_wall_shear", "dset_liq_solution", "sel_bnd_electrolyte_walls");
            repairWallShear(source, "comp_n2_flow", "spf_n2", "var_n2_derived",
                "tauw_n2_test", "pg_n2_wall_shear", "dset_n2_solution", "sel_bnd_n2_walls");
            source.label("LiNRR_M10A1_1_real_cad_flow_repaired.mph");
            source.comments("M10A1.1 result-only repair. Solved M10A1 fields are unchanged; wall shear uses COMSOL-native total-traction variables projected tangentially to the wall.");
            runRequiredPlots(source);
            source.save(output);
            System.out.println("M10A1_1_REPAIRED_MODEL_SAVE=PASS");
        } finally {
            ModelUtil.remove("M10A1Repair");
        }

        final Model reload = ModelUtil.load("M10A1Reload", output);
        try {
            runRequiredPlots(reload);
            final double[] after = fieldSignature(reload);
            compare(before, after);
            validateShear(reload, "comp_electrolyte_flow", "spf_liq", "dset_liq_solution",
                "sel_bnd_electrolyte_walls", "liquid");
            validateShear(reload, "comp_n2_flow", "spf_n2", "dset_n2_solution",
                "sel_bnd_n2_walls", "n2");
            System.out.println("M10A1_1_INDEPENDENT_RELOAD=PASS");
            System.out.println("M10A1_1_SOLVED_FIELD_INVARIANCE=PASS");
            System.out.println("M10A1_1_RESULT_REPAIR=PASS");
        } finally {
            ModelUtil.remove("M10A1Reload");
        }
    }

    private static void repairWallShear(Model model, String component, String physics, String variables,
                                        String variableName, String plot, String dataset, String walls) {
        final String[] traction = nativeTraction(model.component(component).physics(physics));
        final String tx = traction[0];
        final String ty = traction[1];
        final String tz = traction[2];
        final String expression = tangentialMagnitude(tx, ty, tz);
        if (model.component(component).variable().hasTag(variables)) {
            model.component(component).variable().remove(variables);
            System.out.println("M10A1_1_BRITTLE_VARIABLE_NODE_REMOVED|component=" + component + "|tag=" + variables);
        }
        model.result(plot).set("data", dataset);
        model.result(plot).feature("surf").set("expr", expression);
        model.result(plot).feature("surf").set("unit", "Pa");
        final double check = eval(model, "MaxSurface", dataset, walls, expression, "Pa");
        if (!Double.isFinite(check) || check < 0) {
            throw new IllegalStateException("INVALID_REPAIRED_WALL_SHEAR: " + component + " value=" + check);
        }
        System.out.println("M10A1_1_WALL_SHEAR_REPAIRED|component=" + component + "|expression=" + expression
            + "|max_Pa=" + format(check));
    }

    private static String[] nativeTraction(Physics physics) {
        final String[][] table = physics.featureInfo("info").getInfoTable("Expression", "recursive", "all");
        String tx = null;
        for (String[] row : table) {
            for (String cell : row) {
                if (cell != null && cell.matches("[A-Za-z][A-Za-z0-9_]*\\.T_stressx")) {
                    tx = cell;
                }
            }
        }
        if (tx == null) {
            throw new IllegalStateException("NATIVE_TOTAL_TRACTION_NOT_DEFINED: " + physics.tag());
        }
        final String prefix = tx.substring(0, tx.length() - "T_stressx".length());
        final String[] result = {tx, prefix + "T_stressy", prefix + "T_stressz"};
        System.out.println("M10A1_1_NATIVE_TRACTION_DISCOVERED|physics=" + physics.tag() + "|variables="
            + String.join(",", result));
        return result;
    }

    private static String tangentialMagnitude(String tx, String ty, String tz) {
        final String tn = "((" + tx + ")*nx+(" + ty + ")*ny+(" + tz + ")*nz)";
        return "sqrt((" + tx + "-(" + tn + ")*nx)^2+(" + ty + "-(" + tn + ")*ny)^2+("
            + tz + "-(" + tn + ")*nz)^2)";
    }

    private static void runRequiredPlots(Model model) {
        final String[] plots = {
            "pg_liq_velocity", "pg_liq_pressure", "pg_liq_streamlines", "pg_liq_wall_shear",
            "pg_n2_velocity", "pg_n2_pressure", "pg_n2_streamlines", "pg_n2_wall_shear"
        };
        for (String plot : plots) {
            if (!model.result().hasTag(plot)) {
                throw new IllegalStateException("REQUIRED_PLOT_MISSING: " + plot);
            }
            model.result(plot).run();
            if (model.result(plot).hasWarning()) {
                throw new IllegalStateException("REQUIRED_PLOT_WARNING: " + plot);
            }
            System.out.println("M10A1_1_PLOT_RELOAD|tag=" + plot + "|status=PASS");
        }
    }

    private static double[] fieldSignature(Model model) {
        return new double[]{
            eval(model, "AvVolume", "dset_liq_solution", "sel_dom_electrolyte_fluid", "sqrt(u^2+v^2+w^2)", "m/s"),
            eval(model, "MaxVolume", "dset_liq_solution", "sel_dom_electrolyte_fluid", "sqrt(u^2+v^2+w^2)", "m/s"),
            eval(model, "AvSurface", "dset_liq_solution", "sel_bnd_electrolyte_inlet", "p", "Pa"),
            eval(model, "AvSurface", "dset_liq_solution", "sel_bnd_electrolyte_outlet", "p", "Pa"),
            eval(model, "AvVolume", "dset_n2_solution", "sel_dom_n2_channel", "sqrt(u2^2+v2^2+w2^2)", "m/s"),
            eval(model, "MaxVolume", "dset_n2_solution", "sel_dom_n2_channel", "sqrt(u2^2+v2^2+w2^2)", "m/s"),
            eval(model, "AvSurface", "dset_n2_solution", "sel_bnd_n2_inlet", "p2", "Pa"),
            eval(model, "AvSurface", "dset_n2_solution", "sel_bnd_n2_outlet", "p2", "Pa")
        };
    }

    private static void compare(double[] before, double[] after) {
        if (before.length != after.length) {
            throw new IllegalStateException("FIELD_SIGNATURE_LENGTH_CHANGED");
        }
        for (int i = 0; i < before.length; i++) {
            if (!Double.isFinite(before[i]) || !Double.isFinite(after[i])) {
                throw new IllegalStateException("NONFINITE_FIELD_SIGNATURE: index=" + i);
            }
            final double relative = Math.abs(before[i] - after[i]) / Math.max(Math.max(Math.abs(before[i]), Math.abs(after[i])), 1e-30);
            System.out.println("M10A1_1_FIELD_SIGNATURE|index=" + i + "|before=" + format(before[i])
                + "|after=" + format(after[i]) + "|relative=" + format(relative));
            if (relative > 1e-12) {
                throw new IllegalStateException("SOLVED_FIELD_CHANGED: index=" + i + " relative=" + relative);
            }
        }
    }

    private static void validateShear(Model model, String component, String physics, String dataset, String walls, String fluid) {
        final String[] traction = nativeTraction(model.component(component).physics(physics));
        final String expression = tangentialMagnitude(traction[0], traction[1], traction[2]);
        final double average = eval(model, "AvSurface", dataset, walls, expression, "Pa");
        final double maximum = eval(model, "MaxSurface", dataset, walls, expression, "Pa");
        if (!Double.isFinite(average) || !Double.isFinite(maximum) || average < 0 || maximum < average) {
            throw new IllegalStateException("RELOADED_WALL_SHEAR_INVALID: fluid=" + fluid);
        }
        System.out.println("M10A1_1_WALL_SHEAR_RELOAD|fluid=" + fluid + "|average_Pa=" + format(average)
            + "|maximum_Pa=" + format(maximum));
    }

    private static double eval(Model model, String type, String dataset, String selection, String expression, String unit) {
        final String tag = "m10a1_repair_eval_" + (++serial);
        model.result().numerical().create(tag, type);
        try {
            model.result().numerical(tag).set("data", dataset);
            model.result().numerical(tag).selection().named(selection);
            model.result().numerical(tag).set("expr", new String[]{expression});
            model.result().numerical(tag).set("unit", new String[]{unit});
            final double[][] values = model.result().numerical(tag).getReal();
            return values[0][0];
        } finally {
            model.result().numerical().remove(tag);
        }
    }

    private static String runtime(String name) {
        try {
            return ((String) Class.forName(RUNTIME_CLASS).getField(name).get(null)).trim();
        } catch (Exception exception) {
            throw new IllegalStateException("RUNTIME_INPUT_MISSING: " + name, exception);
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.12g", value);
    }
}
