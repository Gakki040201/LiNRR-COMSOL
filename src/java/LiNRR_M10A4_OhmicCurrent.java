import com.comsol.model.Model;
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
 * M10A4 A4A: real-cell primary/ohmic current distribution on the frozen
 * accepted M10A3 real electrolyte component.
 *
 * Governing baseline:
 *   i_l = -kappa_eff * grad(phi_l)
 *   div(i_l) = 0
 *
 * No Butler-Volmer, electrode reactions, SEI, Li plating kinetics, Li-NRR,
 * HER/HOR, FE, or NH3 rate are created. The applied current is a transparent
 * sensitivity baseline, not recovered lab current.
 */
public final class LiNRR_M10A4_OhmicCurrent {
    private static final String ROOT =
        "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED";
    private static final String INPUT_MPH = ROOT +
        "\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
    private static final String RUN_DIR = ROOT +
        "\\runs\\M10A4\\20260820_121258";
    private static final String TABLE_DIR = ROOT + "\\results\\tables";
    private static final String COMP = "comp_species_liq_real";
    private static final String GEOM = "geom_electrolyte_fluid1";
    private static final String DOM = "m10a3_sel_dom_electrolyte_fluid";
    private static final String CATHODE = "m10a3_sel_bnd_electrolyte_gde_top";
    private static final String ANODE = "m10a3_sel_bnd_electrolyte_gde_bottom";

    private static int serial = 0;

    private LiNRR_M10A4_OhmicCurrent() {}

    public static void main(String[] args) throws Exception {
        Path checkpoint = Paths.get(RUN_DIR, "checkpoint_A4A_ohmic_current.mph");
        Path chargeCsv = Paths.get(TABLE_DIR, "M10A4_charge_conservation.csv");
        Files.createDirectories(chargeCsv.getParent());

        Model m = ModelUtil.load("M10A4A", INPUT_MPH);
        try {
            m.label("LiNRR_M10A4_ionic_current_li_plating_A4A.mph");
            m.comments("M10A4 A4A real-cell primary/ohmic current distribution on accepted M10A3 electrolyte component. Applied current is j_app_sensitivity only; no reaction kinetics or Li plating is implemented.");
            defineParameters(m);
            addPrimaryCurrentPhysics(m);
            definePostprocessingVariables(m);
            createOhmicStudy(m);
            evaluateA4A(m, chargeCsv);
            createResults(m);
            m.save(checkpoint.toString());
            System.out.println("M10A4A_OHMIC_CURRENT_DISTRIBUTION=PASS");
            System.out.println("CHECKPOINT_A4A=" + checkpoint);
        } finally {
            ModelUtil.remove("M10A4A");
        }
    }

    private static void defineParameters(Model m) {
        p(m, "A_echem_cathode", "38.44[cm^2]", "REAL_CAD authoritative cathode electrolyte/GDE interface area");
        p(m, "A_echem_anode", "38.44[cm^2]", "REAL_CAD authoritative anode electrolyte/GDE interface area");
        p(m, "kappa_M10A4_nominal", "0.3[S/m]", "PROVISIONAL_SENSITIVITY_LITERATURE_ESTIMATE transparent A4A baseline");
        p(m, "j_app_sensitivity", "10[A/m^2]", "PROVISIONAL_SENSITIVITY transparent 1 mA/cm^2 baseline; not lab current");
        p(m, "I_A4A_sensitivity", "j_app_sensitivity*A_echem_cathode", "DERIVED_FROM_SENSITIVITY total galvanostatic current");
        p(m, "phi_l_cathode_ref", "0[V]", "DERIVED electrolyte potential reference at cathode reaction plane");
    }

    private static void addPrimaryCurrentPhysics(Model m) {
        if (m.component(COMP).physics().hasTag("cd_a4a")) {
            m.component(COMP).physics().remove("cd_a4a");
        }
        m.component(COMP).physics().create("cd_a4a", "PrimaryCurrentDistribution", GEOM);
        m.component(COMP).physics("cd_a4a").label("M10A4 A4A Primary Current Distribution | pure ohmic real electrolyte | no kinetics");
        m.component(COMP).physics("cd_a4a").selection().named(DOM);
        m.component(COMP).physics("cd_a4a").feature("ice1").label("Electrolyte conductivity | PROVISIONAL_SENSITIVITY_LITERATURE_ESTIMATE");
        m.component(COMP).physics("cd_a4a").feature("ice1").set("sigmal_mat", "userdef");
        m.component(COMP).physics("cd_a4a").feature("ice1").set("sigmal", "kappa_M10A4_nominal");
        m.component(COMP).physics("cd_a4a").feature("ins1").label("Default electrical insulation on non-electrode electrolyte boundaries");

        m.component(COMP).physics("cd_a4a").create("anode_current", "ElectrolyteCurrent", 2);
        m.component(COMP).physics("cd_a4a").feature("anode_current").selection().named(ANODE);
        m.component(COMP).physics("cd_a4a").feature("anode_current").label("Anode: galvanostatic current density into real electrolyte | PROVISIONAL_SENSITIVITY");
        m.component(COMP).physics("cd_a4a").feature("anode_current").set("IonicCurrentType", "AverageCurrentDensity");
        m.component(COMP).physics("cd_a4a").feature("anode_current").set("Ial", "j_app_sensitivity");

        m.component(COMP).physics("cd_a4a").create("cathode_ground", "ElectrolytePotential", 2);
        m.component(COMP).physics("cd_a4a").feature("cathode_ground").selection().named(CATHODE);
        m.component(COMP).physics("cd_a4a").feature("cathode_ground").label("Cathode: electrolyte potential reference 0 V");
        m.component(COMP).physics("cd_a4a").feature("cathode_ground").set("philbnd", "phi_l_cathode_ref");
    }

    private static void definePostprocessingVariables(Model m) {
        if (m.component(COMP).variable().hasTag("var_a4a")) {
            m.component(COMP).variable().remove("var_a4a");
        }
        m.component(COMP).variable().create("var_a4a");
        m.component(COMP).variable("var_a4a").label("M10A4 A4A explicit ohmic-current audit variables");
        m.component(COMP).variable("var_a4a").selection().named(DOM);
        m.component(COMP).variable("var_a4a").set("j_a4a_x", "cd.Ilx", "Ohmic ionic current density x component");
        m.component(COMP).variable("var_a4a").set("j_a4a_y", "cd.Ily", "Ohmic ionic current density y component");
        m.component(COMP).variable("var_a4a").set("j_a4a_z", "cd.Ilz", "Ohmic ionic current density z component");
        m.component(COMP).variable("var_a4a").set("j_a4a_mag", "sqrt(cd.Ilx^2+cd.Ily^2+cd.Ilz^2)", "Ohmic ionic current density magnitude");
        m.component(COMP).variable("var_a4a").set("E_a4a_x", "cd.Ex", "Electrolyte electric field x component");
        m.component(COMP).variable("var_a4a").set("E_a4a_y", "cd.Ey", "Electrolyte electric field y component");
        m.component(COMP).variable("var_a4a").set("E_a4a_z", "cd.Ez", "Electrolyte electric field z component");
        m.component(COMP).variable("var_a4a").set("q_ohmic_a4a", "cd.Ilx*cd.Ex+cd.Ily*cd.Ey+cd.Ilz*cd.Ez", "DERIVED_ELECTRICAL_SOURCE_NO_THERMAL_FEEDBACK");
    }

    private static void createOhmicStudy(Model m) {
        if (m.study().hasTag("std_a4a_ohmic")) {
            String[] old = m.study("std_a4a_ohmic").getSolverSequences("SolverSequence");
            m.study().remove("std_a4a_ohmic");
            for (String s : old) if (m.sol().hasTag(s)) m.sol().remove(s);
        }
        if (m.result().dataset().hasTag("dset_a4a_ohmic")) m.result().dataset().remove("dset_a4a_ohmic");
        m.study().create("std_a4a_ohmic");
        m.study("std_a4a_ohmic").label("M10A4 A4A stationary primary current distribution");
        m.study("std_a4a_ohmic").create("stat", "Stationary");
        for (String c : m.component().tags()) {
            for (String ph : m.component(c).physics().tags()) {
                m.study("std_a4a_ohmic").feature("stat").activate(ph, ph.equals("cd_a4a"));
            }
        }
        m.study("std_a4a_ohmic").run();
        rebind(m, "std_a4a_ohmic", "dset_a4a_ohmic", COMP);
    }

    private static void rebind(Model m, String study, String data, String comp) {
        String sol = solver(m, study);
        if (!m.result().dataset().hasTag(data)) m.result().dataset().create(data, "Solution");
        m.result().dataset(data).set("solution", sol);
        m.result().dataset(data).set("comp", comp);
        m.result().dataset(data).label("M10A4 A4A solution for " + study);
    }

    private static void evaluateA4A(Model m, Path csv) throws Exception {
        String data = "dset_a4a_ohmic";
        String jn = "cd.nIl";
        double ianode = eval(m, "IntSurface", data, COMP, ANODE, 2, jn, "A");
        double icathode = eval(m, "IntSurface", data, COMP, CATHODE, 2, jn, "A");
        double scale = Math.max(Math.max(Math.abs(ianode), Math.abs(icathode)), 1e-300);
        double residual = Math.abs(ianode + icathode) / scale;

        double phiAnode = eval(m, "AvSurface", data, COMP, ANODE, 2, "cd.phil", "V");
        double phiCathode = eval(m, "AvSurface", data, COMP, CATHODE, 2, "cd.phil", "V");
        double drop = phiAnode - phiCathode;
        double currentMag = Math.abs(icathode);
        double resistance = currentMag > 0 ? drop / currentMag : Double.NaN;

        double jCathMean = eval(m, "AvSurface", data, COMP, CATHODE, 2, jn, "A/m^2");
        double jCathMin = eval(m, "MinSurface", data, COMP, CATHODE, 2, jn, "A/m^2");
        double jCathMax = eval(m, "MaxSurface", data, COMP, CATHODE, 2, jn, "A/m^2");
        double jAnodeMean = eval(m, "AvSurface", data, COMP, ANODE, 2, jn, "A/m^2");
        double jAnodeMin = eval(m, "MinSurface", data, COMP, ANODE, 2, jn, "A/m^2");
        double jAnodeMax = eval(m, "MaxSurface", data, COMP, ANODE, 2, jn, "A/m^2");
        double phiMin = eval(m, "MinVolume", data, COMP, DOM, 3, "cd.phil", "V");
        double phiMax = eval(m, "MaxVolume", data, COMP, DOM, 3, "cd.phil", "V");
        double qOhmic = eval(m, "IntVolume", data, COMP, DOM, 3, "q_ohmic_a4a", "W");

        boolean finite = Double.isFinite(ianode) && Double.isFinite(icathode) && Double.isFinite(phiMin)
            && Double.isFinite(phiMax) && Double.isFinite(jCathMean) && Double.isFinite(jCathMax)
            && Double.isFinite(jAnodeMean) && Double.isFinite(qOhmic) && Double.isFinite(resistance);
        String status = residual <= 1e-8 && finite && !Double.isNaN(resistance) ? "PASS" : "FAIL";
        if (!"PASS".equals(status)) throw new IllegalStateException("A4A_OHMIC_GATE_FAIL residual=" + residual + " finite=" + finite);

        List<String[]> rows = new ArrayList<>();

        rows.add(new String[]{"integrated_cathode_current", f(icathode), "A", "outward normal electrolyte current integral over cathode", "PASS"});
        rows.add(new String[]{"integrated_anode_current", f(ianode), "A", "outward normal electrolyte current integral over anode", "PASS"});
        rows.add(new String[]{"charge_conservation_residual", f(residual), "1", "abs(I_anode+I_cathode)/max(abs(I_anode),abs(I_cathode))", residual <= 1e-8 ? "PASS" : "FAIL"});
        rows.add(new String[]{"electrolyte_ohmic_drop", f(drop), "V", "AvSurface(phi,anode)-AvSurface(phi,cathode)", "PASS"});
        rows.add(new String[]{"derived_ohmic_resistance", f(resistance), "ohm", "electrolyte_ohmic_drop/abs(integrated_cathode_current)", "PASS"});
        rows.add(new String[]{"cathode_current_density_mean", f(jCathMean), "A/m^2", "AvSurface normal current density", "PASS"});
        rows.add(new String[]{"cathode_current_density_min", f(jCathMin), "A/m^2", "MinSurface normal current density", "PASS"});
        rows.add(new String[]{"cathode_current_density_max", f(jCathMax), "A/m^2", "MaxSurface normal current density", "PASS"});
        rows.add(new String[]{"anode_current_density_mean", f(jAnodeMean), "A/m^2", "AvSurface normal current density", "PASS"});
        rows.add(new String[]{"anode_current_density_min", f(jAnodeMin), "A/m^2", "MinSurface normal current density", "PASS"});
        rows.add(new String[]{"anode_current_density_max", f(jAnodeMax), "A/m^2", "MaxSurface normal current density", "PASS"});
        rows.add(new String[]{"electrolyte_potential_min", f(phiMin), "V", "MinVolume cd_a4a.phil", "PASS"});
        rows.add(new String[]{"electrolyte_potential_max", f(phiMax), "V", "MaxVolume cd_a4a.phil", "PASS"});
        rows.add(new String[]{"integrated_ohmic_heating", f(qOhmic), "W", "IntVolume j_a4a dot E_a4a", "PASS"});
        write(csv, "quantity,value,unit,definition,status", rows);

        System.out.println("M10A4A_CHARGE_CONSERVATION|I_anode_A=" + f(ianode) + "|I_cathode_A=" + f(icathode) + "|relative=" + f(residual) + "|status=" + status);
        System.out.println("M10A4A_OHMIC_DROP|phi_anode_V=" + f(phiAnode) + "|phi_cathode_V=" + f(phiCathode) + "|drop_V=" + f(drop));
        System.out.println("M10A4A_RESISTANCE|R_ohm=" + f(resistance) + "|I_total_A=" + f(currentMag));
        System.out.println("M10A4A_CURRENT_DENSITY|cathode_mean_A_m2=" + f(jCathMean) + "|cathode_min=" + f(jCathMin) + "|cathode_max=" + f(jCathMax) + "|anode_mean=" + f(jAnodeMean) + "|anode_min=" + f(jAnodeMin) + "|anode_max=" + f(jAnodeMax));
        System.out.println("M10A4A_OHMIC_HEATING|integrated_W=" + f(qOhmic));
    }

    private static void createResults(Model m) {
        String data = "dset_a4a_ohmic";
        plot3(m, "pg_a4a_electrolyte_potential", "A4A Electrolyte Potential | real electrolyte", data, "cd.phil", "V", DOM, true);
        plot3(m, "pg_a4a_ionic_current_mag", "A4A Ionic Current Magnitude | real electrolyte", data, "j_a4a_mag", "A/m^2", DOM, true);
        plot3(m, "pg_a4a_cathode_current", "A4A Cathode Local Current Density | real reaction plane", data, "cd.nIl", "A/m^2", CATHODE, false);
        plot3(m, "pg_a4a_anode_current", "A4A Anode Local Current Density | real reaction plane", data, "cd.nIl", "A/m^2", ANODE, false);
        plot3(m, "pg_a4a_ohmic_heating", "A4A Derived Ohmic Heating | NO_THERMAL_FEEDBACK", data, "q_ohmic_a4a", "W/m^3", DOM, true);
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

    private static String solver(Model m, String study) {
        String[] s = m.study(study).getSolverSequences("SolverSequence");
        if (s.length < 1) throw new IllegalStateException("NO_SOLVER: " + study);
        return s[s.length - 1];
    }

    private static double eval(Model m, String type, String data, String comp, String sel, int dim, String expr, String unit) {
        String t = "m10a4_ev_" + (++serial);
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
