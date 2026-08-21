import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_NPChargeProbe {
    private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
    public static void main(String[] args) throws Exception {
        Model m = ModelUtil.load("M10A4NPChargeProbe", MPH);
        try {
            String c = "comp_species_liq_real";
            m.component(c).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
            String[] feats = {"sp1", "cdm1"};
            String[] props = {"z_c1","z_c2","z1","z2","Z_c1","Z_c2","charge_c1","charge_c2","charge1","charge2","zc1","zc2","ion1","ion2"};
            for (String f : feats) {
                for (String p : props) {
                    try {
                        m.component(c).physics("np_probe").feature(f).set(p, "1");
                        System.out.println("SET_OK|" + f + "|" + p);
                    } catch (Throwable e) {
                        System.out.println("SET_FAIL|" + f + "|" + p + "|" + e.getMessage());
                    }
                }
            }
        } finally { ModelUtil.remove("M10A4NPChargeProbe"); }
    }
}