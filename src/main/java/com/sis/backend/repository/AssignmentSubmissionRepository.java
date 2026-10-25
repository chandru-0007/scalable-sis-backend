package com.sis.backend.repository;

import com.sis.backend.model.AssignmentSubmission;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentSubmissionRepository extends MongoRepository<AssignmentSubmission, String> {

    List<AssignmentSubmission> findByAssignmentId(String assignmentId);

    List<AssignmentSubmission> findByStudentId(String studentId);

    Optional<AssignmentSubmission> findByAssignmentIdAndStudentId(String assignmentId, String studentId);

    boolean existsByAssignmentIdAndStudentId(String assignmentId, String studentId);

    long countByAssignmentId(String assignmentId);
}
