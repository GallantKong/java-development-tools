package org.gallant.jdt.core.visitor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * @author kongyong
 * @date 2019/11/4
 */
public class SwitchesCleanerVisitor extends ASTVisitor {

    private static final String SWITCH_UTILS = "SwitchUtils";
    private static final String OPENED = "opened";
    private static final String DOT = ".";
    private ASTRewrite astRewrite;
    /**
     * key : 属性名，value：属性类型
     */
    private Map<String, String> fieldNameTypes = new HashMap<>();

    /**
     * key：属性类型，value：属性类型全限定名
     */
    private Map<String, String> typeFullyQualifiedNames = new HashMap<>();

    private String packageName;
    private String typeName;
    private CompilationUnit astRoot;
    private Map<Statement, Comment> statementCommentMap = new HashMap<>(8);
    private Set<DispatchSwitchCleanLog> dispatchSwitchCleanLogs = new HashSet<>();

    public SwitchesCleanerVisitor(ASTRewrite astRewrite, CompilationUnit astRoot) {
        this.astRewrite = astRewrite;
        this.astRoot = astRoot;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean visit(Block node) {
        fillStatementCommentMap(node);
        Block newNode = (Block) ASTNode.copySubtree(node.getAST(), node);
        List statements = newNode.statements();
        Iterator it = statements.iterator();
        boolean hasModified = false;
        List allStatements = new LinkedList();
        while (it.hasNext()) {
            Object obj = it.next();
            boolean modified = false;
            if (obj instanceof IfStatement) {
                List newStatements = processIfStatement((IfStatement) obj);
                if (newStatements != null) {
                    if (CollectionUtils.isNotEmpty(newStatements)) {
                        allStatements.addAll(newStatements);
                    }
                    hasModified = true;
                    modified = true;
                } else {
                    // 处理场景
                    // if (Objects.isNull(spaceContent))
                    //    return true;
                    // else if (!SwitchUtils.currentCityOpen(this.lbsRiderSpaceServiceSwitches, spaceContent.getCityId())) {
                    IfStatement ifStatement = (IfStatement) obj;
                    if (ifStatement.getElseStatement() instanceof IfStatement) {
                        IfStatement elseIf = (IfStatement) ifStatement.getElseStatement();
                        String switchKey = matchedSwitchUtilsExpressionPrefixExpression(elseIf.getExpression());
                        if (StringUtils.isNotBlank(switchKey)) {
                            addDispatchSwitchCleanLogIfNeed(switchKey);
                            ifStatement.setElseStatement(null);
                            // 不需要拷贝当前节点，仅需删除else语句即可
                            hasModified = true;
                        }
                    }
                }
            }
            if (!modified) {
                ASTNode nodeObj = (ASTNode) obj;
                allStatements.add(ASTNode.copySubtree(nodeObj.getAST(), nodeObj));
            }
            it.remove();
        }
        if (hasModified) {
            statements.addAll(allStatements);
            boolean hasReturned = false;
            Iterator statementIt = statements.iterator();
            while (statementIt.hasNext()) {
                Object obj = statementIt.next();
                if (!hasReturned && obj instanceof ReturnStatement) {
                    hasReturned = true;
                    continue;
                }
                if (hasReturned) {
                    statementIt.remove();
                }
            }
            astRewrite.replace(node, newNode, null);
        }
        return super.visit(node);
    }

    private List processIfStatement(IfStatement node) {
        List newStatements = null;
        if (node.getExpression() != null) {
            Statement statement = null;
            String switchKey = matchedSwitchUtilsExpressionMethodInvocation(node.getExpression());
            if (StringUtils.isNotBlank(switchKey)) {
                statement = node.getThenStatement();
            } else if (StringUtils.isNotBlank((switchKey = matchedSwitchUtilsExpressionPrefixExpression(node.getExpression())))) {
                statement = node.getElseStatement();
            }
            if (StringUtils.isNotBlank(switchKey)) {
                addDispatchSwitchCleanLogIfNeed(switchKey);
                if (statement instanceof Block) {
                    fillStatementCommentMap((Block) statement);
                    Block block = (Block) statement;
                    List statements = block.statements();
                    newStatements = ASTNode.copySubtrees(block.getAST(), statements);
                }
                // 不为null代表需要删除节点
                if (newStatements == null) {
                    newStatements = new LinkedList();
                }
            }
        }
        return newStatements;
    }

    private void fillStatementCommentMap(Block block) {
        List commentList = astRoot.getCommentList();
        List statements = block.statements();
        for (Object statementObj : statements) {
            Statement statement = (Statement) statementObj;
            int commentIndex = astRoot.firstLeadingCommentIndex(statement);
            if (commentIndex > -1) {
                statementCommentMap.putIfAbsent(statement,
                        (Comment) commentList.get(commentIndex));
            }
        }
    }

    @Override
    public boolean visit(ConditionalExpression node) {
        String switchKey = matchedSwitchUtilsExpressionMethodInvocation(node.getExpression());
        if (StringUtils.isNotBlank(switchKey)) {
            astRewrite.replace(node, node.getThenExpression(), null);
        } else if (StringUtils.isNotBlank((matchedSwitchUtilsExpressionPrefixExpression(node.getExpression())))) {
            astRewrite.replace(node, node.getElseExpression(), null);
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(InfixExpression node) {
        // IfStatement 可以处理，需要一层层向下遍历，直接通过visit访问处理
        // 处理开关与其他条件共同判断场景，例如：if (platformId != null && SwitchUtils.currentCityOpen(null, order.getCityId()) && orderType != null)
        String switchKey = matchedSwitchUtilsExpressionMethodInvocation(node.getLeftOperand());
        if (StringUtils.isNotBlank(switchKey)
                || StringUtils.isNotBlank(switchKey = matchedSwitchUtilsExpressionPrefixExpression(node.getLeftOperand()))) {
            astRewrite.remove(node.getLeftOperand(), null);
        } else if (StringUtils.isNotBlank(switchKey = matchedSwitchUtilsExpressionMethodInvocation(node.getRightOperand()))
                || StringUtils.isNotBlank(switchKey = matchedSwitchUtilsExpressionPrefixExpression(node.getRightOperand()))) {
            astRewrite.remove(node.getRightOperand(), null);
        }
        addDispatchSwitchCleanLogIfNeed(switchKey);
        return super.visit(node);
    }

    private void addDispatchSwitchCleanLogIfNeed(String switchKey){
        if (StringUtils.isNotBlank(switchKey)) {
            DispatchSwitchCleanLog dispatchSwitchCleanLog = new DispatchSwitchCleanLog();
            dispatchSwitchCleanLog.setSwitchKey(switchKey);
            dispatchSwitchCleanLogs.add(dispatchSwitchCleanLog);
        }
    }

    @Override
    public boolean visit(PackageDeclaration node) {
        packageName = node.getName().getFullyQualifiedName();
        return super.visit(node);
    }

    /**
     * 不能与matchedSwitchUtilsExpressionPrefixExpression方法合并为一个方法，需要区分是否反向判断，即：!
     * @param expression : 
     * @return java.lang.String : 
     */
    private String matchedSwitchUtilsExpressionMethodInvocation(Expression expression) {
        String switchKey = null;
        if (expression instanceof MethodInvocation) {
            switchKey = matchedSwitchKey(expression);
        }
        return switchKey;
    }

    private String matchedSwitchUtilsExpressionPrefixExpression(Expression expression) {
        String switchKey = null;
        if (expression instanceof PrefixExpression) {
            switchKey = matchedSwitchKey(
                    ((PrefixExpression) expression).getOperand());
        }
        return switchKey;
    }

    private String matchedSwitchKey(Expression node) {
        String matchedSwitchKey = null;
        if (node instanceof MethodInvocation) {
            MethodInvocation mi = (MethodInvocation) node;
            Expression expression = mi.getExpression();
            if (expression instanceof SimpleName) {
                SimpleName sn = (SimpleName) expression;
                String invokerName = sn.getIdentifier();
                // 处理场景1
                if (SWITCH_UTILS.equals(invokerName)) {
                    List arguments = mi.arguments();
                    if (CollectionUtils.isNotEmpty(arguments)) {
                        for (Object arg : arguments) {
                            // 场景：return SwitchUtils.currentCityOpen(openBywaydegreeLog,order.getCityId())
                            matchedSwitchKey = SwitchMetaStoreNew
                                    .matchedSwitchKeyByField(arg, packageName, typeName);
                            // 场景：return SwitchUtils.currentCityOpen(BeanOrUtil.getOpenBywaydegreeLog(),order.getCityId())
                            if (StringUtils.isBlank(matchedSwitchKey)) {
                                matchedSwitchKey = SwitchMetaStoreNew
                                        .matchedSwitchKeyByMethod(arg, packageName, typeName);
                            }
                            String[] packageAndType = null;
                            if (StringUtils.isBlank(matchedSwitchKey)) {
                                String fieldName = SwitchMetaStoreNew.getSwitchFieldName(arg);
                                packageAndType = getPackageAndTypeByFieldName(fieldName);
                                if (packageAndType != null) {
                                    matchedSwitchKey = SwitchMetaStoreNew
                                            .matchedSwitchKeyByField(arg, packageAndType[0], packageAndType[1]);
                                }
                            }
                            if (StringUtils.isBlank(matchedSwitchKey) && packageAndType != null) {
                                matchedSwitchKey = SwitchMetaStoreNew
                                        .matchedSwitchKeyByMethod(arg, packageAndType[0], packageAndType[1]);
                            }
                            if (StringUtils.isNotBlank(matchedSwitchKey)) {
                                break;
                            }
                        }
                    }
                }
                // 处理场景2：return switches.opened(1)
                if (StringUtils.isBlank(matchedSwitchKey)) {
                    if (OPENED.equals(mi.getName().getIdentifier())) {
                        matchedSwitchKey = SwitchMetaStoreNew
                                .matchedSwitchKeyByField(invokerName, packageName, typeName);
                    }
                }
                // 处理场景3：return getNormalSwitches().opened(1)
                if (StringUtils.isBlank(matchedSwitchKey)) {
                    if (OPENED.equals(mi.getName().getIdentifier())) {
                        matchedSwitchKey = SwitchMetaStoreNew.matchedSwitchKeyByMethod(mi, packageName, typeName);
                    }
                }
                String[] packageAndType = null;
                if (StringUtils.isBlank(matchedSwitchKey)) {
                    if (OPENED.equals(mi.getName().getIdentifier())) {
                        matchedSwitchKey = SwitchMetaStoreNew
                                .matchedSwitchKeyByField(invokerName, packageName, typeName);
                        if (StringUtils.isBlank(matchedSwitchKey)) {
                            packageAndType = getPackageAndTypeByFieldName(invokerName);
                            if (packageAndType != null) {
                                matchedSwitchKey = SwitchMetaStoreNew
                                        .matchedSwitchKeyByField(invokerName, packageAndType[0], packageAndType[1]);
                            }
                        }
                    }
                }
                // 处理场景4：return getNormalSwitches().opened(1)
                if (StringUtils.isBlank(matchedSwitchKey) && packageAndType != null) {
                    if (OPENED.equals(mi.getName().getIdentifier())) {
                        matchedSwitchKey = SwitchMetaStoreNew
                                .matchedSwitchKeyByMethod(mi, packageAndType[0], packageAndType[1]);
                    }
                }
                // 处理场景6：if (SwitchConfigUtil.isOpenSwitchesNewAngle(1))
                if (StringUtils.isBlank(matchedSwitchKey)) {
                    packageAndType = getPackageAndTypeByFieldName(invokerName);
                    if (packageAndType != null) {
                        matchedSwitchKey = SwitchMetaStoreNew
                                .matchedSwitchKeyByMethod(node, packageAndType[0], packageAndType[1]);
                    }
                }
            }
            // 处理场景5：if (switchesHolderBean.getNormalSwitches().opened(1))
            if (StringUtils.isBlank(matchedSwitchKey) && expression instanceof MethodInvocation) {
                MethodInvocation getInvokeMethod = (MethodInvocation) expression;
                Expression invokeExpression = getInvokeMethod.getExpression();
                String[] packageAndType = null;
                if (invokeExpression instanceof SimpleName) {
                    SimpleName invokeName = (SimpleName) invokeExpression;
                    packageAndType = getPackageAndTypeByFieldName(invokeName.getIdentifier());
                }
                if (packageAndType != null) {
                    matchedSwitchKey = SwitchMetaStoreNew
                            .matchedSwitchKeyByMethod(expression, packageAndType[0], packageAndType[1]);
                }
            }
        }
        return matchedSwitchKey;
    }

    private String[] getPackageAndTypeByFieldName(String fieldName) {
        String[] packageAndType = null;
        if (StringUtils.isNotBlank(fieldName)) {
            String fieldTypeName = fieldNameTypes.get(fieldName);
            boolean isStaticMethod = false;
            if (StringUtils.isBlank(fieldTypeName) && Character.isUpperCase(fieldName.substring(0, 1).toCharArray()[0])) {
                // 静态方法调用场景
                fieldTypeName = fieldName;
                isStaticMethod = true;
            }
            String fieldTypeFullyName = null;
            if (StringUtils.isNotBlank(fieldTypeName)) {
                if (!fieldTypeName
                        .contains(DOT)) {
                    fieldTypeFullyName = typeFullyQualifiedNames.get(fieldTypeName);
                    if (isStaticMethod) {
                        // 同包下不需要import导入，所以typeFullyQualifiedNames中可能不存在
                        fieldTypeFullyName = packageName + DOT + fieldTypeName;
                    }
                } else {
                    fieldTypeFullyName = fieldTypeName;
                }
            }
            if (StringUtils.isNotBlank(fieldTypeFullyName)) {
                packageAndType = new String[2];
                packageAndType[0] = fieldTypeFullyName.substring(0, fieldTypeFullyName.lastIndexOf(DOT));
                packageAndType[1] = fieldTypeFullyName
                        .substring(fieldTypeFullyName.lastIndexOf(DOT) + 1);
            }
        }
        return packageAndType;
    }

    @Override
    public boolean visit(ReturnStatement node) {
        String switchKey = SwitchMetaStoreNew.matchedSwitchKey4ReturnStatement(node, packageName, typeName);
        if (StringUtils.isNotBlank(switchKey)) {
            ASTNode parent = node.getParent();
            while (!(parent instanceof MethodDeclaration)) {
                parent = parent.getParent();
            }
            addDispatchSwitchCleanLogIfNeed(switchKey);
            astRewrite.remove(parent, null);
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        if (!node.getType().isPrimitiveType()) {
            Type type = node.getType();
            if (type.isSimpleType()) {
                SimpleType simpleType = (SimpleType) type;
                List fragments = node.fragments();
                String feildName = null;
                if (CollectionUtils.isNotEmpty(fragments)) {
                    Object fragmentObj = fragments.get(0);
                    if (fragmentObj instanceof VariableDeclarationFragment) {
                        VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragmentObj;
                        feildName = vdf.getName().getIdentifier();
                    }
                }
                if (StringUtils.isNotBlank(feildName)) {
                    String fullName = simpleType.getName().getFullyQualifiedName();
                    if (!fullName.contains(DOT)) {
                        fullName = packageName + DOT + fullName;
                    }
                    fieldNameTypes
                            .putIfAbsent(feildName, fullName);
                }
            }
        }
        String switchKey;
        if (StringUtils.isNotBlank(switchKey = SwitchMetaStoreNew.matchedSwitchKey4FieldDeclaration(node, packageName, typeName))) {
            addDispatchSwitchCleanLogIfNeed(switchKey);
            astRewrite.replace(node, null, null);
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(ImportDeclaration node) {
        String fullyQualifiedName = node.getName().getFullyQualifiedName();
        typeFullyQualifiedNames
                .putIfAbsent(fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf(".") + 1),
                        fullyQualifiedName);
        return super.visit(node);
    }

    @Override
    public void endVisit(PackageDeclaration node) {
        packageName = node.getName().getFullyQualifiedName();
        super.endVisit(node);
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        typeName = node.getName().getFullyQualifiedName();
        return super.visit(node);
    }

    public Map<Statement, Comment> getStatementCommentMap() {
        return statementCommentMap;
    }

    public Set<DispatchSwitchCleanLog> getDispatchSwitchCleanLogs() {
        return dispatchSwitchCleanLogs;
    }
}
