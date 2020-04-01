import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.TreeVisitor;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * @author kongyong
 * @date 2019/12/6
 */
public class JavaParserTest {

    public static void main(String[] args) throws IOException {
        String fileName = "D:\\tmp\\workspace\\dispatch-bywaydegree\\src\\main\\java\\com\\dianwoba\\dispatch\\bywaydegree\\AbstractByWayDegreeFilter.java";
        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = javaParser.parse(new File(fileName));
//        Optional<ClassOrInterfaceDeclaration> classA = compilationUnit.getClassByName("A");
//        compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream()
//                .filter(c -> !c.isInterface()
//                        && c.isAbstract()
//                        && !c.getNameAsString().startsWith("Abstract"))
//                .forEach(c -> {
//                    String oldName = c.getNameAsString();
//                    String newName = "Abstract" + oldName;
//                    System.out.println("Renaming class " + oldName + " into " + newName);
//                    c.setName(newName);
//                });
        new TreeVisitor() {
            @Override
            public void process(Node node) {
                System.out.println("node:"+node);
            }
        }.visitBreadthFirst(parseResult.getResult().get());
    }

}
