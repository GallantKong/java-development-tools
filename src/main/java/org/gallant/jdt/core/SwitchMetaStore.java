package org.gallant.jdt.core;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kongyong
 * @date 2019/10/25
 */
@Slf4j
class SwitchMetaStore {

    private static Map<String, String> switchKeyFields = new ConcurrentHashMap<>(8);
    private static Map<String, String> switchFieldKeys = new ConcurrentHashMap<>(8);
    private static Map<String, String> switchKeyMethods = new ConcurrentHashMap<>(8);

    private SwitchMetaStore() {
    }

    static void initSwitchKeyFields(String[] switchKeys){
        if (switchKeys != null) {
            for (String switchKey : switchKeys) {
                switchKeyFields.put(switchKey, "-1");
            }
        }
    }

    static void addSwitchKeyField(String key, String fieldName){
        switchKeyFields.put(key, fieldName);
        switchFieldKeys.put(fieldName, key);
    }

    static void addSwitchKeyMethodByFieldName(String fieldName, String methodName) {
        switchKeyMethods.put(switchFieldKeys.get(fieldName), methodName);
    }

    static Set<String> switchKeySet(){
        return switchKeyFields.keySet();
    }

    static Collection<String> switchFields(){
        return switchKeyFields.values();
    }

    static Collection<String> switchMethods(){
        return switchKeyMethods.values();
    }

    static void clear(){
        switchKeyFields.clear();
        switchFieldKeys.clear();
        switchKeyMethods.clear();
    }

    static void println(){
        log.info("switchKeyFields={}\nswitchFieldKeys={}\nswitchKeyMethods={}\n", switchKeyFields, switchFieldKeys, switchKeyMethods);
    }

}
