import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Independent JVM/COMSOL reload, editable-tree, and forbidden-feature audit. */
public final class LiNRR_M03A_3_LoadCheck {
    private static final String[] FORBIDDEN = {
        "SecondaryCurrentDistribution", "TertiaryCurrentDistribution",
        "ElectrodeReaction", "ButlerVolmer", "ExchangeCurrent", "HOR", "HER",
        "LiPlating", "LiStripping", "SEI"
    };

    private LiNRR_M03A_3_LoadCheck() {}

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(requireRunInput("LINRR_PROJECT_ROOT")).toAbsolutePath().normalize();
        Path mph = root.resolve("models/generated/LiNRR_M03A_3_prescribed_current_coupling.mph");
        if (!Files.isRegularFile(mph) || Files.size(mph) <= 0L)
            throw new IllegalStateException("M03A.3 MPH missing or empty: " + mph);
        Model model = ModelUtil.load("M033ReloadAudit", mph.toString());
        requireOne(model, "sel_electrolyte", 2);
        requireOne(model, "sel_inlet", 1);
        requireOne(model, "sel_outlet", 1);
        requireOne(model, "sel_anode_wall", 1);
        requireOne(model, "sel_cathode_wall", 1);
        requireTag(model.component("comp1").physics().tags(), "spf");
        requireTag(model.component("comp1").physics().tags(), "tds");
        requireTag(model.component("comp1").physics().tags(), "cd");
        requireTag(model.component("comp1").physics("cd").feature().tags(), "current_anode_M033");
        requireTag(model.component("comp1").physics("cd").feature().tags(), "potential_cathode_M033");
        requireTag(model.study().tags(), "std_audit");
        requireFinite(model, "m033_i_cathode");
        requireFinite(model, "m033_n2_cath");
        requireFinite(model, "m033_nh3_cath");
        requireFinite(model, "m033_n2_imposed_cath");
        requireFinite(model, "m033_nh3_imposed_cath");

        for (String physicsTag : model.component("comp1").physics().tags()) {
            String physicsType = model.component("comp1").physics(physicsTag).getType();
            String physicsLabel = model.component("comp1").physics(physicsTag).label();
            auditText("physics tag", physicsTag);
            auditText("physics type", physicsType);
            auditText("physics label", physicsLabel);
            System.out.println("M03A3_TREE|physics|" + physicsTag + "|" + physicsType + "|" + safe(physicsLabel));
            for (String featureTag : model.component("comp1").physics(physicsTag).feature().tags()) {
                String featureType = model.component("comp1").physics(physicsTag).feature(featureTag).getType();
                String featureLabel = model.component("comp1").physics(physicsTag).feature(featureTag).label();
                auditText("feature tag", featureTag);
                auditText("feature type", featureType);
                auditText("feature label", featureLabel);
                System.out.println("M03A3_TREE|feature|" + physicsTag + "/" + featureTag + "|" +
                    featureType + "|" + safe(featureLabel));
            }
        }
        if (!"PrimaryCurrentDistribution".equals(
            model.component("comp1").physics("cd").getType()))
            throw new IllegalStateException("cd is not PrimaryCurrentDistribution.");
        if (!"GeneralInwardFlux".equals(model.component("comp1").physics("tds")
            .feature("flux_cathode").getString("FluxType")))
            throw new IllegalStateException("Cathode species feature is not General Inward Flux.");
        System.out.println("M03A3_RELOAD|PASS|editable MPH, selections, retained solution, actual physics tree, and feature types audited");
    }

    private static void auditText(String kind, String text) {
        String value = text == null ? "" : text;
        for (String forbidden : FORBIDDEN) {
            if (value.toLowerCase().contains(forbidden.toLowerCase()))
                throw new IllegalStateException("Forbidden " + kind + " contains " + forbidden + ": " + value);
        }
    }

    private static void requireOne(Model model, String selection, int dimension) {
        int count = model.component("comp1").selection(selection).entities(dimension).length;
        if (count != 1) throw new IllegalStateException("FAILED_SELECTION_MAPPING after reload: " +
            selection + " count=" + count);
    }

    private static void requireTag(String[] tags, String wanted) {
        for (String tag : tags) if (wanted.equals(tag)) return;
        throw new IllegalStateException("Missing editable tag after reload: " + wanted);
    }

    private static void requireFinite(Model model, String numerical) {
        double[][] values = model.result().numerical(numerical).getReal();
        if (values == null || values.length == 0 || values[0].length == 0 ||
            !Double.isFinite(values[0][0]))
            throw new IllegalStateException("Retained result unavailable: " + numerical);
    }

    private static String safe(String text) {
        return (text == null ? "" : text).replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }

    private static String requireRunInput(String name) {
        try {
            Class<?> bridge = Class.forName("LiNRR_M03A_3_RunInputs");
            Object value = bridge.getMethod("get", String.class).invoke(null, name);
            if (value != null && !value.toString().trim().isEmpty()) return value.toString();
        } catch (Throwable ignored) {
            // Fall through for an ordinary JVM.
        }
        try {
            String value = System.getenv(name);
            if (value != null && !value.trim().isEmpty()) return value;
        } catch (SecurityException blocked) {
            throw new IllegalStateException(name + " unavailable; run-input bridge missing.", blocked);
        }
        throw new IllegalStateException(name + " is required.");
    }
}
