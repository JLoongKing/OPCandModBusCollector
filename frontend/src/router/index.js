import { createRouter, createWebHistory } from 'vue-router'
import TaskList from '../views/TaskList.vue'
import TaskConfig from '../views/TaskConfig.vue'
import TaskDashboard from '../views/TaskDashboard.vue'

const routes = [
  { path: '/', component: TaskList },
  { path: '/task/create', component: TaskConfig },
  { path: '/task/edit/:id', component: TaskConfig },
  { path: '/task/dashboard/:id', component: TaskDashboard }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router