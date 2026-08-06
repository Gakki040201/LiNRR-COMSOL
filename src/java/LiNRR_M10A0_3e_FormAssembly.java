import com.comsol.model.GeomSequence;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.util.Locale;

/** M10A0.3e - reproduce validated transforms in comp_registered and Form Assembly. */
public final class LiNRR_M10A0_3e_FormAssembly {
 private static final String RUNTIME_CLASS="LiNRR_M10A0_3e_RuntimeInputs";
 private static String currentApi="NONE",currentFeature="NONE";
 private LiNRR_M10A0_3e_FormAssembly(){}
 public static Model run()throws Exception{
  System.out.println("M10A0_3E_BOOT_START");
  try{
   String input=rt("INPUT_MPH"),output=rt("OUTPUT_MPH"),cc=rt("CC_STEP"),ch=rt("CHAMBER_STEP");rt("CC_SHA256");rt("CHAMBER_SHA256");
   System.out.println("M10A0_3E_RUNTIME_INPUTS_PASS");
   api("ModelUtil.load","Model");Model m=ModelUtil.load("Model",input);api("model.label","Model");m.label("LiNRR_M10A0_3e_form_assembly.mph");
   api("model.component().create","comp_registered");m.component().create("comp_registered",true);api("component.label","comp_registered");m.component("comp_registered").label("Registered chamber and two collectors - assembly");
   api("component.geom().create","geom_registered");GeomSequence g=m.component("comp_registered").geom().create("geom_registered",3);api("geom.lengthUnit","geom_registered");g.lengthUnit("mm");api("geom.geomRep","geom_registered");g.geomRep("cadps");
   strictImport(g,"imp_chamber_registered",ch);
   strictImport(g,"imp_cc_registered",cc);
   api("geom.feature().create(Rotate)","rot_cc_top");g.feature().create("rot_cc_top","Rotate");api("rotate.selection.set","rot_cc_top");g.feature("rot_cc_top").selection("input").set("imp_cc_registered");set(g,"rot_cc_top","specify","axis");set(g,"rot_cc_top","axistype","cartesian");set(g,"rot_cc_top","axis",new double[]{1,0,0});set(g,"rot_cc_top","pos",new double[]{0,0,0});set(g,"rot_cc_top","rot",-90.0);
   api("geom.feature().create(Move)","mov_cc_top");g.feature().create("mov_cc_top","Move");api("move.selection.set","mov_cc_top");g.feature("mov_cc_top").selection("input").set("rot_cc_top");set(g,"mov_cc_top","displ",new double[]{-54,54,28});
   api("geom.feature().create(Mirror)","mir_cc_bottom");g.feature().create("mir_cc_bottom","Mirror");api("mirror.selection.set","mir_cc_bottom");g.feature("mir_cc_bottom").selection("input").set("mov_cc_top");set(g,"mir_cc_bottom","pos",new double[]{0,0,0});set(g,"mir_cc_bottom","axis",new double[]{0,0,1});set(g,"mir_cc_bottom","keep","on");
   System.out.println("M10A0_3E_VALIDATED_FEATURE_SEQUENCE_PASS");
   api("geom.feature(fin).set(action=assembly)","fin");g.feature("fin").set("action","assembly");api("geom.feature(fin).set(createpairs)","fin");g.feature("fin").set("createpairs","off");api("geom.feature(fin).set(imprint)","fin");g.feature("fin").set("imprint","off");
   System.out.println("M10A0_3E_FORM_ASSEMBLY_FEATURE_PASS");
   api("geom.run","geom_registered");g.run();api("geom.check","geom_registered");g.check();
   api("geom.isAssembly","geom_registered");if(!g.isAssembly())throw new IllegalStateException("REGISTERED_NOT_ASSEMBLY");
   api("geom.objectNames","geom_registered");String[] objects=g.objectNames();if(objects.length!=3)throw new IllegalStateException("ASSEMBLY_OBJECT_COUNT="+objects.length);
   api("geom.getBoundingBox","geom_registered");double[]b=g.getBoundingBox();checkBox(b,new double[]{-54.0025,69.2025,-54.0025,54.2025,-28.0025,28.0025});
   api("geom.getNDomains","geom_registered");int nd=g.getNDomains();api("geom.getNBoundaries","geom_registered");int nb=g.getNBoundaries();api("geom.getNEdges","geom_registered");int ne=g.getNEdges();api("geom.getNVertices","geom_registered");int nv=g.getNVertices();
   if(nd!=3||nb!=363||ne!=1004||nv!=662)throw new IllegalStateException("ASSEMBLY_TOPOLOGY_UNEXPECTED: "+nd+","+nb+","+ne+","+nv);
   System.out.println("M10A0_3E_ASSEMBLY_OBJECT_COUNT="+objects.length);for(String o:objects)System.out.println("M10A0_3E_ASSEMBLY_OBJECT="+o);
   System.out.println("M10A0_3E_ASSEMBLY_BBOX_MM="+box(b));System.out.println("M10A0_3E_ASSEMBLY_ENTITIES=domains:"+nd+",boundaries:"+nb+",edges:"+ne+",vertices:"+nv);System.out.println("M10A0_3E_AUTOMATIC_PAIRS_CREATED=FALSE");System.out.println("M10A0_3E_FORM_ASSEMBLY_BUILD_PASS");
   api("model.save","Model");m.save(output);System.out.println("M10A0_3E_MODEL_SAVE_PASS");System.out.println("M10A0_3E_FORM_ASSEMBLY=PASS");return m;
  }catch(Throwable e){System.err.println("M10A0_3E_FAILURE_CONTEXT_BEGIN");System.err.println("M10A0_3E_FIRST_FAILED_API="+currentApi);System.err.println("M10A0_3E_FIRST_FAILED_FEATURE_TAG="+currentFeature);System.err.println("M10A0_3E_EXCEPTION_CLASS="+e.getClass().getName());System.err.println("M10A0_3E_EXCEPTION_MESSAGE="+String.valueOf(e.getMessage()));e.printStackTrace(System.err);System.err.println("M10A0_3E_FAILURE_CONTEXT_END");if(e instanceof Exception)throw(Exception)e;throw(Error)e;}
 }
 private static void strictImport(GeomSequence g,String t,String f){api("geom.feature().create(Import)",t);g.feature().create(t,"Import");set(g,t,"filename",f);set(g,t,"unit","source");set(g,t,"keepsolid","on");set(g,t,"keepbnd","on");set(g,t,"keepfree","off");set(g,t,"knit","solid");set(g,t,"fillholes","off");set(g,t,"removeredundant","off");set(g,t,"simplify","off");set(g,t,"deletedetails","off");set(g,t,"healedges","off");set(g,t,"minimizetol","off");set(g,t,"importbodynames","on");set(g,t,"check","on");set(g,t,"fixerrors","off");api("importData",t);g.feature(t).importData();}
 private static void set(GeomSequence g,String t,String p,String v){api("feature.set("+p+")",t);g.feature(t).set(p,v);}private static void set(GeomSequence g,String t,String p,double v){api("feature.set("+p+")",t);g.feature(t).set(p,v);}private static void set(GeomSequence g,String t,String p,double[]v){api("feature.set("+p+")",t);g.feature(t).set(p,v);}
 private static void checkBox(double[]a,double[]x){if(a==null||a.length!=6)throw new IllegalStateException("INVALID_ASSEMBLY_BBOX");for(int i=0;i<6;i++)if(!Double.isFinite(a[i])||Math.abs(a[i]-x[i])>0.02)throw new IllegalStateException("ASSEMBLY_BBOX_FAIL_"+i+": "+a[i]);}
 private static String box(double[]b){return"["+f(b[0])+","+f(b[1])+";"+f(b[2])+","+f(b[3])+";"+f(b[4])+","+f(b[5])+"]";}private static String f(double x){return String.format(Locale.ROOT,"%.12g",x);}
 private static void api(String a,String t){currentApi=a;currentFeature=t;System.out.println("M10A0_3E_API_BEGIN|api="+a+"|feature="+t);}private static String rt(String n){try{return((String)Class.forName(RUNTIME_CLASS).getField(n).get(null)).trim();}catch(Exception e){throw new IllegalStateException("RUNTIME_INPUT_MISSING: "+n,e);}}
 public static void main(String[]a)throws Exception{run();}
}
