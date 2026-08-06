import com.comsol.model.GeomObject;
import com.comsol.model.GeomSequence;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.util.Locale;

/** M10A0.3d - load validated 3c and append one Z=0 Mirror. */
public final class LiNRR_M10A0_3d_MirrorBottom {
    private static final String RUNTIME_CLASS="LiNRR_M10A0_3d_RuntimeInputs";
    private static String currentApi="NONE",currentFeature="NONE";
    private LiNRR_M10A0_3d_MirrorBottom(){}

    public static Model run() throws Exception {
        System.out.println("M10A0_3D_BOOT_START");
        try{
            String input=runtime("INPUT_MPH"),output=runtime("OUTPUT_MPH");
            runtime("CC_SHA256");runtime("CHAMBER_SHA256");
            System.out.println("M10A0_3D_RUNTIME_INPUTS_PASS");
            api("ModelUtil.load","Model"); Model model=ModelUtil.load("Model",input);
            api("model.label","Model");model.label("LiNRR_M10A0_3d_mirror_bottom.mph");
            api("model.component(tag).geom(tag)","geom_cc_rotated");
            GeomSequence geom=model.component("comp_cc_rotated").geom("geom_cc_rotated");
            api("geom.feature().create(Mirror)","mir_cc_bottom");geom.feature().create("mir_cc_bottom","Mirror");
            api("geom.feature(tag).label","mir_cc_bottom");geom.feature("mir_cc_bottom").label("Bottom collector mirror through global Z=0");
            api("geom.feature(tag).selection(input).set","mir_cc_bottom");geom.feature("mir_cc_bottom").selection("input").set("mov_cc_top");
            api("geom.feature(tag).set(pos)","mir_cc_bottom");geom.feature("mir_cc_bottom").set("pos",new double[]{0,0,0});
            api("geom.feature(tag).set(axis)","mir_cc_bottom");geom.feature("mir_cc_bottom").set("axis",new double[]{0,0,1});
            api("geom.feature(tag).set(keep)","mir_cc_bottom");geom.feature("mir_cc_bottom").set("keep","on");
            System.out.println("M10A0_3D_MIRROR_FEATURE_CREATE_PASS");
            api("geom.run","geom_cc_rotated");geom.run();
            api("geom.check","geom_cc_rotated");geom.check();

            api("geom.obj(mov_cc_top)","mov_cc_top");GeomObject top=geom.obj("mov_cc_top");
            api("object.getBoundingBox","mov_cc_top");double[] topBox=top.getBoundingBox();
            api("geom.obj(mir_cc_bottom)","mir_cc_bottom");GeomObject bottom=geom.obj("mir_cc_bottom");
            api("object.getBoundingBox","mir_cc_bottom");double[] bottomBox=bottom.getBoundingBox();
            validateBox("TOP",topBox,new double[]{-54.0025,54.0025,-54.0025,54.0025,4.9975,28.0025});
            validateBox("BOTTOM",bottomBox,new double[]{-54.0025,54.0025,-54.0025,54.0025,-28.0025,-4.9975});
            for(int i=0;i<4;i++) if(Math.abs(topBox[i]-bottomBox[i])>0.02)throw new IllegalStateException("MIRROR_INPLANE_ASYMMETRY_INDEX_"+i);
            if(Math.abs(topBox[4]+bottomBox[5])>0.02||Math.abs(topBox[5]+bottomBox[4])>0.02)throw new IllegalStateException("MIRROR_Z_ASYMMETRY");
            api("geom.getNDomains","geom_cc_rotated");int nd=geom.getNDomains();
            api("geom.getNBoundaries","geom_cc_rotated");int nb=geom.getNBoundaries();
            api("geom.getNEdges","geom_cc_rotated");int ne=geom.getNEdges();
            api("geom.getNVertices","geom_cc_rotated");int nv=geom.getNVertices();
            if(nd!=2||nb!=270||ne!=744||nv!=490)throw new IllegalStateException("MIRROR_TOPOLOGY_CHANGED: "+nd+","+nb+","+ne+","+nv);
            GeomSequence chamber=model.component("comp_chamber_raw").geom("geom_chamber_raw");
            api("chamber.getBoundingBox","geom_chamber_raw");double[] chamberBox=chamber.getBoundingBox();
            validateBox("CHAMBER",chamberBox,new double[]{-53.2025,69.2025,-53.2025,54.2025,-5.0025,5.0025});
            System.out.println("M10A0_3D_TOP_BBOX_MM="+box(topBox));
            System.out.println("M10A0_3D_BOTTOM_BBOX_MM="+box(bottomBox));
            System.out.println("M10A0_3D_CHAMBER_BBOX_MM="+box(chamberBox));
            System.out.println("M10A0_3D_COLLECTOR_ENTITIES=domains:"+nd+",boundaries:"+nb+",edges:"+ne+",vertices:"+nv);
            System.out.println("M10A0_3D_NOMINAL_SOLID_PENETRATION_MM=0.00000000000");
            System.out.println("M10A0_3D_MIRROR_BUILD_PASS");
            api("model.save","Model");model.save(output);
            System.out.println("M10A0_3D_MODEL_SAVE_PASS");
            System.out.println("M10A0_3D_MIRROR_BOTTOM=PASS");return model;
        }catch(Throwable e){
            System.err.println("M10A0_3D_FAILURE_CONTEXT_BEGIN");System.err.println("M10A0_3D_FIRST_FAILED_API="+currentApi);System.err.println("M10A0_3D_FIRST_FAILED_FEATURE_TAG="+currentFeature);System.err.println("M10A0_3D_EXCEPTION_CLASS="+e.getClass().getName());System.err.println("M10A0_3D_EXCEPTION_MESSAGE="+String.valueOf(e.getMessage()));e.printStackTrace(System.err);System.err.println("M10A0_3D_FAILURE_CONTEXT_END");if(e instanceof Exception)throw(Exception)e;throw(Error)e;
        }
    }
    private static void validateBox(String n,double[]a,double[]x){if(a==null||a.length!=6)throw new IllegalStateException(n+"_INVALID_BBOX");for(int i=0;i<6;i++)if(!Double.isFinite(a[i])||Math.abs(a[i]-x[i])>0.02)throw new IllegalStateException(n+"_BBOX_FAIL_"+i+": "+a[i]+" expected "+x[i]);}
    private static String box(double[]b){return"["+f(b[0])+","+f(b[1])+";"+f(b[2])+","+f(b[3])+";"+f(b[4])+","+f(b[5])+"]";}
    private static String f(double x){return String.format(Locale.ROOT,"%.12g",x);}
    private static void api(String a,String t){currentApi=a;currentFeature=t;System.out.println("M10A0_3D_API_BEGIN|api="+a+"|feature="+t);}
    private static String runtime(String n){try{Object v=Class.forName(RUNTIME_CLASS).getField(n).get(null);return((String)v).trim();}catch(Exception e){throw new IllegalStateException("RUNTIME_INPUT_MISSING: "+n,e);}}
    public static void main(String[]args)throws Exception{run();}
}
