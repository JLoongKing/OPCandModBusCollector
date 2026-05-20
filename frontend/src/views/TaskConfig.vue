<template>
  <div class="task-config">
    <div class="page-header">
      <h2>{{ isEdit ? '编辑任务' : '创建任务' }}</h2>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="140px" v-loading="loading" :validate-on-rule-change="false">
      <el-divider content-position="left">基本信息</el-divider>
      <el-form-item label="任务名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入任务名称" maxlength="100" />
      </el-form-item>
      <el-form-item label="任务描述" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入任务描述" maxlength="500" />
      </el-form-item>
      <el-form-item label="协议类型" prop="protocolType">
        <el-radio-group v-model="form.protocolType" @change="onProtocolChange">
          <el-radio label="OPC_UA">OPC UA</el-radio>
          <el-radio label="MODBUS_TCP">Modbus TCP</el-radio>
        </el-radio-group>
      </el-form-item>

      <template v-if="form.protocolType === 'OPC_UA'">
        <el-divider content-position="left">OPC UA 配置</el-divider>
        <el-form-item label="服务器地址" prop="opcServerUrl">
          <el-input v-model="form.opcServerUrl" placeholder="opc.tcp://host:port" />
        </el-form-item>
        <el-form-item label="安全策略">
          <el-select v-model="form.opcSecurityPolicy" placeholder="请选择安全策略">
            <el-option label="None" value="None" />
            <el-option label="Basic128Rsa15" value="Basic128Rsa15" />
            <el-option label="Basic256" value="Basic256" />
            <el-option label="Basic256Sha256" value="Basic256Sha256" />
          </el-select>
        </el-form-item>
        <el-form-item label="安全模式">
          <el-select v-model="form.opcSecurityMode" placeholder="请选择安全模式">
            <el-option label="None" value="None" />
            <el-option label="Sign" value="Sign" />
            <el-option label="SignAndEncrypt" value="SignAndEncrypt" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.opcUsername" placeholder="匿名认证可不填" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.opcPassword" type="password" placeholder="匿名认证可不填" show-password />
        </el-form-item>
        <el-form-item label="会话超时(ms)">
          <el-input-number v-model="form.opcSessionTimeout" :min="1000" :max="120000" :step="1000" />
        </el-form-item>
        <el-form-item label="采集周期(ms)" prop="opcScanInterval">
          <el-input-number v-model="form.opcScanInterval" :min="100" :max="600000" :step="100" />
          <span class="field-hint">定时读取所有点位的间隔，默认 1000ms</span>
        </el-form-item>
        <el-form-item label="命名空间">
          <el-input v-model="form.opcNamespace" placeholder="如：2" />
          <span class="field-hint">OPC UA服务器的命名空间，默认0，导入Excel时会自动提取</span>
        </el-form-item>
      </template>

      <template v-if="form.protocolType === 'MODBUS_TCP'">
        <el-divider content-position="left">Modbus TCP 配置</el-divider>
        <el-form-item label="主机地址" prop="modbusHost">
          <el-input v-model="form.modbusHost" placeholder="192.168.1.100" />
        </el-form-item>
        <el-form-item label="端口号" prop="modbusPort">
          <el-input-number v-model="form.modbusPort" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="超时时间(ms)">
          <el-input-number v-model="form.modbusTimeout" :min="100" :max="30000" :step="100" />
        </el-form-item>
        <el-form-item label="扫描间隔(ms)">
          <el-input-number v-model="form.modbusScanInterval" :min="100" :max="60000" :step="100" />
        </el-form-item>
      </template>

      <el-divider content-position="left">采集点位配置</el-divider>
      <el-form-item label="批量导入">
        <el-space wrap>
          <el-button type="primary" size="small" @click="downloadTemplate">下载 Excel 模板</el-button>
          <el-upload
            :show-file-list="false"
            accept=".xlsx,.xls"
            :http-request="handleExcelUpload"
          >
            <el-button type="success" size="small">从 Excel 合并点位</el-button>
          </el-upload>
          <span v-if="isEdit" class="import-hint">已保存任务将写入数据库并刷新列表</span>
          <span v-else class="import-hint">新建任务将合并到下方表格（保存后生效）</span>
        </el-space>
      </el-form-item>
      <el-form-item label="采集点位">
        <el-button type="primary" size="small" @click="addPoint">添加点位</el-button>
        <el-table :data="form.points" border stripe style="margin-top: 10px;" empty-text="暂无点位，请添加">
          <el-table-column label="点位名称" min-width="120">
            <template #default="{ row, $index }">
              <el-input v-model="row.name" placeholder="如：温度" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="地址" min-width="180">
            <template #default="{ row, $index }">
              <el-input v-model="row.address" placeholder="如：ns=2;s=Temperature" size="small" />
            </template>
          </el-table-column>

          <el-table-column label="设备编码" min-width="250">
            <template #default="{ row, $index }">
              <el-input v-model="row.devId" placeholder="设备编码" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="点位编码" min-width="250">
            <template #default="{ row, $index }">
              <el-input v-model="row.nodeId" placeholder="点位编码" size="small" />
            </template>
          </el-table-column>
          <el-table-column v-if="form.protocolType === 'MODBUS_TCP'" label="数据类型" width="120">
            <template #default="{ row }">
              <el-select v-model="row.dataType" placeholder="选择类型" size="small" style="width: 100%;">
                <el-option label="int" value="int" />
                <el-option label="uint" value="uint" />
                <el-option label="float" value="float" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column v-if="form.protocolType === 'MODBUS_TCP'" label="位数" width="100">
            <template #default="{ row }">
              <el-select v-model="row.bitLength" placeholder="选择位数" size="small" style="width: 100%;">
                <el-option :label="16" :value="16" />
                <el-option :label="32" :value="32" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="比例系数" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.scaleFactor" :min="0.0001" :max="10000" :step="0.1" size="small" style="width: 100%;" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ $index }">
              <el-button type="danger" size="small" @click="removePoint($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>

      <el-divider content-position="left">Kafka 配置</el-divider>
      <el-form-item label="启用Kafka">
        <el-switch v-model="form.kafkaEnabled" />
      </el-form-item>
      <template v-if="form.kafkaEnabled">
        <el-form-item label="Bootstrap Servers" prop="kafkaBootstrapServers">
          <el-input v-model="form.kafkaBootstrapServers" placeholder="localhost:9092" />
        </el-form-item>
        <el-form-item label="Topic" prop="kafkaTopic">
          <el-input v-model="form.kafkaTopic" placeholder="请输入Topic名称" />
        </el-form-item>
        <el-form-item label="Acks">
          <el-select v-model="form.kafkaAcks" placeholder="请选择">
            <el-option label="0 (不等待确认)" value="0" />
            <el-option label="1 (Leader确认)" value="1" />
            <el-option label="all (全部确认)" value="all" />
          </el-select>
        </el-form-item>
        <el-form-item label="重试次数">
          <el-input-number v-model="form.kafkaRetries" :min="0" :max="10" />
        </el-form-item>
        <el-form-item label="批量大小(bytes)">
          <el-input-number v-model="form.kafkaBatchSize" :min="1024" :max="1048576" :step="1024" />
        </el-form-item>
        <el-form-item label="延迟时间(ms)">
          <el-input-number v-model="form.kafkaLingerMs" :min="0" :max="1000" :step="10" />
        </el-form-item>
        <el-form-item label="缓冲区内存(bytes)">
          <el-input-number v-model="form.kafkaBufferMemory" :min="1048576" :max="134217728" :step="1048576" />
        </el-form-item>
      </template>

      <el-form-item>
        <el-button type="primary" @click="submitForm" :loading="submitting">保存任务</el-button>
        <el-button @click="goBack">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const API_BASE = '/api'

export default {
  name: 'TaskConfig',
  setup() {
    const router = useRouter()
    const route = useRoute()
    const formRef = ref(null)
    const loading = ref(false)
    const submitting = ref(false)
    const isEdit = ref(false)

    const form = reactive({
      name: '',
      description: '',
      protocolType: 'OPC_UA',
      opcServerUrl: 'opc.tcp://localhost:7718',
      opcSecurityPolicy: 'None',
      opcSecurityMode: 'None',
      opcUsername: '',
      opcPassword: '',
      opcSessionTimeout: 5000,
      opcScanInterval: 1000,
      opcNamespace: '2',
      modbusHost: '127.0.0.1',
      modbusPort: 502,
      modbusTimeout: 3000,
      modbusScanInterval: 1000,
      kafkaEnabled: false,
      kafkaBootstrapServers: 'localhost:9092',
      kafkaTopic: '',
      kafkaAcks: '1',
      kafkaRetries: 3,
      kafkaBatchSize: 16384,
      kafkaLingerMs: 0,
      kafkaBufferMemory: 33554432,
      points: []
    })

    const rules = computed(() => {
      const r = {
        name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
        protocolType: [{ required: true, message: '请选择协议类型', trigger: 'change' }]
      }
      if (form.protocolType === 'OPC_UA') {
        r.opcServerUrl = [{ required: true, message: '请输入OPC UA服务器地址', trigger: 'blur' }]
        r.opcScanInterval = [
          {
            validator: (_rule, val, cb) => {
              if (val == null || val === '') {
                cb()
                return
              }
              const n = Number(val)
              if (!Number.isFinite(n) || n < 100 || n > 600000) {
                cb(new Error('采集周期需在 100～600000 ms 之间'))
              } else {
                cb()
              }
            },
            trigger: 'change'
          }
        ]
      }
      if (form.protocolType === 'MODBUS_TCP') {
        r.modbusHost = [{ required: true, message: '请输入Modbus主机地址', trigger: 'blur' }]
        r.modbusPort = [{ required: true, message: '请输入Modbus端口号', trigger: 'blur' }]
      }
      if (form.kafkaEnabled) {
        r.kafkaBootstrapServers = [{ required: true, message: '请输入Kafka Bootstrap Servers', trigger: 'blur' }]
        r.kafkaTopic = [{ required: true, message: '请输入Kafka Topic', trigger: 'blur' }]
      }
      return r
    })

    const onProtocolChange = (val) => {
      if (val === 'OPC_UA') {
        form.opcServerUrl = 'opc.tcp://localhost:7718'
      } else {
        form.modbusHost = '127.0.0.1'
        form.modbusPort = 502
      }
    }

    const addPoint = () => {
      const point = {
        name: '',
        address: '',
        devId: '',
        nodeId: '',
        scaleFactor: 1.0,
        sortOrder: form.points.length + 1
      }
      if (form.protocolType === 'MODBUS_TCP') {
        point.dataType = 'float'
        point.bitLength = 32
      }
      form.points.push(point)
    }

    const removePoint = (index) => {
      form.points.splice(index, 1)
    }

    const submitForm = async () => {
      if (!formRef.value) return
      try {
        await formRef.value.validate()
      } catch {
        ElMessage.warning('请完善必填项')
        return
      }

      if (form.points.length === 0) {
        ElMessage.warning('请至少添加一个采集点位')
        return
      }

      submitting.value = true
      try {
        const data = { ...form }
        data.points = form.points.map((p, i) => ({ ...p, sortOrder: i + 1 }))

        let res
        if (isEdit.value) {
          res = await axios.put(`${API_BASE}/tasks/${route.params.id}`, data)
        } else {
          res = await axios.post(`${API_BASE}/tasks`, data)
        }

        if (res.data.success) {
          ElMessage.success(isEdit.value ? '任务更新成功' : '任务创建成功')
          router.push('/')
        } else {
          ElMessage.error(res.data.message || '保存失败')
        }
      } catch (e) {
        ElMessage.error('保存任务失败')
      } finally {
        submitting.value = false
      }
    }

    const goBack = () => {
      router.push('/')
    }

    const downloadTemplate = async () => {
          try {
            let protocol = form.protocolType || 'OPC_UA'
            // 转换为后端期望的协议名称
            if (protocol === 'MODBUS_TCP') {
              protocol = 'MODBUS'
            }
            const res = await axios.get(API_BASE + '/tasks/template?protocol=' + protocol, {
              responseType: 'blob'
            })
            const url = window.URL.createObjectURL(new Blob([res.data]))
            const link = document.createElement('a')
            link.href = url
            link.setAttribute('download', protocol + '_点位导入模板.xlsx')
            document.body.appendChild(link)
            link.click()
            document.body.removeChild(link)
          } catch (e) {
            ElMessage.error('下载模板失败')
          }
        }

    const handleExcelUpload = async (options) => {
      const fd = new FormData()
      fd.append('file', options.file)
      try {
        // 统一使用预览模式解析，然后在前端合并，避免加载已删除的点位
        const res = await axios.post(`${API_BASE}/tasks/import/preview`, fd, {
          headers: { 'Content-Type': 'multipart/form-data' }
        })
        if (res.data.success && Array.isArray(res.data.data)) {
          const start = form.points.length
          res.data.data.forEach((p, i) => {
            const point = {
              name: p.name || '',
              address: p.address || '',
              devId: p.devId || '',
              nodeId: p.nodeId || '',
              scaleFactor: p.scaleFactor !== undefined && p.scaleFactor !== null ? p.scaleFactor : 1.0,
              sortOrder: start + i + 1
            }
            if (form.protocolType === 'MODBUS_TCP') {
              point.dataType = p.dataType || 'float'
              point.bitLength = p.bitLength || 32
            }
            form.points.push(point)
          })
          ElMessage.success(res.data.message || '已合并到列表')
        } else {
          ElMessage.error(res.data.message || '解析失败')
        }
      } catch (e) {
        ElMessage.error('上传失败')
      }
    }

    const loadTask = async () => {
      const id = route.params.id
      if (!id) return
      isEdit.value = true
      loading.value = true
      try {
        const res = await axios.get(`${API_BASE}/tasks/${id}`)
        if (res.data.success) {
          const task = res.data.data
          Object.keys(form).forEach(key => {
            if (key !== 'points' && task[key] !== undefined && task[key] !== null) {
              form[key] = task[key]
            }
          })
          if (task.points) {
            form.points = task.points.map(p => ({ ...p }))
          }
        }
      } catch (e) {
        ElMessage.error('加载任务信息失败')
      } finally {
        loading.value = false
      }
    }

    onMounted(loadTask)

    return {
      formRef, form, rules, loading, submitting, isEdit,
      onProtocolChange, addPoint, removePoint, submitForm, goBack,
      downloadTemplate, handleExcelUpload
    }
  }
}
</script>

<style scoped>
.task-config {
  padding: 20px;
  max-width: 1080px;
}
.page-header {
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
  color: #303133;
}
.import-hint {
  font-size: 12px;
  color: #909399;
}
.field-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>