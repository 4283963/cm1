import { useState, useEffect } from 'react'
import {
  Modal,
  Checkbox,
  List,
  Avatar,
  Tag,
  Space,
  Button,
  Typography,
  Switch,
  Row,
  Col,
  message,
} from 'antd'
import {
  WarningOutlined,
  UserOutlined,
  PhoneOutlined,
} from '@ant-design/icons'
import { consultationApi } from '../services/api'

const { Title, Text, Paragraph } = Typography

function UrgentInviteModal({ open, onClose, consultationId, operatorId, onInvited }) {
  const [departments, setDepartments] = useState([])
  const [experts, setExperts] = useState([])
  const [selectedDeptIds, setSelectedDeptIds] = useState([])
  const [selectedExpertIds, setSelectedExpertIds] = useState([])
  const [isUrgent, setIsUrgent] = useState(true)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (open) {
      loadDepartments()
      setSelectedDeptIds([])
      setSelectedExpertIds([])
      setExperts([])
    }
  }, [open])

  const loadDepartments = async () => {
    try {
      const data = await consultationApi.getDepartments()
      setDepartments(data || [])
    } catch (error) {
      message.error('加载科室列表失败')
    }
  }

  const loadExperts = async (deptIds) => {
    if (deptIds.length === 0) {
      setExperts([])
      return
    }
    try {
      const data = await consultationApi.getExperts({
        departmentIds: deptIds.join(','),
      })
      setExperts(data || [])
    } catch (error) {
      message.error('加载专家列表失败')
    }
  }

  const handleDeptChange = (checkedValues) => {
    setSelectedDeptIds(checkedValues)
    loadExperts(checkedValues)
    setSelectedExpertIds([])
  }

  const handleConfirm = async () => {
    if (selectedExpertIds.length === 0) {
      message.warning('请至少选择一位专家')
      return
    }
    setLoading(true)
    try {
      await consultationApi.inviteAdditionalExperts(
        consultationId,
        operatorId,
        selectedExpertIds,
        isUrgent
      )
      message.success(isUrgent ? '紧急呼叫已发送！' : '专家邀请已发送')
      onInvited?.()
      onClose()
    } catch (error) {
      message.error(
        '邀请失败：' + (error.response?.data?.message || error.message)
      )
    } finally {
      setLoading(false)
    }
  }

  const getStatusTag = (status) => {
    const map = {
      ONLINE: { color: 'green', text: '在线' },
      OFFLINE: { color: 'default', text: '离线' },
      BUSY: { color: 'orange', text: '忙碌' },
    }
    const cfg = map[status] || map.OFFLINE
    return <Tag color={cfg.color}>{cfg.text}</Tag>
  }

  return (
    <Modal
      title={
        <Space>
          <WarningOutlined style={{ color: isUrgent ? '#ff4d4f' : '#faad14', fontSize: 20 }} />
          <span style={{ fontSize: 18, fontWeight: 600 }}>
            {isUrgent ? '紧急呼叫专家' : '追加邀请专家'}
          </span>
        </Space>
      }
      open={open}
      onCancel={onClose}
      onOk={handleConfirm}
      confirmLoading={loading}
      okText={isUrgent ? '发送紧急呼叫' : '发送邀请'}
      okButtonProps={{ danger: isUrgent, type: isUrgent ? 'primary' : 'primary' }}
      cancelText="取消"
      width={560}
      destroyOnClose
    >
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <div
          style={{
            padding: 12,
            borderRadius: 8,
            background: isUrgent ? '#fff1f0' : '#fffbe6',
            border: `1px solid ${isUrgent ? '#ffa39e' : '#ffe58f'}`,
          }}
        >
          <Row align="middle" justify="space-between">
            <Space>
              <PhoneOutlined style={{ color: isUrgent ? '#ff4d4f' : '#faad14' }} />
              <Text strong>
                {isUrgent ? '紧急呼叫模式：发送强提醒 + 弹窗震动' : '普通邀请模式'}
              </Text>
            </Space>
            <Switch
              checked={isUrgent}
              onChange={setIsUrgent}
              checkedChildren="紧急"
              unCheckedChildren="普通"
            />
          </Row>
        </div>

        <div>
          <Text type="secondary" style={{ marginBottom: 8, display: 'block' }}>
            选择科室（可多选）：
          </Text>
          <Checkbox.Group
            options={departments.map((d) => ({ label: d.name, value: d.id }))}
            value={selectedDeptIds}
            onChange={handleDeptChange}
            style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}
          />
        </div>

        <div style={{ maxHeight: 280, overflowY: 'auto', border: '1px solid #f0f0f0', borderRadius: 8 }}>
          {experts.length === 0 ? (
            <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
              <UserOutlined style={{ fontSize: 40, marginBottom: 12 }} />
              <div>请先选择科室</div>
            </div>
          ) : (
            <Checkbox.Group
              value={selectedExpertIds}
              onChange={setSelectedExpertIds}
              style={{ width: '100%' }}
            >
              <List
                dataSource={experts}
                renderItem={(expert) => (
                  <List.Item
                    key={expert.id}
                    style={{
                      padding: '12px 16px',
                      borderBottom: '1px solid #fafafa',
                    }}
                  >
                    <Checkbox value={expert.id} style={{ width: '100%' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        <Avatar size={40} style={{ backgroundColor: '#1890ff' }}>
                          {expert.name?.charAt(0)}
                        </Avatar>
                        <div style={{ flex: 1 }}>
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
                        {getStatusTag(expert.status)}
                      </div>
                    </Checkbox>
                  </List.Item>
                )}
              />
            </Checkbox.Group>
          )}
        </div>

        <Tag color="blue" style={{ margin: 0 }}>
          已选 {selectedExpertIds.length} 位专家
        </Tag>
      </Space>
    </Modal>
  )
}

export default UrgentInviteModal
