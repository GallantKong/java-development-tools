package org.gallant.jdt.core.visitor.v2;

import com.google.common.collect.Lists;
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
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IVariableBinding;

/**
 * @author kongyong
 * @date 2019/10/25
 */
@Slf4j
public class SwitchMetaStore {

    private static Map<String, Set<IVariableBinding>> switchKeyFields = new ConcurrentHashMap<>(8);
    private static Map<String, Set<String>> obtainSwitchFieldMethods = new ConcurrentHashMap<>(8);
    private static Map<String, String> srcCodeCache = new HashMap<>(128);
    private static Map<String, String> switchKeyProjectPaths = new HashMap<>(128);
    private static final String SRC_DIR = "\\src\\main\\java";
    private static Map<String, CompilationUnit> packageAndCompilationUnit = new ConcurrentHashMap<>(8);

    private SwitchMetaStore() {
    }

    public static void initSwitchKeyFields(String dir, String[] switchKeys) throws IOException {
        if (switchKeys != null) {
            for (String switchKey : switchKeys) {
                switchKeyFields.putIfAbsent(switchKey, new HashSet<>());
                obtainSwitchFieldMethods.putIfAbsent(switchKey, new HashSet<>());
            }
        }
        if (StringUtils.isNotBlank(dir)) {
            Collection<File> files = FileUtils.listFiles(new File(dir + SRC_DIR),
                    FileFilterUtils.suffixFileFilter("java"), FileFilterUtils.trueFileFilter());
            for (File file : files) {
                String src = FileUtils.readFileToString(file, "utf8");
                if (StringUtils.isBlank(src)) {
                    log.error("源文件为空");
                    continue;
                }
                srcCodeCache.putIfAbsent(file.getAbsolutePath(), src);
            }
            initPackageAndCompilationUnit();
        }
    }

    public static void addSwitchKeyFieldIfAbsent(String key, IVariableBinding field){
        Set<IVariableBinding> set = switchKeyFields.get(key);
        for (IVariableBinding iVariableBinding : set) {
            if (iVariableBinding.getKey().equals(field.getKey())) {
                return;
            }
        }
        switchKeyFields.get(key).add(field);
    }

    public static void addObtainSwitchFieldMethods(String key, String name) {
        obtainSwitchFieldMethods.get(key).add(name);
    }

    public static void clear(){
        switchKeyFields.clear();
        obtainSwitchFieldMethods.clear();
        srcCodeCache.clear();
        switchKeyProjectPaths.clear();
        packageAndCompilationUnit.clear();
    }

    public static void println(){
        log.info("switchKeyFields={}\nswitchFieldKeys={}\nsrcCodeCache.keySet={}\nswitchKeyProjectPaths={}\n", switchKeyFields, obtainSwitchFieldMethods, srcCodeCache.keySet(), switchKeyProjectPaths);
    }

    public static Map<String, String> getSrcCodeCache() {
        return new HashMap<>(srcCodeCache);
    }

    public static void addSwitchKeyProjectPathIfNeed(String path) {
        addSwitchKeyProjectPathIfNeed(path, switchKeyFields);
    }

    private static void addSwitchKeyProjectPathIfNeed(String path,
            Map<String, Set<IVariableBinding>> keyFieldsOrMethods) {
        if (keyFieldsOrMethods.size() > 0) {
            for (Entry<String, Set<IVariableBinding>> entry : keyFieldsOrMethods.entrySet()) {
                if (CollectionUtils.isNotEmpty(entry.getValue())) {
                    switchKeyProjectPaths.put(entry.getKey(), path);
                }
            }
        }
    }

    public static Map<String, String> getSwitchKeyProjectPaths() {
        return new HashMap<>(switchKeyProjectPaths);
    }

    public static boolean isSwitchKey(String switchKey){
        boolean matchedKey = false;
        Set<String> switchKeys = switchKeyFields.keySet();
        if (switchKeys.size() > 0) {
            for (String key : switchKeys) {
                if (StringUtils.isNotBlank(switchKey) && StringUtils.isNotBlank(key) && switchKey.equals(key)) {
                    matchedKey = true;
                    break;
                }
            }
        }
        return matchedKey;
    }

    public static String matchSwitchKeyByFieldBindingKey(String fieldBindingKey) {
        Set<Entry<String, Set<IVariableBinding>>> entries = switchKeyFields.entrySet();
        for (Entry<String, Set<IVariableBinding>> entry : entries) {
            String switchKey = entry.getKey();
            Set<IVariableBinding> fieldBindings = entry.getValue();
            for (IVariableBinding fieldBinding : fieldBindings) {
                if (fieldBinding.getKey().equals(fieldBindingKey)) {
                    return switchKey;
                }
            }
        }
        return null;
    }

    public static String matchSwitchKeyByMethodBindingKey(String methodBindingKey) {
        Set<Entry<String, Set<String>>> entries = obtainSwitchFieldMethods.entrySet();
        for (Entry<String, Set<String>> entry : entries) {
            if (entry.getValue().contains(methodBindingKey)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static List<Integer> currentSize(){
        List<Integer> currentSizes = Lists.newArrayList(0, 0);
        switchKeyFields.forEach((k, v) -> currentSizes.set(0, currentSizes.get(0) + v.size()));
        obtainSwitchFieldMethods.forEach((k, v) -> currentSizes.set(1, currentSizes.get(1) + v.size()));
        return currentSizes;
    }

    public static String matchStartWithSwitchKeyByField(String fieldBindingKey) {
        if (StringUtils.isBlank(fieldBindingKey)) {
            return null;
        }
        Set<Entry<String, Set<IVariableBinding>>> entries = switchKeyFields.entrySet();
        for (Entry<String, Set<IVariableBinding>> entry : entries) {
            String switchKey = entry.getKey();
            Set<IVariableBinding> fieldBindings = entry.getValue();
            for (IVariableBinding fieldBinding : fieldBindings) {
                if (fieldBinding.getKey().startsWith(fieldBindingKey)) {
                    return switchKey;
                }
            }
        }
        Set<Entry<String, Set<String>>> entrySet = obtainSwitchFieldMethods.entrySet();
        for (Entry<String, Set<String>> entry : entrySet) {
            String switchKey = entry.getKey();
            Set<String> fieldBindings = entry.getValue();
            for (String methodKey : fieldBindings) {
                if (methodKey.startsWith(fieldBindingKey)) {
                    return switchKey;
                }
            }
        }
        return null;
    }

    private static Map<String, StringBuilder> getPackageAndSrcCode(){
        Map<String, String> srcCodeCache = SwitchMetaStore.getSrcCodeCache();
        Map<String, StringBuilder> packageAndSrcCode = new HashMap<>(srcCodeCache.size());
        for (Entry<String, String> entry : srcCodeCache.entrySet()) {
            String srcPath = entry.getKey();
            String src = entry.getValue();
            int lastFileSeparator = srcPath.lastIndexOf(File.separator);
            String srcDir = srcPath.substring(0, lastFileSeparator);
            packageAndSrcCode.putIfAbsent(srcDir, new StringBuilder());
            packageAndSrcCode.get(srcDir).append(src).append("\n");
        }
        return packageAndSrcCode;
    }

    private static void initPackageAndCompilationUnit() throws IOException {
        if (MapUtils.isEmpty(packageAndCompilationUnit)) {
            Map<String, StringBuilder> packageAndSrcCode = getPackageAndSrcCode();
            if (MapUtils.isNotEmpty(packageAndSrcCode)) {
                List<CompilationUnit> compilationUnits = Lists.newLinkedList();
                for (Entry<String, StringBuilder> entry : packageAndSrcCode.entrySet()) {
                    String src = entry.getValue().toString();
                    // creation of DOM/AST from a ICompilationUnit
                    compilationUnits.add(AstBuilder.createAst(src));
                    // 1. 加载开关关联的属性信息
                }
                packageAndCompilationUnit = compilationUnits.stream().collect(Collectors
                        .toMap(compilationUnit -> compilationUnit.getPackage().getName().getFullyQualifiedName(), compilationUnit -> compilationUnit));
            }
        }
    }

    static Map<String, CompilationUnit> getPackageAndCompilationUnit() {
        return packageAndCompilationUnit;
    }
}
