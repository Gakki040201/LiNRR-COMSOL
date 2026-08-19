import com.comsol.model.GeomSequence;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.util.Locale;

/** M10A0.3c - load the validated 3b MPH and append one Move feature. */
public final class LiNRR_M10A0_3c_MoveTop {
    private static final String RUNTIME_CLASS = "LiNRR_M10A0_3c_RuntimeInputs";
    private static String currentApi = "NONE";
    private static String currentFeature = "NONE";

    private static final double[][] CC_BOLT_XZ = {
        {9.55, -34.95}, {9.55, -73.05}, {34.95, -9.55}, {73.05, -9.55},
        {98.45, -34.95}, {98.45, -73.05}, {34.95, -98.45}, {73.05, -98.45}
    };
    private static final double[][] CHAMBER_BOLT_XY = {
        {-44.45, 19.05}, {-44.45, -19.05}, {-19.05, 44.45}, {19.05, 44.45},
        {44.45, 19.05}, {44.45, -19.05}, {-19.05, -44.45}, {19.05, -44.45}
    };

    private LiNRR_M10A0_3c_MoveTop() {}

    public static Model run() throws Exception {
        System.out.println("M10A0_3C_BOOT_START");
        try {
            final String input = runtimeString("INPUT_MPH");
            final String output = runtimeString("OUTPUT_MPH");
            runtimeString("CC_STEP"); runtimeString("CHAMBER_STEP");
            runtimeString("CC_SHA256"); runtimeString("CHAMBER_SHA256");
            System.out.println("M10A0_3C_RUNTIME_INPUTS_PASS");

            api("ModelUtil.load", "Model");
            final Model model = ModelUtil.load("Model", input);
            api("model.label", "Model");
            model.label("LiNRR_M10A0_3c_move_top.mph");
            api("model.comments", "Model");
            model.comments("M10A0.3c: validated 3b Rotate plus one Move (-54,+54,+28) mm. No Mirror or Assembly.");

            api("model.component(tag).geom(tag)", "geom_cc_rotated");
            final GeomSequence geom = model.component("comp_cc_rotated").geom("geom_cc_rotated");
            api("geom.feature().create(Move)", "mov_cc_top");
            geom.feature().create("mov_cc_top", "Move");
            api("geom.feature(tag).label", "mov_cc_top");
            geom.feature("mov_cc_top").label("Top collector move: (-54,+54,+28) mm");
            api("geom.feature(tag).selection(input).set", "mov_cc_top");
            geom.feature("mov_cc_top").selection("input").set("rot_cc_top");
            api("geom.feature(tag).set(displ)", "mov_cc_top");
            geom.feature("mov_cc_top").set("displ", new double[] {-54.0, 54.0, 28.0});
            System.out.println("M10A0_3C_MOVE_FEATURE_CREATE_PASS");
            api("geom.run", "geom_cc_rotated"); geom.run();
            api("geom.check", "geom_cc_rotated"); geom.check();

            api("geom.getBoundingBox", "geom_cc_rotated");
            final double[] box = geom.getBoundingBox();
            validateBox(box, new double[] {-54.0025,54.0025,-54.0025,54.0025,4.9975,28.0025});
            api("geom.getNDomains", "geom_cc_rotated"); final int nd = geom.getNDomains();
            api("geom.getNBoundaries", "geom_cc_rotated"); final int nb = geom.getNBoundaries();
            api("geom.getNEdges", "geom_cc_rotated"); final int ne = geom.getNEdges();
            api("geom.getNVertices", "geom_cc_rotated"); final int nv = geom.getNVertices();
            api("geom.hasCadRep", "geom_cc_rotated"); final boolean cad = geom.hasCadRep();
            if (nd != 1 || nb != 135 || ne != 372 || nv != 245 || !cad) {
                throw new IllegalStateException("MOVED_TOPOLOGY_CHANGED: domains="+nd+" boundaries="+nb+" edges="+ne+" vertices="+nv+" cad="+cad);
            }
            final double residual = maximumLandmarkResidual();
            if (!Double.isFinite(residual) || residual > 1e-6) {
                throw new IllegalStateException("BOLT_LANDMARK_RESIDUAL_FAILED: " + residual);
            }
            System.out.println("M10A0_3C_MOVED_BBOX_MM=" + formatBox(box));
            System.out.println("M10A0_3C_MOVED_ENTITIES=domains:"+nd+",boundaries:"+nb+",edges:"+ne+",vertices:"+nv);
            System.out.println("M10A0_3C_BOLT_MAX_RESIDUAL_MM=" + number(residual));
            System.out.println("M10A0_3C_MOVE_BUILD_PASS");

            api("model.save", "Model"); model.save(output);
            System.out.println("M10A0_3C_MODEL_SAVE_PASS");
            System.out.println("M10A0_3C_MOVE_TOP=PASS");
            return model;
        } catch (Throwable error) {
            System.err.println("M10A0_3C_FAILURE_CONTEXT_BEGIN");
            System.err.println("M10A0_3C_FIRST_FAILED_API="+currentApi);
            System.err.println("M10A0_3C_FIRST_FAILED_FEATURE_TAG="+currentFeature);
            System.err.println("M10A0_3C_EXCEPTION_CLASS="+error.getClass().getName());
            System.err.println("M10A0_3C_EXCEPTION_MESSAGE="+String.valueOf(error.getMessage()));
            error.printStackTrace(System.err);
            System.err.println("M10A0_3C_FAILURE_CONTEXT_END");
            if (error instanceof Exception) throw (Exception)error;
            throw (Error)error;
        }
    }

    private static void validateBox(double[] actual, double[] expected) {
        if (actual == null || actual.length != 6) throw new IllegalStateException("INVALID_MOVED_BBOX");
        for (int i=0;i<6;i++) {
            if (!Double.isFinite(actual[i]) || Math.abs(actual[i]-expected[i])>0.02)
                throw new IllegalStateException("MOVED_BBOX_OUTSIDE_TOLERANCE_INDEX_"+i+": actual="+actual[i]+" expected="+expected[i]);
        }
    }

    private static double maximumLandmarkResidual() {
        double max=0.0;
        for(int i=0;i<CC_BOLT_XZ.length;i++) {
            final double x=CC_BOLT_XZ[i][0]-54.0;
            final double y=CC_BOLT_XZ[i][1]+54.0;
            max=Math.max(max,Math.hypot(x-CHAMBER_BOLT_XY[i][0],y-CHAMBER_BOLT_XY[i][1]));
        }
        return max;
    }
    private static String formatBox(double[] b){return "["+number(b[0])+","+number(b[1])+";"+number(b[2])+","+number(b[3])+";"+number(b[4])+","+number(b[5])+"]";}
    private static String number(double x){return String.format(Locale.ROOT,"%.12g",x);}
    private static void api(String name,String tag){currentApi=name;currentFeature=tag;System.out.println("M10A0_3C_API_BEGIN|api="+name+"|feature="+tag);}
    private static String runtimeString(String field){
        try{Object v=Class.forName(RUNTIME_CLASS).getField(field).get(null);if(!(v instanceof String)||((String)v).trim().isEmpty())throw new IllegalStateException("INVALID_RUNTIME_FIELD: "+field);return ((String)v).trim();}
        catch(ReflectiveOperationException e){throw new IllegalStateException("RUNTIME_INPUT_MISSING: "+field,e);}
    }
    public static void main(String[] args)throws Exception{run();}
}
