import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Read-only runtime audit of all frozen PlotGroups followed by canonical-field readability. */
public final class LiNRR_M10A4_PlotIntegrityAudit {
    private LiNRR_M10A4_PlotIntegrityAudit() {}

    public static void main(String[] args) throws Exception {
        Path evidence = Paths.get(LiNRR_M10A4_VisualizationCommon.runtime("EVIDENCE_DIR"));
        Path roleMatrix = Paths.get(LiNRR_M10A4_VisualizationCommon.runtime("ROLE_MATRIX"));
        Files.createDirectories(evidence);
        Model model = ModelUtil.load("M10A4VizInputAudit",
            LiNRR_M10A4_VisualizationCommon.runtime("INPUT_MPH"));
        try {
            Map<String, String[]> roles = readRoles(roleMatrix);
            AuditSummary summary = auditPlots(model, roles,
                evidence.resolve("PLOT_INTEGRITY_INPUT.csv"));
            System.out.println("INPUT_PLOTGROUP_COUNT=" + summary.total);
            System.out.println("INPUT_PLOT_RUNTIME_PASS_COUNT=" + summary.pass);
            System.out.println("INPUT_PLOT_WARNING_COUNT=" + summary.warnings);
            System.out.println("INPUT_PLOT_EXCEPTION_COUNT=" + summary.exceptions);
            for (String role : new String[]{"MAIN_TEACHING", "MAIN_SUPPORT", "SI_SUPPORT", "INTERMEDIATE_SUPPORT"}) {
                int[] count = summary.byRole.get(role);
                if (count == null) count = new int[4];
                System.out.println("INPUT_PLOT_ROLE_COUNT|role=" + role + "|total=" + count[0]
                    + "|pass=" + count[1] + "|warning=" + count[2] + "|exception=" + count[3]);
            }
            if (summary.total != 68) {
                System.out.println("INPUT_PLOTGROUP_STATIC_DELTA=" + (summary.total - 68));
            }

            int[] reaction = model.component(LiNRR_M10A4_VisualizationCommon.REACTION_COMPONENT)
                .selection(LiNRR_M10A4_VisualizationCommon.REACTION_SELECTION).entities(2);
            if (reaction == null || reaction.length == 0) {
                throw new IllegalStateException("REACTION_PLANE_SELECTION_EMPTY");
            }
            List<LiNRR_M10A4_VisualizationCommon.FieldValue> values =
                LiNRR_M10A4_VisualizationCommon.evaluateAll(model);
            LiNRR_M10A4_VisualizationCommon.writeCanonical(
                evidence.resolve("CANONICAL_FIELD_READABILITY.csv"), values);
            System.out.println("REACTION_PLANE_SELECTION=PASS");
            System.out.println("REACTION_PLANE_DIMENSION=2");
            System.out.println("REACTION_PLANE_ENTITY_COUNT=" + reaction.length);
            System.out.println("CANONICAL_FIELD_READABILITY=PASS");
            System.out.println("PLOT_INTEGRITY_AUDIT_SOLVE_TRIGGERED=FALSE");
        } finally {
            ModelUtil.remove("M10A4VizInputAudit");
        }
    }

    private static AuditSummary auditPlots(Model model, Map<String, String[]> roles, Path output) throws Exception {
        List<String[]> rows = new ArrayList<String[]>();
        AuditSummary summary = new AuditSummary();
        for (String plot : model.result().tags()) {
            summary.total++;
            String[] role = roles.get(plot);
            String section = role == null ? "UNCLASSIFIED" : role[0];
            String roleName = role == null ? "UNCLASSIFIED" : role[1];
            String data = safeProperty(model, plot, "data");
            List<String> children = new ArrayList<String>();
            List<String> expressions = new ArrayList<String>();
            List<String> units = new ArrayList<String>();
            boolean childSelection = false;
            for (String child : model.result(plot).feature().tags()) {
                String type = model.result(plot).feature(child).getType();
                children.add(child + ":" + type);
                expressions.add(child + "=" + safeChildProperty(model, plot, child, "expr"));
                units.add(child + "=" + safeChildProperty(model, plot, child, "unit"));
                try {
                    if (model.result(plot).feature(child).feature().hasTag("sel")) childSelection = true;
                } catch (Throwable ignored) {}
                if ("Streamline".equals(type)) childSelection = true;
            }
            boolean runOk = false;
            boolean warning = false;
            String warningText = "";
            String exceptionClass = "";
            String exceptionMessage = "";
            try {
                model.result(plot).run();
                runOk = true;
                warning = model.result(plot).hasWarning();
                if (warning) warningText = "hasWarning=true; warning text not exposed through stable audited API";
            } catch (Throwable exception) {
                exceptionClass = exception.getClass().getName();
                exceptionMessage = LiNRR_M10A4_VisualizationCommon.clean(exception.getMessage());
            }
            String status;
            if (!runOk) {
                summary.exceptions++;
                status = "EXCEPTION";
            } else if (warning) {
                summary.warnings++;
                status = "WARNING";
            } else {
                summary.pass++;
                status = "PASS";
            }
            int[] roleCount = summary.byRole.get(roleName);
            if (roleCount == null) {
                roleCount = new int[4];
                summary.byRole.put(roleName, roleCount);
            }
            roleCount[0]++;
            if ("PASS".equals(status)) roleCount[1]++;
            else if ("WARNING".equals(status)) roleCount[2]++;
            else roleCount[3]++;
            rows.add(new String[]{plot, model.result(plot).getType(), model.result(plot).label(), section,
                roleName, data, join(children), join(expressions), join(units), Boolean.toString(childSelection),
                Boolean.toString(runOk), Boolean.toString(warning), warningText, exceptionClass,
                exceptionMessage, status});
            System.out.println("INPUT_PLOT_RUNTIME|tag=" + plot + "|status=" + status
                + "|warning=" + warning + "|exception=" + exceptionClass);
        }
        LiNRR_M10A4_VisualizationCommon.writeCsv(output,
            "plot_tag,plot_type,label,section,role,dataset,child_features,expressions,units,child_selection_exists,plot_run_ok,has_warning,warning_text,exception_class,exception_message,status",
            rows);
        return summary;
    }

    private static Map<String, String[]> readRoles(Path path) throws Exception {
        Map<String, String[]> roles = new HashMap<String, String[]>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            List<String> columns = LiNRR_M10A4_VisualizationCommon.parseCsv(lines.get(i));
            if (columns.size() >= 3) roles.put(columns.get(0), new String[]{columns.get(1), columns.get(2)});
        }
        return roles;
    }

    private static String safeProperty(Model model, String plot, String property) {
        try {
            String value = model.result(plot).getString(property);
            return value == null ? "" : value;
        } catch (Throwable ignored) { return ""; }
    }

    private static String safeChildProperty(Model model, String plot, String child, String property) {
        try {
            String[] values = model.result(plot).feature(child).getStringArray(property);
            if (values != null && values.length > 0) return join(values);
        } catch (Throwable ignored) {}
        try {
            String value = model.result(plot).feature(child).getString(property);
            return value == null ? "" : value;
        } catch (Throwable ignored) { return ""; }
    }

    private static String join(List<String> values) { return join(values.toArray(new String[values.size()])); }

    private static String join(String[] values) {
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) joined.append(';');
            joined.append(LiNRR_M10A4_VisualizationCommon.clean(values[i]));
        }
        return joined.toString();
    }

    public static final class AuditSummary {
        int total;
        int pass;
        int warnings;
        int exceptions;
        final Map<String, int[]> byRole = new HashMap<String, int[]>();

        public AuditSummary() {}
    }
}
