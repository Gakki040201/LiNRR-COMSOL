import com.comsol.model.GeomSequence;
import com.comsol.model.Model;
import com.comsol.model.physics.Physics;
import com.comsol.model.util.ModelUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * M10A2 formal real-geometry application layer.
 *
 * <p>The passed M10A1.1 model remains the immutable 3D flow baseline. New components are derived,
 * named, GUI-editable representations for the mirrored H2 channel, physical SSC/gasket assembly,
 * 1D Pipe Flow networks, through-plane Darcy/phase-transport SSC models, and a conservative N2
 * transfer baseline. Unmeasured wetting, permeability, and electrolyte data remain explicit
 * sensitivity parameters.</p>
 */
public final class LiNRR_M10A2_SSC_Pipe_Wetting {
    private static final String RT = "LiNRR_M10A2_RuntimeInputs";
    private static int serial = 0;
    private static final List<String> AUDIT = new ArrayList<String>();
    private static final Map<String,String> DATASETS = new LinkedHashMap<String,String>();

    private LiNRR_M10A2_SSC_Pipe_Wetting() {}

    public static void main(String[] args) throws Exception {
        final String input = runtime("INPUT_MPH");
        final String output = runtime("OUTPUT_MPH");
        final Path runDir = Paths.get(runtime("RUN_DIR"));
        final Model model = ModelUtil.load("M10A2", input);
        try {
            model.label("LiNRR_M10A2_ssc_pipe_wetting.mph");
            model.comments("M10A2 real-geometry application layer: immutable M10A1.1 flow baseline plus "
                + "mirrored real-CAD H2 channel, explicit 316L SSC and PtAu/316L SSC domains, Manual-backed "
                + "PFA tubing, Darcy permeability sensitivity, licensed porous phase transport, and "
                + "phenomenological gas-liquid N2 transfer. No Li plating, Li-NRR kinetics, SEI, or electrochemistry.");

            final double[] active;
            if (model.component().hasTag("comp_ssc_hardware")) {
                active = auditRealCadActiveArea(model);
                System.out.println("M10A2_RESUME_AFTER_REAL_FLOW=TRUE");
            } else {
                defineParameters(model);
                active = auditRealCadActiveArea(model);
                buildHardware(model, active[0]);
                updateAndSolveRealCadFlows(model);
                model.save(runDir.resolve("M10A2_checkpoint_real_flows.mph").toString());
                System.out.println("M10A2_REAL_FLOW_CHECKPOINT_SAVE=PASS");
            }
            if (model.component().hasTag("comp_h2_flow")) {
                DATASETS.put("std_h2","dset_std_h2");
                System.out.println("M10A2_RESUME_AFTER_H2=TRUE");
            } else {
                buildAndSolveH2Mirror(model);
                model.save(runDir.resolve("M10A2_checkpoint_h2.mph").toString());
                System.out.println("M10A2_H2_CHECKPOINT_SAVE=PASS");
            }
            final double[] pipe;
            final double[] porous;
            if (model.component().hasTag("comp_ssc_darcy")) {
                DATASETS.put("std_pipe_n2","dset_std_pipe_n2");DATASETS.put("std_pipe_h2","dset_std_pipe_h2");DATASETS.put("std_pipe_liq","dset_std_pipe_liq");DATASETS.put("std_ssc_darcy","dset_std_ssc_darcy");
                pipe=new double[]{model.param().evaluate("dp_N2_pipe","Pa"),model.param().evaluate("dp_H2_pipe","Pa"),model.param().evaluate("dp_liq_pipe","Pa")};
                porous=new double[]{1500,model.param().evaluate("1e-13[m^2]*dp_ssc/(mu_liq*t_ssc)","m/s"),model.param().evaluate("1e-9[m^2]*dp_ssc/(mu_liq*t_ssc)","m/s")};
                System.out.println("M10A2_RESUME_AFTER_POROUS=TRUE");
            } else {
                pipe = buildAndSolvePipeNetworks(model);
                porous = buildAndSolvePorousSSC(model);
                model.save(runDir.resolve("M10A2_checkpoint_porous.mph").toString());
                System.out.println("M10A2_POROUS_CHECKPOINT_SAVE=PASS");
            }
            final double[] wetting;
            if (model.component().hasTag("comp_ssc_wetting")) {
                DATASETS.put("std_ssc_wetting","dset_std_ssc_wetting");
                wetting=new double[]{model.param().evaluate("wet_Sl_min_audit"),model.param().evaluate("wet_Sl_max_audit"),model.param().evaluate("wet_breakthrough_Sl_audit")};
                System.out.println("M10A2_RESUME_AFTER_WETTING=TRUE");
            } else {
                wetting = buildAndSolveWetting(model);
                model.save(runDir.resolve("M10A2_checkpoint_wetting.mph").toString());
                System.out.println("M10A2_WETTING_CHECKPOINT_SAVE=PASS");
            }
            final double[] transfer;
            if (model.component().hasTag("comp_n2_transfer")) {
                DATASETS.put("std_n2_transfer","dset_std_n2_transfer");
                transfer=new double[]{model.param().evaluate("n2_transfer_rate_audit","mol/s"),model.param().evaluate("n2_interface_c_audit","mol/m^3"),model.param().evaluate("n2_residual_audit"),model.param().evaluate("n2_Pe_audit"),model.param().evaluate("n2_keff_audit","m/s")};
                System.out.println("M10A2_RESUME_AFTER_TRANSFER=TRUE");
            } else {
                transfer = buildAndSolveN2Transfer(model);
                model.save(runDir.resolve("M10A2_checkpoint_transfer.mph").toString());
                System.out.println("M10A2_TRANSFER_CHECKPOINT_SAVE=PASS");
            }

            createResults(model);
            for(String row:AUDIT)System.out.println("M10A2_AUDIT_CSV|"+row);
            model.save(output);
            System.out.println("M10A2_MODEL_SAVE=PASS");
        } finally {
            ModelUtil.remove("M10A2");
        }

        independentReload(output);
        System.out.println("M10A2A_HYDRAULIC_NETWORK=PASS");
        System.out.println("M10A2B_POROUS_SSC=PASS");
        System.out.println("M10A2C_WETTING=PASS");
        System.out.println("M10A2D_N2_TRANSFER=PASS");
    }

    private static void defineParameters(Model m) {
        // Manual-backed hardware and operation.
        p(m,"Q_N2_lab","50[cm^3/min]","LAB_MANUAL formal N2 baseline; mL/min represented as cm^3/min");
        p(m,"Q_H2_lab","50[cm^3/min]","LAB_MANUAL formal H2 baseline; mL/min represented as cm^3/min");
        p(m,"Q_gas_startup","20[cm^3/min]","LAB_MANUAL optional 20 min preflow case");
        p(m,"t_gas_startup","20[min]","LAB_MANUAL startup duration");
        p(m,"Q_liq_sweep","1[cm^3/min]","CALIBRATION_REQUIRED liquid flow sweep variable; not a lab baseline");
        p(m,"gas_pfa_id","2.0[mm]","LAB_MANUAL PFA ID used for hydraulics");
        p(m,"gas_pfa_od","3.0[mm]","LAB_MANUAL PFA OD, visualization only");
        p(m,"liq_pfa_id","1.5[mm]","LAB_MANUAL liquid PFA ID used for hydraulics");
        p(m,"liq_pfa_id_large","2.0[mm]","LAB_MANUAL 10 cm liquid PFA segment ID");
        p(m,"latex_id","1.6[mm]","LAB_MANUAL 14# latex ID; length remains CALIBRATION_REQUIRED");
        p(m,"L_gas_out","0.20[m]","LAB_MANUAL approximate gas outlet length per side");
        p(m,"L_liq_20","0.20[m]","LAB_MANUAL liquid PFA segment");
        p(m,"L_liq_15","0.15[m]","LAB_MANUAL liquid PFA segment; two segments");
        p(m,"L_liq_10","0.10[m]","LAB_MANUAL liquid PFA segment");
        p(m,"L_latex_included","0[m]","CALIBRATION_REQUIRED latex length; zero explicitly excludes unknown resistance");
        p(m,"ssc_cut","60[mm]","LAB_MANUAL 316L SSC square cut width");
        p(m,"gasket_outer","75[mm]","LAB_MANUAL gasket outer square");
        p(m,"gasket_inner","55[mm]","LAB_MANUAL gasket inner opening");
        p(m,"t_ssc","30[um]","LITERATURE_SAME_PLATFORM provisional SSC thickness; not measured");
        p(m,"t_gasket_visual","0[mm]","CALIBRATION_REQUIRED compression thickness; zero-thickness mask only");

        // Explicit provisional/calibration parameters. No property is labelled experimental.
        p(m,"eps_ssc","0.5","PROVISIONAL_SENSITIVITY; sweep 0.3, 0.5, 0.7");
        p(m,"K_ssc","1e-11[m^2]","PROVISIONAL_SENSITIVITY; not inferred from pore size");
        p(m,"theta_ssc","90[deg]","PROVISIONAL_SENSITIVITY contact angle");
        p(m,"pc_ssc","1500[Pa]","PROVISIONAL_SENSITIVITY entry pressure; not tuned");
        p(m,"tau_ssc","2","PROVISIONAL_SENSITIVITY tortuosity");
        p(m,"dp_ssc","1500[Pa]","Pressure-difference sweep parameter; 15 mbar is literature comparison only");
        p(m,"p_smooth","1[Pa]","Numerical smoothing scale for positive pressure drive");
        p(m,"Sl_initial","0.01","PROVISIONAL initially dry liquid saturation");
        p(m,"Sl_transport","0.5","PROVISIONAL wetting-to-transport sensitivity value");
        p(m,"Sl_breakthrough","0.5","PROVISIONAL diagnostic threshold for liquid breakthrough and flooding front");

        p(m,"rho_N2","1.145[kg/m^3]","PROVISIONAL gas property at nominal condition");
        p(m,"mu_N2","1.76e-5[Pa*s]","PROVISIONAL gas viscosity");
        p(m,"rho_H2","0.082[kg/m^3]","PROVISIONAL gas density; calibration conditions required");
        p(m,"mu_H2","8.9e-6[Pa*s]","PROVISIONAL gas viscosity");
        p(m,"rho_liq","889[kg/m^3]","PROVISIONAL electrolyte density inherited from numerical layer");
        p(m,"mu_liq","0.46[mPa*s]","PROVISIONAL electrolyte viscosity inherited from numerical layer");
        p(m,"p_out","0[Pa]","Gauge outlet reference");

        p(m,"A_ssc_active","2500[mm^2]","DERIVED placeholder overwritten by real-CAD footprint audit");
        p(m,"A_ssc_cut","ssc_cut^2","DERIVED Manual cut area; not all active");
        p(m,"R_N2_out","128*mu_N2*L_gas_out/(pi*gas_pfa_id^4)","Hagen-Poiseuille audit of documented N2 outlet PFA");
        p(m,"R_H2_out","128*mu_H2*L_gas_out/(pi*gas_pfa_id^4)","Hagen-Poiseuille audit of documented H2 outlet PFA");
        p(m,"R_liq_known","128*mu_liq*(L_liq_20+2*L_liq_15)/(pi*liq_pfa_id^4)+128*mu_liq*L_liq_10/(pi*liq_pfa_id_large^4)","Known PFA liquid-loop resistance; latex excluded");
        p(m,"dp_N2_pipe","R_N2_out*Q_N2_lab","Documented N2 outlet tube pressure drop");
        p(m,"dp_H2_pipe","R_H2_out*Q_H2_lab","Documented H2 outlet tube pressure drop");
        p(m,"dp_liq_pipe","R_liq_known*Q_liq_sweep","Known PFA liquid pressure drop at sweep value");
        p(m,"u_ssc_darcy","K_ssc*dp_ssc/(mu_liq*t_ssc)","Darcy superficial velocity audit");
        p(m,"u_ssc_transport","K_ssc*Sl_transport^2*dp_ssc/(mu_liq*t_ssc)","PROVISIONAL transport velocity including declared k_r=Sl^2");
        p(m,"u_wet_in","K_ssc/(mu_liq*t_ssc)*0.5*((dp_ssc-pc_ssc)+sqrt((dp_ssc-pc_ssc)^2+p_smooth^2))","Explicit provisional pressure-entry wetting drive");

        p(m,"T_model","298.15[K]","PROVISIONAL transport temperature");
        p(m,"p_N2_abs","1[atm]","PROVISIONAL gas equilibrium pressure");
        p(m,"H_N2_sens","1e-5[mol/(m^3*Pa)]","PROVISIONAL_SENSITIVITY; electrolyte Henry coefficient is CALIBRATION_REQUIRED");
        p(m,"c_N2_gas","p_N2_abs/(R_const*T_model)","Distinct gas-phase ideal-gas N2 concentration");
        p(m,"c_N2_eq","H_N2_sens*p_N2_abs","Phenomenological Henry-equilibrium dissolved concentration");
        p(m,"D_N2_liq","2e-9[m^2/s]","PROVISIONAL numerical/literature estimate from M02.2");
        p(m,"D_N2_ssc_eff","D_N2_liq*eps_ssc*Sl_transport/tau_ssc","Explicit effective diffusivity sensitivity law");
        p(m,"L_electrolyte_transfer","5[mm]","REAL_CAD half-chamber through-plane distance for baseline transfer slab");
        p(m,"W_transfer","50[mm]","DERIVED real-CAD flow-field footprint width");
        p(m,"c_N2_bulk","0[mol/m^3]","PROVISIONAL well-mixed far-field sink; no reaction kinetics");

        // Update M10A1 parameter aliases without calling them experimental.
        m.param().set("Q_n2_test","Q_N2_lab","M10A2 LAB_MANUAL operating point; legacy tag retained for field compatibility");
        m.param().set("Q_liq_test","Q_liq_sweep","M10A2 CALIBRATION_REQUIRED sweep; legacy tag retained for field compatibility");
        m.param().set("rho_n2_test","rho_N2","PROVISIONAL property; legacy tag retained");
        m.param().set("mu_n2_test","mu_N2","PROVISIONAL property; legacy tag retained");
        m.param().set("rho_liq_test","rho_liq","PROVISIONAL property; legacy tag retained");
        m.param().set("mu_liq_test","mu_liq","PROVISIONAL property; legacy tag retained");
        System.out.println("M10A2_PARAMETERS=PASS");
    }

    private static double[] auditRealCadActiveArea(Model m) {
        final String c="comp_n2_flow", g="geom_n2_channel_fluid", s="sel_bnd_n2_gde_interface";
        final int[] ids=m.component(c).selection(s).entities(2);
        if(ids.length==0) throw new IllegalStateException("REAL_CAD_ACTIVE_SELECTION_EMPTY");
        m.component(c).geom(g).measureFinal().selection().geom(2);
        m.component(c).geom(g).measureFinal().selection().set(ids);
        final double open=m.component(c).geom(g).measureFinal().getArea();
        final double[] b=m.component(c).geom(g).measureFinal().getBoundingBox();
        final double footprint=(b[1]-b[0])*(b[3]-b[2]);
        if(!finite(open)||!finite(footprint)||open<=0||footprint<=0||open>footprint*1.001)
            throw new IllegalStateException("REAL_CAD_ACTIVE_AREA_INVALID");
        m.param().set("A_ssc_active",f(footprint)+"[mm^2]","DERIVED from real-CAD N2/SSC interface bounding footprint");
        System.out.println("M10A2_SSC_AREA|flowfield_footprint_mm2="+f(footprint)+"|open_channel_area_mm2="+f(open)
            +"|total_cut_area_mm2="+f(m.param().evaluate("A_ssc_cut","mm^2"))+"|bbox_mm="+Arrays.toString(b));
        return new double[]{footprint,open,b[1]-b[0],b[3]-b[2]};
    }

    private static void buildHardware(Model m,double activeArea) {
        final String c="comp_ssc_hardware",g="geom_ssc_hardware";
        m.component().create(c,true);m.component(c).label("SSC and zero-thickness gasket assembly reference");
        final GeomSequence geom=m.component(c).geom().create(g,3);geom.lengthUnit("mm");
        block(geom,"blk_cathode_ssc",new String[]{"ssc_cut","ssc_cut","t_ssc"},new String[]{"-ssc_cut/2","-ssc_cut/2","5[mm]"});
        geom.feature("blk_cathode_ssc").label("Cathode 316L 500-mesh SSC | homogenized physical domain");
        block(geom,"blk_anode_ssc",new String[]{"ssc_cut","ssc_cut","t_ssc"},new String[]{"-ssc_cut/2","-ssc_cut/2","-5[mm]-t_ssc"});
        geom.feature("blk_anode_ssc").label("Anode PtAu/316L 500-mesh SSC | homogenized physical domain");
        // A 2D work plane records the gasket mask without inventing compression thickness.
        geom.feature().create("wp_gasket","WorkPlane");geom.feature("wp_gasket").set("quickplane","xy");geom.feature("wp_gasket").set("quickz","5[mm]");
        geom.feature("wp_gasket").geom().create("sq_outer","Square");geom.feature("wp_gasket").geom().feature("sq_outer").set("base","center");geom.feature("wp_gasket").geom().feature("sq_outer").set("size","gasket_outer");
        geom.feature("wp_gasket").geom().create("sq_inner","Square");geom.feature("wp_gasket").geom().feature("sq_inner").set("base","center");geom.feature("wp_gasket").geom().feature("sq_inner").set("size","gasket_inner");
        geom.feature("wp_gasket").geom().create("dif_mask","Difference");geom.feature("wp_gasket").geom().feature("dif_mask").selection("input").set("sq_outer");geom.feature("wp_gasket").geom().feature("dif_mask").selection("input2").set("sq_inner");
        geom.run();
        box3(m,c,"sel_dom_cathode_ssc",3,"-31[mm]","31[mm]","-31[mm]","31[mm]","4.999[mm]","5.1[mm]");
        box3(m,c,"sel_dom_anode_ssc",3,"-31[mm]","31[mm]","-31[mm]","31[mm]","-5.1[mm]","-4.999[mm]");
        require(m,c,"sel_dom_cathode_ssc",3);require(m,c,"sel_dom_anode_ssc",3);
        System.out.println("M10A2_HARDWARE_GEOMETRY|cathode_domains="+m.component(c).selection("sel_dom_cathode_ssc").entities(3).length
            +"|anode_domains="+m.component(c).selection("sel_dom_anode_ssc").entities(3).length
            +"|gasket_thickness_status=CALIBRATION_REQUIRED_ZERO_THICKNESS_MASK|active_area_mm2="+f(activeArea));
        System.out.println("M10A2_SSC_GASKET_GEOMETRY=PASS");
    }

    private static void updateAndSolveRealCadFlows(Model m) {
        m.component("comp_electrolyte_flow").label("Electrolyte real-CAD flow | Q_liquid calibration sweep");
        m.component("comp_n2_flow").label("N2 real-CAD flow | LAB_MANUAL 50 mL/min");
        final double[] liq={0.5,1,2,5};
        for(double q:liq){m.param().set("Q_liq_sweep",f(q)+"[cm^3/min]");m.study("std_liq_stationary").run();
            System.out.println("M10A2_LIQUID_SWEEP|Q_cm3_min="+f(q)+"|status=SOLVED|classification=CALIBRATION_REQUIRED");}
        m.param().set("Q_liq_sweep","1[cm^3/min]");m.study("std_liq_stationary").run();
        final double[] scales={0.2,0.4,0.7,1.0};
        for(double scale:scales){m.param().set("flow_scale_n2_test",f(scale));m.study("std_n2_stationary").run();
            System.out.println("M10A2_N2_CONTINUATION|Q_cm3_min="+f(50*scale)+"|status=SOLVED");}
        m.param().set("flow_scale_n2_test","1");
        rebindDataset(m,"std_liq_stationary","dset_liq_solution","comp_electrolyte_flow");
        rebindDataset(m,"std_n2_stationary","dset_n2_solution","comp_n2_flow");
        final double liqDp=surface(m,"AvSurface","dset_liq_solution","sel_bnd_electrolyte_inlet","p","Pa")
            -surface(m,"AvSurface","dset_liq_solution","sel_bnd_electrolyte_outlet","p","Pa");
        final double n2Dp=surface(m,"AvSurface","dset_n2_solution","sel_bnd_n2_inlet","p2","Pa")
            -surface(m,"AvSurface","dset_n2_solution","sel_bnd_n2_outlet","p2","Pa");
        final double liqBal=flowBalance(m,"dset_liq_solution","sel_bnd_electrolyte_inlet","sel_bnd_electrolyte_outlet","u*nx+v*ny+w*nz","Q_liq_sweep");
        final double n2Bal=flowBalance(m,"dset_n2_solution","sel_bnd_n2_inlet","sel_bnd_n2_outlet","u2*nx+v2*ny+w2*nz","Q_N2_lab");
        if(liqBal>1e-6||n2Bal>1e-6||!finite(liqDp)||!finite(n2Dp)) throw new IllegalStateException("REAL_CAD_FLOW_CONSERVATION");
        audit("real_cad_flow,liquid_dp_Pa,"+f(liqDp));audit("real_cad_flow,n2_dp_Pa,"+f(n2Dp));
        audit("real_cad_flow,liquid_mass_balance_relative,"+f(liqBal));audit("real_cad_flow,n2_mass_balance_relative,"+f(n2Bal));
        m.param().set("dp_liq_cell_audit",f(liqDp)+"[Pa]","Computed real-CAD liquid-cell pressure drop at saved sensitivity case");
        m.param().set("dp_N2_cell_audit",f(n2Dp)+"[Pa]","Computed real-CAD N2-cell pressure drop at LAB_MANUAL flow");
        System.out.println("M10A2_REAL_CAD_FLOW|liquid_dp_Pa="+f(liqDp)+"|n2_dp_Pa="+f(n2Dp)+"|liq_balance="+f(liqBal)+"|n2_balance="+f(n2Bal));
    }

    private static void buildAndSolveH2Mirror(Model m) {
        final String c="comp_h2_flow",g="geom_h2_channel_fluid";
        m.component().create(c,true);m.component(c).label("H2 real-CAD channel mirrored through Z=0");
        final GeomSequence geom=m.component(c).geom().create(g,3);geom.lengthUnit("mm");geom.geomRep("cadps");
        strictImport(geom,"imp_cc_h2_source",runtime("CC_STEP"));
        geom.feature().create("rot_cc_h2","Rotate");geom.feature("rot_cc_h2").selection("input").set("imp_cc_h2_source");geom.feature("rot_cc_h2").set("specify","axis");geom.feature("rot_cc_h2").set("axistype","cartesian");geom.feature("rot_cc_h2").set("axis",new double[]{1,0,0});geom.feature("rot_cc_h2").set("pos",new double[]{0,0,0});geom.feature("rot_cc_h2").set("rot",-90.0);
        geom.feature().create("mov_cc_h2_top","Move");geom.feature("mov_cc_h2_top").selection("input").set("rot_cc_h2");geom.feature("mov_cc_h2_top").set("displ",new double[]{-54,54,28});
        geom.feature().create("mir_cc_h2_bottom","Mirror");geom.feature("mir_cc_h2_bottom").selection("input").set("mov_cc_h2_top");geom.feature("mir_cc_h2_bottom").set("pos",new double[]{0,0,0});geom.feature("mir_cc_h2_bottom").set("axis",new double[]{0,0,1});
        block(geom,"blk_h2_closure",new String[]{"108[mm]","108[mm]","23[mm]"},new String[]{"-54[mm]","-54[mm]","-28[mm]"});
        geom.feature().create("dif_h2_exact","Difference");geom.feature("dif_h2_exact").selection("input").set("blk_h2_closure");geom.feature("dif_h2_exact").selection("input2").set("mir_cc_h2_bottom");geom.feature("dif_h2_exact").set("intbnd","off");
        geom.feature("fin").set("action","union");geom.run();geom.check();if(!geom.hasCadRep())throw new IllegalStateException("H2_CAD_REPRESENTATION_LOST");
        box3(m,c,"sel_dom_h2_channel",3,"-25[mm]","-23[mm]","23[mm]","25[mm]","-27.9[mm]","-26[mm]");
        adjacent(m,c,"sel_bnd_h2_all","sel_dom_h2_channel");
        box3(m,c,"sel_bnd_h2_inlet_raw",2,"-27.3[mm]","-20.7[mm]","20.7[mm]","27.3[mm]","-28.01[mm]","-27.99[mm]");
        intersect(m,c,"sel_bnd_h2_inlet",2,"sel_bnd_h2_all","sel_bnd_h2_inlet_raw");
        box3(m,c,"sel_bnd_h2_outlet_raw",2,"20.7[mm]","27.3[mm]","-27.3[mm]","-20.7[mm]","-28.01[mm]","-27.99[mm]");
        intersect(m,c,"sel_bnd_h2_outlet",2,"sel_bnd_h2_all","sel_bnd_h2_outlet_raw");
        box3(m,c,"sel_bnd_h2_ssc_raw",2,"-27.3[mm]","27.3[mm]","-27.3[mm]","27.3[mm]","-5.01[mm]","-4.99[mm]");
        intersect(m,c,"sel_bnd_h2_ssc",2,"sel_bnd_h2_all","sel_bnd_h2_ssc_raw");
        difference(m,c,"sel_bnd_h2_walls","sel_bnd_h2_all",new String[]{"sel_bnd_h2_inlet","sel_bnd_h2_outlet","sel_bnd_h2_ssc"});
        require(m,c,"sel_dom_h2_channel",3);require(m,c,"sel_bnd_h2_inlet",2);require(m,c,"sel_bnd_h2_outlet",2);require(m,c,"sel_bnd_h2_ssc",2);

        m.component(c).material().create("mat_h2","Common");m.component(c).material("mat_h2").label("PROVISIONAL H2 fluid properties");m.component(c).material("mat_h2").selection().named("sel_dom_h2_channel");m.component(c).material("mat_h2").propertyGroup("def").set("density","rho_H2");m.component(c).material("mat_h2").propertyGroup("def").set("dynamicviscosity","mu_H2");
        m.component(c).physics().create("spf_h2","LaminarFlow",g);m.component(c).physics("spf_h2").selection().named("sel_dom_h2_channel");m.component(c).physics("spf_h2").field("pressure").field("pH2");
        m.component(c).physics("spf_h2").create("in_h2","Inlet",2);m.component(c).physics("spf_h2").feature("in_h2").selection().named("sel_bnd_h2_inlet");m.component(c).physics("spf_h2").feature("in_h2").set("BoundaryCondition","LaminarInflow");m.component(c).physics("spf_h2").feature("in_h2").set("LaminarInflowOption","V0");m.component(c).physics("spf_h2").feature("in_h2").set("V0","Q_H2_lab");
        m.component(c).physics("spf_h2").create("out_h2","Outlet",2);m.component(c).physics("spf_h2").feature("out_h2").selection().named("sel_bnd_h2_outlet");m.component(c).physics("spf_h2").feature("out_h2").set("BoundaryCondition","LaminarOutflow");m.component(c).physics("spf_h2").feature("out_h2").set("LaminarOutflowOption","p0_exit");m.component(c).physics("spf_h2").feature("out_h2").set("p0_exit","p_out");
        for(String[] wall:new String[][]{{"wall_h2","sel_bnd_h2_walls"},{"ssc_wall_h2","sel_bnd_h2_ssc"}}){m.component(c).physics("spf_h2").create(wall[0],"Wall",2);m.component(c).physics("spf_h2").feature(wall[0]).selection().named(wall[1]);}
        m.component(c).mesh().create("mesh_h2");m.component(c).mesh("mesh_h2").autoMeshSize(6);m.component(c).mesh("mesh_h2").create("ftet","FreeTet");m.component(c).mesh("mesh_h2").feature("ftet").selection().named("sel_dom_h2_channel");m.component(c).mesh("mesh_h2").run();
        final String data=study(m,"std_h2","stat","Stationary",new String[]{"spf_h2"});
        final String speed=nativeVariable(m.component(c).physics("spf_h2"),".U");
        final String press="pH2";
        final double dp=surfaceComp(m,"AvSurface",data,c,"sel_bnd_h2_inlet",2,press,"Pa")-surfaceComp(m,"AvSurface",data,c,"sel_bnd_h2_outlet",2,press,"Pa");
        final double h2Bal=flowBalance(m,data,"sel_bnd_h2_inlet","sel_bnd_h2_outlet","u3*nx+v3*ny+w3*nz","Q_H2_lab");
        if(!finite(dp)||dp<=0||h2Bal>1e-3)throw new IllegalStateException("H2_FLOW_AUDIT_INVALID: dp="+dp+" balance="+h2Bal);
        m.param().set("dp_H2_cell_audit",f(dp)+"[Pa]","Computed real-CAD H2-cell pressure drop at LAB_MANUAL flow");
        audit("real_cad_flow,h2_dp_Pa,"+f(dp));audit("real_cad_flow,h2_mass_balance_relative,"+f(h2Bal));audit("real_cad_flow,h2_speed_max_m_s,"+f(surfaceComp(m,"MaxVolume",data,c,"sel_dom_h2_channel",3,speed,"m/s")));
        System.out.println("M10A2_H2_REAL_CAD_MIRROR|dp_Pa="+f(dp)+"|mass_balance_relative="+f(h2Bal)+"|dataset="+data+"|speed="+speed+"|pressure="+press+"|status=PASS");
    }

    private static double[] buildAndSolvePipeNetworks(Model m) {
        final double n2=pipe(m,"comp_pipe_n2","geom_pipe_n2","pfl_n2","std_pipe_n2","N2 outlet PFA | inlet length CALIBRATION_REQUIRED","Q_N2_lab","rho_N2","mu_N2","gas_pfa_id",new double[][]{{0,0},{0.20,0}},null);
        final double h2=pipe(m,"comp_pipe_h2","geom_pipe_h2","pfl_h2","std_pipe_h2","H2 outlet PFA | inlet length CALIBRATION_REQUIRED","Q_H2_lab","rho_H2","mu_H2","gas_pfa_id",new double[][]{{0,0},{0.20,0}},null);
        final double liq=pipe(m,"comp_pipe_liq","geom_pipe_liq","pfl_liq","std_pipe_liq","Liquid loop known PFA segments | latex length excluded","Q_liq_sweep","rho_liq","mu_liq","liq_pfa_id",new double[][]{{0,0},{0.20,0},{0.20,0.15},{0.05,0.15},{0.05,0.05}},"liq_pfa_id_large");
        final double an2=m.param().evaluate("dp_N2_pipe","Pa"),ah2=m.param().evaluate("dp_H2_pipe","Pa"),aliq=m.param().evaluate("dp_liq_pipe","Pa");
        checkRelative("N2_PIPE_HP",n2,an2,0.08);checkRelative("H2_PIPE_HP",h2,ah2,0.08);checkRelative("LIQ_PIPE_HP",liq,aliq,0.08);
        m.param().set("dp_N2_total_known","dp_N2_cell_audit+dp_N2_pipe","Known N2 pressure budget: real cell plus documented outlet PFA");
        m.param().set("dp_H2_total_known","dp_H2_cell_audit+dp_H2_pipe","Known H2 pressure budget: real cell plus documented outlet PFA");
        m.param().set("dp_liq_total_known","dp_liq_cell_audit+dp_liq_pipe","Known liquid pressure budget: real cell plus documented PFA; latex excluded");
        audit("pipe,n2_dp_Pa,"+f(n2));audit("pipe,h2_dp_Pa,"+f(h2));audit("pipe,liquid_known_dp_Pa,"+f(liq));
        audit("pressure_budget,n2_total_known_Pa,"+f(m.param().evaluate("dp_N2_total_known","Pa")));
        audit("pressure_budget,h2_total_known_Pa,"+f(m.param().evaluate("dp_H2_total_known","Pa")));
        audit("pressure_budget,liquid_total_known_Pa,"+f(m.param().evaluate("dp_liq_total_known","Pa")));
        System.out.println("M10A2_PIPE_NETWORK|n2_dp_Pa="+f(n2)+"|h2_dp_Pa="+f(h2)+"|liquid_known_dp_Pa="+f(liq)+"|latex_length=CALIBRATION_REQUIRED|inlet_gas_lengths=CALIBRATION_REQUIRED");
        return new double[]{n2,h2,liq};
    }

    private static double pipe(Model m,String c,String g,String phys,String std,String label,String q,String rho,String mu,String diameter,double[][] points,String largeDiameter) {
        final String edges="sel_"+phys+"_edges",inSel="sel_"+phys+"_in",outSel="sel_"+phys+"_out",largeSel="sel_"+phys+"_large";
        m.component().create(c,true);m.component(c).label(label+" | 3D routing SCHEMATIC_ONLY");final GeomSequence geom=m.component(c).geom().create(g,2);geom.lengthUnit("m");
        geom.feature().create("route","Polygon");geom.feature("route").set("type","open");double[] x=new double[points.length],y=new double[points.length];for(int i=0;i<points.length;i++){x[i]=points[i][0];y[i]=points[i][1];}geom.feature("route").set("x",x);geom.feature("route").set("y",y);geom.run();
        box2(m,c,edges,1,"-1e-6[m]","0.201[m]","-1e-6[m]","0.151[m]");box2(m,c,inSel,0,"-1e-6[m]","1e-6[m]","-1e-6[m]","1e-6[m]");
        final double[] end=points[points.length-1];box2(m,c,outSel,0,f(end[0]-1e-6)+"[m]",f(end[0]+1e-6)+"[m]",f(end[1]-1e-6)+"[m]",f(end[1]+1e-6)+"[m]");
        if(largeDiameter!=null)box2(m,c,largeSel,1,"0.049999[m]","0.050001[m]","0.049999[m]","0.150001[m]");
        require(m,c,edges,1);require(m,c,inSel,0);require(m,c,outSel,0);
        m.component(c).physics().create(phys,"PipeFlow",g);final Physics p=m.component(c).physics(phys);final String pressureName="p_"+phys;p.field("pressure").field(pressureName);p.selection().named(edges);p.feature("fp1").set("rho_mat","userdef");p.feature("fp1").set("rho",rho);p.feature("fp1").set("mu_mat","userdef");p.feature("fp1").set("mu",mu);p.feature("pipe1").set("shape","Round");p.feature("pipe1").set("innerd",diameter);
        if(largeDiameter!=null){p.create("pipe_large","PipeProperties",1);p.feature("pipe_large").selection().named(largeSel);p.feature("pipe_large").set("shape","Round");p.feature("pipe_large").set("innerd",largeDiameter);}
        p.create("inlet","Inlet",0);p.feature("inlet").selection().named(inSel);p.feature("inlet").set("spec","1");p.feature("inlet").set("qv0",q);p.create("outlet","Pressure",0);p.feature("outlet").selection().named(outSel);p.feature("outlet").set("p0","p_out");
        final String meshTag="mesh_"+phys;m.component(c).mesh().create(meshTag);m.component(c).mesh(meshTag).create("edge","Edge");m.component(c).mesh(meshTag).feature("edge").selection().named(edges);m.component(c).mesh(meshTag).autoMeshSize(4);m.component(c).mesh(meshTag).run();
        final String data=study(m,std,"stat","Stationary",new String[]{phys});final String pressure=pressureName;
        final double inlet=point(m,"EvalPoint",data,c,inSel,pressure,"Pa"),outlet=point(m,"EvalPoint",data,c,outSel,pressure,"Pa");
        final String varTag="var_"+phys+"_audit";m.component(c).variable().create(varTag);m.component(c).variable(varTag).set("pipe_dp_"+phys,f(inlet-outlet)+"[Pa]","Solved external pipe pressure drop");
        System.out.println("M10A2_PIPE_SOLVE|component="+c+"|dataset="+data+"|pressure="+pressure+"|dp_Pa="+f(inlet-outlet)+"|status=PASS");
        return inlet-outlet;
    }

    private static double[] buildAndSolvePorousSSC(Model m) {
        final String c="comp_ssc_darcy",g="geom_ssc_darcy",phys="dl_ssc";rectangle(m,c,g,"t_ssc","1[mm]","darcy");
        m.component(c).physics().create(phys,"PorousMediaFlowDarcy",g);final Physics p=m.component(c).physics(phys);p.selection().named("sel_darcy_domain");
        p.field("pressure").field("pDarcy");p.feature("porous1").set("flowModelType","darcian");p.feature("porous1").set("fluidType","incompressible");p.feature("porous1").feature("fluid1").set("rho_mat","userdef");p.feature("porous1").feature("fluid1").set("rho","rho_liq");p.feature("porous1").feature("fluid1").set("mu_mat","userdef");p.feature("porous1").feature("fluid1").set("mu","mu_liq");p.feature("porous1").feature("pm1").set("epsilon_mat","userdef");p.feature("porous1").feature("pm1").set("epsilon","eps_ssc");p.feature("porous1").feature("pm1").set("kappa_mat","userdef");p.feature("porous1").feature("pm1").set("kappa","K_ssc");
        p.create("p_liq","Pressure",1);p.feature("p_liq").selection().named("sel_darcy_left");p.feature("p_liq").set("p0","dp_ssc");p.create("p_gas","Pressure",1);p.feature("p_gas").selection().named("sel_darcy_right");p.feature("p_gas").set("p0","p_out");mapped(m,c,"mesh_darcy","sel_darcy_domain","sel_darcy_bottom","sel_darcy_left",60,6);
        String data=null;double min=Double.POSITIVE_INFINITY,max=0;for(double k:new double[]{1e-13,1e-12,1e-11,1e-10,1e-9}){m.param().set("K_ssc",f(k)+"[m^2]");if(data==null)data=study(m,"std_ssc_darcy","stat","Stationary",new String[]{phys});else m.study("std_ssc_darcy").run();double solved=surfaceComp(m,"AvLine",data,c,"sel_darcy_left",1,"pDarcy","Pa")-surfaceComp(m,"AvLine",data,c,"sel_darcy_right",1,"pDarcy","Pa");double u=m.param().evaluate("u_ssc_darcy","m/s");if(!finite(solved)||Math.abs(solved-1500)>1e-3)throw new IllegalStateException("SSC_DARCY_DP_INVALID");min=Math.min(min,u);max=Math.max(max,u);audit("porous_ssc,K_m2="+f(k)+",dp_Pa="+f(solved)+",u_superficial_m_s="+f(u));System.out.println("M10A2_POROUS_SWEEP|K_m2="+f(k)+"|dp_Pa="+f(solved)+"|u_m_s="+f(u));}
        m.param().set("K_ssc","1e-11[m^2]");m.study("std_ssc_darcy").run();System.out.println("M10A2B_POROUS_SSC_SWEEP=PASS");return new double[]{1500,min,max};
    }

    private static double[] buildAndSolveWetting(Model m) {
        m.param().set("K_wet","1e-13[m^2]","PROVISIONAL_SENSITIVITY wetting case at primary Darcy sweep lower edge");
        m.param().set("u_wet_in","K_wet*Sl_initial^2/(mu_liq*t_ssc)*if(dp_ssc>pc_ssc,dp_ssc-pc_ssc,0[Pa])","Pressure-entry wetting drive including the declared initial liquid relative permeability k_r=Sl_initial^2");
        final String c="comp_ssc_wetting",g="geom_ssc_wetting";rectangle(m,c,g,"t_ssc","1[mm]","wet");
        final Physics ph=m.component(c).physics().create("phtr_ssc","PhaseTransportPorous",g);ph.selection().named("sel_wet_domain");ph.feature("porous1").set("capillarypressuremodelPM","BrooksCorey");ph.feature("porous1").feature("fluid1").set("capillarypressuremodel","BrooksCorey");ph.feature("porous1").feature("fluid1").set("WettingPhase","1");ph.feature("porous1").feature("fluid1").set("pec","pc_ssc");ph.feature("porous1").feature("fluid1").set("lambdap","2");ph.feature("porous1").feature("fluid1").set("kappar_s1","s1^2");ph.feature("porous1").feature("fluid1").set("kappar_s2","s2^2");ph.feature("porous1").feature("fluid1").set("rhoint_s1_mat","userdef");ph.feature("porous1").feature("fluid1").set("rhoint_s1","rho_N2");ph.feature("porous1").feature("fluid1").set("mu_s1_mat","userdef");ph.feature("porous1").feature("fluid1").set("mu_s1","mu_N2");ph.feature("porous1").feature("fluid1").set("rhoint_s2_mat","userdef");ph.feature("porous1").feature("fluid1").set("rhoint_s2","rho_liq");ph.feature("porous1").feature("fluid1").set("mu_s2_mat","userdef");ph.feature("porous1").feature("fluid1").set("mu_s2","mu_liq");ph.feature("porous1").feature("pm1").set("epsilon_p_mat","userdef");ph.feature("porous1").feature("pm1").set("epsilon_p","eps_ssc");ph.feature("porous1").feature("pm1").set("kappa_mat","userdef");ph.feature("porous1").feature("pm1").set("kappa","K_wet");ph.feature("init1").set("s0",new String[]{"1-Sl_initial","Sl_initial"});ph.create("liq_in","MassFlux",1);ph.feature("liq_in").selection().named("sel_wet_left");ph.feature("liq_in").set("phases",new int[]{0,1});ph.feature("liq_in").set("q0",new String[]{"0[kg/(m^2*s)]","rho_liq*u_wet_in"});
        final Physics dl=m.component(c).physics().create("dl_wet","PorousMediaFlowDarcy",g);dl.selection().named("sel_wet_domain");dl.field("pressure").field("pWet");dl.feature("porous1").set("flowModelType","darcian");dl.feature("porous1").set("fluidType","incompressible");dl.feature("porous1").feature("fluid1").set("rho_mat","userdef");dl.feature("porous1").feature("fluid1").set("rho","rho_liq");dl.feature("porous1").feature("fluid1").set("mu_mat","userdef");dl.feature("porous1").feature("fluid1").set("mu","mu_liq");dl.feature("porous1").feature("pm1").set("epsilon_mat","userdef");dl.feature("porous1").feature("pm1").set("epsilon","eps_ssc");dl.feature("porous1").feature("pm1").set("kappa_mat","userdef");dl.feature("porous1").feature("pm1").set("kappa","K_wet");dl.create("p_liq","Pressure",1);dl.feature("p_liq").selection().named("sel_wet_left");dl.feature("p_liq").set("p0","dp_ssc");dl.create("p_gas","Pressure",1);dl.feature("p_gas").selection().named("sel_wet_right");dl.feature("p_gas").set("p0","p_out");
        m.component(c).multiphysics().create("mfpm_ssc","MultiphaseFlowInPorousMedia",2);m.component(c).multiphysics("mfpm_ssc").selection().named("sel_wet_domain");m.component(c).multiphysics("mfpm_ssc").set("multiphaseflow_physics","phtr_ssc");m.component(c).multiphysics("mfpm_ssc").set("darcyc_physics","dl_wet");
        mapped(m,c,"mesh_wet","sel_wet_domain","sel_wet_bottom","sel_wet_left",80,6);
        final double[] pressure={0,500,1000,1500,2000,3000};String data=null;double globalMin=1,globalMax=0,lastBreak=0;for(double dp:pressure){m.param().set("dp_ssc",f(dp)+"[Pa]");if(data==null)data=study(m,"std_ssc_wetting","time","Transient",new String[]{"phtr_ssc","dl_wet"});else m.study("std_ssc_wetting").run();double smin=surfaceComp(m,"MinSurface",data,c,"sel_wet_domain",2,"s2","1"),smax=surfaceComp(m,"MaxSurface",data,c,"sel_wet_domain",2,"s2","1"),br=surfaceComp(m,"AvLine",data,c,"sel_wet_right",1,"s2","1");globalMin=Math.min(globalMin,smin);globalMax=Math.max(globalMax,smax);lastBreak=br;if(!finite(smin)||!finite(smax)||smin<-1e-6||smax>1.000001)throw new IllegalStateException("WETTING_SATURATION_INVALID");audit("wetting,dp_Pa="+f(dp)+",Sl_min="+f(smin)+",Sl_max="+f(smax)+",gas_side_Sl="+f(br));System.out.println("M10A2_WETTING_SWEEP|dp_mbar="+f(dp/100)+"|Sl_min="+f(smin)+"|Sl_max="+f(smax)+"|gas_side_Sl="+f(br));}
        m.param().set("wet_Sl_min_audit",f(globalMin),"Computed minimum over 0-30 mbar wetting sweep");m.param().set("wet_Sl_max_audit",f(globalMax),"Computed maximum over 0-30 mbar wetting sweep");m.param().set("wet_breakthrough_Sl_audit",f(lastBreak),"Gas-side liquid saturation at 30 mbar and 5 ms");m.param().set("dp_ssc","1500[Pa]");m.study("std_ssc_wetting").run();System.out.println("M10A2C_WETTING_LICENSED_INTERFACE=PASS");return new double[]{globalMin,globalMax,lastBreak};
    }

    private static double[] buildAndSolveN2Transfer(Model m) {
        final String c="comp_n2_transfer",g="geom_n2_transfer";m.component().create(c,true);m.component(c).label("Gas-to-liquid N2 transfer | phenomenological equilibrium baseline");final GeomSequence geom=m.component(c).geom().create(g,2);geom.lengthUnit("mm");
        geom.feature().create("ssc","Rectangle");geom.feature("ssc").set("size",new String[]{"t_ssc","W_transfer"});geom.feature().create("liq","Rectangle");geom.feature("liq").set("pos",new String[]{"t_ssc","0"});geom.feature("liq").set("size",new String[]{"L_electrolyte_transfer","W_transfer"});geom.feature("fin").set("action","union");geom.run();
        box2(m,c,"sel_n2_all",2,"-1e-6[mm]","t_ssc+L_electrolyte_transfer+1e-6[mm]","-1e-6[mm]","W_transfer+1e-6[mm]");box2(m,c,"sel_n2_ssc",2,"-1e-6[mm]","t_ssc-1e-6[um]","-1e-6[mm]","W_transfer+1e-6[mm]");box2(m,c,"sel_n2_liq",2,"t_ssc+1e-6[um]","t_ssc+L_electrolyte_transfer+1e-6[mm]","-1e-6[mm]","W_transfer+1e-6[mm]");box2(m,c,"sel_n2_gas",1,"-1e-6[mm]","1e-6[mm]","-1e-6[mm]","W_transfer+1e-6[mm]");box2(m,c,"sel_n2_interface",1,"t_ssc-1e-6[mm]","t_ssc+1e-6[mm]","-1e-6[mm]","W_transfer+1e-6[mm]");box2(m,c,"sel_n2_bulk",1,"t_ssc+L_electrolyte_transfer-1e-6[mm]","t_ssc+L_electrolyte_transfer+1e-6[mm]","-1e-6[mm]","W_transfer+1e-6[mm]");box2(m,c,"sel_n2_bottom",1,"-1e-6[mm]","t_ssc+L_electrolyte_transfer+1e-6[mm]","-1e-6[mm]","1e-6[mm]");
        m.component(c).variable().create("var_n2_transfer");m.component(c).variable("var_n2_transfer").selection().named("sel_n2_all");m.component(c).variable("var_n2_transfer").set("D_N2_local","if(x<t_ssc,D_N2_ssc_eff,D_N2_liq)","Explicit SSC/electrolyte piecewise effective diffusivity");
        final Physics t=m.component(c).physics().create("tds_n2","DilutedSpecies",g,new String[]{"cN2d"});t.selection().named("sel_n2_all");t.feature("cdm1").set("D_cN2d_mat","userdef");t.feature("cdm1").set("D_cN2d","D_N2_local");t.create("gas_eq","Concentration",1);t.feature("gas_eq").selection().named("sel_n2_gas");t.feature("gas_eq").set("species",new int[]{1});t.feature("gas_eq").set("c0",new String[]{"c_N2_eq"});t.create("bulk_sink","Concentration",1);t.feature("bulk_sink").selection().named("sel_n2_bulk");t.feature("bulk_sink").set("species",new int[]{1});t.feature("bulk_sink").set("c0",new String[]{"c_N2_bulk"});
        mapped(m,c,"mesh_n2_transfer","sel_n2_all","sel_n2_bottom","sel_n2_gas",160,20);final String data=study(m,"std_n2_transfer","stat","Stationary",new String[]{"tds_n2"});
        final double in=-line(m,data,c,"sel_n2_gas","nx*(-D_N2_ssc_eff*cN2dx)*W_transfer","mol/s"),out=line(m,data,c,"sel_n2_bulk","nx*(-D_N2_liq*cN2dx)*W_transfer","mol/s");final double residualMol=in-out;final double residualRelative=Math.abs(residualMol)/Math.max(Math.max(Math.abs(in),Math.abs(out)),1e-300);final double ci=surfaceComp(m,"AvLine",data,c,"sel_n2_interface",1,"cN2d","mol/m^3");final double cmin=surfaceComp(m,"MinSurface",data,c,"sel_n2_all",2,"cN2d","mol/m^3");final double cmax=surfaceComp(m,"MaxSurface",data,c,"sel_n2_all",2,"cN2d","mol/m^3");final double pe=m.param().evaluate("u_ssc_transport*t_ssc/D_N2_ssc_eff","1");final double keff=in/(m.param().evaluate("A_ssc_active","m^2")*m.param().evaluate("c_N2_eq","mol/m^3"));if(residualRelative>1e-6||cmin<-1e-9||!finite(in)||!finite(ci))throw new IllegalStateException("N2_TRANSFER_CONSERVATION");m.param().set("n2_transfer_rate_audit",f(in)+"[mol/s]","Computed phenomenological N2 transfer rate");m.param().set("n2_interface_c_audit",f(ci)+"[mol/m^3]","Computed future cathodic-interface concentration");m.param().set("n2_residual_mol_s_audit",f(residualMol)+"[mol/s]","N atom balance: inlet minus outlet minus zero stationary accumulation");m.param().set("n2_residual_audit",f(residualRelative),"N atom conservation relative residual");m.param().set("n2_Pe_audit",f(pe),"Computed SSC Peclet sensitivity including k_r=Sl^2");m.param().set("n2_keff_audit",f(keff)+"[m/s]","Computed effective mass-transfer coefficient");audit("n2_transfer,in_mol_s,"+f(in));audit("n2_transfer,out_mol_s,"+f(out));audit("n2_transfer,accumulation_mol_s,0");audit("n2_transfer,residual_mol_s,"+f(residualMol));audit("n2_transfer,residual_relative,"+f(residualRelative));audit("n2_transfer,interface_c_mol_m3,"+f(ci));audit("n2_transfer,Pe,"+f(pe));audit("n2_transfer,k_eff_m_s,"+f(keff));System.out.println("M10A2_N2_TRANSFER|in_mol_s="+f(in)+"|out_mol_s="+f(out)+"|accumulation_mol_s=0|residual_mol_s="+f(residualMol)+"|residual_relative="+f(residualRelative)+"|interface_c_mol_m3="+f(ci)+"|min="+f(cmin)+"|max="+f(cmax)+"|Pe="+f(pe)+"|k_eff_m_s="+f(keff));return new double[]{in,ci,residualRelative,pe,keff};
    }

    private static void createResults(Model m) {
        m.result("pg_n2_velocity").label("N2 Channel Velocity");m.result("pg_n2_pressure").label("Gas Pressure");m.result("pg_liq_velocity").label("Electrolyte Velocity");m.result("pg_liq_pressure").label("Electrolyte Pressure");
        final String h2data=datasetForStudy(m,"std_h2"),h2u=nativeVariable(m.component("comp_h2_flow").physics("spf_h2"),".U"),h2p="pH2";surfacePlot3D(m,"pg_h2_velocity","H2 Channel Velocity",h2data,h2u,"m/s");surfacePlot3D(m,"pg_h2_pressure","H2 Gas Pressure",h2data,h2p,"Pa");
        final String pipeData=datasetForStudy(m,"std_pipe_n2"),pipeP="p_pfl_n2";linePlot(m,"pg_pipe_dp","External Pipe Pressure Drop",pipeData,pipeP,"Pa");
        surfacePlot(m,"pg_ssc_dp","Cathode SSC Pressure Difference",datasetForStudy(m,"std_ssc_darcy"),"pDarcy","Pa");surfacePlot(m,"pg_ssc_velocity","Cathode SSC Superficial Velocity",datasetForStudy(m,"std_ssc_darcy"),"u_ssc_darcy","m/s");surfacePlot(m,"pg_ssc_sl","Cathode SSC Liquid Saturation",datasetForStudy(m,"std_ssc_wetting"),"s2","1");surfacePlot(m,"pg_ssc_sg","Cathode SSC Gas Saturation",datasetForStudy(m,"std_ssc_wetting"),"s1","1");surfacePlot(m,"pg_penetration","Liquid Penetration Depth",datasetForStudy(m,"std_ssc_wetting"),"if(s2>=Sl_breakthrough,x,0[m])","m");
        final String wet=datasetForStudy(m,"std_ssc_wetting");m.result().create("pg_flood_front","PlotGroup2D");m.result("pg_flood_front").label("Flooding Front");m.result("pg_flood_front").set("data",wet);m.result("pg_flood_front").create("cont","Contour");m.result("pg_flood_front").feature("cont").set("expr","s2");m.result("pg_flood_front").feature("cont").set("levels",new double[]{m.param().evaluate("Sl_breakthrough")});
        final String n2=datasetForStudy(m,"std_n2_transfer");surfacePlot(m,"pg_n2_conc","Dissolved N2 Concentration",n2,"cN2d","mol/m^3");surfacePlot(m,"pg_n2_flux","N2 Flux Through SSC",n2,"sqrt((D_N2_local*cN2dx)^2+(D_N2_local*cN2dy)^2)","mol/(m^2*s)");
        System.out.println("M10A2_RESULTS_CREATE=PASS");
    }

    private static void independentReload(String output) throws Exception {
        final Model m=ModelUtil.load("M10A2Reload",output);try{for(String tag:new String[]{"pg_liq_velocity","pg_liq_pressure","pg_liq_streamlines","pg_liq_wall_shear","pg_n2_velocity","pg_n2_pressure","pg_n2_streamlines","pg_n2_wall_shear","pg_h2_velocity","pg_h2_pressure","pg_pipe_dp","pg_ssc_dp","pg_ssc_velocity","pg_ssc_sl","pg_ssc_sg","pg_penetration","pg_flood_front","pg_n2_conc","pg_n2_flux"}){if(!m.result().hasTag(tag))throw new IllegalStateException("RESULT_MISSING_AFTER_RELOAD: "+tag);m.result(tag).run();if(m.result(tag).hasWarning())throw new IllegalStateException("RESULT_WARNING_AFTER_RELOAD: "+tag);System.out.println("M10A2_RESULT_RELOAD|tag="+tag+"|status=PASS");}System.out.println("M10A2_INDEPENDENT_MPH_RELOAD=PASS");}finally{ModelUtil.remove("M10A2Reload");}
    }

    // ---- COMSOL construction/evaluation helpers ----
    private static void p(Model m,String n,String v,String d){m.param().set(n,v,d);}
    private static void block(GeomSequence g,String tag,String[] size,String[] pos){g.feature().create(tag,"Block");g.feature(tag).set("base","corner");g.feature(tag).set("size",size);g.feature(tag).set("pos",pos);}
    private static void strictImport(GeomSequence g,String tag,String file){g.feature().create(tag,"Import");g.feature(tag).set("filename",file);g.feature(tag).set("unit","source");g.feature(tag).set("keepsolid","on");g.feature(tag).set("keepbnd","on");g.feature(tag).set("keepfree","off");g.feature(tag).set("knit","solid");g.feature(tag).set("fillholes","off");g.feature(tag).set("removeredundant","off");g.feature(tag).set("simplify","off");g.feature(tag).set("deletedetails","off");g.feature(tag).set("healedges","off");g.feature(tag).set("minimizetol","off");g.feature(tag).set("importbodynames","on");g.feature(tag).set("check","on");g.feature(tag).set("fixerrors","off");g.feature(tag).importData();}
    private static void rectangle(Model m,String c,String g,String w,String h,String stem){m.component().create(c,true);m.component(c).label("Homogenized SSC through-plane "+stem+" component");GeomSequence geom=m.component(c).geom().create(g,2);geom.lengthUnit("mm");geom.feature().create("rect","Rectangle");geom.feature("rect").set("size",new String[]{w,h});geom.run();box2(m,c,"sel_"+stem+"_domain",2,"-1e-6[mm]",w+"+1e-6[mm]","-1e-6[mm]",h+"+1e-6[mm]");box2(m,c,"sel_"+stem+"_left",1,"-1e-6[mm]","1e-6[mm]","-1e-6[mm]",h+"+1e-6[mm]");box2(m,c,"sel_"+stem+"_right",1,w+"-1e-6[mm]",w+"+1e-6[mm]","-1e-6[mm]",h+"+1e-6[mm]");box2(m,c,"sel_"+stem+"_bottom",1,"-1e-6[mm]",w+"+1e-6[mm]","-1e-6[mm]","1e-6[mm]");require(m,c,"sel_"+stem+"_domain",2);}
    private static void box2(Model m,String c,String tag,int dim,String xmin,String xmax,String ymin,String ymax){m.component(c).selection().create(tag,"Box");m.component(c).selection(tag).set("entitydim",dim);m.component(c).selection(tag).set("condition","inside");m.component(c).selection(tag).set("xmin",xmin);m.component(c).selection(tag).set("xmax",xmax);m.component(c).selection(tag).set("ymin",ymin);m.component(c).selection(tag).set("ymax",ymax);}
    private static void box3(Model m,String c,String tag,int dim,String xmin,String xmax,String ymin,String ymax,String zmin,String zmax){m.component(c).selection().create(tag,"Box");m.component(c).selection(tag).set("entitydim",dim);m.component(c).selection(tag).set("condition","intersects");m.component(c).selection(tag).set("xmin",xmin);m.component(c).selection(tag).set("xmax",xmax);m.component(c).selection(tag).set("ymin",ymin);m.component(c).selection(tag).set("ymax",ymax);m.component(c).selection(tag).set("zmin",zmin);m.component(c).selection(tag).set("zmax",zmax);}
    private static void adjacent(Model m,String c,String tag,String input){m.component(c).selection().create(tag,"Adjacent");m.component(c).selection(tag).set("entitydim",3);m.component(c).selection(tag).set("outputdim",2);m.component(c).selection(tag).set("input",new String[]{input});m.component(c).selection(tag).set("exterior","on");m.component(c).selection(tag).set("interior","off");}
    private static void intersect(Model m,String c,String tag,int dim,String...input){m.component(c).selection().create(tag,"Intersection");m.component(c).selection(tag).set("entitydim",dim);m.component(c).selection(tag).set("input",input);}
    private static void difference(Model m,String c,String tag,String add,String[] subtract){m.component(c).selection().create(tag,"Difference");m.component(c).selection(tag).set("entitydim",2);m.component(c).selection(tag).set("add",new String[]{add});m.component(c).selection(tag).set("subtract",subtract);}
    private static void require(Model m,String c,String s,int dim){int n=m.component(c).selection(s).entities(dim).length;if(n==0)throw new IllegalStateException("NAMED_SELECTION_EMPTY: "+c+"/"+s);}
    private static void mapped(Model m,String c,String mesh,String dom,String bottom,String left,int nx,int ny){m.component(c).mesh().create(mesh);m.component(c).mesh(mesh).create("map","Map");m.component(c).mesh(mesh).feature("map").selection().named(dom);m.component(c).mesh(mesh).feature("map").create("dx","Distribution");m.component(c).mesh(mesh).feature("map").feature("dx").selection().named(bottom);m.component(c).mesh(mesh).feature("map").feature("dx").set("type","number");m.component(c).mesh(mesh).feature("map").feature("dx").set("numelem",nx);m.component(c).mesh(mesh).feature("map").create("dy","Distribution");m.component(c).mesh(mesh).feature("map").feature("dy").selection().named(left);m.component(c).mesh(mesh).feature("map").feature("dy").set("type","number");m.component(c).mesh(mesh).feature("map").feature("dy").set("numelem",ny);m.component(c).mesh(mesh).run();}
    private static String study(Model m,String tag,String step,String type,String[] active){m.study().create(tag);m.study(tag).create(step,type);if("Transient".equals(type))m.study(tag).feature(step).set("tlist","range(0,0.0001[s],0.005[s])");String activeComponent=null;for(String c:m.component().tags())for(String p:m.component(c).physics().tags()){m.study(tag).feature(step).activate(p,contains(active,p));if(contains(active,p))activeComponent=c;}m.study(tag).run();String[] solvers=m.study(tag).getSolverSequences("SolverSequence");if(solvers.length<1||activeComponent==null)throw new IllegalStateException("STUDY_SOLUTION_MAPPING_FAILED: "+tag);String d="dset_"+tag;if(m.result().dataset().hasTag(d))m.result().dataset().remove(d);m.result().dataset().create(d,"Solution");m.result().dataset(d).set("solution",solvers[0]);m.result().dataset(d).set("comp",activeComponent);m.result().dataset(d).label("Solution for "+tag);DATASETS.put(tag,d);System.out.println("M10A2_DATASET|study="+tag+"|solution="+solvers[0]+"|component="+activeComponent+"|dataset="+d);return d;}
    private static boolean contains(String[]a,String x){for(String s:a)if(s.equals(x))return true;return false;}
    private static String datasetForStudy(Model m,String study){String data=DATASETS.get(study);if(data!=null&&m.result().dataset().hasTag(data))return data;for(String d:m.result().dataset().tags())if(m.result().dataset(d).label().contains(study))return d;throw new IllegalStateException("DATASET_FOR_STUDY_NOT_FOUND: "+study);}
    private static void rebindDataset(Model m,String study,String data,String component){String[] solvers=m.study(study).getSolverSequences("SolverSequence");if(solvers.length<1)throw new IllegalStateException("NO_SOLVER_FOR_REBIND: "+study);String solver=solvers[solvers.length-1];m.result().dataset(data).set("solution",solver);m.result().dataset(data).set("comp",component);System.out.println("M10A2_DATASET_REBIND|study="+study+"|solution="+solver+"|dataset="+data+"|component="+component+"|solver_count="+solvers.length);}
    private static String nativeVariable(Physics p,String suffix){String[][] table=p.featureInfo("info").getInfoTable("Expression","recursive","all");for(String[]row:table)for(String cell:row)if(cell!=null&&cell.matches("[A-Za-z][A-Za-z0-9_]*\\"+suffix.replace(".",".")))return cell;for(String[]row:table)for(String cell:row)if(cell!=null&&cell.endsWith(suffix)&&cell.matches("[A-Za-z][A-Za-z0-9_]*\\.[A-Za-z][A-Za-z0-9_]*"))return cell;throw new IllegalStateException("NATIVE_VARIABLE_NOT_FOUND: "+p.tag()+suffix);}
    private static double surface(Model m,String type,String data,String sel,String expr,String unit){String tag="eval_"+(++serial);m.result().numerical().create(tag,type);try{m.result().numerical(tag).set("data",data);m.result().numerical(tag).selection().named(sel);m.result().numerical(tag).set("expr",new String[]{expr});m.result().numerical(tag).set("unit",new String[]{unit});return last(m.result().numerical(tag).getReal());}finally{m.result().numerical().remove(tag);}}
    private static double surfaceComp(Model m,String type,String data,String c,String sel,int dim,String expr,String unit){String tag="eval_"+(++serial);m.result().numerical().create(tag,type);try{m.result().numerical(tag).set("data",data);m.result().numerical(tag).selection().set(m.component(c).selection(sel).entities(dim));m.result().numerical(tag).set("expr",new String[]{expr});m.result().numerical(tag).set("unit",new String[]{unit});return last(m.result().numerical(tag).getReal());}finally{m.result().numerical().remove(tag);}}
    private static double point(Model m,String type,String data,String c,String sel,String expr,String unit){String tag="eval_"+(++serial);m.result().numerical().create(tag,type);try{m.result().numerical(tag).set("data",data);m.result().numerical(tag).selection().set(m.component(c).selection(sel).entities(0));m.result().numerical(tag).set("expr",new String[]{expr});m.result().numerical(tag).set("unit",new String[]{unit});return last(m.result().numerical(tag).getReal());}finally{m.result().numerical().remove(tag);}}
    private static double line(Model m,String data,String c,String sel,String expr,String unit){String tag="eval_"+(++serial);m.result().numerical().create(tag,"IntLine");try{m.result().numerical(tag).set("data",data);m.result().numerical(tag).selection().set(m.component(c).selection(sel).entities(1));m.result().numerical(tag).set("expr",new String[]{expr});m.result().numerical(tag).set("unit",new String[]{unit});m.result().numerical(tag).set("intorderactive",true);m.result().numerical(tag).set("intorder",8);return last(m.result().numerical(tag).getReal());}finally{m.result().numerical().remove(tag);}}
    private static double flowBalance(Model m,String data,String in,String out,String normal,String q){double a=line3(m,data,in,normal),b=line3(m,data,out,normal),scale=m.param().evaluate(q,"m^3/s");return Math.abs(a+b)/Math.max(Math.abs(scale),1e-300);}
    private static double line3(Model m,String data,String sel,String expr){String tag="eval_"+(++serial);m.result().numerical().create(tag,"IntSurface");try{m.result().numerical(tag).set("data",data);m.result().numerical(tag).selection().named(sel);m.result().numerical(tag).set("expr",new String[]{expr});m.result().numerical(tag).set("unit",new String[]{"m^3/s"});return last(m.result().numerical(tag).getReal());}finally{m.result().numerical().remove(tag);}}
    private static double last(double[][]v){if(v==null||v.length==0||v[0].length==0)throw new IllegalStateException("EMPTY_NUMERICAL_RESULT");return v[0][v[0].length-1];}
    private static void surfacePlot(Model m,String tag,String label,String data,String expr,String unit){m.result().create(tag,"PlotGroup2D");m.result(tag).label(label);m.result(tag).set("data",data);m.result(tag).create("surf","Surface");m.result(tag).feature("surf").set("expr",expr);m.result(tag).feature("surf").set("unit",unit);}
    private static void surfacePlot3D(Model m,String tag,String label,String data,String expr,String unit){m.result().create(tag,"PlotGroup3D");m.result(tag).label(label);m.result(tag).set("data",data);m.result(tag).create("surf","Surface");m.result(tag).feature("surf").set("expr",expr);m.result(tag).feature("surf").set("unit",unit);}
    private static void linePlot(Model m,String tag,String label,String data,String expr,String unit){m.result().create(tag,"PlotGroup1D");m.result(tag).label(label);m.result(tag).set("data",data);m.result(tag).create("line","LineGraph");m.result(tag).feature("line").set("expr",expr);m.result(tag).feature("line").set("unit",unit);}
    private static void checkRelative(String name,double a,double b,double tol){double r=Math.abs(a-b)/Math.max(Math.max(Math.abs(a),Math.abs(b)),1e-30);System.out.println("M10A2_ANALYTIC_AUDIT|name="+name+"|solved="+f(a)+"|analytic="+f(b)+"|relative="+f(r));if(r>tol)throw new IllegalStateException(name+"_MISMATCH");}
    private static void audit(String s){AUDIT.add(s);}
    private static boolean finite(double x){return Double.isFinite(x);}
    private static String f(double x){return String.format(Locale.ROOT,"%.12g",x);}
    private static String runtime(String n){try{return((String)Class.forName(RT).getField(n).get(null)).trim();}catch(Exception e){throw new IllegalStateException("RUNTIME_INPUT_MISSING: "+n,e);}}
}
