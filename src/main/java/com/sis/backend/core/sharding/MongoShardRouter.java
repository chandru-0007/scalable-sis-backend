package com.sis.backend.core.sharding;

import com.sis.backend.core.hashing.ConsistentHashRing;
import com.sis.backend.model.Student;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * MongoShardRouter routes records to logical partitions/shards
 * using Consistent Hashing. Tracks live record distribution across shards.
 */
@Component
public class MongoShardRouter {

    private final ConsistentHashRing consistentHashRing;
    private final MongoTemplate mongoTemplate;

    public MongoShardRouter(ConsistentHashRing consistentHashRing, MongoTemplate mongoTemplate) {
        this.consistentHashRing = consistentHashRing;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Determine shard assignment for a partition key.
     */
    public String routeKey(String partitionKey) {
        return consistentHashRing.getShard(partitionKey);
    }

    /**
     * Retrieve live document distribution count across all active shards.
     */
    public Map<String, Long> getDistribution() {
        Map<String, Long> distribution = new HashMap<>();
        Set<String> activeShards = consistentHashRing.getPhysicalShards();

        for (String shard : activeShards) {
            Query query = new Query(Criteria.where("targetShard").is(shard));
            long count = mongoTemplate.count(query, Student.class);
            distribution.put(shard, count);
        }

        return distribution;
    }

    public ConsistentHashRing getConsistentHashRing() {
        return consistentHashRing;
    }
}
