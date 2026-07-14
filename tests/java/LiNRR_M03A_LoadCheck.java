import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/** Independent, load-only structural audit for the editable M03A MPH. */
public final class LiNRR_M03A_LoadCheck {
    private static final String MPH =
        "F:\\LiNRR_COMSOL\\LiNRR_COMSOL_Codex_Starter\\models\\generated\\LiNRR_M03A_primary_current.mph";

    private LiNRR_M03A_LoadCheck() {}

    private static boolean has(String[] tags, String wanted) {
        for (String tag : tags) if (wanted.equals(tag)) return true;
        return false;
    }

    private static void requireTag(String[] tags, String wanted, String kind) {
        if (!has(tags, wanted)) {
            throw new IllegalStateException("Missing M03A " + kind + " tag: " + wanted);
        }
    }

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.load("M03ALoadCheck", MPH);
        requireTag(model.component().tags(), "comp1", "component");
        model.component("comp1").geom("geom1");
        model.component("comp1").mesh("mesh1");
        requireTag(model.component("comp1").physics().tags(), "cd", "physics");
        requireTag(model.component("comp1").physics("cd").feature().tags(),
            "anode_current", "physics feature");
        requireTag(model.component("comp1").physics("cd").feature().tags(),
            "cathode_ground", "physics feature");

        String[] selections = {"sel_electrolyte", "sel_inlet", "sel_outlet",
            "sel_anode_wall", "sel_cathode_wall"};
        for (String selection : selections) {
            requireTag(model.component("comp1").selection().tags(), selection, "selection");
        }
        requireTag(model.study().tags(), "std_primary", "study");
        requireTag(model.result().tags(), "pg_potential", "result plot");
        requireTag(model.result().tags(), "pg_current", "result plot");
        requireTag(model.result().tags(), "pg_voltage_scaling", "result plot");

        String[] parameters = {"Lcell", "Hcell", "Wcell", "T0", "kappa_el",
            "Icell", "Aelec", "j_app", "R_analytic", "V_analytic"};
        for (String parameter : parameters) {
            String expression = model.param().get(parameter);
            if (expression == null || expression.length() == 0) {
                throw new IllegalStateException("Missing or empty M03A global parameter: " + parameter);
            }
        }
        double voltage = model.param().evaluate("V_analytic", "V");
        if (!(voltage > 0.0) || Double.isInfinite(voltage) || Double.isNaN(voltage)) {
            throw new IllegalStateException("Reloaded V_analytic is invalid: " + voltage);
        }
        System.out.println("M03A_LOAD|PASS|PrimaryCurrentDistribution|editable structure and parameters loaded");
    }
}
