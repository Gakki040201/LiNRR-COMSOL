import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_NPIndexProbe {
    private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
    public static void main(String[] args) throws Exception {
        Model m = ModelUtil.load("M10A4NPIndexProbe", MPH);
        try {
            String c = "comp_species_liq_real";
            m.component(c).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
            String[] props = {"zc","z","charge","z_c","z_c1","z_c2","z1","z2"};
            for (String p : props) {
                for (int idx : new int[]{0,1}) {
                    try { m.component(c).physics("np_probe").feature("sp1").setIndex(p, "1", idx); System.out.println("SETINDEX_OK|sp1|" + p + "|idx=" + idx); }
                    catch (Throwable e) { System.out.println("SETINDEX_FAIL|sp1|" + p + "|idx=" + idx + "|" + e.getMessage()); }
                }
            }
        } finally { ModelUtil.remove("M10A4NPIndexProbe"); }
    }
}