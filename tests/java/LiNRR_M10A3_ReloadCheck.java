import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/** Independent M10A3 reload probe: loads the accepted MPH, evaluates existing result tags, and does not run studies. */
public final class LiNRR_M10A3_ReloadCheck {
    private LiNRR_M10A3_ReloadCheck() {}

    public static void main(String[] args) throws Exception {
        Path mph;
        if (args != null && args.length == 1) {
            mph = Paths.get(args[0]).toAbsolutePath().normalize();
        } else {
            mph = Paths.get("F:/LiNRR_COMSOL/worktrees/LiNRR_M10_PREA4/models/generated/LiNRR_M10A3_real_species_transport.mph").toAbsolutePath().normalize();
        }
        Model model = ModelUtil.load("M10A3Reload", mph.toString());
        try {
            String[] plots = {
                "pg00_physical_cell",
                "pg_n2_velocity",
                "pg_n2_pressure",
                "pg_h2_velocity",
                "pg_h2_pressure",
                "pg_n2_gas_conc",
                "pg_n2_dissolved_real",
                "pg_n2_availability",
                "pg_h2_gas_conc",
                "pg_h2_availability",
                "pg_donor_real",
                "pg_nh3_real",
                "pg_tracer_real",
                "pg_nh3_downstream",
                "pg_nh3_breakthrough",
                "pg_nh3_inventory"
            };
            for (String tag : plots) {
                if (!model.result().hasTag(tag)) {
                    throw new IllegalStateException("REQUIRED_RESULT_MISSING " + tag);
                }
                model.result(tag).run();
                if (model.result(tag).hasWarning()) {
                    throw new IllegalStateException("REQUIRED_RESULT_WARNING " + tag);
                }
                System.out.println("M10A3_RELOAD_PLOT|" + tag + "|PASS");
            }
            String[] tables = {"tbl_rtd_e", "tbl_rtd_f"};
            for (String tag : tables) {
                if (!model.result().table().hasTag(tag)) {
                    throw new IllegalStateException("REQUIRED_RTD_TABLE_MISSING " + tag);
                }
                String[][] data = model.result().table(tag).getTableData(false);
                if (data.length < 3) {
                    throw new IllegalStateException("REQUIRED_RTD_TABLE_EMPTY " + tag);
                }
                for (String[] row : data) {
                    for (String value : row) {
                        if (value == null) {
                            throw new IllegalStateException("REQUIRED_RTD_TABLE_NULL " + tag);
                        }
                        String lower = value.toLowerCase(Locale.ROOT);
                        if (lower.contains("undefined") || lower.contains("nan") || lower.contains("inf")) {
                            throw new IllegalStateException("REQUIRED_RTD_TABLE_NONFINITE " + tag);
                        }
                    }
                }
                System.out.println("M10A3_RELOAD_TABLE|" + tag + "|rows=" + data.length + "|PASS");
            }
            System.out.println("M10A3_INDEPENDENT_RELOAD=PASS");
        } finally {
            ModelUtil.remove("M10A3Reload");
        }
    }
}
