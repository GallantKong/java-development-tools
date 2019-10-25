import java.io.File;
import org.gallant.jdt.core.ManipulatingJavaCode;

/**
 * @author kongyong
 * @date 2019/10/25
 */
public class MainpulatingJavaCodeTest {

    public static void main(String[] args) throws Exception {
        String[] keys = new String[]{"switches-open-bywaydegree-log", "test-city", "switches-newAngle"};
        ManipulatingJavaCode.switchClean(new File("D:/tmp/workspace/dispatch-filter-rules/src/main/java/com/dianwoba/dispatch/filter/rules/riderfilter/AbstractByWayDegreeFilter.java"), keys);
        ManipulatingJavaCode.switchCleanByDir("D:\\tmp\\workspace\\dispatch-bywaydegree", keys);
    }
}
