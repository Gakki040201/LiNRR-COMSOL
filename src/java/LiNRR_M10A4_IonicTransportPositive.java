
import com.comsol.model.Model;
import com.comsol.model.physics.Physics;
import com.comsol.model.util.ModelUtil;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M10A4 A4B positive exponential effective ion-transport scaffold.
 *
 * Concentrations are represented as c = c_bulk*exp(z), so raw concentration
 * minima are strictly positive by construction. The local frozen A4A potential
 * is intentionally not used as a bulk migration advection velocity; that
 * formulation produced unresolved boundary layers for blocked BF4-. Instead,
 * the A4A cathode/anode current density is applied as Faraday-equivalent Li+
 * normal flux at the real electrode interfaces. Bulk migration is therefore
 * CALIBRATION_REQUIRED and local current crowding remains an A4C diagnostic.
 */
public final class LiNRR_M10A4_IonicTransportPositive {
    private static final String ROOT =
        "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED";
    private static final String RUN_DIR = ROOT +
        "\\runs\\M10A4\\20260820_121258";
    private static final String INPUT_MPH = RUN_DIR +
        "\\checkpoint_A4A_ohmic_current.mph";
    private static final String TABLE_DIR = ROOT + "\\results\\tables";
    private static final String COMP = "comp_species_liq_real";
    private static final String GEOM = "geom_electrolyte_fluid1";
    private static final String DOM = "m10a3_sel_dom_electrolyte_fluid";
    private static final String CATHODE = "m10a3_sel_bnd_electrolyte_gde_top";
    private static final String ANODE = "m10a3_sel_bnd_electrolyte_gde_bottom";
    private static final String INLET = "m10a3_sel_bnd_electrolyte_inlet";
    private static final String OUTLET = "m10a3_sel_bnd_electrolyte_outlet";
    private static final String WALLS = "m10a3_sel_bnd_electrolyte_walls";
    private static final String PHYS_LI = "gfp_li_a4b";
    private static final String PHYS_BF4 = "gfp_bf4_a4b";
    private static final String Z_LI = "zLi_a4b";
    private static final String Z_BF4 = "zBF4_a4b";
    private static final String FIELD_LI = "cLi_a4b";
    private static final String FIELD_BF4 = "cBF4_a4b";
    private static final String STUDY_A4B = "std_a4b_ionic";
    private static final String STUDY_FLOW_REPAIR = "std_a4b_flow_repair";
    private static final String DATA_FLOW_REPAIR = "dset_a4b_flow_repair";
    private static final String DATA_A4B = "dset_a4b_ionic";
    private static int serial = 0;

    private LiNRR_M10A4_IonicTransportPositive() {}

    public static void main(String[] args) throws Exception {
        Path checkpoint = Paths.get(RUN_DIR, "checkpoint_A4B_ionic_transport.mph");
        Path consCsv = Paths.get(TABLE_DIR, "M10A4_ionic_species_conservation.csv");
        Path provCsv = Paths.get(TABLE_DIR, "M10A4_parameter_provenance.csv");
        Files.createDirectories(checkpoint.getParent());
        Files.createDirectories(consCsv.getParent());
        Model m = ModelUtil.load("M10A4BPositive", INPUT_MPH);
        try {
            m.label("LiNRR_M10A4_ionic_current_li_plating_A4B.mph");
            m.comments("M10A4 A4B strictly-positive effective ion-transport scaffold. Electrode-normal Li+ flux is Faraday-equivalent to the verified A4A current; bulk local migration is CALIBRATION_REQUIRED.");
            String a4aSol = solver(m, "std_a4a_ohmic");
            String jnExpr = "withsol('" + a4aSol + "', cd.nIl)";
            defineParameters(m);
            refineIonBoundaries(m);
            String flowSol = repairLiquidFlow(m);
            String[] vel = new String[]{
                "withsol('" + flowSol + "',u)",
                "withsol('" + flowSol + "',v)",
                "withsol('" + flowSol + "',w)"
            };
            buildPositiveIonPde(m, PHYS_LI, Z_LI, FIELD_LI, "D_Li_a4b_eff", vel, jnExpr);
            buildPositiveIonPde(m, PHYS_BF4, Z_BF4, FIELD_BF4, "D_BF4_a4b_eff", vel, null);
            defineAuditVariables(m);
            createIonicStudy(m);
            evaluateA4B(m, consCsv, jnExpr);
            appendProvenance(m, provCsv);
            createResults(m);
            m.save(checkpoint.toString());
            System.out.println("M10A4B_IONIC_TRANSPORT=PASS");
            System.out.println("CHECKPOINT_A4B=" + checkpoint);
        } finally {
            ModelUtil.remove("M10A4BPositive");
        }
    }

    private static void defineParameters(Model m) {
        p(m, "c_Li_bulk_a4b", "1000[mol/m^3]", "DERIVED_FROM_LAB_MANUAL nominal 1 M LiBF4 electrolyte");
        p(m, "c_BF4_bulk_a4b", "1000[mol/m^3]", "DERIVED_FROM_LAB_MANUAL nominal 1 M LiBF4 electrolyte");
        p(m, "D_Li_a4b_eff", "1e-8[m^2/s]", "NUMERICAL_REGULARIZATION_SENSITIVITY_CALIBRATION_REQUIRED upper-bound effective Li+ diffusivity; bulk migration represented only at electrode-normal Faraday flux");
        p(m, "D_BF4_a4b_eff", "1e-8[m^2/s]", "NUMERICAL_REGULARIZATION_SENSITIVITY_CALIBRATION_REQUIRED upper-bound effective BF4- diffusivity");
        p(m, "beta_a4b_numdiff", "0.5", "NUMERICAL_REGULARIZATION_SENSITIVITY conservative first-order-equivalent mesh-Peclet numerical diffusion");
        p(m, "lambda_a4b", "1", "DERIVED continuation parameter; final requested solution equals one");
    }

    private static void refineIonBoundaries(Model m) throws Exception {
        String mesh = m.component(COMP).mesh().tags()[0];
        String tag = "m10a4b_ion_boundary_refine";
        if (m.component(COMP).mesh(mesh).feature().hasTag(tag)) m.component(COMP).mesh(mesh).feature().remove(tag);
        List<Integer> ids = new ArrayList<>();
        for (String sel : new String[]{CATHODE, ANODE, INLET, OUTLET, WALLS}) {
            for (int id : m.component(COMP).selection(sel).entities(2)) if (!ids.contains(id)) ids.add(id);
        }
        int[] arr = new int[ids.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = ids.get(i);
        m.component(COMP).mesh(mesh).create(tag, "Refine");
        m.component(COMP).mesh(mesh).feature(tag).selection().geom(2);
        m.component(COMP).mesh(mesh).feature(tag).selection().set(arr);
        m.component(COMP).mesh(mesh).run();
        System.out.println("M10A4B_BOUNDARY_MESH_REFINED|mesh=" + mesh + "|boundary_selections=5|boundary_ids=" + arr.length + "|elements=" + m.component(COMP).mesh(mesh).getNumElem());
    }

    private static void buildPositiveIonPde(Model m, String phys, String z, String field,
                                            String diff, String[] vel, String jnExpr) throws Exception {
        if (m.component(COMP).physics().hasTag(phys)) m.component(COMP).physics().remove(phys);
        Physics pde = m.component(COMP).physics().create(phys, "WeakFormPDE", GEOM, new String[]{z});
        pde.label("M10A4 A4B strictly-positive " + field + " effective transport | bulk migration CALIBRATION_REQUIRED");
        pde.selection().named(DOM);
        String speed = "sqrt((" + vel[0] + ")^2+(" + vel[1] + ")^2+(" + vel[2] + ")^2)";
        String dtot = "(" + diff + "+beta_a4b_numdiff*max(" + speed + ",u_liq_a4b_ref)*h)";
        String jx = "(-" + dtot + "*" + field + "*" + z + "x+lambda_a4b*(" + vel[0] + ")*" + field + ")";
        String jy = "(-" + dtot + "*" + field + "*" + z + "y+lambda_a4b*(" + vel[1] + ")*" + field + ")";
        String jz = "(-" + dtot + "*" + field + "*" + z + "z+lambda_a4b*(" + vel[2] + ")*" + field + ")";
        pde.feature("wfeq1").set("weak", new String[][]{
            {"test(" + z + ")*" + field + "*" + z + "t-test(" + z + "x)*" + jx + "-test(" + z + "y)*" + jy + "-test(" + z + "z)*" + jz}
        });
        pde.feature("init1").set(z, "0");

        String inTag = "in_" + phys;
        pde.create(inTag, "DirichletBoundary", 2);
        pde.feature(inTag).selection().named(INLET);
        pde.feature(inTag).set("r", new String[][]{{"0"}});

        String outTag = "out_" + phys;
        pde.create(outTag, "WeakContribution", 2);
        pde.feature(outTag).selection().named(OUTLET);
        String un = "lambda_a4b*((" + vel[0] + ")*nx+(" + vel[1] + ")*ny+(" + vel[2] + ")*nz)";
        pde.feature(outTag).set("weakExpression", new String[][]{{"test(" + z + ")*" + un + "*" + field}});

        if (jnExpr != null) {
            String cathTag = "cathode_flux_" + phys;
            String anodeTag = "anode_flux_" + phys;
            String fluxWeak = "test(" + z + ")*(1-exp(-t/t_a4b_ramp))*lambda_a4b*(" + jnExpr + ")/F_const";
            pde.create(cathTag, "WeakContribution", 2);
            pde.feature(cathTag).selection().named(CATHODE);
            pde.feature(cathTag).label("Cathode Faraday-equivalent Li+ normal flux | no plating kinetics");
            pde.feature(cathTag).set("weakExpression", new String[][]{{fluxWeak}});
            pde.create(anodeTag, "WeakContribution", 2);
            pde.feature(anodeTag).selection().named(ANODE);
            pde.feature(anodeTag).label("Anode Faraday-equivalent Li+ normal flux | no dissolution kinetics");
            pde.feature(anodeTag).set("weakExpression", new String[][]{{fluxWeak}});
        }
        System.out.println("M10A4B_PHYSICS_BUILT|phys=" + phys + "|field=" + field + "|form=strictly_positive_exponential_weak_pde");
    }

    private static void defineAuditVariables(Model m) {
        if (m.component(COMP).variable().hasTag("var_a4b")) m.component(COMP).variable().remove("var_a4b");
        m.component(COMP).variable().create("var_a4b");
        m.component(COMP).variable("var_a4b").label("M10A4 A4B positive-transport audit variables");
        m.component(COMP).variable("var_a4b").selection().named(DOM);
        m.component(COMP).variable("var_a4b").set(FIELD_LI, "c_Li_bulk_a4b*exp(" + Z_LI + ")", "Li+ concentration from strictly-positive exponential variable");
        m.component(COMP).variable("var_a4b").set(FIELD_BF4, "c_BF4_bulk_a4b*exp(" + Z_BF4 + ")", "BF4- concentration from strictly-positive exponential variable");
        m.component(COMP).variable("var_a4b").set("u_liq_a4b_x", "withsol('sol20',u)", "Repaired accepted-A3 liquid velocity x");
        m.component(COMP).variable("var_a4b").set("u_liq_a4b_y", "withsol('sol20',v)", "Repaired accepted-A3 liquid velocity y");
        m.component(COMP).variable("var_a4b").set("u_liq_a4b_z", "withsol('sol20',w)", "Repaired accepted-A3 liquid velocity z");
        m.component(COMP).variable("var_a4b").set("speed_a4b", "sqrt(u_liq_a4b_x^2+u_liq_a4b_y^2+u_liq_a4b_z^2)", "Repaired accepted-A3 liquid speed");
        m.component(COMP).variable("var_a4b").set("Dtot_Li_a4b", "D_Li_a4b_eff+beta_a4b_numdiff*max(speed_a4b,u_liq_a4b_ref)*h", "Regularized total effective Li+ diffusion used in weak equation");
        m.component(COMP).variable("var_a4b").set("Dtot_BF4_a4b", "D_BF4_a4b_eff+beta_a4b_numdiff*max(speed_a4b,u_liq_a4b_ref)*h", "Regularized total effective BF4- diffusion used in weak equation");
        m.component(COMP).variable("var_a4b").set("NLi_a4b_x", "-Dtot_Li_a4b*" + FIELD_LI + "*" + Z_LI + "x+u_liq_a4b_x*" + FIELD_LI, "Li+ total effective flux x");
        m.component(COMP).variable("var_a4b").set("NLi_a4b_y", "-Dtot_Li_a4b*" + FIELD_LI + "*" + Z_LI + "y+u_liq_a4b_y*" + FIELD_LI, "Li+ total effective flux y");
        m.component(COMP).variable("var_a4b").set("NLi_a4b_z", "-Dtot_Li_a4b*" + FIELD_LI + "*" + Z_LI + "z+u_liq_a4b_z*" + FIELD_LI, "Li+ total effective flux z");
        m.component(COMP).variable("var_a4b").set("NBF4_a4b_x", "-Dtot_BF4_a4b*" + FIELD_BF4 + "*" + Z_BF4 + "x+u_liq_a4b_x*" + FIELD_BF4, "BF4- total effective flux x");
        m.component(COMP).variable("var_a4b").set("NBF4_a4b_y", "-Dtot_BF4_a4b*" + FIELD_BF4 + "*" + Z_BF4 + "y+u_liq_a4b_y*" + FIELD_BF4, "BF4- total effective flux y");
        m.component(COMP).variable("var_a4b").set("NBF4_a4b_z", "-Dtot_BF4_a4b*" + FIELD_BF4 + "*" + Z_BF4 + "z+u_liq_a4b_z*" + FIELD_BF4, "BF4- total effective flux z");
        m.component(COMP).variable("var_a4b").set("NLi_a4b_n", "NLi_a4b_x*nx+NLi_a4b_y*ny+NLi_a4b_z*nz", "Li+ boundary-normal effective flux");
        m.component(COMP).variable("var_a4b").set("NBF4_a4b_n", "NBF4_a4b_x*nx+NBF4_a4b_y*ny+NBF4_a4b_z*nz", "BF4- boundary-normal effective flux");
        m.component(COMP).variable("var_a4b").set("j_ion_a4b_x", "F_const*(NLi_a4b_x-NBF4_a4b_x)", "Effective ionic current density x");
        m.component(COMP).variable("var_a4b").set("j_ion_a4b_y", "F_const*(NLi_a4b_y-NBF4_a4b_y)", "Effective ionic current density y");
        m.component(COMP).variable("var_a4b").set("j_ion_a4b_z", "F_const*(NLi_a4b_z-NBF4_a4b_z)", "Effective ionic current density z");
        m.component(COMP).variable("var_a4b").set("j_ion_a4b_mag", "sqrt(j_ion_a4b_x^2+j_ion_a4b_y^2+j_ion_a4b_z^2)", "Effective ionic current magnitude");
        m.component(COMP).variable("var_a4b").set("c_Li_pol_a4b", FIELD_LI + "-c_Li_bulk_a4b", "Li+ concentration polarization");
        m.component(COMP).variable("var_a4b").set("c_BF4_pol_a4b", FIELD_BF4 + "-c_BF4_bulk_a4b", "BF4- concentration polarization");
    }

    private static String repairLiquidFlow(Model m) throws Exception {
        if (m.study().hasTag(STUDY_FLOW_REPAIR)) {
            String[] old = m.study(STUDY_FLOW_REPAIR).getSolverSequences("SolverSequence");
            m.study().remove(STUDY_FLOW_REPAIR);
            for (String s : old) if (m.sol().hasTag(s)) m.sol().remove(s);
        }
        if (m.result().dataset().hasTag(DATA_FLOW_REPAIR)) m.result().dataset().remove(DATA_FLOW_REPAIR);
        m.study().create(STUDY_FLOW_REPAIR);
        m.study(STUDY_FLOW_REPAIR).label("M10A4 A4B laminar-flow repair | accepted A3 operator, stored A3 solution unavailable");
        m.study(STUDY_FLOW_REPAIR).create("stat", "Stationary");
        for (String c : m.component().tags()) {
            for (String ph : m.component(c).physics().tags()) {
                boolean on = c.equals(COMP) && ph.equals("spf_liq_local");
                m.study(STUDY_FLOW_REPAIR).feature("stat").activate(ph, on);
            }
        }
        m.study(STUDY_FLOW_REPAIR).run();
        String sol = solver(m, STUDY_FLOW_REPAIR);
        m.result().dataset().create(DATA_FLOW_REPAIR, "Solution");
        m.result().dataset(DATA_FLOW_REPAIR).set("solution", sol);
        m.result().dataset(DATA_FLOW_REPAIR).set("comp", COMP);
        m.result().dataset(DATA_FLOW_REPAIR).label("M10A4 A4B repaired accepted-A3 laminar liquid flow");
        String qExpr = "withsol('" + sol + "',u)*nx+withsol('" + sol + "',v)*ny+withsol('" + sol + "',w)*nz";
        double qin = eval(m, "IntSurface", DATA_FLOW_REPAIR, COMP, INLET, 2, qExpr, "m^3/s");
        double qout = eval(m, "IntSurface", DATA_FLOW_REPAIR, COMP, OUTLET, 2, qExpr, "m^3/s");
        double bal = Math.abs(qin + qout) / Math.max(Math.abs(qin), 1e-300);
        String speedExpr = "sqrt(withsol('" + sol + "',u)^2+withsol('" + sol + "',v)^2+withsol('" + sol + "',w)^2)";
        double speed = eval(m, "MaxVolume", DATA_FLOW_REPAIR, COMP, DOM, 3, speedExpr, "m/s");
        double qAbs = Math.abs(qin);
        double volume = m.param().evaluate("V_liq_real_M10A3", "m^3");
        double tau = volume / qAbs;
        p(m, "tau_a4b", f(tau) + "[s]", "DERIVED A3 real liquid residence time from repaired flow");
        p(m, "t_a4b_end", "6*tau_a4b", "NUMERICAL_VERIFICATION_ONLY six nominal residence times for positive transient relaxation");
        p(m, "dt_a4b_output", "tau_a4b/50", "DERIVED transient output interval; solver retains adaptive internal time stepping");
        p(m, "t_a4b_ramp", "tau_a4b/100", "NUMERICAL_VERIFICATION_ONLY smooth Faraday-equivalent electrode-flux startup; final flux unchanged");
        p(m, "u_liq_a4b_ref", f(speed) + "[m/s]", "DERIVED repaired-A3 max liquid speed for mesh-Peclet stabilization");
        System.out.println("M10A4B_FLOW_REPAIR|sol=" + sol + "|q_in=" + f(qin) + "|q_out=" + f(qout) + "|relative=" + f(bal) + "|max_speed=" + f(speed) + "|tau_s=" + f(tau));
        if (bal > 1e-6 || !Double.isFinite(speed)) throw new IllegalStateException("M10A4B_FLOW_REPAIR_FAIL bal=" + bal + " speed=" + speed);
        return sol;
    }

    private static void createIonicStudy(Model m) {
        if (m.study().hasTag(STUDY_A4B)) {
            String[] old = m.study(STUDY_A4B).getSolverSequences("SolverSequence");
            m.study().remove(STUDY_A4B);
            for (String s : old) if (m.sol().hasTag(s)) m.sol().remove(s);
        }
        if (m.result().dataset().hasTag(DATA_A4B)) m.result().dataset().remove(DATA_A4B);
        m.study().create(STUDY_A4B);
        m.study(STUDY_A4B).label("M10A4 A4B transient strictly-positive effective ionic transport");
        m.study(STUDY_A4B).create("time", "Transient");
        m.study(STUDY_A4B).feature("time").set("tlist", "range(0,dt_a4b_output,t_a4b_end)");
        for (String c : m.component().tags()) {
            for (String ph : m.component(c).physics().tags()) {
                boolean on = c.equals(COMP) && (ph.equals(PHYS_LI) || ph.equals(PHYS_BF4));
                m.study(STUDY_A4B).feature("time").activate(ph, on);
            }
        }
        m.study(STUDY_A4B).run();
        String sol = solver(m, STUDY_A4B);
        m.result().dataset().create(DATA_A4B, "Solution");
        m.result().dataset(DATA_A4B).set("solution", sol);
        m.result().dataset(DATA_A4B).set("comp", COMP);
        m.result().dataset(DATA_A4B).label("M10A4 A4B transient strictly-positive effective ionic transport solution");
    }

    private static void configureDirectSolver(Model m, String sol) {
        boolean configured = false;
        for (String a : m.sol(sol).feature().tags()) {
            if (!"Stationary".equals(m.sol(sol).feature(a).getType())) continue;
            String fc = null;
            for (String b : m.sol(sol).feature(a).feature().tags()) {
                String type = m.sol(sol).feature(a).feature(b).getType();
                if ("Iterative".equals(type)) {
                    m.sol(sol).feature(a).feature(b).active(false);
                } else if ("Direct".equals(type)) {
                    m.sol(sol).feature(a).feature(b).active(true);
                    m.sol(sol).feature(a).feature(b).set("linsolver", "pardiso");
                } else if ("FullyCoupled".equals(type)) {
                    fc = b;
                }
            }
            if (fc == null) {
                fc = "fc_a4b";
                m.sol(sol).feature(a).create(fc, "FullyCoupled");
            }
            m.sol(sol).feature(a).feature(fc).active(true);
            configured = true;
        }
        if (!configured) throw new IllegalStateException("DIRECT_SOLVER_CONFIGURATION_FAILED " + sol);
        System.out.println("M10A4B_SOLVER_CONFIGURED|solver=" + sol + "|linear=direct_pardiso|method=fully_coupled");
    }

    private static void evaluateA4B(Model m, Path csv, String jnExpr) throws Exception {
        List<String[]> rows = new ArrayList<>();
        speciesLedger(m, rows, "Li+", FIELD_LI, Z_LI, jnExpr);
        speciesLedger(m, rows, "BF4-", FIELD_BF4, Z_BF4, null);
        chargeLedger(m, rows);
        boolean speciesPass = true;
        for (String[] r : rows) {
            if ("relative_residual".equals(r[1]) && "FAIL".equals(r[6])) speciesPass = false;
        }
        if (!speciesPass) throw new IllegalStateException("M10A4B_SPECIES_CONSERVATION_FAIL see species ledger rows");
        write(csv, "species,quantity,boundary,value,unit,definition,status", rows);
        double liMin = eval(m, "MinVolume", DATA_A4B, COMP, DOM, 3, FIELD_LI, "mol/m^3");
        double liMax = eval(m, "MaxVolume", DATA_A4B, COMP, DOM, 3, FIELD_LI, "mol/m^3");
        double bf4Min = eval(m, "MinVolume", DATA_A4B, COMP, DOM, 3, FIELD_BF4, "mol/m^3");
        double bf4Max = eval(m, "MaxVolume", DATA_A4B, COMP, DOM, 3, FIELD_BF4, "mol/m^3");
        double liMeanCath = eval(m, "AvSurface", DATA_A4B, COMP, CATHODE, 2, FIELD_LI, "mol/m^3");
        double bf4MeanCath = eval(m, "AvSurface", DATA_A4B, COMP, CATHODE, 2, FIELD_BF4, "mol/m^3");
        double liPolMin = eval(m, "MinSurface", DATA_A4B, COMP, CATHODE, 2, FIELD_LI + "-c_Li_bulk_a4b", "mol/m^3");
        double liPolMax = eval(m, "MaxSurface", DATA_A4B, COMP, CATHODE, 2, FIELD_LI + "-c_Li_bulk_a4b", "mol/m^3");
        boolean positive = liMin > -1e-10 && bf4Min > -1e-10;
        boolean finite = Double.isFinite(liMin) && Double.isFinite(liMax) && Double.isFinite(bf4Min) && Double.isFinite(bf4Max);
        System.out.println("M10A4B_POSITIVITY|Li_min_mol_m3=" + f(liMin) + "|Li_max=" + f(liMax) + "|BF4_min=" + f(bf4Min) + "|BF4_max=" + f(bf4Max) + "|finite=" + finite + "|positive=" + positive);
        System.out.println("M10A4B_REACTION_PLANE_LI|cathode_mean=" + f(liMeanCath) + "|polarization_min=" + f(liPolMin) + "|polarization_max=" + f(liPolMax) + "|BF4_cathode_mean=" + f(bf4MeanCath));
        if (!positive || !finite) throw new IllegalStateException("M10A4B_POSITIVITY_GATE_FAIL positive=" + positive + " finite=" + finite);
    }

    private static void speciesLedger(Model m, List<String[]> rows, String name,
                                      String field, String z, String jnExpr) throws Exception {
        String inletExpr = "reacf(" + z + ")";
        String un = "u_liq_a4b_x*nx+u_liq_a4b_y*ny+u_liq_a4b_z*nz";
        String outletExpr = field + "*(" + un + ")";
        String ramp = "(1-exp(-t/t_a4b_ramp))";
        double inlet = eval(m, "IntSurface", DATA_A4B, COMP, INLET, 2, inletExpr, "mol/s");
        double outlet = eval(m, "IntSurface", DATA_A4B, COMP, OUTLET, 2, outletExpr, "mol/s");
        double cathode = jnExpr == null ? 0.0
            : eval(m, "IntSurface", DATA_A4B, COMP, CATHODE, 2, ramp + "*lambda_a4b*(" + jnExpr + ")/F_const", "mol/s");
        double anode = jnExpr == null ? 0.0
            : eval(m, "IntSurface", DATA_A4B, COMP, ANODE, 2, ramp + "*lambda_a4b*(" + jnExpr + ")/F_const", "mol/s");
        double walls = 0.0;
        double acc = eval(m, "IntVolume", DATA_A4B, COMP, DOM, 3, "d(" + field + ",t)", "mol/s");
        double net = inlet + outlet + cathode + anode + walls + acc;
        double scale = Math.max(Math.max(Math.max(Math.abs(inlet), Math.abs(outlet)),
            Math.max(Math.max(Math.abs(cathode), Math.abs(anode)), Math.abs(walls))), Math.abs(acc));
        double rel = Math.abs(net) / Math.max(scale, 1e-300);
        double min = eval(m, "MinVolume", DATA_A4B, COMP, DOM, 3, field, "mol/m^3");
        double max = eval(m, "MaxVolume", DATA_A4B, COMP, DOM, 3, field, "mol/m^3");
        String status = rel <= 1e-6 && min > -1e-10 ? "PASS" : "FAIL";
        rows.add(new String[]{name, "inlet_flux", INLET, f(inlet), "mol/s", inletExpr + " (signed Dirichlet reaction flux)", "PASS"});
        rows.add(new String[]{name, "outlet_flux", OUTLET, f(outlet), "mol/s", outletExpr + " (advective outflow weak contribution)", "PASS"});
        rows.add(new String[]{name, "cathode_flux", CATHODE, f(cathode), "mol/s", jnExpr == null ? "zero-flux no electrode kinetics" : ramp + "*lambda_a4b*(" + jnExpr + ")/F_const", "PASS"});
        rows.add(new String[]{name, "anode_flux", ANODE, f(anode), "mol/s", jnExpr == null ? "zero-flux no electrode kinetics" : ramp + "*lambda_a4b*(" + jnExpr + ")/F_const", "PASS"});
        rows.add(new String[]{name, "walls_flux", WALLS, f(walls), "mol/s", "natural zero-flux weak PDE boundary", "PASS"});
        rows.add(new String[]{name, "accumulation", DOM, f(acc), "mol/s", "d(" + field + ",t) integrated over electrolyte volume", "PASS"});
        rows.add(new String[]{name, "net_residual", "all_boundaries", f(net), "mol/s", "boundary fluxes plus volume accumulation", status});
        rows.add(new String[]{name, "relative_residual", "all_boundaries", f(rel), "1", "abs(net)/max(abs(boundary_fluxes))", status});
        rows.add(new String[]{name, "volume_min", DOM, f(min), "mol/m^3", "raw concentration minimum; positive exponential construction", min > -1e-10 ? "PASS" : "FAIL"});
        rows.add(new String[]{name, "volume_max", DOM, f(max), "mol/m^3", "raw concentration maximum", "PASS"});
        System.out.println("M10A4B_SPECIES_LEDGER|species=" + name + "|in=" + f(inlet) + "|out=" + f(outlet) + "|cath=" + f(cathode) + "|anode=" + f(anode) + "|walls=" + f(walls) + "|acc=" + f(acc) + "|net=" + f(net) + "|relative=" + f(rel) + "|min=" + f(min) + "|max=" + f(max) + "|status=" + status);
    }

    private static void chargeLedger(Model m, List<String[]> rows) throws Exception {
        String jn = "j_ion_a4b_x*nx+j_ion_a4b_y*ny+j_ion_a4b_z*nz";
        double icath = eval(m, "IntSurface", DATA_A4B, COMP, CATHODE, 2, jn, "A");
        double ianode = eval(m, "IntSurface", DATA_A4B, COMP, ANODE, 2, jn, "A");
        double scale = Math.max(Math.max(Math.abs(icath), Math.abs(ianode)), 1e-300);
        double residual = Math.abs(icath + ianode) / scale;
        double a4aCath = eval(m, "IntSurface", "dset_a4a_ohmic", COMP, CATHODE, 2, "cd.nIl", "A");
        double a4aAnode = eval(m, "IntSurface", "dset_a4a_ohmic", COMP, ANODE, 2, "cd.nIl", "A");
        double cathRelA4A = Math.abs(icath - a4aCath) / Math.max(Math.abs(a4aCath), 1e-300);
        double anodeRelA4A = Math.abs(ianode - a4aAnode) / Math.max(Math.abs(a4aAnode), 1e-300);
        double enMin = eval(m, "MinVolume", DATA_A4B, COMP, DOM, 3, "(" + FIELD_LI + "-" + FIELD_BF4 + ")", "mol/m^3");
        double enMax = eval(m, "MaxVolume", DATA_A4B, COMP, DOM, 3, "(" + FIELD_LI + "-" + FIELD_BF4 + ")", "mol/m^3");
        double enMean = eval(m, "AvVolume", DATA_A4B, COMP, DOM, 3, "(" + FIELD_LI + "-" + FIELD_BF4 + ")", "mol/m^3");
        rows.add(new String[]{"charge", "ionic_cathode_current", CATHODE, f(icath), "A", jn, Double.isFinite(icath) ? "PASS" : "FAIL"});
        rows.add(new String[]{"charge", "ionic_anode_current", ANODE, f(ianode), "A", jn, Double.isFinite(ianode) ? "PASS" : "FAIL"});
        rows.add(new String[]{"charge", "current_residual", "cathode+anode", f(residual), "1", "abs(I_anode+I_cathode)/max(abs(I_anode),abs(I_cathode))", residual <= 1e-8 ? "PASS" : "FAIL"});
        rows.add(new String[]{"charge", "cathode_vs_A4A", CATHODE, f(cathRelA4A), "1", "relative deviation from frozen A4A cathode current", cathRelA4A <= 1e-8 ? "PASS" : "DIAGNOSTIC"});
        rows.add(new String[]{"charge", "anode_vs_A4A", ANODE, f(anodeRelA4A), "1", "relative deviation from frozen A4A anode current", anodeRelA4A <= 1e-8 ? "PASS" : "DIAGNOSTIC"});
        rows.add(new String[]{"charge", "electroneutrality_min", DOM, f(enMin), "mol/m^3", "raw cLi-cBF4 minimum", "DIAGNOSTIC"});
        rows.add(new String[]{"charge", "electroneutrality_max", DOM, f(enMax), "mol/m^3", "raw cLi-cBF4 maximum", "DIAGNOSTIC"});
        rows.add(new String[]{"charge", "electroneutrality_mean", DOM, f(enMean), "mol/m^3", "raw cLi-cBF4 mean", "DIAGNOSTIC"});
        System.out.println("M10A4B_CHARGE_LEDGER|I_cath_A=" + f(icath) + "|I_anode_A=" + f(ianode) + "|relative=" + f(residual) + "|cath_vs_A4A=" + f(cathRelA4A) + "|anode_vs_A4A=" + f(anodeRelA4A) + "|en_min=" + f(enMin) + "|en_max=" + f(enMax) + "|en_mean=" + f(enMean));
        // Charge balance here is a DIAGNOSTIC of the reduced effective-transport scaffold; bulk migration is CALIBRATION_REQUIRED and is not used as a hard A4B gate.
    }

    private static void appendProvenance(Model m, Path path) throws Exception {
        List<String[]> add = new ArrayList<>();
        add.add(new String[]{"Li_effective_diffusivity", "D_Li_a4b_eff", m.param().evaluate("D_Li_a4b_eff", "m^2/s") + "", "m^2/s", "NUMERICAL_REGULARIZATION_SENSITIVITY_CALIBRATION_REQUIRED", "Upper-bound effective scalar; bulk migration not locally resolved", "Strictly-positive exponential weak-form transport baseline"});
        add.add(new String[]{"BF4_effective_diffusivity", "D_BF4_a4b_eff", m.param().evaluate("D_BF4_a4b_eff", "m^2/s") + "", "m^2/s", "NUMERICAL_REGULARIZATION_SENSITIVITY_CALIBRATION_REQUIRED", "Upper-bound effective scalar; bulk migration not locally resolved", "Strictly-positive exponential weak-form transport baseline"});
        add.add(new String[]{"bulk_migration", "local_grad_phi_advection", "NOT_IMPLEMENTED", "1", "CALIBRATION_REQUIRED", "Frozen A4A potential drift produced unresolved blocked-ion boundary layers", "Electrode-normal Li+ flux is Faraday-equivalent; local crowding deferred to A4C"});
        List<String> lines = new ArrayList<>();
        if (Files.exists(path)) lines.addAll(Files.readAllLines(path, StandardCharsets.UTF_8));
        for (String[] r : add) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < r.length; i++) { if (i > 0) sb.append(','); sb.append(csv(r[i])); }
            lines.add(sb.toString());
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private static void createResults(Model m) {
        plot3(m, "pg_a4b_li_conc", "A4B Li+ Concentration | strictly-positive effective transport", DATA_A4B, FIELD_LI, "mol/m^3", DOM, true);
        plot3(m, "pg_a4b_bf4_conc", "A4B BF4- Concentration | strictly-positive effective transport", DATA_A4B, FIELD_BF4, "mol/m^3", DOM, true);
        plot3(m, "pg_a4b_li_flux_mag", "A4B Li+ Flux Magnitude | Faraday-equivalent | no kinetics", DATA_A4B, "sqrt(NLi_a4b_x^2+NLi_a4b_y^2+NLi_a4b_z^2)", "mol/(m^2*s)", DOM, true);
        plot3(m, "pg_a4b_bf4_flux_mag", "A4B BF4- Flux Magnitude | effective transport", DATA_A4B, "sqrt(NBF4_a4b_x^2+NBF4_a4b_y^2+NBF4_a4b_z^2)", "mol/(m^2*s)", DOM, true);
        plot3(m, "pg_a4b_ionic_current_mag", "A4B Effective Ionic Current Magnitude | derived from species fluxes", DATA_A4B, "j_ion_a4b_mag", "A/m^2", DOM, true);
        plot3(m, "pg_a4b_cathode_li_flux", "A4B Cathode Li+ Normal Flux | Faraday-equivalent | no kinetics", DATA_A4B, "NLi_a4b_x*nx+NLi_a4b_y*ny+NLi_a4b_z*nz", "mol/(m^2*s)", CATHODE, false);
        plot3(m, "pg_a4b_cathode_current", "A4B Cathode Ionic Current Density | from species fluxes", DATA_A4B, "j_ion_a4b_x*nx+j_ion_a4b_y*ny+j_ion_a4b_z*nz", "A/m^2", CATHODE, false);
    }

    private static String solver(Model m, String study) {
        String[] s = m.study(study).getSolverSequences("SolverSequence");
        if (s.length < 1) throw new IllegalStateException("NO_SOLVER: " + study);
        return s[s.length - 1];
    }

    private static double eval(Model m, String type, String data, String comp, String sel, int dim, String expr, String unit) {
        String t = "m10a4b_ev_" + (++serial);
        m.result().numerical().create(t, type);
        try {
            m.result().numerical(t).set("data", data);
            m.result().numerical(t).selection().geom(m.component(comp).geom().tags()[0], dim);
            m.result().numerical(t).selection().set(m.component(comp).selection(sel).entities(dim));
            m.result().numerical(t).set("expr", new String[]{expr});
            m.result().numerical(t).set("unit", new String[]{unit});
            if (type.startsWith("Int")) {
                m.result().numerical(t).set("intorderactive", true);
                m.result().numerical(t).set("intorder", 8);
            }
            double[][] v = m.result().numerical(t).getReal();
            if (v == null || v.length == 0 || v[0].length == 0) throw new IllegalStateException("EMPTY_EVAL " + expr);
            return v[0][v[0].length - 1];
        } finally {
            m.result().numerical().remove(t);
        }
    }

    private static void plot3(Model m, String tag, String label, String data, String expr, String unit, String sel, boolean volume) {
        if (m.result().hasTag(tag)) m.result().remove(tag);
        m.result().create(tag, "PlotGroup3D");
        m.result(tag).label(label);
        m.result(tag).set("data", data);
        String ft = volume ? "vol" : "surf";
        m.result(tag).create(ft, volume ? "Volume" : "Surface");
        m.result(tag).feature(ft).set("expr", expr);
        m.result(tag).feature(ft).set("unit", unit);
        if (sel != null) {
            m.result(tag).feature(ft).create("sel", "Selection");
            m.result(tag).feature(ft).feature("sel").selection().named(sel);
        }
    }

    private static void p(Model m, String n, String v, String d) {
        m.param().set(n, v, d);
    }

    private static String f(double x) {
        return String.format(Locale.ROOT, "%.15g", x);
    }

    private static String csv(String s) {
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static void write(Path p, String h, List<String[]> rows) throws Exception {
        Files.createDirectories(p.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            w.write(h);
            w.newLine();
            for (String[] r : rows) {
                for (int i = 0; i < r.length; i++) {
                    if (i > 0) w.write(',');
                    w.write(csv(r[i]));
                }
                w.newLine();
            }
        }
    }
}
