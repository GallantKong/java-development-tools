package org.gallant.jdt.core.visitor.v2;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.gallant.jdt.core.visitor.DispatchSwitchCleanLog;
import org.gallant.jdt.core.visitor.VariableDeclarationStatementDTO;

/**
 * @author kongyong
 * @date 2019/11/4
 */
public class SwitchesCleanerVisitor extends ASTVisitor {

    private static final String SWITCH_UTILS = "SwitchUtils";
    private static final String DOT = ".";
    private static final String KEY_IMPORT_STAR_FMT = "L%s/%s;.%s";
    private static final String KEY_METHOD_FMT = "%s.%s";
    private static final String KEY_FULL_FMT = "%s.%s)%s";
    private static final String THIS = "this.";
    private static final String L = "L";
    private static final String SEMICOLON = ";";
    private static final String STAR = "*";
    private static final String SLASH = "/";
    private static final String OBJECT="Object";
    private ASTRewrite astRewrite;

    private CompilationUnit astRoot;
    private Map<Statement, Comment> statementCommentMap = new HashMap<>(8);
    private Set<DispatchSwitchCleanLog> dispatchSwitchCleanLogs = new HashSet<>();
    private TypeDeclaration typeDeclaration;
    private Map<String, List<VariableDeclarationStatementDTO>> variableAndDeclarationStatements = new HashMap<>(8);

    SwitchesCleanerVisitor(ASTRewrite astRewrite, CompilationUnit astRoot) {
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
                List newStatements = processIfStatement((IfStatement) obj, node);
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
                        String switchKey = matchedSwitchKey4PrefixExpression(elseIf.getExpression());
                        if (StringUtils.isNotBlank(switchKey)) {
                            ifStatement.setElseStatement(null);
                            addDispatchSwitchCleanLogIfNeed(switchKey);
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

    private List processIfStatement(IfStatement node, ASTNode originNode) {
        List newStatements = null;
        if (node.getExpression() != null) {
            Statement statement = null;
            String switchKey = matchedSwitchKey4Expression(node.getExpression());
            if (StringUtils.isNotBlank(switchKey)) {
                statement = node.getThenStatement();
            } else if (StringUtils.isNotBlank((switchKey = matchedSwitchKey4PrefixExpression(node.getExpression())))) {
                statement = node.getElseStatement();
            }
            if (StringUtils.isBlank(switchKey)) {
                VariableDeclarationStatementDTO mathedVariable = matchedSwitchKey4Variable(node, originNode);
                if (mathedVariable != null) {
                    switchKey = mathedVariable.getSwitchKey();
                    if (mathedVariable.isReverseExpression()) {
                        statement = node.getElseStatement();
                    } else {
                        statement = node.getThenStatement();
                    }
                }
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
        String switchKey = matchedSwitchKey4Expression(node.getExpression());
        if (StringUtils.isNotBlank(switchKey)) {
            astRewrite.replace(node, node.getThenExpression(), null);
        } else if (StringUtils.isNotBlank((matchedSwitchKey4PrefixExpression(node.getExpression())))) {
            astRewrite.replace(node, node.getElseExpression(), null);
        }
        addDispatchSwitchCleanLogIfNeed(switchKey);
        return super.visit(node);
    }

    @Override
    public boolean visit(InfixExpression node) {
        // IfStatement 可以处理，需要一层层向下遍历，直接通过visit访问处理
        // 处理开关与其他条件共同判断场景，例如：if (platformId != null && SwitchUtils.currentCityOpen(null, order.getCityId()) && orderType != null)
        String switchKey = matchedSwitchKey4Expression(node.getLeftOperand());
        if (StringUtils.isNotBlank(switchKey)
                || StringUtils.isNotBlank(switchKey = matchedSwitchKey4PrefixExpression(node.getLeftOperand()))) {
            astRewrite.remove(node.getLeftOperand(), null);
        } else if (StringUtils.isNotBlank(switchKey = matchedSwitchKey4Expression(node.getRightOperand()))
                || StringUtils.isNotBlank(switchKey = matchedSwitchKey4PrefixExpression(node.getRightOperand()))) {
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

    /**
     * 不能与matchedSwitchKey4PrefixExpression方法合并为一个方法，需要区分是否反向判断，即：!
     * @param expression : 
     * @return java.lang.String : 
     */
    private String matchedSwitchKey4Expression(Expression expression) {
        String switchKey = null;
        if (expression instanceof MethodInvocation) {
            switchKey = matchedSwitchKey(expression);
        }
        return switchKey;
    }

    private String matchedSwitchKey4PrefixExpression(Expression expression) {
        String switchKey = null;
        if (expression instanceof PrefixExpression) {
            switchKey = matchedSwitchKey(
                    ((PrefixExpression) expression).getOperand());
        }
        return switchKey;
    }

    private String matchedSwitchKey(Expression node) {
        // 场景：getTest().opened(null)
        String returnKey = getKeyByFieldOrMethodInvocation(node, typeDeclaration);
        String matchedSwitchKey = SwitchMetaStore
                .matchStartWithSwitchKeyByField(returnKey);
        if (StringUtils.isBlank(matchedSwitchKey) && node instanceof MethodInvocation) {
            MethodInvocation mi = (MethodInvocation) node;
            Expression expression = mi.getExpression();
            if (expression instanceof SimpleName) {
                SimpleName sn = (SimpleName) expression;
                String invokerName = sn.getIdentifier();
                if (SWITCH_UTILS.equals(invokerName)) {
                    List arguments = mi.arguments();
                    if (CollectionUtils.isNotEmpty(arguments)) {
                        Object arg = arguments.get(0);
                        // 场景：if (SwitchUtils.currentCityOpen(switchesMaxBywayDegreeNew ,order.getCityId()))
                        // 场景：if (SwitchUtils.currentCityOpen(switchConfigUtil.getMySwitchesMaxBywayDegreeNew(), null))
                        // 场景：if (SwitchUtils.currentCityOpen(SwitchConfigUtil.getMySwitchesMaxBywayDegreeNew4Static(), null))
                        // 场景：if (SwitchUtils.currentCityOpen(switchesHolderBean.getMySwitchVal(), null))
                        String key = getKeyByFieldOrMethodInvocation(arg, typeDeclaration);
                        matchedSwitchKey = SwitchMetaStore
                                .matchStartWithSwitchKeyByField(key);
                    }
                }
                // 场景：if (SwitchConfigUtil.isOpenSwitchesMaxBywayDegreeNew(order.getCityId()))
                // 场景：if (switchConfigUtil.isOpen222222222222222222222222222(null))
                // 场景：if (test33.opened(123))
                // 场景：if (switchConfigUtil.isOpen3333333333333333333333333333())
                if (StringUtils.isBlank(matchedSwitchKey)) {
                    String key = getKeyByFieldOrMethodInvocation(mi, typeDeclaration);
                    matchedSwitchKey = SwitchMetaStore
                            .matchStartWithSwitchKeyByField(key);
                }
            }
            // 场景：if (switchesHolderBean.getNormalSwitches().opened(1))
            if (expression instanceof MethodInvocation) {
                matchedSwitchKey = matchedSwitchKey(expression);
            }
        }
        return matchedSwitchKey;
    }

    @Override
    public boolean visit(ReturnStatement node) {
        String matchedSwitchKey = matchedSwitchKey(node.getExpression());
        if (StringUtils.isNotBlank(matchedSwitchKey)) {
            ASTNode parent = node.getParent();
            while (!(parent instanceof MethodDeclaration)) {
                parent = parent.getParent();
            }
            astRewrite.remove(parent, null);
            addDispatchSwitchCleanLogIfNeed(matchedSwitchKey);
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        String key = null;
        for (Object fragment : node.fragments()) {
            if (fragment instanceof VariableDeclarationFragment) {
                VariableDeclarationFragment variableDeclarationFragment = (VariableDeclarationFragment) fragment;
                key = variableDeclarationFragment.resolveBinding().getKey();
                break;
            }
        }
        String switchKey;
        if (StringUtils.isNotBlank(switchKey = SwitchMetaStore.matchStartWithSwitchKeyByField(key))) {
            astRewrite.remove(node, null);
            addDispatchSwitchCleanLogIfNeed(switchKey);
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        typeDeclaration = node;
        return super.visit(node);
    }

    Map<Statement, Comment> getStatementCommentMap() {
        return statementCommentMap;
    }

    Set<DispatchSwitchCleanLog> getDispatchSwitchCleanLogs() {
        return dispatchSwitchCleanLogs;
    }

    private String getKeyByFieldOrMethodInvocation(Object fieldObj, TypeDeclaration typeDeclaration) {
        String fieldName = getSwitchFieldName(fieldObj);
        String fieldMethodInvocationName = null;
        if (fieldObj instanceof MethodInvocation) {
            MethodInvocation fieldMethodInvocation = (MethodInvocation) fieldObj;
            Expression getExpr = fieldMethodInvocation.getExpression();
            if (getExpr instanceof SimpleName) {
                fieldName = ((SimpleName) getExpr).getIdentifier();
            }
            // 场景：return getTest2().opened
            if (getExpr == null) {
                fieldName = typeDeclaration.getName().getIdentifier();
            }
            if (getExpr instanceof MethodInvocation) {
                return getKeyByFieldOrMethodInvocation(getExpr, typeDeclaration);
            }
            fieldMethodInvocationName = fieldMethodInvocation.getName().getIdentifier();
        }
        return getKeyByFieldOrMethodInvocation(fieldName, fieldMethodInvocationName, typeDeclaration, astRoot);
    }

    private String getKeyByFieldOrMethodInvocation(String fieldName, String fieldMethodInvocationName, TypeDeclaration typeDeclaration, CompilationUnit compilationUnit) {
        List<String> candidateKeys = Lists.newLinkedList();
        String key = findKey(fieldName, fieldMethodInvocationName, typeDeclaration, candidateKeys, compilationUnit);
        if (StringUtils.isBlank(key)) {
            Type parentType = typeDeclaration.getSuperclassType();
            if (parentType instanceof SimpleType) {
                SimpleType simpleType = (SimpleType) parentType;
                String parentName = simpleType.getName().getFullyQualifiedName();
                if (!OBJECT.equals(parentName)) {
                    List<String> candidatePackages = Lists.newLinkedList();
                    ImportCollectVisitor importCollectVisitor = new ImportCollectVisitor();
                    typeDeclaration.accept(importCollectVisitor);
                    String importTypeBindingKey = findImportTypeBindingKey(parentName, candidatePackages, importCollectVisitor.getImportDeclarations());
                    if (StringUtils.isBlank(importTypeBindingKey) && CollectionUtils.isNotEmpty(candidatePackages)) {
                        importTypeBindingKey = getKey(candidatePackages.get(0), parentName);
                    }
                    if (StringUtils.isBlank(importTypeBindingKey) && CollectionUtils.isEmpty(candidatePackages)) {
                        importTypeBindingKey = getKey(compilationUnit.getPackage().getName().getFullyQualifiedName(), parentName);
                    }
                    if (StringUtils.isNotBlank(importTypeBindingKey)) {
                        Map<String, CompilationUnit> packageAndCompilationUnit = SwitchMetaStore.getPackageAndCompilationUnit();
                        for (Entry<String, CompilationUnit> entry : packageAndCompilationUnit.entrySet()) {
                            CompilationUnit parentCompilationUnit = entry.getValue();
                            ASTNode astNode = parentCompilationUnit.findDeclaringNode(importTypeBindingKey);
                            if (astNode instanceof TypeDeclaration) {
                                TypeDeclaration parent = (TypeDeclaration) astNode;
                                key = getKeyByFieldOrMethodInvocation(fieldName, fieldMethodInvocationName, parent, parentCompilationUnit);
                            }
                        }
                    }
                }
            }
        }
        if (StringUtils.isBlank(key) && CollectionUtils.isNotEmpty(candidateKeys)) {
            key = candidateKeys.get(0);
        }
        return key;
    }

    private static String getKey(String packageName, String parentName) {
        return String.format("%s%s%s%s%s", L , packageName.replace(DOT, SLASH), SLASH, parentName, SEMICOLON);
    }

    private String findKey(String fieldName, String fieldMethodInvocationName, TypeDeclaration typeDeclaration, List<String> candidateKeys, CompilationUnit compilationUnit) {
        ITypeBinding localFieldTypeBinding = null;
        Set<String> localVariableTypeNames = Sets.newHashSet();
        if (StringUtils.isNotBlank(fieldName)) {
            if (Character.isLowerCase(fieldName.charAt(0))) {
                fieldLoop : for (FieldDeclaration fieldDeclaration : typeDeclaration.getFields()) {
                    List fragments = fieldDeclaration.fragments();
                    if (CollectionUtils.isNotEmpty(fragments)) {
                        for (Object fragment : fragments) {
                            if (fragment instanceof VariableDeclarationFragment) {
                                VariableDeclarationFragment variableDeclarationFragment = (VariableDeclarationFragment) fragment;
                                Name name = variableDeclarationFragment.getName();
                                if (fieldName.equals(name.toString())) {
                                    ITypeBinding fieldTypeBinding = fieldDeclaration.getType().resolveBinding();
                                    if (fieldTypeBinding != null) {
                                        boolean isRecovered = fieldTypeBinding.isRecovered();
                                        if (isRecovered) {
                                            localVariableTypeNames.add(fieldTypeBinding.getName());
                                        } else {
                                            localFieldTypeBinding = fieldTypeBinding;
                                        }
                                    }
                                    break fieldLoop;
                                }
                            }
                        }
                    }
                }
                if (localFieldTypeBinding == null) {
                    typeDeclaration.accept(new ASTVisitor(){
                        @Override
                        public boolean visit(VariableDeclarationStatement node) {
                            ITypeBinding iTypeBinding = node.getType().resolveBinding();
                            List fragments = node.fragments();
                            boolean matched;
                            if (CollectionUtils.isNotEmpty(fragments)) {
                                for (Object fragmentObj : fragments) {
                                    if (fragmentObj instanceof VariableDeclarationFragment) {
                                        VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObj;
                                        if (fieldName.equals(fragment.getName().getIdentifier())) {
                                            localVariableTypeNames.add(iTypeBinding.getName());
                                            break;
                                        }
                                    }
                                }
                            }
                            return super.visit(node);
                        }
                    });
                }
            } else {
                // 静态方法调用
                localVariableTypeNames.add(fieldName);
            }
        }
        String key = null;
        // 本地变量声明，类型直接能取到，组装key
        if (localFieldTypeBinding != null && StringUtils.isNotBlank(localFieldTypeBinding.getKey())) {
            key = String.format(KEY_FULL_FMT, typeDeclaration.resolveBinding().getKey(), fieldName, localFieldTypeBinding.getKey());
        } else if (CollectionUtils.isNotEmpty(localVariableTypeNames)){
            for (String localVariableTypeName : localVariableTypeNames) {
                if (StringUtils.isBlank(localVariableTypeName)) {
                    continue;
                }
                List<String> candidatePackages = Lists.newLinkedList();
                List importObjs = compilationUnit.imports();
                List<ImportDeclaration> imports = Lists.newLinkedList();
                if (CollectionUtils.isNotEmpty(importObjs)) {
                    for (Object importObj : importObjs) {
                        if (importObj instanceof ImportDeclaration) {
                            imports.add((ImportDeclaration) importObj);
                        }
                    }
                }
                String importTypeBindingKey = findImportTypeBindingKey(localVariableTypeName,
                        candidatePackages, imports);
                if (StringUtils.isNotBlank(importTypeBindingKey)) {
                    key = String.format(KEY_METHOD_FMT, importTypeBindingKey,
                            fieldMethodInvocationName);
                }
                if (CollectionUtils.isNotEmpty(candidatePackages)) {
                    for (String packageKey : candidatePackages) {
                        candidateKeys.add(String
                                .format(KEY_IMPORT_STAR_FMT, packageKey, localVariableTypeName,
                                        fieldMethodInvocationName));
                    }
                }
                if (StringUtils.isBlank(key) && CollectionUtils.isEmpty(candidatePackages)) {
                    candidateKeys.add(String.format(KEY_IMPORT_STAR_FMT,
                            compilationUnit.getPackage().getName().getFullyQualifiedName()
                                    .replace(DOT, SLASH), localVariableTypeName,
                            fieldMethodInvocationName));
                }
            }
        }
        return key;
    }


    private String findImportTypeBindingKey(String localVariableTypeName, List<String> candidatePackages, List<ImportDeclaration> importDeclarations) {
        String importTypeBindingKey = null;
        // 查找类型全限定名称
        for (ImportDeclaration importDeclaration : importDeclarations) {
            IBinding binding = importDeclaration.resolveBinding();
            if (binding instanceof ITypeBinding) {
                ITypeBinding iTypeBinding = (ITypeBinding) binding;
                String importTypeName = iTypeBinding.getName();
                if (localVariableTypeName.equals(importTypeName)) {
                    importTypeBindingKey = iTypeBinding.getKey();
                    int lIndex = importTypeBindingKey.indexOf(L);
                    if (lIndex > -1) {
                        int semicolonIndex = importTypeBindingKey.indexOf(SEMICOLON);
                        importTypeBindingKey = importTypeBindingKey.substring(lIndex, semicolonIndex + 1);
                    }
                    break;
                }
            }
            if (importDeclaration.toString().contains(STAR)) {
                if (binding instanceof ITypeBinding) {
                    ITypeBinding iTypeBinding = (ITypeBinding) binding;
                    String importPackageName = iTypeBinding.getQualifiedName();
                    candidatePackages.add(importPackageName.replace(DOT, SLASH));
                }
            }
        }
        return importTypeBindingKey;
    }

    /**
     *
     * @param node : AST节点
     * @return java.lang.String :
     */
    private static String getSwitchFieldName(Object node) {
        String switchFieldName = null;
        if (node instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess) node;
            node = fieldAccess.getName();
        }
        if (node instanceof SimpleName) {
            SimpleName name = (SimpleName) node;
            switchFieldName = name.getIdentifier();
        }
        if (node instanceof String) {
            switchFieldName = node.toString();
        }
        if (StringUtils.isNotBlank(switchFieldName) && switchFieldName.startsWith(THIS)) {
            switchFieldName = switchFieldName.substring(switchFieldName.indexOf(THIS) + THIS.length());
        }
        return switchFieldName;
    }

    @Override
    public boolean visit(VariableDeclarationStatement node) {
        List fragments = node.fragments();
        if (CollectionUtils.isNotEmpty(fragments)) {
            for (Object fragmentObj : fragments) {
                if (fragmentObj instanceof VariableDeclarationFragment) {
                    VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObj;
                    Expression expression = fragment.getInitializer();
                    String switchKey = matchedSwitchKey4Expression(expression);
                    Boolean isReverseExpression = null;
                    if (StringUtils.isNotBlank(switchKey)) {
                        isReverseExpression = false;
                    } else if (StringUtils.isNotBlank(
                            (switchKey = matchedSwitchKey4PrefixExpression(expression)))) {
                        isReverseExpression = true;
                    }
                    if (StringUtils.isNotBlank(switchKey) && isReverseExpression != null) {
                        variableAndDeclarationStatements
                                .putIfAbsent(fragment.getName().getIdentifier(),
                                        Lists.newLinkedList());
                        variableAndDeclarationStatements.get(fragment.getName().getIdentifier())
                                .add(VariableDeclarationStatementDTO.builder().switchKey(switchKey)
                                        .variableDeclarationStatement(node)
                                        .isReverseExpression(isReverseExpression).build());
                        break;
                    }
                }
            }
        }
        return super.visit(node);
    }

    private VariableDeclarationStatementDTO matchedSwitchKey4Variable(IfStatement ifStatement, ASTNode originNode){
        VariableDeclarationStatementDTO mathedVariable = null;
        Expression expression = ifStatement.getExpression();
        if (expression instanceof SimpleName) {
            SimpleName simpleName = (SimpleName) expression;
            String name = simpleName.getIdentifier();
            List<VariableDeclarationStatementDTO> variableDeclarationStatements = variableAndDeclarationStatements.get(name);
            List<Object> allParentStatement = Lists.newLinkedList();
            if (CollectionUtils.isNotEmpty(variableDeclarationStatements)) {
                ASTNode parent = originNode.getParent();
                while (parent != null) {
                    if (parent instanceof MethodDeclaration) {
                        break;
                    }
                    if (parent instanceof Block) {
                        Block block = (Block) parent;
                        allParentStatement.addAll(block.statements());
                    } else {
                        allParentStatement.add(parent);
                    }
                    parent = parent.getParent();
                }
                search : for (VariableDeclarationStatementDTO variableDeclarationStatement : variableDeclarationStatements) {
                    for (Object statementObj : allParentStatement) {
                        if (statementObj instanceof Statement) {
                            Statement statement = (Statement) statementObj;
                            boolean matched = statement.subtreeMatch(new ASTMatcher(), variableDeclarationStatement.getVariableDeclarationStatement());
                            if (matched) {
                                mathedVariable = variableDeclarationStatement;
                                break search;
                            }
                        }
                    }
                }
            }
        }
        return mathedVariable;
    }

    @Override
    public void postVisit(ASTNode node) {
        if (MapUtils.isNotEmpty(variableAndDeclarationStatements)) {
            List<VariableDeclarationStatementDTO> variableDeclarationStatements = Lists.newLinkedList();
            variableAndDeclarationStatements.values().forEach(variableDeclarationStatements::addAll);
            variableDeclarationStatements.forEach(variableDeclarationStatement -> astRewrite.remove(variableDeclarationStatement.getVariableDeclarationStatement(), null));
        }
        super.postVisit(node);
    }
}
