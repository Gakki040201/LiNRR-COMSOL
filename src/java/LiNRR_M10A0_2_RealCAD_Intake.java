import com.comsol.model.GeomObject;
import com.comsol.model.GeomSequence;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M10A0.2 — strict intake of the two real STEP CAD files.
 *
 * Runtime-security design:
 *   - COMSOL Runtime can prohibit environment-variable access and arbitrary
 *     Java file reads;
 *   - all run-specific strings and file metadata are compiled into a transparent
 *     companion class by PowerShell;
 *   - this durable builder loads that class by reflection;
 *   - source-file integrity remains checked by PowerShell before COMSOL starts;
 *   - the CAD audit is emitted as ASCII CSV records in the batch log and is
 *     materialized into a CSV by PowerShell.
 *
 * Scientific boundary:
 *   - imports each STEP file unchanged into its own CAD-kernel component;
 *   - records exact COMSOL geometry statistics;
 *   - does not align the parts, infer assembly transforms, create fluid domains,
 *     add mesh, physics, materials, studies, or solvers;
 *   - strict mode disables defeaturing, simplification, and automatic repair.
 */
public final class LiNRR_M10A0_2_RealCAD_Intake {

    private static final String RUNTIME_CLASS = "LiNRR_M10A0_2_RuntimeInputs";
    private static final String AUDIT_PREFIX = "CAD_AUDIT_CSV|";

    private LiNRR_M10A0_2_RealCAD_Intake() {
        // Utility class.
    }

    public static Model run() throws Exception {
        final Path ccStep = Paths.get(runtimeString("CC_STEP"))
                .toAbsolutePath().normalize();
        final Path chamberStep = Paths.get(runtimeString("CHAMBER_STEP"))
                .toAbsolutePath().normalize();
        final Path outputMph = Paths.get(runtimeString("OUTPUT_MPH"))
                .toAbsolutePath().normalize();

        final String ccSha = runtimeString("CC_SHA256");
        final String chamberSha = runtimeString("CHAMBER_SHA256");
        final long ccBytes = runtimeLong("CC_BYTES");
        final long chamberBytes = runtimeLong("CHAMBER_BYTES");
        final String importMode = runtimeString("IMPORT_MODE")
                .trim().toUpperCase(Locale.ROOT);

        if (!importMode.equals("STRICT") && !importMode.equals("AUTO_REPAIR")) {
            throw new IllegalArgumentException(
                    "INVALID_IMPORT_MODE: expected STRICT or AUTO_REPAIR, got " + importMode);
        }

        System.out.println("RUNTIME_INPUT_CLASS=" + RUNTIME_CLASS);
        System.out.println("IMPORT_MODE=" + importMode);
        System.out.println("CC_STEP=" + ccStep);
        System.out.println("CHAMBER_STEP=" + chamberStep);
        System.out.println("OUTPUT_MPH=" + outputMph);

        ModelUtil.setDefaultGeometryKernel("cadps");

        final Model model = ModelUtil.create("Model");
        model.label("LiNRR_M10A0_2_real_cad_intake_" +
                importMode.toLowerCase(Locale.ROOT) + ".mph");
        model.comments(
                "M10A0.2 real-CAD intake. Two source STEP solids are imported in separate " +
                "CAD-kernel components. No assembly transform, fluid extraction, mesh, physics, " +
                "study, solver, or validation is included.");

        buildCadComponent(
                model,
                "comp_cc_raw",
                "Real CAD — current collector / gas flow field (raw source coordinates)",
                "geom_cc_raw",
                "imp_cc",
                ccStep,
                importMode);

        buildCadComponent(
                model,
                "comp_chamber_raw",
                "Real CAD — electrolyte chamber (raw source coordinates)",
                "geom_chamber_raw",
                "imp_chamber",
                chamberStep,
                importMode);

        emitAudit(
                model,
                ccStep,
                ccBytes,
                ccSha,
                chamberStep,
                chamberBytes,
                chamberSha,
                importMode);

        model.save(outputMph.toString());

        System.out.println("M10A0_2_JAVA_IMPORT=PASS");
        System.out.println("IMPORT_MODE=" + importMode);
        System.out.println("OUTPUT_MPH=" + outputMph);
        return model;
    }

    private static void buildCadComponent(
            Model model,
            String componentTag,
            String componentLabel,
            String geometryTag,
            String importTag,
            Path stepFile,
            String importMode) {

        model.component().create(componentTag, true);
        model.component(componentTag).label(componentLabel);

        final GeomSequence geom = model.component(componentTag).geom().create(geometryTag, 3);
        geom.label("Raw STEP import — no assembly transform");
        geom.lengthUnit("mm");
        geom.geomRep("cadps");

        geom.feature().create(importTag, "Import");
        geom.feature(importTag).label("Strict source STEP import");
        geom.feature(importTag).set("filename", stepFile.toString());
        geom.feature(importTag).set("unit", "source");

        geom.feature(importTag).set("keepsolid", "on");
        geom.feature(importTag).set("keepbnd", "on");
        geom.feature(importTag).set("keepfree", "off");
        geom.feature(importTag).set("knit", "solid");
        geom.feature(importTag).set("fillholes", "off");
        geom.feature(importTag).set("removeredundant", "off");
        geom.feature(importTag).set("simplify", "off");
        geom.feature(importTag).set("deletedetails", "off");
        geom.feature(importTag).set("healedges", "off");
        geom.feature(importTag).set("minimizetol", "off");
        geom.feature(importTag).set("importbodynames", "on");
        geom.feature(importTag).set("check", "on");

        if (importMode.equals("STRICT")) {
            geom.feature(importTag).set("fixerrors", "off");
        } else {
            geom.feature(importTag).set("fixerrors", "auto");
        }

        geom.feature(importTag).set("selresult", "on");
        geom.feature(importTag).set("selresultshow", "all");

        geom.feature(importTag).importData();
        geom.run();
        geom.check();
    }

    private static void emitAudit(
            Model model,
            Path ccStep,
            long ccBytes,
            String ccSha,
            Path chamberStep,
            long chamberBytes,
            String chamberSha,
            String importMode) {

        final List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {
                "record_type",
                "role",
                "component",
                "geometry",
                "object_name",
                "object_type",
                "cad_representation",
                "domains",
                "boundaries",
                "edges",
                "vertices",
                "xmin_mm",
                "xmax_mm",
                "ymin_mm",
                "ymax_mm",
                "zmin_mm",
                "zmax_mm",
                "span_x_mm",
                "span_y_mm",
                "span_z_mm",
                "source_file",
                "source_bytes",
                "source_sha256",
                "import_mode"
        });

        appendGeometryAudit(
                rows,
                model,
                "CURRENT_COLLECTOR_FLOW_FIELD",
                "comp_cc_raw",
                "geom_cc_raw",
                ccStep,
                ccBytes,
                ccSha,
                importMode);

        appendGeometryAudit(
                rows,
                model,
                "ELECTROLYTE_CHAMBER",
                "comp_chamber_raw",
                "geom_chamber_raw",
                chamberStep,
                chamberBytes,
                chamberSha,
                importMode);

        for (String[] row : rows) {
            final StringBuilder line = new StringBuilder(AUDIT_PREFIX);
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    line.append(',');
                }
                line.append(csv(row[i]));
            }
            System.out.println(line.toString());
        }
    }

    private static void appendGeometryAudit(
            List<String[]> rows,
            Model model,
            String role,
            String componentTag,
            String geometryTag,
            Path sourceFile,
            long sourceBytes,
            String sourceSha,
            String importMode) {

        final GeomSequence geom = model.component(componentTag).geom(geometryTag);
        final String[] objectNames = geom.objectNames();
        final double[] finalBox = geom.getBoundingBox();

        rows.add(auditRow(
                "FINALIZED_GEOMETRY",
                role,
                componentTag,
                geometryTag,
                "(finalized)",
                geom.getType(),
                Boolean.toString(geom.hasCadRep()),
                geom.getNDomains(),
                geom.getNBoundaries(),
                geom.getNEdges(),
                geom.getNVertices(),
                finalBox,
                sourceFile,
                sourceBytes,
                sourceSha,
                importMode));

        for (String objectName : objectNames) {
            final GeomObject object = geom.obj(objectName);
            final double[] box = object.getBoundingBox();
            rows.add(auditRow(
                    "GEOMETRY_OBJECT",
                    role,
                    componentTag,
                    geometryTag,
                    objectName,
                    object.getType(),
                    Boolean.toString(object.hasCadRep()),
                    object.getNDomains(),
                    object.getNBoundaries(),
                    object.getNEdges(),
                    object.getNVertices(),
                    box,
                    sourceFile,
                    sourceBytes,
                    sourceSha,
                    importMode));
        }

        System.out.println("CAD_AUDIT_ROLE=" + role);
        System.out.println("CAD_AUDIT_OBJECT_COUNT=" + objectNames.length);
        System.out.println("CAD_AUDIT_BBOX_MM=" + formatBox(finalBox));
        System.out.println("CAD_AUDIT_ENTITIES=" +
                "domains:" + geom.getNDomains() +
                ",boundaries:" + geom.getNBoundaries() +
                ",edges:" + geom.getNEdges() +
                ",vertices:" + geom.getNVertices());
    }

    private static String[] auditRow(
            String recordType,
            String role,
            String componentTag,
            String geometryTag,
            String objectName,
            String objectType,
            String cadRepresentation,
            int domains,
            int boundaries,
            int edges,
            int vertices,
            double[] box,
            Path sourceFile,
            long sourceBytes,
            String sourceSha,
            String importMode) {

        validateBoundingBox(box);
        return new String[] {
                recordType,
                role,
                componentTag,
                geometryTag,
                objectName,
                objectType,
                cadRepresentation,
                Integer.toString(domains),
                Integer.toString(boundaries),
                Integer.toString(edges),
                Integer.toString(vertices),
                number(box[0]),
                number(box[1]),
                number(box[2]),
                number(box[3]),
                number(box[4]),
                number(box[5]),
                number(box[1] - box[0]),
                number(box[3] - box[2]),
                number(box[5] - box[4]),
                sourceFile.toString(),
                Long.toString(sourceBytes),
                sourceSha,
                importMode
        };
    }

    private static void validateBoundingBox(double[] box) {
        if (box == null || box.length != 6) {
            throw new IllegalStateException("INVALID_3D_BOUNDING_BOX");
        }
        for (double value : box) {
            if (!Double.isFinite(value)) {
                throw new IllegalStateException("NONFINITE_BOUNDING_BOX");
            }
        }
    }

    private static String formatBox(double[] box) {
        validateBoundingBox(box);
        return "[" +
                number(box[0]) + "," + number(box[1]) + ";" +
                number(box[2]) + "," + number(box[3]) + ";" +
                number(box[4]) + "," + number(box[5]) + "]";
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.12g", value);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        final String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
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
