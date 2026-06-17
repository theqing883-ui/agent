package com.kaer.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;

/**
 * PostgreSQL pgvector 类型处理器
 * 实现 Java float[] 数组与 PostgreSQL pgvector 类型之间的双向转换
 * 
 * @MappedJdbcTypes(JdbcType.OTHER) 指定处理的JDBC类型为OTHER
 * @MappedTypes(float[].class) 指定处理的Java类型为float数组
 */
@MappedJdbcTypes(JdbcType.OTHER)
@MappedTypes(float[].class)
public class PgVectorTypeHandler extends BaseTypeHandler<float[]> {

    /**
     * 设置非空参数
     * 将 Java float[] 数组转换为 PostgreSQL pgvector 格式字符串 [v1,v2,v3,...]
     *
     * @param ps        PreparedStatement 对象
     * @param i         参数索引
     * @param parameter float[] 数组参数
     * @param jdbcType  JDBC类型
     * @throws SQLException SQL异常
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, float[] parameter, JdbcType jdbcType) throws SQLException {
        // 构建 pgvector 格式字符串 [v1,v2,v3,...]
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int j = 0; j < parameter.length; j++) {
            sb.append(parameter[j]);
            if (j < parameter.length - 1) sb.append(',');
        }
        sb.append(']');
        // 使用 Types.OTHER 类型设置参数
        ps.setObject(i, sb.toString(), Types.OTHER);
    }

    /**
     * 从ResultSet中获取指定列名的结果
     *
     * @param rs        ResultSet 对象
     * @param columnName 列名
     * @return float[] 数组
     * @throws SQLException SQL异常
     */
    @Override
    public float[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    /**
     * 从ResultSet中获取指定列索引的结果
     *
     * @param rs          ResultSet 对象
     * @param columnIndex 列索引
     * @return float[] 数组
     * @throws SQLException SQL异常
     */
    @Override
    public float[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    /**
     * 从CallableStatement中获取指定列索引的结果
     *
     * @param cs          CallableStatement 对象
     * @param columnIndex 列索引
     * @return float[] 数组
     * @throws SQLException SQL异常
     */
    @Override
    public float[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    /**
     * 解析 pgvector 格式字符串为 float[] 数组
     * 格式: [v1,v2,v3,...] -> float[]{v1, v2, v3, ...}
     *
     * @param vectorText pgvector格式的字符串
     * @return float[] 数组，如果输入为空则返回null或空数组
     */
    private float[] parse(String vectorText) {
        // 空值检查
        if (vectorText == null) return null;
        // 去掉方括号
        vectorText = vectorText.replace("[", "").replace("]", "");
        // 空字符串检查
        if (vectorText.isBlank()) return new float[0];
        // 按逗号分割
        String[] parts = vectorText.split(",");
        // 转换为float数组
        float[] arr = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            arr[i] = Float.parseFloat(parts[i]);
        }
        return arr;
    }
}