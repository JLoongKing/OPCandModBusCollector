<template>
  <div class="task-dashboard">
    <div class="page-header">
      <h2>数据看板 - {{ taskName }}</h2>
      <div>
        <el-tag :type="taskStatus === 'RUNNING' ? 'success' : 'info'" size="large">
          {{ taskStatus === 'RUNNING' ? '运行中' : '已停止' }}
        </el-tag>
        <el-button style="margin-left: 10px;" @click="goBack">返回任务列表</el-button>
      </div>
    </div>

    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">{{ stat.label }}</div>
            <div class="stat-value">{{ stat.value }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

<el-card style="margin-top: 20px;">
      <template #header>
        <span>最新数据</span>
      </template>
      <el-table :data="latestData" border stripe empty-text="暂无数据">
        <el-table-column prop="pointName" label="点位名称" width="150" />
        <el-table-column prop="address" label="地址" min-width="200" />
        <el-table-column prop="value" label="数值" width="150" />
        <el-table-column prop="dataType" label="数据类型" width="100" />
        <el-table-column prop="timestamp" label="采集时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const API_BASE = '/api'

export default {
  name: 'TaskDashboard',
  setup() {
    const router = useRouter()
    const route = useRoute()
    const taskId = route.params.id
    const taskName = ref('')
    const taskStatus = ref('')
    const latestData = ref([])
    const stats = ref([
      { label: '采集点数', value: '0' },
      { label: '数据总量', value: '0' },
      { label: '最新采集', value: '-' },
      { label: '采集状态', value: '未知' }
    ])

    let refreshTimer = null

    const loadTaskInfo = async () => {
      try {
        const res = await axios.get(`${API_BASE}/tasks/${taskId}`)
        if (res.data.success) {
          const task = res.data.data
          taskName.value = task.name
          taskStatus.value = task.status
          stats.value[0].value = task.points ? String(task.points.length) : '0'
          stats.value[3].value = task.status === 'RUNNING' ? '正常采集' : '已停止'
        }
      } catch (e) {
        ElMessage.error('加载任务信息失败')
      }
    }

    const loadStatistics = async () => {
      try {
        const res = await axios.get(`${API_BASE}/tasks/${taskId}/statistics`)
        if (res.data.success && res.data.data) {
          stats.value[1].value = String(res.data.data.totalRecords ?? 0)
        }
      } catch (e) {
        console.error('加载统计失败', e)
      }
    }



    const loadLatestData = async () => {
      try {
        const res = await axios.get(`${API_BASE}/tasks/${taskId}/data`)
        if (res.data.success && res.data.data) {
          latestData.value = res.data.data.slice(0, 20)
          if (res.data.data.length > 0) {
            stats.value[2].value = res.data.data[0].timestamp
          }
        }
      } catch (e) {
        console.error('加载最新数据失败', e)
      }
    }

    const refreshData = () => {
      loadLatestData()
      loadTaskInfo()
      loadStatistics()
    }

    const goBack = () => {
      router.push('/')
    }

    onMounted(async () => {
      await loadTaskInfo()
      await loadStatistics()
      await loadLatestData()
      refreshTimer = setInterval(refreshData, 5000)
    })

    onUnmounted(() => {
      if (refreshTimer) clearInterval(refreshTimer)
    })

    return {
      taskName, taskStatus, latestData, stats,
      refreshData, goBack
    }
  }
}
</script>

<style scoped>
.task-dashboard {
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
  color: #303133;
}
.stat-card {
  text-align: center;
}
.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
