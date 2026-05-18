# OPC UA数据采集系统

本项目是一个完整的工业数据采集系统，支持OPC UA和Modbus TCP协议，提供数据采集、存储、可视化等功能。

## 项目结构

```
opcua/
├── backend/          # 后端服务（Java Spring Boot）
│   ├── src/          # Java源代码
│   ├── pom.xml       # Maven配置
│   └── README.md     # 后端说明文档
└── frontend/         # 前端应用（Vue 3）
    ├── src/          # Vue源代码
    ├── index-cdn.html # CDN版本前端页面
    └── README.md     # 前端说明文档
```

## 快速开始

### 1. 启动后端服务
```bash
cd backend
mvn spring-boot:run
```

### 2. 启动前端应用
```bash
cd frontend
node server.js
```
然后访问：http://localhost:3000/index-cdn.html

## 主要功能

1. **多协议支持**
   - OPC UA客户端：支持连接OPC UA服务器，实时采集数据
   - Modbus TCP客户端：支持连接Modbus TCP服务器，采集寄存器数据

2. **配置管理**
   - 可视化配置界面
   - 配置持久化存储
   - 前后端配置同步

3. **数据处理**
   - 实时数据采集
   - 数据持久化（H2数据库）
   - 数据可视化展示
   - Kafka消息推送（可选）

4. **数据可视化**
   - 实时数据看板
   - 历史数据趋势图
   - 数据统计分析

## 技术架构

### 后端
- Java 8 + Spring Boot 2.7.18
- Spring Data JPA + H2数据库
- Eclipse Milo OPC UA SDK
- Spring Kafka（可选）

### 前端
- Vue 3 + Element Plus
- Vue Router
- Chart.js 数据可视化
- Vite 构建工具
