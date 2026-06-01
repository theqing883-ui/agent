package com.kaer.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class DataBaseTools implements Tool {

    private final JdbcTemplate jdbcTemplate;

    public DataBaseTools(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getName() {
        return "dataBaseTool";
    }

    @Override
    public String getDescription() {
        return "一个用于执行数据库查询操作的工具，主要用于从 PostgreSQL 中读取数据。";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    /**
     * 执行 SQL 查询（只读）
     * <p>
     * 该方法用于在 PostgreSQL 数据库中执行只读查询操作。
     * 安全机制：仅允许执行 SELECT 语句，禁止任何写操作（INSERT/UPDATE/DELETE/DROP 等）。
     * 查询结果会被格式化为表格形式返回，便于大语言模型理解。
     *
     * @param sql SQL 查询语句（必须以 SELECT 开头）
     * @return 格式化的表格形式查询结果字符串
     */
    @org.springframework.ai.tool.annotation.Tool(name = "databaseQuery", description = "用于在 PostgreSQL 中执行只读查询（SELECT）。接收由模型生成的查询语句，并返回结构化数据结果。该工具仅用于检索数据，严禁任何写入或修改数据库的语句。")
    public String query(@ToolParam(description = "一条在 PostgreSQL 中执行查询（SELECT）的SQL语句") String sql) {
        try {
            // ========== 安全校验阶段 ==========
            // 将 SQL 转为大写并去除首尾空格，便于统一判断
            String trimmedSql = sql.trim().toUpperCase();
            // 安全检查：仅允许 SELECT 查询，防止 SQL 注入和误操作
            if (!trimmedSql.startsWith("SELECT")) {
                log.warn("拒绝执行非 SELECT 查询: {}", sql);
                return "错误：仅支持 SELECT 查询语句。提供的 SQL: " + sql;
            }

            // ========== 执行查询阶段 ==========
            // 使用 JdbcTemplate 执行查询，通过 RowMapper 处理结果集
            List<String> rows = jdbcTemplate.query(sql, (ResultSet rs) -> {
                List<String> resultRows = new ArrayList<>();
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // 处理无列的情况
                if (columnCount == 0) {
                    resultRows.add("查询结果为空（无列）");
                    return resultRows;
                }

                // ========== 列信息收集阶段 ==========
                // 收集列名和初始列宽（初始为列名长度）
                List<String> columnNames = new ArrayList<>();
                List<Integer> columnWidths = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    columnNames.add(columnName);
                    columnWidths.add(columnName.length());
                }

                // ========== 数据行收集阶段 ==========
                // 遍历结果集，收集所有数据行并动态计算列宽
                List<List<String>> dataRows = new ArrayList<>();
                while (rs.next()) {
                    List<String> rowData = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        Object value = rs.getObject(i);
                        String valueStr = value == null ? "NULL" : value.toString();
                        rowData.add(valueStr);
                        // 更新列宽：取列名长度和数据长度的较大值
                        int currentWidth = columnWidths.get(i - 1);
                        if (valueStr.length() > currentWidth) {
                            columnWidths.set(i - 1, valueStr.length());
                        }
                    }
                    dataRows.add(rowData);
                }

                // ========== 结果格式化阶段 ==========
                // 构建表头行
                StringBuilder header = new StringBuilder();
                header.append("| ");
                for (int i = 0; i < columnCount; i++) {
                    String columnName = columnNames.get(i);
                    int width = columnWidths.get(i);
                    header.append(String.format("%-" + width + "s", columnName)).append(" | ");
                }
                resultRows.add(header.toString());

                // 构建分隔线（由 '-' 组成）
                StringBuilder separator = new StringBuilder();
                separator.append("|");
                for (int i = 0; i < columnCount; i++) {
                    int width = columnWidths.get(i);
                    separator.append("-".repeat(width + 2)).append("|");
                }
                resultRows.add(separator.toString());

                // 构建数据行
                if (dataRows.isEmpty()) {
                    // 无数据时显示提示信息
                    StringBuilder emptyRow = new StringBuilder();
                    emptyRow.append("| ");
                    int totalWidth = columnWidths.stream().mapToInt(w -> w + 3).sum() - 1;
                    emptyRow.append(String.format("%-" + (totalWidth - 2) + "s", "(无数据)"));
                    emptyRow.append(" |");
                    resultRows.add(emptyRow.toString());
                } else {
                    // 遍历数据行并格式化输出
                    for (List<String> rowData : dataRows) {
                        StringBuilder row = new StringBuilder();
                        row.append("| ");
                        for (int i = 0; i < columnCount; i++) {
                            String value = rowData.get(i);
                            int width = columnWidths.get(i);
                            row.append(String.format("%-" + width + "s", value)).append(" | ");
                        }
                        resultRows.add(row.toString());
                    }
                }

                return resultRows;
            });

            // ========== 结果统计阶段 ==========
            // 计算实际数据行数（减去表头和分隔线）
            int dataRowCount = rows.size() - 2;
            // 如果最后一行是"无数据"提示，则数据行数为0
            if (rows.size() > 2 && rows.get(rows.size() - 1).contains("(无数据)")) {
                dataRowCount = 0;
            }

            log.info("成功执行 SQL 查询，返回 {} 行数据", dataRowCount);
            // 将所有行拼接为最终的字符串结果
            return "查询结果:\n" + String.join("\n", rows);
        } catch (Exception e) {
            // 捕获所有异常，记录日志并返回友好错误信息
            log.error("数据库查询异常: {}", e.getMessage(), e);
            return "错误：操作失败 - " + e.getMessage() + "\nSQL: " + sql;
        }
    }
}