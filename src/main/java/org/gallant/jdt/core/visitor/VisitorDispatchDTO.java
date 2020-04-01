package org.gallant.jdt.core.visitor;

import java.util.Set;
import lombok.Data;

/**
 * @author kongyong
 * @date 2019/11/14
 */
@Data
public class VisitorDispatchDTO {

    private String javaCode;
    private Set<DispatchSwitchCleanLog> dispatchSwitchCleanLogs;

}
