import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.io.IOException;
import java.util.Locale;

/**
 * M03A.1: editable pure-ohmic calibration copy of the frozen M03A baseline.
 *
 * The PowerShell readiness gate supplies vetted values through environment
 * variables.  Standalone execution deliberately falls back to the frozen M03A
 * provisional smoke-test values.  This model contains no charge-transfer law,
 * reaction kinetics, species transport, or concentration polarization.
 */
public final class LiNRR_M03A_1_Calibration {
    private static final String PROJECT_ROOT =
        "F:\\LiNRR_COMSOL\\LiNRR_COMSOL_Codex_Starter";
    private static final String MPH = PROJECT_ROOT +
        "\\models\\generated\\LiNRR_M03A_1_calibration.mph";

    private static final double CURRENT_TOL = 1.0e-6;
    private static final double VOLTAGE_TOL = 1.0e-4;

    private LiNRR_M03A_1_Calibration() {}

    public static Model run() throws Exception {
        String state = settingText("M03A1_RUN_STATE", "SYNTHETIC_SMOKE_TEST");
        String mode = settingText("M03A1_MODE", "PROVISIONAL");
        boolean calibrated = "EXPERIMENTALLY_CALIBRATED".equals(state);
        String labelStatus = calibrated ? state :
            "SYNTHETIC_SMOKE_TEST — NOT EXPERIMENTALLY CALIBRATED";

        Model model = ModelUtil.create("Model");
        model.label("LiNRR_M03A_1_calibration | " + labelStatus);
        defineParameters(model, state, mode);
        buildGeometry(model, labelStatus);
        createSelections(model);
        addPrimaryCurrentPhysics(model, labelStatus);
        definePostprocessingVariables(model);
        buildMesh(model);
        createStudy(model);
        model.study("std_primary_cal").run();
        createResults(model);

        double anode = scalar(model, "int_anode_cal");
        double cathode = scalar(model, "int_cathode_cal");
        double left = scalar(model, "int_left_cal");
        double right = scalar(model, "int_right_cal");
        double current = model.param().evaluate("Icell", "A");
        double phiAnode = scalar(model, "avg_phi_anode_cal");
        double phiCathode = scalar(model, "avg_phi_cathode_cal");
        double simulatedVoltage = phiAnode - phiCathode;
        double analyticVoltage = model.param().evaluate("V_analytic", "V");
        double balance = Math.abs(anode + cathode + left + right) /
            Math.max(Math.abs(current), 1.0e-30);
        double voltageError = Math.abs(simulatedVoltage - analyticVoltage) /
            Math.max(Math.abs(analyticVoltage), 1.0e-30);
        double effectiveResistance = simulatedVoltage / current;
        double analyticResistance = model.param().evaluate("R_analytic", "ohm");
        double jMean = scalar(model, "avg_j_cal");

        if (!(anode < 0.0 && cathode > 0.0)) {
            throw new IllegalStateException("M03A.1 current sign check failed");
        }
        if (!finite(balance) || balance > CURRENT_TOL) {
            throw new IllegalStateException("M03A.1 current balance failed: " + balance);
        }
        if (!finite(voltageError) || voltageError > VOLTAGE_TOL) {
            throw new IllegalStateException("M03A.1 analytical voltage check failed: " + voltageError);
        }

        System.out.println("M03A1_RESULT|run_state|calibration_mode|simulated_voltage_V|" +
            "analytic_voltage_V|voltage_relative_error|effective_resistance_ohm|" +
            "analytic_resistance_ohm|anode_current_A|cathode_current_A|" +
            "current_balance_relative_error|mean_current_density_A_m2|status");
        System.out.println(String.format(Locale.ROOT,
            "M03A1_RESULT|%s|%s|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|PASS",
            state, mode, simulatedVoltage, analyticVoltage, voltageError,
            effectiveResistance, analyticResistance, anode, cathode, balance, jMean));

        saveModel(model);
        return model;
    }

    private static void defineParameters(Model model, String state, String mode) {
        double l = settingNumber("M03A1_L_M", 0.055);
        double h = settingNumber("M03A1_H_M", 0.004);
        double w = settingNumber("M03A1_W_M", 0.055);
        double area = settingNumber("M03A1_AREA_M2", l * w);
        double temperature = settingNumber("M03A1_T_K", 298.15);
        double kappa = settingNumber("M03A1_KAPPA_S_M", 0.5);
        double kappaU = settingNumber("M03A1_KAPPA_U_S_M", 0.05);
        double current = settingNumber("M03A1_ICELL_A", 0.1);
        double currentU = settingNumber("M03A1_ICELL_U_A", 0.005);
        double measuredV = settingNumber("M03A1_VMEASURED_V", 0.0);
        double hasMeasuredV = settingNumber("M03A1_HAS_VMEASURED", 0.0);
        String geometrySource = settingText("M03A1_GEOMETRY_SOURCE",
            "PROVISIONAL: frozen M03A numerical geometry");
        String kappaSource = settingText("M03A1_KAPPA_SOURCE",
            "PROVISIONAL: frozen M03A value; assumed smoke-test uncertainty");
        String currentSource = settingText("M03A1_CURRENT_SOURCE",
            "PROVISIONAL: frozen M03A value; assumed smoke-test uncertainty");

        requirePositive("Lcell", l);
        requirePositive("Hcell", h);
        requirePositive("Wcell", w);
        requirePositive("electrode_active_area", area);
        requirePositive("temperature", temperature);
        requirePositive("kappa_el", kappa);
        requirePositive("Icell", current);

        model.param().set("Lcell", fmt(l) + "[m]", geometrySource);
        model.param().set("Hcell", fmt(h) + "[m]", geometrySource);
        model.param().set("Wcell", fmt(w) + "[m]", geometrySource);
        model.param().set("Aelec", fmt(area) + "[m^2]",
            "Selected electrode_active_area; " + geometrySource);
        model.param().set("T0", fmt(temperature) + "[K]", geometrySource);
        model.param().set("kappa_el", fmt(kappa) + "[S/m]",
            kappaSource + "; standard uncertainty=" + fmt(kappaU) + " S/m");
        model.param().set("u_kappa_el", fmt(kappaU) + "[S/m]",
            "Standard uncertainty supplied by M03A.1 gate");
        model.param().set("Icell", fmt(current) + "[A]",
            currentSource + "; standard uncertainty=" + fmt(currentU) + " A");
        model.param().set("u_Icell", fmt(currentU) + "[A]",
            "Standard uncertainty supplied by M03A.1 gate");
        model.param().set("j_app", "Icell/Aelec",
            "Applied current density on the explicitly audited area basis");
        model.param().set("R_analytic", "Hcell/(kappa_el*Aelec)",
            "Uniform-electrolyte analytical resistance; excludes all non-electrolyte series terms");
        model.param().set("V_analytic", "Icell*R_analytic",
            "Pure electrolyte ohmic voltage only");
        model.param().set("V_measured", fmt(measuredV) + "[V]",
            hasMeasuredV > 0.5 ? "Measured total cell voltage from calibration input" :
                "Placeholder zero: measured cell voltage unavailable");
        model.param().set("has_V_measured", fmt(hasMeasuredV),
            "1 only when a traceable measured_cell_voltage is available");
        model.param().set("calibration_state_code", calibratedCode(state),
            "0 synthetic smoke test; 1 experimental input incomplete; 2 experimentally calibrated");
        model.param().set("calibration_mode_code", modeCode(mode),
            "0 provisional; 1 direct; 2 de-embedded HFR; 3 cross-check");
        model.param().set("sel_tol", "1e-9[m]", "Coordinate-selection tolerance");
    }

    private static void buildGeometry(Model model, String labelStatus) {
        model.component().create("comp_cal", true);
        model.component("comp_cal").label("M03A.1 pure-ohmic calibration | " + labelStatus);
        model.component("comp_cal").geom().create("geom_cal", 2);
        model.component("comp_cal").geom("geom_cal").lengthUnit("m");
        model.component("comp_cal").geom("geom_cal").create("r_electrolyte_cal", "Rectangle");
        model.component("comp_cal").geom("geom_cal").feature("r_electrolyte_cal")
            .set("size", new String[] {"Lcell", "Hcell"});
        geometryBox(model, "gsel_electrolyte_cal", 2,
            "-sel_tol", "Lcell+sel_tol", "-sel_tol", "Hcell+sel_tol");
        geometryBox(model, "gsel_left_cal", 1,
            "-sel_tol", "sel_tol", "-sel_tol", "Hcell+sel_tol");
        geometryBox(model, "gsel_right_cal", 1,
            "Lcell-sel_tol", "Lcell+sel_tol", "-sel_tol", "Hcell+sel_tol");
        geometryBox(model, "gsel_anode_cal", 1,
            "-sel_tol", "Lcell+sel_tol", "Hcell-sel_tol", "Hcell+sel_tol");
        geometryBox(model, "gsel_cathode_cal", 1,
            "-sel_tol", "Lcell+sel_tol", "-sel_tol", "sel_tol");
        model.component("comp_cal").geom("geom_cal").run();
    }

    private static void geometryBox(Model model, String tag, int dim,
                                    String xmin, String xmax, String ymin, String ymax) {
        model.component("comp_cal").geom("geom_cal").create(tag, "BoxSelection");
        model.component("comp_cal").geom("geom_cal").feature(tag).set("entitydim", dim);
        model.component("comp_cal").geom("geom_cal").feature(tag).set("condition", "inside");
        model.component("comp_cal").geom("geom_cal").feature(tag).set("xmin", xmin);
        model.component("comp_cal").geom("geom_cal").feature(tag).set("xmax", xmax);
        model.component("comp_cal").geom("geom_cal").feature(tag).set("ymin", ymin);
        model.component("comp_cal").geom("geom_cal").feature(tag).set("ymax", ymax);
    }

    private static void createSelections(Model model) {
        box(model, "sel_electrolyte_cal", 2,
            "-sel_tol", "Lcell+sel_tol", "-sel_tol", "Hcell+sel_tol");
        box(model, "sel_left_cal", 1,
            "-sel_tol", "sel_tol", "-sel_tol", "Hcell+sel_tol");
        box(model, "sel_right_cal", 1,
            "Lcell-sel_tol", "Lcell+sel_tol", "-sel_tol", "Hcell+sel_tol");
        box(model, "sel_anode_cal", 1,
            "-sel_tol", "Lcell+sel_tol", "Hcell-sel_tol", "Hcell+sel_tol");
        box(model, "sel_cathode_cal", 1,
            "-sel_tol", "Lcell+sel_tol", "-sel_tol", "sel_tol");
    }

    private static void box(Model model, String tag, int dim,
                            String xmin, String xmax, String ymin, String ymax) {
        model.component("comp_cal").selection().create(tag, "Box");
        model.component("comp_cal").selection(tag).set("entitydim", dim);
        model.component("comp_cal").selection(tag).set("condition", "inside");
        model.component("comp_cal").selection(tag).set("xmin", xmin);
        model.component("comp_cal").selection(tag).set("xmax", xmax);
        model.component("comp_cal").selection(tag).set("ymin", ymin);
        model.component("comp_cal").selection(tag).set("ymax", ymax);
    }

    private static void addPrimaryCurrentPhysics(Model model, String labelStatus) {
        model.component("comp_cal").physics().create(
            "cd_cal", "PrimaryCurrentDistribution", "geom_cal");
        model.component("comp_cal").physics("cd_cal")
            .label("M03A.1 pure-ohmic Primary Current Distribution | " + labelStatus);
        model.component("comp_cal").physics("cd_cal").selection()
            .named("geom_cal_gsel_electrolyte_cal");
        model.component("comp_cal").physics("cd_cal").feature("ice1")
            .set("sigmal_mat", "userdef");
        model.component("comp_cal").physics("cd_cal").feature("ice1")
            .set("sigmal", "kappa_el");
        model.component("comp_cal").physics("cd_cal")
            .create("anode_current_cal", "ElectrolyteCurrent", 1);
        model.component("comp_cal").physics("cd_cal").feature("anode_current_cal")
            .selection().named("geom_cal_gsel_anode_cal");
        model.component("comp_cal").physics("cd_cal").feature("anode_current_cal")
            .set("IonicCurrentType", "AverageCurrentDensity");
        model.component("comp_cal").physics("cd_cal").feature("anode_current_cal")
            .set("Ial", "j_app");
        model.component("comp_cal").physics("cd_cal")
            .create("cathode_ground_cal", "ElectrolytePotential", 1);
        model.component("comp_cal").physics("cd_cal").feature("cathode_ground_cal")
            .selection().named("geom_cal_gsel_cathode_cal");
        model.component("comp_cal").physics("cd_cal").feature("cathode_ground_cal")
            .set("philbnd", "0[V]");
    }

    private static void definePostprocessingVariables(Model model) {
        model.component("comp_cal").variable().create("var_current_cal");
        model.component("comp_cal").variable("var_current_cal").selection()
            .named("sel_electrolyte_cal");
        model.component("comp_cal").variable("var_current_cal").set(
            "j_l_x_cal", "-kappa_el*d(cd.phil,x)");
        model.component("comp_cal").variable("var_current_cal").set(
            "j_l_y_cal", "-kappa_el*d(cd.phil,y)");
        model.component("comp_cal").variable("var_current_cal").set(
            "j_l_mag_cal", "sqrt(j_l_x_cal^2+j_l_y_cal^2)");
    }

    private static void buildMesh(Model model) {
        model.component("comp_cal").mesh().create("mesh_cal");
        model.component("comp_cal").mesh("mesh_cal").create("map_cal", "Map");
        model.component("comp_cal").mesh("mesh_cal").feature("map_cal")
            .selection().named("sel_electrolyte_cal");
        model.component("comp_cal").mesh("mesh_cal").feature("map_cal")
            .create("dist_height_cal", "Distribution");
        model.component("comp_cal").mesh("mesh_cal").feature("map_cal")
            .feature("dist_height_cal").selection().named("sel_left_cal");
        model.component("comp_cal").mesh("mesh_cal").feature("map_cal")
            .feature("dist_height_cal").set("numelem", 20);
        model.component("comp_cal").mesh("mesh_cal").feature("map_cal")
            .create("dist_length_cal", "Distribution");
        model.component("comp_cal").mesh("mesh_cal").feature("map_cal")
            .feature("dist_length_cal").selection().named("sel_cathode_cal");
        model.component("comp_cal").mesh("mesh_cal").feature("map_cal")
            .feature("dist_length_cal").set("numelem", 40);
        model.component("comp_cal").mesh("mesh_cal").run();
    }

    private static void createStudy(Model model) {
        model.study().create("std_primary_cal");
        model.study("std_primary_cal").label("M03A.1 stationary pure-ohmic calibration");
        model.study("std_primary_cal").create("stat_cal", "Stationary");
        model.study("std_primary_cal").feature("stat_cal").activate("cd_cal", true);
    }

    private static void createResults(Model model) {
        model.result().create("pg_potential_cal", "PlotGroup2D");
        model.result("pg_potential_cal").label("M03A.1 electrolyte potential");
        model.result("pg_potential_cal").create("surf_potential_cal", "Surface");
        model.result("pg_potential_cal").feature("surf_potential_cal").set("expr", "cd.phil");
        model.result("pg_potential_cal").feature("surf_potential_cal").set("unit", "V");
        model.result().create("pg_current_cal", "PlotGroup2D");
        model.result("pg_current_cal").label("M03A.1 electrolyte current density");
        model.result("pg_current_cal").create("surf_current_cal", "Surface");
        model.result("pg_current_cal").feature("surf_current_cal").set("expr", "j_l_mag_cal");
        model.result("pg_current_cal").feature("surf_current_cal").set("unit", "A/m^2");
        model.result().create("pg_comparison_cal", "PlotGroup1D");
        model.result("pg_comparison_cal").label("M03A.1 simulated and analytical voltage");
        model.result("pg_comparison_cal").create("glob_comparison_cal", "Global");
        model.result("pg_comparison_cal").feature("glob_comparison_cal").set("expr",
            new String[] {"V_analytic", "V_measured"});
        model.result("pg_comparison_cal").feature("glob_comparison_cal").set("unit",
            new String[] {"V", "V"});

        lineIntegral(model, "int_anode_cal", "sel_anode_cal");
        lineIntegral(model, "int_cathode_cal", "sel_cathode_cal");
        lineIntegral(model, "int_left_cal", "sel_left_cal");
        lineIntegral(model, "int_right_cal", "sel_right_cal");
        lineAverage(model, "avg_phi_anode_cal", "sel_anode_cal", "cd.phil", "V");
        lineAverage(model, "avg_phi_cathode_cal", "sel_cathode_cal", "cd.phil", "V");
        model.result().numerical().create("avg_j_cal", "AvSurface");
        model.result().numerical("avg_j_cal").selection().named("sel_electrolyte_cal");
        model.result().numerical("avg_j_cal").set("expr", new String[] {"j_l_mag_cal"});
        model.result().numerical("avg_j_cal").set("unit", new String[] {"A/m^2"});
    }

    private static void lineIntegral(Model model, String tag, String selection) {
        model.result().numerical().create(tag, "IntLine");
        model.result().numerical(tag).selection().named(selection);
        model.result().numerical(tag).set("expr", new String[] {
            "(-kappa_el*(d(cd.phil,x)*nx+d(cd.phil,y)*ny))*Wcell"});
        model.result().numerical(tag).set("unit", new String[] {"A"});
        model.result().numerical(tag).set("intorderactive", true);
        model.result().numerical(tag).set("intorder", 8);
    }

    private static void lineAverage(Model model, String tag, String selection,
                                    String expression, String unit) {
        model.result().numerical().create(tag, "AvLine");
        model.result().numerical(tag).selection().named(selection);
        model.result().numerical(tag).set("expr", new String[] {expression});
        model.result().numerical(tag).set("unit", new String[] {unit});
        model.result().numerical(tag).set("intorderactive", true);
        model.result().numerical(tag).set("intorder", 8);
    }

    private static double scalar(Model model, String tag) {
        double[][] values = model.result().numerical(tag).getReal();
        if (values == null || values.length == 0 || values[0].length == 0) {
            throw new IllegalStateException("No M03A.1 numerical result for " + tag);
        }
        return values[0][0];
    }

    private static void saveModel(Model model) throws IOException {
        model.save(MPH);
    }

    private static void requirePositive(String name, double value) {
        if (!finite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive: " + value);
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static double settingNumber(String name, double fallback) {
        String value = selectedInput(name);
        if (value == null || value.trim().length() == 0) return fallback;
        return Double.parseDouble(value.trim());
    }

    private static String settingText(String name, String fallback) {
        String value = selectedInput(name);
        if (value == null || value.trim().length() == 0) return fallback;
        return value.trim().replace('|', '/').replace('\n', ' ');
    }

    /**
     * The build script compiles a transparent run-specific input class and adds
     * its directory with comsolbatch -classpathadd. Reflection keeps this
     * durable builder independently compilable and avoids COMSOL's prohibited
     * environment-variable and arbitrary-file reads. Missing input class means
     * the explicit provisional fallbacks above are used.
     */
    private static String selectedInput(String name) {
        try {
            Class<?> selected = Class.forName("LiNRR_M03A_1_SelectedInputs");
            Object value = selected.getMethod("get", String.class).invoke(null, name);
            return value == null ? null : value.toString();
        } catch (Exception exception) {
            return null;
        }
    }

    private static String calibratedCode(String state) {
        if ("EXPERIMENTALLY_CALIBRATED".equals(state)) return "2";
        if ("EXPERIMENTAL_INPUT_INCOMPLETE".equals(state)) return "1";
        return "0";
    }

    private static String modeCode(String mode) {
        if ("DIRECT_CONDUCTIVITY".equals(mode)) return "1";
        if ("DEEMBEDDED_HFR".equals(mode)) return "2";
        if ("CROSS_CHECK".equals(mode)) return "3";
        return "0";
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.12g", value);
    }

    public static void main(String[] args) throws Exception {
        run();
    }
}
