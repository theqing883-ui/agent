# 电商系统（eshop）数据库设计文档（PostgreSQL）

> 说明：
>
> * 主键统一使用 `UUID + gen_random_uuid()`
> * 所有时间字段使用 `TIMESTAMPTZ`
> * 文本型扩展信息统一使用 `JSONB`
> * 评论相关表：`t_comment` / `t_comment_topic` / `t_comment_topic_mapping` / `t_comment_summary_daily`

## 1. 设计目标

1. 支撑电商核心业务：用户、商品、订单、支付、发货。
2. 支持用户对商品的评论与多维度分析（评价内容、评分等）。

## 2. 表清单

| 表名                          | 说明                 |
| --------------------------- | ------------------ |
| `t_app_user`                  | 用户表                |
| `t_role`                      | 角色表（管理员、客服等）       |
| `t_user_role`                 | 用户-角色关联表           |
| `t_product_category`          | 商品类目               |
| `t_product`                   | 商品主表               |
| `t_product_category_relation` | 商品-类目多对多关联         |
| `t_order_header`              | 订单主表               |
| `t_order_item`                | 订单明细               |
| `t_payment`                   | 支付记录               |
| `t_shipment`                  | 发货记录               |
| `t_comment_topic`             | 评论话题定义（“物流”、“质量”等） |
| `t_comment`                   | **评论表，核心分析对象**     |
| `t_comment_topic_mapping`     | 评论-话题关联表（多对多）      |
| `t_comment_summary_daily`     | 评论日汇总表，用于加速统计      |
| `t_system_kv`                 | 系统配置 / 开关表         |

---