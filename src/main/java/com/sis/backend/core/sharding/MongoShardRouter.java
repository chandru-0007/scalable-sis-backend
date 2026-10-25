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
import java.util.concurrent.atomic.AtomicLong;

/**
 * MongoShardRouter implements true Physical Multi-Database Sharding
 * backed by Consistent Hashing with Virtual Nodes.
 *
 * It dynamically routes records to physical shard databases
 * (sis_shard_0, sis_shard_1, sis_shard_2) on MongoDB Atlas,
 * while maintaining sis_db as the central catalog.
 *
 * Safe Drain-Remove: when a shard is decommissioned, all its data
 * is migrated to the successor shard BEFORE removing from the ring.
 */
@Component
public class MongoShardRouter extends AbstractMongoEventListener<Object> {

    private static final Logger log = LoggerFactory.getLogger(MongoShardRouter.class);

    private final ConsistentHashRing consistentHashRing;
    private final MongoTemplate defaultMongoTemplate;
    private final MongoClient mongoClient;
    private final MongoConverter mongoConverter;

    // Track last drain operation status
    private volatile String lastDrainStatus = "IDLE";
    private final AtomicLong lastDrainMigratedCount = new AtomicLong(0);

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
        // 1. Discover any existing physical shard databases on MongoDB Atlas (sis_shard_*)
        try {
            for (String dbName : mongoClient.listDatabaseNames()) {
                if (dbName.startsWith("sis_shard_")) {
                    consistentHashRing.addShard(dbName);
                }
            }
        } catch (Exception e) {
            log.warn("Could not discover shards from Atlas listDatabaseNames: {}", e.getMessage());
        }

        log.info(">>> Initializing Physical Shard Databases on MongoDB Atlas: {}", consistentHashRing.getPhysicalShards());
        for (String shard : consistentHashRing.getPhysicalShards()) {
            ensureCollectionExists(shard, "students");
            ensureCollectionExists(shard, "enrollments");
        }

        // 2. Synchronize any direct MongoDB Compass changes between physical shards and catalog
        syncShardsWithCatalog();

        // 3. Synchronize and rebalance records across physical shards based on Consistent Hashing
        rebalanceCluster();
    }

    /**
     * Add a shard to the ring, ensure its collections on Atlas, and rebalance (shift) data.
     * Only keys mapped to the new shard (~1/N) are moved, preserving consistent hashing invariants.
     */
    public synchronized Map<String, Object> addShardAndRebalance(String shardName) {
        Map<String, Object> result = new LinkedHashMap<>();

        ensureCollectionExists(shardName, "students");
        ensureCollectionExists(shardName, "enrollments");

        boolean alreadyExists = consistentHashRing.getPhysicalShards().contains(shardName);
        consistentHashRing.addShard(shardName);

        // Rebalance data across ring: records now mapping to shardName (or another shard) are shifted
        Map<String, Object> migrationStats = rebalanceCluster();

        long shiftedStudents = (long) migrationStats.getOrDefault("migratedStudents", 0L);
        long shiftedEnrollments = (long) migrationStats.getOrDefault("migratedEnrollments", 0L);

        result.put("success", true);
        result.put("shard", shardName);
        result.put("alreadyExisted", alreadyExists);
        result.put("message", alreadyExists
                ? "Shard [" + shardName + "] already exists in ring. Rebalance complete."
                : "Shard [" + shardName + "] added to ring. Rebalanced: " + shiftedStudents + " students shifted to appropriate shards.");
        result.put("migratedStudents", shiftedStudents);
        result.put("migratedEnrollments", shiftedEnrollments);
        List<String> sortedShards = new ArrayList<>(consistentHashRing.getPhysicalShards());
        Collections.sort(sortedShards);
        result.put("updatedShards", sortedShards);
        result.put("totalVirtualNodes", consistentHashRing.getRingSize());
        result.put("distribution", getDistribution());
        result.put("virtualNodeDistribution", consistentHashRing.getVirtualNodeDistribution());

        log.info(">>> ADD & REBALANCE SHARD [{}]: Shifted {} students, {} enrollments to new topology.",
                shardName, shiftedStudents, shiftedEnrollments);

        return result;
    }

    /**
     * Rebalance cluster: Checks every student and enrollment document,
     * routes via consistentHashRing.getShard(key), copies to the correct shard DB,
     * and deletes from any outdated shard DB.
     */
    public synchronized Map<String, Object> rebalanceCluster() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long shiftedStudents = 0;
        long shiftedEnrollments = 0;

        try {
            MongoDatabase catalogDb = mongoClient.getDatabase("sis_db");

            // 1. Rebalance Students from central catalog
            for (Document doc : catalogDb.getCollection("students").find()) {
                Object idObj = doc.get("_id");
                String id = idObj != null ? idObj.toString() : "";
                if (id.isEmpty()) continue;

                String targetShard = consistentHashRing.getShard(id);
                String oldShard = doc.getString("targetShard");

                ensureCollectionExists(targetShard, "students");

                // If record has moved to a new shard
                if (oldShard != null && !oldShard.equals(targetShard)) {
                    try {
                        mongoClient.getDatabase(oldShard).getCollection("students")
                                .deleteOne(new Document("_id", id));
                    } catch (Exception e) {
                        log.warn("Could not delete student [{}] from old shard [{}]: {}", id, oldShard, e.getMessage());
                    }
                    shiftedStudents++;
                    log.info(">>> Consistent Hash Shift: Student [{}] moved from [{}] -> [{}]", id, oldShard, targetShard);
                }

                doc.put("targetShard", targetShard);
                mongoClient.getDatabase(targetShard).getCollection("students")
                        .replaceOne(new Document("_id", id), doc, new ReplaceOptions().upsert(true));

                catalogDb.getCollection("students")
                        .updateOne(new Document("_id", id), new Document("$set", new Document("targetShard", targetShard)));
            }

            // 2. Also scrub physical shards for any misplaced students
            for (String shard : consistentHashRing.getPhysicalShards()) {
                try {
                    List<Document> misplaced = new ArrayList<>();
                    for (Document doc : mongoClient.getDatabase(shard).getCollection("students").find()) {
                        Object idObj = doc.get("_id");
                        String id = idObj != null ? idObj.toString() : "";
                        if (!id.isEmpty()) {
                            String correctShard = consistentHashRing.getShard(id);
                            if (!correctShard.equals(shard)) {
                                misplaced.add(doc);
                            }
                        }
                    }
                    for (Document doc : misplaced) {
                        String id = doc.get("_id").toString();
                        String correctShard = consistentHashRing.getShard(id);
                        ensureCollectionExists(correctShard, "students");
                        doc.put("targetShard", correctShard);
                        mongoClient.getDatabase(correctShard).getCollection("students")
                                .replaceOne(new Document("_id", id), doc, new ReplaceOptions().upsert(true));
                        mongoClient.getDatabase(shard).getCollection("students")
                                .deleteOne(new Document("_id", id));
                        shiftedStudents++;
                    }
                } catch (Exception e) {
                    log.warn("Physical shard scrub note for [{}]: {}", shard, e.getMessage());
                }
            }

            // 3. Rebalance Enrollments
            for (Document doc : catalogDb.getCollection("enrollments").find()) {
                Object studentIdObj = doc.get("studentId");
                Object idObj = doc.get("_id");
                String studentId = studentIdObj != null ? studentIdObj.toString() : "";
                String id = idObj != null ? idObj.toString() : "";
                if (studentId.isEmpty() || id.isEmpty()) continue;

                String targetShard = consistentHashRing.getShard(studentId);
                String oldShard = doc.getString("targetShard");

                ensureCollectionExists(targetShard, "enrollments");

                if (oldShard != null && !oldShard.equals(targetShard)) {
                    try {
                        mongoClient.getDatabase(oldShard).getCollection("enrollments")
                                .deleteOne(new Document("_id", id));
                    } catch (Exception e) {
                        log.warn("Could not delete enrollment [{}] from old shard [{}]: {}", id, oldShard, e.getMessage());
                    }
                    shiftedEnrollments++;
                }

                doc.put("targetShard", targetShard);
                mongoClient.getDatabase(targetShard).getCollection("enrollments")
                        .replaceOne(new Document("_id", id), doc, new ReplaceOptions().upsert(true));

                catalogDb.getCollection("enrollments")
                        .updateOne(new Document("_id", id), new Document("$set", new Document("targetShard", targetShard)));
            }

            log.info(">>> Cluster Rebalance Complete: Shifted {} students, {} enrollments across consistent hash ring.",
                    shiftedStudents, shiftedEnrollments);
        } catch (Exception e) {
            log.error("Could not complete cluster rebalance: {}", e.getMessage(), e);
        }

        stats.put("migratedStudents", shiftedStudents);
        stats.put("migratedEnrollments", shiftedEnrollments);
        return stats;
    }

    /**
     * Backward-compatible alias for rebalanceCluster().
     */
    public void syncFromCatalogToShards() {
        rebalanceCluster();
    }

    /**
     * SAFE DRAIN-REMOVE: Migrates all data from a decommissioned shard to its
     * successor(s) on the consistent hash ring BEFORE removing the shard.
     *
     * Algorithm:
     * 1. Temporarily remove the shard from the ring
     * 2. For each record in the doomed shard's DB, re-resolve the new target shard
     * 3. Upsert each record into the new shard's DB
     * 4. After all records are confirmed migrated, drop (clear) the old shard's collections
     *
     * @param shardToRemove  The physical shard database name to decommission
     * @return map with migration stats
     */
    public Map<String, Object> drainAndRemoveShard(String shardToRemove) {
        Map<String, Object> result = new LinkedHashMap<>();

        Set<String> currentShards = consistentHashRing.getPhysicalShards();
        if (!currentShards.contains(shardToRemove)) {
            result.put("success", false);
            result.put("message", "Shard [" + shardToRemove + "] not found in ring.");
            return result;
        }
        if (currentShards.size() <= 1) {
            result.put("success", false);
            result.put("message", "Cannot remove the last shard. At least 1 shard must remain.");
            return result;
        }

        lastDrainStatus = "DRAINING";
        long migratedStudents = 0;
        long migratedEnrollments = 0;

        try {
            log.info(">>> DRAIN-REMOVE: Starting safe drain of shard [{}]", shardToRemove);

            // Step 1: Remove shard from the ring FIRST so getShard() returns successor
            boolean removed = consistentHashRing.removeShard(shardToRemove);
            if (!removed) {
                lastDrainStatus = "FAILED";
                result.put("success", false);
                result.put("message", "Failed to remove shard from ring.");
                return result;
            }

            MongoDatabase doomed = mongoClient.getDatabase(shardToRemove);

            // Step 2: Migrate students
            List<Document> studentDocs = new ArrayList<>();
            for (Document doc : doomed.getCollection("students").find()) {
                studentDocs.add(doc);
            }
            for (Document doc : studentDocs) {
                Object idObj = doc.get("_id");
                String id = idObj != null ? idObj.toString() : "";
                if (!id.isEmpty()) {
                    String newShard = consistentHashRing.getShard(id);
                    doc.put("targetShard", newShard);
                    // Ensure shard DB has collection
                    ensureCollectionExists(newShard, "students");
                    mongoClient.getDatabase(newShard).getCollection("students")
                            .replaceOne(new Document("_id", id), doc, new ReplaceOptions().upsert(true));
                    migratedStudents++;
                }
            }

            // Step 3: Migrate enrollments
            List<Document> enrollmentDocs = new ArrayList<>();
            for (Document doc : doomed.getCollection("enrollments").find()) {
                enrollmentDocs.add(doc);
            }
            for (Document doc : enrollmentDocs) {
                Object studentIdObj = doc.get("studentId");
                Object idObj = doc.get("_id");
                String studentId = studentIdObj != null ? studentIdObj.toString() : "";
                String id = idObj != null ? idObj.toString() : "";
                if (!studentId.isEmpty() && !id.isEmpty()) {
                    String newShard = consistentHashRing.getShard(studentId);
                    doc.put("targetShard", newShard);
                    ensureCollectionExists(newShard, "enrollments");
                    mongoClient.getDatabase(newShard).getCollection("enrollments")
                            .replaceOne(new Document("_id", id), doc, new ReplaceOptions().upsert(true));
                    migratedEnrollments++;
                }
            }

            // Step 4: Clear the drained shard's collections (data is safe in new shards)
            doomed.getCollection("students").deleteMany(new Document());
            doomed.getCollection("enrollments").deleteMany(new Document());

            lastDrainMigratedCount.set(migratedStudents + migratedEnrollments);
            lastDrainStatus = "COMPLETED";

            log.info(">>> DRAIN-REMOVE COMPLETE: Migrated {} students, {} enrollments from [{}]. Ring now has {} shards.",
                    migratedStudents, migratedEnrollments, shardToRemove, consistentHashRing.getPhysicalShards().size());

            result.put("success", true);
            result.put("message", "Shard [" + shardToRemove + "] safely drained and removed.");
            result.put("migratedStudents", migratedStudents);
            result.put("migratedEnrollments", migratedEnrollments);
            result.put("remainingShards", consistentHashRing.getPhysicalShards());
            result.put("totalVirtualNodes", consistentHashRing.getRingSize());

        } catch (Exception e) {
            lastDrainStatus = "FAILED";
            log.error(">>> DRAIN-REMOVE FAILED for shard [{}]: {}", shardToRemove, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Drain failed: " + e.getMessage());
            // Note: ring was already modified - may need manual recovery
            result.put("warning", "Ring state may be inconsistent. Check logs.");
        }

        return result;
    }

    private void ensureCollectionExists(String dbName, String collectionName) {
        try {
            MongoDatabase db = mongoClient.getDatabase(dbName);
            Set<String> collections = new HashSet<>();
            for (String name : db.listCollectionNames()) {
                collections.add(name);
            }
            if (!collections.contains(collectionName)) {
                db.createCollection(collectionName);
            }
        } catch (Exception e) {
            log.warn("Could not ensure collection [{}] in [{}]: {}", collectionName, dbName, e.getMessage());
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
        Map<String, Long> distribution = new LinkedHashMap<>();
        // Use insertion-ordered set so UI displays shards in consistent order
        List<String> sortedShards = new ArrayList<>(consistentHashRing.getPhysicalShards());
        Collections.sort(sortedShards);

        for (String shard : sortedShards) {
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

    public String getLastDrainStatus() {
        return lastDrainStatus;
    }

    public long getLastDrainMigratedCount() {
        return lastDrainMigratedCount.get();
    }

    /**
     * Find a student across all physical shards and the central catalog.
     * If the student was modified directly in MongoDB Compass on any physical shard
     * (e.g. sis_shard_1), this method returns the fresh shard record AND synchronizes
     * the central catalog (sis_db) so all parts of the system remain consistent.
     */
    public Optional<Student> findStudent(String idOrRollOrEmail) {
        if (idOrRollOrEmail == null || idOrRollOrEmail.isBlank()) {
            return Optional.empty();
        }
        String clean = idOrRollOrEmail.trim();

        // 1. Search through all active physical shards on MongoDB Atlas
        for (String shard : consistentHashRing.getPhysicalShards()) {
            try {
                Document doc = mongoClient.getDatabase(shard).getCollection("students").find(new Document("$or", List.of(
                        new Document("_id", clean),
                        new Document("rollNumber", clean.toUpperCase()),
                        new Document("rollNumber", clean),
                        new Document("email", clean.toLowerCase())
                ))).first();

                if (doc != null) {
                    Student student = mongoConverter.read(Student.class, doc);
                    if (student != null) {
                        syncToCatalog(student, doc);
                        return Optional.of(student);
                    }
                }
            } catch (Exception e) {
                log.warn("Physical shard student lookup note for [{}]: {}", shard, e.getMessage());
            }
        }

        // 2. Fallback to central catalog sis_db
        try {
            Document doc = mongoClient.getDatabase("sis_db").getCollection("students").find(new Document("$or", List.of(
                    new Document("_id", clean),
                    new Document("rollNumber", clean.toUpperCase()),
                    new Document("rollNumber", clean),
                    new Document("email", clean.toLowerCase())
            ))).first();

            if (doc != null) {
                Student student = mongoConverter.read(Student.class, doc);
                return Optional.ofNullable(student);
            }
        } catch (Exception e) {
            log.warn("Catalog student lookup note: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Retrieve all students with live physical shard truth.
     */
    public List<Student> findAllStudents() {
        Map<String, Student> studentMap = new LinkedHashMap<>();

        // 1. Load from central catalog first
        try {
            for (Document doc : mongoClient.getDatabase("sis_db").getCollection("students").find()) {
                Student s = mongoConverter.read(Student.class, doc);
                if (s != null && s.getId() != null) {
                    studentMap.put(s.getId(), s);
                }
            }
        } catch (Exception e) {
            log.warn("Could not load catalog students: {}", e.getMessage());
        }

        // 2. Overlay live changes from physical shards
        for (String shard : consistentHashRing.getPhysicalShards()) {
            try {
                for (Document doc : mongoClient.getDatabase(shard).getCollection("students").find()) {
                    Student s = mongoConverter.read(Student.class, doc);
                    if (s != null && s.getId() != null) {
                        studentMap.put(s.getId(), s);
                        syncToCatalog(s, doc);
                    }
                }
            } catch (Exception e) {
                log.warn("Could not overlay shard students for [{}]: {}", shard, e.getMessage());
            }
        }

        return new ArrayList<>(studentMap.values());
    }

    /**
     * Synchronize any manual edits made directly in MongoDB Compass across shards and catalog.
     */
    public synchronized void syncShardsWithCatalog() {
        try {
            for (String shard : consistentHashRing.getPhysicalShards()) {
                for (Document shardDoc : mongoClient.getDatabase(shard).getCollection("students").find()) {
                    Object idObj = shardDoc.get("_id");
                    String id = idObj != null ? idObj.toString() : "";
                    if (!id.isEmpty()) {
                        Student s = mongoConverter.read(Student.class, shardDoc);
                        if (s != null) {
                            syncToCatalog(s, shardDoc);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not sync shards with catalog: {}", e.getMessage());
        }
    }

    private void syncToCatalog(Student student, Document shardDoc) {
        try {
            String id = student.getId();
            if (id != null && !id.isEmpty()) {
                Document catalogDoc = mongoClient.getDatabase("sis_db").getCollection("students")
                        .find(new Document("_id", id)).first();

                if (catalogDoc != null) {
                    boolean changed = false;
                    if (!Objects.equals(catalogDoc.get("semester"), shardDoc.get("semester"))) changed = true;
                    if (!Objects.equals(catalogDoc.get("department"), shardDoc.get("department"))) changed = true;
                    if (!Objects.equals(catalogDoc.get("name"), shardDoc.get("name"))) changed = true;
                    if (!Objects.equals(catalogDoc.get("email"), shardDoc.get("email"))) changed = true;

                    if (changed) {
                        shardDoc.put("targetShard", student.getTargetShard() != null ? student.getTargetShard() : consistentHashRing.getShard(id));
                        mongoClient.getDatabase("sis_db").getCollection("students")
                                .replaceOne(new Document("_id", id), shardDoc, new ReplaceOptions().upsert(true));
                        log.info(">>> BI-DIRECTIONAL SYNC: Synchronized student [{}] from physical shard to central catalog (Semester: {})",
                                student.getRollNumber(), student.getSemester());
                    }
                } else {
                    mongoClient.getDatabase("sis_db").getCollection("students")
                            .replaceOne(new Document("_id", id), shardDoc, new ReplaceOptions().upsert(true));
                }
            }
        } catch (Exception e) {
            log.warn("Catalog sync note: {}", e.getMessage());
        }
    }
}
