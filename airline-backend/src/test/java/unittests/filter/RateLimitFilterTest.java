package unittests.filter;

import com.airline.airlinebackend.filter.RateLimitFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link RateLimitFilter}.
 *
 * The filter keeps its buckets in a static map keyed by client IP, so every
 * test resets that map in {@link #resetBuckets()} to stay order-independent.
 * Window expiry is exercised by seeding an already-stale bucket through
 * reflection (the filter reads the wall clock directly); everything else is
 * plain behavioral verification through {@code doFilter}.
 *
 * init/destroy (the cleanup thread) are deliberately not tested: expiry is
 * enforced inline by the bucket lookup, so the cleanup thread only affects
 * memory, not correctness.
 */
@DisplayName("RateLimitFilter")
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    //Mirrors RateLimitFilter.REQUESTS_PER_MINUTE
    private static final int REQUESTS_PER_MINUTE = 60;
    private static final long BUCKET_DURATION_MS = 60_000;

    private static final String TEST_IP = "192.168.1.10";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @Mock
    private PrintWriter writer;

    @Captor
    private ArgumentCaptor<String> bodyCaptor;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new RateLimitFilter();
        resetBuckets();
    }

    // -- test infrastructure ------------------------------------------------

    //Static state shared across the whole JVM — reset it so tests stay order-independent
    @SuppressWarnings("unchecked")
    private static Map<String, Object> buckets() throws Exception {
        Field field = RateLimitFilter.class.getDeclaredField("buckets");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(null);
    }

    private void resetBuckets() throws Exception {
        buckets().clear();
    }

    //Test setup, not behavior testing: age a bucket past the window so expiry
    //can be exercised without sleeping for a real minute
    private void seedExpiredBucket(String ip) throws Exception {
        Class<?> bucketClass = Class.forName(
                "com.airline.airlinebackend.filter.RateLimitFilter$RateLimitBucket");
        Constructor<?> ctor = bucketClass.getDeclaredConstructor(long.class);
        ctor.setAccessible(true);
        Object staleBucket = ctor.newInstance(System.currentTimeMillis() - BUCKET_DURATION_MS - 1_000);
        buckets().put(ip, staleBucket);
    }

    //Requests resolve to getRemoteAddr() when no forwarding headers are present
    private void sendRequests(int count, String ip) throws Exception {
        given(request.getRemoteAddr()).willReturn(ip);
        for (int i = 0; i < count; i++) {
            filter.doFilter(request, response, chain);
        }
    }

    private void exhaustLimit(String ip) throws Exception {
        sendRequests(REQUESTS_PER_MINUTE, ip);
    }

    // -- 1. requests allowed ------------------------------------------------

    @Nested
    @DisplayName("allowed requests")
    class AllowedRequests {

        @Test
        @DisplayName("first request from a new IP passes and creates a bucket")
        void firstRequestFromNewIpPassesAndCreatesBucket() throws Exception {
            sendRequests(1, TEST_IP);

            then(chain).should(times(1)).doFilter(request, response);
            assertThat(buckets()).hasSize(1);
        }

        @Test
        @DisplayName("requests at half the limit still pass")
        void requestAtHalfLimitStillPasses() throws Exception {
            sendRequests(REQUESTS_PER_MINUTE / 2, TEST_IP);

            then(chain).should(times(REQUESTS_PER_MINUTE / 2)).doFilter(request, response);

            //the second half of the quota is also still available
            sendRequests(REQUESTS_PER_MINUTE / 2, TEST_IP);
            then(chain).should(times(REQUESTS_PER_MINUTE)).doFilter(request, response);
        }

        @Test
        @DisplayName("request number 60 (exactly at the limit) is the last one allowed")
        void requestAtExactLimitBoundaryPasses() throws Exception {
            sendRequests(REQUESTS_PER_MINUTE, TEST_IP);

            then(chain).should(times(REQUESTS_PER_MINUTE)).doFilter(request, response);
            then(response).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("an allowed request touches neither status nor headers nor body")
        void allowedRequestDoesNotTouchResponse() throws Exception {
            sendRequests(1, TEST_IP);

            verifyNoInteractions(response);
        }
    }

    // -- 2. requests denied -------------------------------------------------

    @Nested
    @DisplayName("denied requests")
    class DeniedRequests {

        @Test
        @DisplayName("request 61 is denied with 429 and never reaches the chain")
        void requestOverLimitIsDeniedWith429() throws Exception {
            exhaustLimit(TEST_IP);
            given(response.getWriter()).willReturn(writer);

            filter.doFilter(request, response, chain);

            then(response).should().setStatus(429);
            then(response).should().setContentType("application/json");
            then(chain).should(times(REQUESTS_PER_MINUTE)).doFilter(request, response);
        }

        @Test
        @DisplayName("denial body carries the RATE_LIMIT_EXCEEDED error payload")
        void denialBodyContainsErrorPayload() throws Exception {
            exhaustLimit(TEST_IP);
            given(response.getWriter()).willReturn(writer);

            filter.doFilter(request, response, chain);

            then(writer).should().write(bodyCaptor.capture());
            assertThat(bodyCaptor.getValue())
                    .contains("RATE_LIMIT_EXCEEDED")
                    .contains("Too many requests");
        }

        @Test
        @DisplayName("Retry-After header holds seconds until reset, bounded to [1, 60]")
        void retryAfterHeaderWithinBounds() throws Exception {
            exhaustLimit(TEST_IP);
            given(response.getWriter()).willReturn(writer);

            filter.doFilter(request, response, chain);

            ArgumentCaptor<String> headerValue = ArgumentCaptor.forClass(String.class);
            then(response).should().setHeader(eq("Retry-After"), headerValue.capture());
            long retryAfter = Long.parseLong(headerValue.getValue());
            assertThat(retryAfter).isBetween(1L, BUCKET_DURATION_MS / 1000);
        }

        @Test
        @DisplayName("requests after a denial keep being denied")
        void subsequentRequestsAfterDenialStillDenied() throws Exception {
            exhaustLimit(TEST_IP);
            given(response.getWriter()).willReturn(writer);

            for (int i = 0; i < 5; i++) {
                filter.doFilter(request, response, chain);
            }

            then(chain).should(times(REQUESTS_PER_MINUTE)).doFilter(request, response);
            then(response).should(times(5)).setStatus(429);
        }
    }

    // -- 3. bucket lifecycle ------------------------------------------------

    @Nested
    @DisplayName("bucket lifecycle")
    class BucketLifecycle {

        @Test
        @DisplayName("expired bucket that cleanup hasn't reached still grants a fresh window")
        void expiredBucketGrantsFreshWindow() throws Exception {
            seedExpiredBucket(TEST_IP);
            given(request.getRemoteAddr()).willReturn(TEST_IP);

            //first request after expiry passes and replaces the stale bucket
            filter.doFilter(request, response, chain);
            then(chain).should(times(1)).doFilter(request, response);

            //the fresh window holds the full quota again
            for (int i = 1; i < REQUESTS_PER_MINUTE; i++) {
                filter.doFilter(request, response, chain);
            }
            then(chain).should(times(REQUESTS_PER_MINUTE)).doFilter(request, response);

            //and the next request over the fresh quota is denied
            given(response.getWriter()).willReturn(writer);
            filter.doFilter(request, response, chain);
            then(response).should().setStatus(429);
        }

        @Test
        @DisplayName("bucket already removed by the cleanup thread simply starts a new one")
        void bucketRemovedByCleanupAllowsRequest() throws Exception {
            //the map is empty — same state the cleanup thread leaves behind
            sendRequests(1, TEST_IP);

            then(chain).should(times(1)).doFilter(request, response);
            assertThat(buckets()).hasSize(1);
        }
    }

    // -- 4. client isolation ------------------------------------------------

    @Nested
    @DisplayName("client isolation")
    class ClientIsolation {

        @Test
        @DisplayName("an exhausted IP does not affect a different client")
        void exhaustedIpDoesNotAffectOtherClients() throws Exception {
            exhaustLimit("10.0.0.1");
            given(response.getWriter()).willReturn(writer);
            filter.doFilter(request, response, chain);
            then(response).should().setStatus(429);

            given(request.getRemoteAddr()).willReturn("10.0.0.2");
            filter.doFilter(request, response, chain);
            then(chain).should(times(REQUESTS_PER_MINUTE + 1)).doFilter(request, response);
        }

        @Test
        @DisplayName("mixed traffic: only clients over the limit get blocked")
        void mixedTrafficBlocksOnlyClientsOverTheLimit() throws Exception {
            given(response.getWriter()).willReturn(writer);

            //10.0.0.1 pushes past the limit
            sendRequests(REQUESTS_PER_MINUTE + 1, "10.0.0.1");
            //10.0.0.2 stays comfortably under
            sendRequests(30, "10.0.0.2");
            //10.0.0.3 lands exactly at the boundary
            sendRequests(REQUESTS_PER_MINUTE, "10.0.0.3");

            //60 allowed for .1, 30 for .2, 60 for .3 — the 61st from .1 was dropped
            then(chain).should(times(150)).doFilter(request, response);
            assertThat(buckets()).hasSize(3);
        }

        @Test
        @DisplayName("buckets are keyed on the forwarded client IP, not the proxy address")
        void rateLimitKeyedOnForwardedClientIp() throws Exception {
            given(request.getHeader("X-Forwarded-For")).willReturn("1.1.1.1, 2.2.2.2");
            for (int i = 0; i < REQUESTS_PER_MINUTE; i++) {
                filter.doFilter(request, response, chain);
            }

            given(request.getHeader("X-Forwarded-For")).willReturn("3.3.3.3");
            filter.doFilter(request, response, chain);

            //1.1.1.1 exhausted its quota, 3.3.3.3 arrives fresh — both behind one proxy
            then(chain).should(times(REQUESTS_PER_MINUTE + 1)).doFilter(request, response);
            assertThat(buckets()).hasSize(2);
        }

        @Test
        @DisplayName("a concurrent burst from one IP admits exactly the limit")
        void concurrentBurstAllowsExactlyLimitRequests() throws Exception {
            int threads = 100;
            given(request.getRemoteAddr()).willReturn(TEST_IP);
            given(response.getWriter()).willReturn(writer);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            try {
                for (int i = 0; i < threads; i++) {
                    pool.submit(() -> {
                        try {
                            start.await();
                            filter.doFilter(request, response, chain);
                        } catch (Exception e) {
                            //denied requests still reach the writer; a failure
                            //here surfaces through the latch timeout below
                        } finally {
                            done.countDown();
                        }
                        return null;
                    });
                }
                start.countDown();

                assertThat(done.await(5, TimeUnit.SECONDS))
                        .as("all %d requests should complete", threads)
                        .isTrue();

                then(chain).should(times(REQUESTS_PER_MINUTE)).doFilter(request, response);
                assertThat(buckets()).hasSize(1);
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
