import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_NernstPlanckInfoProbe {
    private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
    public static void main(String[] args) throws Exception {
        Model m = ModelUtil.load("M10A4NernstPlanckInfoProbe", MPH);
        try {
            String c = "comp_species_liq_real";
            m.component(c).physics().create("np_probe", "NernstPlanck", "geom_electrolyte_fluid1");
            String[] tags = m.component(c).physics("np_probe").field().tags();
            System.out.println("FIELD_TAGS=" + String.join(",", tags));
            for (String t : tags) {
                Object f = m.component(c).physics("np_probe").field(t);
                System.out.println("FIELD|" + t + "|class=" + (f == null ? "null" : f.getClass().getName()));
                for (java.lang.reflect.Method meth : f.getClass().getMethods()) {
                    if (meth.getParameterCount() == 0) {
                        try { Object v = meth.invoke(f); if (v != null && (meth.getName().equals("field") || meth.getName().equals("component") || meth.getName().equals("fieldname") || meth.getName().equals("tag"))) System.out.println("FIELD_METHOD|" + t + "|" + meth.getName() + "|" + (v instanceof String[] ? String.join(",", (String[]) v) : v)); } catch (Throwable ignore) {}
                    }
                }
            }
            String[][] table = m.component(c).physics("np_probe").featureInfo("info").getInfoTable("Expression", "recursive", "all");
            for (String[] row : table) {
                StringBuilder sb = new StringBuilder();
                for (String cell : row) sb.append(cell == null ? "<null>" : cell).append('|');
                System.out.println("ROW|" + sb);
            }
        } finally { ModelUtil.remove("M10A4NernstPlanckInfoProbe"); }
    }
}