import com.comsol.model.Model;
import com.comsol.model.physics.Physics;
import com.comsol.model.physics.PhysicsFeature;
import com.comsol.model.util.ModelUtil;

import java.lang.reflect.Method;
import java.util.Arrays;

/** Read-only API and installed-model probe for M10A2 implementation choices. */
public final class LiNRR_M10A2_ApiProbe {
    private static final String RUNTIME_CLASS = "LiNRR_M10A2_ApiProbeRuntime";

    private LiNRR_M10A2_ApiProbe() {}

    public static void main(String[] args) throws Exception {
        final Model pipe = ModelUtil.load("PipeApiProbe", runtime("PIPE_EXAMPLE"));
        try {
            for (String component : pipe.component().tags()) {
                for (String physicsTag : pipe.component(component).physics().tags()) {
                    final Physics physics = pipe.component(component).physics(physicsTag);
                    if (!"FlowInPipes".equals(physics.getType())) continue;
                    for (String featureTag : physics.feature().tags()) {
                        final PhysicsFeature feature = physics.feature(featureTag);
                        if (!"Inlet".equals(feature.getType())) continue;
                        System.out.println("M10A2_PIPE_INLET|tag=" + featureTag
                            + "|spec=" + feature.getString("spec")
                            + "|qv0=" + feature.getString("qv0")
                            + "|qm0=" + feature.getString("qm0")
                            + "|selection=" + Arrays.toString(feature.selection().entities()));
                    }
                }
            }
            printMethods("COMPONENT_LIST", pipe.component().getClass());
            printMethods("COMPONENT", pipe.component(pipe.component().tags()[0]).getClass());
            printMethods("GEOMETRY_LIST", pipe.component(pipe.component().tags()[0]).geom().getClass());

            final String original = pipe.component().tags()[0];
            try {
                pipe.component().copy("comp_copy_probe", original);
                System.out.println("M10A2_COMPONENT_COPY|source=" + original + "|new=comp_copy_probe|status=PASS");
            } catch (Throwable error) {
                System.out.println("M10A2_COMPONENT_COPY|source=" + original + "|status=UNAVAILABLE|class="
                    + error.getClass().getName() + "|message=" + clean(error.getMessage()));
            }

            final Model wetting = ModelUtil.load("WettingApiProbe", runtime("WETTING_EXAMPLE"));
            try {
                try {
                    wetting.component().copyTo("comp_wetting_copy_probe", wetting.component().tags()[0], "PipeApiProbe");
                    System.out.println("M10A2_COMPONENT_COPY_TO|source_model=WettingApiProbe|destination_model=PipeApiProbe"
                        + "|new=comp_wetting_copy_probe|status="
                        + (pipe.component().hasTag("comp_wetting_copy_probe") ? "PASS" : "FAIL"));
                } catch (Throwable error) {
                    System.out.println("M10A2_COMPONENT_COPY_TO|status=UNAVAILABLE|class="
                        + error.getClass().getName() + "|message=" + clean(error.getMessage()));
                }
            } finally {
                ModelUtil.remove("WettingApiProbe");
            }
        } finally {
            ModelUtil.remove("PipeApiProbe");
        }
        System.out.println("M10A2_API_PROBE=PASS");
    }

    private static void printMethods(String label, Class<?> type) {
        for (Method method : type.getMethods()) {
            final String name = method.getName().toLowerCase();
            if (name.contains("duplicate") || name.contains("copy") || name.equals("create")) {
                System.out.println("M10A2_API_METHOD|owner=" + label + "|declaring="
                    + method.getDeclaringClass().getName() + "|method=" + method.toGenericString());
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
        return String.valueOf(value).replace('\n', ' ').replace('\r', ' ').replace('|', '/');
    }
}
