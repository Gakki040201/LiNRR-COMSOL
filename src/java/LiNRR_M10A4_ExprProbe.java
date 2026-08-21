import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_ExprProbe {
    private static final String MPH =
        "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
    private static final String COMP = "comp_species_liq_real";
    private static final String GEOM = "geom_electrolyte_fluid1";
    private static final String DOM = "m10a3_sel_dom_electrolyte_fluid";
    private static final String CATHODE = "m10a3_sel_bnd_electrolyte_gde_top";
    private static final String ANODE = "m10a3_sel_bnd_electrolyte_gde_bottom";
    private static int serial = 0;

    public static void main(String[] args) throws Exception {
        Model m = ModelUtil.load("M10A4ExprProbe", MPH);
        try {
            p(m, "kappa_M10A4_nominal", "0.3[S/m]", "probe");
            p(m, "j_app_sensitivity", "10[A/m^2]", "probe");
            p(m, "phi_l_cathode_ref", "0[V]", "probe");
            m.component(COMP).physics().create("cd_probe", "PrimaryCurrentDistribution", GEOM);
            m.component(COMP).physics("cd_probe").selection().named(DOM);
            m.component(COMP).physics("cd_probe").feature("ice1").set("sigmal_mat", "userdef");
            m.component(COMP).physics("cd_probe").feature("ice1").set("sigmal", "kappa_M10A4_nominal");
            m.component(COMP).physics("cd_probe").create("anode_current", "ElectrolyteCurrent", 2);
            m.component(COMP).physics("cd_probe").feature("anode_current").selection().named(ANODE);
            m.component(COMP).physics("cd_probe").feature("anode_current").set("IonicCurrentType", "AverageCurrentDensity");
            m.component(COMP).physics("cd_probe").feature("anode_current").set("Ial", "j_app_sensitivity");
            m.component(COMP).physics("cd_probe").create("cathode_ground", "ElectrolytePotential", 2);
            m.component(COMP).physics("cd_probe").feature("cathode_ground").selection().named(CATHODE);
            m.component(COMP).physics("cd_probe").feature("cathode_ground").set("philbnd", "phi_l_cathode_ref");
            m.study().create("std_expr_probe");
            m.study("std_expr_probe").create("stat", "Stationary");
            for (String c : m.component().tags()) for (String ph : m.component(c).physics().tags())
                m.study("std_expr_probe").feature("stat").activate(ph, ph.equals("cd_probe"));
            m.study("std_expr_probe").run();
            String[] sol = m.study("std_expr_probe").getSolverSequences("SolverSequence");
            m.result().dataset().create("dset_expr_probe", "Solution");
            m.result().dataset("dset_expr_probe").set("solution", sol[sol.length - 1]);
            m.result().dataset("dset_expr_probe").set("comp", COMP);

            test(m, "AvSurface", "cd_probe.phil", CATHODE, 2, "V");
            test(m, "AvSurface", "phil", CATHODE, 2, "V");
            test(m, "AvSurface", "cd_probe.electricpotentialionicphase", CATHODE, 2, "V");
            test(m, "MinVolume", "cd_probe.phil", DOM, 3, "V");
            test(m, "MinVolume", "phil", DOM, 3, "V");
            test(m, "IntSurface", "-kappa_M10A4_nominal*(d(cd_probe.phil,x)*nx+d(cd_probe.phil,y)*ny+d(cd_probe.phil,z)*nz)", CATHODE, 2, "A");
            test(m, "IntSurface", "-kappa_M10A4_nominal*(d(phil,x)*nx+d(phil,y)*ny+d(phil,z)*nz)", CATHODE, 2, "A");
            test(m, "IntSurface", "-kappa_M10A4_nominal*(d(cd_probe.electricpotentialionicphase,x)*nx+d(cd_probe.electricpotentialionicphase,y)*ny+d(cd_probe.electricpotentialionicphase,z)*nz)", CATHODE, 2, "A");
        } finally {
            ModelUtil.remove("M10A4ExprProbe");
        }
    }

    private static void test(Model m, String type, String expr, String sel, int dim, String unit) {
        String t = "expr_ev_" + (++serial);
        try {
            m.result().numerical().create(t, type);
            m.result().numerical(t).set("data", "dset_expr_probe");
            m.result().numerical(t).selection().geom(GEOM, dim);
            m.result().numerical(t).selection().set(m.component(COMP).selection(sel).entities(dim));
            m.result().numerical(t).set("expr", new String[]{expr});
            m.result().numerical(t).set("unit", new String[]{unit});
            if (type.startsWith("Int")) {
                m.result().numerical(t).set("intorderactive", true);
                m.result().numerical(t).set("intorder", 8);
            }
            double[][] v = m.result().numerical(t).getReal();
            System.out.println("EXPR_OK|type=" + type + "|expr=" + expr + "|value=" + (v == null || v.length == 0 || v[0].length == 0 ? "empty" : v[0][v[0].length - 1]));
        } catch (Throwable e) {
            System.out.println("EXPR_FAIL|type=" + type + "|expr=" + expr + "|error=" + e.getMessage());
        } finally {
            try { m.result().numerical().remove(t); } catch (Throwable ignore) {}
        }
    }

    private static void p(Model m, String n, String v, String d) { m.param().set(n, v, d); }
}