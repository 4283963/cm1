package com.mdt.repository;

import com.mdt.entity.ConsultationExpert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsultationExpertRepository extends JpaRepository<ConsultationExpert, Long> {
    List<ConsultationExpert> findByConsultationId(Long consultationId);
    List<ConsultationExpert> findByExpertId(Long expertId);
    Optional<ConsultationExpert> findByConsultationIdAndExpertId(Long consultationId, Long expertId);
    List<ConsultationExpert> findByExpertIdAndStatus(Long expertId, String status);
}
