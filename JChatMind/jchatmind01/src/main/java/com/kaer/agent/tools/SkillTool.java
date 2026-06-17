package com.kaer.agent.tools;

import com.kaer.agent.AgentContextHolder;
import com.kaer.agent.skill.SkillManager;
import com.kaer.context.memory.TokenAwareChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 技能加载工具 —— Agent 运行时按需加载 Skill 指令到对话上下文。
 *
 * <p>此工具为 {@link ToolType#FIXED} 类型，所有 Agent 默认自动拥有。
 * Agent 通过 System prompt 中的可用技能列表发现技能，
 * 然后调用 {@code loadSkill(skillName)} 将技能的完整指令注入当前会话。
 *
 * <p>注入方式：将 Skill 正文包装为 SystemMessage 添加到
 * {@link TokenAwareChatMemory}，后续 think() 循环中自动进入上下文窗口。
 *
 * <p>幂等性：同一 Skill 在同一会话中不会重复加载，
 * 通过检查 SystemMessage 中的 marker 前缀实现。
 */
@Slf4j
@Component
public class SkillTool implements Tool {

    private static final String TOOL_NAME = "loadSkill";
    private static final String SKILL_MARKER_PREFIX = "[已加载技能: ";

    private final SkillManager skillManager;
    private final TokenAwareChatMemory chatMemory;

    public SkillTool(SkillManager skillManager, TokenAwareChatMemory chatMemory) {
        this.skillManager = skillManager;
        this.chatMemory = chatMemory;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "按需加载技能指令到对话上下文。可用技能: " + formatSkillCatalog();
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    /**
     * 格式化可用技能目录为工具描述的一部分，
     * 便于 REST API 和前端展示当前可用技能。
     */
    private String formatSkillCatalog() {
        java.util.List<com.kaer.agent.skill.SkillMeta> skills = skillManager.getAllSkillMetas();
        if (skills.isEmpty()) {
            return "(暂无可用技能)";
        }
        return skills.stream()
                .map(m -> m.getName() + ": " + m.getDescription())
                .collect(Collectors.joining("; "));
    }

    /**
     * 按名称加载技能，将其指令注入当前会话上下文。
     *
     * @param skillName 要加载的技能名称（通过 System prompt 或 SkillTool 描述查看可用列表）
     * @return 加载结果描述
     */
    @org.springframework.ai.tool.annotation.Tool(
            name = "loadSkill",
            description = "按名称加载一个技能的完整指令到当前对话上下文中。"
                    + "加载后，该技能的专项指令将指导后续的所有响应。"
                    + "当你需要某项不在当前上下文中的专项能力时调用此工具。"
                    + "可用技能列表已在 System prompt 中列出。"
    )
    public String loadSkill(
            @ToolParam(description = "要加载的技能名称，必须与 System prompt 中列出的技能名称完全一致")
            String skillName) {

        // 1. 参数校验
        if (skillName == null || skillName.isBlank()) {
            return "错误：技能名称不能为空。";
        }

        // 2. 技能存在性校验
        if (!skillManager.hasSkill(skillName)) {
            return "错误：技能 '" + skillName + "' 不存在。可用技能: " + formatSkillCatalog();
        }

        // 3. 获取当前会话 ID
        String sessionId = AgentContextHolder.getSessionId();
        if (sessionId == null) {
            log.warn("SkillTool.loadSkill 在 Agent 上下文之外被调用");
            return "错误：当前没有活跃的 Agent 会话，无法加载技能。";
        }

        // 4. 防重复加载
        if (isSkillAlreadyLoaded(sessionId, skillName)) {
            log.debug("Skill '{}' 已在会话 {} 中加载，跳过", skillName, sessionId);
            return "技能 '" + skillName + "' 已在当前对话上下文中加载，无需重复加载。";
        }

        // 5. 加载正文
        String skillContent;
        try {
            skillContent = skillManager.loadSkill(skillName);
        } catch (IllegalArgumentException e) {
            return "错误：" + e.getMessage();
        }

        // 6. 注入为 SystemMessage
        String systemMessageContent = SKILL_MARKER_PREFIX + skillName + "]\n" + skillContent;
        chatMemory.add(sessionId, new SystemMessage(systemMessageContent));

        log.info("Skill '{}' 已加载到会话 {}（{} 字符）", skillName, sessionId, skillContent.length());
        return "技能 '" + skillName + "' 已成功加载。该技能的专项指令现在已激活，将在后续对话中生效。"
                + "（指令内容长度: " + skillContent.length() + " 字符）";
    }

    /**
     * 检查指定 Skill 是否已在会话中加载过。
     * 通过扫描 SystemMessage 中的 marker 前缀来判断。
     */
    private boolean isSkillAlreadyLoaded(String sessionId, String skillName) {
        String marker = SKILL_MARKER_PREFIX + skillName + "]";
        return chatMemory.getAll(sessionId).stream()
                .anyMatch(msg -> msg instanceof SystemMessage
                        && msg.getText() != null
                        && msg.getText().startsWith(marker));
    }
}
