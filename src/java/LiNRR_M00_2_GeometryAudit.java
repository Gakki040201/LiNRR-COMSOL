import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.io.IOException;
import java.util.Locale;

/**
 * M00.2: parameter-scaling and named-selection robustness audit.
 *
 * Geometry and all dimensions remain PROVISIONAL. This is numerical
 * verification only and contains no physics.
 */
public final class LiNRR_M00_2_GeometryAudit {
    private static final String OUTPUT =
        "models/generated/LiNRR_M00_2_geometry_audit.mph";
    private static final String PROVISIONAL =
        "PROVISIONAL - numerical verification only";

    private static final String[] CASES = {
        "baseline", "Lcell_x_0.5", "Lcell_x_2", "Hcell_x_0.5",
        "Hcell_x_2", "Wcell_x_0.5", "Wcell_x_2"
    };
    private static final double[][] SCALE = {
        {1.0, 1.0, 1.0}, {0.5, 1.0, 1.0}, {2.0, 1.0, 1.0},
        {1.0, 0.5, 1.0}, {1.0, 2.0, 1.0}, {1.0, 1.0, 0.5},
        {1.0, 1.0, 2.0}
    };

    private LiNRR_M00_2_GeometryAudit() {}

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("M00_2_Audit");
        model.label("LiNRR_M00_2_geometry_audit | " + PROVISIONAL);
        defineParameters(model);
        buildGeometry(model);
        createSelections(model);
        buildMesh(model);

        System.out.println("M00_2_SCALING|case|Lcell_mm|Hcell_mm|Wcell_mm|" +
            "domain_area_mm2|inlet_length_mm|outlet_length_mm|" +
            "upper_length_mm|lower_length_mm|mesh_built|mph_saved|status");
        System.out.println("M00_2_SELECTION|case|selection|entity_dimension|" +
            "entity_count|measure_mm_or_mm2|expected_measure_mm_or_mm2|" +
            "relative_error|status");

        for (int i = 0; i < CASES.length; i++) {
            auditCase(model, CASES[i], SCALE[i]);
        }

        // The retained editable MPH is the baseline configuration.
        setDimensions(model, 1.0, 1.0, 1.0);
        rebuild(model);
        saveModel(model);
        System.out.println("M00_2_META|RUN_STATE|SYNTHETIC_SMOKE_TEST");
        System.out.println("M00_2_META|CALIBRATION_MODE|PROVISIONAL");
        System.out.println("M00_2_META|M03B_READY|FALSE");
    }

    private static void defineParameters(Model model) {
        model.param().set("Lcell_base", "55[mm]", PROVISIONAL);
        model.param().set("Hcell_base", "4[mm]", PROVISIONAL);
        model.param().set("Wcell_base", "55[mm]", PROVISIONAL);
        model.param().set("sL", "1", "M00.2 length scale factor");
        model.param().set("sH", "1", "M00.2 height scale factor");
        model.param().set("sW", "1", "M00.2 out-of-plane width scale factor");
        model.param().set("Lcell", "sL*Lcell_base", PROVISIONAL);
        model.param().set("Hcell", "sH*Hcell_base", PROVISIONAL);
        model.param().set("Wcell", "sW*Wcell_base", PROVISIONAL);
        model.param().set("sel_tol", "1e-6[mm]", "Coordinate-selection tolerance");
    }

    private static void buildGeometry(Model model) {
        model.component().create("comp1", true);
        model.component("comp1").label("M00.2 parameterized 2D channel");
        model.component("comp1").geom().create("geom1", 2);
        model.component("comp1").geom("geom1").lengthUnit("mm");
        model.component("comp1").geom("geom1").create("r_channel", "Rectangle");
        model.component("comp1").geom("geom1").feature("r_channel")
            .label("Parameterized electrolyte rectangle");
        model.component("comp1").geom("geom1").feature("r_channel")
            .set("size", new String[] {"Lcell", "Hcell"});
        model.component("comp1").geom("geom1").run();
    }

    private static void createSelections(Model model) {
        createBox(model, "sel_electrolyte_m002", "Electrolyte domain", 2,
            "-sel_tol", "Lcell+sel_tol", "-sel_tol", "Hcell+sel_tol");
        createBox(model, "sel_inlet_m002", "Inlet", 1,
            "-sel_tol", "sel_tol", "-sel_tol", "Hcell+sel_tol");
        createBox(model, "sel_outlet_m002", "Outlet", 1,
            "Lcell-sel_tol", "Lcell+sel_tol", "-sel_tol", "Hcell+sel_tol");
        createBox(model, "sel_upper_m002", "Upper boundary", 1,
            "-sel_tol", "Lcell+sel_tol", "Hcell-sel_tol", "Hcell+sel_tol");
        createBox(model, "sel_lower_m002", "Lower boundary", 1,
            "-sel_tol", "Lcell+sel_tol", "-sel_tol", "sel_tol");
    }

    private static void createBox(Model model, String tag, String label, int dim,
                                  String xmin, String xmax, String ymin, String ymax) {
        model.component("comp1").selection().create(tag, "Box");
        model.component("comp1").selection(tag).label(label);
        model.component("comp1").selection(tag).set("entitydim", dim);
        model.component("comp1").selection(tag).set("condition", "inside");
        model.component("comp1").selection(tag).set("xmin", xmin);
        model.component("comp1").selection(tag).set("xmax", xmax);
        model.component("comp1").selection(tag).set("ymin", ymin);
        model.component("comp1").selection(tag).set("ymax", ymax);
    }

    private static void buildMesh(Model model) {
        model.component("comp1").mesh().create("mesh1");
        model.component("comp1").mesh("mesh1").label("M00.2 geometry-audit mesh");
        model.component("comp1").mesh("mesh1").autoMeshSize(3);
        model.component("comp1").mesh("mesh1").run();
    }

    private static void setDimensions(Model model, double sL, double sH, double sW) {
        model.param().set("sL", format(sL));
        model.param().set("sH", format(sH));
        model.param().set("sW", format(sW));
    }

    private static void rebuild(Model model) {
        model.component("comp1").geom("geom1").run();
        model.component("comp1").mesh("mesh1").run();
    }

    private static void auditCase(Model model, String name, double[] scale)
            throws IOException {
        setDimensions(model, scale[0], scale[1], scale[2]);
        rebuild(model);

        double l = model.param().evaluate("Lcell", "mm");
        double h = model.param().evaluate("Hcell", "mm");
        double w = model.param().evaluate("Wcell", "mm");
        double area = namedMeasure(model, "sel_electrolyte_m002", 2);
        double inlet = namedMeasure(model, "sel_inlet_m002", 1);
        double outlet = namedMeasure(model, "sel_outlet_m002", 1);
        double upper = namedMeasure(model, "sel_upper_m002", 1);
        double lower = namedMeasure(model, "sel_lower_m002", 1);

        auditSelection(model, name, "sel_electrolyte_m002", 2, area, l * h);
        auditSelection(model, name, "sel_inlet_m002", 1, inlet, h);
        auditSelection(model, name, "sel_outlet_m002", 1, outlet, h);
        auditSelection(model, name, "sel_upper_m002", 1, upper, l);
        auditSelection(model, name, "sel_lower_m002", 1, lower, l);

        boolean pass = positive(area, inlet, outlet, upper, lower);
        if (!pass) {
            throw new IllegalStateException("FAILED_SELECTION_MAPPING: " + name);
        }
        // Exercise MPH serialization for every scaling case without retaining
        // seven durable model files. The final save below retains baseline.
        model.save(OUTPUT);
        System.out.println(String.format(Locale.ROOT,
            "M00_2_SCALING|%s|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|" +
            "%.12g|%.12g|true|true|PASS",
            name, l, h, w, area, inlet, outlet, upper, lower));
    }

    private static double namedMeasure(Model model, String selectionTag, int dim) {
        int[] entities = model.component("comp1").selection(selectionTag).entities(dim);
        if (entities == null || entities.length != 1) {
            throw new IllegalStateException("FAILED_SELECTION_MAPPING: " + selectionTag +
                " expected one entity, found " + (entities == null ? 0 : entities.length));
        }
        model.component("comp1").geom("geom1").measureFinal().selection().geom(dim);
        model.component("comp1").geom("geom1").measureFinal().selection().set(entities);
        return model.component("comp1").geom("geom1").measureFinal().getVolume();
    }

    private static void auditSelection(Model model, String caseName, String tag,
                                       int dim, double measure, double expected) {
        int count = model.component("comp1").selection(tag).entities(dim).length;
        double error = relative(measure, expected);
        String status = count == 1 && measure > 0.0 && error <= 1.0e-10
            ? "PASS" : "FAILED_SELECTION_MAPPING";
        System.out.println(String.format(Locale.ROOT,
            "M00_2_SELECTION|%s|%s|%d|%d|%.12g|%.12g|%.12g|%s",
            caseName, tag, dim, count, measure, expected, error, status));
        if (!"PASS".equals(status)) {
            throw new IllegalStateException("FAILED_SELECTION_MAPPING: " +
                caseName + "/" + tag);
        }
    }

    private static boolean positive(double... values) {
        for (double value : values) {
            if (!(Double.isFinite(value) && value > 0.0)) return false;
        }
        return true;
    }

    private static double relative(double a, double b) {
        return Math.abs(a - b) / Math.max(Math.abs(b), 1.0e-30);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.12g", value);
    }

    private static void saveModel(Model model) throws IOException {
        model.save(OUTPUT);
    }
}
