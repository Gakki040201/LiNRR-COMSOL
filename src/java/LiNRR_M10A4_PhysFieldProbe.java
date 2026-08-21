import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.lang.reflect.Method;

public final class LiNRR_M10A4_PhysFieldProbe {
    private static final String MPH =
        "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
    public static void main(String[] args) throws Exception {
        Model m = ModelUtil.load("M10A4PhysFieldProbe", MPH);
        try {
            String c = "comp_species_liq_real";
            m.component(c).physics().create("cd_probe", "PrimaryCurrentDistribution", "geom_electrolyte_fluid1");
            m.component(c).physics("cd_probe").selection().named("m10a3_sel_dom_electrolyte_fluid");
            String[] tags = m.component(c).physics("cd_probe").field().tags();
            System.out.println("FIELD_TAGS=" + String.join(",", tags));
            for (String t : tags) {
                Object f = m.component(c).physics("cd_probe").field(t);
                System.out.println("FIELD|" + t + "|class=" + (f == null ? "null" : f.getClass().getName()));
                if (f == null) continue;
                for (Method meth : f.getClass().getMethods()) {
                    if (meth.getParameterCount() == 0) {
                        try {
                            Object v = meth.invoke(f);
                            if (v != null) System.out.println("METHOD|" + t + "|" + meth.getName() + "|" + v);
                        } catch (Throwable e) {
                            System.out.println("METHOD|" + t + "|" + meth.getName() + "|ERR|" + e);
                        }
                    }
                }
            }
        } finally {
            ModelUtil.remove("M10A4PhysFieldProbe");
        }
    }
}