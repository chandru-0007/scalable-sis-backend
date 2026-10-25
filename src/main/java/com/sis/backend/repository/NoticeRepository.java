package com.sis.backend.repository;

import com.sis.backend.model.AcademicNotice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoticeRepository extends MongoRepository<AcademicNotice, String> {

    List<AcademicNotice> findByCategory(String category);

    List<AcademicNotice> findByDepartment(String department);

    List<AcademicNotice> findByCategoryAndDepartment(String category, String department);

    List<AcademicNotice> findAllByOrderByScrapedAtDesc();
}
