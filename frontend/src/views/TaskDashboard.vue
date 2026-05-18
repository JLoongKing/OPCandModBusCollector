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

    <el-card>
      <template #header>
        <div class="card-header">
          <span>实时数据趋势</span>
          <el-button size="small" @click="refreshChart">刷新</el-button>
        </div>
      </template>
      <div class="chart-container">
        <canvas ref="chartCanvas"></canvas>
      </div>
    </el-card>

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
import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Legend,
  Tooltip,
  Filler
} from 'chart.js'

Chart.register(LineController, LineElement, PointElement, LinearScale, CategoryScale, Legend, Tooltip, Filler)

const API_BASE = '/api'

/**
 * 将后端 /data/chart 返回的 { 点位名: [{ timestamp, value }, ...] } 转为 Chart.js 所需结构。
 */
function transformChartSeries(apiData) {
  if (!apiData || typeof apiData !== 'object') {
    return { labels: [], series: [] }
  }
  const pointNames = Object.keys(apiData)
  if (pointNames.length === 0) {
    return { labels: [], series: [] }
  }

  const allTimes = new Set()
  pointNames.forEach((name) => {
    const arr = apiData[name] || []
    arr.forEach((p) => {
      if (p && p.timestamp) {
        allTimes.add(p.timestamp)
      }
    })
  })
  const labels = Array.from(allTimes).sort()

  const series = pointNames.map((name) => {
    const arr = apiData[name] || []
    const byTime = new Map(arr.map((p) => [p.timestamp, p.value]))
    const data = labels.map((t) => {
      const raw = byTime.get(t)
      if (raw === undefined || raw === null) {
        return null
      }
      const s = String(raw)
      if (s.startsWith('ERROR:')) {
        return null
      }
      const n = Number(s)
      return Number.isFinite(n) ? n : null
    })
    return { name, data }
  })

  return { labels, series }
}

export default {
  name: 'TaskDashboard',
  setup() {
    const router = useRouter()
    const route = useRoute()
    const taskId = route.params.id
    const taskName = ref('')
    const taskStatus = ref('')
    const chartCanvas = ref(null)
    const latestData = ref([])
    const stats = ref([
      { label: '采集点数', value: '0' },
      { label: '数据总量', value: '0' },
      { label: '最新采集', value: '-' },
      { label: '采集状态', value: '未知' }
    ])

    let chartInstance = null
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

    const loadChartData = async () => {
      try {
        const res = await axios.get(`${API_BASE}/tasks/${taskId}/data/chart`, { params: { seconds: 120 } })
        if (res.data.success && res.data.data) {
          const { labels, series } = transformChartSeries(res.data.data)
          renderChart(labels, series)
        }
      } catch (e) {
        console.error('加载图表数据失败', e)
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

    const renderChart = (labels, series) => {
      if (!chartCanvas.value) return

      const ctx = chartCanvas.value.getContext('2d')
      if (chartInstance) {
        chartInstance.destroy()
      }

      const datasets = []
      const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#B37FEB', '#5CDBD3', '#FF85C0']
      if (series && series.length > 0) {
        series.forEach((s, index) => {
          datasets.push({
            label: s.name,
            data: s.data,
            borderColor: colors[index % colors.length],
            backgroundColor: 'transparent',
            borderWidth: 2,
            pointRadius: 1,
            tension: 0.25,
            spanGaps: true
          })
        })
      }

      chartInstance = new Chart(ctx, {
        type: 'line',
        data: { labels: labels || [], datasets },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          animation: { duration: 300 },
          scales: {
            x: {
              display: true,
              title: { display: true, text: '时间' },
              ticks: { maxTicksLimit: 24 }
            },
            y: {
              display: true,
              title: { display: true, text: '数值' }
            }
          },
          plugins: {
            legend: { position: 'top' }
          }
        }
      })
    }

    const refreshChart = () => {
      loadChartData()
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
      await nextTick()
      await loadChartData()
      await loadLatestData()
      refreshTimer = setInterval(refreshChart, 5000)
    })

    onUnmounted(() => {
      if (refreshTimer) clearInterval(refreshTimer)
      if (chartInstance) chartInstance.destroy()
    })

    return {
      taskName, taskStatus, chartCanvas, latestData, stats,
      refreshChart, goBack
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
.chart-container {
  height: 400px;
  position: relative;
}
.chart-container canvas {
  width: 100% !important;
  height: 100% !important;
}
</style>
