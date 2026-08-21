import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_NPBCProbe {
  private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
  private static final String C = "comp_species_liq_real";
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4NPBCProbe", MPH);
    try {
      m.component(C).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
      m.component(C).physics("np_probe").selection().named("m10a3_sel_dom_electrolyte_fluid");
      String[] defs = {"fcx","ConcX","epx","einX","ifX","ofX","opX"};
      m.component(C).physics("np_probe").create("fcx", "Flux", 2);
      m.component(C).physics("np_probe").create("ConcX", "Concentration", 2);
      m.component(C).physics("np_probe").create("epx", "ElectricPotential", 2);
      m.component(C).physics("np_probe").create("einX", "ElectricInsulation", 2);
      m.component(C).physics("np_probe").create("ifX", "Inflow", 2);
      m.component(C).physics("np_probe").create("ofX", "Outflow", 2);
      m.component(C).physics("np_probe").create("opX", "OpenBoundary", 2);
      for (String ft : defs) {
        System.out.println("===FEATURE " + ft + " type=" + m.component(C).physics("np_probe").feature(ft).getType());
        for (String itype : new String[]{"Property","Settings","Expression","Equation","Weak","Constraint","Shape"}) {
          try {
            String[][] info = m.component(C).physics("np_probe").feature(ft).featureInfo("info").getInfoTable(itype, "recursive", "all");
            System.out.println("INFOTYPE_OK " + itype + " rows=" + (info == null ? -1 : info.length));
            if (info != null) {
              for (String[] row : info) {
                StringBuilder sb = new StringBuilder("ROW|" + itype + "|");
                for (String cell : row) sb.append(cell == null ? "<null>" : cell).append('|');
                System.out.println(sb);
              }
            }
          } catch (Throwable e) { System.out.println("INFOTYPE_FAIL " + itype + " " + e.getMessage()); }
        }
      }
    } finally { ModelUtil.remove("M10A4NPBCProbe"); }
  }
}
