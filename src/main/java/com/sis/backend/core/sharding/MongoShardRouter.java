package com.sis.backend.core.sharding;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.sis.backend.core.hashing.ConsistentHashRing;
import com.sis.backend.model.Enrollment;
import com.sis.backend.model.Student;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MongoShardRouter implements true Physical Multi-Database Sharding
 * backed by Consistent Hashing with Virtual Nodes.
 *
 * It dynamically routes records to physical shard databases
 * (sis_shard_0, sis_shard_1, sis_shard_2) on MongoDB Atlas,
 * while maintaining sis_db as the central catalog.
 */
@Component
public class MongoShardRouter extends AbstractMongoEventListener<Object> {

    private static final Logger log = LoggerFactory.getLogger(MongoShardRouter.class);

    private final ConsistentHashRing consistentHashRing;
    private final MongoTemplate defaultMongoTemplate;
    private final MongoClient mongoClient;
    private final MongoConverter mongoConverter;

    public MongoShardRouter(ConsistentHashRing consistentHashRing,
                            MongoTemplate defaultMongoTemplate,
                            MongoClient mongoClient,
                            MongoConverter mongoConverter) {
        this.consistentHashRing = consistentHashRing;
        this.defaultMongoTemplate = defaultMongoTemplate;
        this.mongoClient = mongoClient;
        this.mongoConverter = mongoConverter;
    }

    @PostConstruct
    public void initPhysicalShards() {
        log.info(">>> Initializing Physical Shard Databases on MongoDB Atlas: {}", consistentHashRing.getPhysicalShards());
        for (String shard : consistentHashRing.getPhysicalShards()) {
            try {
                MongoDatabase db = mongoClient.getDatabase(shard);
                Set<String> existing = new HashSet<>();
                for (String name : db.listCollectionNames()) {
                    existing.add(name);
                }
                if (!existing.contains("students")) {
                    db.createCollection("students");
                }
                if (!existing.contains("enrollments")) {
                    db.createCollection("enrollments");
                }
            } catch (Exception e) {
                log.warn("Shard initialization note for [{}]: {}", shard, e.getMessage());
            }
        }

        // Migrate/synchronize any records from sis_db to physical shards
        syncFromCatalogToShards();
    }

    /**
     * Synchronize all students and enrollments from central catalog to physical shard databases.
     */
    public void syncFromCatalogToShards() {
        try {
            List<Student> students = defaultMongoTemplate.findAll(Student.class);
            for (Student s : students) {
                String shard = consistentHashRing.getShard(s.getId());
                s.setTargetShard(shard);
                persistToShardDb(shard, "students", s.getId(), s);
            }

            List<Enrollment> enrollments = defaultMongoTemplate.findAll(Enrollment.class);
            for (Enrollment e : enrollments) {
                String shard = consistentHashRing.getShard(e.getStudentId());
                e.setTargetShard(shard);
                persistToShardDb(shard, "enrollments", e.getId(), e);
            }
            log.info(">>> Synchronized {} students and {} enrollments across physical shards.",
                    students.size(), enrollments.size());
        } catch (Exception e) {
            log.error("Could not complete sync to physical shards: {}", e.getMessage());
        }
    }

    /**
     * Automatic lifecycle hook: When any entity is saved in Spring Data Mongo,
     * replicate it to its assigned physical shard database in Atlas.
     */
    @Override
    public void onAfterSave(AfterSaveEvent<Object> event) {
        Object source = event.getSource();
        if (source instanceof Student student) {
            String shard = consistentHashRing.getShard(student.getId());
            student.setTargetShard(shard);
            persistToShardDb(shard, "students", student.getId(), student);
        } else if (source instanceof Enrollment enrollment) {
            String shard = consistentHashRing.getShard(enrollment.getStudentId());
            enrollment.setTargetShard(shard);
            persistToShardDb(shard, "enrollments", enrollment.getId(), enrollment);
        }
    }

    public void persistToShardDb(String shardName, String collectionName, String id, Object entity) {
        try {
            Document doc = new Document();
            mongoConverter.write(entity, doc);
            mongoClient.getDatabase(shardName)
                    .getCollection(collectionName)
                    .replaceOne(new Document("_id", id), doc, new ReplaceOptions().upsert(true));
        } catch (Exception e) {
            log.error("Failed to replicate entity [{}] to physical shard [{}]: {}", id, shardName, e.getMessage());
        }
    }

    /**
     * Route a partition key (e.g., studentId) to its target shard database.
     */
    public String routeKey(String partitionKey) {
        return consistentHashRing.getShard(partitionKey);
    }

    /**
     * Retrieve live document distribution count directly from each physical shard database.
     */
    public Map<String, Long> getDistribution() {
        Map<String, Long> distribution = new HashMap<>();
        Set<String> activeShards = consistentHashRing.getPhysicalShards();

        for (String shard : activeShards) {
            try {
                long count = mongoClient.getDatabase(shard).getCollection("students").countDocuments();
                distribution.put(shard, count);
            } catch (Exception e) {
                log.warn("Could not query physical shard [{}]: {}", shard, e.getMessage());
                distribution.put(shard, 0L);
            }
        }

        return distribution;
    }

    public ConsistentHashRing getConsistentHashRing() {
        return consistentHashRing;
    }
}
