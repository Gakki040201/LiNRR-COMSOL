import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Result-only presentation layer for the compact Paper V1 M10A4 artifact. */
public final class LiNRR_M10A4_VisualizationRepair {
    private static int serial = 0;
    private static final String COMP = "comp_species_liq_real";
    private static final String DOM = "m10a3_sel_dom_electrolyte_fluid";
    private static final String CATH = "m10a3_sel_bnd_electrolyte_gde_top";
    private static final String DLIQ = "viz_dset_liq_sol20";
    private static final String DN2G = "viz_dset_n2_species_sol15";

    private LiNRR_M10A4_VisualizationRepair() {}

    public static void main(String[] args) throws Exception {
        Path output = Paths.get(LiNRR_M10A4_VisualizationCommon.runtime("OUTPUT_MPH"));
        Path evidence = Paths.get(LiNRR_M10A4_VisualizationCommon.runtime("EVIDENCE_DIR"));
        Path images = Paths.get(LiNRR_M10A4_VisualizationCommon.runtime("IMAGE_DIR"));
        Files.createDirectories(output.getParent());
        Files.createDirectories(evidence);
        Files.createDirectories(images);
        if (output.equals(Paths.get(LiNRR_M10A4_VisualizationCommon.runtime("INPUT_MPH"))))
            throw new IllegalStateException("REFUSE_OVERWRITE_FROZEN_SOURCE");

        Model model = ModelUtil.load("M10A4VisualizationRepair",
            LiNRR_M10A4_VisualizationCommon.runtime("INPUT_MPH"));
        try {
            List<String[]> specs = new ArrayList<String[]>();
            createDisplayDatasets(model);
            createReactor(model, specs);
            createLiquidFlow(model, specs);
            createSpecies(model, specs);
            createIonics(model, specs);
            createCurrent(model, specs);
            createLiEquivalent(model, specs);
            createColimitation(model, specs);
            createElectrical(model, specs);

            List<String[]> auditRows = validateAll(model, specs);
            appendFallbackRows(auditRows, evidence.resolve("PLOT_INTEGRITY_FALLBACK.csv"));
            LiNRR_M10A4_VisualizationCommon.writeCsv(evidence.resolve("PLOT_INTEGRITY_OUTPUT.csv"),
                "plot_tag,label,section,role,dataset,component,selection,expression,dimension,selection_count,plot_run_ok,warning,finite_min,finite_max,status,notes",
                auditRows);
            exportImages(model, images);
            model.label("LiNRR_M10A4_VISUAL_REVIEW.mph");
            model.comments("Paper V1 result-only visualization layer on the authoritative compact M10A4 artifact. No study, solver, or mesh run. Liquid flow routes retained sol20. N2 flow is exported separately from the accepted immutable pre-compaction checkpoint and is not embedded here. Generic donor, Li-equivalent, co-limitation, and electrical diagnostic limitations remain explicit.");
            model.save(output.toString());
            if (!Files.isRegularFile(output) || Files.size(output) == 0)
                throw new IllegalStateException("VISUAL_REVIEW_MPH_MISSING_AFTER_SAVE");
            System.out.println("VIZ_COMPACT_MODEL_PLOT_COUNT=" + specs.size());
            System.out.println("VIZ_MAIN_PLOT_COUNT=" + (specs.size() + 3));
            System.out.println("VIZ_MAIN_PLOT_RUNTIME_PASS=PASS");
            System.out.println("VIZ_MAIN_PLOT_WARNINGS=0");
            System.out.println("VISUAL_REVIEW_MODEL_SAVE=PASS");
            System.out.println("VISUALIZATION_REPAIR_SOLVE_TRIGGERED=FALSE");
        } finally { ModelUtil.remove("M10A4VisualizationRepair"); }
    }

    private static void createDisplayDatasets(Model model) {
        createSolutionDataset(model, DLIQ, "sol20", COMP,
            "V01 retained canonical real liquid flow | sol20 | DISPLAY ROUTE ONLY");
        createSolutionDataset(model, DN2G, "sol15", "comp_species_n2_real",
            "V02 retained cN2g component route | sol15 | DISPLAY ROUTE ONLY");
    }

    private static void createSolutionDataset(Model model, String tag, String solution,
                                              String component, String label) {
        if (model.result().dataset().hasTag(tag)) model.result().dataset().remove(tag);
        model.result().dataset().create(tag, "Solution");
        model.result().dataset(tag).set("solution", solution);
        model.result().dataset(tag).set("comp", component);
        model.result().dataset(tag).label(label);
    }

    private static void createReactor(Model model, List<String[]> specs) {
        copy(model, "viz00_reactor_full", "pg00_physical_cell",
            "V00 | REACTOR | Physical cell | REAL / PHYSICAL");
        spec(specs, "viz00_reactor_full", "V00_REACTOR", "MAIN_TEACHING",
            "dset_physical_mesh", "comp_cell_physical", "sel_dom_physical_all", "", 3, "1", "PURE_DISPLAY");
        copy(model, "viz00_reactor_exploded", "pg01_physical_exploded",
            "V00 | REACTOR | Exploded SSC/GDE location | DISPLAY SCALE ONLY");
        spec(specs, "viz00_reactor_exploded", "V00_REACTOR", "MAIN_TEACHING",
            "dset_physical_mesh", "comp_cell_physical", "sel_dom_physical_all", "", 3, "1", "PURE_DISPLAY");
        copy(model, "viz00_ssc_gde_location", "pg06_ssc_true_scale",
            "V00 | REACTOR | Cathode SSC/GDE location | PHYSICAL TRUE SCALE");
        spec(specs, "viz00_ssc_gde_location", "V00_REACTOR", "MAIN_TEACHING",
            "dset_physical_mesh", "comp_cell_physical", "sel_dom_cathode_ssc_true", "", 3, "1",
            "Cathode SSC/GDE physical location; true-scale 30 um literature-same-platform layer");
        surface(model, "viz00_reaction_plane",
            "V00 | REACTOR | AUTHORITATIVE REACTION PLANE | REAL INTERFACE", "dset_a4b_ionic_en",
            "1", "1", CATH);
        spec(specs, "viz00_reaction_plane", "V00_REACTOR", "MAIN_TEACHING",
            "dset_a4b_ionic_en", COMP, CATH, "1", 2, "1", "REAL INTERFACE; consistent reaction-plane framing");
    }

    private static void createLiquidFlow(Model model, List<String[]> specs) {
        fieldSlice(model, "viz01_liq_velocity",
            "V01 | FLOW | Electrolyte velocity | retained sol20 | LOG DISPLAY OF MAGNITUDE",
            DLIQ, "log10(1+sqrt(u^2+v^2+w^2)/(1e-6[m/s]))", "1", DOM, false);
        spec(specs, "viz01_liq_velocity", "V01_FLOW", "MAIN_TEACHING", DLIQ, COMP, DOM,
            "sqrt(u^2+v^2+w^2)", 3, "m/s",
            "RETAINED_CANONICAL_REAL_LIQUID_FLOW; plot uses labelled log display to expose chamber-scale variation");
        fieldSlice(model, "viz01_liq_pressure", "V01 | FLOW | Electrolyte pressure | retained sol20",
            DLIQ, "p", "Pa", DOM, true);
        spec(specs, "viz01_liq_pressure", "V01_FLOW", "MAIN_TEACHING", DLIQ, COMP, DOM,
            "p", 3, "Pa", "Pressure is not voltage");
        streamline(model, "viz01_liq_streamlines",
            "V01 | FLOW | Electrolyte streamlines | retained sol20",
            DLIQ, "m10a3_sel_bnd_electrolyte_inlet", new String[]{"u", "v", "w"});
        spec(specs, "viz01_liq_streamlines", "V01_FLOW", "MAIN_TEACHING", DLIQ, COMP, DOM,
            "sqrt(u^2+v^2+w^2)", 3, "m/s", "Seeded from authoritative retained-flow inlet");
    }

    private static void createSpecies(Model model, List<String[]> specs) {
        volume(model, "viz02_n2_gas", "V02 | SPECIES | N2 gas concentration | retained sol15 route",
            DN2G, "cN2g", "mol/m^3", "m10a3_sel_dom_n2_channel");
        spec(specs, "viz02_n2_gas", "V02_SPECIES", "MAIN_TEACHING", DN2G,
            "comp_species_n2_real", "m10a3_sel_dom_n2_channel", "cN2g", 3, "mol/m^3",
            "RETAINED_DERIVED component route; concentration only");
        volume(model, "viz02_n2_dissolved", "V02 | SPECIES | Dissolved N2 | final retained sol15",
            "dset_species_liq_n2", "cN2d", "mol/m^3", DOM);
        spec(specs, "viz02_n2_dissolved", "V02_SPECIES", "MAIN_TEACHING", "dset_species_liq_n2",
            COMP, DOM, "cN2d", 3, "mol/m^3", "final stored time");
        surface(model, "viz02_n2_reaction_plane",
            "V02 | SPECIES | N2 at AUTHORITATIVE REACTION PLANE | REAL INTERFACE",
            "dset_species_liq_n2", "cN2d", "mol/m^3", CATH);
        spec(specs, "viz02_n2_reaction_plane", "V02_SPECIES", "MAIN_TEACHING",
            "dset_species_liq_n2", COMP, CATH, "cN2d", 2, "mol/m^3", "final stored time; common framing");
        volume(model, "viz02_donor", "V02 | SPECIES | GENERIC DONOR | CALIBRATION REQUIRED",
            "dset_species_liq_n2", "cDonor", "mol/m^3", DOM);
        spec(specs, "viz02_donor", "V02_SPECIES", "MAIN_TEACHING", "dset_species_liq_n2",
            COMP, DOM, "cDonor", 3, "mol/m^3", "GENERIC DONOR; never ethanol");
    }

    private static void createIonics(Model model, List<String[]> specs) {
        volume(model, "viz03_electrolyte_potential",
            "V03 | IONICS | Electrolyte potential | NOT FULL-CELL VOLTAGE", "dset_a4a_ohmic",
            "cd.phil", "V", DOM);
        spec(specs, "viz03_electrolyte_potential", "V03_IONICS", "MAIN_TEACHING",
            "dset_a4a_ohmic", COMP, DOM, "cd.phil", 3, "V", "NOT FULL-CELL VOLTAGE");
        volume(model, "viz03_li_concentration",
            "V03 | IONICS | Li+ concentration | PROVISIONAL SENSITIVITY", "dset_a4b_ionic_en",
            "cLi_a4b", "mol/m^3", DOM);
        spec(specs, "viz03_li_concentration", "V03_IONICS", "MAIN_TEACHING",
            "dset_a4b_ionic_en", COMP, DOM, "cLi_a4b", 3, "mol/m^3", "PROVISIONAL SENSITIVITY");
        volume(model, "viz03_bf4_concentration",
            "V03 | IONICS | BF4- concentration | PROVISIONAL SENSITIVITY", "dset_a4b_ionic_en",
            "cBF4_a4b", "mol/m^3", DOM);
        spec(specs, "viz03_bf4_concentration", "V03_IONICS", "MAIN_TEACHING",
            "dset_a4b_ionic_en", COMP, DOM, "cBF4_a4b", 3, "mol/m^3", "PROVISIONAL SENSITIVITY");
        volume(model, "viz03_ionic_current",
            "V03 | IONICS | Ionic current density | SPECIES-FLUX IDENTITY", "dset_a4b_ionic_en",
            "j_ion_a4b_mag", "A/m^2", DOM);
        spec(specs, "viz03_ionic_current", "V03_IONICS", "MAIN_TEACHING",
            "dset_a4b_ionic_en", COMP, DOM, "j_ion_a4b_mag", 3, "A/m^2", "SPECIES-FLUX IDENTITY");
    }

    private static void createCurrent(Model model, List<String[]> specs) {
        acceptedSurface(model, specs, "viz04_cathode_current", "V04 | CURRENT | Cathode current | ELECTROLYTE OUTWARD",
            "pg_a4c_cathode_current", "V04_CURRENT", "MAIN_TEACHING", "A/m^2");
        acceptedSurface(model, specs, "viz04_current_nonuniformity", "V04 | CURRENT | Current nonuniformity | MAGNITUDE DIAGNOSTIC",
            "pg_a4c_nonuniformity", "V04_CURRENT", "MAIN_TEACHING", "A/m^2");
        acceptedSurface(model, specs, "viz04_current_crowding", "V04 | CURRENT | Current crowding | SPATIAL ASSOCIATION",
            "pg_a4c_crowding", "V04_CURRENT", "MAIN_TEACHING", "1");
    }

    private static void createLiEquivalent(Model model, List<String[]> specs) {
        for (int charge : new int[]{9, 45, 54, 99, 297})
            acceptedSurface(model, specs, "viz05_h" + charge,
                "V05 | LI-EQUIVALENT | " + charge + " C | NUMERICAL UPPER BOUND f=1",
                "pg_a4d_h" + charge, "V05_LI_EQUIVALENT", "MAIN_TEACHING", "m");
    }

    private static void createColimitation(Model model, List<String[]> specs) {
        acceptedSurface(model, specs, "viz06_n2_current",
            "V06 | CO-LIMITATION | N2-current overlap | DIAGNOSTIC ONLY",
            "pg_a4e_n2_current", "V06_COLIMITATION", "MAIN_TEACHING", "1");
        acceptedSurface(model, specs, "viz06_donor_current",
            "V06 | CO-LIMITATION | GENERIC DONOR-current overlap | CALIBRATION REQUIRED | DIAGNOSTIC ONLY",
            "pg_a4e_donor_current", "V06_COLIMITATION", "MAIN_TEACHING", "1");
        acceptedSurface(model, specs, "viz06_colim",
            "V06 | CO-LIMITATION | Four-field co-limitation | DIAGNOSTIC ONLY",
            "pg_a4e_colim", "V06_COLIMITATION", "MAIN_TEACHING", "1");
        acceptedSurface(model, specs, "viz06_robustness",
            "V06 | CO-LIMITATION | Threshold robustness q=0.40/0.50/0.60 | DIAGNOSTIC ONLY",
            "pg_a4e_robust", "V06_COLIMITATION", "MAIN_SUPPORT", "1");
    }

    private static void createElectrical(Model model, List<String[]> specs) {
        volume(model, "viz07_ohmic_drop", "V07 | ELECTRICAL | Electrolyte ohmic-drop diagnostic | NOT FULL-CELL VOLTAGE",
            "dset_a4a_ohmic", "cd.phil", "V", DOM);
        spec(specs, "viz07_ohmic_drop", "V07_ELECTRICAL", "MAIN_SUPPORT", "dset_a4a_ohmic",
            COMP, DOM, "cd.phil", 3, "V", "Supporting diagnostic");
        volume(model, "viz07_joule", "V07 | ELECTRICAL | Joule-source diagnostic | NO THERMAL FEEDBACK",
            "dset_a4a_ohmic", "q_ohmic_a4a", "W/m^3", DOM);
        spec(specs, "viz07_joule", "V07_ELECTRICAL", "MAIN_SUPPORT", "dset_a4a_ohmic",
            COMP, DOM, "q_ohmic_a4a", 3, "W/m^3", "Supporting diagnostic; no thermal feedback");
    }

    private static void acceptedSurface(Model model, List<String[]> specs, String tag, String label,
                                        String sourcePlot, String section, String role, String unit) {
        String expression = LiNRR_M10A4_VisualizationCommon.plotExpression(model, sourcePlot);
        surface(model, tag, label, "dset_a4b_ionic_en", expression, unit, CATH);
        spec(specs, tag, section, role, "dset_a4b_ionic_en", COMP, CATH, expression, 2, unit,
            "Accepted expression recovered programmatically; common reaction-plane framing");
    }

    private static void copy(Model model, String target, String source, String label) {
        if (model.result().hasTag(target)) model.result().remove(target);
        model.result().copy(target, source);
        model.result(target).label(label);
    }

    private static void fieldSlice(Model model, String tag, String label, String data,
                                   String expression, String unit, String selection, boolean addSurface) {
        reset(model, tag, label, data);
        model.result(tag).create("slice", "Slice");
        model.result(tag).feature("slice").set("expr", expression);
        model.result(tag).feature("slice").set("unit", unit);
        model.result(tag).feature("slice").create("sel", "Selection");
        model.result(tag).feature("slice").feature("sel").selection().named(selection);
        if (addSurface) {
            model.result(tag).create("surf", "Surface");
            model.result(tag).feature("surf").set("expr", expression);
            model.result(tag).feature("surf").set("unit", unit);
            model.result(tag).feature("surf").create("sel", "Selection");
            model.result(tag).feature("surf").feature("sel").selection().named(selection);
        }
    }

    private static void streamline(Model model, String tag, String label, String data,
                                   String inlet, String[] expression) {
        reset(model, tag, label, data);
        model.result(tag).create("str", "Streamline");
        model.result(tag).feature("str").selection().named(inlet);
        model.result(tag).feature("str").set("expr", expression);
    }

    private static void volume(Model model, String tag, String label, String data,
                               String expression, String unit, String selection) {
        reset(model, tag, label, data);
        model.result(tag).create("vol", "Volume");
        model.result(tag).feature("vol").set("expr", expression);
        model.result(tag).feature("vol").set("unit", unit);
        model.result(tag).feature("vol").create("sel", "Selection");
        model.result(tag).feature("vol").feature("sel").selection().named(selection);
    }

    private static void surface(Model model, String tag, String label, String data,
                                String expression, String unit, String selection) {
        reset(model, tag, label, data);
        model.result(tag).create("surf", "Surface");
        model.result(tag).feature("surf").set("expr", expression);
        model.result(tag).feature("surf").set("unit", unit);
        model.result(tag).feature("surf").create("sel", "Selection");
        model.result(tag).feature("surf").feature("sel").selection().named(selection);
        applyReactionView(model, tag);
    }

    private static void applyReactionView(Model model, String tag) {
        try {
            String view = model.result("pg_a4c_cathode_current").getString("view");
            if (view != null && !view.trim().isEmpty()) model.result(tag).set("view", view);
        } catch (Throwable ignored) {}
    }

    private static void reset(Model model, String tag, String label, String data) {
        if (model.result().hasTag(tag)) model.result().remove(tag);
        model.result().create(tag, "PlotGroup3D");
        model.result(tag).label(label);
        model.result(tag).set("data", data);
    }

    private static void spec(List<String[]> specs, String tag, String section, String role,
                             String data, String component, String selection, String expression,
                             int dimension, String unit, String notes) {
        specs.add(new String[]{tag, section, role, data, component, selection, expression,
            Integer.toString(dimension), unit, notes});
    }

    private static List<String[]> validateAll(Model model, List<String[]> specs) {
        List<String[]> rows = new ArrayList<String[]>();
        for (String[] spec : specs) {
            String tag = spec[0];
            model.result(tag).run();
            boolean warning = model.result(tag).hasWarning();
            if (warning) throw new IllegalStateException("VIZ_PLOT_WARNING " + tag);
            int dimension = Integer.parseInt(spec[7]);
            int count = model.component(spec[4]).selection(spec[5]).entities(dimension).length;
            if (count == 0) throw new IllegalStateException("VIZ_EMPTY_SELECTION " + tag);
            String min = "NOT_APPLICABLE", max = "NOT_APPLICABLE";
            if (!spec[6].isEmpty()) {
                double[] values = triple(model, spec[3], spec[4], spec[5], dimension, spec[6], spec[8], needsLast(spec[3]));
                min = f(values[0]);
                max = f(values[1]);
            }
            rows.add(new String[]{tag, model.result(tag).label(), spec[1], spec[2], spec[3], spec[4],
                spec[5], spec[6], spec[7], Integer.toString(count), "true", "false", min, max, "PASS", spec[9]});
            System.out.println("VIZ_PLOT_RUNTIME|tag=" + tag + "|status=PASS|warning=false");
        }
        return rows;
    }

    private static boolean needsLast(String dataset) {
        return "dset_species_liq_n2".equals(dataset) || "dset_a4b_ionic_en".equals(dataset)
            || DN2G.equals(dataset);
    }

    private static double[] triple(Model model, String dataset, String component, String selection,
                                   int dimension, String expression, String unit, boolean last) {
        int[] ids = model.component(component).selection(selection).entities(dimension);
        String suffix = dimension == 3 ? "Volume" : "Surface";
        return new double[]{scalar(model, "Min" + suffix, dataset, component, ids, dimension, expression, unit, last),
            scalar(model, "Max" + suffix, dataset, component, ids, dimension, expression, unit, last)};
    }

    private static double scalar(Model model, String type, String dataset, String component, int[] ids,
                                 int dimension, String expression, String unit, boolean last) {
        String tag = "viz_validate_" + (++serial);
        model.result().numerical().create(tag, type);
        try {
            model.result().numerical(tag).set("data", dataset);
            if (last) {
                String[] levels = model.result().numerical(tag).getStringArray("looplevelinput");
                for (int i = 0; i < levels.length; i++) levels[i] = "last";
                model.result().numerical(tag).set("looplevelinput", levels);
            }
            model.result().numerical(tag).selection().geom(model.component(component).geom().tags()[0], dimension);
            model.result().numerical(tag).selection().set(ids);
            model.result().numerical(tag).set("expr", new String[]{expression});
            model.result().numerical(tag).set("unit", new String[]{unit});
            double[][] values = model.result().numerical(tag).getReal();
            if (values == null || values.length == 0 || values[0].length == 0)
                throw new IllegalStateException("VIZ_EMPTY_FIELD " + dataset + " " + expression);
            double value = values[0][values[0].length - 1];
            if (!Double.isFinite(value)) throw new IllegalStateException("VIZ_NONFINITE_FIELD " + expression);
            return value;
        } finally { model.result().numerical().remove(tag); }
    }

    private static void exportImages(Model model, Path images) throws Exception {
        String[][] exports = {
            {"viz00_reactor_full", "reactor_full.png"}, {"viz00_reactor_exploded", "reactor_exploded.png"},
            {"viz00_ssc_gde_location", "ssc_gde_location.png"}, {"viz00_reaction_plane", "reaction_plane.png"},
            {"viz01_liq_velocity", "liquid_velocity.png"},
            {"viz01_liq_pressure", "liquid_pressure.png"}, {"viz01_liq_streamlines", "liquid_streamlines.png"},
            {"viz02_n2_gas", "n2_gas_concentration.png"}, {"viz02_n2_dissolved", "dissolved_n2.png"},
            {"viz02_n2_reaction_plane", "n2_reaction_plane.png"}, {"viz02_donor", "generic_donor.png"},
            {"viz03_electrolyte_potential", "electrolyte_potential.png"}, {"viz03_li_concentration", "li_concentration.png"},
            {"viz03_bf4_concentration", "bf4_concentration.png"}, {"viz03_ionic_current", "ionic_current.png"},
            {"viz04_cathode_current", "cathode_current.png"},
            {"viz04_current_nonuniformity", "current_nonuniformity.png"},
            {"viz04_current_crowding", "current_crowding.png"},
            {"viz05_h9", "li_equivalent_9C.png"}, {"viz05_h45", "li_equivalent_45C.png"},
            {"viz05_h54", "li_equivalent_54C.png"}, {"viz05_h99", "li_equivalent_99C.png"},
            {"viz05_h297", "li_equivalent_297C.png"}, {"viz06_n2_current", "n2_current_overlap.png"},
            {"viz06_donor_current", "donor_current_overlap.png"}, {"viz06_colim", "four_field_colimitation.png"},
            {"viz06_robustness", "colimitation_robustness.png"}, {"viz07_ohmic_drop", "ohmic_drop.png"},
            {"viz07_joule", "joule_diagnostic.png"}
        };
        for (String[] item : exports) image(model, item[0], images.resolve(item[1]));
    }

    private static void image(Model model, String plot, Path file) throws Exception {
        model.result(plot).run();
        String tag = "viz_image_" + (++serial);
        model.result().export().create(tag, plot, "Image3D");
        try {
            model.result().export(tag).set("target", "file");
            model.result().export(tag).set("filename", file.toString());
            model.result().export(tag).set("width", 1400);
            model.result().export(tag).set("height", 1000);
            model.result().export(tag).run();
            if (!Files.isRegularFile(file) || Files.size(file) == 0)
                throw new IllegalStateException("VIZ_IMAGE_EMPTY " + file);
        } finally { model.result().export().remove(tag); }
    }

    private static void appendFallbackRows(List<String[]> rows, Path fallback) throws Exception {
        if (!Files.isRegularFile(fallback)) throw new IllegalStateException("FALLBACK_PLOT_AUDIT_MISSING");
        List<String> lines = Files.readAllLines(fallback, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            List<String> columns = LiNRR_M10A4_VisualizationCommon.parseCsv(lines.get(i));
            rows.add(columns.toArray(new String[columns.size()]));
        }
    }

    private static String f(double value) { return String.format(Locale.ROOT, "%.17g", value); }
}
