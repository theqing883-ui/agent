package com.kaer.context.model;

public record BudgetAllocation(
        int systemPromptBudget,
        int toolDefinitionsBudget,
        int messagesBudget,
        int totalBudget
) {}
