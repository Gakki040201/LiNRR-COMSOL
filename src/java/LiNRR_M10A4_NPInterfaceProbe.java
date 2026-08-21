import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.lang.reflect.Method;

public final class LiNRR_M10A4_NPInterfaceProbe {
  private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
  private static final String C = "comp_species_liq_real";
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4NPInterfaceProbe", MPH);
    try {
      m.component(C).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
      Object ph = m.component(C).physics("np_probe");
      System.out.println("PHYSICS_CLASS=" + ph.getClass().getName());
      for (Method meth : ph.getClass().getMethods()) {
        String n = meth.getName();
        if (n.matches(".*([Ff]eature|[Ii]nfo|[Ss]et|[Cc]reate).*")) {
          System.out.println("PHYS_METHOD|" + n + "|" + meth.getParameterCount());
        }
      }
      try { m.component(C).physics("np_probe").feature("cdm1").set("u", new String[]{"u_liq_species","v_liq_species","w_liq_species"}); System.out.println("SET_VECTOR_OK|cdm1|u"); } catch (Throwable e) { System.out.println("SET_VECTOR_FAIL|cdm1|u|" + e.getMessage()); }
      String[] bc = new String[]{"Electrode","ElectrodeCurrent","CurrentSource","Ground","ElectricPotential","Potential","Concentration","Flux","Inflow","Outflow","NoFlux","Symmetry","OpenBoundary","ElectricInsulation","ElectrolytePotential","ElectrolyteCurrent","ElectricGround","Current","ElectrodeSurface","BoundaryElectrode","InwardCurrentDensity","AverageCurrentDensity"};
      for (String s : bc) {
        String tag = "x_" + s.replaceAll("[^A-Za-z0-9_]","");
        try { m.component(C).physics("np_probe").create(tag, s, 2); System.out.println("CREATE_OK|"+s+"|tag="+tag); }
        catch (Throwable e) { System.out.println("CREATE_FAIL|"+s+"|"+e.getMessage()); }
      }
    } finally { ModelUtil.remove("M10A4NPInterfaceProbe"); }
  }
}
