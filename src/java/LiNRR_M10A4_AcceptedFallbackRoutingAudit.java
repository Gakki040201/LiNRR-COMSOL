import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Result-only audit of the accepted pre-compaction visualization source. */
public final class LiNRR_M10A4_AcceptedFallbackRoutingAudit {
    private static int serial = 0;
    private LiNRR_M10A4_AcceptedFallbackRoutingAudit() {}

    public static void main(String[] args) throws Exception {
        Path output = Paths.get(LiNRR_M10A4_VisualizationRuntimeInputs.EVIDENCE_DIR,
            "ACCEPTED_FALLBACK_SOURCE_ROUTING.csv");
        Files.createDirectories(output.getParent());
        List<String[]> rows = new ArrayList<String[]>();
        Model model = ModelUtil.load("M10A4PrecompactFallback",
            LiNRR_M10A4_FallbackRuntimeInputs.PRECOMPACT_M10A4);
        try {
            double[] velocity = triple(model, "dset_n2_solution", "comp_n2_flow",
                "sel_dom_n2_channel", 3, "sqrt(u2^2+v2^2+w2^2)", "m/s");
            double[] pressure = triple(model, "dset_n2_solution", "comp_n2_flow",
                "sel_dom_n2_channel", 3, "p2", "Pa");
            double[] pIn = triple(model, "dset_n2_solution", "comp_n2_flow",
                "sel_bnd_n2_inlet", 2, "p2", "Pa");
            double[] pOut = triple(model, "dset_n2_solution", "comp_n2_flow",
                "sel_bnd_n2_outlet", 2, "p2", "Pa");
            double qIn = integral(model, "dset_n2_solution", "comp_n2_flow", "sel_bnd_n2_inlet",
                "u2*nx+v2*ny+w2*nz", "m^3/s");
            double qOut = integral(model, "dset_n2_solution", "comp_n2_flow", "sel_bnd_n2_outlet",
                "u2*nx+v2*ny+w2*nz", "m^3/s");
            double expected = model.param().evaluate("Q_N2_lab", "m^3/s");
            double closure = Math.abs(qIn + qOut) / Math.max(Math.abs(qIn), 1e-300);
            double inletRelative = Math.abs(Math.abs(qIn) - expected) / Math.max(expected, 1e-300);
            require(velocity[1] > 0.0 && pressure[1] > pressure[0], "PRECOMPACT_N2_FLOW_ZERO_OR_FLAT");
            require(closure <= 1e-6 && inletRelative <= 1e-6,
                "PRECOMPACT_N2_FLOW_INCONSISTENT closure=" + closure + " inlet_relative=" + inletRelative);
            add(rows, "N2_velocity", "dset_n2_solution", "sol4", "comp_n2_flow", "sel_dom_n2_channel",
                "sqrt(u2^2+v2^2+w2^2)", "m/s", velocity,
                "Accepted N2 flow cache was intentionally cleared from compact M10A4; qin=" + f(qIn)
                    + "; qout=" + f(qOut) + "; Q_N2_lab=" + f(expected) + "; closure=" + f(closure));
            add(rows, "N2_pressure", "dset_n2_solution", "sol4", "comp_n2_flow", "sel_dom_n2_channel",
                "p2", "Pa", pressure, "inlet_mean=" + f(pIn[2]) + "; outlet_mean=" + f(pOut[2]));

            double[] cN2g = triple(model, "dset_species_n2_gas", "comp_species_n2_real",
                "m10a3_sel_dom_n2_channel", 3, "cN2g", "mol/m^3");
            add(rows, "N2_gas_concentration", "dset_species_n2_gas", "sol12", "comp_species_n2_real",
                "m10a3_sel_dom_n2_channel", "cN2g", "mol/m^3", cN2g,
                "Compact artifact also exposes equivalent cN2g through retained component routes; precompact source is retained as explicit fallback provenance.");

            LiNRR_M10A4_VisualizationCommon.writeCsv(output,
                "field,source_model,source_model_sha256,source_stage,dataset,configured_solution,component,selection,expression,unit,min,max,mean,finite,scientific_authority,reason_compact_source_unavailable,repository_provenance",
                rows);
            System.out.println("PRECOMPACTION_N2_FLOW_READABILITY=PASS");
            System.out.println("PRECOMPACTION_N2_FLOW_QIN=" + f(qIn));
            System.out.println("PRECOMPACTION_N2_FLOW_QOUT=" + f(qOut));
            System.out.println("PRECOMPACTION_N2_FLOW_CLOSURE=" + f(closure));
            System.out.println("ACCEPTED_FALLBACK_ROUTING_AUDIT=PASS");
            System.out.println("ACCEPTED_FALLBACK_ROUTING_SOLVE_TRIGGERED=FALSE");
        } finally { ModelUtil.remove("M10A4PrecompactFallback"); }
    }

    private static void add(List<String[]> rows, String field, String dataset, String solution,
                            String component, String selection, String expression, String unit,
                            double[] values, String reason) {
        rows.add(new String[]{field, LiNRR_M10A4_FallbackRuntimeInputs.PRECOMPACT_M10A4,
            LiNRR_M10A4_FallbackRuntimeInputs.PRECOMPACT_SHA256, "M10A4_IMMUTABLE_PRE_AUDIT_CHECKPOINT",
            dataset, solution, component, selection, expression, unit, f(values[0]), f(values[1]),
            f(values[2]), "true", "ACCEPTED_PRECOMPACTION_VISUALIZATION_SOURCE", reason,
            "Referenced by docs/M10A4_ionic_current_li_plating_contract.md and used as INPUT by LiNRR_M10A4_FinalCompact.java; accepted M10A4 run lineage."});
    }

    private static double[] triple(Model model, String dataset, String component, String selection,
                                   int dimension, String expression, String unit) {
        String suffix = dimension == 3 ? "Volume" : "Surface";
        int[] ids = model.component(component).selection(selection).entities(dimension);
        return new double[]{scalar(model, "Min" + suffix, dataset, component, ids, dimension, expression, unit),
            scalar(model, "Max" + suffix, dataset, component, ids, dimension, expression, unit),
            scalar(model, "Av" + suffix, dataset, component, ids, dimension, expression, unit)};
    }

    private static double integral(Model model, String dataset, String component, String selection,
                                   String expression, String unit) {
        int[] ids = model.component(component).selection(selection).entities(2);
        return scalar(model, "IntSurface", dataset, component, ids, 2, expression, unit);
    }

    private static double scalar(Model model, String type, String dataset, String component, int[] ids,
                                 int dimension, String expression, String unit) {
        String tag = "fallback_eval_" + (++serial);
        model.result().numerical().create(tag, type);
        try {
            model.result().numerical(tag).set("data", dataset);
            model.result().numerical(tag).selection().geom(model.component(component).geom().tags()[0], dimension);
            model.result().numerical(tag).selection().set(ids);
            model.result().numerical(tag).set("expr", new String[]{expression});
            model.result().numerical(tag).set("unit", new String[]{unit});
            if (type.startsWith("Int")) {
                model.result().numerical(tag).set("intorderactive", true);
                model.result().numerical(tag).set("intorder", 8);
            }
            double[][] values = model.result().numerical(tag).getReal();
            if (values == null || values.length == 0 || values[0].length == 0)
                throw new IllegalStateException("EMPTY_FALLBACK_EVAL " + dataset + " " + expression);
            double value = values[0][values[0].length - 1];
            if (!Double.isFinite(value)) throw new IllegalStateException("NONFINITE_FALLBACK_EVAL " + expression);
            return value;
        } finally { model.result().numerical().remove(tag); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static String f(double value) { return String.format(Locale.ROOT, "%.17g", value); }
}
