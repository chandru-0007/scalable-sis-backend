package com.sis.backend.repository;

import com.sis.backend.model.Assignment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssignmentRepository extends MongoRepository<Assignment, String> {

    List<Assignment> findByCourseCode(String courseCode);

    List<Assignment> findByCourseCodeIn(List<String> courseCodes);

    List<Assignment> findByFacultyId(String facultyId);

    List<Assignment> findByFacultyNameIgnoreCase(String facultyName);
}
