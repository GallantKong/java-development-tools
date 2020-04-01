package org.gallant.jdt.core.visitor.v2;

import com.google.common.collect.Lists;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ImportDeclaration;

/**
 * @author kongyong
 * @date 2020/1/8
 */
public class ImportCollectVisitor extends ASTVisitor {

    private List<ImportDeclaration> importDeclarations = Lists.newLinkedList();

    @Override
    public boolean visit(ImportDeclaration node) {
        importDeclarations.add(node);
        return super.visit(node);
    }

    public List<ImportDeclaration> getImportDeclarations() {
        return importDeclarations;
    }
}
