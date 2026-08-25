import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Corrected result-only canonical readability audit with explicit retained/fallback routing. */
public final class LiNRR_M10A4_CorrectedCanonicalReadability {
    private static int serial = 0;
    private static final String COMPACT_SHA = "FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B";
    private static final String COMP = "comp_species_liq_real";
    private static final String DOM = "m10a3_sel_dom_electrolyte_fluid";
    private static final String CATH = "m10a3_sel_bnd_electrolyte_gde_top";

    private LiNRR_M10A4_CorrectedCanonicalReadability() {}

    public static void main(String[] args) throws Exception {
        Path evidence = Paths.get(LiNRR_M10A4_VisualizationCommon.runtime("EVIDENCE_DIR"));
        Path fallback = evidence.resolve("ACCEPTED_FALLBACK_SOURCE_ROUTING.csv");
        List<String[]> rows = new ArrayList<String[]>();
        addFallbackRows(rows, fallback);

        Model model = ModelUtil.load("M10A4CorrectedCanonical",
            LiNRR_M10A4_VisualizationCommon.runtime("INPUT_MPH"));
        try {
            String compact = LiNRR_M10A4_VisualizationCommon.runtime("INPUT_MPH");
            add(model, rows, "retained_liquid_velocity", "dset_liq_solution", "dset_a4b_flow_repair",
                compact, COMPACT_SHA, COMP, DOM, 3, "sqrt(u^2+v^2+w^2)", "m/s", false,
                "RETAINED_CANONICAL",
                "Previous failed dset_liq_solution/sol3 observation preserved: legacy cache was cleared by compaction; sol20 recovery is accepted.");
            add(model, rows, "retained_liquid_pressure", "dset_liq_solution", "dset_a4b_flow_repair",
                compact, COMPACT_SHA, COMP, DOM, 3, "p", "Pa", false, "RETAINED_DERIVED",
                "Laminar Flow pressure p is present in retained sol20.");
            add(model, rows, "retained_liquid_inlet_pressure", "dset_liq_solution", "dset_a4b_flow_repair",
                compact, COMPACT_SHA, COMP, "m10a3_sel_bnd_electrolyte_inlet", 2, "p", "Pa", false,
                "RETAINED_DERIVED", "Retained sol20 inlet pressure.");
            add(model, rows, "retained_liquid_outlet_pressure", "dset_liq_solution", "dset_a4b_flow_repair",
                compact, COMPACT_SHA, COMP, "m10a3_sel_bnd_electrolyte_outlet", 2, "p", "Pa", false,
                "RETAINED_DERIVED", "Retained sol20 outlet pressure.");

            add(model, rows, "n2_gas_concentration", "dset_species_n2_gas", "dset93",
                compact, COMPACT_SHA, "comp_species_n2_real", "m10a3_sel_dom_n2_channel", 3,
                "cN2g", "mol/m^3", true, "RETAINED_DERIVED",
                "sol12 cache is cleared; component-specific dset93 routes the retained sol15 state and reproduces cN2g.");
            add(model, rows, "dissolved_n2", "dset_species_liq_n2", "dset_species_liq_n2",
                compact, COMPACT_SHA, COMP, DOM, 3, "cN2d", "mol/m^3", true,
                "RETAINED_CANONICAL", "Full sol15 retained; final stored time selected explicitly.");
            add(model, rows, "generic_donor", "dset_species_liq_n2", "dset_species_liq_n2",
                compact, COMPACT_SHA, COMP, DOM, 3, "cDonor", "mol/m^3", true,
                "RETAINED_CANONICAL", "GENERIC DONOR; calibration required; never relabeled ethanol.");

            add(model, rows, "electrolyte_potential", "dset_a4a_ohmic", "dset_a4a_ohmic",
                compact, COMPACT_SHA, COMP, DOM, 3, "cd.phil", "V", false,
                "RETAINED_CANONICAL", "sol19 retained; not full-cell voltage.");
            add(model, rows, "li_concentration", "dset_a4b_ionic_en", "dset_a4b_ionic_en",
                compact, COMPACT_SHA, COMP, DOM, 3, "cLi_a4b", "mol/m^3", true,
                "RETAINED_CANONICAL", "Final sol21 snapshot selected.");
            add(model, rows, "bf4_concentration", "dset_a4b_ionic_en", "dset_a4b_ionic_en",
                compact, COMPACT_SHA, COMP, DOM, 3, "cBF4_a4b", "mol/m^3", true,
                "RETAINED_CANONICAL", "Final sol21 snapshot selected.");
            add(model, rows, "ionic_current", "dset_a4b_ionic_en", "dset_a4b_ionic_en",
                compact, COMPACT_SHA, COMP, DOM, 3, "j_ion_a4b_mag", "A/m^2", true,
                "RETAINED_CANONICAL", "Species-flux identity; final sol21 snapshot.");
            add(model, rows, "cathode_current", "dset_a4b_ionic_en", "dset_a4b_ionic_en",
                compact, COMPACT_SHA, COMP, CATH, 2,
                LiNRR_M10A4_VisualizationCommon.plotExpression(model, "pg_a4c_cathode_current"),
                "A/m^2", true, "RETAINED_CANONICAL", "Accepted expression recovered from frozen PlotGroup.");

            for (int charge : new int[]{9, 45, 54, 99, 297}) {
                add(model, rows, "li_equivalent_" + charge + "C", "dset_a4b_ionic_en", "dset_a4b_ionic_en",
                    compact, COMPACT_SHA, COMP, CATH, 2,
                    LiNRR_M10A4_VisualizationCommon.plotExpression(model, "pg_a4d_h" + charge),
                    "m", true, "RETAINED_CANONICAL",
                    "LI-EQUIVALENT numerical upper bound f=1; not retained metallic Li.");
            }
            addPlotExpression(model, rows, compact, "n2_current_overlap", "pg_a4e_n2_current");
            addPlotExpression(model, rows, compact, "donor_current_overlap", "pg_a4e_donor_current");
            addPlotExpression(model, rows, compact, "four_field_colimitation", "pg_a4e_colim");
            addPlotExpression(model, rows, compact, "colimitation_robustness", "pg_a4e_robust");
            add(model, rows, "joule_diagnostic", "dset_a4a_ohmic", "dset_a4a_ohmic",
                compact, COMPACT_SHA, COMP, DOM, 3, "q_ohmic_a4a", "W/m^3", false,
                "RETAINED_DERIVED", "Electrical source diagnostic; no thermal feedback.");

            int[] plane = model.component(COMP).selection(CATH).entities(2);
            if (plane == null || plane.length == 0) throw new IllegalStateException("REACTION_PLANE_SELECTION_EMPTY");
            Path output = evidence.resolve("CANONICAL_FIELD_READABILITY.csv");
            LiNRR_M10A4_VisualizationCommon.writeCsv(output,
                "field,requested_legacy_dataset,effective_dataset,effective_source_model,source_sha256,component,selection,expression,unit,min,max,mean,routing_class,status,notes",
                rows);
            System.out.println("CANONICAL_FIELD_COUNT=" + rows.size());
            System.out.println("CANONICAL_FIELD_READABILITY=PASS");
            System.out.println("REACTION_PLANE_SELECTION=PASS");
            System.out.println("REACTION_PLANE_ENTITY_COUNT=" + plane.length);
            System.out.println("CORRECTED_CANONICAL_SOLVE_TRIGGERED=FALSE");
        } finally { ModelUtil.remove("M10A4CorrectedCanonical"); }
    }

    private static void addPlotExpression(Model model, List<String[]> rows, String compact,
                                          String field, String plot) {
        add(model, rows, field, "dset_a4b_ionic_en", "dset_a4b_ionic_en", compact, COMPACT_SHA,
            COMP, CATH, 2, LiNRR_M10A4_VisualizationCommon.plotExpression(model, plot), "1", true,
            "RETAINED_DERIVED", "Diagnostic only; accepted expression recovered from frozen PlotGroup.");
    }

    private static void add(Model model, List<String[]> rows, String field, String requested,
                            String effective, String source, String sha, String component,
                            String selection, int dimension, String expression, String unit,
                            boolean last, String routing, String notes) {
        double[] values = triple(model, effective, component, selection, dimension, expression, unit, last);
        rows.add(new String[]{field, requested, effective, source, sha, component, selection, expression,
            unit, f(values[0]), f(values[1]), f(values[2]), routing, "PASS", notes});
        System.out.println("CORRECTED_CANONICAL_FIELD|field=" + field + "|dataset=" + effective
            + "|routing=" + routing + "|status=PASS");
    }

    private static void addFallbackRows(List<String[]> rows, Path path) throws Exception {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            List<String> c = LiNRR_M10A4_VisualizationCommon.parseCsv(lines.get(i));
            if (c.size() < 17 || !"true".equals(c.get(13))) continue;
            if (!"N2_velocity".equals(c.get(0)) && !"N2_pressure".equals(c.get(0))) continue;
            rows.add(new String[]{c.get(0), "dset_n2_solution", c.get(4), c.get(1), c.get(2),
                c.get(6), c.get(7), c.get(8), c.get(9), c.get(10), c.get(11), c.get(12),
                "ACCEPTED_FALLBACK_SOURCE", "PASS", c.get(15) + "; " + c.get(16)});
        }
    }

    private static double[] triple(Model model, String dataset, String component, String selection,
                                   int dimension, String expression, String unit, boolean last) {
        int[] ids = model.component(component).selection(selection).entities(dimension);
        if (ids == null || ids.length == 0) throw new IllegalStateException("EMPTY_SELECTION " + selection);
        String suffix = dimension == 3 ? "Volume" : "Surface";
        return new double[]{scalar(model, "Min" + suffix, dataset, component, ids, dimension, expression, unit, last),
            scalar(model, "Max" + suffix, dataset, component, ids, dimension, expression, unit, last),
            scalar(model, "Av" + suffix, dataset, component, ids, dimension, expression, unit, last)};
    }

    private static double scalar(Model model, String type, String dataset, String component, int[] ids,
                                 int dimension, String expression, String unit, boolean last) {
        String tag = "corrected_eval_" + (++serial);
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
                throw new IllegalStateException("EMPTY_CORRECTED_CANONICAL " + fieldName(dataset, expression));
            double value = values[0][values[0].length - 1];
            if (!Double.isFinite(value)) throw new IllegalStateException("NONFINITE_CORRECTED_CANONICAL " + expression);
            return value;
        } finally { model.result().numerical().remove(tag); }
    }

    private static String fieldName(String dataset, String expression) { return dataset + ":" + expression; }
    private static String f(double value) { return String.format(Locale.ROOT, "%.17g", value); }
}
