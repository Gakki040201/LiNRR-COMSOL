import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.lang.reflect.Method;

public final class LiNRR_M10A4_TDSProbe {
  private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
  private static final String C = "comp_species_liq_real";
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4TDSProbe", MPH);
    try {
      System.out.println("EXISTING_PHYSICS=" + String.join(",", m.component(C).physics().tags()));
      for (String ph : new String[]{"tds_tracer","tds_n2g"}) {
        if (!m.component(C).physics().hasTag(ph)) { System.out.println("MISSING " + ph); continue; }
        System.out.println("===PHYSICS " + ph + " type=" + m.component(C).physics(ph).getType());
        for (String ft : m.component(C).physics(ph).feature().tags()) {
          Object f = m.component(C).physics(ph).feature(ft);
          String[] props = (String[]) f.getClass().getMethod("properties").invoke(f);
          System.out.println("FEATURE|" + ft + "|type=" + f.getClass().getMethod("getType").invoke(f) + "|props=" + String.join(",", props));
          for (String p : props) {
            try { Object v = f.getClass().getMethod("getString", String.class).invoke(f, p); System.out.println("  PROP|" + p + "|string=" + v); } catch(Throwable e) {}
            try { Object av = f.getClass().getMethod("getAllowedPropertyValues", String.class).invoke(f, p); if (av instanceof String[] && ((String[])av).length > 0 && ((String[])av).length <= 30) System.out.println("  PROP|" + p + "|allowed=" + String.join(",", (String[])av)); } catch(Throwable e) {}
          }
        }
      }
      m.component(C).physics().create("tds_probe", "DilutedSpecies", "geom_electrolyte_fluid1");
      System.out.println("===NEW_DILUTED type=" + m.component(C).physics("tds_probe").getType());
      for (String ft : m.component(C).physics("tds_probe").feature().tags()) {
        Object f = m.component(C).physics("tds_probe").feature(ft);
        String[] props = (String[]) f.getClass().getMethod("properties").invoke(f);
        System.out.println("FEATURE|" + ft + "|type=" + f.getClass().getMethod("getType").invoke(f) + "|props=" + String.join(",", props));
        for (String p : props) {
          try { Object v = f.getClass().getMethod("getString", String.class).invoke(f, p); System.out.println("  PROP|" + p + "|string=" + v); } catch(Throwable e) {}
          try { Object av = f.getClass().getMethod("getAllowedPropertyValues", String.class).invoke(f, p); if (av instanceof String[] && ((String[])av).length > 0 && ((String[])av).length <= 30) System.out.println("  PROP|" + p + "|allowed=" + String.join(",", (String[])av)); } catch(Throwable e) {}
        }
      }
      String[] btypes = {"Flux","Concentration","Outflow","Inflow","NoFlux","Symmetry","OpenBoundary"};
      for (String bt : btypes) {
        try { m.component(C).physics("tds_probe").create("bc_" + bt, bt, 2); System.out.println("CREATE_OK|" + bt); } catch(Throwable e) { System.out.println("CREATE_FAIL|" + bt + "|" + e.getMessage()); }
      }
    } finally { ModelUtil.remove("M10A4TDSProbe"); }
  }
}
