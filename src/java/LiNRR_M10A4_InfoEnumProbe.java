import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_InfoEnumProbe {
  public static void main(String[] args) throws Exception {
    System.out.println("VARIABLEINFO_TYPE");
    for (Object v : com.comsol.model.applapi.VariableInfo.Type.values()) System.out.println(v);
  }
}
