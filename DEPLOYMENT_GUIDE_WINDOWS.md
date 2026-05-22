# OPC UA 客户端系统 - Windows 生产环境部署指南

## 📋 系统要求

### 硬件要求
- CPU：2核及以上
- 内存：4GB及以上
- 存储：20GB及以上可用空间
- 网络：稳定的网络连接（用于连接OPC UA服务器和数据库）

### 软件要求
- Windows Server 2016/2019/2022 或 Windows 10/11
- Java 8（项目指定使用Java 8）
- PostgreSQL 12 或更高版本（项目默认使用PostgreSQL）
- Nginx（可选，用于前端反向代理）

## 📦 部署前准备

### 1. 下载部署文件

从项目构建结果中获取以下文件：

#### 前端文件
- 位置：`frontend/dist/`
- 包含：`index.html`、`assets/`目录

#### 后端文件
- 位置：`backend/target/opcua-client-1.0.0.jar`
- 配置文件：`backend/src/main/resources/application.yml`

### 2. 安装Java环境

1. 下载Java 8版本（推荐Adoptium OpenJDK 8 LTS）
   - 下载地址：https://adoptium.net/temurin/releases/?version=8
2. 运行安装程序，选择"Add to PATH"选项
3. 验证安装：
   ```cmd
   java -version
   ```
   应显示类似：
   ```
   openjdk version "1.8.0_402" 2024-04-16
   OpenJDK Runtime Environment Temurin-8.0.402+6 (build 1.8.0_402-b06)
   OpenJDK 64-Bit Server VM Temurin-8.0.402+6 (build 25.402-b06, mixed mode)
   ```

### 3. 安装数据库（PostgreSQL）

1. 下载PostgreSQL 14 LTS版本
   - 下载地址：https://www.postgresql.org/download/windows/
2. 运行安装程序，选择"PostgreSQL Server"和"pgAdmin 4"
3. 设置postgres用户密码（默认用户名为postgres）
4. 完成安装后，打开pgAdmin 4
5. 连接到本地服务器，创建数据库：
   ```sql
   CREATE DATABASE opcua WITH ENCODING 'UTF8' LC_COLLATE 'Chinese (Simplified)_China.936' LC_CTYPE 'Chinese (Simplified)_China.936';
   ```

## 🚀 后端部署

### 1. 配置文件修改

复制`application.yml`到JAR文件同级目录，修改以下配置：

```yaml
server:
  port: 8081  # 后端服务端口

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/opcua
    driverClassName: org.postgresql.Driver
    username: postgres
    password: your_postgres_password
    hikari:
      data-source-properties:
        stringtype: unspecified
        client_encoding: UTF8

  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: update  # 生产环境建议使用none，手动执行SQL脚本
    show-sql: false  # 生产环境建议关闭
    properties:
      hibernate:
        format_sql: false

# OPC UA 配置（可选，可在前端任务配置中设置）
opcua:
  default-namespace: 2

# Kafka 配置（可选，不需要可禁用）
kafka:
  enabled: false
  bootstrap-servers: localhost:9092
  topic: opcua-data
```

### 2. 创建服务（推荐）

使用NSSM（Non-Sucking Service Manager）将后端服务注册为Windows服务，实现开机自启。

1. 下载NSSM：https://nssm.cc/download
2. 解压到`C:\nssm\`目录，并添加到系统PATH
3. 创建服务：
   ```cmd
   nssm install OpcuaService
   ```

4. 在弹出的窗口中配置：
   - Path：`C:\Program Files\Eclipse Adoptium\jdk-8.0.402.6-hotspot\bin\java.exe`
   - Arguments：`-jar opcua-client-1.0.0.jar`
   - Working directory：`C:\opcua\backend\`

5. 启动服务：
   ```cmd
   nssm start OpcuaService
   ```

6. 其他命令：
   ```cmd
   nssm stop OpcuaService  # 停止服务
   nssm restart OpcuaService  # 重启服务
   nssm remove OpcuaService  # 删除服务
   ```

### 3. 手动运行（测试用）

如果不需要注册为服务，可以手动运行：

```cmd
cd C:\opcua\backend\
java -jar opcua-client-1.0.0.jar
```

### 4. 验证后端服务

打开浏览器访问：`http://localhost:8081/api/tasks`

应返回类似：
```json
{"success":true,"data":[]}
```

## 🎨 前端部署

### 1. 部署到Nginx（推荐）

#### 安装Nginx
1. 下载Nginx Windows版本：https://nginx.org/en/download.html
2. 解压到`C:\nginx\`目录

#### 配置Nginx

编辑`C:\nginx\conf\nginx.conf`，修改server块：

```nginx
server {
    listen       80;
    server_name  localhost;

    # 前端静态文件
    location / {
        root   C:/opcua/frontend/dist;
        index  index.html index.htm;
        try_files $uri $uri/ /index.html;  # 支持Vue路由
    }

    # API反向代理
    location /api/ {
        proxy_pass http://localhost:8081/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 错误页面
    error_page   500 502 503 504  /50x.html;
    location = /50x.html {
        root   html;
    }
}
```

#### 启动Nginx

```cmd
cd C:\nginx\
start nginx
```

#### Nginx管理命令

```cmd
nginx -s stop  # 停止
nginx -s reload  # 重新加载配置
nginx -s quit  # 优雅退出
```

### 2. 部署到IIS

1. 安装IIS角色（通过"添加角色和功能"向导）
2. 安装"URL重写"模块：https://www.iis.net/downloads/microsoft/url-rewrite
3. 安装"应用程序请求路由"模块：https://www.iis.net/downloads/microsoft/application-request-routing
4. 创建网站，指向`C:\opcua\frontend\dist`目录
5. 配置URL重写规则，支持Vue路由
6. 配置反向代理，将/api/*转发到后端服务

### 3. 验证前端

打开浏览器访问：`http://localhost`

应显示OPC UA客户端系统的登录/任务管理页面。

## 🔒 安全配置

### 1. 防火墙配置

打开Windows防火墙，添加入站规则：
- 允许端口80（前端HTTP）
- 允许端口8081（后端API，仅内部访问）
- 允许5432端口（仅内部访问数据库）

### 2. HTTPS配置（推荐）

#### 前端HTTPS
1. 申请SSL证书（可使用Let's Encrypt免费证书）
2. 在Nginx或IIS中配置HTTPS
3. 强制HTTP跳转HTTPS

#### 后端HTTPS
1. 在`application.yml`中配置SSL：
   ```yaml
   server:
     port: 8443
     ssl:
       key-store: classpath:keystore.p12
       key-store-password: your_password
       key-store-type: PKCS12
       key-alias: tomcat
   ```

### 3. 数据库安全
- 为应用创建专用数据库用户，避免使用postgres用户
- 限制5432端口的访问IP
- 定期备份数据库
- 配置PostgreSQL的pg_hba.conf文件，限制访问权限

## 📊 监控与维护

### 1. 日志查看

#### 后端日志
- 默认日志文件：`C:\opcua\backend\logs\opcua.log`
- 可在`application.yml`中配置日志级别和路径

#### Nginx日志
- 访问日志：`C:\nginx\logs\access.log`
- 错误日志：`C:\nginx\logs\error.log`

### 2. 性能监控
- 使用Windows任务管理器监控CPU、内存使用情况
- 使用Java VisualVM监控JVM性能
- 配置Prometheus + Grafana进行更详细的监控（可选）

### 3. 备份策略

#### 数据库备份
```cmd
pg_dump -U postgres opcua > opcua_backup_$(Get-Date -Format "yyyyMMdd").sql
```

#### 数据库恢复
```cmd
psql -U postgres -d opcua -f opcua_backup_20260521.sql
```

#### 配置文件备份
定期备份以下文件：
- `application.yml`
- Nginx配置文件
- 数据库备份文件

## 🚨 故障排查

### 1. 服务无法启动
- 检查Java环境是否正确配置
- 查看日志文件中的错误信息
- 检查数据库连接是否正常
- 检查端口是否被占用：`netstat -ano | findstr :8081`

### 2. 前端无法访问后端
- 检查防火墙是否允许端口访问
- 检查Nginx反向代理配置是否正确
- 检查后端服务是否正常运行

### 3. OPC UA连接失败
- 检查OPC UA服务器地址和端口是否正确
- 检查命名空间配置是否正确
- 检查网络连接是否正常
- 查看后端日志中的错误信息

## 📞 技术支持

如有问题，请联系技术支持团队，提供以下信息：
- 操作系统版本
- Java版本
- 数据库版本
- 相关日志文件
- 错误截图或描述

---

**更新日期**：2026-05-21
**版本**：v1.0.0