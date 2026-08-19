import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.util.Locale;

/** M10A0.3f - stable coordinate selections on the validated real-CAD assembly. */
public final class LiNRR_M10A0_3f_RegisteredAssembly {
 private static final String RUNTIME_CLASS="LiNRR_M10A0_3f_RuntimeInputs";
 private static String currentApi="NONE",currentFeature="NONE";
 private static final String[] TAGS={
  "sel_dom_chamber_solid","sel_dom_cc_top_solid","sel_dom_cc_bottom_solid",
  "sel_bnd_chamber_top_opening","sel_bnd_chamber_bottom_opening",
  "sel_bnd_cc_top_flow_face","sel_bnd_cc_bottom_flow_face",
  "sel_bnd_chamber_liquid_ports","sel_bnd_cc_top_gas_ports","sel_bnd_cc_bottom_gas_ports",
  "sel_bnd_candidate_top_interface","sel_bnd_candidate_bottom_interface"};
 private static final int[] DIMS={3,3,3,2,2,2,2,2,2,2,2,2};
 private static final double[][] BOXES={
  {-70,80,-60,60,-5.01,5.01},{-55,55,-55,55,4.99,28.01},{-55,55,-55,55,-28.01,-4.99},
  {-31,31,-31,31,4.99,5.01},{-31,31,-31,31,-5.01,-4.99},
  {-55,55,-55,55,4.99,5.01},{-55,55,-55,55,-5.01,-4.99},
  {69.19,69.21,-55,55,-5.1,5.1},{-27.3,27.3,-27.3,27.3,27.99,28.01},{-27.3,27.3,-27.3,27.3,-28.01,-27.99},
  {-55,70,-55,55,4.99,5.01},{-55,70,-55,55,-5.01,-4.99}};
 private LiNRR_M10A0_3f_RegisteredAssembly(){}
 public static Model run()throws Exception{
  System.out.println("M10A0_3F_BOOT_START");
  try{
   String input=rt("INPUT_MPH"),output=rt("OUTPUT_MPH");rt("CC_SHA256");rt("CHAMBER_SHA256");System.out.println("M10A0_3F_RUNTIME_INPUTS_PASS");
   api("ModelUtil.load","Model");Model m=ModelUtil.load("Model",input);api("model.label","Model");m.label("LiNRR_M10A0_3_real_cad_registered.mph");
   for(int i=0;i<TAGS.length;i++){
    if("sel_bnd_cc_top_gas_ports".equals(TAGS[i]))createTwoPortUnion(m,TAGS[i],28.0);
    else if("sel_bnd_cc_bottom_gas_ports".equals(TAGS[i]))createTwoPortUnion(m,TAGS[i],-28.0);
    else createBox(m,TAGS[i],DIMS[i],BOXES[i]);
   }
   System.out.println("M10A0_3F_SELECTION_CREATE_PASS");
   for(int i=0;i<TAGS.length;i++){
    api("selection.entities",TAGS[i]);int count=m.component("comp_registered").selection(TAGS[i]).entities(DIMS[i]).length;
    if(count<1)throw new IllegalStateException("EMPTY_SELECTION: "+TAGS[i]);
    System.out.println("M10A0_3F_SELECTION|name="+TAGS[i]+"|dim="+DIMS[i]+"|count="+count+"|bounds_mm="+bounds(BOXES[i]));
   }
   System.out.println("M10A0_3F_SELECTION_AUDIT_PASS");
   api("model.save","Model");m.save(output);System.out.println("M10A0_3F_MODEL_SAVE_PASS");System.out.println("M10A0_3F_REGISTERED_ASSEMBLY=PASS");return m;
  }catch(Throwable e){System.err.println("M10A0_3F_FAILURE_CONTEXT_BEGIN");System.err.println("M10A0_3F_FIRST_FAILED_API="+currentApi);System.err.println("M10A0_3F_FIRST_FAILED_FEATURE_TAG="+currentFeature);System.err.println("M10A0_3F_EXCEPTION_CLASS="+e.getClass().getName());System.err.println("M10A0_3F_EXCEPTION_MESSAGE="+String.valueOf(e.getMessage()));e.printStackTrace(System.err);System.err.println("M10A0_3F_FAILURE_CONTEXT_END");if(e instanceof Exception)throw(Exception)e;throw(Error)e;}
 }
 private static void createBox(Model m,String t,int d,double[]b){api("component.selection().create(Box)",t);m.component("comp_registered").selection().create(t,"Box");api("selection.label",t);m.component("comp_registered").selection(t).label(t);set(m,t,"entitydim",d);set(m,t,"condition",d==3?"inside":"intersects");set(m,t,"xmin",b[0]);set(m,t,"xmax",b[1]);set(m,t,"ymin",b[2]);set(m,t,"ymax",b[3]);set(m,t,"zmin",b[4]);set(m,t,"zmax",b[5]);}
 private static void createTwoPortUnion(Model m,String t,double z){String a=t+"_in",b=t+"_out";createBox(m,a,2,new double[]{-27.3,-20.7,20.7,27.3,z-0.01,z+0.01});createBox(m,b,2,new double[]{20.7,27.3,-27.3,-20.7,z-0.01,z+0.01});api("component.selection().create(Union)",t);m.component("comp_registered").selection().create(t,"Union");api("selection.label",t);m.component("comp_registered").selection(t).label(t);set(m,t,"entitydim",2);api("selection.set(input)",t);m.component("comp_registered").selection(t).set("input",new String[]{a,b});}
 private static void set(Model m,String t,String p,String v){api("selection.set("+p+")",t);m.component("comp_registered").selection(t).set(p,v);}private static void set(Model m,String t,String p,int v){api("selection.set("+p+")",t);m.component("comp_registered").selection(t).set(p,v);}private static void set(Model m,String t,String p,double v){api("selection.set("+p+")",t);m.component("comp_registered").selection(t).set(p,v);}
 private static String bounds(double[]b){return"["+f(b[0])+","+f(b[1])+";"+f(b[2])+","+f(b[3])+";"+f(b[4])+","+f(b[5])+"]";}private static String f(double x){return String.format(Locale.ROOT,"%.9g",x);}
 private static void api(String a,String t){currentApi=a;currentFeature=t;System.out.println("M10A0_3F_API_BEGIN|api="+a+"|feature="+t);}private static String rt(String n){try{return((String)Class.forName(RUNTIME_CLASS).getField(n).get(null)).trim();}catch(Exception e){throw new IllegalStateException("RUNTIME_INPUT_MISSING: "+n,e);}}
 public static void main(String[]a)throws Exception{run();}
}
