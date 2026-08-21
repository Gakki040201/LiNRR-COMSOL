import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.lang.reflect.Method;

public final class LiNRR_M10A4_FeatureMethodDumpProbe {
  private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
  private static final String C = "comp_species_liq_real";
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4FeatureMethodDumpProbe", MPH);
    try {
      m.component(C).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
      m.component(C).physics("np_probe").create("fcx", "Flux", 2);
      m.component(C).physics("np_probe").create("ConcX", "Concentration", 2);
      m.component(C).physics("np_probe").create("epx", "ElectricPotential", 2);
      m.component(C).physics("np_probe").create("einX", "ElectricInsulation", 2);
      m.component(C).physics("np_probe").create("ifX", "Inflow", 2);
      m.component(C).physics("np_probe").create("ofX", "Outflow", 2);
      for (String ft : new String[]{"fcx","ConcX","epx","einX","ifX","ofX"}) {
        Object f = m.component(C).physics("np_probe").feature(ft);
        System.out.println("===FEATURE " + ft + " class=" + f.getClass().getName());
        for (Method meth : f.getClass().getMethods()) {
          if (meth.getParameterCount() == 0 && !meth.getName().startsWith("wait")) {
            try {
              Object v = meth.invoke(f);
              if (v != null) {
                String s = v instanceof String[] ? String.join(",", (String[]) v) : v.toString();
                if (s.length() < 500) System.out.println("METH|" + meth.getName() + "|" + meth.getReturnType().getSimpleName() + "|" + s);
              }
            } catch (Throwable e) { }
          }
        }
      }
    } finally { ModelUtil.remove("M10A4FeatureMethodDumpProbe"); }
  }
}
