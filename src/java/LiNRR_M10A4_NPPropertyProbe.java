import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_NPPropertyProbe {
    private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
    public static void main(String[] args) throws Exception {
        Model m = ModelUtil.load("M10A4NPPropertyProbe", MPH);
        try {
            String c = "comp_species_liq_real";
            m.component(c).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
            String[][] props = m.component(c).physics("np_probe").featureInfo("info").getInfoTable("Property", "recursive", "all");
            System.out.println("PROP_ROW_COUNT=" + (props == null ? -1 : props.length));
            if (props != null) for (String[] row : props) {
                StringBuilder sb = new StringBuilder();
                for (String cell : row) sb.append(cell == null ? "<null>" : cell).append('|');
                System.out.println("PROP|" + sb);
            }
            String[][] expr = m.component(c).physics("np_probe").featureInfo("info").getInfoTable("Expression", "recursive", "all");
            System.out.println("EXPR_ROW_COUNT=" + (expr == null ? -1 : expr.length));
            for (String[] row : expr) {
                StringBuilder sb = new StringBuilder();
                for (String cell : row) sb.append(cell == null ? "<null>" : cell).append('|');
                String s = sb.toString();
                if (s.contains("npe.D") || s.contains("npe.um") || s.contains("npe.z") || s.contains("npe.c1") || s.contains("npe.c2") || s.contains("npe.V")) System.out.println("EXPR|" + s);
            }
        } finally { ModelUtil.remove("M10A4NPPropertyProbe"); }
    }
}