import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/** Strict 10-to-50 cm3/min historical N2 flow control using the pre-SSC real-CAD model. */
public final class LiNRR_M10A2_1_MatchedFlowControl {
    private static final String RT="LiNRR_M10A3_RuntimeInputs";
    private static int serial=0;
    private LiNRR_M10A2_1_MatchedFlowControl() {}
    public static void main(String[] args)throws Exception{
        String input=rt("MATCHED_INPUT_MPH"),output=rt("MATCHED_OUTPUT_MPH");
        Model m=ModelUtil.load("M10A21Matched",input);
        try{
            m.label("LiNRR_M10A2_1_matched_flow_control.mph");
            m.comments("Historical matched-flow control: exact real-CAD single-phase N2 channel, no SSC crossflow and no species transport. 10 and 50 cm^3/min are compared before any attribution to SSC.");
            m.param().set("Q_n2_test","10[cm^3/min]","M10A1 NUMERICAL_TEST_ONLY historical flow");
            m.param().set("flow_scale_n2_test","1");m.study("std_n2_stationary").run();
            String d=data(m,"std_n2_stationary","comp_n2_flow");double dp10=dp(m,d);
            for(double q:new double[]{20,35,50}){m.param().set("Q_n2_test",f(q)+"[cm^3/min]","MATCHED_FLOW_CONTROL");m.study("std_n2_stationary").run();System.out.println("M10A2_1_CONTINUATION|Q_cm3_min="+f(q)+"|status=SOLVED");}
            d=data(m,"std_n2_stationary","comp_n2_flow");double dp50=dp(m,d);
            if(!(dp10>0&&dp50>dp10))throw new IllegalStateException("MATCHED_FLOW_PRESSURE_TREND_INVALID");
            m.param().set("m10a21_dp_10",f(dp10)+"[Pa]","DERIVED historical 10 cm^3/min N2 cell pressure drop");
            m.param().set("m10a21_dp_50",f(dp50)+"[Pa]","DERIVED matched 50 cm^3/min single-phase no-crossflow pressure drop");
            m.save(output);
            System.out.println("M10A2_1_MATCHED_FLOW|dp10_Pa="+f(dp10)+"|dp50_Pa="+f(dp50)+"|ratio="+f(dp50/dp10)+"|classification=NUMERICAL_CONTROL");
            System.out.println("M10A2_1_MATCHED_FLOW_CONTROL=PASS");
        }finally{ModelUtil.remove("M10A21Matched");}
    }
    private static String data(Model m,String study,String comp){String[]s=m.study(study).getSolverSequences("SolverSequence");if(s.length<1)throw new IllegalStateException("NO_SOLVER");String d="dset_m10a21";if(!m.result().dataset().hasTag(d))m.result().dataset().create(d,"Solution");m.result().dataset(d).set("solution",s[s.length-1]);m.result().dataset(d).set("comp",comp);return d;}
    private static double dp(Model m,String d){return eval(m,d,"sel_bnd_n2_inlet","p2")-eval(m,d,"sel_bnd_n2_outlet","p2");}
    private static double eval(Model m,String d,String s,String e){String t="m10a21_"+(++serial);m.result().numerical().create(t,"AvSurface");try{m.result().numerical(t).set("data",d);m.result().numerical(t).selection().named(s);m.result().numerical(t).set("expr",new String[]{e});m.result().numerical(t).set("unit",new String[]{"Pa"});return m.result().numerical(t).getReal()[0][0];}finally{m.result().numerical().remove(t);}}
    private static String f(double x){return String.format(Locale.ROOT,"%.15g",x);}
    private static String rt(String n){try{return((String)Class.forName(RT).getField(n).get(null)).trim();}catch(Exception e){throw new IllegalStateException("RUNTIME_INPUT_MISSING: "+n,e);}}
}
