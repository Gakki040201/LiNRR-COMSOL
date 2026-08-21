import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_NPConfigProbe {
    private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
    private static final String C = "comp_species_liq_real";
    public static void main(String[] args) throws Exception {
        Model m = ModelUtil.load("M10A4NPConfigProbe", MPH);
        try {
            m.component(C).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
            m.param().set("D_test", "1e-10[m^2/s]", "probe");
            m.param().set("z_test", "1", "probe");

            setp(m, "cdm1", "u", "u_liq_species");
            setp(m, "cdm1", "v", "v_liq_species");
            setp(m, "cdm1", "w", "w_liq_species");
            setp(m, "cdm1", "u_spf", "u_liq_species");
            setp(m, "cdm1", "v_spf", "v_liq_species");
            setp(m, "cdm1", "w_spf", "w_liq_species");
            setp(m, "cdm1", "u_input", "u_liq_species");
            setp(m, "cdm1", "v_input", "v_liq_species");
            setp(m, "cdm1", "w_input", "w_liq_species");
            setp(m, "cdm1", "velx", "u_liq_species");
            setp(m, "cdm1", "vely", "v_liq_species");
            setp(m, "cdm1", "velz", "w_liq_species");
            setp(m, "cdm1", "D_c1", "D_test");
            setp(m, "cdm1", "D_c2", "D_test");
            setp(m, "cdm1", "D_c1_mat", "userdef");
            setp(m, "cdm1", "D_c2_mat", "userdef");
            setIndex(m, "sp1", "z", "1", 0);
            setIndex(m, "sp1", "z", "-1", 1);

            createFeat(m, "Conc1", "Concentration");
            createFeat(m, "Flux1", "Flux");
            createFeat(m, "Outflow1", "Outflow");
            createFeat(m, "Inflow1", "Inflow");
            createFeat(m, "Sym1", "Symmetry");
            createFeat(m, "Open1", "OpenBoundary");
            createFeat(m, "ElecPot1", "ElectrolytePotential");
            createFeat(m, "ElecCur1", "ElectrolyteCurrent");
            createFeat(m, "ElecIns1", "ElectricInsulation");
            createFeat(m, "ElecGround1", "ElectricGround");
            createFeat(m, "Current1", "Current");
            createFeat(m, "Potential1", "Potential");
        } finally { ModelUtil.remove("M10A4NPConfigProbe"); }
    }

    private static void setp(Model m, String feat, String prop, String val) {
        try { m.component(C).physics("np_probe").feature(feat).set(prop, val); System.out.println("SET_OK|" + feat + "|" + prop + "|" + val); }
        catch (Throwable e) { System.out.println("SET_FAIL|" + feat + "|" + prop + "|" + e.getMessage()); }
    }

    private static void setIndex(Model m, String feat, String prop, String val, int idx) {
        try { m.component(C).physics("np_probe").feature(feat).setIndex(prop, val, idx); System.out.println("SETINDEX_OK|" + feat + "|" + prop + "|idx=" + idx + "|" + val); }
        catch (Throwable e) { System.out.println("SETINDEX_FAIL|" + feat + "|" + prop + "|idx=" + idx + "|" + e.getMessage()); }
    }

    private static void createFeat(Model m, String tag, String type) {
        try { m.component(C).physics("np_probe").create(tag, type, 2); System.out.println("CREATE_OK|" + tag + "|" + type); }
        catch (Throwable e) { System.out.println("CREATE_FAIL|" + tag + "|" + type + "|" + e.getMessage()); }
    }
}
