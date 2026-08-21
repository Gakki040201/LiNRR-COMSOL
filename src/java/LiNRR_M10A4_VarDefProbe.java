import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_VarDefProbe {
  private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4VarDefProbe", MPH);
    try {
      String c = "comp_species_liq_real";
      for (String vt : m.component(c).variable().tags()) {
        System.out.println("VAR_TAG=" + vt + " label=" + m.component(c).variable(vt).label());
        String[] names = m.component(c).variable(vt).varnames();
        for (String n : names) {
          try { System.out.println("VARDEF|" + vt + "|" + n + "|" + m.component(c).variable(vt).get(n)); }
          catch (Throwable e) { System.out.println("VARDEF_ERR|" + vt + "|" + n + "|" + e.getMessage()); }
        }
      }
      for (String vt : new String[]{"spf_liq_local","gfp_n2_liq","gfp_nh3_liq","tds_tracer"}) {
        System.out.println("PHYS=" + vt + " type=" + m.component(c).physics(vt).getType() + " fields=" + String.join(",", m.component(c).physics(vt).field().tags()));
        String[] ft = m.component(c).physics(vt).feature().tags();
        System.out.println("FEATURES=" + String.join(",", ft));
      }
      System.out.println("DATASET dset_species_liq_flow label=" + m.result().dataset("dset_species_liq_flow").label());
      System.out.println("DATASET props solution=" + m.result().dataset("dset_species_liq_flow").getString("solution") + " comp=" + m.result().dataset("dset_species_liq_flow").getString("comp"));
      String[] props = new String[]{"solution","comp","geom","frame","spatial","studysolnum","time","userexplicit","evalmode"};
      for (String p : props) { try { System.out.println("DATASET_PROP|" + p + "|" + m.result().dataset("dset_species_liq_flow").getString(p)); } catch (Throwable e) { System.out.println("DATASET_PROP_ERR|" + p + "|" + e.getMessage()); } }
    } finally { ModelUtil.remove("M10A4VarDefProbe"); }
  }
}
