package org.gallant.jdt.core.visitor.v2;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * @author kongyong
 * @date 2019/10/18
 */
public class SwitchCollectVisitor extends ASTVisitor {

    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final String DEFAULT_PLACEHOLDER_PREFIX = "${";
    private static final String DEFAULT_VALUE_SEPARATOR = ":";
    private static final String QUOTATION = "\"";
    private static final String GET_METHOD_FMT = "%s.get%s";
    private static final String GET_METHOD_FMT4RETURN = "%s.%s";
    private static final String THIS = "this.";
    private static final String DATA_CLASS_KEY = "Llombok/Data;";
    private static final String VALUE_CLASS_KEY = "Lorg/springframework/beans/factory/annotation/Value;";
    private static final String GETTER_CLASS_KEY = "Llombok/Getter;";
    private static final String SWITCHER_CLASS_KEY = "Lcom/dianwoba/wireless/switches/annotation/Switcher;";
    private static final String SWITCH_UTILS_CUR_CITY_OPEN = "Lcom/dianwoba/dispatch/utils/SwitchUtils;.currentCityOpen(Ljava/lang/String;Ljava/lang/Integer;)Z";
    private static final String CITY_PLATFORM_SWITCHES_OPENED = "Lcom/dianwoba/wireless/switches/switcher/CityPlatformSwitches;.opened(Lcom/dianwoba/wireless/switches/switcher/CityPlatformSwitches$CityPlatformParam;)Z";
    private static final String CITY_SWITCHES_OPENED = "Lcom/dianwoba/wireless/switches/switcher/CitySwitches;.opened(Ljava/lang/Integer;)Z";
    private static final String NORMAL_SWITCHES_OPENED = "Lcom/dianwoba/wireless/switches/switcher/NormalSwitches;.opened()Z";
    private static final String PLATFORM_SWITCHES_OPENED = "Lcom/dianwoba/wireless/switches/switcher/PlatformSwitches;.opened(Ljava/lang/Integer;)Z";
    private static final String KEY_FMT = "%s.%s)%s";
    private ITypeBinding typeBinding;
    private static final List<String> OPENED_SWITCHES = Lists
            .newArrayList(CITY_PLATFORM_SWITCHES_OPENED, CITY_SWITCHES_OPENED, NORMAL_SWITCHES_OPENED, PLATFORM_SWITCHES_OPENED);
    private Map<String, CompilationUnit> packageAndCompilationUnit;

    public SwitchCollectVisitor(Map<String, CompilationUnit> packageAndCompilationUnit) {
        this.packageAndCompilationUnit = packageAndCompilationUnit;
    }

    /**
     * 收集所有开关字段信息，lombok生成的get方法信息
     * @param node :
     * @return boolean :
     */
    @Override
    public boolean visit(TypeDeclaration node) {
        ITypeBinding iTypeBinding = node.resolveBinding();
        this.typeBinding = iTypeBinding;
        if (iTypeBinding != null) {
            IAnnotationBinding[] typeAnnotations = iTypeBinding.getAnnotations();
            boolean hasGetter = false;
            for (IAnnotationBinding typeAnnotation : typeAnnotations) {
                String key = typeAnnotation.getAnnotationType().getKey();
                if (key.equals(DATA_CLASS_KEY)) {
                    hasGetter = true;
                }
            }
            IVariableBinding[] fields = iTypeBinding.getDeclaredFields();
            for (IVariableBinding field : fields) {
                IAnnotationBinding[] fieldAnnotations = field.getAnnotations();
                String matchedSwitchKey = null;
                for (IAnnotationBinding fieldAnnotation : fieldAnnotations) {
                    String key = fieldAnnotation.getAnnotationType().getKey();
                    if (VALUE_CLASS_KEY.equals(key)) {
                        matchedSwitchKey = addSwitchField(fieldAnnotation, field, VALUE);
                    }
                    if (SWITCHER_CLASS_KEY.equals(key)) {
                        matchedSwitchKey = addSwitchField(fieldAnnotation, field, KEY);
                    }
                    if (!hasGetter && GETTER_CLASS_KEY.equals(key)) {
                        hasGetter = true;
                    }
                }
                if (StringUtils.isNotBlank(matchedSwitchKey) && hasGetter) {
                    String fieldName = field.getName();
                    String firstUpperCaseSwitchField = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                    SwitchMetaStore.addObtainSwitchFieldMethods(matchedSwitchKey
                            , String.format(GET_METHOD_FMT, typeBinding.getKey(), firstUpperCaseSwitchField));
                }
            }
        }
        return super.visit(node);
    }

    private String addSwitchField(IAnnotationBinding fieldAnnotation, IVariableBinding field, String annotationKey){
        String matchedSwitchKey = null;
        IMemberValuePairBinding[] all = fieldAnnotation.getAllMemberValuePairs();
        for (IMemberValuePairBinding iMemberValuePairBinding : all) {
            String name = iMemberValuePairBinding.getName();
            if (annotationKey.equals(name)) {
                Object valueObj = iMemberValuePairBinding.getValue();
                if (valueObj != null) {
                    String switchKey = valueObj.toString();
                    if (StringUtils.isNotBlank(switchKey)) {
                        switchKey = resolveSwitchKey(switchKey);
                        if (SwitchMetaStore.isSwitchKey(switchKey)) {
                            SwitchMetaStore.addSwitchKeyFieldIfAbsent(switchKey, field);
                            matchedSwitchKey = switchKey;
                        }
                    }
                }
            }
        }
        return matchedSwitchKey;
    }

    private static String resolveSwitchKey(String value) {
        String switchKey = value;
        if (value.contains(DEFAULT_PLACEHOLDER_PREFIX)) {
            int index = value.indexOf(DEFAULT_VALUE_SEPARATOR);
            switchKey = value.substring(value.indexOf(DEFAULT_PLACEHOLDER_PREFIX) + DEFAULT_PLACEHOLDER_PREFIX.length(),
                    index < 1 ? value.length() - 1 : index);
        }
        if (switchKey.startsWith(QUOTATION)) {
            switchKey = switchKey.substring(1);
        }
        if (switchKey.endsWith(QUOTATION)) {
            switchKey = switchKey.substring(0, switchKey.length() - 1);
        }
        return switchKey;
    }

    @Override
    public boolean visit(ReturnStatement node) {
        Expression expression = node.getExpression();
        String fieldName = getSwitchFieldName(expression);
        String matchedKey = null;
        if (StringUtils.isNotBlank(fieldName)) {
            matchedKey = matchSwitchKeyByField(fieldName, expression.resolveTypeBinding());
        }
        if (StringUtils.isBlank(matchedKey) && expression instanceof MethodInvocation) {
            MethodInvocation methodInvocation = (MethodInvocation) expression;
            String methodKey = null;
            IMethodBinding iMethodBinding = methodInvocation.resolveMethodBinding();
            if (iMethodBinding != null) methodKey = iMethodBinding.getKey();
            if (SWITCH_UTILS_CUR_CITY_OPEN.equals(methodKey)) {
                List args = methodInvocation.arguments();
                ITypeBinding[] iTypeBindings = iMethodBinding.getParameterTypes();
                if (CollectionUtils.isNotEmpty(args)) {
                    for (int i = 0; i < args.size(); i++) {
                        ITypeBinding iTypeBinding = iTypeBindings[i];
                        Object arg = args.get(i);
                        // 场景：return SwitchUtils.currentCityOpen(openBywaydegreeLog,order.getCityId())
                        matchedKey = matchSwitchKeyByField(arg.toString(), iTypeBinding);
                        // 场景：return SwitchUtils.currentCityOpen(BeanOrUtil.getOpenBywaydegreeLog(),order.getCityId())
                        if (StringUtils.isBlank(matchedKey)) {
                            if (arg instanceof MethodInvocation) {
                                MethodInvocation argMethodInvocation = (MethodInvocation) arg;
                                IMethodBinding methodBinding = argMethodInvocation.resolveMethodBinding();
                                if (methodBinding != null) {
                                    String argMethodKey = methodBinding.getKey();
                                    matchedKey = SwitchMetaStore.matchSwitchKeyByMethodBindingKey(argMethodKey);
                                }
                            }
                        }
                        if (StringUtils.isNotBlank(matchedKey)) {
                            break;
                        }
                    }
                }
            }
            // 处理场景2：return switches.opened(1)
            if (StringUtils.isBlank(matchedKey)) {
                if (StringUtils.isNotBlank(methodKey)) {
                    if (OPENED_SWITCHES.contains(methodKey)) {
                        if (methodInvocation.getExpression() instanceof SimpleName) {
                            matchedKey = matchSwitchKeyByField(((SimpleName) methodInvocation.getExpression()).getIdentifier()
                                    , iMethodBinding.getDeclaringClass());
                        }
                    }
                }
            }
            // 处理场景2：return getNormalSwitches().opened(1)
            if (StringUtils.isBlank(matchedKey) && methodInvocation.getExpression() instanceof MethodInvocation) {
                MethodInvocation returnMethodInvocation = (MethodInvocation) methodInvocation
                        .getExpression();
                String returnMethodInvocationKey = null;
                IMethodBinding returnMethodBinding = returnMethodInvocation.resolveMethodBinding();
                if (returnMethodBinding == null) {
                    // lombok get 方法
                    Expression invoker = returnMethodInvocation.getExpression();
                    String methodName = returnMethodInvocation.getName().getIdentifier();
                    if (invoker != null) {
                        ITypeBinding iTypeBinding = invoker.resolveTypeBinding();
                        if (iTypeBinding != null) {
                            returnMethodInvocationKey = String.format(GET_METHOD_FMT4RETURN, iTypeBinding.getKey(), methodName);
                        }
                    }
                } else {
                    // 获取属性的方法，非lombok注解
                    returnMethodInvocationKey = returnMethodBinding.getKey();
                }
                matchedKey = SwitchMetaStore.matchSwitchKeyByMethodBindingKey(
                        returnMethodInvocationKey);
            }
        }
        if (StringUtils.isNotBlank(matchedKey)) {
            ASTNode parent = node.getParent();
            while (parent != null) {
                if (parent instanceof MethodDeclaration) {
                    IMethodBinding iMethodBinding = ((MethodDeclaration) parent).resolveBinding();
                    SwitchMetaStore.addObtainSwitchFieldMethods(matchedKey, iMethodBinding.getKey());
                    break;
                }
                parent = parent.getParent();
            }
        }
        return super.visit(node);
    }

    private String matchSwitchKeyByField(String fieldName, ITypeBinding iTypeBinding) {
        String matchedKey = null;
        // 属性可能继承至父类，遍历父类
        if (iTypeBinding != null) {
            do {
                String key = String.format(KEY_FMT, typeBinding.getKey(), fieldName, iTypeBinding.getKey());
                matchedKey = SwitchMetaStore.matchSwitchKeyByFieldBindingKey(key);
            } while (StringUtils.isBlank(matchedKey)
                    && (iTypeBinding = iTypeBinding.getSuperclass()) != null);
        }
        return matchedKey;
    }

    public static String getSwitchFieldName(Object node) {
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
}
