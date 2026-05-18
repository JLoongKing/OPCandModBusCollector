# 后端服务 - OPC UA数据采集系统

## 技术栈
- Java 8
- Spring Boot 2.7.18
- H2嵌入式数据库
- Spring Data JPA
- Eclipse Milo (OPC UA)

## 启动方式

### 方式一：使用Maven直接运行
```bash
mvn spring-boot:run
```

### 方式二：先编译再运行
```bash
mvn clean package
java -jar target/opcua-client-1.0.0.jar
```

## 访问地址
- 后端API: http://localhost:8081
- H2数据库控制台: http://localhost:8081/h2-console
  - JDBC URL: jdbc:h2:file:./data/opcua
  - 用户名: sa
  - 密码: 空

## 主要功能
- OPC UA客户端连接与数据采集
- Modbus TCP客户端连接与数据采集
- 配置管理API（OPC UA、Modbus、点位表、采集计划等）
- 数据持久化存储
- 数据可视化API支持
- 模拟数据生成（用于测试）
