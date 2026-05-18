<template>
  <div id="app">
    <el-container>
      <el-header class="app-header">
        <span class="app-title">多协议数据采集平台</span>
        <el-tag type="info" size="small">OPC UA / Modbus TCP / Kafka</el-tag>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script>
export default {
  name: 'App'
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
html, body, #app {
  width: 100%;
  height: 100%;
}
.app-header {
  background: linear-gradient(135deg, #409EFF, #337ECC);
  color: white;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px !important;
}
.app-title {
  font-size: 20px;
  font-weight: bold;
  letter-spacing: 2px;
}
</style>