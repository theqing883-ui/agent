package com.kaer.agent.skill;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 技能元数据 —— 从 Skill markdown 文件的 YAML frontmatter 中提取。
 * <p>
 * 在启动时由 {@link SkillManager} 扫描并填充，
 * 用于 System prompt 中展业可用技能列表，
 * 以及在运行时供 Agent 通过 loadSkill 工具按需加载。
 */
@Data
@AllArgsConstructor
public class SkillMeta {
    /** 技能唯一标识（对应 frontmatter 中 name 字段） */
    private String name;
    /** 技能描述（对应 frontmatter 中 description 字段） */
    private String description;
}
