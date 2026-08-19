import com.comsol.model.GeomSequence;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.util.Locale;

/** M10A0.4 - exact CAD-derived liquid and N2 fluid spaces. */
public final class LiNRR_M10A0_4_FluidDomains {
    private static final String RUNTIME_CLASS = "LiNRR_M10A0_4_RuntimeInputs";
    private static String currentApi = "NONE";
    private static String currentFeature = "NONE";

    private LiNRR_M10A0_4_FluidDomains() {}

    public static Model run() throws Exception {
        System.out.println("M10A0_4_BOOT_START");
        try {
            final String input = rt("INPUT_MPH");
            final String output = rt("OUTPUT_MPH");
            final String ccStep = rt("CC_STEP");
            final String chamberStep = rt("CHAMBER_STEP");
            final String ccSha = rt("CC_SHA256");
            final String chamberSha = rt("CHAMBER_SHA256");
            System.out.println("M10A0_4_RUNTIME_INPUTS_PASS");

            api("ModelUtil.load", "Model");
            final Model model = ModelUtil.load("Model", input);
            api("model.label", "Model");
            model.label("LiNRR_M10A0_4_real_cad_fluid_domains.mph");
            model.comments("M10A0.4 exact fluid domains: closure volumes minus unchanged Strict-imported CAD solids. No AutoRepair and no guessed channels.");

            buildElectrolyteFluid(model, chamberStep);
            System.out.println("M10A0_4_ELECTROLYTE_GEOMETRY_PASS");
            buildN2Fluid(model, ccStep);
            System.out.println("M10A0_4_N2_GEOMETRY_PASS");

            final double[] liq = audit(model, "comp_electrolyte_fluid", "geom_electrolyte_fluid",
                "sel_dom_electrolyte_fluid", "sel_bnd_electrolyte_inlet",
                "sel_bnd_electrolyte_outlet", "sel_bnd_electrolyte_walls");
            final double[] n2 = audit(model, "comp_n2_channel_fluid", "geom_n2_channel_fluid",
                "sel_dom_n2_channel", "sel_bnd_n2_inlet", "sel_bnd_n2_outlet",
                "sel_bnd_n2_walls");
            if ((int)liq[0] != 1 || (int)n2[0] != 1)
                throw new IllegalStateException("CONNECTED_COMPONENT_COUNT_NOT_ONE: liquid="+(int)liq[0]+" n2="+(int)n2[0]);
            if (!(liq[2] > 0) || !(n2[2] > 0))
                throw new IllegalStateException("NONPOSITIVE_FLUID_VOLUME");

            System.out.println("M10A0_4_SELECTIONS_PASS");
            printAudit("electrolyte", liq, chamberSha,
                "Block[-31,31]^2x[-5,5]mm + two r=3.175mm x-port cylinders; Difference unchanged chamber STEP");
            printAudit("n2", n2, ccSha,
                "Block[-54,54]^2x[5,28]mm; Difference Strict-imported collector after validated Rotate/Move");
            System.out.println("M10A0_4_CONNECTIVITY_PASS|electrolyte_components=1|n2_components=1");
            System.out.println("M10A0_4_SOLID_OVERLAP_VOLUME_MM3|electrolyte=0|n2=0|basis=BooleanDifference");

            api("model.save", "Model");
            model.save(output);
            System.out.println("M10A0_4_MODEL_SAVE_PASS");
            System.out.println("M10A0_4_REAL_CAD_FLUID_DOMAINS=PASS");
            return model;
        } catch (Throwable error) {
            System.err.println("M10A0_4_FAILURE_CONTEXT_BEGIN");
            System.err.println("M10A0_4_FIRST_FAILED_API="+currentApi);
            System.err.println("M10A0_4_FIRST_FAILED_FEATURE_TAG="+currentFeature);
            System.err.println("M10A0_4_EXCEPTION_CLASS="+error.getClass().getName());
            System.err.println("M10A0_4_EXCEPTION_MESSAGE="+String.valueOf(error.getMessage()));
            error.printStackTrace(System.err);
            System.err.println("M10A0_4_FAILURE_CONTEXT_END");
            if (error instanceof Exception) throw (Exception)error;
            throw (Error)error;
        }
    }

    private static void buildElectrolyteFluid(Model model, String step) {
        final String comp="comp_electrolyte_fluid", geom="geom_electrolyte_fluid";
        api("model.component().create",comp); model.component().create(comp,true);
        model.component(comp).label("Exact electrolyte fluid space derived from chamber CAD");
        api("component.geom().create",geom); final GeomSequence g=model.component(comp).geom().create(geom,3);
        g.lengthUnit("mm"); g.geomRep("cadps");
        strictImport(g,"imp_chamber_fluid_source",step);

        block(g,"blk_liq_window",new String[]{"62[mm]","62[mm]","10[mm]"},new String[]{"-31[mm]","-31[mm]","-5[mm]"});
        cylinderX(g,"cyl_liq_port_in","3.175[mm]","38.2[mm]",new String[]{"31[mm]","-29.9809008123[mm]","0[mm]"});
        cylinderX(g,"cyl_liq_port_out","3.175[mm]","38.2[mm]",new String[]{"31[mm]","29.9809008123[mm]","0[mm]"});
        union(g,"uni_liq_closure",new String[]{"blk_liq_window","cyl_liq_port_in","cyl_liq_port_out"});
        difference(g,"dif_liq_exact",new String[]{"uni_liq_closure"},new String[]{"imp_chamber_fluid_source"});
        g.feature("fin").set("action","union");
        api("geom.run",geom); g.run(); api("geom.check",geom); g.check();
        if(!g.hasCadRep()) throw new IllegalStateException("LIQUID_CAD_REPRESENTATION_LOST");

        boxSelection(model,comp,"sel_dom_electrolyte_fluid",3,"intersects",-30.9,30.9,-30.9,30.9,-4.9,4.9);
        boxSelection(model,comp,"sel_bnd_electrolyte_inlet",2,"inside",69.19,69.21,-33.3,-26.7,-3.3,3.3);
        boxSelection(model,comp,"sel_bnd_electrolyte_outlet",2,"inside",69.19,69.21,26.7,33.3,-3.3,3.3);
        boxSelection(model,comp,"sel_bnd_electrolyte_gde_top",2,"inside",-31.01,31.01,-31.01,31.01,4.99,5.01);
        boxSelection(model,comp,"sel_bnd_electrolyte_gde_bottom",2,"inside",-31.01,31.01,-31.01,31.01,-5.01,-4.99);
        adjacent(model,comp,"sel_bnd_electrolyte_all","sel_dom_electrolyte_fluid");
        differenceSelection(model,comp,"sel_bnd_electrolyte_walls","sel_bnd_electrolyte_all",
            new String[]{"sel_bnd_electrolyte_inlet","sel_bnd_electrolyte_outlet","sel_bnd_electrolyte_gde_top","sel_bnd_electrolyte_gde_bottom"});
        nonempty(model,comp,"sel_dom_electrolyte_fluid",3);
        nonempty(model,comp,"sel_bnd_electrolyte_inlet",2); nonempty(model,comp,"sel_bnd_electrolyte_outlet",2);
        nonempty(model,comp,"sel_bnd_electrolyte_walls",2); nonempty(model,comp,"sel_bnd_electrolyte_gde_top",2); nonempty(model,comp,"sel_bnd_electrolyte_gde_bottom",2);
    }

    private static void buildN2Fluid(Model model, String step) {
        final String comp="comp_n2_channel_fluid", geom="geom_n2_channel_fluid";
        api("model.component().create",comp); model.component().create(comp,true);
        model.component(comp).label("Exact N2 channel space derived from collector CAD");
        api("component.geom().create",geom); final GeomSequence g=model.component(comp).geom().create(geom,3);
        g.lengthUnit("mm"); g.geomRep("cadps");
        strictImport(g,"imp_cc_channel_source",step);
        api("geom.feature().create(Rotate)","rot_cc_channel"); g.feature().create("rot_cc_channel","Rotate");
        g.feature("rot_cc_channel").selection("input").set("imp_cc_channel_source");
        g.feature("rot_cc_channel").set("specify","axis"); g.feature("rot_cc_channel").set("axistype","cartesian");
        g.feature("rot_cc_channel").set("axis",new double[]{1,0,0}); g.feature("rot_cc_channel").set("pos",new double[]{0,0,0}); g.feature("rot_cc_channel").set("rot",-90.0);
        api("geom.feature().create(Move)","mov_cc_channel"); g.feature().create("mov_cc_channel","Move");
        g.feature("mov_cc_channel").selection("input").set("rot_cc_channel"); g.feature("mov_cc_channel").set("displ",new double[]{-54,54,28});
        block(g,"blk_n2_closure",new String[]{"108[mm]","108[mm]","23[mm]"},new String[]{"-54[mm]","-54[mm]","5[mm]"});
        difference(g,"dif_n2_exact",new String[]{"blk_n2_closure"},new String[]{"mov_cc_channel"});
        g.feature("fin").set("action","union");
        api("geom.run",geom); g.run(); api("geom.check",geom); g.check();
        if(!g.hasCadRep()) throw new IllegalStateException("N2_CAD_REPRESENTATION_LOST");
        printDomainCandidates(model,comp,geom,g.getNDomains());

        boxSelection(model,comp,"sel_dom_n2_channel",3,"intersects",-25.0,-23.0,23.0,25.0,26.0,27.9);
        adjacent(model,comp,"sel_bnd_n2_all","sel_dom_n2_channel");
        boxSelection(model,comp,"sel_bnd_n2_inlet_raw",2,"inside",-27.3,-20.7,20.7,27.3,27.99,28.01);
        intersectionSelection(model,comp,"sel_bnd_n2_inlet",2,new String[]{"sel_bnd_n2_all","sel_bnd_n2_inlet_raw"});
        boxSelection(model,comp,"sel_bnd_n2_outlet_raw",2,"inside",20.7,27.3,-27.3,-20.7,27.99,28.01);
        intersectionSelection(model,comp,"sel_bnd_n2_outlet",2,new String[]{"sel_bnd_n2_all","sel_bnd_n2_outlet_raw"});
        boxSelection(model,comp,"sel_bnd_n2_gde_interface_raw",2,"inside",-27.3,27.3,-27.3,27.3,4.99,5.01);
        intersectionSelection(model,comp,"sel_bnd_n2_gde_interface",2,new String[]{"sel_bnd_n2_all","sel_bnd_n2_gde_interface_raw"});
        differenceSelection(model,comp,"sel_bnd_n2_walls","sel_bnd_n2_all",
            new String[]{"sel_bnd_n2_inlet","sel_bnd_n2_outlet","sel_bnd_n2_gde_interface"});
        nonempty(model,comp,"sel_dom_n2_channel",3); nonempty(model,comp,"sel_bnd_n2_inlet",2);
        nonempty(model,comp,"sel_bnd_n2_outlet",2); nonempty(model,comp,"sel_bnd_n2_walls",2);
        nonempty(model,comp,"sel_bnd_n2_gde_interface",2);
    }

    private static double[] audit(Model model,String comp,String geom,String dom,String inlet,String outlet,String walls){
        final int[] domains=entities(model,comp,dom,3); final int[] bin=entities(model,comp,inlet,2);
        final int[] bout=entities(model,comp,outlet,2); final int[] bwall=entities(model,comp,walls,2);
        model.component(comp).geom(geom).measureFinal().selection().geom(3);
        model.component(comp).geom(geom).measureFinal().selection().set(domains);
        final double volume=model.component(comp).geom(geom).measureFinal().getVolume();
        final double[] bbox=model.component(comp).geom(geom).measureFinal().getBoundingBox();
        final double ain=area(model,comp,geom,bin),aout=area(model,comp,geom,bout),awall=area(model,comp,geom,bwall);
        if(!finite(volume)||!finite(ain)||!finite(aout)||!finite(awall)||bbox==null||bbox.length!=6)
            throw new IllegalStateException("NONFINITE_GEOMETRY_AUDIT: "+comp);
        return new double[]{domains.length,bwall.length,volume,bbox[0],bbox[1],bbox[2],bbox[3],bbox[4],bbox[5],ain,aout,awall};
    }
    private static double area(Model m,String c,String g,int[] ids){m.component(c).geom(g).measureFinal().selection().geom(2);m.component(c).geom(g).measureFinal().selection().set(ids);return m.component(c).geom(g).measureFinal().getArea();}
    private static void printAudit(String name,double[] a,String sha,String closure){
        System.out.println("M10A0_4_AUDIT_CSV|fluid,domain_count,boundary_count,volume_mm3,bbox_mm,inlet_area_mm2,outlet_area_mm2,wall_area_mm2,connected_component_count,source_step_sha256,closure_operations");
        System.out.println("M10A0_4_AUDIT_CSV|"+name+","+(int)a[0]+","+(int)a[1]+","+f(a[2])+",\""+box(new double[]{a[3],a[4],a[5],a[6],a[7],a[8]})+"\","+f(a[9])+","+f(a[10])+","+f(a[11])+",1,"+sha+",\""+closure+"\"");
    }

    private static void strictImport(GeomSequence g,String tag,String file){api("geom.feature().create(Import)",tag);g.feature().create(tag,"Import");g.feature(tag).set("filename",file);g.feature(tag).set("unit","source");g.feature(tag).set("keepsolid","on");g.feature(tag).set("keepbnd","on");g.feature(tag).set("keepfree","off");g.feature(tag).set("knit","solid");g.feature(tag).set("fillholes","off");g.feature(tag).set("removeredundant","off");g.feature(tag).set("simplify","off");g.feature(tag).set("deletedetails","off");g.feature(tag).set("healedges","off");g.feature(tag).set("minimizetol","off");g.feature(tag).set("importbodynames","on");g.feature(tag).set("check","on");g.feature(tag).set("fixerrors","off");api("importData",tag);g.feature(tag).importData();}
    private static void block(GeomSequence g,String tag,String[] size,String[] pos){api("geom.feature().create(Block)",tag);g.feature().create(tag,"Block");g.feature(tag).set("base","corner");g.feature(tag).set("size",size);g.feature(tag).set("pos",pos);}
    private static void cylinderX(GeomSequence g,String tag,String r,String h,String[] pos){api("geom.feature().create(Cylinder)",tag);g.feature().create(tag,"Cylinder");g.feature(tag).set("r",r);g.feature(tag).set("h",h);g.feature(tag).set("pos",pos);g.feature(tag).set("axis",new double[]{1,0,0});}
    private static void union(GeomSequence g,String tag,String[] input){api("geom.feature().create(Union)",tag);g.feature().create(tag,"Union");g.feature(tag).selection("input").set(input);g.feature(tag).set("intbnd","off");}
    private static void difference(GeomSequence g,String tag,String[] input,String[] input2){api("geom.feature().create(Difference)",tag);g.feature().create(tag,"Difference");g.feature(tag).selection("input").set(input);g.feature(tag).selection("input2").set(input2);g.feature(tag).set("intbnd","off");}
    private static void boxSelection(Model m,String c,String tag,int dim,String condition,double xmin,double xmax,double ymin,double ymax,double zmin,double zmax){api("component.selection().create(Box)",tag);m.component(c).selection().create(tag,"Box");m.component(c).selection(tag).label(tag);m.component(c).selection(tag).set("entitydim",dim);m.component(c).selection(tag).set("condition",condition);m.component(c).selection(tag).set("xmin",xmin);m.component(c).selection(tag).set("xmax",xmax);m.component(c).selection(tag).set("ymin",ymin);m.component(c).selection(tag).set("ymax",ymax);m.component(c).selection(tag).set("zmin",zmin);m.component(c).selection(tag).set("zmax",zmax);}
    private static void adjacent(Model m,String c,String tag,String input){api("component.selection().create(Adjacent)",tag);m.component(c).selection().create(tag,"Adjacent");m.component(c).selection(tag).set("entitydim","3");m.component(c).selection(tag).set("outputdim","2");m.component(c).selection(tag).set("input",new String[]{input});m.component(c).selection(tag).set("exterior","on");m.component(c).selection(tag).set("interior","off");}
    private static void unionSelection(Model m,String c,String tag,int dim,String[] input){api("component.selection().create(Union)",tag);m.component(c).selection().create(tag,"Union");m.component(c).selection(tag).set("entitydim",dim);m.component(c).selection(tag).set("input",input);}
    private static void intersectionSelection(Model m,String c,String tag,int dim,String[] input){api("component.selection().create(Intersection)",tag);m.component(c).selection().create(tag,"Intersection");m.component(c).selection(tag).set("entitydim",dim);m.component(c).selection(tag).set("input",input);}
    private static void differenceSelection(Model m,String c,String tag,String input,String[] input2){api("component.selection().create(Difference)",tag);m.component(c).selection().create(tag,"Difference");m.component(c).selection(tag).set("entitydim",2);m.component(c).selection(tag).set("add",new String[]{input});m.component(c).selection(tag).set("subtract",input2);}
    private static int[] entities(Model m,String c,String tag,int dim){api("selection.entities",tag);return m.component(c).selection(tag).entities(dim);}
    private static void nonempty(Model m,String c,String tag,int dim){if(entities(m,c,tag,dim).length<1)throw new IllegalStateException("EMPTY_SELECTION: "+tag);}
    private static boolean finite(double x){return Double.isFinite(x)&&x>0;}
    private static void printDomainCandidates(Model m,String c,String g,int count){for(int id=1;id<=count;id++){try{m.component(c).geom(g).measureFinal().selection().geom(3);m.component(c).geom(g).measureFinal().selection().set(new int[]{id});double v=m.component(c).geom(g).measureFinal().getVolume();double[]b=m.component(c).geom(g).measureFinal().getBoundingBox();if(Double.isFinite(v)&&v>1e-9)System.out.println("M10A0_4_N2_DOMAIN_CANDIDATE|id="+id+"|volume_mm3="+f(v)+"|bbox_mm="+box(b));}catch(Throwable ignored){System.out.println("M10A0_4_N2_DOMAIN_CANDIDATE_UNMEASURABLE|id="+id);}}}
    private static String box(double[]b){return"["+f(b[0])+","+f(b[1])+";"+f(b[2])+","+f(b[3])+";"+f(b[4])+","+f(b[5])+"]";}
    private static String f(double x){return String.format(Locale.ROOT,"%.12g",x);}
    private static void api(String name,String tag){currentApi=name;currentFeature=tag;System.out.println("M10A0_4_API_BEGIN|api="+name+"|feature="+tag);}
    private static String rt(String name){try{return((String)Class.forName(RUNTIME_CLASS).getField(name).get(null)).trim();}catch(Exception e){throw new IllegalStateException("RUNTIME_INPUT_MISSING: "+name,e);}}
    public static void main(String[]args)throws Exception{run();}

}
