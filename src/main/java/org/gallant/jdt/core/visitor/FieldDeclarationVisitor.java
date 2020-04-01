package org.gallant.jdt.core.visitor;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * @author kongyong
 * @date 2019/10/18
 */
public class FieldDeclarationVisitor extends ASTVisitor {

    private String packageName;
    private String typeName;

    @Override
    public boolean visit(FieldDeclaration node) {
        SwitchMetaStoreNew.isSwitchField(node, packageName, typeName);
        return super.visit(node);
    }

    @Override
    public boolean visit(PackageDeclaration node) {
        packageName = node.getName().getFullyQualifiedName();
        return super.visit(node);
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        typeName = node.getName().getFullyQualifiedName();
        return super.visit(node);
    }
}
