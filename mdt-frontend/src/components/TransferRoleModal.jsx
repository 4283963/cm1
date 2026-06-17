import { useState } from 'react'
import {
  Modal,
  List,
  Avatar,
  Button,
  Tag,
  Typography,
  Radio,
  Space,
  Tooltip,
  message,
} from 'antd'
import {
  CrownOutlined,
  UserOutlined,
  CheckCircleOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import { consultationApi } from '../services/api'

const { Title, Text, Paragraph } = Typography

function TransferRoleModal({
  open,
  onClose,
  consultationId,
  currentUserId,
  currentPresenterId,
  participants,
  onTransferred,
}) {
  const [targetUserId, setTargetUserId] = useState(null)
  const [loading, setLoading] = useState(false)

  const eligibleTargets = (participants || []).filter(
    (p) =>
      p.status === 'ACCEPTED' ||
      p.status === 'JOINED' ||
      p.status === 'INVITED'
  )

  const handleConfirm = async () => {
    if (!targetUserId) {
      message.warning('请选择要移交的目标专家')
      return
    }
    setLoading(true)
    try {
      await consultationApi.transferPresenter(
        consultationId,
        currentUserId,
        targetUserId
      )
      const target = eligibleTargets.find((e) => e.expertId === targetUserId)
      message.success(`主讲人权限已移交给 ${target?.expertName || '专家'}`)
      onTransferred?.(targetUserId, target?.expertName)
      onClose()
    } catch (error) {
      message.error(
        '移交失败：' + (error.response?.data?.message || error.message)
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal
      title={
        <Space>
          <SwapOutlined style={{ color: '#722ed1', fontSize: 20 }} />
          <span style={{ fontSize: 18, fontWeight: 600 }}>移交主讲人权限</span>
        </Space>
      }
      open={open}
      onCancel={onClose}
      onOk={handleConfirm}
      confirmLoading={loading}
      okText="确认移交"
      okButtonProps={{ type: 'primary' }}
      width={480}
      destroyOnClose
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <div
          style={{
            padding: 16,
            borderRadius: 8,
            background: '#f9f0ff',
            border: '1px solid #d3adf7',
          }}
        >
          <Space>
            <CrownOutlined style={{ color: '#722ed1', fontSize: 18 }} />
            <Text strong>当前主讲人：</Text>
            {eligibleTargets.find((e) => e.expertId === currentPresenterId)?.expertName ||
              '主持人'}
          </Space>
        </div>

        <Paragraph type="secondary" style={{ marginBottom: 8 }}>
          选择要将「翻页控制权 / 课件切换权 / 主讲权限」移交给哪位专家：
        </Paragraph>

        <div
          style={{
            maxHeight: 320,
            overflowY: 'auto',
            border: '1px solid #f0f0f0',
            borderRadius: 8,
          }}
        >
          <Radio.Group
            value={targetUserId}
            onChange={(e) => setTargetUserId(e.target.value)}
            style={{ width: '100%' }}
          >
            <List
              dataSource={eligibleTargets}
              renderItem={(expert) => {
                const isMe = expert.expertId === currentUserId
                const isCurrentPresenter = expert.expertId === currentPresenterId
                return (
                  <List.Item
                    key={expert.expertId}
                    style={{
                      padding: '12px 16px',
                      borderBottom: '1px solid #fafafa',
                      background: isCurrentPresenter ? '#f6ffed' : 'transparent',
                    }}
                  >
                    <Radio
                      value={expert.expertId}
                      disabled={isMe || isCurrentPresenter}
                      style={{ width: '100%' }}
                    >
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 12,
                          width: '100%',
                        }}
                      >
                        <Avatar
                          size={40}
                          style={{
                            backgroundColor: isCurrentPresenter ? '#52c41a' : '#1890ff',
                          }}
                        >
                          {expert.expertName?.charAt(0)}
                        </Avatar>
                        <div style={{ flex: 1 }}>
                          <Space>
                            <span style={{ fontWeight: 500 }}>
                              {expert.expertName}
                            </span>
                            {isMe && <Tag color="blue">我</Tag>}
                            {isCurrentPresenter && (
                              <Tag color="green" icon={<CrownOutlined />}>
                                当前主讲人
                              </Tag>
                            )}
                          </Space>
                          <div style={{ fontSize: 12, color: '#888', marginTop: 2 }}>
                            {expert.departmentName}
                          </div>
                        </div>
                        {isCurrentPresenter && (
                          <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 18 }} />
                        )}
                      </div>
                    </Radio>
                  </List.Item>
                )
              }}
            />
          </Radio.Group>
        </div>

        {targetUserId && (
          <Alert
            type="warning"
            showIcon
            message="提示"
            description="移交后，翻页、课件切换、标注等控制权将归目标专家所有，您仍可作为参会者查看。"
          />
        )}
      </Space>
    </Modal>
  )
}

function Alert({ type, showIcon, message, description }) {
  return (
    <div
      style={{
        padding: 12,
        borderRadius: 8,
        background: type === 'warning' ? '#fffbe6' : '#f6ffed',
        border: `1px solid ${type === 'warning' ? '#ffe58f' : '#b7eb8f'}`,
        fontSize: 13,
      }}
    >
      {message && (
        <div style={{ fontWeight: 500, marginBottom: 4 }}>
          {showIcon && '⚠️ '}
          {message}
        </div>
      )}
      {description && <div style={{ color: '#666' }}>{description}</div>}
    </div>
  )
}

export default TransferRoleModal
