import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.lang.reflect.Method;

public final class LiNRR_M10A4_FeatureMethodsProbe {
  private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
  private static final String C = "comp_species_liq_real";
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4FeatureMethodsProbe2", MPH);
    try {
      m.component(C).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
      m.component(C).physics("np_probe").create("fc", "Flux", 2);
      m.component(C).physics("np_probe").create("ep", "ElectricPotential", 2);
      for (String ft : new String[]{"fc","ep","cdm1","sp1"}) {
        Object f = m.component(C).physics("np_probe").feature(ft);
        System.out.println("=== " + ft + " " + f.getClass().getName());
        for (Method meth : f.getClass().getMethods()) {
          if (meth.getParameterCount() == 0 && (meth.getName().startsWith("get") || meth.getName().startsWith("is") || meth.getName().startsWith("set") || meth.getName().equals("toString"))) {
            try { Object v = meth.invoke(f); System.out.println("METH|" + ft + "|" + meth.getName() + "|" + (v == null ? "<null>" : v.toString())); }
            catch (Throwable e) { System.out.println("METH_ERR|" + ft + "|" + meth.getName() + "|" + e.getMessage()); }
          }
        }
      }
    } finally { ModelUtil.remove("M10A4FeatureMethodsProbe2"); }
  }
}
