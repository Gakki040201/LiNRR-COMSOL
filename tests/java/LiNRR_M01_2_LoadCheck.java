import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/** Independent-process reload check for the M01.2 editable MPH. */
public final class LiNRR_M01_2_LoadCheck {
    private static final String SOURCE =
        "models/generated/LiNRR_M01_2_flow_verification.mph";

    private LiNRR_M01_2_LoadCheck() {}

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.load("M01_2_Reload", SOURCE);
        requireOne(model, "sel_electrolyte", 2);
        requireOne(model, "sel_inlet", 1);
        requireOne(model, "sel_outlet", 1);
        requireOne(model, "sel_anode_wall", 1);
        requireOne(model, "sel_cathode_wall", 1);
        double[][] center = model.result().numerical("m012_u_center").getReal();
        if (center == null || center.length == 0 || center[0].length == 0 ||
            !Double.isFinite(center[0][0])) {
            throw new IllegalStateException("M01.2 center velocity unavailable after reload.");
        }
        System.out.println("M01_2_RELOAD|PASS|independent MPH reload, named " +
            "selections, retained solution, and center evaluation succeeded");
        ModelUtil.remove("M01_2_Reload");
    }

    private static void requireOne(Model model, String tag, int dim) {
        int count = model.component("comp1").selection(tag).entities(dim).length;
        if (count != 1) {
            throw new IllegalStateException("FAILED_SELECTION_MAPPING after reload: " +
                tag + " count=" + count);
        }
    }
}
