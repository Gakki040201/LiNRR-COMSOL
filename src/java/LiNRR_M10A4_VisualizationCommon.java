import com.comsol.model.Model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared result-only helpers for the Paper V1 visualization repair. */
public final class LiNRR_M10A4_VisualizationCommon {
    static final String RUNTIME_CLASS = "LiNRR_M10A4_VisualizationRuntimeInputs";
    static final String REACTION_COMPONENT = "comp_species_liq_real";
    static final String REACTION_SELECTION = "m10a3_sel_bnd_electrolyte_gde_top";
    static final String IONIC_DATASET = "dset_a4b_ionic_en";
    static final String OHMIC_DATASET = "dset_a4a_ohmic";
    static final double INVARIANCE_TOLERANCE = 1e-12;
    private static int serial = 0;

    private LiNRR_M10A4_VisualizationCommon() {}

    public static final class FieldSpec {
        final String name;
        final String dataset;
        final String component;
        final String selection;
        final int dimension;
        final String expression;
        final String unit;

        public FieldSpec(String name, String dataset, String component, String selection,
                  int dimension, String expression, String unit) {
            this.name = name;
            this.dataset = dataset;
            this.component = component;
            this.selection = selection;
            this.dimension = dimension;
            this.expression = expression;
            this.unit = unit;
        }
    }

    public static final class FieldValue {
        final FieldSpec spec;
        final int selectionCount;
        final double minimum;
        final double maximum;
        final double mean;

        public FieldValue(FieldSpec spec, int selectionCount, double minimum, double maximum, double mean) {
            this.spec = spec;
            this.selectionCount = selectionCount;
            this.minimum = minimum;
            this.maximum = maximum;
            this.mean = mean;
        }
    }

    static String runtime(String name) {
        try {
            return ((String) Class.forName(RUNTIME_CLASS).getField(name).get(null)).trim();
        } catch (Exception exception) {
            throw new IllegalStateException("RUNTIME_INPUT_MISSING: " + name, exception);
        }
    }

    static List<FieldSpec> canonicalSpecs(Model model) {
        List<FieldSpec> specs = new ArrayList<FieldSpec>();
        specs.add(new FieldSpec("liquid_velocity", "dset_liq_solution", "comp_electrolyte_flow",
            "sel_dom_electrolyte_fluid", 3, "sqrt(u^2+v^2+w^2)", "m/s"));
        specs.add(new FieldSpec("liquid_inlet_pressure", "dset_liq_solution", "comp_electrolyte_flow",
            "sel_bnd_electrolyte_inlet", 2, "p", "Pa"));
        specs.add(new FieldSpec("liquid_outlet_pressure", "dset_liq_solution", "comp_electrolyte_flow",
            "sel_bnd_electrolyte_outlet", 2, "p", "Pa"));
        specs.add(new FieldSpec("n2_velocity", "dset_n2_solution", "comp_n2_flow",
            "sel_dom_n2_channel", 3, "sqrt(u2^2+v2^2+w2^2)", "m/s"));
        specs.add(new FieldSpec("n2_inlet_pressure", "dset_n2_solution", "comp_n2_flow",
            "sel_bnd_n2_inlet", 2, "p2", "Pa"));
        specs.add(new FieldSpec("n2_outlet_pressure", "dset_n2_solution", "comp_n2_flow",
            "sel_bnd_n2_outlet", 2, "p2", "Pa"));
        specs.add(new FieldSpec("n2_gas", "dset_species_n2_gas", "comp_species_n2_real",
            "m10a3_sel_dom_n2_channel", 3, "cN2g", "mol/m^3"));
        specs.add(new FieldSpec("dissolved_n2", "dset_species_liq_n2", REACTION_COMPONENT,
            "m10a3_sel_dom_electrolyte_fluid", 3, "cN2d", "mol/m^3"));
        specs.add(new FieldSpec("generic_donor", "dset_species_liq_n2", REACTION_COMPONENT,
            "m10a3_sel_dom_electrolyte_fluid", 3, "cDonor", "mol/m^3"));
        specs.add(new FieldSpec("electrolyte_potential", OHMIC_DATASET, REACTION_COMPONENT,
            "m10a3_sel_dom_electrolyte_fluid", 3, "cd.phil", "V"));
        specs.add(new FieldSpec("li_concentration", IONIC_DATASET, REACTION_COMPONENT,
            "m10a3_sel_dom_electrolyte_fluid", 3, "cLi_a4b", "mol/m^3"));
        specs.add(new FieldSpec("bf4_concentration", IONIC_DATASET, REACTION_COMPONENT,
            "m10a3_sel_dom_electrolyte_fluid", 3, "cBF4_a4b", "mol/m^3"));
        specs.add(new FieldSpec("ionic_current", IONIC_DATASET, REACTION_COMPONENT,
            "m10a3_sel_dom_electrolyte_fluid", 3, "j_ion_a4b_mag", "A/m^2"));
        specs.add(new FieldSpec("cathode_current", IONIC_DATASET, REACTION_COMPONENT,
            REACTION_SELECTION, 2, plotExpression(model, "pg_a4c_cathode_current"), "A/m^2"));
        for (int charge : new int[]{9, 45, 54, 99, 297}) {
            specs.add(new FieldSpec("li_equivalent_" + charge + "C", IONIC_DATASET,
                REACTION_COMPONENT, REACTION_SELECTION, 2,
                plotExpression(model, "pg_a4d_h" + charge), "m"));
        }
        specs.add(new FieldSpec("n2_current_overlap", IONIC_DATASET, REACTION_COMPONENT,
            REACTION_SELECTION, 2, plotExpression(model, "pg_a4e_n2_current"), "1"));
        specs.add(new FieldSpec("donor_current_overlap", IONIC_DATASET, REACTION_COMPONENT,
            REACTION_SELECTION, 2, plotExpression(model, "pg_a4e_donor_current"), "1"));
        specs.add(new FieldSpec("four_field_colimitation", IONIC_DATASET, REACTION_COMPONENT,
            REACTION_SELECTION, 2, plotExpression(model, "pg_a4e_colim"), "1"));
        specs.add(new FieldSpec("colimitation_robustness", IONIC_DATASET, REACTION_COMPONENT,
            REACTION_SELECTION, 2, plotExpression(model, "pg_a4e_robust"), "1"));
        specs.add(new FieldSpec("joule_source", OHMIC_DATASET, REACTION_COMPONENT,
            "m10a3_sel_dom_electrolyte_fluid", 3, "q_ohmic_a4a", "W/m^3"));
        return specs;
    }

    static String plotExpression(Model model, String plotTag) {
        if (!model.result().hasTag(plotTag)) {
            throw new IllegalStateException("ACCEPTED_PLOT_MISSING: " + plotTag);
        }
        String[] childTags = model.result(plotTag).feature().tags();
        if (childTags.length == 0) {
            throw new IllegalStateException("ACCEPTED_PLOT_HAS_NO_FEATURE: " + plotTag);
        }
        for (String child : childTags) {
            try {
                String expression = model.result(plotTag).feature(child).getString("expr");
                if (expression != null && !expression.trim().isEmpty()) return expression.trim();
            } catch (Throwable ignored) {}
            try {
                String[] expressions = model.result(plotTag).feature(child).getStringArray("expr");
                if (expressions != null && expressions.length > 0 && !expressions[0].trim().isEmpty()) {
                    return expressions[0].trim();
                }
            } catch (Throwable ignored) {}
        }
        throw new IllegalStateException("ACCEPTED_PLOT_EXPRESSION_UNREADABLE: " + plotTag);
    }

    static FieldValue evaluate(Model model, FieldSpec spec) {
        int[] entities = model.component(spec.component).selection(spec.selection).entities(spec.dimension);
        if (entities == null || entities.length == 0) {
            throw new IllegalStateException("EMPTY_CANONICAL_SELECTION: " + spec.component + "/" + spec.selection);
        }
        String suffix = spec.dimension == 3 ? "Volume" : spec.dimension == 2 ? "Surface" : "Line";
        double minimum = eval(model, "Min" + suffix, spec, entities);
        double maximum = eval(model, "Max" + suffix, spec, entities);
        double mean = eval(model, "Av" + suffix, spec, entities);
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || !Double.isFinite(mean)) {
            throw new IllegalStateException("NONFINITE_CANONICAL_FIELD: " + spec.name);
        }
        return new FieldValue(spec, entities.length, minimum, maximum, mean);
    }

    private static double eval(Model model, String type, FieldSpec spec, int[] entities) {
        String tag = "viz_eval_" + (++serial);
        model.result().numerical().create(tag, type);
        try {
            model.result().numerical(tag).set("data", spec.dataset);
            if ("dset_species_liq_n2".equals(spec.dataset)) {
                try {
                    String[] levels = model.result().numerical(tag).getStringArray("looplevelinput");
                    for (int i = 0; i < levels.length; i++) levels[i] = "last";
                    model.result().numerical(tag).set("looplevelinput", levels);
                } catch (Throwable ignored) {}
            }
            if ("comp_electrolyte_flow".equals(spec.component) || "comp_n2_flow".equals(spec.component)) {
                // The accepted M10A1 result-only reload binds these original-component
                // selections by name. Raw entity rebinding can be empty for their
                // solution dataset even when the named selection is valid.
                model.result().numerical(tag).selection().named(spec.selection);
            } else {
                String[] geometries = model.component(spec.component).geom().tags();
                if (geometries.length == 0) throw new IllegalStateException("COMPONENT_GEOMETRY_MISSING: " + spec.component);
                model.result().numerical(tag).selection().geom(geometries[0], spec.dimension);
                model.result().numerical(tag).selection().set(entities);
            }
            model.result().numerical(tag).set("expr", new String[]{spec.expression});
            model.result().numerical(tag).set("unit", new String[]{spec.unit});
            double[][] values = model.result().numerical(tag).getReal();
            if (values == null || values.length == 0 || values[0].length == 0) {
                throw new IllegalStateException("EMPTY_CANONICAL_EVALUATION: " + spec.name);
            }
            return values[0][values[0].length - 1];
        } finally {
            model.result().numerical().remove(tag);
        }
    }

    static List<FieldValue> evaluateAll(Model model) {
        List<FieldValue> values = new ArrayList<FieldValue>();
        for (FieldSpec spec : canonicalSpecs(model)) values.add(evaluate(model, spec));
        return values;
    }

    static void writeCanonical(Path path, List<FieldValue> values) throws Exception {
        List<String[]> rows = new ArrayList<String[]>();
        for (FieldValue value : values) {
            FieldSpec spec = value.spec;
            rows.add(new String[]{spec.name, spec.dataset, spec.component, spec.selection,
                Integer.toString(spec.dimension), Integer.toString(value.selectionCount), spec.expression,
                spec.unit, format(value.minimum), format(value.maximum), format(value.mean), "PASS", "final stored solution level"});
        }
        writeCsv(path,
            "field,dataset,component,selection,dimension,selection_count,expression,unit,finite_min,finite_max,finite_mean,status,notes",
            rows);
    }

    static void writeCsv(Path path, String header, List<String[]> rows) throws Exception {
        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(header);
            writer.newLine();
            for (String[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) writer.write(',');
                    writer.write(csv(row[i]));
                }
                writer.newLine();
            }
        }
    }

    static Map<String, double[]> readCanonicalSignature(Path path) throws Exception {
        Map<String, double[]> result = new LinkedHashMap<String, double[]>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            if (line == null) throw new IllegalStateException("CANONICAL_SIGNATURE_EMPTY: " + path);
            while ((line = reader.readLine()) != null) {
                List<String> columns = parseCsv(line);
                if (columns.size() < 12 || !"PASS".equals(columns.get(11))) continue;
                result.put(columns.get(0), new double[]{Double.parseDouble(columns.get(8)),
                    Double.parseDouble(columns.get(9)), Double.parseDouble(columns.get(10))});
            }
        }
        return result;
    }

    static List<String> parseCsv(String line) {
        List<String> values = new ArrayList<String>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else value.append(c);
        }
        values.add(value.toString());
        return values;
    }

    static String format(double value) {
        return String.format(Locale.ROOT, "%.17g", value);
    }

    static double relative(double before, double after) {
        return Math.abs(before - after) /
            Math.max(Math.max(Math.abs(before), Math.abs(after)), 1e-300);
    }

    static String clean(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ').replace('\n', ' ').replace('|', '/').trim();
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
