import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** M10A4 read-only real-CAD electrochemical area audit. */
public final class LiNRR_M10A4_AreaAudit {
    private LiNRR_M10A4_AreaAudit() {}

    public static void main(String[] args) throws Exception {
        Path table = Paths.get(rt("AREA_TABLE"));
        Files.createDirectories(table.getParent());
        Model m = ModelUtil.load("M10A4AreaAudit", rt("INPUT_MPH"));
        try {
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"surface","selection_tag","component","physical_definition","area_mm2","area_cm2","used_for_current_normalization","provenance","status","notes"});

            add(m, rows, "sel_bnd_electrolyte_gde_top", "comp_electrolyte_flow", "cathode electrolyte/GDE interface (real-CAD electrolyte fluid face)", 2, "YES", "REAL_CAD", "GATE");
            add(m, rows, "sel_bnd_electrolyte_gde_bottom", "comp_electrolyte_flow", "anode electrolyte/GDE interface (real-CAD electrolyte fluid face)", 2, "YES", "REAL_CAD", "GATE");
            add(m, rows, "sel_bnd_electrolyte_inlet", "comp_electrolyte_flow", "electrolyte liquid inlet", 2, "no", "REAL_CAD", "AUDIT");
            add(m, rows, "sel_bnd_electrolyte_outlet", "comp_electrolyte_flow", "electrolyte liquid outlet", 2, "no", "REAL_CAD", "AUDIT");
            add(m, rows, "sel_dom_electrolyte_fluid", "comp_electrolyte_flow", "electrolyte fluid domain volume", 3, "no", "REAL_CAD", "AUDIT");
            add(m, rows, "sel_bnd_electrolyte_all", "comp_electrolyte_flow", "all electrolyte fluid boundaries", 2, "no", "REAL_CAD", "AUDIT");
            add(m, rows, "sel_bnd_electrolyte_walls", "comp_electrolyte_flow", "electrolyte non-electrode walls", 2, "no", "REAL_CAD", "AUDIT");
            add(m, rows, "sel_bnd_chamber_top_opening", "comp_registered", "registered chamber top opening (multi-boundary)", 2, "no", "REAL_CAD", "REFERENCE");
            add(m, rows, "sel_bnd_candidate_top_interface", "comp_registered", "registered top candidate interface (multi-boundary)", 2, "no", "REAL_CAD", "REFERENCE");
            add(m, rows, "sel_bnd_candidate_bottom_interface", "comp_registered", "registered bottom candidate interface (multi-boundary)", 2, "no", "REAL_CAD", "REFERENCE");

            rows.add(new String[]{"SSC physical cut", "ssc_cut^2", "parameter", "60x60 mm SSC cut area", f(m.param().evaluate("ssc_cut^2", "mm^2")), f(m.param().evaluate("ssc_cut^2", "cm^2")), "no", "DERIVED_FROM_LAB_MANUAL", "REFERENCE", "not electrochemically active by itself"});
            rows.add(new String[]{"gasket inner aperture", "gasket_inner^2", "parameter", "55x55 mm gasket inner opening", f(m.param().evaluate("gasket_inner^2", "mm^2")), f(m.param().evaluate("gasket_inner^2", "cm^2")), "no", "DERIVED_FROM_LAB_MANUAL", "REFERENCE", "physical seal window; not the fluid-domain CAD face"});
            rows.add(new String[]{"N2 open channel", "A_n2_interface", "parameter", "cathode gas-open channel area", f(m.param().evaluate("A_n2_interface", "mm^2")), f(m.param().evaluate("A_n2_interface", "cm^2")), "no", "REAL_CAD", "REFERENCE", "gas-side area; do not use for electrolyte current normalization"});
            rows.add(new String[]{"historical footprint", "A_ssc_active", "parameter", "historical SSC flowfield footprint estimate", f(m.param().evaluate("A_ssc_active", "mm^2")), f(m.param().evaluate("A_ssc_active", "cm^2")), "no", "DERIVED", "REFERENCE", "not assumed as M10A4 active area"});

            write(table, "surface,selection_tag,component,physical_definition,area_mm2,area_cm2,used_for_current_normalization,provenance,status,notes", rows);

            double top = area(m, "comp_electrolyte_flow", "sel_bnd_electrolyte_gde_top");
            double bottom = area(m, "comp_electrolyte_flow", "sel_bnd_electrolyte_gde_bottom");
            double eq = Math.abs(top - bottom) / Math.max(Math.abs(top), Math.abs(bottom));
            System.out.println("M10A4_AREA_AUDIT|cathode_mm2=" + f(top) + "|anode_mm2=" + f(bottom) + "|relative_difference=" + f(eq));
            if (eq > 1e-8) throw new IllegalStateException("M10A4_ACTIVE_AREA_ASYMMETRY " + eq);
            System.out.println("M10A4_AREA_AUDIT=PASS");
        } finally {
            ModelUtil.remove("M10A4AreaAudit");
        }
    }

    private static void add(Model m, List<String[]> rows, String sel, String comp, String def, int dim, String norm, String prov, String status) {
        double v;
        try {
            v = measure(m, comp, sel, dim, dim == 2);
        } catch (Throwable e) {
            rows.add(new String[]{"", sel, comp, def, "ERROR", "ERROR", norm, prov, "ERROR", e.getMessage()});
            return;
        }
        String mm2 = dim == 2 ? f(v) : "";
        String cm2 = dim == 2 ? f(v / 100.0) : "";
        rows.add(new String[]{"", sel, comp, def, mm2, cm2, norm, prov, status, ""});
    }

    private static double area(Model m, String comp, String sel) {
        return measure(m, comp, sel, 2, true);
    }

    private static double measure(Model m, String comp, String sel, int dim, boolean areaFlag) {
        int[] entities = m.component(comp).selection(sel).entities(dim);
        if (entities.length == 0) throw new IllegalStateException("EMPTY_SELECTION " + sel + " dim=" + dim);
        String geom = m.component(comp).geom().tags()[0];
        m.component(comp).geom(geom).measureFinal().selection().geom(dim);
        m.component(comp).geom(geom).measureFinal().selection().set(entities);
        return areaFlag
            ? m.component(comp).geom(geom).measureFinal().getArea()
            : m.component(comp).geom(geom).measureFinal().getVolume();
    }

    private static String f(double x) { return String.format(Locale.ROOT, "%.15g", x); }

    private static String csv(String s) { return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s; }

    private static void write(Path p, String h, List<String[]> rows) throws Exception {
        try (java.io.BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            w.write(h); w.newLine();
            for (String[] r : rows) {
                for (int i = 0; i < r.length; i++) { if (i > 0) w.write(','); w.write(csv(r[i])); }
                w.newLine();
            }
        }
    }

    private static String rt(String n) {
        try { return ((String) Class.forName("LiNRR_M10A4_RuntimeInputs").getField(n).get(null)).trim(); }
        catch (Exception e) { throw new IllegalStateException("RUNTIME_INPUT_MISSING: " + n, e); }
    }
}
