import { useState, useRef, useCallback, useEffect } from 'react'

const ICE_SERVERS = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' },
]

export function useWebRTC(roomId, userId, userName) {
  const [localStream, setLocalStream] = useState(null)
  const [remoteStreams, setRemoteStreams] = useState({})
  const [isAudioEnabled, setIsAudioEnabled] = useState(true)
  const [isVideoEnabled, setIsVideoEnabled] = useState(true)
  const [isConnecting, setIsConnecting] = useState(false)

  const peerConnections = useRef({})
  const localStreamRef = useRef(null)

  const getLocalStream = useCallback(async () => {
    if (localStreamRef.current) {
      return localStreamRef.current
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: true,
        audio: true,
      })
      localStreamRef.current = stream
      setLocalStream(stream)
      return stream
    } catch (error) {
      console.error('获取本地媒体流失败:', error)
      throw error
    }
  }, [])

  const createPeerConnection = useCallback((targetUserId) => {
    if (peerConnections.current[targetUserId]) {
      return peerConnections.current[targetUserId]
    }

    const pc = new RTCPeerConnection({
      iceServers: ICE_SERVERS,
    })

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        console.log(`ICE candidate for ${targetUserId}:`, event.candidate)
      }
    }

    pc.oniceconnectionstatechange = () => {
      console.log(`ICE connection state with ${targetUserId}:`, pc.iceConnectionState)
      if (pc.iceConnectionState === 'disconnected' || pc.iceConnectionState === 'failed') {
        setRemoteStreams((prev) => {
          const newStreams = { ...prev }
          delete newStreams[targetUserId]
          return newStreams
        })
      }
    }

    pc.ontrack = (event) => {
      console.log(`收到远端视频流 from ${targetUserId}`)
      const [stream] = event.streams
      setRemoteStreams((prev) => ({
        ...prev,
        [targetUserId]: {
          stream,
          userId: targetUserId,
          userName: `专家 ${targetUserId}`,
        },
      }))
    }

    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((track) => {
        pc.addTrack(track, localStreamRef.current)
      })
    }

    peerConnections.current[targetUserId] = pc
    return pc
  }, [])

  const createOffer = useCallback(async (targetUserId) => {
    const pc = createPeerConnection(targetUserId)
    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    return offer
  }, [createPeerConnection])

  const handleOffer = useCallback(async (targetUserId, offer) => {
    const pc = createPeerConnection(targetUserId)
    await pc.setRemoteDescription(new RTCSessionDescription(offer))
    const answer = await pc.createAnswer()
    await pc.setLocalDescription(answer)
    return answer
  }, [createPeerConnection])

  const handleAnswer = useCallback(async (targetUserId, answer) => {
    const pc = peerConnections.current[targetUserId]
    if (pc) {
      await pc.setRemoteDescription(new RTCSessionDescription(answer))
    }
  }, [])

  const handleIceCandidate = useCallback((targetUserId, candidate) => {
    const pc = peerConnections.current[targetUserId]
    if (pc && candidate) {
      pc.addIceCandidate(new RTCIceCandidate(candidate))
    }
  }, [])

  const joinRoom = useCallback(async () => {
    setIsConnecting(true)
    try {
      await getLocalStream()
      console.log('加入房间:', roomId)
    } catch (error) {
      console.error('加入房间失败:', error)
      throw error
    } finally {
      setIsConnecting(false)
    }
  }, [roomId, getLocalStream])

  const leaveRoom = useCallback(() => {
    Object.values(peerConnections.current).forEach((pc) => {
      pc.close()
    })
    peerConnections.current = {}

    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((track) => track.stop())
      localStreamRef.current = null
    }
    setLocalStream(null)
    setRemoteStreams({})
    setIsAudioEnabled(true)
    setIsVideoEnabled(true)
    console.log('离开房间:', roomId)
  }, [roomId])

  const toggleAudio = useCallback(() => {
    if (localStreamRef.current) {
      const audioTrack = localStreamRef.current.getAudioTracks()[0]
      if (audioTrack) {
        audioTrack.enabled = !audioTrack.enabled
        setIsAudioEnabled(audioTrack.enabled)
      }
    }
  }, [])

  const toggleVideo = useCallback(() => {
    if (localStreamRef.current) {
      const videoTrack = localStreamRef.current.getVideoTracks()[0]
      if (videoTrack) {
        videoTrack.enabled = !videoTrack.enabled
        setIsVideoEnabled(videoTrack.enabled)
      }
    }
  }, [])

  const addRemoteUser = useCallback(async (remoteUserId, remoteUserName) => {
    console.log('与远端用户建立连接:', remoteUserId)
    const offer = await createOffer(remoteUserId)
    return { offer, remoteUserId, remoteUserName }
  }, [createOffer])

  useEffect(() => {
    return () => {
      leaveRoom()
    }
  }, [leaveRoom])

  return {
    localStream,
    remoteStreams,
    isAudioEnabled,
    isVideoEnabled,
    isConnecting,
    joinRoom,
    leaveRoom,
    toggleAudio,
    toggleVideo,
    createOffer,
    handleOffer,
    handleAnswer,
    handleIceCandidate,
    addRemoteUser,
  }
}

export default useWebRTC
