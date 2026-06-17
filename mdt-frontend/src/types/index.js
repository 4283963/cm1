export interface Department {
  id: number;
  code: string;
  name: string;
  description?: string;
  createdAt?: string;
}

export interface Expert {
  id: number;
  username: string;
  name: string;
  departmentId: number;
  departmentName?: string;
  title?: string;
  specialty?: string;
  status: 'ONLINE' | 'OFFLINE' | 'BUSY';
  createdAt?: string;
}

export interface ConsultationExpert {
  expertId: number;
  expertName: string;
  departmentName: string;
  status: 'INVITED' | 'ACCEPTED' | 'DECLINED' | 'JOINED' | 'LEFT';
  joinedAt?: string;
}

export interface Consultation {
  id: number;
  consultationNo: string;
  title: string;
  patientName?: string;
  patientInfo?: string;
  description?: string;
  initiatorId: number;
  initiatorName: string;
  roomId: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  startedAt?: string;
  endedAt?: string;
  createdAt: string;
  experts: ConsultationExpert[];
}

export interface CreateConsultationRequest {
  title: string;
  patientName?: string;
  patientInfo?: string;
  description?: string;
  initiatorId: number;
  initiatorName: string;
  expertIds: number[];
}

export interface ConsultationNotification {
  type: string;
  consultationId: number;
  consultationNo: string;
  title: string;
  initiatorName: string;
  patientName?: string;
  roomId: string;
  createdAt: string;
  message: string;
}

export interface Participant {
  userId: number;
  userName: string;
  departmentName?: string;
  role: string;
  streamId?: string;
  status: string;
  audioEnabled?: boolean;
  videoEnabled?: boolean;
  stream?: MediaStream;
}

export interface WebRTCConfig {
  iceServers?: RTCIceServer[];
}
