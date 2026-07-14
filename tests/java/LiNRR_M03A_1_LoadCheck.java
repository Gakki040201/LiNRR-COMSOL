import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/** Independent load-only structural audit for the editable M03A.1 MPH. */
public final class LiNRR_M03A_1_LoadCheck {
    private static final String MPH =
        "F:\\LiNRR_COMSOL\\LiNRR_COMSOL_Codex_Starter\\models\\generated\\LiNRR_M03A_1_calibration.mph";

    private LiNRR_M03A_1_LoadCheck() {}

    private static boolean has(String[] tags, String wanted) {
        for (String tag : tags) if (wanted.equals(tag)) return true;
        return false;
    }

    private static void requireTag(String[] tags, String wanted, String kind) {
        if (!has(tags, wanted)) {
            throw new IllegalStateException("Missing M03A.1 " + kind + " tag: " + wanted);
        }
    }

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.load("M03A1LoadCheck", MPH);
        requireTag(model.component().tags(), "comp_cal", "component");
        model.component("comp_cal").geom("geom_cal");
        model.component("comp_cal").mesh("mesh_cal");
        requireTag(model.component("comp_cal").physics().tags(), "cd_cal", "physics");
        requireTag(model.component("comp_cal").physics("cd_cal").feature().tags(),
            "anode_current_cal", "physics feature");
        requireTag(model.component("comp_cal").physics("cd_cal").feature().tags(),
            "cathode_ground_cal", "physics feature");
        requireTag(model.study().tags(), "std_primary_cal", "study");
        requireTag(model.result().tags(), "pg_potential_cal", "result plot");
        requireTag(model.result().tags(), "pg_current_cal", "result plot");
        requireTag(model.result().tags(), "pg_comparison_cal", "result plot");

        String[] selections = {"sel_electrolyte_cal", "sel_left_cal", "sel_right_cal",
            "sel_anode_cal", "sel_cathode_cal"};
        for (String selection : selections) {
            requireTag(model.component("comp_cal").selection().tags(), selection, "selection");
        }
        String[] parameters = {"Lcell", "Hcell", "Wcell", "Aelec", "T0", "kappa_el",
            "u_kappa_el", "Icell", "u_Icell", "j_app", "R_analytic", "V_analytic",
            "calibration_state_code", "calibration_mode_code"};
        for (String parameter : parameters) {
            String expression = model.param().get(parameter);
            if (expression == null || expression.length() == 0) {
                throw new IllegalStateException("Missing M03A.1 parameter: " + parameter);
            }
        }
        double voltage = model.param().evaluate("V_analytic", "V");
        if (!(voltage > 0.0) || Double.isNaN(voltage) || Double.isInfinite(voltage)) {
            throw new IllegalStateException("Reloaded M03A.1 voltage invalid: " + voltage);
        }
        String label = model.label();
        if (label == null || label.indexOf("M03A_1_calibration") < 0) {
            throw new IllegalStateException("Unexpected M03A.1 model label: " + label);
        }
        System.out.println("M03A1_LOAD|PASS|PrimaryCurrentDistribution|editable calibration structure loaded");
    }
}
