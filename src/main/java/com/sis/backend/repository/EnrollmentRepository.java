package com.sis.backend.repository;

import com.sis.backend.model.Enrollment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends MongoRepository<Enrollment, String> {

    List<Enrollment> findByStudentId(String studentId);

    List<Enrollment> findByCourseCode(String courseCode);

    Optional<Enrollment> findByStudentIdAndCourseCode(String studentId, String courseCode);

    boolean existsByStudentIdAndCourseCode(String studentId, String courseCode);

    long countByTargetShard(String targetShard);
}
