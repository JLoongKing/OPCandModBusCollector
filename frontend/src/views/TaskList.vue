<template>
  <div class="task-list">
    <div class="page-header">
      <h2>任务管理</h2>
      <el-button type="primary" @click="createTask">创建任务</el-button>
    </div>

    <el-table :data="tasks" border stripe v-loading="loading" empty-text="暂无任务，请点击创建任务">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="任务名称" min-width="150" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="协议类型" width="120">
        <template #default="{ row }">
          <el-tag :type="row.protocolType === 'OPC_UA' ? 'success' : 'warning'">
            {{ row.protocolType === 'OPC_UA' ? 'OPC UA' : 'Modbus TCP' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'RUNNING' ? 'success' : 'info'">
            {{ row.status === 'RUNNING' ? '运行中' : '已停止' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="采集点数" width="80">
        <template #default="{ row }">
          {{ row.points ? row.points.length : 0 }}
        </template>
      </el-table-column>
      <el-table-column label="Kafka" width="80">
        <template #default="{ row }">
          <el-tag :type="row.kafkaEnabled ? 'success' : 'info'" size="small">
            {{ row.kafkaEnabled ? '开启' : '关闭' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status !== 'RUNNING'"
            type="success"
            size="small"
            :loading="actionLoading['start-' + row.id]"
            @click="startTask(row)"
          >启动</el-button>
          <el-button
            v-else
            type="warning"
            size="small"
            :loading="actionLoading['stop-' + row.id]"
            @click="stopTask(row)"
          >停止</el-button>
          <el-button type="primary" size="small" @click="viewDashboard(row)">数据</el-button>
          <el-button size="small" @click="editTask(row)">编辑</el-button>
          <el-button type="danger" size="small" :loading="actionLoading['delete-' + row.id]" @click="deleteTask(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

const API_BASE = '/api'

export default {
  name: 'TaskList',
  setup() {
    const router = useRouter()
    const tasks = ref([])
    const loading = ref(false)
    const actionLoading = reactive({})

    const fetchTasks = async () => {
      loading.value = true
      try {
        const response = await fetch(`${API_BASE}/tasks`)
        const res = await response.json()
        if (res.success) {
          tasks.value = res.data
        }
      } catch (e) {
        ElMessage.error('获取任务列表失败')
      } finally {
        loading.value = false
      }
    }

    const createTask = () => {
      router.push('/task/create')
    }

    const editTask = (task) => {
      router.push(`/task/edit/${task.id}`)
    }

    const viewDashboard = (task) => {
      router.push(`/task/dashboard/${task.id}`)
    }

    const startTask = async (task) => {
      actionLoading['start-' + task.id] = true
      try {
        const response = await fetch(`${API_BASE}/tasks/${task.id}/start`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' }
        })
        const res = await response.json()
        if (res.success) {
          ElMessage.success(`任务 "${task.name}" 已启动`)
          fetchTasks()
        } else {
          ElMessage.error(res.message || '启动失败')
        }
      } catch (e) {
        ElMessage.error('启动任务失败')
      } finally {
        actionLoading['start-' + task.id] = false
      }
    }

    const stopTask = async (task) => {
      actionLoading['stop-' + task.id] = true
      try {
        const response = await fetch(`${API_BASE}/tasks/${task.id}/stop`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' }
        })
        const res = await response.json()
        if (res.success) {
          ElMessage.success(`任务 "${task.name}" 已停止`)
          fetchTasks()
        } else {
          ElMessage.error(res.message || '停止失败')
        }
      } catch (e) {
        ElMessage.error('停止任务失败')
      } finally {
        actionLoading['stop-' + task.id] = false
      }
    }

    const deleteTask = async (task) => {
      try {
        await ElMessageBox.confirm(
          `确定要删除任务 "${task.name}" 吗？此操作不可恢复。`,
          '确认删除',
          { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
        )
        actionLoading['delete-' + task.id] = true
        const response = await fetch(`${API_BASE}/tasks/${task.id}`, {
          method: 'DELETE',
          headers: { 'Content-Type': 'application/json' }
        })
        const res = await response.json()
        if (res.success) {
          ElMessage.success('任务已删除')
          fetchTasks()
        } else {
          ElMessage.error(res.message || '删除失败')
        }
      } catch (e) {
        if (e !== 'cancel') {
          ElMessage.error('删除任务失败')
        }
      } finally {
        actionLoading['delete-' + task.id] = false
      }
    }

    onMounted(fetchTasks)

    return {
      tasks, loading, actionLoading, createTask, editTask, viewDashboard,
      startTask, stopTask, deleteTask
    }
  }
}
</script>

<style scoped>
.task-list {
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
</style>