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
    private static final String OPENED = "opened";
    private static final String VALUE = "Value";
    private static final String DEFAULT_PLACEHOLDER_PREFIX = "${";
    private static final String DEFAULT_VALUE_SEPARATOR = ":";
    private static final String QUOTATION = "\"";
    private static final String TYPE_METHOD_FMT = "%s.%s";
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
        // 加载配置的key与配置key对应属性名
        List modifiers = node.modifiers();
        if (CollectionUtils.isNotEmpty(modifiers)) {
            for (Object obj : modifiers) {
                if (obj instanceof Annotation) {
                    Annotation annotation = (Annotation) obj;
                    String switchKey = null;
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
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(ReturnStatement node) {
        // 加载与开关相关的方法信息
        isSwitch(node.getExpression());
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
            List arguments = mi.arguments();
            String methodName = mi.getName().getIdentifier();
            if (expression instanceof SimpleName) {
                SimpleName sn = (SimpleName) expression;
                // 处理场景：SwitchUtils.currentCityOpen(openBywaydegreeLog,order.getCityId())
                if (SWITCH_UTILS.equals(sn.getIdentifier())) {
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
                            loadSwitchKeyMethods(mi, arg);
                        }
                    }
                }
                // 处理场景：if (switches.opened(1))
                if (!isSwitch && SwitchMetaStore.switchFields().contains(sn.getIdentifier())) {
                    isSwitch = OPENED.equals(mi.getName().getIdentifier());
                }
                // 处理场景1：UtilOrBean.isOpenSwitchesNewAngle(1)
                // 处理场景2：SwitchUtils.currentCityOpen(switchConfigUtil.getSwitchesNewAngle(), cityId)
                if (!isSwitch) {
                    String name = String.format(TYPE_METHOD_FMT, sn.getIdentifier(), methodName);
                    String name1 = name.substring(0, 1).toLowerCase() + name.substring(1);
                    isSwitch = SwitchMetaStore.switchMethods().contains(name) || SwitchMetaStore.switchMethods().contains(name1);
                }
            }
        }
        return isSwitch;
    }

    private void loadSwitchKeyMethods(ASTNode node, Object arg){
        if (arg instanceof MethodInvocation) {
            MethodInvocation getter = (MethodInvocation) arg;
            String getterMethodName = getter.getName().getIdentifier();
            if (getterMethodName.startsWith(GET)) {
                String switchFieldName = getterMethodName.substring(GET.length());
                switchFieldName = switchFieldName.substring(0, 1).toLowerCase() + switchFieldName.substring(1);
                if (SwitchMetaStore.switchFields().contains(switchFieldName)) {
                    // 仅记录开关关联的方法信息
                    ASTNode returnNode = node.getParent();
                    if (returnNode instanceof ReturnStatement) {
                        ASTNode parent = returnNode.getParent();
                        while (parent != null) {
                            if (parent instanceof MethodDeclaration) {
                                MethodDeclaration methodDeclaration = (MethodDeclaration) parent;
                                String methodName = methodDeclaration.getName().getIdentifier();
                                String typeName = ((TypeDeclaration) parent.getParent()).getName().getIdentifier();
                                SwitchMetaStore.addSwitchKeyMethodByFieldName(switchFieldName, String.format(TYPE_METHOD_FMT, typeName, methodName));
                                break;
                            }
                            parent = parent.getParent();
                        }
                    }
                }
            }
        }
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
