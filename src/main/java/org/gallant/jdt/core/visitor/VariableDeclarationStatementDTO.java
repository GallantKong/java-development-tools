package org.gallant.jdt.core.visitor;

import lombok.Builder;
import lombok.Data;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * @author kongyong
 * @date 2020/1/8
 */
@Data
@Builder
public class VariableDeclarationStatementDTO {

    private VariableDeclarationStatement variableDeclarationStatement;
    private String switchKey;
    private boolean isReverseExpression;

}
