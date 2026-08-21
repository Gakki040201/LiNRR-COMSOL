import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Derived electrical-loss postprocessing only. No Heat Transfer physics or thermal feedback. */
public final class LiNRR_M10A4_ElectricalLoss {
    private static final String ROOT = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED";
    private static final String RUN = ROOT + "\\runs\\M10A4\\20260820_121258";
    private static final String INPUT = RUN + "\\checkpoint_A4E_spatial_colimitation.mph";
    private static final String CHECKPOINT = RUN + "\\checkpoint_A4_electrical_loss.mph";
    private static final String TABLE = ROOT + "\\results\\tables\\M10A4_electrical_loss_diagnostics.csv";
    private static final String COMP = "comp_species_liq_real";
    private static final String GEOM = "geom_electrolyte_fluid1";
    private static final String DOM = "m10a3_sel_dom_electrolyte_fluid";
    private static final String CATHODE = "m10a3_sel_bnd_electrolyte_gde_top";
    private static final String ANODE = "m10a3_sel_bnd_electrolyte_gde_bottom";
    private static final String DATA = "dset_a4a_ohmic";
    private static int serial = 0;

    private LiNRR_M10A4_ElectricalLoss() {}

    public static void main(String[] args) throws Exception {
        if (Files.exists(Paths.get(CHECKPOINT))) throw new IllegalStateException("REFUSE_OVERWRITE_ELECTRICAL_LOSS_CHECKPOINT");
        Model m = ModelUtil.load("M10A4Loss", INPUT);
        try {
            require(m.result().dataset().hasTag(DATA), "A4A_DATASET_MISSING");
            require(m.component(COMP).selection().hasTag(DOM), "ELECTROLYTE_DOMAIN_SELECTION_MISSING");

            createVolumePlot(m, "pg_a4loss_phi", "07 | ELECTRICAL LOSS | Electrolyte Ohmic Drop | NOT FULL-CELL VOLTAGE", "cd.phil", "V");
            createVolumePlot(m, "pg_a4loss_joule", "07 | ELECTRICAL LOSS | Joule Heating Density | DERIVED ELECTRICAL SOURCE | NO THERMAL FEEDBACK", "q_ohmic_a4a", "W/m^3");
            m.comments("M10A4 derived q_ohmic=i dot E diagnostic. DERIVED_ELECTRICAL_SOURCE; NO_THERMAL_FEEDBACK; not predicted full-cell voltage.");
            m.save(CHECKPOINT); // required immutable checkpoint before formal audit

            double phiAn = scalar(m, "AvSurface", ANODE, 2, "cd.phil", "V");
            double phiCa = scalar(m, "AvSurface", CATHODE, 2, "cd.phil", "V");
            double drop = phiAn - phiCa;
            double iCath = scalar(m, "IntSurface", CATHODE, 2, "cd.nIl", "A");
            double resistance = drop / Math.abs(iCath);
            double qInt = scalar(m, "IntVolume", DOM, 3, "q_ohmic_a4a", "W");
            double qMin = scalar(m, "MinVolume", DOM, 3, "q_ohmic_a4a", "W/m^3");
            double qMax = scalar(m, "MaxVolume", DOM, 3, "q_ohmic_a4a", "W/m^3");
            boolean finite = Double.isFinite(drop) && Double.isFinite(resistance) && Double.isFinite(qInt)
                && Double.isFinite(qMin) && Double.isFinite(qMax);
            boolean pass = finite && qInt >= 0 && qMin >= -1e-12 * Math.max(qMax, 1.0);

            List<String[]> rows = new ArrayList<>();
            add(rows, "electrolyte_ohmic_drop", drop, "V", "AvSurface(phi,anode)-AvSurface(phi,cathode)", "DERIVED_DIAGNOSTIC", "NOT_FULL_CELL_VOLTAGE", pass);
            add(rows, "model_electrolyte_ohmic_resistance", resistance, "ohm", "electrolyte_ohmic_drop/abs(I_cathode)", "DERIVED_DIAGNOSTIC", "kappa=0.3 S/m PROVISIONAL_SENSITIVITY; not fitted to Manual EIS threshold", pass);
            add(rows, "integrated_electrolyte_joule_source", qInt, "W", "IntVolume(i dot E)", "DERIVED_ELECTRICAL_SOURCE", "NO_THERMAL_FEEDBACK", pass);
            add(rows, "joule_heating_density_min", qMin, "W/m^3", "MinVolume(i dot E)", "DERIVED_ELECTRICAL_SOURCE", "NO_THERMAL_FEEDBACK", pass);
            add(rows, "joule_heating_density_max", qMax, "W/m^3", "MaxVolume(i dot E)", "DERIVED_ELECTRICAL_SOURCE", "NO_THERMAL_FEEDBACK", pass);
            write(Paths.get(TABLE), rows);

            System.out.println("M10A4_ELECTRICAL_LOSS|drop_V=" + f(drop) + "|R_ohm=" + f(resistance)
                + "|joule_W=" + f(qInt) + "|q_min_W_m3=" + f(qMin) + "|q_max_W_m3=" + f(qMax)
                + "|thermal_feedback=FALSE|full_cell_voltage=FALSE|status=" + (pass ? "PASS" : "FAIL"));
            if (!pass) throw new IllegalStateException("DERIVED_ELECTRICAL_LOSS_GATE_FAIL");
            System.out.println("M10A4_DERIVED_ELECTRICAL_LOSS=PASS");
            System.out.println("CHECKPOINT_ELECTRICAL_LOSS=" + CHECKPOINT);
        } finally {
            ModelUtil.remove("M10A4Loss");
        }
    }

    private static void createVolumePlot(Model m, String tag, String label, String expr, String unit) {
        if (m.result().hasTag(tag)) m.result().remove(tag);
        m.result().create(tag, "PlotGroup3D");
        m.result(tag).label(label);
        m.result(tag).set("data", DATA);
        m.result(tag).create("vol", "Volume");
        m.result(tag).feature("vol").set("expr", expr);
        m.result(tag).feature("vol").set("unit", unit);
        m.result(tag).feature("vol").create("sel", "Selection");
        m.result(tag).feature("vol").feature("sel").selection().named(DOM);
    }

    private static double scalar(Model m, String type, String selection, int dim, String expr, String unit) {
        String tag = "m10a4loss_ev_" + (++serial);
        m.result().numerical().create(tag, type);
        try {
            m.result().numerical(tag).set("data", DATA);
            m.result().numerical(tag).selection().geom(GEOM, dim);
            m.result().numerical(tag).selection().set(m.component(COMP).selection(selection).entities(dim));
            m.result().numerical(tag).set("expr", new String[]{expr});
            m.result().numerical(tag).set("unit", new String[]{unit});
            if (type.startsWith("Int")) {
                m.result().numerical(tag).set("intorderactive", true);
                m.result().numerical(tag).set("intorder", 8);
            }
            double[][] values = m.result().numerical(tag).getReal();
            require(values != null && values.length > 0 && values[0].length > 0, "EMPTY_EVALUATION " + expr);
            return values[0][values[0].length - 1];
        } finally {
            m.result().numerical().remove(tag);
        }
    }

    private static void add(List<String[]> rows, String q, double value, String unit, String definition,
                            String sourceClass, String notes, boolean pass) {
        rows.add(new String[]{q, f(value), unit, definition, DATA, DOM, sourceClass,
            pass ? "PASS" : "FAIL", notes});
    }

    private static void write(Path path, List<String[]> rows) throws Exception {
        Files.createDirectories(path.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("quantity,value,unit,definition,dataset,selection,source_class,status,notes");
            w.newLine();
            for (String[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) w.write(',');
                    w.write(csv(row[i]));
                }
                w.newLine();
            }
        }
    }

    private static String csv(String s) {
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
    private static String f(double x) { return String.format(Locale.ROOT, "%.15g", x); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
