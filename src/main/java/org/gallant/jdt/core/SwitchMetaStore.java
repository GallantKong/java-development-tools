package org.gallant.jdt.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author kongyong
 * @date 2019/10/25
 */
class SwitchMetaStore {

    private static Map<String, String> switchKeyFields = new ConcurrentHashMap<>(8);
    private static Map<String, String> switchFieldKeys = new ConcurrentHashMap<>(8);
    private static Map<String, String> switchKeyMethods = new ConcurrentHashMap<>(8);

    private SwitchMetaStore() {
    }

    static Map<String, String> getSwitchKeyFields() {
        return switchKeyFields;
    }

    static Map<String, String> getSwitchFieldKeys() {
        return switchFieldKeys;
    }

    static Map<String, String> getSwitchKeyMethods() {
        return switchKeyMethods;
    }

    static void clear(){
        switchKeyFields = new ConcurrentHashMap<>(8);
        switchFieldKeys = new ConcurrentHashMap<>(8);
        switchKeyMethods = new ConcurrentHashMap<>(8);
    }
}
