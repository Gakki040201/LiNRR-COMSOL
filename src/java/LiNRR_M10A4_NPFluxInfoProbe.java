import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_NPFluxInfoProbe {
  private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
  private static final String C = "comp_species_liq_real";
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4NPFluxInfoProbe", MPH);
    try {
      m.component(C).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
      m.component(C).physics("np_probe").selection().named("m10a3_sel_dom_electrolyte_fluid");
      m.component(C).physics("np_probe").create("fc", "Flux", 2);
      m.component(C).physics("np_probe").feature("fc").selection().named("m10a3_sel_bnd_electrolyte_gde_top");
      String[] types = {"Expression","Equation","Weak","Constraint","Shape"};
      for (String ft : new String[]{"fc","ein1","nflx1","sp1","cdm1"}) {
        System.out.println("===FEATURE " + ft + " type=" + m.component(C).physics("np_probe").feature(ft).getType());
        for (String itype : types) {
          try {
            String[][] info = m.component(C).physics("np_probe").feature(ft).featureInfo("info").getInfoTable(itype, "recursive", "all");
            System.out.println("INFOTYPE_OK " + itype + " rows=" + (info == null ? -1 : info.length));
            if (info != null) {
              for (String[] row : info) {
                StringBuilder sb = new StringBuilder("ROW|" + itype + "|");
                for (String cell : row) sb.append(cell == null ? "<null>" : cell).append('|');
                String str = sb.toString();
                if (str.contains("npe.J") || str.contains("npe.bndFlux") || str.contains("npe.nJl") || str.contains("J0") || str.contains("c1") || str.contains("c2") || str.contains("V") || str.contains("Flux") || str.contains("test")) System.out.println(str);
              }
            }
          } catch (Throwable e) { System.out.println("INFOTYPE_FAIL " + itype + " " + e.getClass().getSimpleName() + " " + e.getMessage()); }
        }
      }
    } finally { ModelUtil.remove("M10A4NPFluxInfoProbe"); }
  }
}
