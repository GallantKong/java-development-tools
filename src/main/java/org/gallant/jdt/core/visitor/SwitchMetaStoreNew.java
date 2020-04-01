package org.gallant.jdt.core.visitor;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * @author kongyong
 * @date 2019/10/25
 */
@Slf4j
public class SwitchMetaStoreNew {

    private static final String PACKAGE_TYPE_NAME_FMT = "%s.%s.%s";
    private static Map<String, Set<String>> switchKeyFields = new ConcurrentHashMap<>(8);
    private static Map<String, String> switchFieldKeys = new ConcurrentHashMap<>(8);
    private static Map<String, Set<String>> switchKeyMethods = new ConcurrentHashMap<>(8);
    private static Map<String, String> srcCodeCache = new HashMap<>(128);
    private static Map<String, String> switchKeyProjectPaths = new HashMap<>(128);
    private static final String SWITCHER = "Switcher";
    private static final String GETTER = "Getter";
    private static final String VALUE = "Value";
    private static final String DEFAULT_PLACEHOLDER_PREFIX = "${";
    private static final String DEFAULT_VALUE_SEPARATOR = ":";
    private static final String QUOTATION = "\"";
    private static final String GET_METHOD_FMT = "get%s";
    private static final String SWITCH_UTILS = "SwitchUtils";
    private static final String OPENED = "opened";
    private static final String THIS = "this.";

    private SwitchMetaStoreNew() {
    }

    public static void initSwitchKeyFields(String dir, String[] switchKeys) throws IOException {
        if (switchKeys != null) {
            for (String switchKey : switchKeys) {
                switchKeyFields.put(switchKey, new HashSet<>());
                switchKeyMethods.putIfAbsent(switchKey, new HashSet<>());
            }
        }
        if (StringUtils.isNotBlank(dir)) {
            Collection<File> files = FileUtils.listFiles(new File(dir),
                    FileFilterUtils.suffixFileFilter("java"), FileFilterUtils.trueFileFilter());
            for (File file : files) {
                String src = FileUtils.readFileToString(file, "utf8");
                if (StringUtils.isBlank(src)) {
                    log.error("源文件为空");
                    continue;
                }
                srcCodeCache.putIfAbsent(file.getAbsolutePath(), src);
            }
        }
    }

    public static void addSwitchKeyField(String key, String packageName, String typeName, String fieldName){
        String field = getNameWithPackageAndType(packageName, typeName, fieldName);
        switchKeyFields.get(key).add(field);
        switchFieldKeys.put(field, key);
    }

    public static String getNameWithPackageAndType(String packageName, String typeName, String name){
        return String.format(PACKAGE_TYPE_NAME_FMT, packageName, typeName, name);
    }

    public static void addSwitchKeyMethodByFieldName(String fieldName, String methodName, String packageName, String typeName) {
        switchKeyMethods.get(switchFieldKeys.get(fieldName)).add(getNameWithPackageAndType(packageName, typeName, methodName));
    }

    public static void addSwitchKeyMethod(String switchKey, String packageName, String typeName, String methodName) {
        switchKeyMethods.get(switchKey).add(getNameWithPackageAndType(packageName, typeName, methodName));
    }

    public static Set<String> switchKeySet(){
        return switchKeyFields.keySet();
    }

    public static String matchedSwitchKeyByField(Object node, String packageName, String typeName) {
        String matchedSwitchKey = null;
        String switchFieldName = getSwitchFieldName(node);
        if (StringUtils.isNotBlank(switchFieldName)) {
            switchFieldName = getNameWithPackageAndType(packageName, typeName, switchFieldName);
        }
        if (StringUtils.isNotBlank(switchFieldName)) {
            Set<Entry<String, Set<String>>> keyFieldNames = switchKeyFields.entrySet();
            for (Entry<String, Set<String>> entry : keyFieldNames) {
                if (entry.getValue().contains(switchFieldName)) {
                    matchedSwitchKey = entry.getKey();
                }
            }
        }
        return matchedSwitchKey;
    }

    public static String matchedSwitchKeyByMethod(Object node, String packageName, String typeName) {
        String matchedSwitchKey = null;
        if (node instanceof MethodInvocation) {
            MethodInvocation getter = (MethodInvocation) node;
            String getterMethodName = getter.getName().getIdentifier();
            if (MapUtils.isNotEmpty(switchKeyMethods)) {
                for (Entry<String, Set<String>> entry : switchKeyMethods.entrySet()) {
                    if (entry.getValue().contains(getNameWithPackageAndType(packageName, typeName, getterMethodName))) {
                        matchedSwitchKey = entry.getKey();
                    }
                }
            }
        }
        return matchedSwitchKey;
    }

    /**
     *
     * @param node : AST节点
     * @return java.lang.String : 
     */
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

    public static void clear(){
        switchKeyFields.clear();
        switchFieldKeys.clear();
        switchKeyMethods.clear();
        srcCodeCache.clear();
        switchKeyProjectPaths.clear();
    }

    public static void println(){
        log.info("switchKeyFields={}\nswitchFieldKeys={}\nswitchKeyMethods={}\nsrcCodeCache.keySet={}\nswitchKeyProjectPaths={}\n", switchKeyFields, switchFieldKeys, switchKeyMethods, srcCodeCache.keySet(), switchKeyProjectPaths);
    }

    public static Map<String, String> getSrcCodeCache() {
        return new HashMap<>(srcCodeCache);
    }

    public static void addSwitchKeyProjectPathIfNeed(String path) {
        addSwitchKeyProjectPathIfNeed(path, switchKeyFields);
    }

    private static void addSwitchKeyProjectPathIfNeed(String path,
            Map<String, Set<String>> keyFieldsOrMethods) {
        if (keyFieldsOrMethods.size() > 0) {
            for (Entry<String, Set<String>> entry : keyFieldsOrMethods.entrySet()) {
                if (CollectionUtils.isNotEmpty(entry.getValue())) {
                    switchKeyProjectPaths.put(entry.getKey(), path);
                }
            }
        }
    }

    public static Map<String, String> getSwitchKeyProjectPaths() {
        return new HashMap<>(switchKeyProjectPaths);
    }

    public static boolean isSwitchField(FieldDeclaration node, String packageName, String typeName) {
        return StringUtils.isNotBlank(matchedSwitchKey4FieldDeclaration(node, packageName, typeName));
    }

    public static String matchedSwitchKey4FieldDeclaration(FieldDeclaration node, String packageName, String typeName) {
        String matchedKey = null;
        // 加载配置的key与配置key对应属性名，并删除开全国的开关属性
        List modifiers = node.modifiers();
        if (CollectionUtils.isNotEmpty(modifiers)) {
            boolean hasGetter = false;
            boolean hasSwitcher = false;
            String switchKey = null;
            String matchedFeildName = null;
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
                        matchedKey = matchedKey(switchKey);
                        if (matchedKey != null) {
                            List fragments = node.fragments();
                            if (CollectionUtils.isNotEmpty(fragments)) {
                                Object fragmentObj = fragments.get(0);
                                if (fragmentObj instanceof VariableDeclarationFragment) {
                                    VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragmentObj;
                                    matchedFeildName = vdf.getName().getIdentifier();
                                    SwitchMetaStoreNew
                                            .addSwitchKeyField(matchedKey, packageName, typeName, vdf.getName().getIdentifier());
                                }
                            }
                        }
                    }
                }
            }
            // 如果存在Getter注解的属性，则将get+属性名称（首字母大写）方法存入属性映射：switchKeyMethods
            if (StringUtils.isNotBlank(matchedKey) && hasGetter && hasSwitcher && StringUtils.isNotBlank(matchedFeildName)) {
                String firstUpperCaseSwitchField = matchedFeildName.substring(0, 1).toUpperCase() + matchedFeildName.substring(1);
                SwitchMetaStoreNew.addSwitchKeyMethod(switchKey, packageName, typeName,
                        String.format(GET_METHOD_FMT, firstUpperCaseSwitchField));
            }
        }
        return matchedKey;
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

    private static String matchedKey(String switchKey){
        String matchedKey = null;
        Set<String> switchKeys = SwitchMetaStoreNew.switchKeySet();
        if (switchKeys.size() > 0) {
            for (String key : switchKeys) {
                if (StringUtils.isNotBlank(switchKey) && StringUtils.isNotBlank(key) && switchKey.equals(key)) {
                    matchedKey = key;
                }
            }
        }
        return matchedKey;
    }

    public static boolean isSwitchReturn(ReturnStatement node, String packageName, String typeName){
        return StringUtils.isNotBlank(matchedSwitchKey4ReturnStatement(node, packageName, typeName));
    }

    public static String matchedSwitchKey4ReturnStatement(ReturnStatement node, String packageName, String typeName){
        String matchedSwitchKey = null;
        // 加载与开关相关的方法信息
        String switchKey = SwitchMetaStoreNew.matchedSwitchKeyByField(node.getExpression(), packageName, typeName);
        if (switchKey != null || ((switchKey = matchedSwitchKey4ReturnStatementExpression(node.getExpression(), packageName, typeName)) != null)) {
            ASTNode parent = node.getParent();
            while (parent != null) {
                if (parent instanceof MethodDeclaration) {
                    MethodDeclaration methodDeclaration = (MethodDeclaration) parent;
                    String methodName = methodDeclaration.getName().getIdentifier();
                    SwitchMetaStoreNew.addSwitchKeyMethod(switchKey, packageName, typeName, methodName);
                    matchedSwitchKey = switchKey;
                    break;
                }
                parent = parent.getParent();
            }
        }
        return matchedSwitchKey;
    }

    private static String matchedSwitchKey4ReturnStatementExpression(Expression node, String packageName, String typeName){
        String matchedSwitchKey = null;
        if (node instanceof MethodInvocation) {
            MethodInvocation mi = (MethodInvocation) node;
            Expression expression = mi.getExpression();
            if (expression instanceof SimpleName) {
                SimpleName sn = (SimpleName) expression;
                // 处理场景1
                if (SWITCH_UTILS.equals(sn.getIdentifier())) {
                    List arguments = mi.arguments();
                    if (CollectionUtils.isNotEmpty(arguments)) {
                        for (Object arg : arguments) {
                            // 场景：return SwitchUtils.currentCityOpen(openBywaydegreeLog,order.getCityId())
                            matchedSwitchKey = SwitchMetaStoreNew.matchedSwitchKeyByField(arg, packageName, typeName);
                            // 场景：return SwitchUtils.currentCityOpen(BeanOrUtil.getOpenBywaydegreeLog(),order.getCityId())
                            if (org.apache.commons.lang.StringUtils.isBlank(matchedSwitchKey)) {
                                matchedSwitchKey = SwitchMetaStoreNew.matchedSwitchKeyByMethod(arg, packageName, typeName);
                            }
                            if (org.apache.commons.lang.StringUtils.isNotBlank(matchedSwitchKey)) {
                                break;
                            }
                        }
                    }
                }
                // 处理场景2：return switches.opened(1)
                if (org.apache.commons.lang.StringUtils.isBlank(matchedSwitchKey)) {
                    if (OPENED.equals(mi.getName().getIdentifier())) {
                        matchedSwitchKey = SwitchMetaStoreNew
                                .matchedSwitchKeyByField(sn.getIdentifier(), packageName, typeName);
                    }
                }
                // 处理场景2：return getNormalSwitches().opened(1)
                if (org.apache.commons.lang.StringUtils.isBlank(matchedSwitchKey)) {
                    if (OPENED.equals(mi.getName().getIdentifier())) {
                        matchedSwitchKey = SwitchMetaStoreNew.matchedSwitchKeyByMethod(mi, packageName, typeName);
                    }
                }
            }
        }
        return matchedSwitchKey;
    }
}
