import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_FeatureInfoProbe {
    private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
    public static void main(String[] args) throws Exception {
        Model m = ModelUtil.load("M10A4FeatureInfoProbe", MPH);
        try {
            String c = "comp_species_liq_real";
            m.component(c).physics().create("cd_probe", "PrimaryCurrentDistribution", "geom_electrolyte_fluid1");
            m.component(c).physics("cd_probe").selection().named("m10a3_sel_dom_electrolyte_fluid");
            String[][] table = m.component(c).physics("cd_probe").featureInfo("info").getInfoTable("Expression", "recursive", "all");
            for (String[] row : table) {
                StringBuilder sb = new StringBuilder();
                for (String cell : row) sb.append(cell == null ? "<null>" : cell).append('|');
                System.out.println("ROW|" + sb);
            }
        } finally { ModelUtil.remove("M10A4FeatureInfoProbe"); }
    }
}