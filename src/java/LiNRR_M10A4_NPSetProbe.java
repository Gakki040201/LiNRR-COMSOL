import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_NPSetProbe {
    private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
    public static void main(String[] args) throws Exception {
        Model m = ModelUtil.load("M10A4NPSetProbe", MPH);
        try {
            String c = "comp_species_liq_real";
            m.component(c).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
            m.param().set("D_test", "1e-9[m^2/s]", "probe");
            m.param().set("um_test", "1e-13[s*mol/kg]", "probe");
            m.param().set("z_test", "1", "probe");
            test(m, "np_probe", "cdm1", "D_c1", "D_test");
            test(m, "np_probe", "cdm1", "D_c2", "D_test");
            test(m, "np_probe", "cdm1", "D_c1_mat", "userdef");
            test(m, "np_probe", "cdm1", "D_c2_mat", "userdef");
            test(m, "np_probe", "cdm1", "um_c1", "um_test");
            test(m, "np_probe", "cdm1", "um_c2", "um_test");
            test(m, "np_probe", "cdm1", "um_c1_mat", "userdef");
            test(m, "np_probe", "cdm1", "um_c2_mat", "userdef");
            test(m, "np_probe", "cdm1", "u_c1", "um_test");
            test(m, "np_probe", "cdm1", "u_c2", "um_test");
            test(m, "np_probe", "sp1", "z_c1", "z_test");
            test(m, "np_probe", "sp1", "z_c2", "z_test");
            test(m, "np_probe", "sp1", "z1", "z_test");
            test(m, "np_probe", "sp1", "z2", "z_test");
        } finally { ModelUtil.remove("M10A4NPSetProbe"); }
    }
    private static void test(Model m, String phys, String feat, String prop, String val) {
        try { m.component("comp_species_liq_real").physics(phys).feature(feat).set(prop, val); System.out.println("SET_OK|" + feat + "|" + prop + "|" + val); }
        catch (Throwable e) { System.out.println("SET_FAIL|" + feat + "|" + prop + "|" + e.getMessage()); }
    }
}