import { useEffect, useRef } from 'react'
import { CrownOutlined, UserOutlined } from '@ant-design/icons'

function VideoPlayer({
  stream,
  name,
  role,
  muted = false,
  isLocal = false,
  isPresenter = false,
  audioEnabled = true,
  videoEnabled = true,
}) {
  const videoRef = useRef(null)

  useEffect(() => {
    if (videoRef.current && stream) {
      videoRef.current.srcObject = stream
    }
  }, [stream])

  return (
    <div className="video-container" style={{ position: 'relative' }}>
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted={muted}
        style={{
          transform: isLocal ? 'scaleX(-1)' : 'none',
          width: '100%',
          height: '100%',
          objectFit: 'cover',
        }}
      />
      <div
        className="video-overlay"
        style={{
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          padding: '8px 12px',
          background: 'linear-gradient(transparent, rgba(0,0,0,0.7))',
          color: '#fff',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span className="video-name" style={{ fontSize: 13 }}>
            {name || '未知'}
          </span>
          {role && (
            <span
              className="video-status"
              style={{
                marginLeft: 4,
                fontSize: 11,
                padding: '1px 6px',
                background: 'rgba(255,255,255,0.2)',
                borderRadius: 4,
              }}
            >
              {role}
            </span>
          )}
          {!audioEnabled && (
            <span
              style={{
                fontSize: 11,
                padding: '1px 6px',
                background: '#ff4d4f',
                borderRadius: 4,
              }}
            >
              静音
            </span>
          )}
        </div>
        {isPresenter && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              fontSize: 11,
              padding: '2px 8px',
              background: 'linear-gradient(135deg, #ffd700, #ff8c00)',
              borderRadius: 12,
              color: '#fff',
              fontWeight: 600,
              boxShadow: '0 2px 4px rgba(255,140,0,0.4)',
            }}
          >
            <CrownOutlined />
            <span>主讲人</span>
          </div>
        )}
      </div>
      {isPresenter && (
        <div
          style={{
            position: 'absolute',
            top: 8,
            right: 8,
            width: 4,
            height: 4,
            borderRadius: '50%',
            background: '#ff4d4f',
            boxShadow: '0 0 8px #ff4d4f',
            animation: 'pulse 1.5s infinite',
          }}
        />
      )}
      {!stream && (
        <div
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontSize: 12,
            background: 'rgba(0,0,0,0.5)',
          }}
        >
          <UserOutlined style={{ fontSize: 48, opacity: 0.6 }} />
        </div>
      )}
    </div>
  )
}

export default VideoPlayer
