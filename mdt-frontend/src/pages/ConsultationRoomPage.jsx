import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import {
  Space,
  Typography,
  Tag,
  Button,
  Drawer,
  List,
  Avatar,
  Badge,
  message,
  Modal,
  Tooltip,
  Row,
  Col,
  Divider,
} from 'antd'
import {
  VideoCameraOutlined,
  AudioOutlined,
  AudioMutedOutlined,
  VideoCameraFilled,
  UserOutlined,
  TeamOutlined,
  ArrowLeftOutlined,
  CaretRightOutlined,
  StopOutlined,
  InfoCircleOutlined,
  FullscreenOutlined,
  PhoneFilled,
  PhoneOutlined,
  SwapOutlined,
  CrownOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import VideoPlayer from '../components/VideoPlayer'
import UrgentInviteModal from '../components/UrgentInviteModal'
import TransferRoleModal from '../components/TransferRoleModal'
import { consultationApi } from '../services/api'
import wsService from '../services/websocket'
import useWebRTC from '../hooks/useWebRTC'
import { usePresenterControl } from '../hooks/usePresenterControl'
import dayjs from 'dayjs'

const { Title, Text, Paragraph } = Typography

function ConsultationRoomPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [consultation, setConsultation] = useState(null)
  const [loading, setLoading] = useState(true)
  const [expertDrawerVisible, setExpertDrawerVisible] = useState(false)
  const [joined, setJoined] = useState(false)
  const [isInitiator, setIsInitiator] = useState(false)

  const [urgentInviteOpen, setUrgentInviteOpen] = useState(false)
  const [transferRoleOpen, setTransferRoleOpen] = useState(false)

  const userId = Number(searchParams.get('userId') || localStorage.getItem('userId') || 1)
  const userName = searchParams.get('userName') || localStorage.getItem('userName') || '医生'

  const {
    localStream,
    remoteStreams,
    isAudioEnabled,
    isVideoEnabled,
    joinRoom,
    leaveRoom,
    toggleAudio,
    toggleVideo,
    handleOffer,
    handleAnswer,
    handleIceCandidate,
    createOffer,
  } = useWebRTC(consultation?.roomId, userId, userName)

  const {
    presenterId,
    presenterName,
    initiatorId,
    currentPage,
    currentPresentationId,
    isPresenter,
    hasControl,
    flipPage,
    switchPresentation,
    loadControlState,
    setPresenterId,
    setPresenterName,
  } = usePresenterControl(
    consultation?.id, userId, userName, consultation?.roomId)

  const remoteStreamsRef = useRef(remoteStreams)
  useEffect(() => {
    remoteStreamsRef.current = remoteStreams
  }, [remoteStreams])

  useEffect(() => {
    if (!userId) {
      message.error('请先登录')
      navigate('/')
      return
    }
    localStorage.setItem('userId', userId)
    localStorage.setItem('userName', userName)

    loadConsultationDetail()
    setupWebSocket()

    return () => {
      leaveRoom()
    }
  }, [id, userId])

  const loadConsultationDetail = async () => {
    try {
      const data = await consultationApi.getDetail(id)
      setConsultation(data)
      setIsInitiator(data.initiatorId === userId)

      if (data.status === 'IN_PROGRESS') {
        handleJoin()
      }
    } catch (error) {
      message.error('加载会诊信息失败')
    } finally {
      setLoading(false)
    }
  }

  const setupWebSocket = async () => {
    try {
      if (!wsService.isConnected()) {
        await wsService.connect(userId)
      }

      wsService.subscribeToConsultationStatus(id, (data) => {
        console.log('专家状态更新:', data)
        loadConsultationDetail()
      })

      wsService.subscribeToConsultationEnded(id, () => {
        message.info('会诊已结束')
        loadConsultationDetail()
      })

      if (consultation?.roomId) {
        wsService.subscribeToRoom(consultation.roomId, (msg) => {
          handleRoomMessage(msg)
        })
      }
    } catch (error) {
      console.error('WebSocket 连接失败:', error)
    }
  }

  const handleRoomMessage = async (message) => {
    console.log('收到房间消息:', message)

    switch (message.type) {
      case 'OFFER':
        if (message.toUserId === userId && message.fromUserId !== userId) {
          const answer = await handleOffer(message.fromUserId, message.offer)
          if (consultation?.roomId) {
            wsService.send(`/topic/room/${consultation.roomId}`, {
              type: 'ANSWER',
              fromUserId: userId,
              toUserId: message.fromUserId,
              answer,
            })
          }
        }
        break

      case 'ANSWER':
        if (message.toUserId === userId) {
          handleAnswer(message.fromUserId, message.answer)
        }
        break

      case 'ICE_CANDIDATE':
        if (message.toUserId === userId) {
          handleIceCandidate(message.fromUserId, message.candidate)
        }
        break

      case 'USER_JOINED':
        if (message.userId !== userId && joined) {
          await connectToNewUser(message.userId, message.userName)
        }
        break

      case 'EXPERT_INVITED':
        message.success(
          `${message.userName || '新专家'} 已被邀请加入会诊`
        )
        loadConsultationDetail()
        break

      case 'PRESENTER_TRANSFERRED':
        setPresenterId(message.toUserId)
        setPresenterName(message.toUserName)
        if (message.toUserId === userId) {
          message.success('您已成为主讲人，可以控制课件和翻页')
        } else {
          message.info(`主讲人已变更为：${message.toUserName}`)
        }
        break

      case 'CONTROL_EVENT':
        if (message.event) {
          const event = message.event
          if (event.eventType === 'PAGE_FLIP') {
            message.info(
              `${event.operatorName} 翻到第 ${event.payload?.pageNumber} 页`
            )
          }
        }
        break

      default:
        break
    }
  }

  const connectToNewUser = async (remoteUserId, remoteUserName) => {
    console.log('连接到新用户:', remoteUserId, remoteUserName)
    try {
      const offer = await createOffer(remoteUserId)
      if (consultation?.roomId) {
        wsService.send(`/topic/room/${consultation.roomId}`, {
          type: 'OFFER',
          fromUserId: userId,
          toUserId: remoteUserId,
          offer,
        })
      }
    } catch (error) {
      console.error('创建 offer 失败:', error)
    }
  }

  const handleJoin = async () => {
    try {
      await joinRoom()
      setJoined(true)

      if (consultation?.experts) {
        const acceptedExperts = consultation.experts.filter(
          (e) => e.status === 'ACCEPTED' || e.status === 'JOINED'
        )
        for (const expert of acceptedExperts) {
          if (expert.expertId !== userId) {
            await connectToNewUser(expert.expertId, expert.expertName)
          }
        }
      }

      if (wsService.isConnected() && consultation?.roomId) {
        wsService.send(`/topic/room/${consultation.roomId}`, {
          type: 'USER_JOINED',
          userId,
          userName,
        })
      }

      message.success('已加入会诊')
    } catch (error) {
      message.error('加入会诊失败: ' + error.message)
    }
  }

  const handleLeave = () => {
    Modal.confirm({
      title: '确认离开会诊？',
      content: '离开后可以重新加入',
      onOk: () => {
        leaveRoom()
        setJoined(false)
        navigate('/')
      },
    })
  }

  const handleStart = async () => {
    try {
      await consultationApi.start(id, userId)
      message.success('会诊已开始')
      loadConsultationDetail()
    } catch (error) {
      message.error('开始会诊失败')
    }
  }

  const handleEnd = () => {
    Modal.confirm({
      title: '确认结束会诊？',
      content: '结束后所有参与者将被移出房间',
      okText: '结束会诊',
      okType: 'danger',
      onOk: async () => {
        try {
          await consultationApi.end(id, userId)
          message.success('会诊已结束')
          leaveRoom()
          setJoined(false)
          loadConsultationDetail()
        } catch (error) {
          message.error('结束会诊失败')
        }
      },
    })
  }

  const handleUrgentInvited = () => {
    loadConsultationDetail()
  }

  const getStatusConfig = (status) => {
    const map = {
      PENDING: { color: 'blue', text: '待开始' },
      IN_PROGRESS: { color: 'green', text: '进行中' },
      COMPLETED: { color: 'default', text: '已完成' },
      CANCELLED: { color: 'red', text: '已取消' },
    }
    return map[status] || map.PENDING
  }

  const getExpertStatusColor = (status) => {
    const map = {
      INVITED: 'default',
      ACCEPTED: 'blue',
      DECLINED: 'red',
      JOINED: 'green',
      LEFT: 'default',
    }
    return map[status] || 'default'
  }

  const getExpertStatusText = (status) => {
    const map = {
      INVITED: '已邀请',
      ACCEPTED: '已接受',
      DECLINED: '已拒绝',
      JOINED: '已加入',
      LEFT: '已离开',
    }
    return map[status] || status
  }

  const allVideos = () => {
    const videos = []

    if (joined && localStream) {
      videos.push({
        userId,
        userName: `${userName}（我）`,
        stream: localStream,
        isLocal: true,
        role: isPresenter ? '主讲人' : isInitiator ? '主持人' : '专家',
        isPresenter,
      })
    }

    Object.values(remoteStreams).forEach((remote) => {
      videos.push({
        userId: remote.userId,
        userName: remote.userName,
        stream: remote.stream,
        isLocal: false,
        role: presenterId === remote.userId ? '主讲人' : '专家',
        isPresenter: presenterId === remote.userId,
      })
    })

    if (!joined && consultation) {
      consultation.experts?.forEach((exp) => {
        if (exp.status === 'ACCEPTED' || exp.status === 'JOINED') {
          videos.push({
            userId: exp.expertId,
            userName: exp.expertName,
            stream: null,
            isLocal: false,
            role: presenterId === exp.expertId ? '主讲人' : '专家',
            isPresenter: presenterId === exp.expertId,
            status: exp.status,
          })
        }
      })
    }

    return videos
  }

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <div>加载中...</div>
      </div>
    )
  }

  const statusCfg = getStatusConfig(consultation?.status)
  const videos = allVideos()

  return (
    <div style={{ minHeight: '100vh', background: '#1a1a2e' }}>
      <div
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          height: 60,
          background: 'rgba(0, 0, 0, 0.8)',
          color: '#fff',
          display: 'flex',
          alignItems: 'center',
          padding: '0 24px',
          zIndex: 100,
          justifyContent: 'space-between',
        }}
      >
        <Space size="large">
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            style={{ color: '#fff' }}
            onClick={() => navigate('/')}
          >
            返回
          </Button>
          <div>
            <Title level={4} style={{ color: '#fff', margin: 0 }}>
              {consultation?.title}
            </Title>
            <Space size="middle">
              <Tag color={statusCfg.color}>{statusCfg.text}</Tag>
              <Text style={{ color: '#999', fontSize: 12 }}>
                {consultation?.consultationNo}
              </Text>
              {presenterName && (
                <Tag color="gold" icon={<CrownOutlined />}>
                  主讲：{presenterName}
                </Tag>
              )}
            </Space>
          </div>
        </Space>

        <Space size="middle">
          {consultation?.status === 'IN_PROGRESS' && (
            <Space size="middle">
              <Tooltip title="紧急呼叫外部专家">
                <Button
                  danger
                  icon={<PhoneOutlined />}
                  onClick={() => setUrgentInviteOpen(true)}
                >
                  紧急呼叫
                </Button>
              </Tooltip>
              {hasControl && consultation?.experts?.length > 1 && (
                <Tooltip title="移交主讲人权限">
                  <Button icon={<SwapOutlined />} onClick={() => setTransferRoleOpen(true)}>
                    移交主讲人
                  </Button>
                </Tooltip>
              )}
            </Space>
          )}

          <Badge count={videos.length} title="在线人数">
            <Button
              type="text"
              icon={<TeamOutlined />}
              style={{ color: '#fff' }}
              onClick={() => setExpertDrawerVisible(true)}
            >
              参与者 ({consultation?.experts?.length || 0})
            </Button>
          </Badge>
        </Space>
      </div>

      <div style={{ padding: '80px 24px 120px' }}>
        {!joined ? (
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              minHeight: '60vh',
              color: '#fff',
            }}
          >
            <VideoCameraOutlined style={{ fontSize: 80, color: '#1890ff', marginBottom: 24 }} />
            <Title level={3} style={{ color: '#fff', marginBottom: 16 }}>
              {consultation?.title}
            </Title>
            <Text style={{ color: '#999', marginBottom: 32, fontSize: 16 }}>
              主持人：{consultation?.initiatorName}
            </Text>
            <Space size="large">
              <Button type="primary" size="large" icon={<CaretRightOutlined />} onClick={handleJoin}>
                加入会诊
              </Button>
              {isInitiator && consultation?.status === 'PENDING' && (
                <Button size="large" onClick={handleStart}>
                  开始会诊
                </Button>
              )}
            </Space>
          </div>
        ) : (
          <div className="room-grid">
            {videos.length === 0 ? (
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'center',
                  alignItems: 'center',
                  height: 400,
                  color: '#666',
                  gridColumn: '1 / -1',
                }}
              >
                  <div style={{ textAlign: 'center' }}>
                    <UserOutlined style={{ fontSize: 64, marginBottom: 16 }} />
                    <div>暂无参与者加入</div>
                  </div>
                </div>
              ) : (
                videos.map((video, index) => (
                  <div key={video.userId} className={index === 0 ? 'main-video' : ''}>
                    <VideoPlayer
                      stream={video.stream}
                      name={video.userName}
                      role={video.role}
                      muted={video.isLocal}
                      isLocal={video.isLocal}
                      isPresenter={video.isPresenter}
                    />
                  </div>
                ))
              )}
            </div>
          )}
      </div>

      {joined && (
        <div className="control-bar">
          <Tooltip title={isAudioEnabled ? '静音' : '取消静音'}>
            <button
              className={`control-btn mic ${!isAudioEnabled ? 'off' : ''}`}
              onClick={toggleAudio}
            >
              {isAudioEnabled ? <AudioOutlined /> : <AudioMutedOutlined />}
            </button>
          </Tooltip>

          <Tooltip title={isVideoEnabled ? '关闭摄像头' : '打开摄像头'}>
            <button
              className={`control-btn camera ${!isVideoEnabled ? 'off' : ''}`}
              onClick={toggleVideo}
            >
              {isVideoEnabled ? <VideoCameraFilled /> : <VideoCameraOutlined />}
            </button>
          </Tooltip>

          {isInitiator && consultation?.status === 'IN_PROGRESS' && (
            <Tooltip title="结束会诊">
              <button className="control-btn end" onClick={handleEnd}>
                <PhoneFilled style={{ transform: 'rotate(135deg)' }} />
              </button>
            </Tooltip>
          )}

          {!isInitiator && (
            <Tooltip title="离开会诊">
              <button className="control-btn end" onClick={handleLeave}>
                <PhoneFilled style={{ transform: 'rotate(135deg)' }} />
              </button>
            </Tooltip>
          )}

          <Tooltip title="紧急呼叫">
            <button
              className="control-btn"
              style={{ background: hasControl ? '#ff4d4f' : '#333', color: '#fff' }}
              onClick={() => setUrgentInviteOpen(true)}
            >
              <WarningOutlined />
            </button>
          </Tooltip>

          <Tooltip title="参与者列表">
            <button
              className="control-btn"
              style={{ background: '#333', color: '#fff' }}
              onClick={() => setExpertDrawerVisible(true)}
            >
              <TeamOutlined />
            </button>
          </Tooltip>

          <Tooltip title="会议信息">
            <button
              className="control-btn"
              style={{ background: '#333', color: '#fff' }}
            >
              <InfoCircleOutlined />
            </button>
          </Tooltip>
        </div>
      )}

      <Drawer
        title="参与者列表"
        placement="right"
        onClose={() => setExpertDrawerVisible(false)}
        open={expertDrawerVisible}
        width={320}
      >
        <List
          dataSource={consultation?.experts || []}
          renderItem={(expert) => {
            const isCurrentPresenter = expert.expertId === presenterId
            return (
              <List.Item key={expert.expertId}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, width: '100%' }}>
                  <Avatar
                    size={40}
                    style={{ backgroundColor: isCurrentPresenter ? '#ffd700' : '#1890ff' }}
                    icon={<UserOutlined />}
                  />
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 500 }}>
                      {expert.expertName}
                      {expert.expertId === userId && <Tag color="blue" style={{ marginLeft: 8 }}>我</Tag>}
                    </div>
                    <div style={{ fontSize: 12, color: '#999' }}>{expert.departmentName}</div>
                  </div>
                  <Space direction="vertical" align="end" size={4}>
                    {isCurrentPresenter && (
                      <Tag color="gold" icon={<CrownOutlined />}>
                        主讲人
                      </Tag>
                    )}
                    <Tag color={getExpertStatusColor(expert.status)}>
                      {getExpertStatusText(expert.status)}
                    </Tag>
                  </Space>
                  {hasControl && !isCurrentPresenter && expert.status !== 'DECLINED' && (
                      <Button
                      type="link"
                      size="small"
                      icon={<SwapOutlined />}
                      onClick={() => {
                        setTransferRoleOpen(true)
                      }}
                    >
                      移交
                    </Button>
                  )}
                </div>
              </List.Item>
            )
          }}
        />
      </Drawer>

      <UrgentInviteModal
        open={urgentInviteOpen}
        onClose={() => setUrgentInviteOpen(false)}
        consultationId={consultation?.id}
        operatorId={userId}
        onInvited={handleUrgentInvited}
      />

      <TransferRoleModal
        open={transferRoleOpen}
        onClose={() => setTransferRoleOpen(false)}
        consultationId={consultation?.id}
        currentUserId={userId}
        currentPresenterId={presenterId}
        participants={consultation?.experts || []}
        onTransferred={(targetId, targetName) => {
          setPresenterId(targetId)
          setPresenterName(targetName)
        }}
      />
    </div>
  )
}

export default ConsultationRoomPage
