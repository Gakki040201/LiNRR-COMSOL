import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_PhysicsTypeProbe {
    private static final String MPH = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph";
    public static void main(String[] args) throws Exception {
        Model m = ModelUtil.load("M10A4PhysicsTypeProbe", MPH);
        try {
            String c = "comp_species_liq_real";
            String[] types = new String[]{"TertiaryCurrentDistribution", "NernstPlanck", "SecondaryCurrentDistribution", "DilutedSpecies", "TransportOfDilutedSpecies"};
            for (String type : types) {
                String tag = "probe_" + type;
                try {
                    m.component(c).physics().create(tag, type, "geom_electrolyte_fluid1");
                    System.out.println("PHYSICS_TYPE_OK|" + type + "|tag=" + tag + "|actualType=" + m.component(c).physics(tag).getType());
                    m.component(c).physics().remove(tag);
                } catch (Throwable e) {
                    System.out.println("PHYSICS_TYPE_FAIL|" + type + "|" + e.getMessage());
                }
            }
        } finally { ModelUtil.remove("M10A4PhysicsTypeProbe"); }
    }
}