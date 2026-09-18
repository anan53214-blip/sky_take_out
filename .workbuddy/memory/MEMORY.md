# 项目长期记忆 — sky-take-out（苍穹外卖）

## 项目定位 / 目录关系
- `D:\program\sky-take-out` = 用户正在练手的后端项目（苍穹外卖，黑马教程）。
- `D:\program\sky-take-out-info` = 教程指南 / 参考答案（含按天的文档与参考实现），用于对照已完成进度、找 bug、查规范。
- 概念：按天对照——用户完成某天任务后，用 info 里的当天参考实现对比其实际代码（逻辑/语法/规范/bug）。分析时不改用户代码，只给结论与建议。

## 构建 / 环境
- Maven 多模块：`sky-common` / `sky-pojo` / `sky-server`，父 pom 继承 `spring-boot-starter-parent 2.7.3`。
- **Java 语言级别 = 1.8**（父 pom 默认值，项目内未覆盖 `java.version`；已编译 class 校验为 major 52）。
  - **禁止使用 Java 9+ API**：`List.of` / `Set.of` / `Map.of` / `List.copyOf`、文本块 `"""`、局部变量 `var`、接口私有方法等。
  - 单元素集合改用：`Collections.singletonList(x)` 或 `Arrays.asList(x)`。
  - 如需升级：在根 pom `<properties>` 加 `<java.version>11</java.version>`（Spring Boot 父 pom 会据此设置 compiler source/target），但教程项目原基于 8，升级前评估兼容性。
- MySQL 客户端：`/d/mySQL/file/bin/mysql`（非 PATH，需全路径）；库 `sky_take_out`，root / pjq20021202.（见 `application-dev.yml`）。

## Redis 配置
- Redis 装在 `D:\redis`（Windows 版）。启动须带配置文件：`redis-server.exe redis.windows.conf`，双击 exe 会用默认 6379 且忽略 conf。
- 项目 `application-dev.yml` 的 redis：`host=localhost`、`port=6379`、`password=pjq20021202.`、`database=10`（通过 `application.yml` 里 `sky.redis.*` 占位符引用）。
- 坑：配了 password 但 redis 未设 requirepass → 连接时报 "Client sent AUTH, but no password is set"；两者必须一致。
- 坑：Spring Boot 测试类必须带 `@SpringBootTest`，否则 IOC 容器不启动，`@Autowired` 字段为 null（用户曾把 `@SpringBootTest` 注释掉导致 NPE）。

## 代码约定
- Mapper 批量删除统一签名 `deleteByIds(List<Long> ids)`，XML 为 `delete from setmeal_dish where setmeal_id in (...)`。
  - **单条删除复用批量方法**，传单元素集合（`Collections.singletonList(id)`），不新建 `deleteById` 之类的方法。
- Service 层 `BeanUtils.copyProperties(dto, entity)` + `mapper.update(entity)` 是标准更新套路。

## @AutoFill 公共字段填充约定
- mapper 接口方法加 `@AutoFill(OperationType.UPDATE/INSERT)` → AOP 前置通知自动反射填 entity 公共字段：
  - UPDATE：仅 `updateTime`(LocalDateTime.now())、`updateUser`(BaseContext.getCurrentId())。
  - INSERT：外加 `createTime`、`createUser`。
- 注意：(1) 注解必须加在 **mapper 接口方法**上（切点 `execution(* com.sky.mapper.*.*(..)) && @annotation(...)`），加在 service 方法上不触发；(2) XML 的 update/insert 必须**无条件**写 `update_time = #{updateTime}, update_user = #{updateUser}` 等列，否则 AOP 填的值落不了库——前端不传这些字段，由 AOP 补；(3) 时间类型(LocalDateTime)/数值类型(Long)字段**禁止 `!= ''` 比较**，会抛 MyBatis OGNL `invalid comparison` 报错，只判 `!= null` 或不判。
