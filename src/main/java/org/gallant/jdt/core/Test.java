package org.gallant.jdt.core;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.eclipse.core.internal.localstore.Bucket.Visitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

/**
 * @author kongyong
 * @date 2019/11/25
 */
public class Test {

    public static void main(String[] args) throws IOException {
        // creation of DOM/AST from a ICompilationUnit
        ASTParser astParser = ASTParser.newParser(AST.JLS11);
        Map<String, String> compilerOptions = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, compilerOptions);
        astParser.setCompilerOptions(compilerOptions);
        astParser.setResolveBindings(true);
        astParser.setBindingsRecovery(true);
        astParser.setStatementsRecovery(true);
        astParser.setUnitName("myUnit");
        astParser.setEnvironment(null, null, null, true);
        String fileName = "D:\\tmp\\workspace\\dispatch-bywaydegree\\src\\main\\java\\com\\dianwoba\\dispatch\\bywaydegree\\AbstractByWayDegreeFilter.java";
        String src = FileUtils.readFileToString(new File(fileName), "utf8");
        astParser.setSource(src.toCharArray());
        ASTNode astRoot = astParser.createAST(null);
        MyVisitor visitor = new MyVisitor();
        astRoot.accept(visitor);
        ASTRewrite astRewrite = ASTRewrite.create(astRoot.getAST());
        Document document = new Document(src);
        astRewrite.replace(null, null, null);
        TextEdit edits = astRewrite.rewriteAST(document, compilerOptions);
//        edits.apply(null);
        System.out.println(astRoot);
    }

    public static class MyVisitor extends ASTVisitor {

        @Override
        public boolean visit(ArrayAccess node) {
            return super.visit(node);
        }

        @Override
        public boolean visit(FieldAccess node) {
            return super.visit(node);
        }

        @Override
        public boolean visit(TypeDeclaration node) {
            ITypeBinding iTypeBinding = node.resolveBinding();
            IVariableBinding[] ibs = iTypeBinding.getDeclaredFields();
            IMethodBinding[] imbs = iTypeBinding.getDeclaredMethods();
            System.out.println();
            return super.visit(node);
        }
    }
}
