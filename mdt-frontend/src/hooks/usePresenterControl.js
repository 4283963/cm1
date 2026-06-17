import { useState, useCallback, useEffect } from 'react'
import { consultationApi } from '../services/api'
import wsService from '../services/websocket'
import { message } from 'antd'

export function usePresenterControl(consultationId, userId, userName, roomId) {
  const [presenterId, setPresenterId] = useState(null)
  const [presenterName, setPresenterName] = useState(null)
  const [initiatorId, setInitiatorId] = useState(null)
  const [currentPage, setCurrentPage] = useState(1)
  const [currentPresentationId, setCurrentPresentationId] = useState(null)
  const [controlHistory, setControlHistory] = useState([])
  const [loading, setLoading] = useState(false)

  const isPresenter = presenterId === userId
  const isInitiator = initiatorId === userId
  const hasControl = isPresenter || isInitiator

  const loadControlState = useCallback(async () => {
    if (!consultationId) return
    setLoading(true)
    try {
      const state = await consultationApi.getControlState(consultationId)
      setPresenterId(state.presenterId)
      setPresenterName(state.presenterName)
      setInitiatorId(state.initiatorId)
      setCurrentPage(state.currentPageNumber || 1)
      setCurrentPresentationId(state.currentPresentationId || null)
    } catch (error) {
      console.error('加载控制状态失败:', error)
    } finally {
      setLoading(false)
    }
  }, [consultationId])

  useEffect(() => {
    if (consultationId) {
      loadControlState()
    }
  }, [consultationId, loadControlState])

  useEffect(() => {
    if (!roomId || !consultationId) return

    const handleRoomMessage = (data) => {
      if (!data || !data.type) return

      switch (data.type) {
        case 'CONTROL_EVENT': {
          const event = data.event
          if (!event) return
          setControlHistory((prev) => [...prev.slice(-50), event])
          if (event.eventType === 'PAGE_FLIP' && event.payload?.pageNumber) {
            setCurrentPage(Number(event.payload.pageNumber))
          }
          if (event.eventType === 'PRESENTATION_SWITCH' && event.payload?.presentationId) {
            setCurrentPresentationId(event.payload.presentationId)
          }
          break
        }
        case 'PRESENTER_TRANSFERRED': {
          setPresenterId(data.toUserId)
          setPresenterName(data.toUserName)
          message.info(`主讲人已变更为：${data.toUserName}`)
          break
        }
        default:
          break
      }
    }

    if (wsService.isConnected() && roomId) {
      wsService.subscribeToRoom(roomId, handleRoomMessage)
    }

    return () => {}
  }, [roomId, consultationId])

  const flipPage = useCallback(
    async (pageNumber) => {
      if (!hasControl) {
        message.warning('您没有翻页控制权')
        return false
      }
      try {
        await consultationApi.broadcastControlEvent(
          consultationId, userId, 'PAGE_FLIP', { pageNumber })
        setCurrentPage(pageNumber)
        return true
      } catch (error) {
        message.error('翻页失败：' + (error.response?.data?.message || error.message))
        return false
      }
    },
    [consultationId, userId, hasControl]
  )

  const switchPresentation = useCallback(
    async (presentationId) => {
      if (!hasControl) {
        message.warning('您没有课件切换控制权')
        return false
      }
      try {
        await consultationApi.broadcastControlEvent(
          consultationId, userId, 'PRESENTATION_SWITCH', { presentationId })
        setCurrentPresentationId(presentationId)
        return true
      } catch (error) {
        message.error('切换课件失败')
        return false
      }
    },
    [consultationId, userId, hasControl]
  )

  const sendPointerMove = useCallback(
    async (x, y) => {
      if (!hasControl) return false
      try {
        await consultationApi.broadcastControlEvent(
          consultationId, userId, 'POINTER_MOVE', { x, y })
        return true
      } catch (error) {
        return false
      }
    },
    [consultationId, userId, hasControl]
  )

  const sendCustomEvent = useCallback(
    async (eventType, payload) => {
      if (!hasControl) {
        message.warning('您没有控制权')
        return false
      }
      try {
        await consultationApi.broadcastControlEvent(consultationId, userId, eventType, payload)
        return true
      } catch (error) {
        message.error('发送控制事件失败')
        return false
      }
    },
    [consultationId, userId, hasControl]
  )

  const transferPresenter = useCallback(
    async (targetUserId) => {
      if (!hasControl) {
        message.warning('您无权移交主讲人权限')
        return false
      }
      try {
        await consultationApi.transferPresenter(consultationId, userId, targetUserId)
        return true
      } catch (error) {
        message.error('移交失败：' + (error.response?.data?.message || error.message))
        return false
      }
    },
    [consultationId, userId, hasControl]
  )

  return {
    presenterId,
    presenterName,
    initiatorId,
    currentPage,
    currentPresentationId,
    controlHistory,
    isPresenter,
    isInitiator,
    hasControl,
    loading,
    flipPage,
    switchPresentation,
    sendPointerMove,
    sendCustomEvent,
    transferPresenter,
    loadControlState,
    setPresenterId,
    setPresenterName,
  }
}

export default usePresenterControl
