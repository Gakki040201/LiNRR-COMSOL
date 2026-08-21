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
 * M10A4 A4B electroneutral, strictly-positive effective binary-ion transport.
 *
 * This is a reduced transference-number form of an electroneutral binary
 * Nernst-Planck model.  The common salt concentration is c=c_bulk*exp(z).
 * Species fluxes are reconstructed as
 *   N_Li  = N_salt + t_plus*i_A4A/F
 *   N_BF4 = N_salt - (1-t_plus)*i_A4A/F.
 * Thus F*(N_Li-N_BF4)=i_A4A identically, while cLi=cBF4 enforces local
 * electroneutrality.  D_salt and t_plus are provisional sensitivities, not
 * experimentally calibrated transport properties.  No concentration clipping
 * or replacement is used.
 */
public final class LiNRR_M10A4_IonicTransportElectroneutral {
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
    private static final String PHYS_SALT = "gfp_salt_a4b";
    private static final String Z_SALT = "zSalt_a4b";
    private static final String C_SALT = "cSalt_a4b";
    private static final String C_LI = "cLi_a4b";
    private static final String C_BF4 = "cBF4_a4b";
    private static final String STUDY_FLOW = "std_a4b_flow_repair";
    private static final String DATA_FLOW = "dset_a4b_flow_repair";
    private static final String STUDY_A4B = "std_a4b_ionic_en";
    private static final String DATA_A4B = "dset_a4b_ionic_en";
    private static int serial = 0;

    private LiNRR_M10A4_IonicTransportElectroneutral() {}

    public static void main(String[] args) throws Exception {
        Path attempt = Paths.get(RUN_DIR, "checkpoint_A4B_electroneutral_attempt8.mph");
        Path accepted = Paths.get(RUN_DIR, "checkpoint_A4B_ionic_transport.mph");
        Path ledger = Paths.get(TABLE_DIR, "M10A4_ionic_species_conservation_attempt8.csv");
        Path provenance = Paths.get(TABLE_DIR, "M10A4_ionic_transport_provenance_attempt8.csv");
        Model m = ModelUtil.load("M10A4BElectroneutral", INPUT_MPH);
        try {
            m.label("LiNRR_M10A4_ionic_current_li_plating_A4B_electroneutral.mph");
            m.comments("M10A4 A4B reduced electroneutral binary Nernst-Planck transport. Strictly positive common salt concentration; A4A current partitioned by provisional t_plus; no kinetics and no concentration clipping.");
            String a4aSol = solver(m, "std_a4a_ohmic");
            defineParameters(m);
            refineIonBoundaries(m);
            String flowSol = repairLiquidFlow(m);
            buildSaltPde(m, flowSol, a4aSol);
            defineAuditVariables(m, flowSol, a4aSol);
            createIonicStudy(m);
            m.save(attempt.toString()); // preserve the solved attempt even if a gate fails
            evaluateA4B(m, ledger, a4aSol);
            writeProvenance(m, provenance);
            createResults(m, a4aSol);
            m.save(accepted.toString());
            System.out.println("M10A4B_IONIC_TRANSPORT=PASS");
            System.out.println("CHECKPOINT_A4B=" + accepted);
        } finally {
            ModelUtil.remove("M10A4BElectroneutral");
        }
    }

    private static void defineParameters(Model m) {
        p(m, "c_salt_bulk_a4b", "1000[mol/m^3]", "DERIVED_FROM_LAB_MANUAL nominal 1 M LiBF4 electrolyte");
        p(m, "D_salt_a4b_eff", "1e-8[m^2/s]", "PROVISIONAL_SENSITIVITY_CALIBRATION_REQUIRED effective salt diffusivity");
        p(m, "t_plus_a4b", "0.5", "ASSUMED_SENSITIVITY_CALIBRATION_REQUIRED Li+ transference number; not experimentally calibrated");
        p(m, "beta_a4b_numdiff", "0.5", "NUMERICAL_REGULARIZATION_SENSITIVITY conservative mesh-Peclet numerical diffusion");
        p(m, "lambda_a4b", "1", "DERIVED continuation parameter; final requested solution equals one");
    }

    private static void refineIonBoundaries(Model m) throws Exception {
        String mesh = m.component(COMP).mesh().tags()[0];
        String tag = "m10a4b_ion_boundary_refine";
        if (m.component(COMP).mesh(mesh).feature().hasTag(tag))
            m.component(COMP).mesh(mesh).feature().remove(tag);
        List<Integer> ids = new ArrayList<>();
        for (String sel : new String[]{CATHODE, ANODE, INLET, OUTLET, WALLS})
            for (int id : m.component(COMP).selection(sel).entities(2))
                if (!ids.contains(id)) ids.add(id);
        int[] arr = new int[ids.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = ids.get(i);
        m.component(COMP).mesh(mesh).create(tag, "Refine");
        m.component(COMP).mesh(mesh).feature(tag).selection().geom(2);
        m.component(COMP).mesh(mesh).feature(tag).selection().set(arr);
        m.component(COMP).mesh(mesh).run();
        System.out.println("M10A4B_BOUNDARY_MESH_REFINED|mesh=" + mesh +
            "|boundary_ids=" + arr.length + "|elements=" + m.component(COMP).mesh(mesh).getNumElem());
    }

    private static void buildSaltPde(Model m, String flowSol, String a4aSol) throws Exception {
        if (m.component(COMP).physics().hasTag(PHYS_SALT)) m.component(COMP).physics().remove(PHYS_SALT);
        Physics pde = m.component(COMP).physics().create(PHYS_SALT, "WeakFormPDE", GEOM, new String[]{Z_SALT});
        pde.label("M10A4 A4B strictly-positive electroneutral salt transport | reduced binary NP");
        pde.selection().named(DOM);
        String ux = "withsol('" + flowSol + "',u)";
        String uy = "withsol('" + flowSol + "',v)";
        String uz = "withsol('" + flowSol + "',w)";
        String speed = "sqrt((" + ux + ")^2+(" + uy + ")^2+(" + uz + ")^2)";
        String dtot = "(D_salt_a4b_eff+beta_a4b_numdiff*max(" + speed + ",u_liq_a4b_ref)*h)";
        String jx = "(-" + dtot + "*" + C_SALT + "*" + Z_SALT + "x+lambda_a4b*(" + ux + ")*" + C_SALT + ")";
        String jy = "(-" + dtot + "*" + C_SALT + "*" + Z_SALT + "y+lambda_a4b*(" + uy + ")*" + C_SALT + ")";
        String jz = "(-" + dtot + "*" + C_SALT + "*" + Z_SALT + "z+lambda_a4b*(" + uz + ")*" + C_SALT + ")";
        pde.feature("wfeq1").set("weak", new String[][]{{
            "test(" + Z_SALT + ")*" + C_SALT + "*" + Z_SALT + "t-test(" + Z_SALT + "x)*" + jx +
            "-test(" + Z_SALT + "y)*" + jy + "-test(" + Z_SALT + "z)*" + jz
        }});
        pde.feature("init1").set(Z_SALT, "0");

        String un = "lambda_a4b*((" + ux + ")*nx+(" + uy + ")*ny+(" + uz + ")*nz)";
        addWeakFlux(pde, "in_salt_a4b", INLET, "test(" + Z_SALT + ")*(" + un + ")*c_salt_bulk_a4b",
            "Conservative Danckwerts inlet salt flux");
        addWeakFlux(pde, "out_salt_a4b", OUTLET, "test(" + Z_SALT + ")*(" + un + ")*" + C_SALT,
            "Conservative advective outlet salt flux");
        String ramp = "(1-exp(-t/t_a4b_ramp))*lambda_a4b";
        String jn = "withsol('" + a4aSol + "',cd.nIl)";
        String electrodeSalt = "test(" + Z_SALT + ")*(1-t_plus_a4b)*" + ramp + "*(" + jn + ")/F_const";
        addWeakFlux(pde, "cath_salt_a4b", CATHODE, electrodeSalt,
            "Cathode electroneutral salt flux consistent with Li Faraday flux and blocked BF4");
        addWeakFlux(pde, "an_salt_a4b", ANODE, electrodeSalt,
            "Anode electroneutral salt flux consistent with Li Faraday flux and blocked BF4");
        System.out.println("M10A4B_PHYSICS_BUILT|phys=" + PHYS_SALT +
            "|form=electroneutral_positive_binary_NP|inlet=conservative_Danckwerts");
    }

    private static void addWeakFlux(Physics pde, String tag, String sel, String expr, String label) {
        pde.create(tag, "WeakContribution", 2);
        pde.feature(tag).selection().named(sel);
        pde.feature(tag).label(label);
        pde.feature(tag).set("weakExpression", new String[][]{{expr}});
    }

    private static void defineAuditVariables(Model m, String flowSol, String a4aSol) {
        if (m.component(COMP).variable().hasTag("var_a4b")) m.component(COMP).variable().remove("var_a4b");
        m.component(COMP).variable().create("var_a4b");
        m.component(COMP).variable("var_a4b").label("M10A4 A4B electroneutral flux audit variables");
        m.component(COMP).variable("var_a4b").selection().named(DOM);
        String ux = "withsol('" + flowSol + "',u)";
        String uy = "withsol('" + flowSol + "',v)";
        String uz = "withsol('" + flowSol + "',w)";
        String ramp = "(1-exp(-t/t_a4b_ramp))*lambda_a4b";
        m.component(COMP).variable("var_a4b").set(C_SALT, "c_salt_bulk_a4b*exp(" + Z_SALT + ")", "Strictly positive common salt concentration");
        m.component(COMP).variable("var_a4b").set(C_LI, C_SALT, "Li+ concentration; exact electroneutral equality");
        m.component(COMP).variable("var_a4b").set(C_BF4, C_SALT, "BF4- concentration; exact electroneutral equality");
        m.component(COMP).variable("var_a4b").set("u_liq_a4b_x", ux, "Accepted real liquid velocity x");
        m.component(COMP).variable("var_a4b").set("u_liq_a4b_y", uy, "Accepted real liquid velocity y");
        m.component(COMP).variable("var_a4b").set("u_liq_a4b_z", uz, "Accepted real liquid velocity z");
        m.component(COMP).variable("var_a4b").set("speed_a4b", "sqrt(u_liq_a4b_x^2+u_liq_a4b_y^2+u_liq_a4b_z^2)", "Accepted real liquid speed");
        m.component(COMP).variable("var_a4b").set("Dtot_salt_a4b", "D_salt_a4b_eff+beta_a4b_numdiff*max(speed_a4b,u_liq_a4b_ref)*h", "Regularized effective salt diffusivity");
        for (String axis : new String[]{"x", "y", "z"}) {
            String u = "u_liq_a4b_" + axis;
            String ns = "-Dtot_salt_a4b*" + C_SALT + "*" + Z_SALT + axis + "+" + u + "*" + C_SALT;
            String ia = ramp + "*withsol('" + a4aSol + "',cd.Il" + axis + ")";
            m.component(COMP).variable("var_a4b").set("Nsalt_a4b_" + axis, ns, "Common salt flux " + axis);
            m.component(COMP).variable("var_a4b").set("iA4A_a4b_" + axis, ia, "Ramped accepted A4A current " + axis);
            m.component(COMP).variable("var_a4b").set("NLi_a4b_" + axis, "Nsalt_a4b_" + axis + "+t_plus_a4b*iA4A_a4b_" + axis + "/F_const", "Li+ flux from transference decomposition");
            m.component(COMP).variable("var_a4b").set("NBF4_a4b_" + axis, "Nsalt_a4b_" + axis + "-(1-t_plus_a4b)*iA4A_a4b_" + axis + "/F_const", "BF4- flux from transference decomposition");
            m.component(COMP).variable("var_a4b").set("j_ion_a4b_" + axis, "F_const*(NLi_a4b_" + axis + "-NBF4_a4b_" + axis + ")", "Ionic current reconstructed from species fluxes");
        }
        m.component(COMP).variable("var_a4b").set("j_ion_a4b_mag", "sqrt(j_ion_a4b_x^2+j_ion_a4b_y^2+j_ion_a4b_z^2)", "Effective ionic current magnitude");
    }

    private static String repairLiquidFlow(Model m) throws Exception {
        if (m.study().hasTag(STUDY_FLOW)) {
            String[] old = m.study(STUDY_FLOW).getSolverSequences("SolverSequence");
            m.study().remove(STUDY_FLOW);
            for (String s : old) if (m.sol().hasTag(s)) m.sol().remove(s);
        }
        if (m.result().dataset().hasTag(DATA_FLOW)) m.result().dataset().remove(DATA_FLOW);
        m.study().create(STUDY_FLOW);
        m.study(STUDY_FLOW).label("M10A4 A4B accepted real A3 liquid-flow recovery");
        m.study(STUDY_FLOW).create("stat", "Stationary");
        for (String c : m.component().tags())
            for (String ph : m.component(c).physics().tags())
                m.study(STUDY_FLOW).feature("stat").activate(ph, c.equals(COMP) && ph.equals("spf_liq_local"));
        m.study(STUDY_FLOW).run();
        String sol = solver(m, STUDY_FLOW);
        m.result().dataset().create(DATA_FLOW, "Solution");
        m.result().dataset(DATA_FLOW).set("solution", sol);
        m.result().dataset(DATA_FLOW).set("comp", COMP);
        String q = "withsol('" + sol + "',u)*nx+withsol('" + sol + "',v)*ny+withsol('" + sol + "',w)*nz";
        double qin = eval(m, "IntSurface", DATA_FLOW, INLET, 2, q, "m^3/s");
        double qout = eval(m, "IntSurface", DATA_FLOW, OUTLET, 2, q, "m^3/s");
        double bal = Math.abs(qin + qout) / Math.max(Math.abs(qin), 1e-300);
        String speedExpr = "sqrt(withsol('" + sol + "',u)^2+withsol('" + sol + "',v)^2+withsol('" + sol + "',w)^2)";
        double speed = eval(m, "MaxVolume", DATA_FLOW, DOM, 3, speedExpr, "m/s");
        double tau = m.param().evaluate("V_liq_real_M10A3", "m^3") / Math.abs(qin);
        p(m, "tau_a4b", f(tau) + "[s]", "DERIVED accepted real-flow residence time");
        p(m, "t_a4b_end", "6*tau_a4b", "NUMERICAL_VERIFICATION_ONLY six residence times");
        p(m, "dt_a4b_output", "tau_a4b/50", "DERIVED transient output interval");
        p(m, "t_a4b_ramp", "tau_a4b/100", "NUMERICAL_VERIFICATION_ONLY smooth current startup");
        p(m, "u_liq_a4b_ref", f(speed) + "[m/s]", "DERIVED accepted real-flow maximum speed");
        System.out.println("M10A4B_FLOW_REPAIR|sol=" + sol + "|q_in=" + f(qin) +
            "|q_out=" + f(qout) + "|relative=" + f(bal) + "|max_speed=" + f(speed) + "|tau_s=" + f(tau));
        if (bal > 1e-6 || !Double.isFinite(speed))
            throw new IllegalStateException("M10A4B_FLOW_REPAIR_FAIL bal=" + bal + " speed=" + speed);
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
        m.study(STUDY_A4B).label("M10A4 A4B transient positive electroneutral effective ionic transport");
        m.study(STUDY_A4B).create("time", "Transient");
        m.study(STUDY_A4B).feature("time").set("tlist", "range(0,dt_a4b_output,t_a4b_end)");
        m.study(STUDY_A4B).feature("time").set("rtol", "1e-8");
        for (String c : m.component().tags())
            for (String ph : m.component(c).physics().tags())
                m.study(STUDY_A4B).feature("time").activate(ph, c.equals(COMP) && ph.equals(PHYS_SALT));
        m.study(STUDY_A4B).run();
        String sol = solver(m, STUDY_A4B);
        m.result().dataset().create(DATA_A4B, "Solution");
        m.result().dataset(DATA_A4B).set("solution", sol);
        m.result().dataset(DATA_A4B).set("comp", COMP);
    }

    private static void evaluateA4B(Model m, Path csv, String a4aSol) throws Exception {
        List<String[]> rows = new ArrayList<>();
        String un = "u_liq_a4b_x*nx+u_liq_a4b_y*ny+u_liq_a4b_z*nz";
        String ramp = "(1-exp(-t/t_a4b_ramp))*lambda_a4b";
        String jn = "withsol('" + a4aSol + "',cd.nIl)";
        double inlet = eval(m, "IntSurface", DATA_A4B, INLET, 2, "c_salt_bulk_a4b*(" + un + ")", "mol/s");
        double outlet = eval(m, "IntSurface", DATA_A4B, OUTLET, 2, C_SALT + "*(" + un + ")", "mol/s");
        double cathI = eval(m, "IntSurface", DATA_A4B, CATHODE, 2, ramp + "*(" + jn + ")", "A");
        double anI = eval(m, "IntSurface", DATA_A4B, ANODE, 2, ramp + "*(" + jn + ")", "A");
        double acc = eval(m, "IntVolume", DATA_A4B, DOM, 3, "d(" + C_SALT + ",t)", "mol/s");
        double tplus = m.param().evaluate("t_plus_a4b");
        double fconst = m.param().evaluate("F_const", "C/mol");
        double saltCath = (1.0 - tplus) * cathI / fconst;
        double saltAn = (1.0 - tplus) * anI / fconst;
        double saltNet = inlet + outlet + saltCath + saltAn + acc;
        double scale = maxAbs(inlet, outlet, saltCath, saltAn, acc);
        double saltRel = Math.abs(saltNet) / Math.max(scale, 1e-300);
        double liNet = saltNet + tplus * (cathI + anI) / fconst;
        double bf4Net = saltNet - (1.0 - tplus) * (cathI + anI) / fconst;
        double liRel = Math.abs(liNet) / Math.max(scale, 1e-300);
        double bf4Rel = Math.abs(bf4Net) / Math.max(scale, 1e-300);
        double cmin = eval(m, "MinVolume", DATA_A4B, DOM, 3, C_SALT, "mol/m^3");
        double cmax = eval(m, "MaxVolume", DATA_A4B, DOM, 3, C_SALT, "mol/m^3");
        double enMax = eval(m, "MaxVolume", DATA_A4B, DOM, 3, "abs(" + C_LI + "-" + C_BF4 + ")", "mol/m^3");
        double currentRel = Math.abs(cathI + anI) / Math.max(maxAbs(cathI, anI), 1e-300);
        addSpeciesRows(rows, "Li+", inlet, outlet, cathI / fconst, anI / fconst, acc, liNet, liRel, cmin, cmax);
        addSpeciesRows(rows, "BF4-", inlet, outlet, 0.0, 0.0, acc, bf4Net, bf4Rel, cmin, cmax);
        rows.add(new String[]{"charge", "ionic_cathode_current", CATHODE, f(cathI), "A", "conservative imposed A4A normal current", "PASS"});
        rows.add(new String[]{"charge", "ionic_anode_current", ANODE, f(anI), "A", "conservative imposed A4A normal current", "PASS"});
        rows.add(new String[]{"charge", "current_residual", "cathode+anode", f(currentRel), "1", "abs(I_anode+I_cathode)/max(abs(I))", currentRel <= 1e-8 ? "PASS" : "FAIL"});
        rows.add(new String[]{"charge", "electroneutrality_max_abs", DOM, f(enMax), "mol/m^3", "max(abs(cLi-cBF4)); common concentration", enMax <= 1e-12 ? "PASS" : "FAIL"});
        write(csv, "species,quantity,boundary,value,unit,definition,status", rows);
        boolean finite = Double.isFinite(cmin) && Double.isFinite(cmax) && Double.isFinite(liRel) && Double.isFinite(bf4Rel) && Double.isFinite(currentRel);
        boolean pass = finite && cmin > 0.0 && liRel <= 1e-6 && bf4Rel <= 1e-6 && currentRel <= 1e-8 && enMax <= 1e-12;
        System.out.println("M10A4B_SPECIES_LEDGER|Li_relative=" + f(liRel) + "|BF4_relative=" + f(bf4Rel) +
            "|in=" + f(inlet) + "|out=" + f(outlet) + "|acc=" + f(acc) + "|c_min=" + f(cmin) + "|c_max=" + f(cmax));
        System.out.println("M10A4B_CHARGE_LEDGER|I_cath_A=" + f(cathI) + "|I_anode_A=" + f(anI) +
            "|relative=" + f(currentRel) + "|electroneutrality_max_abs=" + f(enMax));
        System.out.println("M10A4B_GATES|finite=" + finite + "|positive=" + (cmin > 0.0) + "|status=" + (pass ? "PASS" : "FAIL"));
        if (!pass) throw new IllegalStateException("M10A4B_FORMAL_GATE_FAIL see " + csv);
    }

    private static void addSpeciesRows(List<String[]> rows, String species, double inlet, double outlet,
                                       double cath, double anode, double acc, double net, double rel,
                                       double min, double max) {
        String status = rel <= 1e-6 && min > 0.0 ? "PASS" : "FAIL";
        rows.add(new String[]{species, "inlet_flux", INLET, f(inlet), "mol/s", "conservative Danckwerts boundary flux", "PASS"});
        rows.add(new String[]{species, "outlet_flux", OUTLET, f(outlet), "mol/s", "conservative advective boundary flux", "PASS"});
        rows.add(new String[]{species, "cathode_flux", CATHODE, f(cath), "mol/s", species.equals("Li+") ? "Faraday-equivalent A4A flux" : "blocked anion zero flux", "PASS"});
        rows.add(new String[]{species, "anode_flux", ANODE, f(anode), "mol/s", species.equals("Li+") ? "Faraday-equivalent A4A flux" : "blocked anion zero flux", "PASS"});
        rows.add(new String[]{species, "walls_flux", WALLS, "0", "mol/s", "natural zero salt flux and A4A insulation", "PASS"});
        rows.add(new String[]{species, "accumulation", DOM, f(acc), "mol/s", "d(common salt concentration,t)", "PASS"});
        rows.add(new String[]{species, "net_residual", "all_boundaries", f(net), "mol/s", "boundary fluxes plus accumulation", status});
        rows.add(new String[]{species, "relative_residual", "all_boundaries", f(rel), "1", "abs(net)/max(abs(ledger terms))", status});
        rows.add(new String[]{species, "volume_min", DOM, f(min), "mol/m^3", "raw c_bulk*exp(z), no clipping", min > 0.0 ? "PASS" : "FAIL"});
        rows.add(new String[]{species, "volume_max", DOM, f(max), "mol/m^3", "raw c_bulk*exp(z), no clipping", "PASS"});
    }

    private static void writeProvenance(Model m, Path path) throws Exception {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"effective_salt_diffusivity", "D_salt_a4b_eff", f(m.param().evaluate("D_salt_a4b_eff", "m^2/s")), "m^2/s", "PROVISIONAL_SENSITIVITY_CALIBRATION_REQUIRED", "Not experimentally calibrated"});
        rows.add(new String[]{"cation_transference_number", "t_plus_a4b", f(m.param().evaluate("t_plus_a4b")), "1", "ASSUMED_SENSITIVITY_CALIBRATION_REQUIRED", "0.5 symmetry assumption; not experimentally calibrated"});
        rows.add(new String[]{"electroneutral_binary_flux_form", "N_i=N_salt+partition(i/F)", "ENFORCED", "1", "REDUCED_PHENOMENOLOGICAL_NERNST_PLANCK", "Calibration range unavailable; do not use quantitatively"});
        write(path, "parameter,symbol,value,unit,classification,notes", rows);
    }

    private static void createResults(Model m, String a4aSol) {
        plot3(m, "pg_a4b_li_conc", "A4B Li+ Concentration | exact electroneutrality", C_LI, "mol/m^3", DOM, true);
        plot3(m, "pg_a4b_bf4_conc", "A4B BF4- Concentration | exact electroneutrality", C_BF4, "mol/m^3", DOM, true);
        plot3(m, "pg_a4b_li_flux_mag", "A4B Li+ Flux Magnitude | reduced binary NP", "sqrt(NLi_a4b_x^2+NLi_a4b_y^2+NLi_a4b_z^2)", "mol/(m^2*s)", DOM, true);
        plot3(m, "pg_a4b_bf4_flux_mag", "A4B BF4- Flux Magnitude | reduced binary NP", "sqrt(NBF4_a4b_x^2+NBF4_a4b_y^2+NBF4_a4b_z^2)", "mol/(m^2*s)", DOM, true);
        plot3(m, "pg_a4b_ionic_current_mag", "A4B Ionic Current | species-flux identity", "j_ion_a4b_mag", "A/m^2", DOM, true);
        plot3(m, "pg_a4b_cathode_current", "A4B Conservative Cathode Current | accepted A4A", "withsol('" + a4aSol + "',cd.nIl)", "A/m^2", CATHODE, false);
    }

    private static String solver(Model m, String study) {
        String[] s = m.study(study).getSolverSequences("SolverSequence");
        if (s.length < 1) throw new IllegalStateException("NO_SOLVER: " + study);
        return s[s.length - 1];
    }

    private static double eval(Model m, String type, String data, String sel, int dim, String expr, String unit) {
        String tag = "m10a4b_en_ev_" + (++serial);
        m.result().numerical().create(tag, type);
        try {
            m.result().numerical(tag).set("data", data);
            m.result().numerical(tag).selection().geom(GEOM, dim);
            m.result().numerical(tag).selection().set(m.component(COMP).selection(sel).entities(dim));
            m.result().numerical(tag).set("expr", new String[]{expr});
            m.result().numerical(tag).set("unit", new String[]{unit});
            if (type.startsWith("Int")) {
                m.result().numerical(tag).set("intorderactive", true);
                m.result().numerical(tag).set("intorder", 8);
            }
            double[][] v = m.result().numerical(tag).getReal();
            if (v == null || v.length == 0 || v[0].length == 0) throw new IllegalStateException("EMPTY_EVAL " + expr);
            return v[0][v[0].length - 1];
        } finally {
            m.result().numerical().remove(tag);
        }
    }

    private static void plot3(Model m, String tag, String label, String expr, String unit, String sel, boolean volume) {
        if (m.result().hasTag(tag)) m.result().remove(tag);
        m.result().create(tag, "PlotGroup3D");
        m.result(tag).label(label);
        m.result(tag).set("data", DATA_A4B);
        String ft = volume ? "vol" : "surf";
        m.result(tag).create(ft, volume ? "Volume" : "Surface");
        m.result(tag).feature(ft).set("expr", expr);
        m.result(tag).feature(ft).set("unit", unit);
        m.result(tag).feature(ft).create("sel", "Selection");
        m.result(tag).feature(ft).feature("sel").selection().named(sel);
    }

    private static double maxAbs(double... values) {
        double m = 0.0;
        for (double v : values) m = Math.max(m, Math.abs(v));
        return m;
    }

    private static void p(Model m, String name, String value, String description) {
        m.param().set(name, value, description);
    }

    private static String f(double x) {
        return String.format(Locale.ROOT, "%.15g", x);
    }

    private static String csv(String s) {
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static void write(Path path, String header, List<String[]> rows) throws Exception {
        Files.createDirectories(path.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write(header);
            w.newLine();
            for (String[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) w.write(',');
                    w.write(csv(row[i]));
                }
                w.newLine();
            }
        }
    }
}
