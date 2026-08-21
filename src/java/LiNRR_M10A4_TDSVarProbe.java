import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_TDSVarProbe {
  private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4TDSVarProbe", MPH);
    try {
      String c = "comp_species_liq_real";
      String[][] table = m.component(c).physics("tds_tracer").featureInfo("info").getInfoTable("Expression", "recursive", "all");
      System.out.println("EXPR_ROWS=" + (table==null?-1:table.length));
      if (table != null) for (String[] row : table) {
        StringBuilder sb = new StringBuilder();
        for (String cell : row) sb.append(cell == null ? "<null>" : cell).append('|');
        String s = sb.toString();
        if (s.contains("tflux") || s.contains("dflux") || s.contains("cflux") || s.contains("mflux") || s.contains("ntflux")) System.out.println("ROW|" + s);
      }
    } finally { ModelUtil.remove("M10A4TDSVarProbe"); }
  }
}
