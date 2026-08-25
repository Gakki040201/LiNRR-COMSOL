import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Result-only N2-flow visualization export from the accepted immutable pre-compaction checkpoint. */
public final class LiNRR_M10A4_FallbackVisualizationExport {
    private static int serial = 0;
    private static final String DATA = "dset_n2_solution";
    private static final String COMP = "comp_n2_flow";
    private static final String DOM = "sel_dom_n2_channel";

    private LiNRR_M10A4_FallbackVisualizationExport() {}

    public static void main(String[] args) throws Exception {
        Path evidence = Paths.get(LiNRR_M10A4_VisualizationRuntimeInputs.EVIDENCE_DIR);
        Path images = Paths.get(LiNRR_M10A4_VisualizationRuntimeInputs.IMAGE_DIR);
        Files.createDirectories(evidence);
        Files.createDirectories(images);
        Model model = ModelUtil.load("M10A4FallbackVisualization",
            LiNRR_M10A4_FallbackRuntimeInputs.PRECOMPACT_M10A4);
        try {
            slice(model, "vizfb01_n2_velocity",
                "V01 | FLOW | N2 velocity | ACCEPTED PRECOMPACTION VISUALIZATION SOURCE | LOG DISPLAY OF MAGNITUDE",
                "log10(1+sqrt(u2^2+v2^2+w2^2)/(1e-3[m/s]))", "1");
            slice(model, "vizfb01_n2_pressure",
                "V01 | FLOW | N2 pressure | ACCEPTED PRECOMPACTION VISUALIZATION SOURCE",
                "p2", "Pa");
            streamline(model, "vizfb01_n2_streamlines",
                "V01 | FLOW | N2 streamlines | ACCEPTED PRECOMPACTION VISUALIZATION SOURCE");

            double[] velocity = range(model, "sqrt(u2^2+v2^2+w2^2)", "m/s");
            double[] pressure = range(model, "p2", "Pa");
            if (!(velocity[1] > 0.0) || !(pressure[1] > pressure[0]))
                throw new IllegalStateException("FALLBACK_N2_FIELD_ZERO_OR_FLAT");

            String provenance = "ACCEPTED_PRECOMPACTION_VISUALIZATION_SOURCE; source_sha256="
                + LiNRR_M10A4_FallbackRuntimeInputs.PRECOMPACT_SHA256
                + "; compact M10A4 remains scientific identity authority; not embedded in VISUAL_REVIEW.mph";
            List<String[]> rows = new ArrayList<String[]>();
            add(model, rows, "vizfb01_n2_velocity", "sqrt(u2^2+v2^2+w2^2)", "m/s", velocity, provenance);
            add(model, rows, "vizfb01_n2_pressure", "p2", "Pa", pressure, provenance);
            add(model, rows, "vizfb01_n2_streamlines", "sqrt(u2^2+v2^2+w2^2)", "m/s", velocity, provenance);
            LiNRR_M10A4_VisualizationCommon.writeCsv(evidence.resolve("PLOT_INTEGRITY_FALLBACK.csv"),
                "plot_tag,label,section,role,dataset,component,selection,expression,dimension,selection_count,plot_run_ok,warning,finite_min,finite_max,status,notes",
                rows);

            image(model, "vizfb01_n2_velocity", images.resolve("n2_velocity.png"));
            image(model, "vizfb01_n2_pressure", images.resolve("n2_pressure.png"));
            image(model, "vizfb01_n2_streamlines", images.resolve("n2_streamlines.png"));
            System.out.println("FALLBACK_N2_VISUALIZATION_PLOT_COUNT=3");
            System.out.println("FALLBACK_N2_VISUALIZATION_RUNTIME_PASS=PASS");
            System.out.println("FALLBACK_N2_VISUALIZATION_WARNINGS=0");
            System.out.println("FALLBACK_N2_VISUALIZATION_SOLVE_TRIGGERED=FALSE");
        } finally { ModelUtil.remove("M10A4FallbackVisualization"); }
    }

    private static void base(Model model, String tag, String label) {
        if (model.result().hasTag(tag)) model.result().remove(tag);
        model.result().create(tag, "PlotGroup3D");
        model.result(tag).label(label);
        model.result(tag).set("data", DATA);
    }

    private static void slice(Model model, String tag, String label, String expression, String unit) {
        base(model, tag, label);
        model.result(tag).create("slice", "Slice");
        model.result(tag).feature("slice").set("expr", expression);
        model.result(tag).feature("slice").set("unit", unit);
    }

    private static void streamline(Model model, String tag, String label) {
        base(model, tag, label);
        model.result(tag).create("str", "Streamline");
        model.result(tag).feature("str").selection().named("sel_bnd_n2_inlet");
        model.result(tag).feature("str").set("expr", new String[]{"u2", "v2", "w2"});
    }

    private static void add(Model model, List<String[]> rows, String tag, String expression,
                            String unit, double[] values, String notes) {
        model.result(tag).run();
        if (model.result(tag).hasWarning()) throw new IllegalStateException("FALLBACK_PLOT_WARNING " + tag);
        int count = model.component(COMP).selection(DOM).entities(3).length;
        if (count == 0) throw new IllegalStateException("FALLBACK_N2_SELECTION_EMPTY");
        rows.add(new String[]{tag, model.result(tag).label(), "V01_FLOW", "MAIN_TEACHING", DATA,
            COMP, DOM, expression, "3", Integer.toString(count), "true", "false", f(values[0]),
            f(values[1]), "PASS", notes});
        System.out.println("FALLBACK_VIZ_PLOT_RUNTIME|tag=" + tag + "|status=PASS|warning=false");
    }

    private static double[] range(Model model, String expression, String unit) {
        int[] ids = model.component(COMP).selection(DOM).entities(3);
        return new double[]{scalar(model, "MinVolume", ids, expression, unit),
            scalar(model, "MaxVolume", ids, expression, unit)};
    }

    private static double scalar(Model model, String type, int[] ids, String expression, String unit) {
        String tag = "fallback_viz_eval_" + (++serial);
        model.result().numerical().create(tag, type);
        try {
            model.result().numerical(tag).set("data", DATA);
            model.result().numerical(tag).selection().geom(model.component(COMP).geom().tags()[0], 3);
            model.result().numerical(tag).selection().set(ids);
            model.result().numerical(tag).set("expr", new String[]{expression});
            model.result().numerical(tag).set("unit", new String[]{unit});
            double[][] values = model.result().numerical(tag).getReal();
            if (values == null || values.length == 0 || values[0].length == 0)
                throw new IllegalStateException("FALLBACK_VIZ_EMPTY_FIELD " + expression);
            double value = values[0][values[0].length - 1];
            if (!Double.isFinite(value)) throw new IllegalStateException("FALLBACK_VIZ_NONFINITE " + expression);
            return value;
        } finally { model.result().numerical().remove(tag); }
    }

    private static void image(Model model, String plot, Path file) throws Exception {
        model.result(plot).run();
        String tag = "fallback_viz_image_" + (++serial);
        model.result().export().create(tag, plot, "Image3D");
        try {
            model.result().export(tag).set("target", "file");
            model.result().export(tag).set("filename", file.toString());
            model.result().export(tag).set("width", 1400);
            model.result().export(tag).set("height", 1000);
            model.result().export(tag).run();
            if (!Files.isRegularFile(file) || Files.size(file) == 0)
                throw new IllegalStateException("FALLBACK_VIZ_IMAGE_EMPTY " + file);
        } finally { model.result().export().remove(tag); }
    }

    private static String f(double value) { return String.format(Locale.ROOT, "%.17g", value); }
}
