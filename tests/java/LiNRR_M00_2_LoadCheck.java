import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/** Independent-process reload check for the M00.2 editable MPH. */
public final class LiNRR_M00_2_LoadCheck {
    private static final String SOURCE =
        "models/generated/LiNRR_M00_2_geometry_audit.mph";

    private LiNRR_M00_2_LoadCheck() {}

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.load("M00_2_Reload", SOURCE);
        requireOne(model, "sel_electrolyte_m002", 2);
        requireOne(model, "sel_inlet_m002", 1);
        requireOne(model, "sel_outlet_m002", 1);
        requireOne(model, "sel_upper_m002", 1);
        requireOne(model, "sel_lower_m002", 1);
        model.component("comp1").geom("geom1").run();
        model.component("comp1").mesh("mesh1").run();
        System.out.println("M00_2_RELOAD|PASS|independent MPH reload, geometry, " +
            "named selections, and mesh build succeeded");
        ModelUtil.remove("M00_2_Reload");
    }

    private static void requireOne(Model model, String tag, int dim) {
        int count = model.component("comp1").selection(tag).entities(dim).length;
        if (count != 1) {
            throw new IllegalStateException("FAILED_SELECTION_MAPPING after reload: " +
                tag + " count=" + count);
        }
    }
}
