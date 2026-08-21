import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_NernstPlanckFeatureProbe {
    private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
    public static void main(String[] args) throws Exception {
        Model m = ModelUtil.load("M10A4NPFeatureProbe", MPH);
        try {
            String c = "comp_species_liq_real";
            m.component(c).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
            String[] ftags = m.component(c).physics("np_probe").feature().tags();
            System.out.println("FEATURES=" + String.join(",", ftags));
            for (String ft : ftags) {
                System.out.println("FEATURE|" + ft + "|type=" + m.component(c).physics("np_probe").feature(ft).getType() + "|label=" + m.component(c).physics("np_probe").feature(ft).label());
            }
            String[][] props = m.component(c).physics("np_probe").feature("cdm1").featureInfo("info").getInfoTable("Property", "recursive", "all");
            for (String[] row : props) {
                StringBuilder sb = new StringBuilder();
                for (String cell : row) sb.append(cell == null ? "<null>" : cell).append('|');
                System.out.println("PROP|" + sb);
            }
        } finally { ModelUtil.remove("M10A4NPFeatureProbe"); }
    }
}