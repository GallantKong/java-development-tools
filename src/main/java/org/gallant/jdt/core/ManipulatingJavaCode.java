package org.gallant.jdt.core;

import java.io.File;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

/**
 * @author kongyong
 * @date 2019/10/16
 */
@Slf4j
public class ManipulatingJavaCode {

    private static void switchClean(String javaFilePath) throws Exception {
        String src;
        try {
            src = FileUtils.readFileToString(new File(javaFilePath), "utf8");
        } catch (Exception var5) {
            log.error("读取源文件失败", var5);
            return;
        }
        if (StringUtils.isBlank(src)) {
            log.error("源文件为空");
            return;
        }
        Document document= new Document(src);

        // creation of DOM/AST from a ICompilationUnit
        ASTParser astParser = ASTParser.newParser(AST.JLS11);
        Map<String, String> compilerOptions = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, compilerOptions);
        astParser.setCompilerOptions(compilerOptions);
        astParser.setResolveBindings(true);
        astParser.setBindingsRecovery(true);
        astParser.setStatementsRecovery(true);
        astParser.setSource(src.toCharArray());
        CompilationUnit astRoot = (CompilationUnit) astParser.createAST(null);

        // creation of ASTRewrite
        ASTRewrite astRewrite = ASTRewrite.create(astRoot.getAST());

        // description of the change
        SimpleName oldName = ((TypeDeclaration)astRoot.types().get(0)).getName();
        SimpleName newName = astRoot.getAST().newSimpleName("Y");
        astRewrite.replace(oldName, newName, null);

        String[] keys = new String[]{"switches-open-bywaydegree-log", "test-city", "switches-newAngle"};
        SwitchesCleaner switchesFinder = new SwitchesCleaner(astRewrite, keys);
        // 1. 正常属性开关清理
        astRoot.accept(switchesFinder);
        // 2. 开关工具类或开关bean清理
        astRoot.accept(switchesFinder);

        // computation of the text edits
        TextEdit edits = astRewrite.rewriteAST(document, compilerOptions);

        // computation of the new source code
        edits.apply(document);

        // update of the compilation unit
        System.out.println(document.get());
    }

    public static void main(String[] args) throws Exception {
        switchClean("D:/tmp/workspace/dispatch-filter-rules/src/main/java/com/dianwoba/dispatch/filter/rules/riderfilter/AbstractByWayDegreeFilter.java");
    }

}
