package org.gallant.jdt.core.visitor.v2;

import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.gallant.jdt.core.visitor.DispatchSwitchCleanLog;
import org.gallant.jdt.core.visitor.VisitorDispatchDTO;

/**
 * @author kongyong
 * @date 2019/11/4
 */
@Slf4j
public class VisitorDispatch {

    private static final String NEW_LINE = "\n";
    private static final String SPACE_NEW_LINE_TAB_ENTER = "\\s";
    private static int maxScanRecursiveTimes = 5;

    private static void switchCollect(int maxRecursiveTimes) {
        for (int i=0; i < maxRecursiveTimes; i++) {
            log.info("current recursive time:{}", i);
            List<Integer> beforeSizes = SwitchMetaStore.currentSize();
            VisitorDispatch.switchCollect();
            List<Integer> afterSizes = SwitchMetaStore.currentSize();
            if (beforeSizes.get(0).equals(afterSizes.get(0)) && beforeSizes.get(1).equals(afterSizes.get(1))) {
                log.info("break, current recursive time:{}", i);
                break;
            }
        }
    }

    private static void switchCollect() {
        Map<String, CompilationUnit> packageAndCompilationUnit = SwitchMetaStore.getPackageAndCompilationUnit();
        if (MapUtils.isNotEmpty(packageAndCompilationUnit)) {
            SwitchCollectVisitor switchCollectVisitor = new SwitchCollectVisitor(packageAndCompilationUnit);
            for (CompilationUnit astRoot : packageAndCompilationUnit.values()) {
                astRoot.accept(switchCollectVisitor);
            }
        }
    }

    public static void scan(String dir, String[] keys) throws IOException {
        SwitchMetaStore.initSwitchKeyFields(dir, keys);
        VisitorDispatch.switchCollect(maxScanRecursiveTimes);
        SwitchMetaStore.addSwitchKeyProjectPathIfNeed(dir);
        SwitchMetaStore.println();
    }

    public static List<DispatchSwitchCleanLog> clean(boolean apply2SourceFile) throws BadLocationException, IOException {
        List<DispatchSwitchCleanLog> allDispatchSwitchCleanLogs = Lists.newLinkedList();
        Map<String, String> srcCodeCache = SwitchMetaStore.getSrcCodeCache();
        Set<Entry<String, String>> entrySet = srcCodeCache.entrySet();
        // 2. 清理开关
        for (Entry<String, String> entry : entrySet) {
            log.info("clean 4 file : {}", entry.getKey());
            VisitorDispatchDTO visitorDispatchDTO = switchClean(entry.getValue());
            String newSrc = visitorDispatchDTO.getJavaCode();
            if (newSrc != null) {
                log.info("modify file : {}", entry.getKey());
                if (apply2SourceFile) {
                    FileUtils.write(new File(entry.getKey()), newSrc, "utf8");
                    String fileName = entry.getKey();
                    Set<DispatchSwitchCleanLog> dispatchSwitchCleanLogs = visitorDispatchDTO.getDispatchSwitchCleanLogs();
                    if (CollectionUtils.isNotEmpty(dispatchSwitchCleanLogs)) {
                        dispatchSwitchCleanLogs.forEach(
                                dispatchSwitchCleanLogDTO -> dispatchSwitchCleanLogDTO.setFilePath(fileName));
                        allDispatchSwitchCleanLogs.addAll(dispatchSwitchCleanLogs);
                    }
                }
            }
        }
        SwitchMetaStore.clear();
        return allDispatchSwitchCleanLogs;
    }

    private static VisitorDispatchDTO switchClean(String src)
            throws BadLocationException, IOException {
        VisitorDispatchDTO visitorDispatchDTO = new VisitorDispatchDTO();
        String newSrc = null;
        Document document= new Document(src);

        // creation of DOM/AST from a ICompilationUnit
        CompilationUnit astRoot = AstBuilder.createAst(src);
        List commentList = astRoot.getCommentList();
        Map<Comment, String> commentStringMap = new HashMap<>(commentList.size());
        for (Object commentObj : commentList) {
            Comment comment = (Comment) commentObj;
            String commentStr = src.substring(comment.getStartPosition(), comment.getStartPosition() + comment.getLength());
            commentStringMap.putIfAbsent(comment, commentStr);
        }

        // creation of ASTRewrite
        ASTRewrite astRewrite = ASTRewrite.create(astRoot.getAST());

        SwitchesCleanerVisitor switchesCleanerVisitor = new SwitchesCleanerVisitor(astRewrite, astRoot);
        // 3.清理开关
        astRoot.accept(switchesCleanerVisitor);

        // computation of the text edits
        TextEdit edits = astRewrite.rewriteAST(document, AstBuilder.COMPILER_OPTIONS);

        // computation of the new source code
        edits.apply(document);

        // update of the compilation unit
        if (!src.equals(document.get())) {
            newSrc = document.get();
            Map<Statement, Comment> statementCommentMap = switchesCleanerVisitor.getStatementCommentMap();
            if (MapUtils.isNotEmpty(statementCommentMap)) {
                String[] lines = newSrc.split(NEW_LINE);
                StringBuilder sb = new StringBuilder();
                String lastLine = null;
                for (String line : lines) {
                    if (line != null) {
                        String lineWithoutSpace = line.replaceAll(SPACE_NEW_LINE_TAB_ENTER, "");
                        for (Entry<Statement, Comment> entry : statementCommentMap.entrySet()) {
                            String statementWithoutSpace = entry.getKey().toString().split(NEW_LINE)[0].replaceAll(SPACE_NEW_LINE_TAB_ENTER, "");
                            // 处理场景
                            // if(!idcSyncTask.getOtherCityList().contains(oldCityId)
                            // if(!idcSyncTask.getOtherCityList().contains(oldCityId)&&!idcSyncTask.getOtherCityList().contains(newCityId)){
                            // 长度大于0会丢失这类注解：注解的下一行是空白行
                            if (lineWithoutSpace.length() > 0) {
                                if (lineWithoutSpace.equals(statementWithoutSpace) || statementWithoutSpace.startsWith(lineWithoutSpace)) {
                                    String comment = commentStringMap.get(entry.getValue());
                                    if (lastLine == null || !lastLine
                                            .replaceAll(SPACE_NEW_LINE_TAB_ENTER, "").equals(comment
                                                    .replaceAll(SPACE_NEW_LINE_TAB_ENTER, ""))) {
                                        String indent = line.substring(0,
                                                line.indexOf(lineWithoutSpace.substring(0, 1)));
                                        sb.append(indent).append(comment).append(NEW_LINE);
                                    }
                                }
                            }
                        }
                        lastLine = line;
                        sb.append(line).append(NEW_LINE);
                    }
                }
                newSrc = sb.toString();
            }
        }
        visitorDispatchDTO.setJavaCode(newSrc);
        visitorDispatchDTO.setDispatchSwitchCleanLogs(switchesCleanerVisitor.getDispatchSwitchCleanLogs());
        return visitorDispatchDTO;
    }
}
