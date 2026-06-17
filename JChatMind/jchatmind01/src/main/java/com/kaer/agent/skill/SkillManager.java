package com.kaer.agent.skill;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 技能管理器 —— 启动时扫描 skills 资源目录，维护技能注册表。
 *
 * <p>职责：
 * <ul>
 *   <li>启动阶段：扫描 {@code classpath:skills/*.md}，解析 YAML frontmatter</li>
 *   <li>运行时：提供技能列表（供 System prompt 格式化）和按需加载技能正文</li>
 * </ul>
 *
 * <p>线程安全：使用 {@link ConcurrentHashMap} 存储，{@code @PostConstruct}
 * 扫描在单线程中完成，后续读写操作线程安全。
 */
@Slf4j
@Component
public class SkillManager {

    /** name → SkillMeta */
    private final Map<String, SkillMeta> skillMetaMap = new ConcurrentHashMap<>();
    /** name → 完整正文内容（frontmatter 之后的部分） */
    private final Map<String, String> skillContentMap = new ConcurrentHashMap<>();

    private static final String DELIMITER = "---";

    @PostConstruct
    public void init() {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath:skills/*.md");
            if (resources.length == 0) {
                log.warn("未在 classpath:skills/ 下找到任何 skill 文件");
                return;
            }
            for (Resource resource : resources) {
                try {
                    processSkillFile(resource);
                } catch (Exception e) {
                    log.warn("解析 skill 文件失败: {} — {}", resource.getFilename(), e.getMessage());
                }
            }
            log.info("SkillManager 初始化完成，共注册 {} 个技能: {}",
                    skillMetaMap.size(), skillMetaMap.keySet());
        } catch (Exception e) {
            log.error("扫描 skill 资源目录失败", e);
        }
    }

    /**
     * 处理单个 skill markdown 文件，提取 frontmatter 和正文。
     */
    private void processSkillFile(Resource resource) throws Exception {
        String filename = resource.getFilename();
        if (filename == null) return;

        // 读取文件全文
        String fullContent;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            fullContent = reader.lines().collect(Collectors.joining("\n"));
        }

        // 校验 frontmatter 起始标记
        if (!fullContent.startsWith(DELIMITER)) {
            log.warn("Skill 文件 {} 不以 '---' 开头，跳过", filename);
            return;
        }

        // 查找 frontmatter 结束标记
        int secondDelim = fullContent.indexOf(DELIMITER, 3);
        if (secondDelim == -1) {
            log.warn("Skill 文件 {} 缺少闭合的 '---'，跳过", filename);
            return;
        }

        // 分离 YAML 块和正文
        String yamlBlock = fullContent.substring(3, secondDelim).trim();
        String body = fullContent.substring(secondDelim + 3).trim();

        // 简单 key: value 解析（避免引入 YAML 解析库依赖）
        String name = null;
        String description = null;
        for (String line : yamlBlock.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int colonIdx = line.indexOf(':');
            if (colonIdx == -1) continue;
            String key = line.substring(0, colonIdx).trim();
            String value = line.substring(colonIdx + 1).trim();
            switch (key) {
                case "name" -> name = value;
                case "description" -> description = value;
            }
        }

        // 校验必填字段
        if (name == null || name.isEmpty()) {
            log.warn("Skill 文件 {} 缺少 'name' 字段，跳过", filename);
            return;
        }
        if (description == null || description.isEmpty()) {
            log.warn("Skill 文件 {} 缺少 'description' 字段，跳过", filename);
            return;
        }

        // 注册（重复名称：后者覆盖）
        if (skillMetaMap.containsKey(name)) {
            log.warn("重复的 skill 名称 '{}'（文件 {}），将覆盖旧值", name, filename);
        }

        skillMetaMap.put(name, new SkillMeta(name, description));
        skillContentMap.put(name, body);
        log.debug("已注册 skill: name='{}', desc='{}', contentLength={}", name, description, body.length());
    }

    /**
     * 返回所有已注册技能的元数据列表（不可变副本）。
     */
    public List<SkillMeta> getAllSkillMetas() {
        return List.copyOf(skillMetaMap.values());
    }

    /**
     * 按名称加载技能正文内容。
     *
     * @param name 技能名称
     * @return 技能正文（frontmatter 之后的内容）
     * @throws IllegalArgumentException 如果技能不存在
     */
    public String loadSkill(String name) {
        String content = skillContentMap.get(name);
        if (content == null) {
            throw new IllegalArgumentException("技能不存在: " + name
                    + "。可用技能: " + skillMetaMap.keySet());
        }
        return content;
    }

    /**
     * 检查指定名称的技能是否已注册。
     */
    public boolean hasSkill(String name) {
        return skillMetaMap.containsKey(name);
    }
}
