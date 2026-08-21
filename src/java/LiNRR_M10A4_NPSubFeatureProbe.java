import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_NPSubFeatureProbe {
    private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
    public static void main(String[] args) throws Exception {
        Model m = ModelUtil.load("M10A4NPSubFeatureProbe", MPH);
        try {
            String c = "comp_species_liq_real";
            m.component(c).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
            for (String ft : m.component(c).physics("np_probe").feature().tags()) {
                System.out.println("FEATURE|" + ft + "|type=" + m.component(c).physics("np_probe").feature(ft).getType());
                try {
                    String[] subs = m.component(c).physics("np_probe").feature(ft).feature().tags();
                    for (String s : subs) System.out.println("  SUB|" + ft + "|" + s + "|type=" + m.component(c).physics("np_probe").feature(ft).feature(s).getType());
                } catch (Throwable e) { System.out.println("  SUB_ERR|" + ft + "|" + e.getMessage()); }
            }
        } finally { ModelUtil.remove("M10A4NPSubFeatureProbe"); }
    }
}