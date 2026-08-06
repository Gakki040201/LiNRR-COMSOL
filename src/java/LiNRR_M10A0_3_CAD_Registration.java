import com.comsol.model.GeomSequence;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * M10A0.3a - diagnostic import baseline.
 *
 * This stage deliberately reproduces only the already validated M10A0.2
 * strict-import path. It creates two raw CAD components and imports one exact,
 * hash-locked STEP solid into each component. It creates no transforms,
 * assembly, selections, materials, mesh, physics, study, or solver.
 */
public final class LiNRR_M10A0_3_CAD_Registration {

    private static final String RUNTIME_CLASS = "LiNRR_M10A0_3_RuntimeInputs";

    private static String currentApi = "NONE";
    private static String currentFeature = "NONE";

    private LiNRR_M10A0_3_CAD_Registration() {
        // Utility class.
    }

    public static Model run() throws Exception {
        System.out.println("M10A0_3_BOOT_START");

        try {
            currentApi = "runtimeString/runtimeLong";
            currentFeature = "RUNTIME_INPUTS";

            final Path ccStep = Paths.get(runtimeString("CC_STEP"))
                    .toAbsolutePath().normalize();
            final Path chamberStep = Paths.get(runtimeString("CHAMBER_STEP"))
                    .toAbsolutePath().normalize();
            final Path outputMph = Paths.get(runtimeString("OUTPUT_MPH"))
                    .toAbsolutePath().normalize();

            // Force every compiled runtime field to be resolved before COMSOL
            // model creation so runtime-input failures remain distinguishable.
            runtimeString("CC_SHA256");
            runtimeString("CHAMBER_SHA256");
            runtimeLong("CC_BYTES");
            runtimeLong("CHAMBER_BYTES");

            System.out.println("RUNTIME_INPUT_CLASS=" + RUNTIME_CLASS);
            System.out.println("CC_STEP=" + ccStep);
            System.out.println("CHAMBER_STEP=" + chamberStep);
            System.out.println("OUTPUT_MPH=" + outputMph);
            System.out.println("M10A0_3_RUNTIME_INPUTS_PASS");

            markApi("ModelUtil.setDefaultGeometryKernel(cadps)", "GLOBAL");
            ModelUtil.setDefaultGeometryKernel("cadps");
            System.out.println("M10A0_3_CAD_KERNEL_PASS");

            markApi("ModelUtil.create(Model)", "Model");
            final Model model = ModelUtil.create("Model");
            System.out.println("M10A0_3_MODEL_CREATE_PASS");

            markApi("model.label", "Model");
            model.label("LiNRR_M10A0_3a_import_baseline.mph");
            markApi("model.comments", "Model");
            model.comments(
                    "M10A0.3a diagnostic baseline. Exact M10A0.2 STEP solids " +
                    "are imported in separate raw CAD components. No Rotate, " +
                    "Move, Mirror, Assembly, Selection, material, mesh, physics, " +
                    "study, or solver is included.");

            buildCadComponent(
                    model,
                    "comp_cc_raw",
                    "Raw real CAD - current collector / gas flow field",
                    "geom_cc_raw",
                    "imp_cc",
                    ccStep);

            buildCadComponent(
                    model,
                    "comp_chamber_raw",
                    "Raw real CAD - electrolyte chamber",
                    "geom_chamber_raw",
                    "imp_chamber",
                    chamberStep);

            System.out.println("M10A0_3_RAW_COMPONENT_CREATE_PASS");

            markApi("model.save", "Model");
            model.save(outputMph.toString());

            System.out.println("M10A0_3A_IMPORT_BASELINE_PASS");
            System.out.println("OUTPUT_MPH=" + outputMph);
            return model;
        } catch (Throwable error) {
            System.err.println("M10A0_3_FAILURE_CONTEXT_BEGIN");
            System.err.println("M10A0_3_FIRST_FAILED_API=" + currentApi);
            System.err.println("M10A0_3_FIRST_FAILED_FEATURE_TAG=" + currentFeature);
            System.err.println("M10A0_3_EXCEPTION_CLASS=" +
                    error.getClass().getName());
            System.err.println("M10A0_3_EXCEPTION_MESSAGE=" +
                    String.valueOf(error.getMessage()));
            error.printStackTrace(System.err);
            System.err.println("M10A0_3_FAILURE_CONTEXT_END");

            if (error instanceof Exception) {
                throw (Exception) error;
            }
            throw (Error) error;
        }
    }

    private static void buildCadComponent(
            Model model,
            String componentTag,
            String componentLabel,
            String geometryTag,
            String importTag,
            Path stepFile) {

        markApi("model.component().create", componentTag);
        model.component().create(componentTag, true);
        markApi("model.component(tag).label", componentTag);
        model.component(componentTag).label(componentLabel);

        markApi("model.component(tag).geom().create", geometryTag);
        final GeomSequence geom =
                model.component(componentTag).geom().create(geometryTag, 3);
        markApi("geom.label", geometryTag);
        geom.label("Strict raw STEP import - no assembly transform");
        markApi("geom.lengthUnit", geometryTag);
        geom.lengthUnit("mm");
        markApi("geom.geomRep", geometryTag);
        geom.geomRep("cadps");

        markApi("geom.feature().create(Import)", importTag);
        geom.feature().create(importTag, "Import");
        markApi("geom.feature(tag).label", importTag);
        geom.feature(importTag).label("Strict source STEP import");

        setImport(geom, importTag, "filename", stepFile.toString());
        setImport(geom, importTag, "unit", "source");
        setImport(geom, importTag, "keepsolid", "on");
        setImport(geom, importTag, "keepbnd", "on");
        setImport(geom, importTag, "keepfree", "off");
        setImport(geom, importTag, "knit", "solid");
        setImport(geom, importTag, "fillholes", "off");
        setImport(geom, importTag, "removeredundant", "off");
        setImport(geom, importTag, "simplify", "off");
        setImport(geom, importTag, "deletedetails", "off");
        setImport(geom, importTag, "healedges", "off");
        setImport(geom, importTag, "minimizetol", "off");
        setImport(geom, importTag, "importbodynames", "on");
        setImport(geom, importTag, "check", "on");
        setImport(geom, importTag, "fixerrors", "off");
        setImport(geom, importTag, "selresult", "on");
        setImport(geom, importTag, "selresultshow", "all");

        markApi("geom.feature(tag).importData", importTag);
        geom.feature(importTag).importData();
        markApi("geom.run", geometryTag);
        geom.run();
        markApi("geom.check", geometryTag);
        geom.check();
    }

    private static void setImport(
            GeomSequence geom,
            String importTag,
            String property,
            String value) {

        markApi("geom.feature(tag).set(" + property + ")", importTag);
        geom.feature(importTag).set(property, value);
    }

    private static void markApi(String api, String featureTag) {
        currentApi = api;
        currentFeature = featureTag;
        System.out.println(
                "M10A0_3_API_BEGIN|api=" + api + "|feature=" + featureTag);
    }

    private static String runtimeString(String fieldName) {
        try {
            final Class<?> runtime = Class.forName(RUNTIME_CLASS);
            final Object value = runtime.getField(fieldName).get(null);
            if (!(value instanceof String)) {
                throw new IllegalStateException(
                        "RUNTIME_FIELD_NOT_STRING: " + fieldName);
            }
            final String text = ((String) value).trim();
            if (text.isEmpty()) {
                throw new IllegalStateException(
                        "RUNTIME_FIELD_EMPTY: " + fieldName);
            }
            return text;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(
                    "RUNTIME_INPUT_CLASS_OR_FIELD_MISSING: " + fieldName,
                    error);
        }
    }

    private static long runtimeLong(String fieldName) {
        try {
            final Class<?> runtime = Class.forName(RUNTIME_CLASS);
            final Object value = runtime.getField(fieldName).get(null);
            if (value instanceof Long) {
                return ((Long) value).longValue();
            }
            if (value instanceof Integer) {
                return ((Integer) value).longValue();
            }
            throw new IllegalStateException(
                    "RUNTIME_FIELD_NOT_INTEGER: " + fieldName);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(
                    "RUNTIME_INPUT_CLASS_OR_FIELD_MISSING: " + fieldName,
                    error);
        }
    }

    public static void main(String[] args) throws Exception {
        run();
    }
}
