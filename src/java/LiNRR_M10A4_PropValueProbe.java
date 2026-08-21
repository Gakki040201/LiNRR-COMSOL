import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_PropValueProbe {
  private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
  private static final String C = "comp_species_liq_real";
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4PropValueProbe", MPH);
    try {
      m.component(C).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
      String[] map = {"fcx","Flux","ConcX","Concentration","epx","ElectricPotential","ifX","Inflow","ofX","Outflow","einX","ElectricInsulation"};
      for(int i=0;i<map.length;i+=2){
        m.component(C).physics("np_probe").create(map[i], map[i+1], 2);
      }
      for (String ft : new String[]{"fcx","ConcX","epx","ifX","ofX","einX"}) {
        Object f = m.component(C).physics("np_probe").feature(ft);
        String[] props = (String[]) f.getClass().getMethod("properties").invoke(f);
        System.out.println("===FEATURE " + ft + " props=" + String.join(",", props));
        for (String p : props) {
          try { System.out.println("PROP|" + p + "|string=" + f.getClass().getMethod("getString", String.class).invoke(f, p)); } catch(Throwable e) { System.out.println("PROP|" + p + "|getString_ERR=" + e.getMessage()); }
          try { Object a = f.getClass().getMethod("getStringArray", String.class).invoke(f, p); System.out.println("PROP|" + p + "|arr=" + (a instanceof String[] ? String.join(",", (String[]) a) : String.valueOf(a))); } catch(Throwable e) { }
          try { Object av = f.getClass().getMethod("getAllowedPropertyValues", String.class).invoke(f, p); System.out.println("PROP|" + p + "|allowed=" + (av instanceof String[] ? String.join(",", (String[]) av) : String.valueOf(av))); } catch(Throwable e) { }
          try { System.out.println("PROP|" + p + "|valtype=" + f.getClass().getMethod("getValueType", String.class).invoke(f, p)); } catch(Throwable e) { }
        }
      }
    } finally { ModelUtil.remove("M10A4PropValueProbe"); }
  }
}
