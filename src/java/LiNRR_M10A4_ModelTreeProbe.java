import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/** Read-only M10A4 A3 model tree probe focused on liquid and datasets. */
public final class LiNRR_M10A4_ModelTreeProbe {
    private static final String MPH =
        "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";

    private LiNRR_M10A4_ModelTreeProbe() {}

    public static void main(String[] args) throws Exception {
        Model m = ModelUtil.load("M10A4TreeProbe", MPH);
        try {
            System.out.println("MODEL_LABEL=" + m.label());
            System.out.println("COMPONENTS=" + String.join(",", m.component().tags()));
            for (String c : m.component().tags()) {
                try {
                    String[] ph = m.component(c).physics().tags();
                    System.out.println("COMPONENT|" + c + "|label=" + m.component(c).label()
                        + "|geom=" + String.join(",", m.component(c).geom().tags())
                        + "|physics=" + String.join(",", ph));
                    for (String p : ph) {
                        try {
                            String type = m.component(c).physics(p).getType();
                            String[] fields = m.component(c).physics(p).field().tags();
                            System.out.println("PHYSICS|" + c + "|" + p + "|type=" + type + "|fields=" + String.join(";", fields));
                        } catch (Throwable e) {
                            System.out.println("PHYSICS|" + c + "|" + p + "|error=" + e);
                        }
                    }
                    for (String v : m.component(c).variable().tags()) {
                        String[] names = m.component(c).variable(v).varnames();
                        System.out.println("VARIABLE|" + c + "|" + v + "|names=" + String.join(";", names));
                    }
                } catch (Throwable e) {
                    System.out.println("COMPONENT|" + c + "|error=" + e);
                }
            }
            for (String c : new String[]{"comp_electrolyte_flow", "comp_species_liq_real"}) {
                if (!m.component().hasTag(c)) continue;
                System.out.println("SELECTIONS_COMPONENT=" + c);
                for (String s : m.component(c).selection().tags()) {
                    int d3 = m.component(c).selection(s).entities(3).length;
                    int d2 = m.component(c).selection(s).entities(2).length;
                    System.out.println("SELECTION|" + c + "|" + s + "|label=" + m.component(c).selection(s).label() + "|d3=" + d3 + "|d2=" + d2);
                }
            }
            System.out.println("DATASETS=" + String.join(",", m.result().dataset().tags()));
            for (String d : m.result().dataset().tags()) {
                try {
                    System.out.println("DATASET|" + d + "|label=" + m.result().dataset(d).label());
                } catch (Throwable e) {
                    System.out.println("DATASET|" + d + "|error=" + e);
                }
            }
            System.out.println("STUDIES=" + String.join(",", m.study().tags()));
            System.out.println("SOLVERS=" + String.join(",", m.sol().tags()));
            System.out.println("RESULTS=" + String.join(",", m.result().tags()));
        } finally {
            ModelUtil.remove("M10A4TreeProbe");
        }
    }
}