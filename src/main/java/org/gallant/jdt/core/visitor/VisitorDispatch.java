package org.gallant.jdt.core.visitor;

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
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

/**
 * @author kongyong
 * @date 2019/11/4
 */
@Slf4j
public class VisitorDispatch {

    private static final String NEW_LINE = "\n";
    private static final String SPACE_NEW_LINE_TAB_ENTER = "\\s";

    private static void visitField(String src) {
        // creation of DOM/AST from a ICompilationUnit
        ASTParser astParser = ASTParser.newParser(AST.JLS11);
        Map<String, String> compilerOptions = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, compilerOptions);
        astParser.setCompilerOptions(compilerOptions);
        astParser.setResolveBindings(true);
        astParser.setBindingsRecovery(true);
        astParser.setStatementsRecovery(true);
        astParser.setSource(src.toCharArray());
        CompilationUnit astRoot = (CompilationUnit) astParser.createAST(null);
        // 1. 加载开关关联的属性信息
        FieldDeclarationVisitor fieldDeclarationVisitor = new FieldDeclarationVisitor();
        astRoot.accept(fieldDeclarationVisitor);
    }

    private static void visitMethod(String src) {
        // creation of DOM/AST from a ICompilationUnit
        ASTParser astParser = ASTParser.newParser(AST.JLS11);
        Map<String, String> compilerOptions = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, compilerOptions);
        astParser.setCompilerOptions(compilerOptions);
        astParser.setResolveBindings(true);
        astParser.setBindingsRecovery(true);
        astParser.setStatementsRecovery(true);
        astParser.setSource(src.toCharArray());
        CompilationUnit astRoot = (CompilationUnit) astParser.createAST(null);
        // 2. 加载开关关联的方法信息
        ReturnStatementVisitor returnStatementVisitor = new ReturnStatementVisitor();
        astRoot.accept(returnStatementVisitor);
    }

    private static VisitorDispatchDTO switchClean(String src)
            throws BadLocationException {
        VisitorDispatchDTO visitorDispatchDTO = new VisitorDispatchDTO();
        String newSrc = null;
        Document document= new Document(src);

        // creation of DOM/AST from a ICompilationUnit
        ASTParser astParser = ASTParser.newParser(AST.JLS11);
        Map<String, String> compilerOptions = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, compilerOptions);
        astParser.setCompilerOptions(compilerOptions);
        astParser.setResolveBindings(true);
        astParser.setBindingsRecovery(true);
        astParser.setStatementsRecovery(true);
        astParser.setSource(src.toCharArray());
        CompilationUnit astRoot = (CompilationUnit) astParser.createAST(null);
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
        TextEdit edits = astRewrite.rewriteAST(document, compilerOptions);

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

    public static void scan(String dir, String[] keys) throws IOException {
        SwitchMetaStoreNew.initSwitchKeyFields(dir, keys);
        Map<String, String> srcCodeCache = SwitchMetaStoreNew.getSrcCodeCache();
        Set<Entry<String, String>> entrySet = srcCodeCache.entrySet();
        // 1. 采集开关相关数据
        // 采集field、getField方法信息
        for (Entry<String, String> entry : entrySet) {
            log.info("scan field 4 file : {}", entry.getKey());
            visitField(entry.getValue());
        }
        // 开关属性直接访问，或通过get方法访问，两种方式的return收集
        // 采集return开关属性的方法信息：
        // return SwitchUtils.currentCityOpen(openBywaydegreeLog,order.getCityId())
        // return SwitchUtils.currentCityOpen(BeanOrUtil.getOpenBywaydegreeLog(),order.getCityId())
        // return normalSwitches.opened(1)
        // return BeanOrUtil.getNormalSwitches().opened(1)
        for (Entry<String, String> entry : entrySet) {
            log.info("first scan method 4 file : {}", entry.getKey());
            visitMethod(entry.getValue());
        }
        // 在收集了直接访问以及get方法访问的信息之后，收集通过上两种访问方式进行间接访问方式的收集
        // 采集间接调用开关方法的方法信息：
        // 1.5 return SwitchesUtils.isOpen(BeanOrUtil.getMySwitchesAttribute(), city_id)
        // 1.6 return UtilOrBean.isOpenSwitchesNewAngle(1)
        for (Entry<String, String> entry : entrySet) {
            log.info("second scan method 4 file : {}", entry.getKey());
            visitMethod(entry.getValue());
        }
        SwitchMetaStoreNew.addSwitchKeyProjectPathIfNeed(dir);
        SwitchMetaStoreNew.println();
    }

    public static List<DispatchSwitchCleanLog> clean() throws BadLocationException, IOException {
        List<DispatchSwitchCleanLog> allDispatchSwitchCleanLogs = Lists.newLinkedList();
        Map<String, String> srcCodeCache = SwitchMetaStoreNew.getSrcCodeCache();
        Set<Entry<String, String>> entrySet = srcCodeCache.entrySet();
        // 2. 清理开关
        for (Entry<String, String> entry : entrySet) {
            log.info("clean 4 file : {}", entry.getKey());
            VisitorDispatchDTO visitorDispatchDTO = switchClean(entry.getValue());
            String newSrc = visitorDispatchDTO.getJavaCode();
            if (newSrc != null) {
                log.info("modify file : {}", entry.getKey());
                FileUtils.write(new File(entry.getKey()), newSrc, "utf8");
                String fileName = entry.getKey();
                Set<DispatchSwitchCleanLog> dispatchSwitchCleanLogs = visitorDispatchDTO.getDispatchSwitchCleanLogs();
                if (CollectionUtils.isNotEmpty(dispatchSwitchCleanLogs)) {
                    dispatchSwitchCleanLogs.forEach(dispatchSwitchCleanLogDTO -> dispatchSwitchCleanLogDTO.setFilePath(fileName));
                    allDispatchSwitchCleanLogs.addAll(dispatchSwitchCleanLogs);
                }
            }
        }
        SwitchMetaStoreNew.clear();
        return allDispatchSwitchCleanLogs;
    }
}
