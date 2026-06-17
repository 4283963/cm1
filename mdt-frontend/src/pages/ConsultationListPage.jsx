import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Card,
  List,
  Tag,
  Button,
  Space,
  Typography,
  Tabs,
  Empty,
  Avatar,
} from 'antd'
import {
  VideoCameraOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  UserOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import { consultationApi } from '../services/api'
import dayjs from 'dayjs'

const { Title, Text } = Typography

function ConsultationListPage() {
  const navigate = useNavigate()
  const [initiatedList, setInitiatedList] = useState([])
  const [invitedList, setInvitedList] = useState([])
  const [loading, setLoading] = useState(false)
  const [currentUser, setCurrentUser] = useState({ id: 1, name: '张医生' })

  useEffect(() => {
    const userId = localStorage.getItem('userId')
    const userName = localStorage.getItem('userName')
    if (userId) {
      setCurrentUser({ id: Number(userId), name: userName || '医生' })
    }
    loadData()
  }, [])

  const loadData = async () => {
    setLoading(true)
    try {
      const [initiated, invited] = await Promise.all([
        consultationApi.getMyInitiated(currentUser.id),
        consultationApi.getMyInvitations(currentUser.id),
      ])
      setInitiatedList(initiated || [])
      setInvitedList(invited || [])
    } catch (error) {
      console.error('加载会诊列表失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const getStatusConfig = (status) => {
    const map = {
      PENDING: { color: 'blue', text: '待开始', icon: <ClockCircleOutlined /> },
      IN_PROGRESS: { color: 'green', text: '进行中', icon: <PlayCircleOutlined /> },
      COMPLETED: { color: 'default', text: '已完成', icon: <CheckCircleOutlined /> },
      CANCELLED: { color: 'red', text: '已取消', icon: <ClockCircleOutlined /> },
    }
    return map[status] || map.PENDING
  }

  const renderConsultationCard = (item, isInitiator) => {
    const statusCfg = getStatusConfig(item.status)
    return (
      <Card
        key={item.id}
        className="consultation-card"
        hoverable
        onClick={() => navigate(`/room/${item.id}`)}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div style={{ flex: 1 }}>
            <Space style={{ marginBottom: 8 }}>
              <Tag color={statusCfg.color} icon={statusCfg.icon}>
                {statusCfg.text}
              </Tag>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {item.consultationNo}
              </Text>
            </Space>
            <Title level={5} style={{ marginBottom: 8 }}>
              {item.title}
            </Title>
            <Space size="large" style={{ marginBottom: 12 }}>
              <Text type="secondary">
                <UserOutlined style={{ marginRight: 4 }} />
                {isInitiator ? `发起: ${item.initiatorName}` : `发起: ${item.initiatorName}`}
              </Text>
              {item.patientName && (
                <Text type="secondary">
                  患者: {item.patientName}
                </Text>
              )}
            </Space>
            <div style={{ marginBottom: 8 }}>
              {item.experts?.slice(0, 3).map((exp, idx) => (
                <Avatar
                  key={idx}
                  size={28}
                  style={{
                    backgroundColor: ['#1890ff', '#722ed1', '#13c2c2'][idx % 3],
                    marginLeft: idx > 0 ? -8 : 0,
                    border: '2px solid #fff',
                  }}
                >
                  {exp.expertName?.charAt(0)}
                </Avatar>
              ))}
              {item.experts?.length > 3 && (
                <span style={{ marginLeft: 8, color: '#999', fontSize: 12 }}>
                  等 {item.experts.length} 位专家
                </span>
              )}
            </div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              创建于 {dayjs(item.createdAt).format('YYYY-MM-DD HH:mm')}
            </Text>
          </div>
          <Button type="primary" size="small" icon={<VideoCameraOutlined />}>
            {item.status === 'IN_PROGRESS' ? '进入会诊' : '查看详情'}
          </Button>
        </div>
      </Card>
    )
  }

  const tabItems = [
    {
      key: 'initiated',
      label: `我发起的 (${initiatedList.length})`,
      children:
        initiatedList.length === 0 ? (
          <Empty description="暂无发起的会诊">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/create')}>
              发起会诊
            </Button>
          </Empty>
        ) : (
          <List
            dataSource={initiatedList}
            renderItem={(item) => renderConsultationCard(item, true)}
          />
        ),
    },
    {
      key: 'invited',
      label: `我参与的 (${invitedList.length})`,
      children:
        invitedList.length === 0 ? (
          <Empty description="暂无受邀的会诊" />
        ) : (
          <List
            dataSource={invitedList}
            renderItem={(item) => renderConsultationCard(item, false)}
          />
        ),
    },
  ]

  return (
    <div className="page-container">
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space size="middle">
          <VideoCameraOutlined style={{ fontSize: 32, color: '#1890ff' }} />
          <div>
            <h1 className="page-title">MDT专家联合会诊</h1>
            <p className="page-subtitle">跨科室多学科专家协同诊疗平台</p>
          </div>
        </Space>
        <Button type="primary" size="large" icon={<PlusOutlined />} onClick={() => navigate('/create')}>
          发起会诊
        </Button>
      </div>

      <Card bordered={false}>
        <Tabs defaultActiveKey="initiated" items={tabItems} />
      </Card>
    </div>
  )
}

export default ConsultationListPage
