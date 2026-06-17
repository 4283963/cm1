package com.mdt.repository;

import com.mdt.entity.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsultationRepository extends JpaRepository<Consultation, Long> {
    Optional<Consultation> findByConsultationNo(String consultationNo);
    Optional<Consultation> findByRoomId(String roomId);
    List<Consultation> findByInitiatorIdOrderByCreatedAtDesc(Long initiatorId);
    List<Consultation> findByStatusOrderByCreatedAtDesc(String status);
}
