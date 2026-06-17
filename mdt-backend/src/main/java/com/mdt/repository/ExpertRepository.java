package com.mdt.repository;

import com.mdt.entity.Expert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpertRepository extends JpaRepository<Expert, Long> {
    Optional<Expert> findByUsername(String username);
    List<Expert> findByDepartmentId(Long departmentId);
    List<Expert> findByStatus(String status);
    List<Expert> findByDepartmentIdIn(List<Long> departmentIds);
}
