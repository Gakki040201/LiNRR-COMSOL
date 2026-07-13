import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.io.IOException;

/**
 * M00: minimal COMSOL automation-chain test.
 *
 * Creates a parameterized 2D electrolyte rectangle, builds a mesh,
 * and saves an editable MPH file. No physics is added at this stage.
 */
public final class LiNRR_M00_Geometry {

    private LiNRR_M00_Geometry() {
        // Utility class.
    }

    public static Model run() throws IOException {
        Model model = ModelUtil.create("Model");
        model.label("LiNRR_M00_geometry.mph");

        defineParameters(model);
        buildGeometry(model);
        buildMesh(model);

        model.save("models/generated/LiNRR_M00_geometry.mph");
        return model;
    }

    private static void defineParameters(Model model) {
        model.param().set("Lcell", "55[mm]", "Effective electrolyte-channel length");
        model.param().set("Hcell", "4[mm]", "Electrolyte-layer thickness");
        model.param().set("Wcell", "55[mm]", "Out-of-plane active width");
        model.param().set("T0", "298.15[K]", "Operating temperature");
    }

    private static void buildGeometry(Model model) {
        model.component().create("comp1", true);
        model.component("comp1").label("Continuous-flow Li-NRR cell");

        model.component("comp1").geom().create("geom1", 2);
        model.component("comp1").geom("geom1").lengthUnit("mm");
        model.component("comp1").geom("geom1").create("r1", "Rectangle");
        model.component("comp1").geom("geom1").feature("r1")
             .set("size", new String[] {"Lcell", "Hcell"});
        model.component("comp1").geom("geom1").run();
    }

    private static void buildMesh(Model model) {
        model.component("comp1").mesh().create("mesh1");
        model.component("comp1").mesh("mesh1").autoMeshSize(3);
        model.component("comp1").mesh("mesh1").run();
    }

    public static void main(String[] args) throws IOException {
        run();
    }
}
