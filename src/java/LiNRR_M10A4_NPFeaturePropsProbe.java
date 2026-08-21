import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_NPFeaturePropsProbe {
  private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
  private static final String C = "comp_species_liq_real";
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4NPFeaturePropsProbe", MPH);
    try {
      m.component(C).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
      m.component(C).physics("np_probe").selection().named("m10a3_sel_dom_electrolyte_fluid");
      m.component(C).physics("np_probe").create("fc", "Flux", 2);
      m.component(C).physics("np_probe").feature("fc").selection().named("m10a3_sel_bnd_electrolyte_gde_top");
      m.component(C).physics("np_probe").create("ep", "ElectricPotential", 2);
      m.component(C).physics("np_probe").feature("ep").selection().named("m10a3_sel_bnd_electrolyte_gde_bottom");
      for (String ft : new String[]{"sp1","cdm1","fc","ep","nflx1","ein1","init1","dcont1"}) {
        System.out.println("===FEATURE " + ft + " type=" + m.component(C).physics("np_probe").feature(ft).getType());
        String[][] info = null;
        for (String itype : new String[]{"Property","Expression","Equation","Settings"}) {
          try { info = m.component(C).physics("np_probe").feature(ft).featureInfo("info").getInfoTable(itype, "recursive", "all"); System.out.println("INFOTYPE_OK " + itype); }
          catch (Throwable e) { System.out.println("INFOTYPE_FAIL " + itype + " " + e.getMessage()); info = null; }
          if (info != null) {
            for (String[] row : info) {
              StringBuilder sb = new StringBuilder("ROW|" + itype + "|");
              for (String cell : row) sb.append(cell == null ? "<null>" : cell).append('|');
              System.out.println(sb);
            }
          }
        }
      }
    } finally { ModelUtil.remove("M10A4NPFeaturePropsProbe"); }
  }
}
