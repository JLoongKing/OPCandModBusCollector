# 前端应用 - 数据采集配置系统

## 技术栈
- Vue 3
- Element Plus
- Vue Router
- Chart.js
- Vite

## 启动方式

### 方式一：使用CDN版本（无需Node.js）
直接在浏览器中打开 `index-cdn.html` 文件，或通过HTTP服务器访问：
```bash
# 使用Python
python -m http.server 3000

# 或使用Node.js
node server.js
```
然后访问：http://localhost:3000/index-cdn.html

### 方式二：使用Vite构建（需要Node.js 16+）
```bash
# 安装依赖
npm install

# 开发模式
npm run dev

# 生产构建
npm run build

# 预览构建结果
npm run preview
```

## 主要功能
- 数据看板（实时数据可视化）
- OPC UA配置管理
- Modbus TCP配置管理
- 点位表配置管理
- 采集计划配置管理
- Kafka配置管理
- 前后端配置同步

## 配置说明
- 前端默认连接后端地址：http://localhost:8081
- 如果后端地址有变化，请修改 `index-cdn.html` 中的 `API_BASE` 变量
