package com.kaer.agent.tools;


/* 注释 @Component 注解，暂不将 DirectAnswerTool 注册为 Spring Bean ,
 * 因为这是直接对话工具，在JChatMind中已经可以进行对话了，可以不使用这个工具 */
//@Component
public class DirectAnswerTool implements Tool {
    @Override
    public String getName() {
        return "directAnswer";
    }

    @Override
    public String getDescription() {
        return "当用户的请求不需要执行操作时调用此工具，用以直接返回自然语言回答。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }
}

