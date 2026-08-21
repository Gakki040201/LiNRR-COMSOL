import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.lang.reflect.Method;

public final class LiNRR_M10A4_FeatureMethodProbe {
    private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
    public static void main(String[] args) throws Exception {
        Model m = ModelUtil.load("M10A4FeatureMethodProbe", MPH);
        try {
            String c = "comp_species_liq_real";
            m.component(c).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
            for (String ft : new String[]{"sp1","cdm1"}) {
                Object f = m.component(c).physics("np_probe").feature(ft);
                System.out.println("FEATURE_CLASS|" + ft + "|" + f.getClass().getName());
                for (Method meth : f.getClass().getMethods()) {
                    String n = meth.getName();
                    if (n.startsWith("set") && meth.getParameterCount() <= 3) {
                        System.out.println("METHOD|" + ft + "|" + n + "|" + meth.getParameterCount());
                    }
                }
            }
        } finally { ModelUtil.remove("M10A4FeatureMethodProbe"); }
    }
}