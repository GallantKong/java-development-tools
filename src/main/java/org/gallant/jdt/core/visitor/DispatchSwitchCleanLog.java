package org.gallant.jdt.core.visitor;

import java.util.Objects;
import lombok.Data;

/**
 * @author kongyong
 * @date 2019/11/14
 */
@Data
public class DispatchSwitchCleanLog {

    private String switchKey;
    private String filePath;
    private Long dispatchGitlabProjectId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DispatchSwitchCleanLog that = (DispatchSwitchCleanLog) o;
        return Objects.equals(switchKey, that.switchKey) && Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {

        return Objects.hash(switchKey, filePath);
    }
}
