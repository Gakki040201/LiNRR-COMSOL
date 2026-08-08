import com.comsol.model.Model;
import com.comsol.model.physics.Physics;
import com.comsol.model.util.ModelUtil;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/** Read-only M10A1 probe for COMSOL-native flow traction/stress variables and licensed products. */
public final class LiNRR_M10A1_1_VariableProbe {
    private static final String RUNTIME_CLASS = "LiNRR_M10A1_1_RuntimeInputs";
    private static int serial = 0;

    private LiNRR_M10A1_1_VariableProbe() {}

    public static void main(String[] args) throws Exception {
        final String input = runtime("INPUT_MPH");
        final Model model = ModelUtil.load("M10A1Probe", input);
        try {
            System.out.println("M10A1_1_PROBE_INPUT=" + input);
            System.out.println("M10A1_1_PROBE_MODEL_USED_PRODUCTS=" + String.join(";", model.getUsedProducts()));
            for (String product : new String[]{"PIPEFLOW", "POROUSMEDIAFLOW", "CFD", "CHEM", "MICROFLUIDICS", "SUBSURFACEFLOW"}) {
                System.out.println("M10A1_1_LICENSE|product=" + product + "|available=" + ModelUtil.hasProduct(product));
            }
            probePhysics(model, "comp_electrolyte_flow", "spf_liq", "dset_liq_solution", "sel_bnd_electrolyte_walls");
            probePhysics(model, "comp_n2_flow", "spf_n2", "dset_n2_solution", "sel_bnd_n2_walls");
            measureArea(model, "comp_electrolyte_flow", "geom_electrolyte_fluid", "sel_bnd_electrolyte_gde_top");
            measureArea(model, "comp_electrolyte_flow", "geom_electrolyte_fluid", "sel_bnd_electrolyte_gde_bottom");
            measureArea(model, "comp_n2_flow", "geom_n2_channel_fluid", "sel_bnd_n2_gde_interface");
            System.out.println("M10A1_1_READ_ONLY_VARIABLE_PROBE=PASS");
        } finally {
            ModelUtil.remove("M10A1Probe");
        }
    }

    private static void probePhysics(Model model, String component, String physicsTag, String dataset, String walls) {
        final Physics physics = model.component(component).physics(physicsTag);
        final String[][] table = physics.featureInfo("info").getInfoTable("Expression", "recursive", "all");
        int matched = 0;
        String tx = null;
        for (String[] row : table) {
            final String joined = String.join("\t", row);
            final String lower = joined.toLowerCase(Locale.ROOT);
            if (lower.contains(".t_stressx") || lower.contains(".t_stressy") || lower.contains(".t_stressz")
                || lower.contains(".t_tracx") || lower.contains(".t_tracy") || lower.contains(".t_tracz")
                || lower.contains(".t_stress_tensorxx") || lower.contains("wall shear")) {
                System.out.println("M10A1_1_EQUATION_VIEW|component=" + component + "|physics=" + physicsTag
                    + "|row=" + clean(joined));
                matched++;
            }
            for (String cell : row) {
                if (cell != null && cell.matches("[A-Za-z][A-Za-z0-9_]*\\.T_stressx")) {
                    tx = cell;
                }
            }
        }
        if (matched == 0 || tx == null) {
            throw new IllegalStateException("NO_STRESS_OR_TRACTION_VARIABLES_IN_EQUATION_VIEW: " + physicsTag);
        }

        final String prefix = tx.substring(0, tx.length() - "T_stressx".length());
        final String ty = prefix + "T_stressy";
        final String tz = prefix + "T_stressz";
        final String tn = "((" + tx + ")*nx+(" + ty + ")*ny+(" + tz + ")*nz)";
        final String tau = "sqrt((" + tx + "-(" + tn + ")*nx)^2+(" + ty + "-(" + tn
            + ")*ny)^2+(" + tz + "-(" + tn + ")*nz)^2)";
        final String[] expressions = {tx, ty, tz, tau};
        final String[] units = {"Pa", "Pa", "Pa", "Pa"};
        for (int i = 0; i < expressions.length; i++) {
            final double value = eval(model, "MaxSurface", dataset, walls, expressions[i], units[i]);
            if (!Double.isFinite(value)) {
                throw new IllegalStateException("NONFINITE_NATIVE_TRACTION_PROBE: " + expressions[i]);
            }
            System.out.println("M10A1_1_NATIVE_VARIABLE_AVAILABLE|component=" + component + "|physics=" + physicsTag
                + "|expression=" + expressions[i] + "|max_value_Pa=" + format(value));
        }
        System.out.println("M10A1_1_NATIVE_WALL_SHEAR_EXPRESSION|physics=" + physicsTag + "|expression=" + tau);
    }

    private static double eval(Model model, String type, String dataset, String selection, String expression, String unit) {
        final String tag = "m10a1_probe_" + (++serial);
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

    private static void measureArea(Model model, String component, String geometry, String selection) {
        final int[] entities = model.component(component).selection(selection).entities(2);
        model.component(component).geom(geometry).measureFinal().selection().geom(2);
        model.component(component).geom(geometry).measureFinal().selection().set(entities);
        final double area = model.component(component).geom(geometry).measureFinal().getArea();
        System.out.println("M10A1_1_INTERFACE_AREA|component=" + component + "|selection=" + selection
            + "|entity_count=" + entities.length + "|area_mm2=" + format(area));
    }

    private static String runtime(String name) {
        try {
            return ((String) Class.forName(RUNTIME_CLASS).getField(name).get(null)).trim();
        } catch (Exception exception) {
            throw new IllegalStateException("RUNTIME_INPUT_MISSING: " + name, exception);
        }
    }

    private static String clean(String value) {
        return value.replace('\n', ' ').replace('\r', ' ').replace('|', '/');
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.12g", value);
    }
}
