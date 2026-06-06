import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useWebSocket } from './useWebSocket'

interface RealtimeEvent {
  type?: string
  id?: number
  taskId?: number
  taskType?: string
  status?: string
  progress?: number
  message?: string
  level?: string
  source?: string
  createdAt?: string
}

interface DashboardRealtimeCallbacks {
  onTaskUpdate?: (event: RealtimeEvent) => void
  onEvent?: (event: RealtimeEvent) => void
  onServerEvent?: (event: Record<string, unknown>) => void
  onConnect?: () => void
  onDisconnect?: () => void
}

/**
 * Composable that subscribes to WebSocket topics relevant to the Dashboard
 * and provides real-time event callbacks for task progress, alerts, and server events.
 */
export function useDashboardRealtime(callbacks: DashboardRealtimeCallbacks = {}) {
  const { connected, connect, subscribe, unsubscribe, disconnect } = useWebSocket()
  const activeTopics = ref<string[]>([])

  function startListening() {
    connect()

    // Subscribe to general events (alerts, system events)
    subscribe<RealtimeEvent>('/topic/events', (event) => {
      if (event.type === 'HEARTBEAT') return // Skip heartbeat noise
      callbacks.onEvent?.(event)
    })
    activeTopics.value.push('/topic/events')

    // Subscribe to task updates (snapshot creation, recovery, etc.)
    subscribe<RealtimeEvent>('/topic/tasks', (event) => {
      callbacks.onTaskUpdate?.(event)
    })
    activeTopics.value.push('/topic/tasks')

    // Subscribe to alert-specific events
    subscribe<RealtimeEvent>('/topic/events/warn', (event) => {
      callbacks.onEvent?.(event)
    })
    activeTopics.value.push('/topic/events/warn')

    subscribe<RealtimeEvent>('/topic/events/err', (event) => {
      callbacks.onEvent?.(event)
    })
    activeTopics.value.push('/topic/events/err')

    // Subscribe to snapshot-specific events
    subscribe<RealtimeEvent>('/topic/events/source/snapshot', (event) => {
      callbacks.onEvent?.(event)
    })
    activeTopics.value.push('/topic/events/source/snapshot')
  }

  function stopListening() {
    for (const topic of activeTopics.value) {
      unsubscribe(topic)
    }
    activeTopics.value = []
  }

  function subscribeToServer(serverId: number) {
    const topic = `/topic/servers/${serverId}`
    subscribe<Record<string, unknown>>(topic, (event) => {
      callbacks.onServerEvent?.(event)
    })
    activeTopics.value.push(topic)
    return () => {
      unsubscribe(topic)
      activeTopics.value = activeTopics.value.filter(t => t !== topic)
    }
  }

  onMounted(() => {
    startListening()
  })

  onBeforeUnmount(() => {
    stopListening()
  })

  return {
    connected,
    subscribeToServer,
    startListening,
    stopListening,
  }
}
