import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Independent-process reload and editable-structure check for both M02.2 MPH files. */
public final class LiNRR_M02_2_LoadCheck {
    private LiNRR_M02_2_LoadCheck() {}

    public static void main(String[] args) throws Exception {
        String rootText=requireRunInput("LINRR_PROJECT_ROOT");
        Path root=Paths.get(rootText).toAbsolutePath().normalize();

        Model operators=ModelUtil.load("M022MmsReload",
            root.resolve("models/generated/LiNRR_M02_2_operator_benchmarks.mph").toString());
        requireOne(operators,"comp_m","sel_m_domain",2);
        operators.component("comp_m").physics("tds_m");
        operators.study("std_m");requireFinite(operators,"m_min");

        Model diffusion=ModelUtil.load("M022DiffusionReload",
            root.resolve("models/generated/LiNRR_M02_2_diffusion_operator.mph").toString());
        requireOne(diffusion,"comp_d","sel_d_domain",2);
        diffusion.component("comp_d").physics("tds_d");
        diffusion.study("std_d");requireFinite(diffusion,"d_min");

        Model wall=ModelUtil.load("M022WallReload",
            root.resolve("models/generated/LiNRR_M02_2_transport_verification.mph").toString());
        requireOne(wall,"comp1","sel_electrolyte",2);
        requireOne(wall,"comp1","sel_inlet",1);
        requireOne(wall,"comp1","sel_outlet",1);
        requireOne(wall,"comp1","sel_cathode_wall",1);
        wall.component("comp1").physics("spf");wall.component("comp1").physics("tds");
        wall.study("std_audit");requireFinite(wall,"m022_min_n2");
        System.out.println("M022_RELOAD|PASS|diffusion, MMS operator, and wall MPH files independently loaded; " +
            "named selections, physics, studies, retained solutions, and numerical nodes are editable");
    }

    private static void requireOne(Model model,String comp,String selection,int dim){
        int count=model.component(comp).selection(selection).entities(dim).length;
        if(count!=1)throw new IllegalStateException("FAILED_SELECTION_MAPPING after reload: "+
            comp+"/"+selection+" count="+count);
    }

    private static void requireFinite(Model model,String numerical){
        double[][] values=model.result().numerical(numerical).getReal();
        if(values==null||values.length==0||values[0].length==0||!Double.isFinite(values[0][0]))
            throw new IllegalStateException("Retained numerical result unavailable: "+numerical);
    }

    private static String requireRunInput(String name){
        try{
            Class<?> inputs=Class.forName("LiNRR_M02_2_RunInputs");
            Object value=inputs.getMethod("get",String.class).invoke(null,name);
            if(value!=null&&!value.toString().trim().isEmpty())return value.toString();
        }catch(Throwable ignored){
            // Fall through for direct execution under an ordinary JVM.
        }
        try{
            String value=System.getenv(name);
            if(value!=null&&!value.trim().isEmpty())return value;
        }catch(SecurityException blocked){
            throw new IllegalStateException(name+
                " is unavailable; run-specific input bridge was not loaded.",blocked);
        }
        throw new IllegalStateException(name+" is required.");
    }
}
