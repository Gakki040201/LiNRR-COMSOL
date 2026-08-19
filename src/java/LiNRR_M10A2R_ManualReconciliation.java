import com.comsol.model.GeomSequence;
import com.comsol.model.Model;
import com.comsol.model.physics.Physics;
import com.comsol.model.util.ModelUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * M10A2R manual reconciliation and physical-3D visualization layer.
 *
 * <p>This updater loads the accepted M10A2 MPH, preserves every solved field and governing
 * equation, clarifies every reduced-order component, and adds a separate physical assembly
 * visualization derived from the real STEP geometry. The PEEK reverse-cone fitting is not
 * modeled because its internal dimensions are not verified by the laboratory Manual.</p>
 */
public final class LiNRR_M10A2R_ManualReconciliation {
    private static final String RT = "LiNRR_M10A2R_RuntimeInputs";
    private static final List<String[]> GEOMETRY_PROVENANCE = new ArrayList<String[]>();
    private static int imageSerial = 0;

    private LiNRR_M10A2R_ManualReconciliation() {}

    public static void main(String[] args) throws Exception {
        final String input = runtime("INPUT_MPH");
        final String output = runtime("OUTPUT_MPH");
        final Path runDir = Paths.get(runtime("RUN_DIR"));
        final Path evidenceDir = Paths.get(runtime("EVIDENCE_DIR"));
        Files.createDirectories(runDir);
        Files.createDirectories(evidenceDir);

        final Model model = ModelUtil.load("M10A2R", input);
        try {
            model.label("LiNRR_M10A2R_manual_reconciled.mph");
            model.comments("M10A2R Manual-reconciled physical visualization. Accepted M10A2 equations and solved "
                + "fields are unchanged. Physical Cell 3D contains real current-collector/chamber STEP solids, "
                + "true-scale 316L SSC and PtAu/316L SSC, two schematic gasket solids, and short PFA 3x2 mm gas "
                + "connection stubs. PEEK fitting internal geometry is NOT MODELED UNLESS VERIFIED. All Pipe Flow, "
                + "Darcy, wetting, and N2-transfer components are explicitly labeled reduced models.");

            defineParameters(model);
            relabelExistingTree(model);
            buildPhysicalCell(model);
            auditGeometryProvenance(model);
            buildPhysicalResults(model);
            reorderResults(model);
            final Map<String,Double> current = evaluateCurrentNumerics(model);
            final Map<String,Double> baseline = verifyInvariance(current);
            exportEvidence(model, evidenceDir, baseline, current);
            exportImages(model, evidenceDir);
            model.save(output);
            System.out.println("M10A2R_MODEL_SAVE=PASS");
        } finally {
            ModelUtil.remove("M10A2R");
        }

        independentReload(output, evidenceDir);
        System.out.println("M10A2R_OVERALL=PASS");
    }

    private static void defineParameters(Model m) {
        m.param().set("L_gas_stub_visual", "15[mm]",
            "SCHEMATIC_VISUAL_ONLY | VISUALIZATION ONLY | NO HYDRAULIC ROLE | not a Manual dimension");
        m.param().set("gas_pfa_id_visual", "2[mm]", "LAB_MANUAL gas PFA bore; hydraulic diameter remains 2 mm");
        m.param().set("gas_pfa_od_visual", "3[mm]", "LAB_MANUAL gas PFA outer diameter; visualization only");
        m.param().set("t_gasket_display", "0.5[mm]",
            "SCHEMATIC_VISUAL_ONLY display thickness; true compressed gasket thickness remains CALIBRATION_REQUIRED");
        m.param().set("ssc_exploded_offset", "12[mm]", "DISPLAY SCALE ONLY | no physical or hydraulic role");
        System.out.println("M10A2R_PARAMETERS=PASS");
    }

    private static void relabelExistingTree(Model m) {
        labelComponent(m,"comp_cc_raw","REAL / PHYSICAL | Raw real CAD - current collector");
        labelComponent(m,"comp_chamber_raw","REAL / PHYSICAL | Raw real CAD - chamber");
        labelComponent(m,"comp_registered","REAL / PHYSICAL | Registered physical cell");
        labelComponent(m,"comp_electrolyte_flow","REAL / PHYSICAL | Electrolyte fluid - real CAD");
        labelComponent(m,"comp_n2_flow","REAL / PHYSICAL | N2 channel - real CAD | 50 cm^3/min");
        labelComponent(m,"comp_h2_flow","REAL / PHYSICAL | H2 channel - mirrored real CAD | 50 cm^3/min");
        labelComponent(m,"comp_ssc_hardware","REAL / PHYSICAL | Physical SSC / gasket assembly");

        labelComponent(m,"comp_pipe_n2","REDUCED ENGINEERING MODELS | N2 External Hydraulic Network [1D REDUCED MODEL - NOT PHYSICAL ROUTING]");
        labelComponent(m,"comp_pipe_h2","REDUCED ENGINEERING MODELS | H2 External Hydraulic Network [1D REDUCED MODEL - NOT PHYSICAL ROUTING]");
        labelComponent(m,"comp_pipe_liq","REDUCED ENGINEERING MODELS | Liquid External Hydraulic Network [1D REDUCED MODEL - NOT PHYSICAL ROUTING]");
        labelComponent(m,"comp_ssc_darcy","Cathode SSC Darcy [REDUCED THROUGH-PLANE MODEL]");
        labelComponent(m,"comp_ssc_wetting","Cathode SSC Wetting [REDUCED THROUGH-PLANE MODEL]");
        labelComponent(m,"comp_n2_transfer","N2 Transfer [REDUCED / PHENOMENOLOGICAL MODEL]");
        m.component("comp_ssc_darcy").comments("This rectangle is not the physical shape of the 60x60 mm SSC. "
            + "It is a reduced through-thickness surrogate used to solve pressure across the approximately 30 um SSC thickness.");
        m.component("comp_ssc_wetting").comments("This rectangle is not the physical shape of the 60x60 mm SSC. "
            + "It is a reduced through-thickness surrogate used to solve saturation across the approximately 30 um SSC thickness.");
        m.component("comp_pipe_n2").comments("1D REDUCED MODEL - NOT PHYSICAL ROUTING. The 0.20 m edge is the Manual-backed external outlet-tube resistance, not a tube through the cell.");
        m.component("comp_pipe_h2").comments("1D REDUCED MODEL - NOT PHYSICAL ROUTING. The 0.20 m edge is the Manual-backed external outlet-tube resistance, not a tube through the cell.");
        m.component("comp_pipe_liq").comments("1D REDUCED MODEL - NOT PHYSICAL ROUTING. Edges represent documented external hydraulic lengths only.");

        for (String c : new String[]{"comp_pipe_n2","comp_pipe_h2","comp_pipe_liq"})
            for (String v : m.component(c).view().tags())
                m.component(c).view(v).label("External Hydraulic Network Schematic");
        for (String v : m.component("comp_ssc_darcy").view().tags())
            m.component("comp_ssc_darcy").view(v).label("SSC Darcy - Reduced Through-Plane");
        for (String v : m.component("comp_ssc_wetting").view().tags())
            m.component("comp_ssc_wetting").view(v).label("SSC Wetting - Reduced Through-Plane");

        labelResult(m,"pg_n2_velocity","REAL FLOW RESULTS | N2 Velocity");
        labelResult(m,"pg_n2_pressure","REAL FLOW RESULTS | N2 Pressure");
        labelResult(m,"pg_h2_velocity","REAL FLOW RESULTS | H2 Velocity");
        labelResult(m,"pg_h2_pressure","REAL FLOW RESULTS | H2 Pressure");
        labelResult(m,"pg_liq_velocity","REAL FLOW RESULTS | Electrolyte Velocity");
        labelResult(m,"pg_liq_pressure","REAL FLOW RESULTS | Electrolyte Pressure");
        labelResult(m,"pg_liq_wall_shear","REAL FLOW RESULTS | Electrolyte Wall Shear");
        labelResult(m,"pg_n2_wall_shear","REAL FLOW RESULTS | N2 Wall Shear");
        labelResult(m,"pg_pipe_dp","REDUCED MODEL RESULTS | External Pipe Pressure [REDUCED MODEL]");
        labelResult(m,"pg_ssc_dp","REDUCED MODEL RESULTS | SSC Darcy Pressure [REDUCED MODEL]");
        labelResult(m,"pg_ssc_velocity","REDUCED MODEL RESULTS | SSC Darcy Velocity [REDUCED MODEL]");
        labelResult(m,"pg_ssc_sl","REDUCED MODEL RESULTS | SSC Liquid Saturation [REDUCED MODEL]");
        labelResult(m,"pg_ssc_sg","REDUCED MODEL RESULTS | SSC Gas Saturation [REDUCED MODEL]");
        labelResult(m,"pg_penetration","REDUCED MODEL RESULTS | Liquid Penetration [REDUCED MODEL]");
        labelResult(m,"pg_flood_front","REDUCED MODEL RESULTS | Flooding Front [REDUCED MODEL]");
        labelResult(m,"pg_n2_conc","REDUCED MODEL RESULTS | N2 Transfer Concentration [REDUCED / PHENOMENOLOGICAL MODEL]");
        labelResult(m,"pg_n2_flux","REDUCED MODEL RESULTS | N2 Transfer Flux [REDUCED / PHENOMENOLOGICAL MODEL]");
        System.out.println("M10A2R_TREE_RELABEL=PASS");
    }

    private static void buildPhysicalCell(Model m) {
        final String c="comp_cell_physical", g="geom_cell_physical";
        if (m.component().hasTag(c)) m.component().remove(c);
        m.component().create(c,true);
        m.component(c).label("REAL / PHYSICAL | Physical Cell 3D");
        m.component(c).comments("Physical assembly visualization only. Real current collectors and chamber are strict STEP imports. "
            + "Gas stubs are PFA OD 3 mm / ID 2 mm and are created from the audited real-port centers and outward normals. "
            + "Stub length is VISUALIZATION ONLY and has NO HYDRAULIC ROLE. PEEK fitting internal geometry is omitted.");
        final GeomSequence geom=m.component(c).geom().create(g,3);
        geom.lengthUnit("mm"); geom.geomRep("cadps");
        strictImport(geom,"imp_chamber_physical",runtime("CHAMBER_STEP"));
        strictImport(geom,"imp_cc_physical",runtime("CC_STEP"));
        geom.feature().create("rot_cc_physical","Rotate");
        geom.feature("rot_cc_physical").selection("input").set("imp_cc_physical");
        geom.feature("rot_cc_physical").set("specify","axis"); geom.feature("rot_cc_physical").set("axistype","cartesian");
        geom.feature("rot_cc_physical").set("axis",new double[]{1,0,0}); geom.feature("rot_cc_physical").set("pos",new double[]{0,0,0});
        geom.feature("rot_cc_physical").set("rot",-90.0);
        geom.feature().create("mov_cc_negative","Move"); geom.feature("mov_cc_negative").selection("input").set("rot_cc_physical");
        geom.feature("mov_cc_negative").set("displ",new double[]{-54,54,28});
        geom.feature().create("mir_cc_positive","Mirror"); geom.feature("mir_cc_positive").selection("input").set("mov_cc_negative");
        geom.feature("mir_cc_positive").set("pos",new double[]{0,0,0}); geom.feature("mir_cc_positive").set("axis",new double[]{0,0,1});
        geom.feature("mir_cc_positive").set("keep","on");

        block(geom,"blk_cathode_ssc_true",new String[]{"ssc_cut","ssc_cut","t_ssc"},new String[]{"-ssc_cut/2","-ssc_cut/2","5[mm]"});
        geom.feature("blk_cathode_ssc_true").label("Cathode SSC | 316L | 500 mesh source identity | 60x60 mm | t=30 um LITERATURE_SAME_PLATFORM");
        block(geom,"blk_anode_ssc_true",new String[]{"ssc_cut","ssc_cut","t_ssc"},new String[]{"-ssc_cut/2","-ssc_cut/2","-5[mm]-t_ssc"});
        geom.feature("blk_anode_ssc_true").label("Anode PtAu/316L SSC | 500 mesh source identity | 60x60 mm | t=30 um LITERATURE_SAME_PLATFORM");
        frame(geom,"gasket_negative_visual","5[mm]-t_gasket_display", "Negative-side gasket | SCHEMATIC_VISUAL_ONLY | thickness not verified");
        frame(geom,"gasket_positive_visual","-5[mm]", "Positive-side gasket | SCHEMATIC_VISUAL_ONLY | thickness not verified");

        // Derive every stub center and outward normal from the named real-fluid port selections.
        // No connection coordinate is entered independently of the audited STEP-derived boundaries.
        final double[][] n2In=realPortPose(m,"comp_n2_flow","geom_n2_channel_fluid","sel_bnd_n2_inlet");gasTube(geom,"n2_in",n2In[0],n2In[1]);
        final double[][] n2Out=realPortPose(m,"comp_n2_flow","geom_n2_channel_fluid","sel_bnd_n2_outlet");gasTube(geom,"n2_out",n2Out[0],n2Out[1]);
        final double[][] h2In=realPortPose(m,"comp_h2_flow","geom_h2_channel_fluid","sel_bnd_h2_inlet");gasTube(geom,"h2_in",h2In[0],h2In[1]);
        final double[][] h2Out=realPortPose(m,"comp_h2_flow","geom_h2_channel_fluid","sel_bnd_h2_outlet");gasTube(geom,"h2_out",h2Out[0],h2Out[1]);

        geom.feature("fin").set("action","assembly"); geom.feature("fin").set("createpairs","off"); geom.feature("fin").set("imprint","off");
        geom.run(); geom.check();
        if(!geom.isAssembly()) throw new IllegalStateException("M10A2R_PHYSICAL_NOT_ASSEMBLY");
        selectBox(m,c,"sel_dom_physical_all",3,-80,80,-80,80,-50,50);
        selectBox(m,c,"sel_dom_cathode_ssc_true",3,-31,31,-31,31,4.999,5.1);
        selectBox(m,c,"sel_dom_anode_ssc_true",3,-31,31,-31,31,-5.1,-4.999);
        selectBox(m,c,"sel_dom_pfa_gas_stubs_top",3,-30,30,-30,30,28.001,45);
        selectBox(m,c,"sel_dom_pfa_gas_stubs_bottom",3,-30,30,-30,30,-45,-28.001);
        selectBox(m,c,"sel_bnd_cathode_ssc_true",2,-31,31,-31,31,4.999,5.1);
        selectBox(m,c,"sel_bnd_pfa_gas_stubs_top",2,-30,30,-30,30,28.001,45);
        selectBox(m,c,"sel_bnd_pfa_gas_stubs_bottom",2,-30,30,-30,30,-45,-28.001);
        m.component(c).selection().create("sel_dom_pfa_gas_stubs","Union");
        m.component(c).selection("sel_dom_pfa_gas_stubs").set("entitydim",3);
        m.component(c).selection("sel_dom_pfa_gas_stubs").set("input",new String[]{"sel_dom_pfa_gas_stubs_top","sel_dom_pfa_gas_stubs_bottom"});
        m.component(c).selection().create("sel_bnd_pfa_gas_stubs","Union");
        m.component(c).selection("sel_bnd_pfa_gas_stubs").set("entitydim",2);
        m.component(c).selection("sel_bnd_pfa_gas_stubs").set("input",new String[]{"sel_bnd_pfa_gas_stubs_top","sel_bnd_pfa_gas_stubs_bottom"});
        m.component(c).selection().create("sel_dom_nonssc_visual_mesh","Difference");
        m.component(c).selection("sel_dom_nonssc_visual_mesh").set("entitydim",3);
        m.component(c).selection("sel_dom_nonssc_visual_mesh").set("add",new String[]{"sel_dom_physical_all"});
        m.component(c).selection("sel_dom_nonssc_visual_mesh").set("subtract",new String[]{"sel_dom_cathode_ssc_true","sel_dom_anode_ssc_true"});
        if(m.component(c).selection("sel_dom_cathode_ssc_true").entities(3).length<1
                ||m.component(c).selection("sel_dom_anode_ssc_true").entities(3).length<1)
            throw new IllegalStateException("M10A2R_SSC_SELECTION_EMPTY");
        m.component(c).mesh().create("mesh_cell_physical");
        m.component(c).mesh("mesh_cell_physical").create("ftri","FreeTri");
        m.component(c).mesh("mesh_cell_physical").feature("ftri").selection().geom(g,2);
        m.component(c).mesh("mesh_cell_physical").feature("ftri").selection().all();
        m.component(c).mesh("mesh_cell_physical").create("ftet","FreeTet");
        m.component(c).mesh("mesh_cell_physical").feature("ftet").selection().named("sel_dom_physical_all");
        m.component(c).mesh("mesh_cell_physical").autoMeshSize(8);
        m.component(c).mesh("mesh_cell_physical").run();
        for(String v:m.component(c).view().tags())m.component(c).view(v).label("Physical Cell 3D");
        m.component(c).view().create("view_physical_exploded",3).label("Physical SSC - exploded visualization | DISPLAY SCALE ONLY");
        System.out.println("M10A2R_PHYSICAL_CELL_GEOMETRY=PASS");
        System.out.println("M10A2R_GAS_PORT_CENTER_NORMAL_SOURCE=REAL_CAD_SELECTION_AUDIT");
    }

    private static void frame(GeomSequence g,String tag,String z,String label) {
        block(g,tag+"_outer",new String[]{"gasket_outer","gasket_outer","t_gasket_display"},new String[]{"-gasket_outer/2","-gasket_outer/2",z});
        block(g,tag+"_inner",new String[]{"gasket_inner","gasket_inner","t_gasket_display+0.02[mm]"},new String[]{"-gasket_inner/2","-gasket_inner/2",z+"-0.01[mm]"});
        g.feature().create(tag,"Difference");g.feature(tag).selection("input").set(tag+"_outer");g.feature(tag).selection("input2").set(tag+"_inner");
        g.feature(tag).label(label);
    }

    private static double[][] realPortPose(Model m,String component,String geometry,String selection) {
        final int[] entities=m.component(component).selection(selection).entities(2);
        if(entities.length<1)throw new IllegalStateException("REAL_PORT_SELECTION_EMPTY: "+component+"/"+selection);
        m.component(component).measure().selection().geom(2);
        m.component(component).measure().selection().set(entities);
        double[] b=null;double flatness=Double.POSITIVE_INFINITY;
        for(int entity:entities){
            m.component(component).measure().selection().set(new int[]{entity});
            final double[] candidate=m.component(component).measure().getBoundingBox();
            final double candidateFlatness=Math.min(Math.abs(candidate[1]-candidate[0]),Math.min(Math.abs(candidate[3]-candidate[2]),Math.abs(candidate[5]-candidate[4])));
            if(candidateFlatness<flatness){flatness=candidateFlatness;b=candidate;}
        }
        if(b==null)throw new IllegalStateException("REAL_PORT_PLANAR_FACE_MISSING: "+component+"/"+selection);
        final double[] whole=m.component(component).geom(geometry).getBoundingBox();
        final double[] center=new double[]{(b[0]+b[1])/2,(b[2]+b[3])/2,(b[4]+b[5])/2};
        final double[] span=new double[]{Math.abs(b[1]-b[0]),Math.abs(b[3]-b[2]),Math.abs(b[5]-b[4])};
        int axis=0;if(span[1]<span[axis])axis=1;if(span[2]<span[axis])axis=2;
        final double middle=(whole[2*axis]+whole[2*axis+1])/2;
        final double[] normal=new double[]{0,0,0};normal[axis]=center[axis]>=middle?1:-1;
        System.out.println("M10A2R_REAL_PORT_POSE|component="+component+"|selection="+selection
            +"|center_mm="+fmt(center[0])+";"+fmt(center[1])+";"+fmt(center[2])
            +"|normal="+fmt(normal[0])+";"+fmt(normal[1])+";"+fmt(normal[2])+"|source=REAL_CAD_NAMED_SELECTION");
        return new double[][]{center,normal};
    }

    private static void gasTube(GeomSequence g,String stem,double[] center,double[] normal) {
        final String outer="cyl_"+stem+"_pfa_outer",bore="cyl_"+stem+"_bore_tool",wall="dif_"+stem+"_pfa_wall",fluid="cyl_"+stem+"_fluid_bore";
        g.feature().create(outer,"Cylinder");g.feature(outer).set("r","gas_pfa_od_visual/2");g.feature(outer).set("h","L_gas_stub_visual");
        g.feature(outer).set("pos",center);g.feature(outer).set("axis",normal);g.feature(outer).label(stem.toUpperCase(Locale.ROOT)+" PFA wall | OD 3 mm | SCHEMATIC_VISUAL_ONLY");
        g.feature().create(bore,"Cylinder");g.feature(bore).set("r","gas_pfa_id_visual/2");g.feature(bore).set("h","L_gas_stub_visual");
        g.feature(bore).set("pos",center);g.feature(bore).set("axis",normal);g.feature(bore).label(stem.toUpperCase(Locale.ROOT)+" gas bore | ID 2 mm | no PEEK cone");
        g.feature().create(wall,"Difference");g.feature(wall).selection("input").set(outer);g.feature(wall).selection("input2").set(bore);
        g.feature(wall).label(stem.toUpperCase(Locale.ROOT)+" hollow PFA tube wall | VISUALIZATION ONLY | NO HYDRAULIC ROLE");
        g.feature().create(fluid,"Cylinder");g.feature(fluid).set("r","gas_pfa_id_visual/2");g.feature(fluid).set("h","L_gas_stub_visual");
        g.feature(fluid).set("pos",center);g.feature(fluid).set("axis",normal);
        g.feature(fluid).label(stem.toUpperCase(Locale.ROOT)+" distinct gas bore | ID 2 mm | VISUALIZATION ONLY | NO HYDRAULIC ROLE");
    }

    private static void buildPhysicalResults(Model m) {
        final String d="dset_physical_mesh";
        if(m.result().dataset().hasTag(d))m.result().dataset().remove(d);

        meshPlot(m,"pg00_physical_cell","00 Physical Cell - Full 3D",d,null,false);
        meshPlot(m,"pg01_physical_exploded","01 Physical Cell - Exploded SSC/Gasket | DISPLAY SCALE ONLY",d,null,true);
        meshPlot(m,"pg02_gas_connections","02 Gas Port Connections - PFA 3x2 mm | NO PEEK INTERNAL GEOMETRY",d,"abs(z)>28.01[mm]",false);
        fixedSurface(m,"pg03_n2_channel","03 Real N2 Channel","dset_n2_solution","1",null);
        fixedSurface(m,"pg04_h2_channel","04 Real H2 Channel",datasetForLabel(m,"std_h2"),"1",null);
        fixedSurface(m,"pg05_electrolyte","05 Electrolyte Chamber","dset_liq_solution","1",null);
        meshPlot(m,"pg06_ssc_true_scale","Physical SSC - true scale | t=30 um LITERATURE_SAME_PLATFORM",d,"abs(z-5[mm])<0.1[mm]",false);
        System.out.println("M10A2R_PHYSICAL_RESULTS=PASS");
    }

    private static void meshPlot(Model m,String tag,String label,String data,String selection,boolean exploded) {
        if(m.result().hasTag(tag))m.result().remove(tag);
        m.component("comp_cell_physical").mesh("mesh_cell_physical").createPlot(data,tag);
        m.result().dataset(data).label("Physical Cell 3D - geometry visualization-mesh dataset");
        m.result(tag).label(label);m.result(tag).set("data",data);
        final String mesh=m.result(tag).feature().tags()[0];
        m.result(tag).feature(mesh).set("elemcolor","type");
        m.result(tag).feature(mesh).set("wireframecolor","none");m.result(tag).feature(mesh).set("meshdomain","surface");
        if(selection!=null){m.result(tag).feature(mesh).set("filteractive","on");m.result(tag).feature(mesh).set("elemfilter","logicexpression");m.result(tag).feature(mesh).set("logfilterexpr",selection);}
        if(exploded){
            m.result(tag).feature(mesh).create("display_transform","Transformation");
            m.result(tag).feature(mesh).feature("display_transform").set("enablescale",true);
            m.result(tag).feature(mesh).feature("display_transform").set("scale",new double[]{1,1,2});
        }
    }

    private static void fixedSurface(Model m,String tag,String label,String data,String expr,String selection) {
        if(m.result().hasTag(tag))m.result().remove(tag);
        m.result().create(tag,"PlotGroup3D");m.result(tag).label(label);m.result(tag).set("data",data);
        m.result(tag).create("surf","Surface");m.result(tag).feature("surf").set("expr",expr);
        m.result(tag).feature("surf").set("coloring","uniform");m.result(tag).feature("surf").set("color","custom");
        m.result(tag).feature("surf").set("customcolor",new double[]{0.2,0.55,0.85});
        if(selection!=null)m.result(tag).feature("surf").selection().named(selection);
    }

    private static void reorderResults(Model m) {
        final String[] first={"pg00_physical_cell","pg01_physical_exploded","pg02_gas_connections","pg03_n2_channel","pg04_h2_channel","pg05_electrolyte","pg06_ssc_true_scale"};
        for(int i=first.length-1;i>=0;i--)m.result().move(first[i],0);
    }

    private static Map<String,Double> evaluateCurrentNumerics(Model m) {
        final Map<String,Double> v=new LinkedHashMap<String,Double>();
        v.put("N2 cell dp",m.param().evaluate("dp_N2_cell_audit","Pa"));
        v.put("H2 cell dp",m.param().evaluate("dp_H2_cell_audit","Pa"));
        v.put("liquid cell dp",m.param().evaluate("dp_liq_cell_audit","Pa"));
        v.put("N2 pipe dp",m.param().evaluate("dp_N2_pipe","Pa"));
        v.put("H2 pipe dp",m.param().evaluate("dp_H2_pipe","Pa"));
        v.put("liquid pipe dp",m.param().evaluate("dp_liq_pipe","Pa"));
        v.put("SSC Darcy dp",m.param().evaluate("dp_ssc","Pa"));
        v.put("Sl min",m.param().evaluate("wet_Sl_min_audit"));
        v.put("Sl max",m.param().evaluate("wet_Sl_max_audit"));
        v.put("N2 transfer rate",m.param().evaluate("n2_transfer_rate_audit","mol/s"));
        for(Map.Entry<String,Double> e:v.entrySet())if(!Double.isFinite(e.getValue()))throw new IllegalStateException("NONFINITE_INVARIANCE_VALUE: "+e.getKey());
        return v;
    }

    private static Map<String,Double> verifyInvariance(Map<String,Double> after) throws IOException {
        final Path baseline=Paths.get(runtime("BASELINE_NUMERICS"));
        final List<String> lines=Files.readAllLines(baseline,StandardCharsets.UTF_8);
        final Map<String,Double> before=new LinkedHashMap<String,Double>();
        for(int i=1;i<lines.size();i++){
            String[] p=lines.get(i).split(",",-1);if(p.length>=2)before.put(p[0],Double.valueOf(p[1]));
        }
        for(Map.Entry<String,Double> e:after.entrySet()){
            Double b=before.get(e.getKey());if(b==null)throw new IllegalStateException("BASELINE_METRIC_MISSING: "+e.getKey());
            double r=Math.abs(e.getValue()-b)/Math.max(Math.max(Math.abs(e.getValue()),Math.abs(b)),1e-300);
            if(r>1e-10)throw new IllegalStateException("NUMERICAL_INVARIANCE_FAILED: "+e.getKey()+" relative="+r);
        }
        System.out.println("M10A2R_NUMERICAL_INVARIANCE=PASS");
        return before;
    }

    private static void auditGeometryProvenance(Model m) {
        GEOMETRY_PROVENANCE.clear();
        for(String c:m.component().tags())for(String g:m.component(c).geom().tags())for(String f:m.component(c).geom(g).feature().tags()){
            String type=m.component(c).geom(g).feature(f).getType();
            String source="M10A2R_OTHER",meaning="geometry construction",action="KEEP",reason="stable existing feature";
            if(type.equals("Import")){source="REAL_CAD";meaning="original STEP B-rep";reason="unchanged strict import";}
            else if(f.contains("closure")||f.contains("exact")){source="M10A0_4_FLUID_DOMAIN_EXTRACTION";meaning="numerical closure minus REAL_CAD solid";reason="validated real-CAD fluid domain; port bore matches real STEP";}
            else if(c.startsWith("comp_pipe_")){source="REDUCED_1D_HYDRAULIC_NETWORK";meaning="external hydraulic resistance network";reason="keep only in schematic reduced-model view";}
            else if(c.equals("comp_cell_physical")&&f.contains("pfa")){source="M10A2R_SCHEMATIC_VISUAL_ONLY";meaning="short PFA wall or 2 mm gas bore";reason="visual connection only; no hydraulic role";}
            else if(c.equals("comp_cell_physical")&&f.contains("gasket")){source="M10A2R_SCHEMATIC_VISUAL_ONLY";meaning="gasket display frame";reason="display thickness is not a measured compressed thickness";}
            else if(c.startsWith("comp_ssc_")||c.equals("comp_n2_transfer")){source=c.equals("comp_ssc_hardware")?"M10A2_PHYSICAL_HARDWARE":"REDUCED_ENGINEERING_MODEL";meaning=c.equals("comp_ssc_hardware")?"physical SSC/gasket reference":"reduced through-plane or transfer surrogate";reason="retain validated mathematical result with explicit label";}
            GEOMETRY_PROVENANCE.add(new String[]{c,g,f,type,source,"Java source / inherited MPH",meaning,action,reason});
        }
        GEOMETRY_PROVENANCE.add(new String[]{"comp_n2_flow","geom_n2_channel_fluid","REAL_CAD_INTERNAL_PORT_PROFILE","B-rep face profile","REAL_CAD","original collector STEP -> M10A0.4 Difference","visible conical/flared gas-port profile","KEEP","not a PEEK fitting; no synthetic Cone/Revolve/Sweep exists"});
        System.out.println("M10A2R_CONE_GEOMETRY_ORIGIN=REAL_CAD");
        System.out.println("M10A2R_CONE_GEOMETRY_ACTION=KEEP_NOT_PEEK");
    }

    private static void exportEvidence(Model m,Path out,Map<String,Double> baseline,Map<String,Double> numerics) throws Exception {
        writeGeometryProvenance(out.resolve("geometry_feature_provenance.csv"));
        writeGeometryProvenance(Paths.get(runtime("RESULT_GEOMETRY_PROVENANCE")));
        writeInventory(out.resolve("component_inventory.csv"),"tag,label,dimension,classification",componentRows(m));
        writeInventory(out.resolve("selection_inventory.csv"),"component,selection,dimension,count,label",selectionRows(m));
        writeInventory(out.resolve("physics_inventory.csv"),"component,physics_tag,physics_type,label,features",physicsRows(m));
        writeInventory(out.resolve("study_inventory.csv"),"study_tag,label,features,solver_sequences",studyRows(m));
        writeInventory(out.resolve("dataset_inventory.csv"),"dataset_tag,dataset_type,label",datasetRows(m));
        writeInventory(out.resolve("result_inventory.csv"),"result_tag,result_type,label,features",resultRows(m));
        writeInventory(out.resolve("parameter_inventory.csv"),"parameter,expression,description",parameterRows(m));
        writeNumerics(out.resolve("numerical_invariance.csv"),baseline,numerics);
        writeNumerics(Paths.get(runtime("RESULT_NUMERICAL_INVARIANCE")),baseline,numerics);
        writeTree(m,out.resolve("model_tree.txt"),out.resolve("model_tree.json"));
        System.out.println("M10A2R_EVIDENCE_EXPORT=PASS");
    }

    private static void exportImages(Model m,Path out) {
        image(m,"pg00_physical_cell",out.resolve("physical_cell_full_3d.png"));
        image(m,"pg01_physical_exploded",out.resolve("physical_cell_exploded.png"));
        image(m,"pg02_gas_connections",out.resolve("gas_port_connections.png"));
        image(m,"pg03_n2_channel",out.resolve("n2_channel_geometry.png"));
        image(m,"pg04_h2_channel",out.resolve("h2_channel_geometry.png"));
        image(m,"pg05_electrolyte",out.resolve("electrolyte_chamber_geometry.png"));
        image(m,"pg06_ssc_true_scale",out.resolve("ssc_physical_true_scale.png"));
        image(m,"pg_ssc_dp",out.resolve("ssc_reduced_darcy.png"));
        image(m,"pg_ssc_sl",out.resolve("ssc_reduced_wetting.png"));
        System.out.println("M10A2R_IMAGE_EXPORT=PASS");
    }

    private static void image(Model m,String plot,Path file) {
        final String tag="img_m10a2r_"+(++imageSerial);
        try{
            m.result(plot).run();
            m.result().export().create(tag,plot,"Image3D");
            m.result().export(tag).set("target","file");m.result().export(tag).set("filename",file.toString());
            m.result().export(tag).set("width",1400);m.result().export(tag).set("height",1000);m.result().export(tag).run();
            if(!Files.isRegularFile(file)||file.toFile().length()==0)throw new IllegalStateException("IMAGE_EMPTY");
        }catch(Throwable e){throw new IllegalStateException("IMAGE_EXPORT_FAILED: "+plot+" "+e.getMessage(),e);}
    }

    private static void independentReload(String output,Path evidence) throws Exception {
        final Model m=ModelUtil.load("M10A2RReload",output);
        try{
            for(String tag:new String[]{"pg00_physical_cell","pg01_physical_exploded","pg02_gas_connections"}){
                if(!m.result().hasTag(tag))throw new IllegalStateException("PHYSICAL_RESULT_MISSING: "+tag);m.result(tag).run();
                if(m.result(tag).hasWarning())throw new IllegalStateException("PHYSICAL_RESULT_WARNING: "+tag);
            }
            System.out.println("M10A2R_PHYSICAL_VIEW_RELOAD=PASS");
            for(String tag:new String[]{"pg_pipe_dp","pg_ssc_dp","pg_ssc_sl","pg_ssc_sg","pg_n2_conc"}){
                if(!m.result().hasTag(tag))throw new IllegalStateException("REDUCED_RESULT_MISSING: "+tag);m.result(tag).run();
                if(m.result(tag).hasWarning())throw new IllegalStateException("REDUCED_RESULT_WARNING: "+tag);
            }
            System.out.println("M10A2R_REDUCED_VIEW_RELOAD=PASS");
            for(String tag:m.result().tags()){
                m.result(tag).run();if(m.result(tag).hasWarning())throw new IllegalStateException("RESULT_WARNING_AFTER_RELOAD: "+tag);
            }
            System.out.println("M10A2R_RESULT_RELOAD=PASS");
        }finally{ModelUtil.remove("M10A2RReload");}
    }

    private static List<String[]> componentRows(Model m){List<String[]>r=new ArrayList<String[]>();for(String c:m.component().tags()){int d=m.component(c).geom().tags().length==0?0:m.component(c).geom(m.component(c).geom().tags()[0]).getSDim();String l=m.component(c).label();r.add(new String[]{c,l,Integer.toString(d),classification(l)});}return r;}
    private static List<String[]> selectionRows(Model m){List<String[]>r=new ArrayList<String[]>();for(String c:m.component().tags())for(String s:m.component(c).selection().tags()){int dim=-1,count=-1;for(int d=3;d>=0;d--)try{int n=m.component(c).selection(s).entities(d).length;if(n>0){dim=d;count=n;break;}}catch(Throwable ignored){}r.add(new String[]{c,s,Integer.toString(dim),Integer.toString(count),m.component(c).selection(s).label()});}return r;}
    private static List<String[]> physicsRows(Model m){List<String[]>r=new ArrayList<String[]>();for(String c:m.component().tags())for(String p:m.component(c).physics().tags()){Physics x=m.component(c).physics(p);r.add(new String[]{c,p,x.getType(),x.label(),String.join(";",x.feature().tags())});}return r;}
    private static List<String[]> studyRows(Model m){List<String[]>r=new ArrayList<String[]>();for(String s:m.study().tags())r.add(new String[]{s,m.study(s).label(),String.join(";",m.study(s).feature().tags()),String.join(";",m.study(s).getSolverSequences("SolverSequence"))});return r;}
    private static List<String[]> datasetRows(Model m){List<String[]>r=new ArrayList<String[]>();for(String d:m.result().dataset().tags())r.add(new String[]{d,m.result().dataset(d).getType(),m.result().dataset(d).label()});return r;}
    private static List<String[]> resultRows(Model m){List<String[]>r=new ArrayList<String[]>();for(String p:m.result().tags())r.add(new String[]{p,m.result(p).getType(),m.result(p).label(),String.join(";",m.result(p).feature().tags())});return r;}
    private static List<String[]> parameterRows(Model m){List<String[]>r=new ArrayList<String[]>();for(String p:m.param().varnames())r.add(new String[]{p,m.param().get(p),m.param().descr(p)});return r;}

    private static void writeTree(Model m,Path txt,Path json)throws IOException{
        StringBuilder t=new StringBuilder("MODEL LiNRR_M10A2R_manual_reconciled\n");StringBuilder j=new StringBuilder("{\n  \"model\": \"LiNRR_M10A2R_manual_reconciled\",\n  \"components\": [\n");
        String[] cs=m.component().tags();for(int i=0;i<cs.length;i++){String c=cs[i];t.append("COMPONENT ").append(c).append(" | ").append(m.component(c).label()).append('\n');for(String g:m.component(c).geom().tags()){t.append("  GEOMETRY ").append(g).append('\n');for(String f:m.component(c).geom(g).feature().tags())t.append("    FEATURE ").append(f).append(" | ").append(m.component(c).geom(g).feature(f).getType()).append('\n');}j.append("    {\"tag\":\"").append(js(c)).append("\",\"label\":\"").append(js(m.component(c).label())).append("\",\"geometries\":[");String[] gs=m.component(c).geom().tags();for(int k=0;k<gs.length;k++){if(k>0)j.append(',');j.append("{\"tag\":\"").append(js(gs[k])).append("\",\"features\":[");String[] fs=m.component(c).geom(gs[k]).feature().tags();for(int q=0;q<fs.length;q++){if(q>0)j.append(',');j.append("{\"tag\":\"").append(js(fs[q])).append("\",\"type\":\"").append(js(m.component(c).geom(gs[k]).feature(fs[q]).getType())).append("\"}");}j.append("]}");}j.append("]}").append(i+1<cs.length?",\n":"\n");}j.append("  ],\n  \"results\": [");String[] ps=m.result().tags();for(int i=0;i<ps.length;i++){if(i>0)j.append(',');j.append("{\"tag\":\"").append(js(ps[i])).append("\",\"label\":\"").append(js(m.result(ps[i]).label())).append("\"}");}j.append("]\n}\n");Files.createDirectories(txt.getParent());Files.write(txt,t.toString().getBytes(StandardCharsets.UTF_8));Files.write(json,j.toString().getBytes(StandardCharsets.UTF_8));}

    private static void writeGeometryProvenance(Path p)throws IOException{writeInventory(p,"component,geometry,feature_tag,feature_type,source_class,created_by,physical_meaning,keep_or_remove,reason",GEOMETRY_PROVENANCE);}
    private static void writeNumerics(Path p,Map<String,Double> before,Map<String,Double> after)throws IOException{Files.createDirectories(p.getParent());try(BufferedWriter w=Files.newBufferedWriter(p,StandardCharsets.UTF_8)){w.write("metric,before,after,relative_difference,status\n");for(Map.Entry<String,Double>e:after.entrySet()){double b=before.get(e.getKey());double a=e.getValue();double r=Math.abs(a-b)/Math.max(Math.max(Math.abs(a),Math.abs(b)),1e-300);w.write(csv(e.getKey()));w.write(',');w.write(fmt(b));w.write(',');w.write(fmt(a));w.write(',');w.write(fmt(r));w.write(r<=1e-10?",PASS\n":",FAIL\n");}}}
    private static void writeInventory(Path p,String header,List<String[]> rows)throws IOException{Files.createDirectories(p.getParent());try(BufferedWriter w=Files.newBufferedWriter(p,StandardCharsets.UTF_8)){w.write(header);w.write('\n');for(String[]row:rows){for(int i=0;i<row.length;i++){if(i>0)w.write(',');w.write(csv(row[i]));}w.write('\n');}}}

    private static void labelComponent(Model m,String tag,String label){if(m.component().hasTag(tag))m.component(tag).label(label);}
    private static void labelResult(Model m,String tag,String label){if(m.result().hasTag(tag))m.result(tag).label(label);}
    private static String datasetForLabel(Model m,String token){for(String d:m.result().dataset().tags())if(m.result().dataset(d).label().contains(token))return d;throw new IllegalStateException("DATASET_NOT_FOUND: "+token);}
    private static String classification(String l){return l.contains("REDUCED")?"REDUCED_ENGINEERING_MODEL":l.contains("PHYSICAL")?"REAL_PHYSICAL":"OTHER";}
    private static void block(GeomSequence g,String t,String[]s,String[]p){g.feature().create(t,"Block");g.feature(t).set("base","corner");g.feature(t).set("size",s);g.feature(t).set("pos",p);}
    private static void strictImport(GeomSequence g,String t,String f){g.feature().create(t,"Import");g.feature(t).set("filename",f);g.feature(t).set("unit","source");g.feature(t).set("keepsolid","on");g.feature(t).set("keepbnd","on");g.feature(t).set("keepfree","off");g.feature(t).set("knit","solid");g.feature(t).set("fillholes","off");g.feature(t).set("removeredundant","off");g.feature(t).set("simplify","off");g.feature(t).set("deletedetails","off");g.feature(t).set("healedges","off");g.feature(t).set("minimizetol","off");g.feature(t).set("importbodynames","on");g.feature(t).set("check","on");g.feature(t).set("fixerrors","off");g.feature(t).importData();}
    private static void selectBox(Model m,String c,String t,int d,double xmin,double xmax,double ymin,double ymax,double zmin,double zmax){m.component(c).selection().create(t,"Box");m.component(c).selection(t).set("entitydim",d);m.component(c).selection(t).set("condition","intersects");m.component(c).selection(t).set("xmin",xmin);m.component(c).selection(t).set("xmax",xmax);m.component(c).selection(t).set("ymin",ymin);m.component(c).selection(t).set("ymax",ymax);m.component(c).selection(t).set("zmin",zmin);m.component(c).selection(t).set("zmax",zmax);}
    private static String runtime(String n){try{return((String)Class.forName(RT).getField(n).get(null)).trim();}catch(Exception e){throw new IllegalStateException("RUNTIME_INPUT_MISSING: "+n,e);}}
    private static String csv(String s){String x=String.valueOf(s);return(x.contains(",")||x.contains("\"")||x.contains("\n"))?"\""+x.replace("\"","\"\"")+"\"":x;}
    private static String js(String s){return String.valueOf(s).replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","");}
    private static String fmt(double x){return String.format(Locale.ROOT,"%.15g",x);}
}
