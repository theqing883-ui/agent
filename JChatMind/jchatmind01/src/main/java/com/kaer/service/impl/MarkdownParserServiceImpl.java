package com.kaer.service.impl;

import com.kaer.service.MarkdownParserService;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Block;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 解析服务实现类
 * 使用 Flexmark 库解析 Markdown 文档，提取章节结构
 */
@Service
@Slf4j
public class MarkdownParserServiceImpl implements MarkdownParserService {

    /**
     * Flexmark 解析器实例，用于解析 Markdown 文本
     */
    private final Parser parser;

    /**
     * 原始 Markdown 内容缓存，用于表格等节点的精确提取
     */
    private String originalMarkdownContent;

    /**
     * 构造函数，初始化 Flexmark 解析器
     */
    public MarkdownParserServiceImpl() {
        // 创建解析器配置选项
        MutableDataSet option = new MutableDataSet();
        // 构建解析器实例
        this.parser = Parser.builder(option).build();
    }

    /**
     * 解析 Markdown 输入流，提取章节列表
     *
     * @param inputStream Markdown 文件的输入流
     * @return 章节列表，每个章节包含标题和内容
     * @throws RuntimeException 当解析失败时抛出
     */
    @Override
    public List<MarkdownSection> parseMarkdown(InputStream inputStream) {
        try {
            // 将输入流读取为 UTF-8 字符串
            originalMarkdownContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            // 使用 Flexmark 解析器解析 Markdown 内容
            Document document = parser.parse(originalMarkdownContent);

            // 提取章节结构
            ArrayList<MarkdownSection> sections = new ArrayList<>();
            extractSection(document, sections);

            log.info("解析 Markdown 完成，共提取 {} 个章节", sections.size());
            return sections;
        } catch (IOException e) {
            log.error("解析 Markdown 失败", e);
            throw new RuntimeException("解析 Markdown 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从文档节点中提取章节结构
     *
     * @param document 解析后的文档节点
     * @param sections 用于存储提取的章节列表
     */
    private void extractSection(Document document, List<MarkdownSection> sections) {
        // 收集所有顶层节点
        ArrayList<Node> topLevelNodes = new ArrayList<>();
        Node child = document.getFirstChild();

        // Markdown 中的标题和正文内容在 document 中都是以节点形式存在的
        // 这里获取的是顶层节点，不只有标题对应的节点
        while (child != null) {
            topLevelNodes.add(child);
            child = child.getNext();
        }

        // 遍历顶层节点，找到所有标题并提取章节
        for (int i = 0; i < topLevelNodes.size(); i++) {
            Node node = topLevelNodes.get(i);
            // 判断是否为标题节点
            if (node instanceof Heading heading) {
                // 提取标题文本
                String title = extractHeadingText(heading);
                if (title == null || title.trim().isEmpty()) {
                    continue;
                }

                // 收集当前标题到下一个标题（任何级别）之间的所有内容
                StringBuilder contentBuilder = new StringBuilder();
                for (int j = i + 1; j < topLevelNodes.size(); j++) {
                    Node nextNode = topLevelNodes.get(j);
                    // 如果遇到任何标题，停止收集
                    if (nextNode instanceof Heading) {
                        break;
                    }

                    // 收集非标题节点的内容
                    String content = extractNodeContent(nextNode);
                    if (content != null && !content.trim().isEmpty()) {
                        if (!contentBuilder.isEmpty()) {
                            contentBuilder.append("\n");
                        }
                        contentBuilder.append(content);
                    }
                }
                String content = contentBuilder.toString().trim();
                sections.add(new MarkdownSection(title, content));
            }
        }
    }

    /**
     * 根据节点类型提取内容
     *
     * @param node 待处理的节点
     * @return 节点内容字符串
     */
    private String extractNodeContent(Node node) {
        if (node == null) {
            return null;
        }
        // 如果是表格节点，提取完整的 Markdown 表格内容
        if (node instanceof TableBlock) {
            return extractTableContent((TableBlock) node);
        }
        // 对于其他节点，提取纯文本内容
        return extractPlainText(node);
    }

    /**
     * 提取表格节点的完整 Markdown 内容
     *
     * @param tableNode 表格节点
     * @return 表格的 Markdown 文本
     */
    private String extractTableContent(Node tableNode) {
        // 如果没有原始内容缓存，回退到纯文本提取
        if (originalMarkdownContent == null) {
            return extractPlainText(tableNode);
        }
        try {
            // 获取表格节点在原始内容中的字符序列引用
            BasedSequence chars = tableNode.getChars();
            if (chars != null && !chars.isEmpty()) {
                // 获取起始和结束偏移量
                int startOffset = chars.getStartOffset();
                int endOffset = chars.getEndOffset();

                // 校验偏移量合法性
                if (startOffset >= 0 && endOffset <= originalMarkdownContent.length() && endOffset > startOffset) {
                    // 从原始内容中截取表格的 Markdown 文本
                    String tableMarkdown = originalMarkdownContent.substring(startOffset, endOffset);
                    return tableMarkdown.trim();
                }
            }
            // 如果无法从原始内容提取，尝试从节点本身提取
            return extractPlainText(tableNode);
        } catch (Exception e) {
            log.warn("提取表格 Markdown 失败，使用文本提取: {}", e.getMessage());
            return extractPlainText(tableNode);
        }
    }

    /**
     * 提取标题节点的文本内容
     *
     * @param heading 标题节点
     * @return 标题文本
     */
    private String extractHeadingText(Heading heading) {
        StringBuilder text = new StringBuilder();
        Node child = heading.getFirstChild();
        while (child != null) {
            String plainText = extractPlainText(child);
            if (plainText != null && !plainText.isEmpty()) {
                if (!text.isEmpty()) {
                    text.append(" ");
                }
                text.append(plainText);
            }
            child = child.getNext();
        }
        return text.toString().trim();
    }

    /**
     * 提取节点的纯文本内容
     *
     * @param node 待处理的节点
     * @return 纯文本内容
     */
    private String extractPlainText(Node node) {
        if (node == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        extractTextRecursive(node, text);
        return !text.isEmpty() ? text.toString() : null;
    }

    /**
     * 递归提取节点的纯文本内容
     *
     * @param node 当前正在处理的节点（可能是容器，也可能是具体的文字）
     * @param text 用于承载最终拼接结果的 StringBuilder
     */
    private void extractTextRecursive(Node node, StringBuilder text) {
        // 1. 安全检查：如果节点为空，直接返回
        if (node == null) {
            return;
        }

        // 2. 业务过滤：跳过标题节点
        // 逻辑：因为在外部的 extractSections 方法中已经单独提取并处理了标题，
        // 这里是为了提取“标题下的正文”，所以要排除 Heading 节点，防止内容重复。
        if (node instanceof Heading) {
            return;
        }

        // 3. 判断节点类型：是容器（Container）还是叶子（Leaf）？
        Node child = node.getFirstChild();

        if (child != null) {
            /* --- 情况 A：当前是一个容器节点（如 Paragraph, BulletList, StrongEmphasis） --- */
            boolean isFirstChild = true;

            // 遍历该容器下的所有子节点
            while (child != null) {
                // A.1 智能处理节点之间的分隔符
                if (!isFirstChild && !text.isEmpty()) {
                    // 如果当前子节点是“块级元素”（如段落、列表项、引用块）
                    if (child instanceof Block) {
                        // 如果结尾还没换行，就补一个换行符，保证文本段落分明
                        if (!text.toString().endsWith("\n")) {
                            text.append("\n");
                        }
                    } else {
                        // 如果是“行内元素”（如加粗、斜体、链接），补一个空格防止单词黏连
                        text.append(" ");
                    }
                }

                // A.2 递归调用：继续向下挖掘子节点的内容
                extractTextRecursive(child, text);

                // A.3 移动到下一个兄弟节点
                child = child.getNext();
                isFirstChild = false;
            }
        } else {
            /* --- 情况 B：当前是一个叶子节点（如 Text, Code, HardLineBreak） --- */
            try {
                // B.1 从 Flexmark 的序列中获取该节点对应的原始文本
                // getChars() 返回的是该节点在原始 Markdown 字符串中的位置引用
                BasedSequence chars = node.getChars();

                if (chars != null && !chars.isEmpty()) {
                    String nodeText = chars.toString().trim();

                    // B.2 只有当文字非空时才进行追加
                    if (!nodeText.isEmpty()) {
                        // 检查前缀：如果当前缓冲区已有内容且结尾不是换行，追加空格作为过渡
                        if (!text.isEmpty() && !text.toString().endsWith("\n")) {
                            text.append(" ");
                        }
                        text.append(nodeText);
                    }
                }
            } catch (Exception e) {
                // 容错处理：如果提取某个特殊节点失败，忽略它，保证整个文档解析不中断
                log.warn("提取节点文本失败: {}", e.getMessage());
            }
        }
    }
}