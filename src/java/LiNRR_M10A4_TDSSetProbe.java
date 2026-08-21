import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_TDSSetProbe {
  private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
  private static final String C = "comp_species_liq_real";
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4TDSSetProbe", MPH);
    try {
      m.param().set("D_li_a4b_test", "1e-9[m^2/s]", "test");
      m.param().set("um_li_a4b_test", "1e-13[s*mol/kg]", "test");
      m.param().set("c_bulk_test", "1000[mol/m^3]", "test");
      m.param().set("phi_test", "0[V]", "test");
      m.param().set("u_test", "u_liq_species", "test");
      m.param().set("v_test", "v_liq_species", "test");
      m.param().set("w_test", "w_liq_species", "test");
      m.component(C).physics().create("tds_test", "DilutedSpecies", "geom_electrolyte_fluid1", new String[]{"cLi_test"});
      try { m.component(C).physics("tds_test").selection().named("m10a3_sel_dom_electrolyte_fluid"); System.out.println("SEL_OK"); } catch(Throwable e) { System.out.println("SEL_FAIL " + e.getMessage()); }
      for (String ft : m.component(C).physics("tds_test").feature().tags()) System.out.println("FEATURE|" + ft + "|" + m.component(C).physics("tds_test").feature(ft).getType());
      setp(m, "tds_test", "sp1", "z", "1");
      setp(m, "tds_test", "cdm1", "u", "u_test");
      setp(m, "tds_test", "cdm1", "v", "v_test");
      setp(m, "tds_test", "cdm1", "w", "w_test");
      setp(m, "tds_test", "cdm1", "D_cLi_test_mat", "userdef");
      setp(m, "tds_test", "cdm1", "D_cLi_test", "D_li_a4b_test");
      setp(m, "tds_test", "cdm1", "um", "um_li_a4b_test");
      setp(m, "tds_test", "cdm1", "V", "phi_test");
      setp(m, "tds_test", "init1", "initc", "c_bulk_test");
      m.component(C).physics("tds_test").create("cath_li", "FluxBoundary", 2);
      m.component(C).physics("tds_test").feature("cath_li").selection().named("m10a3_sel_bnd_electrolyte_gde_top");
      setpi(m, "tds_test", "cath_li", "species", new int[]{1});
      setp(m, "tds_test", "cath_li", "FluxType", "GeneralInwardFlux");
      setps(m, "tds_test", "cath_li", "J0", new String[]{"-1[mol/(m^2*s)]"});
      m.component(C).physics("tds_test").create("in_li", "Inflow", 2);
      m.component(C).physics("tds_test").feature("in_li").selection().named("m10a3_sel_bnd_electrolyte_inlet");
      setp(m, "tds_test", "in_li", "BoundaryConditionType", "FluxDanckwerts");
      setps(m, "tds_test", "in_li", "c0", new String[]{"c_bulk_test"});
      m.component(C).physics("tds_test").create("out_li", "Outflow", 2);
      m.component(C).physics("tds_test").feature("out_li").selection().named("m10a3_sel_bnd_electrolyte_outlet");
      System.out.println("DONE");
    } finally { ModelUtil.remove("M10A4TDSSetProbe"); }
  }
  private static void setp(Model m, String ph, String ft, String prop, String val) {
    try { m.component("comp_species_liq_real").physics(ph).feature(ft).set(prop, val); System.out.println("SET_OK|" + ft + "|" + prop + "|" + val); }
    catch(Throwable e) { System.out.println("SET_FAIL|" + ft + "|" + prop + "|" + e.getMessage()); }
  }
  private static void setps(Model m, String ph, String ft, String prop, String[] val) {
    try { m.component("comp_species_liq_real").physics(ph).feature(ft).set(prop, val); System.out.println("SETARR_OK|" + ft + "|" + prop); }
    catch(Throwable e) { System.out.println("SETARR_FAIL|" + ft + "|" + prop + "|" + e.getMessage()); }
  }
  private static void setpi(Model m, String ph, String ft, String prop, int[] val) {
    try { m.component("comp_species_liq_real").physics(ph).feature(ft).set(prop, val); System.out.println("SETINT_OK|" + ft + "|" + prop); }
    catch(Throwable e) { System.out.println("SETINT_FAIL|" + ft + "|" + prop + "|" + e.getMessage()); }
  }
}
