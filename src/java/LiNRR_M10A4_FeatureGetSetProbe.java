import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.lang.reflect.Method;

public final class LiNRR_M10A4_FeatureGetSetProbe {
  private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
  private static final String C = "comp_species_liq_real";
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4FeatureGetSetProbe", MPH);
    try {
      m.component(C).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
      m.component(C).physics("np_probe").create("fcx", "Flux", 2);
      m.component(C).physics("np_probe").create("ConcX", "Concentration", 2);
      m.component(C).physics("np_probe").create("epx", "ElectricPotential", 2);
      for (String ft : new String[]{"fcx","ConcX","epx"}) {
        Object f = m.component(C).physics("np_probe").feature(ft);
        System.out.println("===FEATURE " + ft);
        for (Method meth : f.getClass().getMethods()) {
          String n = meth.getName();
          if ((n.startsWith("get")||n.startsWith("set")||n.startsWith("is")) && !n.equals("getClass")) {
            Class<?>[] ps = meth.getParameterTypes();
            StringBuilder sb = new StringBuilder();
            for(Class<?> p: ps) sb.append(p.getSimpleName()).append(',');
            System.out.println("METH|" + n + "|" + meth.getReturnType().getSimpleName() + "|" + sb);
          }
        }
      }
    } finally { ModelUtil.remove("M10A4FeatureGetSetProbe"); }
  }
}
