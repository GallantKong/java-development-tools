package org.gallant.jdt.core.visitor.v2;

import java.io.IOException;
import java.util.Map;
import java.util.jar.JarFile;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * @author kongyong
 * @date 2020/1/8
 */
public class AstBuilder {

    static final Map<String, String> COMPILER_OPTIONS;
    static {
        COMPILER_OPTIONS = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, COMPILER_OPTIONS);
    }

    public static CompilationUnit createAst(String src) throws IOException {
        ASTParser astParser = ASTParser.newParser(AST.JLS11);
        astParser.setCompilerOptions(COMPILER_OPTIONS);
        astParser.setResolveBindings(true);
        astParser.setBindingsRecovery(true);
        astParser.setStatementsRecovery(true);
        astParser.setUnitName("myUnitJust4ResolveBinding");
        JarFile dispatchUtils = new JarFile("D:\\Applications\\apache-maven-3.5.0\\conf\\repository\\com\\dianwoba\\dispatch\\dispatch-utils\\2.0.0-SNAPSHOT\\dispatch-utils-2.0.0-20191127.075555-3.jar");
        JarFile lombok = new JarFile("D:\\Applications\\apache-maven-3.5.0\\conf\\repository\\org\\projectlombok\\lombok\\1.16.10\\lombok-1.16.10.jar");
        JarFile springBeans = new JarFile("D:\\Applications\\apache-maven-3.5.0\\conf\\repository\\org\\springframework\\spring-beans\\4.3.20.RELEASE\\spring-beans-4.3.20.RELEASE.jar");
        JarFile wirelessGlobalSwitches = new JarFile("D:\\Applications\\apache-maven-3.5.0\\conf\\repository\\com\\dianwoba\\wireless\\wireless-global-switches\\3.0.2-SNAPSHOT\\wireless-global-switches-3.0.2-20190418.114959-5.jar");
        JarFile wirelessGlobalSwitchesApi = new JarFile("D:\\Applications\\apache-maven-3.5.0\\conf\\repository\\com\\dianwoba\\wireless\\wireless-global-switches-api\\3.0.2-SNAPSHOT\\wireless-global-switches-api-3.0.2-20190326.052638-1.jar");
        String[] classpathEntries = new String[]{dispatchUtils.getName(), lombok.getName(), springBeans.getName(), wirelessGlobalSwitches.getName(), wirelessGlobalSwitchesApi.getName()};
        astParser.setEnvironment(classpathEntries, null, null, true);
        astParser.setSource(src.toCharArray());
        return (CompilationUnit) astParser.createAST(null);
    }

}
