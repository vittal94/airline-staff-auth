package com.airline.airlinebackend.filter;

import com.airline.airlinebackend.dto.response.ErrorResponse;
import com.airline.airlinebackend.util.JsonUtil;
import com.airline.airlinebackend.util.ServletUtil;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * General rate limiting filter for API endpoints
 */
@WebFilter(urlPatterns = "/api/*")
public class RateLimitFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

    //Simple in-memory rate limiter for API endpoints
    //For production, consider using Redis or database-backed solution
    private static final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    private static final int REQUESTS_PER_MINUTE = 60;
    private static final long BUCKET_DURATION_MS = 60_000; // 1 minute

    private volatile Thread cleanupThread;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        //Periodic cleanup of old buckets
        cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60_000); //Every minute
                    long now = System.currentTimeMillis();
                    buckets.entrySet().removeIf( entry ->
                            entry.getValue().isExpired(now));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.setName("RateLimitCleanup");
        cleanupThread.start();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

        String clientIp = ServletUtil.getClientIP(httpRequest);

        if (!allowRequest(clientIp)) {
            logger.warn("Rate limit exceeded or IP: {}", clientIp);

            httpResponse.setStatus(429);//To many requests
            httpResponse.setHeader("Retry-After", String.valueOf(secondsUntilReset(clientIp)));
            httpResponse.setContentType("application/json");

            ErrorResponse error = ErrorResponse.of("RATE_LIMIT_EXCEEDED",
                    "Too many requests. Please slow down.");
            httpResponse.getWriter().write(JsonUtil.toJson(error));
            return;
        }
        filterChain.doFilter(servletRequest, servletResponse);

    }

    private boolean allowRequest(String clientIp) {
        long now = System.currentTimeMillis();

        AtomicBoolean isAllowed = new AtomicBoolean(false);
        buckets.compute(clientIp, (key, bucket) -> {
            if (bucket == null || bucket.isExpired(now)) {
                RateLimitBucket b = new RateLimitBucket(now);
                isAllowed.set(b.tryConsume());
                return b;
            }
            isAllowed.set(bucket.tryConsume());
            return bucket;
        });
        return isAllowed.get();
    }

    //Seconds until the client's bucket resets; used for the Retry-After header
    private long secondsUntilReset(String clientIp) {
        RateLimitBucket bucket = buckets.get(clientIp);
        if (bucket == null) {
            return BUCKET_DURATION_MS / 1000; //Bucket already expired — full window applies
        }
        return bucket.secondsUntilExpiry(System.currentTimeMillis());
    }

    @Override
    public void destroy() {
        //Stop the cleanup thread so redeploys don't leak it (and the old classloader)
        Thread thread = cleanupThread;
        if (thread != null) {
            thread.interrupt();
            cleanupThread = null;
        }
    }
    private static class RateLimitBucket {
        private final long createdAt;
        private final AtomicInteger remaining;

        public RateLimitBucket(long createdAt) {
            this.createdAt = createdAt;
            this.remaining = new AtomicInteger(REQUESTS_PER_MINUTE);
        }

        public boolean isExpired(long now) {
            return (now - createdAt) > BUCKET_DURATION_MS;
        }

        //Remaining seconds until expiry, rounded up, at least 1
        public long secondsUntilExpiry(long now) {
            long remainingMs = BUCKET_DURATION_MS - (now - createdAt);
            return Math.max(1, (remainingMs + 999) / 1000);
        }

        public boolean tryConsume() {
            return remaining.decrementAndGet() >= 0;
        }
    }
}
