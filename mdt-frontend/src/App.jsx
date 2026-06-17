import { useState, useEffect } from 'react'
import { Routes, Route, useNavigate } from 'react-router-dom'
import { Modal, Form, Select, Button, Card, Typography, Space, Avatar, Tag } from 'antd'
import { UserOutlined, TeamOutlined, VideoCameraOutlined } from '@ant-design/icons'
import ConsultationListPage from './pages/ConsultationListPage'
import CreateConsultationPage from './pages/CreateConsultationPage'
import ConsultationRoomPage from './pages/ConsultationRoomPage'
import ConsultationNotificationModal from './components/ConsultationNotificationModal'
import { consultationApi } from './services/api'
import wsService from './services/websocket'

const { Title, Text } = Typography

function UserSelectModal({ visible, onSelect }) {
  const [form] = Form.useForm()
  const [experts, setExperts] = useState([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    loadExperts()
  }, [])

  const loadExperts = async () => {
    setLoading(true)
    try {
      const data = await consultationApi.getExperts({})
      setExperts(data || [])
    } catch (error) {
      console.error('加载专家列表失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = (values) => {
    const expert = experts.find((e) => e.id === values.userId)
    if (expert) {
      localStorage.setItem('userId', expert.id)
      localStorage.setItem('userName', expert.name)
      onSelect(expert)
    }
  }

  return (
    <Modal
      title={
        <Space>
          <UserOutlined />
          <span>选择身份登录</span>
        </Space>
      }
      open={visible}
      footer={null}
      closable={false}
      maskClosable={false}
      width={480}
    >
      <Form form={form} layout="vertical" onFinish={handleSubmit}>
        <Form.Item
          name="userId"
          label="选择用户身份"
          rules={[{ required: true, message: '请选择一个用户' }]}
        >
          <Select
            placeholder="请选择登录身份"
            size="large"
            loading={loading}
            options={experts.map((e) => ({
              label: `${e.name} - ${e.title || '专家'} (${e.status})`,
              value: e.id,
            }))}
            listHeight={300}
          />
        </Form.Item>

        <div style={{ marginBottom: 16, color: '#999', fontSize: 12 }}>
          💡 提示：选择不同的专家身份可以体验发起会诊和接收邀请的流程
        </div>

        <Form.Item>
          <Button type="primary" htmlType="submit" size="large" block>
            进入系统
          </Button>
        </Form.Item>
      </Form>
    </Modal>
  )
}

function HomePage() {
  const navigate = useNavigate()
  const [userModalVisible, setUserModalVisible] = useState(false)
  const [currentUser, setCurrentUser] = useState(null)

  useEffect(() => {
    const userId = localStorage.getItem('userId')
    const userName = localStorage.getItem('userName')
    if (userId && userName) {
      setCurrentUser({ id: Number(userId), name: userName })
      connectWebSocket(Number(userId))
    } else {
      setUserModalVisible(true)
    }
  }, [])

  const connectWebSocket = async (userId) => {
    try {
      await wsService.connect(userId)
      console.log('WebSocket 连接成功')
    } catch (error) {
      console.warn('WebSocket 连接失败，稍后重试')
    }
  }

  const handleUserSelect = (user) => {
    setCurrentUser(user)
    setUserModalVisible(false)
    connectWebSocket(user.id)
  }

  const handleSwitchUser = () => {
    wsService.disconnect()
    localStorage.removeItem('userId')
    localStorage.removeItem('userName')
    setCurrentUser(null)
    setUserModalVisible(true)
  }

  return (
    <div>
      <ConsultationNotificationModal />
      <UserSelectModal visible={userModalVisible} onSelect={handleUserSelect} />

      {currentUser && (
        <div>
          <div
            style={{
              background: '#fff',
              padding: '12px 24px',
              borderBottom: '1px solid #e8e8e8',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <Space size="large">
              <VideoCameraOutlined style={{ fontSize: 24, color: '#1890ff' }} />
              <Text strong style={{ fontSize: 16 }}>
                MDT专家联合会诊系统
              </Text>
            </Space>
            <Space size="middle">
              <Space>
                <Avatar size={32} style={{ backgroundColor: '#1890ff' }}>
                  {currentUser.name?.charAt(0)}
                </Avatar>
                <Text>{currentUser.name}</Text>
              </Space>
              <Button type="link" onClick={handleSwitchUser}>
                切换身份
              </Button>
            </Space>
          </div>

          <ConsultationListPage />
        </div>
      )}
    </div>
  )
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/create" element={<CreateConsultationPage />} />
      <Route path="/room/:id" element={<ConsultationRoomPage />} />
    </Routes>
  )
}

export default App
