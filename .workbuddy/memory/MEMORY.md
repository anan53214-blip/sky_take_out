# 项目长期记忆 — sky-take-out（苍穹外卖）

## 构建 / 环境
- Maven 多模块：`sky-common` / `sky-pojo` / `sky-server`，父 pom 继承 `spring-boot-starter-parent 2.7.3`。
- **Java 语言级别 = 1.8**（父 pom 默认值，项目内未覆盖 `java.version`；已编译 class 校验为 major 52）。
  - **禁止使用 Java 9+ API**：`List.of` / `Set.of` / `Map.of` / `List.copyOf`、文本块 `"""`、局部变量 `var`、接口私有方法等。
  - 单元素集合改用：`Collections.singletonList(x)` 或 `Arrays.asList(x)`。
  - 如需升级：在根 pom `<properties>` 加 `<java.version>11</java.version>`（Spring Boot 父 pom 会据此设置 compiler source/target），但教程项目原基于 8，升级前评估兼容性。
- MySQL 客户端：`/d/mySQL/file/bin/mysql`（非 PATH，需全路径）；库 `sky_take_out`，root / pjq20021202.（见 `application-dev.yml`）。

## 代码约定
- Mapper 批量删除统一签名 `deleteByIds(List<Long> ids)`，XML 为 `delete from setmeal_dish where setmeal_id in (...)`。
  - **单条删除复用批量方法**，传单元素集合（`Collections.singletonList(id)`），不新建 `deleteById` 之类的方法。
- Service 层 `BeanUtils.copyProperties(dto, entity)` + `mapper.update(entity)` 是标准更新套路。
