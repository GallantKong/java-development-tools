package org.gallant.jdt.core;

import java.util.List;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * @author kongyong
 * @date 2019/10/18
 */
public class SwitchesCleaner extends ASTVisitor {

    private static final String SWITCH_UTILS = "SwitchUtils";
    private static final String SWITCHER = "Switcher";
    private static final String GETTER = "Getter";
    private static final String OPENED = "opened";
    private static final String VALUE = "Value";
    private static final String DEFAULT_PLACEHOLDER_PREFIX = "${";
    private static final String DEFAULT_VALUE_SEPARATOR = ":";
    private static final String QUOTATION = "\"";
    private static final String TYPE_METHOD_FMT = "%s.%s";
    private static final String TYPE_GET_METHOD_FMT = "%s.get%s";
    private static final String GET = "get";
    private ASTRewrite astRewrite;

    SwitchesCleaner(ASTRewrite astRewrite, String... switchKeys) {
        this.astRewrite = astRewrite;
        SwitchMetaStore.initSwitchKeyFields(switchKeys);
    }

    @Override
    public boolean visit(IfStatement node) {
        if (node.getExpression() != null) {
            Statement statement = null;
            boolean needReplace = false;
            // 处理开关为true的场景，例如：if (SwitchUtils.currentCityOpen(switchesNewBywayDegreeCal, order.getCityId()))
            if (isSwitchUtilsExpression(node.getExpression())) {
                statement = node.getThenStatement();
                needReplace = true;
            }
            // 处理开关为false的场景，例如：if(!SwitchUtils.currentCityOpen(openBywaydegreeLog, order.getCityId()))
            if (isSwitchUtilsPrefixExpression(node.getExpression())) {
                statement = node.getElseStatement();
                needReplace = true;
            }
            if (needReplace) {
                astRewrite.replace(node, statement, null);
            }
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(InfixExpression node) {
        // IfStatement 可以处理，需要一层层向下遍历，直接通过visit访问处理
        // 处理开关与其他条件共同判断场景，例如：if (platformId != null && SwitchUtils.currentCityOpen(null, order.getCityId()) && orderType != null)
        if (isSwitchUtilsExpression(node.getLeftOperand())) {
            astRewrite.replace(node.getLeftOperand(), null, null);
        }
        if (isSwitchUtilsExpression(node.getRightOperand())) {
            astRewrite.replace(node.getRightOperand(), null, null);
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        // 加载配置的key与配置key对应属性名，并删除开全国的开关属性
        List modifiers = node.modifiers();
        if (CollectionUtils.isNotEmpty(modifiers)) {
            boolean hasGetter = false;
            boolean hasSwitcher = false;
            String switchKey = null;
            for (Object obj : modifiers) {
                if (obj instanceof Annotation) {
                    Annotation annotation = (Annotation) obj;
                    if (VALUE.equals(annotation.getTypeName().toString())) {
                        SingleMemberAnnotation sma = (SingleMemberAnnotation) annotation;
                        switchKey = sma.getValue().toString();
                    } else if (SWITCHER.equals(annotation.getTypeName().toString())) {
                        NormalAnnotation na = (NormalAnnotation) annotation;
                        List valueNodes = na.values();
                        for (Object valueObj : valueNodes) {
                            if (valueObj instanceof MemberValuePair) {
                                MemberValuePair value = (MemberValuePair) valueObj;
                                switchKey = value.getValue().toString();
                            }
                        }
                        hasSwitcher = true;
                    } else if (GETTER.equals(annotation.getTypeName().toString())) {
                        hasGetter = true;
                    }
                    if (StringUtils.isNotBlank(switchKey)) {
                        switchKey = resolveSwitchKey(switchKey);
                        String matchedKey = matchedKey(switchKey);
                        if (matchedKey != null) {
                            List fragments = node.fragments();
                            if (CollectionUtils.isNotEmpty(fragments)) {
                                Object fragmentObj = fragments.get(0);
                                if (fragmentObj instanceof VariableDeclarationFragment) {
                                    VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragmentObj;
                                    SwitchMetaStore.addSwitchKeyField(matchedKey, vdf.getName().getIdentifier());
                                    astRewrite.replace(node, null, null);
                                }
                            }
                        }
                    }
                }
            }
            // 如果存在Getter注解的属性，则将get+属性名称（首字母大写）方法存入属性映射：switchKeyMethods
            if (StringUtils.isNotBlank(switchKey) && hasGetter && hasSwitcher) {
                String switchField = SwitchMetaStore.getSwitchField(switchKey);
                if (StringUtils.isNotBlank(switchField)) {
                    String firstUpperCaseSwitchField = switchField.substring(0, 1).toUpperCase() + switchField.substring(1);
                    ASTNode astNode = node.getParent();
                    if (astNode instanceof TypeDeclaration) {
                        TypeDeclaration typeDeclaration = (TypeDeclaration) astNode;
                        SwitchMetaStore.addSwitchKeyMethodIfAbsent(switchKey,
                                String.format(TYPE_GET_METHOD_FMT, typeDeclaration.getName().getIdentifier(), firstUpperCaseSwitchField));
                    }
                }
            }
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(ReturnStatement node) {
        // 加载与开关相关的方法信息
        boolean isGetSwitchFieldMethod = (node.getExpression() instanceof SimpleName && SwitchMetaStore.switchFields().contains(((SimpleName) node.getExpression()).getIdentifier()));
        if (isSwitch(node.getExpression()) || isGetSwitchFieldMethod) {
            ASTNode parent = node.getParent();
            while (!(parent instanceof MethodDeclaration)) {
                parent = parent.getParent();
            }
            astRewrite.replace(parent, null, null);
        }
        return super.visit(node);
    }

    private boolean isSwitchUtilsExpression(Expression expression){
        boolean isSwitchExpression = false;
        if (expression instanceof MethodInvocation && isSwitch(expression)) {
            isSwitchExpression = true;
        }
        return isSwitchExpression;
    }

    private boolean isSwitchUtilsPrefixExpression(Expression expression){
        boolean isSwitchExpression = false;
        if (expression instanceof PrefixExpression && isSwitch(((PrefixExpression) expression).getOperand())) {
            isSwitchExpression = true;
        }
        return isSwitchExpression;
    }

    private String matchedKey(String switchKey){
        String matchedKey = null;
        Set<String> switchKeys = SwitchMetaStore.switchKeySet();
        if (switchKeys.size() > 0) {
            for (String key : switchKeys) {
                if (StringUtils.isNotBlank(switchKey) && StringUtils.isNotBlank(key) && switchKey.equals(key)) {
                    matchedKey = key;
                }
            }
        }
        return matchedKey;
    }

    private boolean isSwitch(ASTNode node){
        boolean isSwitch = false;
        if (node instanceof MethodInvocation) {
            MethodInvocation mi = (MethodInvocation) node;
            Expression expression = mi.getExpression();
            String methodName = mi.getName().getIdentifier();
            if (expression instanceof SimpleName) {
                SimpleName sn = (SimpleName) expression;
                // 处理场景1：if (SwitchUtils.currentCityOpen(openBywaydegreeLog,order.getCityId()))
                if (SWITCH_UTILS.equals(sn.getIdentifier())) {
                    List arguments = mi.arguments();
                    if (CollectionUtils.isNotEmpty(arguments)) {
                        for (Object arg : arguments) {
                            if (arg instanceof SimpleName) {
                                SimpleName argName = (SimpleName) arg;
                                if (SwitchMetaStore.switchFields().contains(argName.getIdentifier())) {
                                    isSwitch = true;
                                }
                            }
                            // 加载数据场景：return SwitchUtils.currentCityOpen(openBywaydegreeLog,order.getCityId())
                            // 从ReturnStatement语句加载开关与方法对应关系
                            if (!isSwitch) {
                                isSwitch = loadSwitchKeyMethods(arg);
                            }
                        }
                    }
                }
                // 处理场景2：if (switches.opened(1))
                if (!isSwitch && SwitchMetaStore.switchFields().contains(sn.getIdentifier())) {
                    isSwitch = OPENED.equals(mi.getName().getIdentifier());
                }
                // 处理场景3：if (UtilOrBean.isOpenSwitchesNewAngle(1))
                // 处理场景4：if (SwitchUtils.currentCityOpen(switchConfigUtil.getSwitchesNewAngle(), cityId))
                if (!isSwitch) {
                    isSwitch = isSwitch(sn, methodName);
                }
            }
            // 处理场景5：if (switchesHolderBean.getNormalSwitches().opened(1))
            if (!isSwitch && expression instanceof MethodInvocation) {
                MethodInvocation getInvokeMethod = (MethodInvocation) expression;
                Expression invokeExpression = getInvokeMethod.getExpression();
                if (invokeExpression instanceof SimpleName) {
                    SimpleName invokeName = (SimpleName) invokeExpression;
                    isSwitch = isSwitch(invokeName, getInvokeMethod.getName().getIdentifier());
                }
            }
        }
        return isSwitch;
    }

    private boolean isSwitch(SimpleName sn, String methodName){
        String name = String.format(TYPE_METHOD_FMT, sn.getIdentifier(), methodName);
        String first = name.substring(0, 1);
        String name1 = (Character.isUpperCase(first.toCharArray()[0]) ? first.toLowerCase() : first.toUpperCase()) + name.substring(1);
        return SwitchMetaStore.switchMethods().contains(name) || SwitchMetaStore.switchMethods().contains(name1);
    }

    private boolean loadSwitchKeyMethods(Object arg){
        boolean isSwitch = false;
        if (arg instanceof MethodInvocation) {
            MethodInvocation getter = (MethodInvocation) arg;
            String getterMethodName = getter.getName().getIdentifier();
            if (getterMethodName.startsWith(GET)) {
                String switchFieldName = getterMethodName.substring(GET.length());
                switchFieldName = switchFieldName.substring(0, 1).toLowerCase() + switchFieldName.substring(1);
                if (SwitchMetaStore.switchFields().contains(switchFieldName)) {
                    // 仅记录开关关联的方法信息
                    ASTNode node = getter.getParent();
                    ASTNode returnNode = node.getParent();
                    if (returnNode instanceof ReturnStatement) {
                        ASTNode parent = returnNode.getParent();
                        while (parent != null) {
                            if (parent instanceof MethodDeclaration) {
                                MethodDeclaration methodDeclaration = (MethodDeclaration) parent;
                                String methodName = methodDeclaration.getName().getIdentifier();
                                String typeName = ((TypeDeclaration) parent.getParent()).getName().getIdentifier();
                                SwitchMetaStore.addSwitchKeyMethodByFieldName(switchFieldName, String.format(TYPE_METHOD_FMT, typeName, methodName));
                                isSwitch = true;
                                break;
                            }
                            parent = parent.getParent();
                        }
                    }
                }
            }
        }
        return isSwitch;
    }

    private String resolveSwitchKey(String value) {
        String switchKey = value;
        if (value.contains(DEFAULT_PLACEHOLDER_PREFIX)) {
            switchKey = value.substring(value.indexOf(DEFAULT_PLACEHOLDER_PREFIX) + DEFAULT_PLACEHOLDER_PREFIX.length(),
                    value.indexOf(DEFAULT_VALUE_SEPARATOR));
        }
        if (switchKey.startsWith(QUOTATION)) {
            switchKey = switchKey.substring(1);
        }
        if (switchKey.endsWith(QUOTATION)) {
            switchKey = switchKey.substring(0, switchKey.length() - 1);
        }
        return switchKey;
    }
}
