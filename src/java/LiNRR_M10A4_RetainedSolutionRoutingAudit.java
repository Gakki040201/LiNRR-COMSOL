import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Result-only storage/routing audit for the compact Paper V1 M10A4 artifact.
 * Never runs a study, solver, or mesh and never saves the loaded model.
 */
public final class LiNRR_M10A4_RetainedSolutionRoutingAudit {
    private static int serial = 0;
    private static final String COMP_LIQ = "comp_species_liq_real";
    private static final String GEOM_LIQ = "geom_electrolyte_fluid1";
    private static final String DOM_LIQ = "m10a3_sel_dom_electrolyte_fluid";
    private static final String IN_LIQ = "m10a3_sel_bnd_electrolyte_inlet";
    private static final String OUT_LIQ = "m10a3_sel_bnd_electrolyte_outlet";
    private static final String COMPACT_SHA = "FADEEA4D8ADF9E472B4B855B05E39C22EF418214C0F62FB0C1977ACA36DA667B";

    private LiNRR_M10A4_RetainedSolutionRoutingAudit() {}

    public static void main(String[] args) throws Exception {
        Path evidence = Paths.get(LiNRR_M10A4_VisualizationCommon.runtime("EVIDENCE_DIR"));
        Files.createDirectories(evidence);
        Model model = ModelUtil.load("M10A4RetainedRouting",
            LiNRR_M10A4_VisualizationCommon.runtime("INPUT_MPH"));
        try {
            List<String[]> rows = new ArrayList<String[]>();
            Set<String> explicitlyTested = new HashSet<String>();

            test(model, rows, explicitlyTested, "dset_liq_solution", "LEGACY_REAL_LIQUID_FLOW",
                "comp_electrolyte_flow", "sel_dom_electrolyte_fluid", 3,
                "sqrt(u^2+v^2+w^2)", "m/s", false,
                "LEGACY_CACHE_CLEARED", "dset_a4b_flow_repair",
                "Previous audit observed empty Derived Values; retained here as routing history.");
            test(model, rows, explicitlyTested, "dset_n2_solution", "LEGACY_REAL_N2_FLOW",
                "comp_n2_flow", "sel_dom_n2_channel", 3,
                "sqrt(u2^2+v2^2+w2^2)", "m/s", false,
                "LEGACY_CACHE_CLEARED", "", "Do not infer usability from the surviving dataset tag.");
            test(model, rows, explicitlyTested, "dset_n2_solution", "LEGACY_REAL_N2_PRESSURE",
                "comp_n2_flow", "sel_dom_n2_channel", 3, "p2", "Pa", false,
                "LEGACY_CACHE_CLEARED", "", "Pressure is tested independently from velocity.");
            test(model, rows, explicitlyTested, "dset_species_n2_gas", "REAL_N2_GAS_SPECIES",
                "comp_species_n2_real", "m10a3_sel_dom_n2_channel", 3,
                "cN2g", "mol/m^3", false, "LEGACY_CACHE_CLEARED", "",
                "Concentration dataset is never relabeled as an N2 velocity source.");
            test(model, rows, explicitlyTested, "dset_species_liq_flow", "M10A3_COLLOCATED_LIQUID_FLOW",
                COMP_LIQ, DOM_LIQ, 3, "sqrt(u^2+v^2+w^2)", "m/s", false,
                "LEGACY_CACHE_CLEARED", "dset_a4b_flow_repair", "Historical collocated-flow route.");
            test(model, rows, explicitlyTested, "dset_species_liq_n2", "DISSOLVED_N2_RETAINED_FINAL",
                COMP_LIQ, DOM_LIQ, 3, "cN2d", "mol/m^3", true,
                "RETAINED_CANONICAL", "", "sol15 is retained in full; use final stored time for presentation.");
            test(model, rows, explicitlyTested, "dset_species_liq_n2", "GENERIC_DONOR_RETAINED_FINAL",
                COMP_LIQ, DOM_LIQ, 3, "cDonor", "mol/m^3", true,
                "RETAINED_CANONICAL", "", "Generic donor; calibration required; never label ethanol.");
            test(model, rows, explicitlyTested, "dset_a4a_ohmic", "A4A_ELECTROLYTE_POTENTIAL",
                COMP_LIQ, DOM_LIQ, 3, "cd.phil", "V", false,
                "RETAINED_CANONICAL", "", "sol19 stationary state retained.");
            test(model, rows, explicitlyTested, "dset_a4b_flow_repair", "A4B_REAL_LIQUID_VELOCITY_RECOVERY",
                COMP_LIQ, DOM_LIQ, 3, "sqrt(u^2+v^2+w^2)", "m/s", false,
                "RETAINED_CANONICAL", "dset_liq_solution", "Accepted A4B real-flow recovery; sol20.");
            test(model, rows, explicitlyTested, "dset_a4b_flow_repair", "A4B_REAL_LIQUID_PRESSURE_RECOVERY",
                COMP_LIQ, DOM_LIQ, 3, "p", "Pa", false,
                "RETAINED_DERIVED", "dset_liq_solution", "Pressure expression taken from Laminar Flow dependent variable p.");
            test(model, rows, explicitlyTested, "dset_a4b_ionic_en", "A4B_LI_CONCENTRATION_FINAL",
                COMP_LIQ, DOM_LIQ, 3, "cLi_a4b", "mol/m^3", true,
                "RETAINED_CANONICAL", "", "sol21 was compacted to its accepted final snapshot.");

            addFlowBoundaryTests(model, rows, explicitlyTested);
            testRetainedN2Candidates(model, rows, explicitlyTested);
            inspectAllRetainedRoutes(model, rows, explicitlyTested);

            Path output = evidence.resolve("RETAINED_SOLUTION_ROUTING.csv");
            LiNRR_M10A4_VisualizationCommon.writeCsv(output,
                "dataset_tag,dataset_label,dataset_type,configured_solution_tag,configured_component,candidate_scientific_role,solution_present,stored_solution_state_detected,selection,expression,derived_value_nonempty,finite,min,max,mean,classification,replacement_for_legacy_dataset,notes",
                rows);

            boolean liquid = rowPass(rows, "dset_a4b_flow_repair", "A4B_REAL_LIQUID_VELOCITY_RECOVERY");
            boolean liquidPressure = rowPass(rows, "dset_a4b_flow_repair", "A4B_REAL_LIQUID_PRESSURE_RECOVERY");
            boolean legacyLiquid = rowPass(rows, "dset_liq_solution", "LEGACY_REAL_LIQUID_FLOW");
            boolean legacyN2 = rowPass(rows, "dset_n2_solution", "LEGACY_REAL_N2_FLOW");
            String retainedN2Dataset = firstPassingRolePrefix(rows, "RETAINED_N2_VELOCITY_CANDIDATE_");
            if (!liquid) throw new IllegalStateException("RETAINED_LIQUID_FLOW_UNREADABLE: dset_a4b_flow_repair");
            System.out.println("LEGACY_LIQ_DATASET_READABILITY=" + (legacyLiquid ? "READABLE" : "EMPTY_CACHE_CLEARED"));
            System.out.println("RETAINED_LIQ_DATASET=dset_a4b_flow_repair");
            System.out.println("RETAINED_LIQ_DATASET_READABILITY=PASS");
            System.out.println("RETAINED_LIQ_PRESSURE_READABILITY=" + (liquidPressure ? "PASS" : "UNAVAILABLE"));
            System.out.println("LEGACY_N2_DATASET_READABILITY=" + (legacyN2 ? "PASS" : "EMPTY_CACHE_CLEARED"));
            System.out.println("LIQUID_FLOW_ROUTING_CLASS=RETAINED_CANONICAL_REAL_LIQUID_FLOW");
            System.out.println("RETAINED_N2_FLOW_SOURCE=" + (legacyN2 ? "dset_n2_solution" : retainedN2Dataset.isEmpty() ? "NONE_IN_COMPACT" : retainedN2Dataset));
            System.out.println("RETAINED_N2_FLOW_READABILITY=" + (legacyN2 || !retainedN2Dataset.isEmpty() ? "PASS" : "NOT_RETAINED_IN_COMPACT"));
            System.out.println("N2_FLOW_ROUTING_CLASS=" + (legacyN2 || !retainedN2Dataset.isEmpty() ? "RETAINED_DERIVED" : "FALLBACK_AUDIT_REQUIRED"));
            System.out.println("RETAINED_SOLUTION_ROUTING_AUDIT=PASS");
            System.out.println("RETAINED_SOLUTION_ROUTING_SOLVE_TRIGGERED=FALSE");
            System.out.println("COMPACT_SOURCE_SHA256=" + COMPACT_SHA);
        } finally {
            ModelUtil.remove("M10A4RetainedRouting");
        }
    }

    private static void addFlowBoundaryTests(Model model, List<String[]> rows, Set<String> tested) {
        test(model, rows, tested, "dset_a4b_flow_repair", "A4B_LIQUID_INLET_PRESSURE",
            COMP_LIQ, IN_LIQ, 2, "p", "Pa", false, "RETAINED_DERIVED", "dset_liq_solution",
            "Retained sol20 inlet average pressure.");
        test(model, rows, tested, "dset_a4b_flow_repair", "A4B_LIQUID_OUTLET_PRESSURE",
            COMP_LIQ, OUT_LIQ, 2, "p", "Pa", false, "RETAINED_DERIVED", "dset_liq_solution",
            "Retained sol20 outlet average pressure.");
        test(model, rows, tested, "dset_a4b_flow_repair", "A4B_LIQUID_INLET_NORMAL_FLOW",
            COMP_LIQ, IN_LIQ, 2, "u*nx+v*ny+w*nz", "m/s", false, "RETAINED_DERIVED",
            "dset_liq_solution", "Surface mean supplements the separately integrated flow balance.");
        double qIn = integral(model, "dset_a4b_flow_repair", COMP_LIQ, IN_LIQ,
            "u*nx+v*ny+w*nz", "m^3/s");
        double qOut = integral(model, "dset_a4b_flow_repair", COMP_LIQ, OUT_LIQ,
            "u*nx+v*ny+w*nz", "m^3/s");
        double relative = Math.abs(qIn + qOut) / Math.max(Math.abs(qIn), 1e-300);
        if (!Double.isFinite(qIn) || !Double.isFinite(qOut) || relative > 1e-6) {
            throw new IllegalStateException("RETAINED_FLOW_BALANCE_FAIL qin=" + qIn + " qout=" + qOut + " relative=" + relative);
        }
        String dataset = "dset_a4b_flow_repair";
        rows.add(base(model, dataset, "A4B_LIQUID_VOLUMETRIC_FLOW_CONSISTENCY",
            COMP_LIQ, IN_LIQ + ";" + OUT_LIQ, "integral(u*n_x+v*n_y+w*n_z)", true, true,
            Math.min(qIn, qOut), Math.max(qIn, qOut), relative,
            "RETAINED_DERIVED", "dset_liq_solution",
            "qin=" + f(qIn) + "; qout=" + f(qOut) + "; relative_closure=" + f(relative)));
        tested.add(dataset);
    }

    private static void testRetainedN2Candidates(Model model, List<String[]> rows, Set<String> tested) {
        String[][] candidates = {
            {"dset85", "sol15"}, {"dset135", "sol19"}, {"dset148", "sol20"}, {"dset161", "sol21"}
        };
        for (String[] candidate : candidates) {
            String dataset = candidate[0];
            test(model, rows, tested, dataset, "RETAINED_N2_VELOCITY_CANDIDATE_" + candidate[1],
                "comp_n2_flow", "sel_dom_n2_channel", 3, "sqrt(u2^2+v2^2+w2^2)", "m/s",
                "sol21".equals(candidate[1]), "UNAVAILABLE_WITHOUT_SOLVE", "",
                "Actual component-specific dataset connected to retained " + candidate[1] + "; tested, not inferred.");
            test(model, rows, tested, dataset, "RETAINED_N2_PRESSURE_CANDIDATE_" + candidate[1],
                "comp_n2_flow", "sel_dom_n2_channel", 3, "p2", "Pa",
                "sol21".equals(candidate[1]), "UNAVAILABLE_WITHOUT_SOLVE", "",
                "Pressure tested independently on the same retained solution route.");
        }
        String[][] species = {
            {"dset93", "sol15"}, {"dset143", "sol19"}, {"dset156", "sol20"}, {"dset169", "sol21"}
        };
        for (String[] candidate : species) {
            test(model, rows, tested, candidate[0], "RETAINED_CN2G_CANDIDATE_" + candidate[1],
                "comp_species_n2_real", "m10a3_sel_dom_n2_channel", 3, "cN2g", "mol/m^3",
                "sol21".equals(candidate[1]), "UNAVAILABLE_WITHOUT_SOLVE", "",
                "Actual species-component dataset connected to retained " + candidate[1] + "; concentration only.");
        }
    }

    private static void inspectAllRetainedRoutes(Model model, List<String[]> rows, Set<String> tested) {
        Set<String> retained = new HashSet<String>();
        for (String solution : new String[]{"sol15", "sol19", "sol20", "sol21"}) retained.add(solution);
        for (String dataset : model.result().dataset().tags()) {
            if (!"Solution".equals(model.result().dataset(dataset).getType())) continue;
            String solution = property(model, dataset, "solution");
            if (!retained.contains(solution) || tested.contains(dataset)) continue;
            boolean present = model.sol().hasTag(solution);
            boolean stored = stored(model, solution);
            rows.add(new String[]{dataset, model.result().dataset(dataset).label(),
                model.result().dataset(dataset).getType(), solution, property(model, dataset, "comp"),
                "RETAINED_SOLUTION_ROUTE_INSPECTION", Boolean.toString(present), Boolean.toString(stored),
                "", "", "NOT_TESTED", "NOT_TESTED", "", "", "",
                stored ? "RETAINED_DERIVED" : "UNAVAILABLE_WITHOUT_SOLVE", "",
                "Loaded MPH property inspection; configured route targets " + solution + "."});
        }
    }

    private static void test(Model model, List<String[]> rows, Set<String> tested, String dataset,
                             String role, String component, String selection, int dimension,
                             String expression, String unit, boolean finalLevel,
                             String emptyClassification, String replacement, String notes) {
        tested.add(dataset);
        double[] value = evaluate(model, dataset, component, selection, dimension, expression, unit, finalLevel);
        boolean nonempty = value != null;
        boolean finite = nonempty && Double.isFinite(value[0]) && Double.isFinite(value[1]) && Double.isFinite(value[2]);
        boolean scientificUsable = finite;
        if (role.startsWith("RETAINED_N2_VELOCITY_CANDIDATE_")
            && (!scientificUsable || !(value[1] > 0.0))) scientificUsable = false;
        if (role.startsWith("RETAINED_N2_PRESSURE_CANDIDATE_")
            && (!scientificUsable || !(value[1] > value[0]))) scientificUsable = false;
        String classification = scientificUsable ? (role.contains("RECOVERY") || role.contains("FINAL") || role.contains("A4")
            ? "RETAINED_CANONICAL" : "RETAINED_DERIVED") : emptyClassification;
        rows.add(base(model, dataset, role, component, selection, expression, nonempty, finite,
            finite ? value[0] : Double.NaN, finite ? value[1] : Double.NaN, finite ? value[2] : Double.NaN,
            classification, replacement, notes));
        System.out.println("ROUTING_TEST|dataset=" + dataset + "|role=" + role + "|solution="
            + property(model, dataset, "solution") + "|nonempty=" + nonempty + "|finite=" + finite
            + "|scientific_usable=" + scientificUsable + "|classification=" + classification);
    }

    private static String[] base(Model model, String dataset, String role, String component,
                                 String selection, String expression, boolean nonempty, boolean finite,
                                 double min, double max, double mean, String classification,
                                 String replacement, String notes) {
        String solution = property(model, dataset, "solution");
        boolean present = !solution.isEmpty() && model.sol().hasTag(solution);
        return new String[]{dataset, model.result().dataset().hasTag(dataset) ? model.result().dataset(dataset).label() : "",
            model.result().dataset().hasTag(dataset) ? model.result().dataset(dataset).getType() : "",
            solution, property(model, dataset, "comp"), role, Boolean.toString(present),
            Boolean.toString(present && stored(model, solution)), selection, expression,
            Boolean.toString(nonempty), Boolean.toString(finite), finite ? f(min) : "",
            finite ? f(max) : "", finite ? f(mean) : "", classification, replacement, notes};
    }

    private static double[] evaluate(Model model, String dataset, String component, String selection,
                                     int dimension, String expression, String unit, boolean finalLevel) {
        if (!model.result().dataset().hasTag(dataset)) return null;
        int[] entities;
        try { entities = model.component(component).selection(selection).entities(dimension); }
        catch (Throwable failure) { return null; }
        if (entities == null || entities.length == 0) return null;
        String suffix = dimension == 3 ? "Volume" : "Surface";
        try {
            double lo = scalar(model, "Min" + suffix, dataset, component, entities, dimension, expression, unit, finalLevel);
            double hi = scalar(model, "Max" + suffix, dataset, component, entities, dimension, expression, unit, finalLevel);
            double av = scalar(model, "Av" + suffix, dataset, component, entities, dimension, expression, unit, finalLevel);
            return new double[]{lo, hi, av};
        } catch (Throwable failure) {
            System.out.println("ROUTING_EVAL_EMPTY|dataset=" + dataset + "|expression=" + expression
                + "|class=" + failure.getClass().getName() + "|message=" + clean(failure.getMessage()));
            return null;
        }
    }

    private static double integral(Model model, String dataset, String component, String selection,
                                   String expression, String unit) {
        int[] entities = model.component(component).selection(selection).entities(2);
        return scalar(model, "IntSurface", dataset, component, entities, 2, expression, unit, false);
    }

    private static double scalar(Model model, String type, String dataset, String component,
                                 int[] entities, int dimension, String expression, String unit,
                                 boolean finalLevel) {
        String tag = "routing_eval_" + (++serial);
        model.result().numerical().create(tag, type);
        try {
            model.result().numerical(tag).set("data", dataset);
            if (finalLevel) {
                String[] levels = model.result().numerical(tag).getStringArray("looplevelinput");
                for (int i = 0; i < levels.length; i++) levels[i] = "last";
                model.result().numerical(tag).set("looplevelinput", levels);
            }
            String[] geometries = model.component(component).geom().tags();
            if (geometries.length == 0) throw new IllegalStateException("NO_GEOMETRY " + component);
            model.result().numerical(tag).selection().geom(geometries[0], dimension);
            model.result().numerical(tag).selection().set(entities);
            model.result().numerical(tag).set("expr", new String[]{expression});
            model.result().numerical(tag).set("unit", new String[]{unit});
            if (type.startsWith("Int")) {
                model.result().numerical(tag).set("intorderactive", true);
                model.result().numerical(tag).set("intorder", 8);
            }
            double[][] values = model.result().numerical(tag).getReal();
            if (values == null || values.length == 0 || values[0].length == 0)
                throw new IllegalStateException("EMPTY_RESULT");
            return values[0][values[0].length - 1];
        } finally {
            model.result().numerical().remove(tag);
        }
    }

    private static String property(Model model, String dataset, String name) {
        try {
            if (!model.result().dataset().hasTag(dataset)) return "";
            String value = model.result().dataset(dataset).getString(name);
            return value == null ? "" : value.trim();
        } catch (Throwable ignored) { return ""; }
    }

    private static boolean stored(Model model, String solution) {
        if (solution == null || solution.isEmpty() || !model.sol().hasTag(solution)) return false;
        try {
            double[] values = model.sol(solution).getPVals();
            if (values != null && values.length > 0) return true;
        } catch (Throwable ignored) {}
        try {
            double[] values = model.sol(solution).getU(1);
            return values != null && values.length > 0;
        } catch (Throwable ignored) { return false; }
    }

    private static boolean rowPass(List<String[]> rows, String dataset, String role) {
        for (String[] row : rows)
            if (dataset.equals(row[0]) && role.equals(row[5]))
                return "true".equals(row[11]) && !"UNAVAILABLE_WITHOUT_SOLVE".equals(row[15]);
        return false;
    }

    private static String firstPassingRolePrefix(List<String[]> rows, String prefix) {
        for (String[] row : rows)
            if (row[5].startsWith(prefix) && "true".equals(row[11])
                && !"UNAVAILABLE_WITHOUT_SOLVE".equals(row[15])) return row[0];
        return "";
    }

    private static String f(double value) { return String.format(Locale.ROOT, "%.17g", value); }
    private static String clean(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('|', '/');
    }
}
