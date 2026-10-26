package com.sis.backend.repository;

import com.sis.backend.model.Student;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentRepository extends MongoRepository<Student, String> {

    Optional<Student> findByEmail(String email);

    Optional<Student> findByRollNumber(String rollNumber);

    boolean existsByEmail(String email);

    boolean existsByRollNumber(String rollNumber);

    java.util.List<Student> findByRole(String role);
}
