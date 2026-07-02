package org.huss.socialsaas.recommendation.service;

import lombok.RequiredArgsConstructor;
import org.huss.socialsaas.recommendation.dto.response.RecommendationFeedResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RecommendationCacheService {

    private final ObjectProvider<RedisTemplate<Object, Object>> redisTemplateProvider;

    @Value("${app.recommendation.feed-cache-ttl-seconds:900}")
    private long feedCacheTtlSeconds;

    private final Map<String, RecommendationFeedResponse> localCache = new ConcurrentHashMap<>();

    public String buildUserFeedKey(Long userId) {
        return "rec:feed:user:" + userId;
    }

    public Optional<RecommendationFeedResponse> getUserFeed(Long userId) {
        String key = buildUserFeedKey(userId);

        RedisTemplate<Object, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                Object payload = redisTemplate.opsForValue().get(key);
                if (payload == null) {
                    return Optional.ofNullable(localCache.get(key));
                }
                if (payload instanceof RecommendationFeedResponse response) {
                    return Optional.of(response);
                }
                redisTemplate.delete(key);
                return Optional.empty();
            } catch (Exception ignored) {
                return Optional.ofNullable(localCache.get(key));
            }
        }

        return Optional.ofNullable(localCache.get(key));
    }

    public void cacheUserFeed(Long userId, RecommendationFeedResponse response) {
        String key = buildUserFeedKey(userId);
        RedisTemplate<Object, Object> redisTemplate = redisTemplateProvider.getIfAvailable();

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(key, response, Duration.ofSeconds(feedCacheTtlSeconds));
                return;
            } catch (Exception ignored) {
                // fall through to local cache
            }
        }

        localCache.put(key, response);
    }

    public void evictUserFeed(Long userId) {
        String key = buildUserFeedKey(userId);
        RedisTemplate<Object, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(key);
            } catch (Exception ignored) {
                // ignore and evict local cache only
            }
        }
        localCache.remove(key);
    }

    public long getFeedCacheTtlSeconds() {
        return feedCacheTtlSeconds;
    }
}




