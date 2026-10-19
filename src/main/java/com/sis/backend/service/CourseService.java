package com.sis.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sis.backend.core.autocomplete.TrieAutocompleteService;
import com.sis.backend.core.id.SnowflakeIdGenerator;
import com.sis.backend.core.kv.KeyValueCacheService;
import com.sis.backend.model.Course;
import com.sis.backend.repository.CourseRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CourseService {

    private static final String COURSES_CACHE_KEY = "cache:courses:all";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final CourseRepository courseRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final KeyValueCacheService keyValueCacheService;
    private final TrieAutocompleteService trieAutocompleteService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CourseService(CourseRepository courseRepository,
                         SnowflakeIdGenerator snowflakeIdGenerator,
                         KeyValueCacheService keyValueCacheService,
                         TrieAutocompleteService trieAutocompleteService) {
        this.courseRepository = courseRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.keyValueCacheService = keyValueCacheService;
        this.trieAutocompleteService = trieAutocompleteService;
    }

    @PostConstruct
    public void init() {
        if (courseRepository.count() == 0) {
            seedSampleCourses();
        }
        // Load all courses into Prefix Trie for instant Autocomplete
        courseRepository.findAll().forEach(course -> {
            trieAutocompleteService.insert(course.getCode(), course.getCode(), course.getTitle());
            trieAutocompleteService.insert(course.getTitle(), course.getCode(), course.getTitle());
            for (String word : course.getTitle().split("\\s+")) {
                trieAutocompleteService.insert(word, course.getCode(), course.getTitle());
            }
        });
    }

    public List<Course> getAllCourses() {
        // 1. Try reading from Redis Key-Value Store
        String cachedJson = keyValueCacheService.get(COURSES_CACHE_KEY);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, new TypeReference<List<Course>>() {});
            } catch (Exception ignored) {
            }
        }

        // 2. Cache miss -> Read from Database
        List<Course> courses = courseRepository.findAll();

        // 3. Write back to Redis Key-Value Store
        try {
            String json = objectMapper.writeValueAsString(courses);
            keyValueCacheService.put(COURSES_CACHE_KEY, json, CACHE_TTL);
        } catch (Exception ignored) {
        }

        return courses;
    }

    public Optional<Course> getCourseByCode(String code) {
        return courseRepository.findByCode(code.trim().toUpperCase());
    }

    public List<TrieAutocompleteService.CourseSuggestion> getSuggestions(String query, int limit) {
        return trieAutocompleteService.search(query, limit);
    }

    private void seedSampleCourses() {
        List<Course> list = new ArrayList<>();
        list.add(new Course(snowflakeIdGenerator.nextIdStr(), "CS101", "Introduction to Computing", "CSE", 3, "Dr. Alan Turing", 60, 42, "Mon 09:00-10:30, Wed 09:00-10:30", "LH-101"));
        list.add(new Course(snowflakeIdGenerator.nextIdStr(), "CS201", "Data Structures and Algorithms", "CSE", 4, "Dr. Donald Knuth", 60, 58, "Tue 10:30-12:00, Thu 10:30-12:00", "LH-102"));
        list.add(new Course(snowflakeIdGenerator.nextIdStr(), "CS301", "Database Management Systems", "CSE", 4, "Dr. Edgar Codd", 60, 39, "Mon 11:00-12:30, Wed 11:00-12:30", "LH-201"));
        list.add(new Course(snowflakeIdGenerator.nextIdStr(), "CS302", "Operating Systems", "CSE", 4, "Dr. Andrew Tanenbaum", 60, 48, "Tue 14:00-15:30, Thu 14:00-15:30", "LH-202"));
        list.add(new Course(snowflakeIdGenerator.nextIdStr(), "CS401", "Computer Networks", "CSE", 3, "Dr. Vint Cerf", 50, 35, "Wed 14:00-15:30, Fri 14:00-15:30", "LH-301"));
        list.add(new Course(snowflakeIdGenerator.nextIdStr(), "CS402", "Distributed Systems", "CSE", 4, "Dr. Leslie Lamport", 50, 49, "Mon 14:00-15:30, Thu 09:00-10:30", "LH-302"));
        list.add(new Course(snowflakeIdGenerator.nextIdStr(), "CS501", "Cloud Computing & DevOps", "CSE", 3, "Prof. Werner Vogels", 45, 30, "Fri 09:00-10:30, Fri 11:00-12:30", "LH-303"));
        list.add(new Course(snowflakeIdGenerator.nextIdStr(), "CS502", "Machine Learning & AI", "CSE", 4, "Dr. Geoffrey Hinton", 60, 55, "Tue 09:00-10:30, Fri 15:30-17:00", "LH-101"));
        courseRepository.saveAll(list);
    }
}
