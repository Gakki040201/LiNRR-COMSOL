import com.comsol.model.GeomSequence;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/** M10A0.3b - frozen 3a baseline plus one top-collector Rotate feature. */
public final class LiNRR_M10A0_3b_RotateTop {

    private static final String RUNTIME_CLASS =
            "LiNRR_M10A0_3b_RuntimeInputs";
    private static final double BBOX_TOLERANCE_MM = 0.02;

    private static String currentApi = "NONE";
    private static String currentFeature = "NONE";

    private LiNRR_M10A0_3b_RotateTop() {
        // Utility class.
    }

    public static Model run() throws Exception {
        System.out.println("M10A0_3B_BOOT_START");
        try {
            currentApi = "runtimeString/runtimeLong";
            currentFeature = "RUNTIME_INPUTS";

            final Path ccStep = Paths.get(runtimeString("CC_STEP"))
                    .toAbsolutePath().normalize();
            final Path chamberStep = Paths.get(runtimeString("CHAMBER_STEP"))
                    .toAbsolutePath().normalize();
            final Path outputMph = Paths.get(runtimeString("OUTPUT_MPH"))
                    .toAbsolutePath().normalize();

            runtimeString("CC_SHA256");
            runtimeString("CHAMBER_SHA256");
            runtimeLong("CC_BYTES");
            runtimeLong("CHAMBER_BYTES");

            System.out.println("RUNTIME_INPUT_CLASS=" + RUNTIME_CLASS);
            System.out.println("CC_STEP=" + ccStep);
            System.out.println("CHAMBER_STEP=" + chamberStep);
            System.out.println("OUTPUT_MPH=" + outputMph);
            System.out.println("M10A0_3B_RUNTIME_INPUTS_PASS");

            markApi("ModelUtil.setDefaultGeometryKernel(cadps)", "GLOBAL");
            ModelUtil.setDefaultGeometryKernel("cadps");
            System.out.println("M10A0_3B_CAD_KERNEL_PASS");

            markApi("ModelUtil.create(Model)", "Model");
            final Model model = ModelUtil.create("Model");
            markApi("model.label", "Model");
            model.label("LiNRR_M10A0_3b_rotate_top.mph");
            markApi("model.comments", "Model");
            model.comments(
                    "M10A0.3b. Frozen strict raw imports plus one independent " +
                    "current-collector Rotate about global X by -90 deg. No " +
                    "translation, reflection, assembly, explicit selection " +
                    "feature, pair, material, mesh, physics, study, or solver.");

            buildRawComponent(
                    model,
                    "comp_cc_raw",
                    "Raw real CAD - current collector / gas flow field",
                    "geom_cc_raw",
                    "imp_cc",
                    ccStep);
            buildRawComponent(
                    model,
                    "comp_chamber_raw",
                    "Raw real CAD - electrolyte chamber",
                    "geom_chamber_raw",
                    "imp_chamber",
                    chamberStep);
            System.out.println("M10A0_3B_RAW_IMPORTS_PASS");

            buildRotatedComponent(model, ccStep);

            final double[] sourceBox = geometryBox(
                    model, "comp_cc_raw", "geom_cc_raw");
            final double[] rotatedBox = geometryBox(
                    model, "comp_cc_rotated", "geom_cc_rotated");
            final int[] rotatedEntities = geometryEntities(
                    model, "comp_cc_rotated", "geom_cc_rotated");
            final boolean rotatedCadRepresentation = geometryHasCadRep(
                    model, "comp_cc_rotated", "geom_cc_rotated");
            validateGeometry(
                    sourceBox,
                    rotatedBox,
                    rotatedEntities,
                    rotatedCadRepresentation);

            System.out.println(
                    "M10A0_3B_SOURCE_CC_BBOX_MM=" +
                    formatBox(sourceBox));
            System.out.println(
                    "M10A0_3B_ROTATED_CC_BBOX_MM=" +
                    formatBox(rotatedBox));
            System.out.println(
                    "M10A0_3B_ROTATED_ENTITIES=" +
                    "domains:" + rotatedEntities[0] +
                    ",boundaries:" + rotatedEntities[1] +
                    ",edges:" + rotatedEntities[2] +
                    ",vertices:" + rotatedEntities[3]);
            System.out.println(
                    "M10A0_3B_ROTATED_CAD_REPRESENTATION=" +
                    rotatedCadRepresentation);
            System.out.println("M10A0_3B_ROTATE_BUILD_PASS");

            markApi("model.save", "Model");
            model.save(outputMph.toString());
            System.out.println("M10A0_3B_MODEL_SAVE_PASS");
            System.out.println("M10A0_3B_ROTATE_TOP=PASS");
            System.out.println("OUTPUT_MPH=" + outputMph);
            return model;
        } catch (Throwable error) {
            System.err.println("M10A0_3B_FAILURE_CONTEXT_BEGIN");
            System.err.println("M10A0_3B_FIRST_FAILED_API=" + currentApi);
            System.err.println(
                    "M10A0_3B_FIRST_FAILED_FEATURE_TAG=" + currentFeature);
            System.err.println(
                    "M10A0_3B_EXCEPTION_CLASS=" + error.getClass().getName());
            System.err.println(
                    "M10A0_3B_EXCEPTION_MESSAGE=" +
                    String.valueOf(error.getMessage()));
            error.printStackTrace(System.err);
            System.err.println("M10A0_3B_FAILURE_CONTEXT_END");
            if (error instanceof Exception) {
                throw (Exception) error;
            }
            throw (Error) error;
        }
    }

    private static void buildRawComponent(
            Model model,
            String componentTag,
            String componentLabel,
            String geometryTag,
            String importTag,
            Path stepFile) {

        final GeomSequence geom = createCadGeometry(
                model, componentTag, componentLabel, geometryTag,
                "Strict raw STEP import - no transform");
        createStrictImport(geom, importTag, stepFile, true);
        markApi("geom.run", geometryTag);
        geom.run();
        markApi("geom.check", geometryTag);
        geom.check();
    }

    private static void buildRotatedComponent(Model model, Path ccStep) {
        final String geometryTag = "geom_cc_rotated";
        final String importTag = "imp_cc_rotate";
        final String rotateTag = "rot_cc_top";

        final GeomSequence geom = createCadGeometry(
                model,
                "comp_cc_rotated",
                "Current collector rotated about global X - M10A0.3b",
                geometryTag,
                "Strict collector import plus Rx(-90 deg)");
        createStrictImport(geom, importTag, ccStep, false);

        markApi("geom.feature().create(Rotate)", rotateTag);
        geom.feature().create(rotateTag, "Rotate");
        markApi("geom.feature(tag).label", rotateTag);
        geom.feature(rotateTag).label(
                "Top collector rotation: global X axis, -90 deg");
        markApi("geom.feature(tag).selection(input).set", rotateTag);
        geom.feature(rotateTag).selection("input").set(importTag);
        setRotate(geom, rotateTag, "specify", "axis");
        setRotate(geom, rotateTag, "axistype", "cartesian");
        setRotate(geom, rotateTag, "axis", new double[] {1.0, 0.0, 0.0});
        setRotate(geom, rotateTag, "pos", new double[] {0.0, 0.0, 0.0});
        setRotate(geom, rotateTag, "rot", -90.0);
        System.out.println("M10A0_3B_ROTATE_FEATURE_CREATE_PASS");

        markApi("geom.run", geometryTag);
        geom.run();
        markApi("geom.check", geometryTag);
        geom.check();
    }

    private static GeomSequence createCadGeometry(
            Model model,
            String componentTag,
            String componentLabel,
            String geometryTag,
            String geometryLabel) {

        markApi("model.component().create", componentTag);
        model.component().create(componentTag, true);
        markApi("model.component(tag).label", componentTag);
        model.component(componentTag).label(componentLabel);
        markApi("model.component(tag).geom().create", geometryTag);
        final GeomSequence geom =
                model.component(componentTag).geom().create(geometryTag, 3);
        markApi("geom.label", geometryTag);
        geom.label(geometryLabel);
        markApi("geom.lengthUnit", geometryTag);
        geom.lengthUnit("mm");
        markApi("geom.geomRep", geometryTag);
        geom.geomRep("cadps");
        return geom;
    }

    private static void createStrictImport(
            GeomSequence geom,
            String importTag,
            Path stepFile,
            boolean retainBaselineResultSelections) {

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
        if (retainBaselineResultSelections) {
            setImport(geom, importTag, "selresult", "on");
            setImport(geom, importTag, "selresultshow", "all");
        }
        markApi("geom.feature(tag).importData", importTag);
        geom.feature(importTag).importData();
    }

    private static void setImport(
            GeomSequence geom,
            String importTag,
            String property,
            String value) {
        markApi("geom.feature(tag).set(" + property + ")", importTag);
        geom.feature(importTag).set(property, value);
    }

    private static void setRotate(
            GeomSequence geom,
            String rotateTag,
            String property,
            String value) {
        markApi("geom.feature(tag).set(" + property + ")", rotateTag);
        geom.feature(rotateTag).set(property, value);
    }

    private static void setRotate(
            GeomSequence geom,
            String rotateTag,
            String property,
            double value) {
        markApi("geom.feature(tag).set(" + property + ")", rotateTag);
        geom.feature(rotateTag).set(property, value);
    }

    private static void setRotate(
            GeomSequence geom,
            String rotateTag,
            String property,
            double[] value) {
        markApi("geom.feature(tag).set(" + property + ")", rotateTag);
        geom.feature(rotateTag).set(property, value);
    }

    private static double[] geometryBox(
            Model model,
            String componentTag,
            String geometryTag) {

        markApi("model.component(tag).geom(tag)", geometryTag);
        final GeomSequence geom = model.component(componentTag).geom(geometryTag);
        markApi("geom.getBoundingBox", geometryTag);
        return geom.getBoundingBox();
    }

    private static int[] geometryEntities(
            Model model,
            String componentTag,
            String geometryTag) {

        markApi("model.component(tag).geom(tag)", geometryTag);
        final GeomSequence geom = model.component(componentTag).geom(geometryTag);
        markApi("geom.getNDomains", geometryTag);
        final int domains = geom.getNDomains();
        markApi("geom.getNBoundaries", geometryTag);
        final int boundaries = geom.getNBoundaries();
        markApi("geom.getNEdges", geometryTag);
        final int edges = geom.getNEdges();
        markApi("geom.getNVertices", geometryTag);
        final int vertices = geom.getNVertices();
        return new int[] {domains, boundaries, edges, vertices};
    }

    private static boolean geometryHasCadRep(
            Model model,
            String componentTag,
            String geometryTag) {

        markApi("model.component(tag).geom(tag)", geometryTag);
        final GeomSequence geom = model.component(componentTag).geom(geometryTag);
        markApi("geom.hasCadRep", geometryTag);
        return geom.hasCadRep();
    }

    private static void validateGeometry(
            double[] sourceBox,
            double[] rotatedBox,
            int[] rotatedEntities,
            boolean rotatedCadRepresentation) {

        final double[] expectedSource = {
                -0.0025, 108.0025,
                -0.0025, 23.0025,
                -108.0025, 0.0025
        };
        final double[] expectedRotated = {
                -0.0025, 108.0025,
                -108.0025, 0.0025,
                -23.0025, 0.0025
        };
        validateBox("SOURCE_CC", sourceBox, expectedSource);
        validateBox("ROTATED_CC", rotatedBox, expectedRotated);

        if (rotatedEntities == null || rotatedEntities.length != 4) {
            throw new IllegalStateException("ROTATED_ENTITY_COUNTS_INVALID");
        }
        if (rotatedEntities[0] != 1 ||
                rotatedEntities[1] != 135 ||
                rotatedEntities[2] != 372 ||
                rotatedEntities[3] != 245) {
            throw new IllegalStateException(
                    "ROTATED_TOPOLOGY_CHANGED: domains=" + rotatedEntities[0] +
                    " boundaries=" + rotatedEntities[1] +
                    " edges=" + rotatedEntities[2] +
                    " vertices=" + rotatedEntities[3]);
        }
        if (!rotatedCadRepresentation) {
            throw new IllegalStateException(
                    "ROTATED_CAD_REPRESENTATION_FALSE");
        }
    }

    private static void validateBox(
            String name,
            double[] actual,
            double[] expected) {
        if (actual == null || actual.length != 6) {
            throw new IllegalStateException(name + "_INVALID_BOUNDING_BOX");
        }
        for (int i = 0; i < actual.length; i++) {
            if (!Double.isFinite(actual[i])) {
                throw new IllegalStateException(
                        name + "_NONFINITE_BOUNDING_BOX_INDEX_" + i);
            }
            if (Math.abs(actual[i] - expected[i]) > BBOX_TOLERANCE_MM) {
                throw new IllegalStateException(
                        name + "_BBOX_OUTSIDE_TOLERANCE_INDEX_" + i +
                        ": actual=" + actual[i] +
                        " expected=" + expected[i] +
                        " tolerance_mm=" + BBOX_TOLERANCE_MM);
            }
        }
    }

    private static String formatBox(double[] box) {
        return "[" +
                number(box[0]) + "," + number(box[1]) + ";" +
                number(box[2]) + "," + number(box[3]) + ";" +
                number(box[4]) + "," + number(box[5]) + "]";
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.12g", value);
    }

    private static void markApi(String api, String featureTag) {
        currentApi = api;
        currentFeature = featureTag;
        System.out.println(
                "M10A0_3B_API_BEGIN|api=" + api + "|feature=" + featureTag);
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
