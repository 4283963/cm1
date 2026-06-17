import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Form,
  Input,
  Button,
  Card,
  Checkbox,
  List,
  Avatar,
  Tag,
  Space,
  Typography,
  Row,
  Col,
  message,
} from 'antd'
import {
  TeamOutlined,
  VideoCameraOutlined,
  UserOutlined,
  PlusOutlined,
  SafetyOutlined,
} from '@ant-design/icons'
import { consultationApi } from '../services/api'

const { Title, Text } = Typography
const { TextArea } = Input

function CreateConsultationPage() {
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [departments, setDepartments] = useState([])
  const [experts, setExperts] = useState([])
  const [selectedDeptIds, setSelectedDeptIds] = useState([])
  const [selectedExpertIds, setSelectedExpertIds] = useState([])
  const [currentUser, setCurrentUser] = useState({ id: 1, name: '张医生' })

  useEffect(() => {
    loadDepartments()
    const userId = localStorage.getItem('userId')
    const userName = localStorage.getItem('userName')
    if (userId) {
      setCurrentUser({ id: Number(userId), name: userName || '医生' })
    }
  }, [])

  const loadDepartments = async () => {
    try {
      const data = await consultationApi.getDepartments()
      setDepartments(data)
    } catch (error) {
      console.error('加载科室失败:', error)
    }
  }

  const loadExperts = async (deptIds) => {
    if (deptIds.length === 0) {
      setExperts([])
      return
    }
    try {
      const data = await consultationApi.getExperts({ departmentIds: deptIds.join(',') })
      setExperts(data)
    } catch (error) {
      console.error('加载专家失败:', error)
    }
  }

  const handleDeptChange = (checkedValues) => {
    setSelectedDeptIds(checkedValues)
    loadExperts(checkedValues)
    setSelectedExpertIds([])
  }

  const handleExpertChange = (checkedValues) => {
    setSelectedExpertIds(checkedValues)
  }

  const getStatusTag = (status) => {
    const statusMap = {
      ONLINE: { color: 'green', text: '在线' },
      OFFLINE: { color: 'default', text: '离线' },
      BUSY: { color: 'orange', text: '忙碌' },
    }
    const cfg = statusMap[status] || statusMap.OFFLINE
    return <Tag color={cfg.color}>{cfg.text}</Tag>
  }

  const handleSubmit = async (values) => {
    if (selectedExpertIds.length === 0) {
      message.warning('请至少选择一位专家')
      return
    }

    setLoading(true)
    try {
      const data = await consultationApi.create({
        ...values,
        initiatorId: currentUser.id,
        initiatorName: currentUser.name,
        expertIds: selectedExpertIds,
      })
      message.success('会诊邀请已发送！')
      navigate(`/room/${data.id}`)
    } catch (error) {
      message.error('发起会诊失败: ' + (error.response?.data?.message || error.message))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page-container">
      <div className="page-header">
        <Space size="middle">
          <VideoCameraOutlined style={{ fontSize: 32, color: '#1890ff' }} />
          <div>
            <h1 className="page-title">发起MDT会诊</h1>
            <p className="page-subtitle">邀请多科室专家进行联合会诊</p>
          </div>
        </Space>
      </div>

      <Row gutter={24}>
        <Col xs={24} lg={14}>
          <Card title="会诊信息" bordered={false}>
            <Form
              form={form}
              layout="vertical"
              onFinish={handleSubmit}
              initialValues={{
                title: '',
                patientName: '',
                patientInfo: '',
                description: '',
              }}
            >
              <Form.Item
                name="title"
                label="会诊标题"
                rules={[{ required: true, message: '请输入会诊标题' }]}
              >
                <Input placeholder="请输入会诊标题，如：肺癌多学科会诊" size="large" />
              </Form.Item>

              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item name="patientName" label="患者姓名">
                    <Input placeholder="请输入患者姓名" size="large" />
                  </Form.Item>
                </Col>
              </Row>

              <Form.Item name="patientInfo" label="患者基本信息">
                <TextArea
                  rows={3}
                  placeholder="请简要描述患者的基本情况、病史等"
                  size="large"
                />
              </Form.Item>

              <Form.Item name="description" label="会诊目的与问题">
                <TextArea
                  rows={4}
                  placeholder="请描述本次会诊需要解决的主要问题"
                  size="large"
                />
              </Form.Item>

              <Form.Item>
                <Space size="large">
                  <Button
                    type="primary"
                    size="large"
                    htmlType="submit"
                    loading={loading}
                    icon={<PlusOutlined />}
                  >
                    发起会诊
                  </Button>
                  <Button size="large" onClick={() => navigate(-1)}>
                    取消
                  </Button>
                </Space>
              </Form.Item>
            </Form>
          </Card>
        </Col>

        <Col xs={24} lg={10}>
          <Card
            title={
              <Space>
                <TeamOutlined />
                <span>选择专家</span>
                <Tag color="blue">已选 {selectedExpertIds.length} 人</Tag>
              </Space>
            }
            bordered={false}
            style={{ position: 'sticky', top: 24 }}
          >
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ marginBottom: 8, display: 'block' }}>
                按科室筛选：
              </Text>
              <Checkbox.Group
                options={departments.map((d) => ({
                  label: d.name,
                  value: d.id,
                }))}
                value={selectedDeptIds}
                onChange={handleDeptChange}
                style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}
              />
            </div>

            <div style={{ maxHeight: 400, overflowY: 'auto' }}>
              {experts.length === 0 ? (
                <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
                  <SafetyOutlined style={{ fontSize: 48, marginBottom: 12 }} />
                  <div>请先选择科室查看专家</div>
                </div>
              ) : (
                <Checkbox.Group
                  value={selectedExpertIds}
                  onChange={handleExpertChange}
                  style={{ width: '100%' }}
                >
                  <List
                    dataSource={experts}
                    renderItem={(expert) => (
                      <List.Item key={expert.id}>
                        <Checkbox value={expert.id} style={{ width: '100%' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                            <Avatar
                              size={40}
                              style={{ backgroundColor: '#1890ff' }}
                              icon={<UserOutlined />}
                            />
                            <div>
                              <div style={{ fontWeight: 500 }}>
                                {expert.name}
                                <span style={{ marginLeft: 8, color: '#999', fontSize: 12 }}>
                                  {expert.title}
                                </span>
                              </div>
                              <div style={{ fontSize: 12, color: '#888' }}>
                                {expert.specialty}
                              </div>
                            </div>
                            <div style={{ marginLeft: 'auto' }}>
                              {getStatusTag(expert.status)}
                            </div>
                          </div>
                        </Checkbox>
                      </List.Item>
                    )}
                  />
                </Checkbox.Group>
              )}
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  )
}

export default CreateConsultationPage
