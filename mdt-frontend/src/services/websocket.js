import SockJS from 'sockjs-client'
import Stomp from 'stompjs'

class WebSocketService {
  constructor() {
    this.stompClient = null
    this.connected = false
    this.subscriptions = {}
    this.userId = null
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
  }

  connect(userId) {
    return new Promise((resolve, reject) => {
      this.userId = userId

      const socket = new SockJS('/ws/mdt')
      this.stompClient = Stomp.over(socket)

      this.stompClient.connect(
        { userId: String(userId) },
        (frame) => {
          console.log('WebSocket 连接成功')
          this.connected = true
          this.reconnectAttempts = 0
          resolve(frame)
        },
        (error) => {
          console.error('WebSocket 连接失败:', error)
          this.connected = false
          this.handleReconnect()
          reject(error)
        }
      )

      socket.onclose = () => {
        console.warn('WebSocket 连接关闭')
        this.connected = false
        this.handleReconnect()
      }
    })
  }

  handleReconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++
      console.log(`正在尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`)
      setTimeout(() => {
        if (this.userId) {
          this.connect(this.userId).catch(() => {})
        }
      }, 3000 * this.reconnectAttempts)
    }
  }

  subscribe(destination, callback) {
    if (!this.stompClient || !this.connected) {
      console.warn('WebSocket 未连接，无法订阅:', destination)
      return null
    }

    const subscription = this.stompClient.subscribe(destination, (message) => {
      try {
        const body = JSON.parse(message.body)
        callback(body)
      } catch (e) {
        callback(message.body)
      }
    })

    this.subscriptions[destination] = subscription
    return subscription
  }

  subscribeToUserInvite(callback) {
    if (!this.userId) return null
    const destination = `/user/${this.userId}/queue/consultation/invite`
    return this.subscribe(destination, callback)
  }

  subscribeToConsultationStatus(consultationId, callback) {
    const destination = `/topic/consultation/${consultationId}/status`
    return this.subscribe(destination, callback)
  }

  subscribeToRoom(roomId, callback) {
    const destination = `/topic/room/${roomId}`
    return this.subscribe(destination, callback)
  }

  subscribeToConsultationEnded(consultationId, callback) {
    const destination = `/topic/consultation/${consultationId}/ended`
    return this.subscribe(destination, callback)
  }

  send(destination, body) {
    if (!this.stompClient || !this.connected) {
      console.warn('WebSocket 未连接，无法发送消息')
      return false
    }

    this.stompClient.send(destination, {}, JSON.stringify(body))
    return true
  }

  disconnect() {
    if (this.stompClient) {
      this.stompClient.disconnect()
      this.stompClient = null
    }
    this.connected = false
    this.subscriptions = {}
    this.userId = null
    console.log('WebSocket 已断开')
  }

  isConnected() {
    return this.connected
  }
}

const wsService = new WebSocketService()
export default wsService
