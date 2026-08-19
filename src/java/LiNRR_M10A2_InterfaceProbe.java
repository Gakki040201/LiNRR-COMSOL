import com.comsol.model.Model;
import com.comsol.model.physics.Physics;
import com.comsol.model.physics.PhysicsFeature;
import com.comsol.model.util.ModelUtil;

import java.util.Locale;

/** Read-only inspection of installed COMSOL 6.4 application models relevant to M10A2. */
public final class LiNRR_M10A2_InterfaceProbe {
    private static final String RUNTIME_CLASS = "LiNRR_M10A2_ProbeRuntime";

    private LiNRR_M10A2_InterfaceProbe() {}

    public static void main(String[] args) throws Exception {
        inspect("PipeExample", runtime("PIPE_EXAMPLE"));
        inspect("PorousExample", runtime("POROUS_EXAMPLE"));
        inspect("WettingExample", runtime("WETTING_EXAMPLE"));
        System.out.println("M10A2_INTERFACE_PROBE=PASS");
    }

    private static void inspect(String tag, String path) throws Exception {
        final Model model = ModelUtil.load(tag, path);
        try {
            System.out.println("M10A2_APP_MODEL|tag=" + tag + "|path=" + path + "|used_products="
                + clean(String.join(";", model.getUsedProducts())));
            for (String component : model.component().tags()) {
                System.out.println("M10A2_APP_COMPONENT|model=" + tag + "|component=" + component);
                for (String physicsTag : model.component(component).physics().tags()) {
                    final Physics physics = model.component(component).physics(physicsTag);
                    System.out.println("M10A2_APP_PHYSICS|model=" + tag + "|component=" + component
                        + "|tag=" + physicsTag + "|type=" + physics.getType() + "|geom=" + physics.geom()
                        + "|features=" + String.join(";", physics.feature().tags()));
                    for (String featureTag : physics.feature().tags()) {
                        printFeature(tag, physicsTag, featureTag, physics.feature(featureTag), 0);
                    }
                }
                for (String coupling : model.component(component).multiphysics().tags()) {
                    System.out.println("M10A2_APP_MULTIPHYSICS|model=" + tag + "|component=" + component
                        + "|tag=" + coupling + "|type=" + model.component(component).multiphysics(coupling).getType()
                        + "|properties=" + String.join(";", model.component(component).multiphysics(coupling).properties()));
                }
            }
            for (String study : model.study().tags()) {
                System.out.println("M10A2_APP_STUDY|model=" + tag + "|tag=" + study + "|features="
                    + String.join(";", model.study(study).feature().tags()));
            }
        } finally {
            ModelUtil.remove(tag);
        }
    }

    private static void printFeature(String modelTag, String physicsTag, String path, PhysicsFeature feature, int depth) {
        final String properties = String.join(";", feature.properties());
        String named = "";
        try { named = String.join(";", feature.selection().named()); } catch (Throwable ignored) {}
        System.out.println("M10A2_APP_FEATURE|model=" + modelTag + "|physics=" + physicsTag
            + "|tag=" + path + "|type=" + feature.getType() + "|selection=" + named
            + "|properties=" + properties);
        for (String property : feature.properties()) {
            if (property.equals("StudyStep") || property.equals("showPhysicsSymbols") || property.equals("pairContrib")) continue;
            try {
                final String[] value = feature.getStringArray(property);
                if (value != null && value.length > 0) {
                    System.out.println("M10A2_APP_PROPERTY|model=" + modelTag + "|physics=" + physicsTag
                        + "|tag=" + path + "|name=" + property + "|value=" + clean(String.join(";", value)));
                }
            } catch (Throwable ignored) {}
        }
        if (depth == 0) {
            final String[][] expressions = feature.featureInfo("info").getInfoTable("Expression", "recursive", "all");
            int emitted = 0;
            for (String[] row : expressions) {
                final String joined = String.join("\t", row);
                final String lower = joined.toLowerCase(Locale.ROOT);
                if ((lower.contains("satur") || lower.contains("capillar") || lower.contains("permeab")
                    || lower.contains("pressure") || lower.contains("flow rate")) && emitted < 30) {
                    System.out.println("M10A2_APP_EXPR|model=" + modelTag + "|physics=" + physicsTag
                        + "|feature=" + path + "|row=" + clean(joined));
                    emitted++;
                }
            }
        }
        if (depth < 2) {
            for (String child : feature.feature().tags()) {
                printFeature(modelTag, physicsTag, path + "/" + child, feature.feature(child), depth + 1);
            }
        }
    }

    private static String runtime(String name) {
        try {
            return ((String) Class.forName(RUNTIME_CLASS).getField(name).get(null)).trim();
        } catch (Exception exception) {
            throw new IllegalStateException("RUNTIME_INPUT_MISSING: " + name, exception);
        }
    }

    private static String clean(String value) {
        return value.replace('\n', ' ').replace('\r', ' ').replace('|', '/');
    }
}
