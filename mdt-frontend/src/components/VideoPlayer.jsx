import { useEffect, useRef } from 'react'

function VideoPlayer({ stream, name, role, muted = false, isLocal = false }) {
  const videoRef = useRef(null)

  useEffect(() => {
    if (videoRef.current && stream) {
      videoRef.current.srcObject = stream
    }
  }, [stream])

  return (
    <div className="video-container">
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted={muted}
        style={{ transform: isLocal ? 'scaleX(-1)' : 'none' }}
      />
      <div className="video-overlay">
        <div>
          <span className="video-name">{name}</span>
          {role && <span className="video-status" style={{ marginLeft: 8 }}>{role}</span>}
        </div>
        {!stream && (
          <div style={{ color: '#fff', fontSize: 12 }}>连接中...</div>
        )}
      </div>
    </div>
  )
}

export default VideoPlayer
