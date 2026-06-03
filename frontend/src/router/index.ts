import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/Register.vue'),
    },
    {
      path: '/onboarding',
      name: 'Onboarding',
      component: () => import('@/views/Onboarding.vue'),
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/Dashboard.vue'),
        },
        {
          path: 'servers',
          name: 'ServerList',
          component: () => import('@/views/ServerList.vue'),
        },
        {
          path: 'snapshots',
          name: 'Snapshots',
          component: () => import('@/views/Snapshots.vue'),
        },
        {
          path: 'snapshots/timeline',
          name: 'Timeline',
          component: () => import('@/views/Timeline.vue'),
        },
        {
          path: 'snapshots/diff',
          name: 'SnapshotDiff',
          component: () => import('@/views/SnapshotDiff.vue'),
        },
        {
          path: 'recovery',
          name: 'Recovery',
          component: () => import('@/views/Recovery.vue'),
        },
        {
          path: 'servers/:id',
          name: 'ServerDetail',
          component: () => import('@/views/ServerDetail.vue'),
        },
        {
          path: 'storage',
          name: 'Storage',
          component: () => import('@/views/Storage.vue'),
        },
        {
          path: 'team',
          name: 'Team',
          component: () => import('@/views/Team.vue'),
        },
        {
          path: 'settings',
          name: 'Settings',
          component: () => import('@/views/Settings.vue'),
        },
        {
          path: 'risk',
          name: 'RiskCenter',
          component: () => import('@/views/RiskCenter.vue'),
        },
        {
          path: 'alerts',
          name: 'Alerts',
          component: () => import('@/views/Alerts.vue'),
        },
        {
          path: 'ai-insights',
          name: 'AiInsights',
          component: () => import('@/views/AiInsights.vue'),
        },
      ],
    },
  ],
})

const publicPaths = ['/login', '/register', '/onboarding']

router.beforeEach((to) => {
  const token = localStorage.getItem('cv_token')
  if (!token && !publicPaths.includes(to.path)) {
    return '/login'
  }
  if (token && (to.path === '/login' || to.path === '/register' || to.path === '/')) {
    return '/dashboard'
  }
})

export default router
