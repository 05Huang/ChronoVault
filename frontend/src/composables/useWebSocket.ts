import { ref } from 'vue'
import { Client } from '@stomp/stompjs'
// @ts-ignore
import SockJS from 'sockjs-client/dist/sockjs'

interface WebSocketOptions {
  onConnect?: () => void
  onDisconnect?: () => void
  onError?: (error: Error | { headers: Record<string, string> }) => void
}

const WS_URL = import.meta.env.VITE_WS_URL || `${window.location.protocol}//${window.location.host}/ws/events`

function getWsUrlWithToken(): string {
  const token = localStorage.getItem('cv_token')
  if (token) {
    return `${WS_URL}?token=${encodeURIComponent(token)}`
  }
  return WS_URL
}

let stompClient: Client | null = null
const connected = ref(false)
const subscriptions = new Map<string, { unsubscribe: () => void }>()

export function useWebSocket(options: WebSocketOptions = {}) {
  function connect() {
    if (stompClient?.connected) return

    stompClient = new Client({
      webSocketFactory: () => new SockJS(getWsUrlWithToken()),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        connected.value = true
        options.onConnect?.()
      },
      onDisconnect: () => {
        connected.value = false
        options.onDisconnect?.()
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message'])
        options.onError?.(frame)
      },
    })

    stompClient.activate()
  }

  function subscribe<T = any>(topic: string, callback: (data: T) => void) {
    if (!stompClient?.connected) {
      console.warn('WebSocket not connected, queuing subscription:', topic)
      return null
    }

    const sub = stompClient.subscribe(topic, (message) => {
      try {
        const data = JSON.parse(message.body)
        callback(data)
      } catch {
        callback(message.body as any)
      }
    })

    subscriptions.set(topic, sub)
    return sub
  }

  function unsubscribe(topic: string) {
    const sub = subscriptions.get(topic)
    if (sub) {
      sub.unsubscribe()
      subscriptions.delete(topic)
    }
  }

  function disconnect() {
    subscriptions.forEach((sub) => sub.unsubscribe())
    subscriptions.clear()
    stompClient?.deactivate()
    stompClient = null
    connected.value = false
  }

  return {
    connected,
    connect,
    subscribe,
    unsubscribe,
    disconnect,
  }
}

interface TaskEventData {
  id?: number
  taskId?: number
  taskType?: string
  type?: string
  status?: string
  progress?: number
  message?: string
  error?: string
}

interface ServerEventData {
  serverId?: number
  event?: string
  data?: unknown
}

export function subscribeToTask(taskId: number, callback: (data: TaskEventData) => void) {
  const { subscribe, unsubscribe } = useWebSocket()
  const topic = `/topic/tasks/${taskId}`
  subscribe<TaskEventData>(topic, callback)
  return () => unsubscribe(topic)
}

export function subscribeToServerEvents(serverId: number, callback: (data: ServerEventData) => void) {
  const { subscribe, unsubscribe } = useWebSocket()
  const topic = `/topic/servers/${serverId}`
  subscribe<ServerEventData>(topic, callback)
  return () => unsubscribe(topic)
}

export function subscribeToGlobalEvents(callback: (data: Record<string, unknown>) => void) {
  const { subscribe, unsubscribe } = useWebSocket()
  subscribe<Record<string, unknown>>('/topic/events', callback)
  return () => unsubscribe('/topic/events')
}
