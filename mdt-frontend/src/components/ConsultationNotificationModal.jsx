import { useState, useEffect } from 'react'
import { Modal, Button, Tag, Typography, Space } from 'antd'
import { VideoCameraOutlined, ClockCircleOutlined, UserOutlined } from '@ant-design/icons'
import wsService from '../services/websocket'
import dayjs from 'dayjs'

const { Title, Text, Paragraph } = Typography

function ConsultationNotificationModal() {
  const [visible, setVisible] = useState(false)
  const [notification, setNotification] = useState(null)

  useEffect(() => {
    const userId = localStorage.getItem('userId')
    if (!userId) return

    const handleInvite = (data) => {
      console.log('收到会诊邀请:', data)
      setNotification(data)
      setVisible(true)

      if (Notification.permission === 'granted') {
        new Notification('MDT会诊邀请', {
          body: `${data.initiatorName} 邀请您参加「${data.title}」会诊`,
          icon: '/favicon.ico',
        })
      }
    }

    if (wsService.isConnected()) {
      wsService.subscribeToUserInvite(handleInvite)
    } else {
      wsService.connect(userId).then(() => {
        wsService.subscribeToUserInvite(handleInvite)
      }).catch(() => {})
    }

    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission()
    }

    return () => {
    }
  }, [])

  const handleAccept = () => {
    if (notification) {
      window.location.href = `/room/${notification.consultationId}?userId=${localStorage.getItem('userId')}`
    }
    setVisible(false)
  }

  const handleDecline = () => {
    setVisible(false)
    setNotification(null)
  }

  return (
    <Modal
      open={visible}
      title={
        <Space>
          <VideoCameraOutlined style={{ color: '#1890ff', fontSize: 20 }} />
          <span style={{ fontSize: 18, fontWeight: 600 }}>MDT会诊邀请</span>
          <Tag color="red" style={{ marginLeft: 8 }}>
            新邀请
          </Tag>
        </Space>
      }
      onCancel={handleDecline}
      footer={[
        <Button key="decline" size="large" onClick={handleDecline}>
          拒绝
        </Button>,
        <Button key="accept" type="primary" size="large" onClick={handleAccept}>
          立即加入
        </Button>,
      ]}
      width={480}
      centered
      closable={false}
      maskClosable={false}
    >
      {notification && (
        <div style={{ padding: '16px 0' }}>
          <Title level={4} style={{ marginBottom: 16 }}>
            {notification.title}
          </Title>

          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <UserOutlined style={{ color: '#888' }} />
              <Text>发起人：{notification.initiatorName}</Text>
            </div>

            {notification.patientName && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <UserOutlined style={{ color: '#888' }} />
                <Text>患者：{notification.patientName}</Text>
              </div>
            )}

            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <ClockCircleOutlined style={{ color: '#888' }} />
              <Text>
                邀请时间：{dayjs(notification.createdAt).format('YYYY-MM-DD HH:mm')}
              </Text>
            </div>

            <Paragraph style={{ marginTop: 16, color: '#666' }}>
              {notification.message}
            </Paragraph>
          </Space>
        </div>
      )}
    </Modal>
  )
}

export default ConsultationNotificationModal
