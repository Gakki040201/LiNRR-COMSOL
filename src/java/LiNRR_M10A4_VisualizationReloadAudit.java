import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Fresh-load Result-only runtime and stored-field invariance audit for VISUAL_REVIEW.mph. */
public final class LiNRR_M10A4_VisualizationReloadAudit {
    private static int serial = 0;
    private static final String SOURCE_SHA256 =
        "FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B";
    private LiNRR_M10A4_VisualizationReloadAudit() {}

    public static void main(String[] args) throws Exception {
        Path modelPath = Paths.get(LiNRR_M10A4_VisualizationRuntimeInputs.OUTPUT_MPH);
        Path evidence = Paths.get(LiNRR_M10A4_VisualizationRuntimeInputs.EVIDENCE_DIR);
        if (!Files.isRegularFile(modelPath)) throw new IllegalStateException("VISUAL_REVIEW_MPH_MISSING");
        Model model = ModelUtil.load("M10A4VisualizationFreshReload", modelPath.toString());
        try {
            List<String[]> plotRows = new ArrayList<String[]>();
            int count = 0;
            for (String tag : model.result().tags()) {
                if (!tag.startsWith("viz")) continue;
                model.result(tag).run();
                boolean warning = model.result(tag).hasWarning();
                if (warning) throw new IllegalStateException("RELOAD_PLOT_WARNING " + tag);
                plotRows.add(new String[]{tag, model.result(tag).label(), "true", "false", "PASS",
                    "Fresh independent load; compact-M10A4 visualization field"});
                count++;
            }
            if (count != 29) throw new IllegalStateException("RELOAD_COMPACT_PLOT_COUNT=" + count);
            LiNRR_M10A4_VisualizationCommon.writeCsv(evidence.resolve("VISUALIZATION_RELOAD_AUDIT.csv"),
                "plot_tag,label,plot_run_ok,warning,status,notes", plotRows);

            List<String[]> invariantRows = auditInvariance(model, evidence.resolve("CANONICAL_FIELD_READABILITY.csv"));
            LiNRR_M10A4_VisualizationCommon.writeCsv(evidence.resolve("VISUALIZATION_FIELD_INVARIANCE.csv"),
                "field,dataset,component,selection,expression,before_min,after_min,before_max,after_max,before_mean,after_mean,max_relative_error,status,notes",
                invariantRows);
            int rp = model.component("comp_species_liq_real")
                .selection("m10a3_sel_bnd_electrolyte_gde_top").entities(2).length;
            if (rp <= 0) throw new IllegalStateException("RELOAD_REACTION_PLANE_EMPTY");
            System.out.println("VIZ_COMPACT_RELOAD_PLOT_COUNT=" + count);
            System.out.println("VIZ_MAIN_PLOT_RUNTIME_PASS=PASS");
            System.out.println("VIZ_MAIN_PLOT_WARNINGS=0");
            System.out.println("VISUALIZATION_FIELD_INVARIANCE=PASS");
            System.out.println("PAPER_V1_VISUALIZATION_INDEPENDENT_RELOAD=PASS");
            System.out.println("REACTION_PLANE_SELECTION=PASS");
            System.out.println("VISUALIZATION_RELOAD_SOLVE_TRIGGERED=FALSE");
        } finally { ModelUtil.remove("M10A4VisualizationFreshReload"); }
    }

    private static List<String[]> auditInvariance(Model model, Path canonical) throws Exception {
        List<String[]> rows = new ArrayList<String[]>();
        List<String> lines = Files.readAllLines(canonical, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            List<String> c = LiNRR_M10A4_VisualizationCommon.parseCsv(lines.get(i));
            if (c.size() < 15 || !SOURCE_SHA256.equals(c.get(4))) continue;
            String field = c.get(0), dataset = c.get(2), component = c.get(5), selection = c.get(6);
            String expression = c.get(7), unit = c.get(8);
            double beforeMin = Double.parseDouble(c.get(9));
            double beforeMax = Double.parseDouble(c.get(10));
            double beforeMean = Double.parseDouble(c.get(11));
            int dimension = dimension(model, component, selection);
            double[] after = triple(model, dataset, component, selection, dimension, expression, unit, needsLast(dataset));
            double error = Math.max(relative(beforeMin, after[0]),
                Math.max(relative(beforeMax, after[1]), relative(beforeMean, after[2])));
            if (error > 1e-12) throw new IllegalStateException("FIELD_INVARIANCE_FAILED " + field + " rel=" + error);
            rows.add(new String[]{field, dataset, component, selection, expression, f(beforeMin), f(after[0]),
                f(beforeMax), f(after[1]), f(beforeMean), f(after[2]), f(error), "PASS",
                "Stored scientific result unchanged after visualization-only save/reload"});
        }
        if (rows.size() != 22) throw new IllegalStateException("FIELD_INVARIANCE_COMPACT_COUNT=" + rows.size());
        return rows;
    }

    private static int dimension(Model model, String component, String selection) {
        if (model.component(component).selection(selection).entities(3).length > 0) return 3;
        if (model.component(component).selection(selection).entities(2).length > 0) return 2;
        throw new IllegalStateException("INVARIANCE_SELECTION_EMPTY " + component + "/" + selection);
    }

    private static boolean needsLast(String dataset) {
        return "dset_species_liq_n2".equals(dataset) || "dset_a4b_ionic_en".equals(dataset)
            || "dset93".equals(dataset);
    }

    private static double[] triple(Model model, String dataset, String component, String selection,
                                   int dimension, String expression, String unit, boolean last) {
        int[] ids = model.component(component).selection(selection).entities(dimension);
        String suffix = dimension == 3 ? "Volume" : "Surface";
        return new double[]{scalar(model, "Min" + suffix, dataset, component, ids, dimension, expression, unit, last),
            scalar(model, "Max" + suffix, dataset, component, ids, dimension, expression, unit, last),
            scalar(model, "Av" + suffix, dataset, component, ids, dimension, expression, unit, last)};
    }

    private static double scalar(Model model, String type, String dataset, String component, int[] ids,
                                 int dimension, String expression, String unit, boolean last) {
        String tag = "reload_eval_" + (++serial);
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
                throw new IllegalStateException("RELOAD_EMPTY_FIELD " + fieldKey(dataset, expression));
            double value = values[0][values[0].length - 1];
            if (!Double.isFinite(value)) throw new IllegalStateException("RELOAD_NONFINITE_FIELD " + expression);
            return value;
        } finally { model.result().numerical().remove(tag); }
    }

    private static double relative(double a, double b) {
        return Math.abs(a - b) / Math.max(Math.max(Math.abs(a), Math.abs(b)), 1e-300);
    }
    private static String fieldKey(String dataset, String expression) { return dataset + ":" + expression; }
    private static String f(double value) { return String.format(Locale.ROOT, "%.17g", value); }
}
