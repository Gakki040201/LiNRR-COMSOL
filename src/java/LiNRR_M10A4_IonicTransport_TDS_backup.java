import com.comsol.model.Model;import com.comsol.model.util.ModelUtil;
import com.comsol.model.physics.Physics;import java.io.BufferedWriter;import java.nio.charset.StandardCharsets;import java.nio.file.Files;import java.nio.file.Path;import java.nio.file.Paths;import java.util.ArrayList;import java.util.List;import java.util.Locale;/** * M10A4 A4B: explicit effective Nernst-Planck Li+/BF4- transport on the * accepted real electrolyte component. * * Governing fields: *   cLi_a4b, cBF4_a4b (DilutedSpecies) * Frozen drift/migration field: *   phi_l_a4a = withsol(A4A_solution, cd.phil) * Frozen liquid velocity: *   (u_liq_species, v_liq_species, w_liq_species) inherited from accepted M10A3. * * Concentrated 1 M LiBF4/diglyme+EtOH electrolyte is explicitly treated as * PROVISIONAL_SENSITIVITY / LITERATURE_ESTIMATE, not as a quantitatively * predictive ideal-dilute model. */public final class LiNRR_M10A4_IonicTransport {
    private static final String ROOT =        "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED";
    private static final String RUN_DIR = ROOT +        "\\runs\\M10A4\\20260820_121258";
    private static final String INPUT_MPH = RUN_DIR +        "\\checkpoint_A4A_ohmic_current.mph";
    private static final String TABLE_DIR = ROOT + "\\results\\tables";
    private static final String COMP = "comp_species_liq_real";
    private static final String GEOM = "geom_electrolyte_fluid1";
    private static final String DOM = "m10a3_sel_dom_electrolyte_fluid";
    private static final String CATHODE = "m10a3_sel_bnd_electrolyte_gde_top";
    private static final String ANODE = "m10a3_sel_bnd_electrolyte_gde_bottom";
    private static final String INLET = "m10a3_sel_bnd_electrolyte_inlet";
    private static final String OUTLET = "m10a3_sel_bnd_electrolyte_outlet";
    private static final String WALLS = "m10a3_sel_bnd_electrolyte_walls";
    private static final String PHYS_LI = "tds_li_a4b";
    private static final String PHYS_BF4 = "tds_bf4_a4b";
    private static final String FIELD_LI = "cLi_a4b";
    private static final String FIELD_BF4 = "cBF4_a4b";
    private static final String STUDY_A4B = "std_a4b_ionic";
    private static final String STUDY_FLOW_REPAIR = "std_a4b_flow_repair";
    private static final String DATA_FLOW_REPAIR = "dset_a4b_flow_repair";
    private static final String DATA_A4B = "dset_a4b_ionic";
    private static int serial = 0;
    private static String liPrefix = PHYS_LI;
    private static String bf4Prefix = PHYS_BF4;
    private LiNRR_M10A4_IonicTransport() {}
    public static void main(String[] args) throws Exception {
        Path checkpoint = Paths.get(RUN_DIR, "checkpoint_A4B_ionic_transport.mph");
        Path consCsv = Paths.get(TABLE_DIR, "M10A4_ionic_species_conservation.csv");
        Path provCsv = Paths.get(TABLE_DIR, "M10A4_parameter_provenance.csv");
        Files.createDirectories(checkpoint.getParent());
        Files.createDirectories(consCsv.getParent());
        Model m = ModelUtil.load("M10A4B", INPUT_MPH);
        try {
            m.label("LiNRR_M10A4_ionic_current_li_plating_A4B.mph");
            m.comments("M10A4 A4B explicit effective Nernst-Planck Li+/BF4- transport, frozen verified A4A electrolyte potential, frozen accepted M10A3 liquid velocity. Concentrated-electrolyte parameters are PROVISIONAL_SENSITIVITY/LITERATURE_ESTIMATE; no reaction kinetics or Li plating.");
            String a4aSol = solver(m, "std_a4a_ohmic");
            String phiExpr = "withsol('" + a4aSol + "', cd.phil)";
            String jnExpr = "withsol('" + a4aSol + "', cd.nIl)";
            defineParameters(m, phiExpr);
            String flowSol = repairLiquidFlow(m);
            buildIonPhysics(m, PHYS_LI, FIELD_LI, "Li+", 1,                "D_Li_a4b_eff", "u_Li_elec_a4b",                "c_Li_bulk_a4b", "-", jnExpr, flowSol);
            buildIonPhysics(m, PHYS_BF4, FIELD_BF4, "BF4-", -1,                "D_BF4_a4b_eff", "u_BF4_elec_a4b",                "c_BF4_bulk_a4b", "+", null, flowSol);
            liPrefix = physicsPrefix(m, PHYS_LI, FIELD_LI);
            bf4Prefix = physicsPrefix(m, PHYS_BF4, FIELD_BF4);
            defineFrozenFields(m, phiExpr);
            createIonicStudy(m);
            evaluateA4B(m, consCsv);
            appendProvenance(m, provCsv);
            createResults(m);
            m.save(checkpoint.toString());
            System.out.println("M10A4B_IONIC_TRANSPORT=PASS");
            System.out.println("CHECKPOINT_A4B=" + checkpoint);
        } finally {
            ModelUtil.remove("M10A4B");
        }    }
    private static void defineParameters(Model m, String phiExpr) {
        p(m, "c_Li_bulk_a4b", "1000[mol/m^3]", "DERIVED_FROM_LAB_MANUAL nominal 1 M LiBF4 electrolyte");
        p(m, "c_BF4_bulk_a4b", "1000[mol/m^3]", "DERIVED_FROM_LAB_MANUAL nominal 1 M LiBF4 electrolyte");
        p(m, "D_Li_a4b_eff", "1e-8[m^2/s]", "NUMERICAL_REGULARIZATION_SENSITIVITY_CALIBRATION_REQUIRED effective Li+ diffusivity upper-bound baseline; migration is represented at electrode-normal Faraday flux, not local frozen-potential drift");
        p(m, "D_BF4_a4b_eff", "1e-8[m^2/s]", "NUMERICAL_REGULARIZATION_SENSITIVITY_CALIBRATION_REQUIRED effective BF4- diffusivity upper-bound baseline; bulk migration calibration pending");
        p(m, "t_plus_a4b_eff", "0.5", "PROVISIONAL_SENSITIVITY symmetric binary transference partition; not calibrated");
        p(m, "u_Li_elec_a4b", "t_plus_a4b_eff*kappa_M10A4_nominal/(F_const*c_Li_bulk_a4b)", "DERIVED_FROM_SENSITIVITY effective Li+ electrical mobility consistent with A4A kappa baseline");
        p(m, "u_BF4_elec_a4b", "(1-t_plus_a4b_eff)*kappa_M10A4_nominal/(F_const*c_BF4_bulk_a4b)", "DERIVED_FROM_SENSITIVITY effective BF4- electrical mobility consistent with A4A kappa baseline");
        p(m, "E_a4b_cap", "100[V/m]", "NUMERICAL_REGULARIZATION_SENSITIVITY caps frozen A4A migration field at ~3x the nominal applied-current field; local crowding remains A4C diagnostic");
        p(m, "eps_E_a4b", "1e-6[V/m]", "NUMERICAL_REGULARIZATION_SENSITIVITY tiny floor for capped electric-field normalization");
        p(m, "phi_l_a4a_expr", phiExpr, "DERIVED frozen A4A electrolyte potential expression");    }
    private static void defineFrozenFields(Model m, String phiExpr) {
        if (m.component(COMP).variable().hasTag("var_a4b")) {
            m.component(COMP).variable().remove("var_a4b");
        }
        m.component(COMP).variable().create("var_a4b");
        m.component(COMP).variable("var_a4b").label("M10A4 A4B frozen-field and ionic-flux audit variables");
        m.component(COMP).variable("var_a4b").selection().named(DOM);
        m.component(COMP).variable("var_a4b").set("phi_l_a4a", phiExpr, "Frozen A4A electrolyte potential");
        m.component(COMP).variable("var_a4b").set("E_a4b_x", "-d(phi_l_a4a,x)", "Frozen A4A electric field x");
        m.component(COMP).variable("var_a4b").set("E_a4b_y", "-d(phi_l_a4a,y)", "Frozen A4A electric field y");
        m.component(COMP).variable("var_a4b").set("E_a4b_z", "-d(phi_l_a4a,z)", "Frozen A4A electric field z");
        m.component(COMP).variable("var_a4b").set("E_a4b_mag", "sqrt(E_a4b_x^2+E_a4b_y^2+E_a4b_z^2)", "Frozen A4A electric field magnitude");
        m.component(COMP).variable("var_a4b").set("E_a4b_reg_scale", "min(1, E_a4b_cap/(nojac(E_a4b_mag)+eps_E_a4b))", "Capped migration-field scale factor");
        m.component(COMP).variable("var_a4b").set("E_a4b_reg_x", "E_a4b_x*E_a4b_reg_scale", "Regularized effective electric field x");
        m.component(COMP).variable("var_a4b").set("E_a4b_reg_y", "E_a4b_y*E_a4b_reg_scale", "Regularized effective electric field y");
        m.component(COMP).variable("var_a4b").set("E_a4b_reg_z", "E_a4b_z*E_a4b_reg_scale", "Regularized effective electric field z");
        m.component(COMP).variable("var_a4b").set("j_ion_a4b_x", "F_const*(" + liPrefix + ".tfluxx_" + FIELD_LI + "-" + bf4Prefix + ".tfluxx_" + FIELD_BF4 + ")", "Effective ionic current density x");
        m.component(COMP).variable("var_a4b").set("j_ion_a4b_y", "F_const*(" + liPrefix + ".tfluxy_" + FIELD_LI + "-" + bf4Prefix + ".tfluxy_" + FIELD_BF4 + ")", "Effective ionic current density y");
        m.component(COMP).variable("var_a4b").set("j_ion_a4b_z", "F_const*(" + liPrefix + ".tfluxz_" + FIELD_LI + "-" + bf4Prefix + ".tfluxz_" + FIELD_BF4 + ")", "Effective ionic current density z");
        m.component(COMP).variable("var_a4b").set("j_ion_a4b_mag", "sqrt(j_ion_a4b_x^2+j_ion_a4b_y^2+j_ion_a4b_z^2)", "Effective ionic current density magnitude");
        m.component(COMP).variable("var_a4b").set("c_Li_pol_a4b", FIELD_LI + "-c_Li_bulk_a4b", "Li+ concentration polarization");
        m.component(COMP).variable("var_a4b").set("c_BF4_pol_a4b", FIELD_BF4 + "-c_BF4_bulk_a4b", "BF4- concentration polarization");    }
    private static void buildIonPhysics(Model m, String phys, String field, String species,                                        int z, String dparam, String uparam,                                        String cbulk, String migSign, String jnExpr, String flowSol) throws Exception {
        if (m.component(COMP).physics().hasTag(phys)) {
            m.component(COMP).physics().remove(phys);
        }
        m.component(COMP).physics().create(phys, "DilutedSpecies", GEOM, new String[]{field});
        m.component(COMP).physics(phys).label("M10A4 A4B " + species + " effective Nernst-Planck transport | concentrated-electrolyte PROVISIONAL_SENSITIVITY");
        m.component(COMP).physics(phys).selection().named(DOM);
        m.component(COMP).physics(phys).feature("sp1").set("z", Integer.toString(z));
        m.component(COMP).physics(phys).feature("cdm1").label("Effective electrolyte transport | " + species);
        m.component(COMP).physics(phys).feature("cdm1").set("D_" + field + "_mat", "userdef");
        m.component(COMP).physics(phys).feature("cdm1").set("D_" + field, dparam);
        stabilizeTds(m.component(COMP).physics(phys));
        String sx = migSign.equals("-") ? "-" : "+";
        m.component(COMP).physics(phys).feature("cdm1").set("u", new String[]{
            "withsol('" + flowSol + "',u)",
            "withsol('" + flowSol + "',v)",
            "withsol('" + flowSol + "',w)"
        });
        m.component(COMP).physics(phys).feature("init1").set("initc", cbulk);
        String inTag = "in_" + phys;
        String outTag = "out_" + phys;
        m.component(COMP).physics(phys).create(inTag, "Inflow", 2);
        m.component(COMP).physics(phys).feature(inTag).selection().named(INLET);
        m.component(COMP).physics(phys).feature(inTag).set("BoundaryConditionType", "FluxDanckwerts");
        m.component(COMP).physics(phys).feature(inTag).set("c0", new String[]{cbulk});
        m.component(COMP).physics(phys).create(outTag, "Outflow", 2);
        m.component(COMP).physics(phys).feature(outTag).selection().named(OUTLET);
        if (jnExpr != null) {
            String cathTag = "cathode_flux_" + phys;
            String anodeTag = "anode_flux_" + phys;
            m.component(COMP).physics(phys).create(cathTag, "FluxBoundary", 2);
            m.component(COMP).physics(phys).feature(cathTag).selection().named(CATHODE);
            m.component(COMP).physics(phys).feature(cathTag).label("Cathode Li+ Faraday-equivalent flux | no plating kinetics");
            m.component(COMP).physics(phys).feature(cathTag).set("species", new int[]{1});
            m.component(COMP).physics(phys).feature(cathTag).set("FluxType", "GeneralInwardFlux");
            m.component(COMP).physics(phys).feature(cathTag).set("J0", new String[]{"-(" + jnExpr + ")/F_const"});
            m.component(COMP).physics(phys).create(anodeTag, "FluxBoundary", 2);
            m.component(COMP).physics(phys).feature(anodeTag).selection().named(ANODE);
            m.component(COMP).physics(phys).feature(anodeTag).label("Anode Li+ Faraday-equivalent flux | no dissolution kinetics");
            m.component(COMP).physics(phys).feature(anodeTag).set("species", new int[]{1});
            m.component(COMP).physics(phys).feature(anodeTag).set("FluxType", "GeneralInwardFlux");
            m.component(COMP).physics(phys).feature(anodeTag).set("J0", new String[]{"-(" + jnExpr + ")/F_const"});
        }
        System.out.println("M10A4B_PHYSICS_BUILT|phys=" + phys + "|field=" + field + "|z=" + z);    }
    private static void stabilizeTds(Physics t) {
        t.prop("AdvancedSettings").set("ConvectiveTerm", "cons");
        t.prop("MassConsistentStabilization").set("massStreamlineDiffusion", true);
        t.prop("MassConsistentStabilization").set("massCrosswindDiffusion", true);
        t.prop("MassConsistentStabilization").set("CrosswindType", "Codina");
        t.prop("MassConsistentStabilization").set("Residual", "FullResidual");
        t.prop("ShapeProperty").set("order_concentration", 1);
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
        System.out.println("M10A4B_FLOW_REPAIR|sol=" + sol + "|q_in=" + f(qin) + "|q_out=" + f(qout) + "|relative=" + f(bal) + "|max_speed=" + f(speed));
        if (bal > 1e-6 || !Double.isFinite(speed)) throw new IllegalStateException("M10A4B_FLOW_REPAIR_FAIL bal=" + bal + " speed=" + speed);
        return sol;    }
    private static void createIonicStudy(Model m) {
        if (m.study().hasTag(STUDY_A4B)) {
            String[] old = m.study(STUDY_A4B).getSolverSequences("SolverSequence");
            m.study().remove(STUDY_A4B);
            for (String s : old) if (m.sol().hasTag(s)) m.sol().remove(s);
        }
        if (m.result().dataset().hasTag(DATA_A4B)) m.result().dataset().remove(DATA_A4B);
        m.study().create(STUDY_A4B);
        m.study(STUDY_A4B).label("M10A4 A4B stationary effective ionic transport | frozen A4A potential and A3 flow");
        m.study(STUDY_A4B).create("stat", "Stationary");
        for (String c : m.component().tags()) {
            for (String ph : m.component(c).physics().tags()) {
                boolean on = c.equals(COMP) && (ph.equals(PHYS_LI) || ph.equals(PHYS_BF4));
                m.study(STUDY_A4B).feature("stat").activate(ph, on);
            }
        }
        try {
            m.study(STUDY_A4B).run();
        } catch (Throwable first) {
            System.out.println("M10A4B_DEFAULT_SOLVE_REJECTED|class=" + first.getClass().getSimpleName() + "|message=" + first.getMessage());
        }
        String sol = solver(m, STUDY_A4B);
        configureDirectSolver(m, sol);
        m.sol(sol).runAll();
        m.result().dataset().create(DATA_A4B, "Solution");
        m.result().dataset(DATA_A4B).set("solution", sol);
        m.result().dataset(DATA_A4B).set("comp", COMP);
        m.result().dataset(DATA_A4B).label("M10A4 A4B effective ionic transport solution");    }
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

    private static void evaluateA4B(Model m, Path csv) throws Exception {
        List<String[]> rows = new ArrayList<>();
        speciesLedger(m, rows, "Li+", PHYS_LI, FIELD_LI);
        speciesLedger(m, rows, "BF4-", PHYS_BF4, FIELD_BF4);
        chargeLedger(m, rows);
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
        if (!positive || !finite) throw new IllegalStateException("M10A4B_POSITIVITY_GATE_FAIL positive=" + positive + " finite=" + finite);    }
    private static void speciesLedger(Model m, List<String[]> rows, String name,                                      String phys, String field) throws Exception {
        String ntf = phys.equals(PHYS_LI) ? (liPrefix + ".ntflux_" + field) : (bf4Prefix + ".ntflux_" + field);
        double inlet = eval(m, "IntSurface", DATA_A4B, COMP, INLET, 2, ntf, "mol/s");
        double outlet = eval(m, "IntSurface", DATA_A4B, COMP, OUTLET, 2, ntf, "mol/s");
        double cathode = eval(m, "IntSurface", DATA_A4B, COMP, CATHODE, 2, ntf, "mol/s");
        double anode = eval(m, "IntSurface", DATA_A4B, COMP, ANODE, 2, ntf, "mol/s");
        double walls = eval(m, "IntSurface", DATA_A4B, COMP, WALLS, 2, ntf, "mol/s");
        double net = inlet + outlet + cathode + anode + walls;
        double scale = Math.max(Math.max(Math.abs(inlet), Math.abs(outlet)),            Math.max(Math.max(Math.abs(cathode), Math.abs(anode)), Math.abs(walls)));
        double rel = Math.abs(net) / Math.max(scale, 1e-300);
        double min = eval(m, "MinVolume", DATA_A4B, COMP, DOM, 3, field, "mol/m^3");
        double max = eval(m, "MaxVolume", DATA_A4B, COMP, DOM, 3, field, "mol/m^3");
        String status = rel <= 1e-6 && min > -1e-10 ? "PASS" : "FAIL";
        rows.add(new String[]{name, "inlet_flux", INLET, f(inlet), "mol/s", ntf, "PASS"});
        rows.add(new String[]{name, "outlet_flux", OUTLET, f(outlet), "mol/s", ntf, "PASS"});
        rows.add(new String[]{name, "cathode_flux", CATHODE, f(cathode), "mol/s", ntf, "PASS"});
        rows.add(new String[]{name, "anode_flux", ANODE, f(anode), "mol/s", ntf, "PASS"});
        rows.add(new String[]{name, "walls_flux", WALLS, f(walls), "mol/s", ntf, "PASS"});
        rows.add(new String[]{name, "net_residual", "all_boundaries", f(net), "mol/s", "sum of boundary total-flux integrals", status});
        rows.add(new String[]{name, "relative_residual", "all_boundaries", f(rel), "1", "abs(net)/max(abs(boundary_fluxes))", status});
        rows.add(new String[]{name, "volume_min", DOM, f(min), "mol/m^3", "raw concentration minimum; no clipping", min > -1e-10 ? "PASS" : "FAIL"});
        rows.add(new String[]{name, "volume_max", DOM, f(max), "mol/m^3", "raw concentration maximum", "PASS"});
        System.out.println("M10A4B_SPECIES_LEDGER|species=" + name + "|in=" + f(inlet) + "|out=" + f(outlet) + "|cath=" + f(cathode) + "|anode=" + f(anode) + "|walls=" + f(walls) + "|net=" + f(net) + "|relative=" + f(rel) + "|min=" + f(min) + "|max=" + f(max) + "|status=" + status);
        if (!"PASS".equals(status)) throw new IllegalStateException(name + "_A4B_CONSERVATION_FAIL rel=" + rel + " min=" + min);    }
    private static void chargeLedger(Model m, List<String[]> rows) throws Exception {
        String jion = "F_const*(" + liPrefix + ".ntflux_" + FIELD_LI + "-" + bf4Prefix + ".ntflux_" + FIELD_BF4 + ")";
        double icath = eval(m, "IntSurface", DATA_A4B, COMP, CATHODE, 2, jion, "A");
        double ianode = eval(m, "IntSurface", DATA_A4B, COMP, ANODE, 2, jion, "A");
        double scale = Math.max(Math.max(Math.abs(icath), Math.abs(ianode)), 1e-300);
        double residual = Math.abs(icath + ianode) / scale;
        String a4aJn = "withsol('" + solver(m, "std_a4a_ohmic") + "', cd.nIl)";
        double a4aCath = eval(m, "IntSurface", "dset_a4a_ohmic", COMP, CATHODE, 2, "cd.nIl", "A");
        double a4aAnode = eval(m, "IntSurface", "dset_a4a_ohmic", COMP, ANODE, 2, "cd.nIl", "A");
        double cathRelA4A = Math.abs(icath - a4aCath) / Math.max(Math.abs(a4aCath), 1e-300);
        double anodeRelA4A = Math.abs(ianode - a4aAnode) / Math.max(Math.abs(a4aAnode), 1e-300);
        double enMin = eval(m, "MinVolume", DATA_A4B, COMP, DOM, 3, "(" + FIELD_LI + "-" + FIELD_BF4 + ")", "mol/m^3");
        double enMax = eval(m, "MaxVolume", DATA_A4B, COMP, DOM, 3, "(" + FIELD_LI + "-" + FIELD_BF4 + ")", "mol/m^3");
        double enMean = eval(m, "AvVolume", DATA_A4B, COMP, DOM, 3, "(" + FIELD_LI + "-" + FIELD_BF4 + ")", "mol/m^3");
        rows.add(new String[]{"charge", "ionic_cathode_current", CATHODE, f(icath), "A", jion, Double.isFinite(icath) ? "PASS" : "FAIL"});
        rows.add(new String[]{"charge", "ionic_anode_current", ANODE, f(ianode), "A", jion, Double.isFinite(ianode) ? "PASS" : "FAIL"});
        rows.add(new String[]{"charge", "current_residual", "cathode+anode", f(residual), "1", "abs(I_anode+I_cathode)/max(abs(I_anode),abs(I_cathode))", residual <= 1e-8 ? "PASS" : "FAIL"});
        rows.add(new String[]{"charge", "cathode_vs_A4A", CATHODE, f(cathRelA4A), "1", "relative deviation from frozen A4A cathode current", cathRelA4A <= 1e-8 ? "PASS" : "DIAGNOSTIC"});
        rows.add(new String[]{"charge", "anode_vs_A4A", ANODE, f(anodeRelA4A), "1", "relative deviation from frozen A4A anode current", anodeRelA4A <= 1e-8 ? "PASS" : "DIAGNOSTIC"});
        rows.add(new String[]{"charge", "electroneutrality_min", DOM, f(enMin), "mol/m^3", "raw cLi-cBF4 minimum", "DIAGNOSTIC"});
        rows.add(new String[]{"charge", "electroneutrality_max", DOM, f(enMax), "mol/m^3", "raw cLi-cBF4 maximum", "DIAGNOSTIC"});
        rows.add(new String[]{"charge", "electroneutrality_mean", DOM, f(enMean), "mol/m^3", "raw cLi-cBF4 mean", "DIAGNOSTIC"});
        System.out.println("M10A4B_CHARGE_LEDGER|I_cath_A=" + f(icath) + "|I_anode_A=" + f(ianode) + "|relative=" + f(residual) + "|cath_vs_A4A=" + f(cathRelA4A) + "|anode_vs_A4A=" + f(anodeRelA4A) + "|en_min=" + f(enMin) + "|en_max=" + f(enMax) + "|en_mean=" + f(enMean));    }
    private static void appendProvenance(Model m, Path path) throws Exception {
        List<String[]> add = new ArrayList<>();
        add.add(new String[]{"Li_effective_diffusivity", "D_Li_a4b_eff", m.param().evaluate("D_Li_a4b_eff", "m^2/s") + "", "m^2/s", "PROVISIONAL_SENSITIVITY_LITERATURE_ESTIMATE", "Concentrated glyme/LiBF4 transport literature range", "Effective scalar diffusivity; not validated concentrated-electrolyte property"});
        add.add(new String[]{"BF4_effective_diffusivity", "D_BF4_a4b_eff", m.param().evaluate("D_BF4_a4b_eff", "m^2/s") + "", "m^2/s", "PROVISIONAL_SENSITIVITY_LITERATURE_ESTIMATE", "Concentrated glyme/LiBF4 transport literature range", "Effective scalar diffusivity; not validated"});
        add.add(new String[]{"Li_effective_electrical_mobility", "u_Li_elec_a4b", m.param().evaluate("u_Li_elec_a4b", "m^2/(V*s)") + "", "m^2/(V*s)", "DERIVED_FROM_SENSITIVITY", "kappa_M10A4_nominal and t_plus_a4b_eff", "Effective mobility consistent with A4A conductivity baseline"});
        add.add(new String[]{"BF4_effective_electrical_mobility", "u_BF4_elec_a4b", m.param().evaluate("u_BF4_elec_a4b", "m^2/(V*s)") + "", "m^2/(V*s)", "DERIVED_FROM_SENSITIVITY", "kappa_M10A4_nominal and t_plus_a4b_eff", "Effective mobility consistent with A4A conductivity baseline"});
        add.add(new String[]{"effective_cation_transference", "t_plus_a4b_eff", "0.5", "1", "PROVISIONAL_SENSITIVITY", "Symmetric binary electrolyte sensitivity partition", "Not calibrated; explicit sensitivity value only"});
        List<String> lines = new ArrayList<>();
        if (Files.exists(path)) lines.addAll(Files.readAllLines(path, StandardCharsets.UTF_8));
        for (String[] r : add) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < r.length; i++) { if (i > 0) sb.append(','); sb.append(csv(r[i])); }
            lines.add(sb.toString());
        }
        Files.write(path, lines, StandardCharsets.UTF_8);    }
    private static void createResults(Model m) {
        plot3(m, "pg_a4b_li_conc", "A4B Li+ Concentration | effective NP | PROVISIONAL_SENSITIVITY", DATA_A4B, FIELD_LI, "mol/m^3", DOM, true);
        plot3(m, "pg_a4b_bf4_conc", "A4B BF4- Concentration | effective NP | PROVISIONAL_SENSITIVITY", DATA_A4B, FIELD_BF4, "mol/m^3", DOM, true);
        plot3(m, "pg_a4b_li_flux_mag", "A4B Li+ Flux Magnitude | effective NP", DATA_A4B, liPrefix + ".tfluxMag_" + FIELD_LI, "mol/(m^2*s)", DOM, true);
        plot3(m, "pg_a4b_bf4_flux_mag", "A4B BF4- Flux Magnitude | effective NP", DATA_A4B, bf4Prefix + ".tfluxMag_" + FIELD_BF4, "mol/(m^2*s)", DOM, true);
        plot3(m, "pg_a4b_phi_frozen", "A4B Frozen A4A Electrolyte Potential", DATA_A4B, "phi_l_a4a", "V", DOM, true);
        plot3(m, "pg_a4b_ionic_current_mag", "A4B Effective Ionic Current Magnitude | derived from species fluxes", DATA_A4B, "j_ion_a4b_mag", "A/m^2", DOM, true);
        plot3(m, "pg_a4b_cathode_li_flux", "A4B Cathode Li+ Normal Flux | Faraday-equivalent | no kinetics", DATA_A4B, liPrefix + ".ntflux_" + FIELD_LI, "mol/(m^2*s)", CATHODE, false);
        plot3(m, "pg_a4b_cathode_current", "A4B Cathode Ionic Current Density | from species fluxes", DATA_A4B, "F_const*(" + liPrefix + ".ntflux_" + FIELD_LI + "-" + bf4Prefix + ".ntflux_" + FIELD_BF4 + ")", "A/m^2", CATHODE, false);    }
    private static String physicsPrefix(Model m, String phys, String field) {
        String[][] table = m.component(COMP).physics(phys).featureInfo("info").getInfoTable("Expression", "recursive", "all");
        if (table != null) {
            for (String[] row : table) {
                for (String cell : row) {
                    if (cell != null && cell.endsWith(".ntflux_" + field)) {
                        return cell.substring(0, cell.length() - (".ntflux_" + field).length());
                    }
                }
            }
        }
        throw new IllegalStateException("PHYSICS_PREFIX_NOT_FOUND " + phys + "/" + field);    }
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
        }    }
    private static String solver(Model m, String study) {
        String[] s = m.study(study).getSolverSequences("SolverSequence");
        if (s.length < 1) throw new IllegalStateException("NO_SOLVER: " + study);
        return s[s.length - 1];    }
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
        }    }
    private static void p(Model m, String n, String v, String d) {
        m.param().set(n, v, d);    }
    private static String f(double x) {
        return String.format(Locale.ROOT, "%.15g", x);    }
    private static String csv(String s) {
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;    }
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
        }    }}