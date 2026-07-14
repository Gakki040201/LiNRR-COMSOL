import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/** Load-only structural check for the generated editable M02 MPH. */
public final class LiNRR_M02_LoadCheck {
    private LiNRR_M02_LoadCheck() {}

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.load("M02LoadCheck",
            "models/generated/LiNRR_M02_transport.mph");
        model.component("comp1").geom("geom1");
        model.component("comp1").mesh("mesh1");
        model.component("comp1").physics("spf");
        model.component("comp1").physics("tds");
        model.component("comp1").selection("sel_inlet");
        model.component("comp1").selection("sel_outlet");
        model.component("comp1").selection("sel_cathode_wall");
        model.study("std_A");
        model.study("std_B");
        model.result("pg_N2");
        model.result("pg_NH3");
        model.result("pg_cathode");
        System.out.println("M02_LOAD_CHECK|PASS|editable structure loaded");
    }
}
