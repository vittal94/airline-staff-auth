package unittests.util;

import com.airline.airlinebackend.config.AppConfig;
import com.airline.airlinebackend.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CookieUtil")
public class CookieUtilTest {
    private static final String SET_COOKIE = "Set-Cookie";
    private static final String SESSION_COOKIE = "AIRLINE_SESSION";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String PATH = "/";
    private static final String SAME_SITE = "Strict";

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * Captures the single {@code Set-Cookie} header that was added to the response
     * and asserts it was the only interaction with the mock.
     */
    private String captureRawSetCookieHeader() {
        ArgumentCaptor<String> nameCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCap = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(nameCap.capture(), valueCap.capture());
        assertThat(nameCap.getValue()).isEqualTo(SET_COOKIE);
        verifyNoMoreInteractions(response);
        return valueCap.getValue();
    }

    /** Captures the header and parses it into a {@link CookieHeader} record. */
    private CookieHeader captureParsedHeader() {
        return CookieHeader.parse(captureRawSetCookieHeader());
    }

    @Nested
    @DisplayName("getCookieValue()")
    class GetCookieValue {
        @Test
        @DisplayName("return empty when request has no cookie at all")
        void returnsEmptyWhenCookieAreNull() {
            when(request.getCookies()).thenReturn(null);

            Optional<String> result = CookieUtil.getCookieValue(request, "SESSION");

            assertAll(
                    () -> assertThat(result).isEmpty(),
                    () -> verify(request).getCookies()
            );

        }

        @Test
        @DisplayName("return empty when cookie array is empty")
        void returnsEmptyWhenCookieArrayIsEmpty() {
          when(request.getCookies()).thenReturn(new Cookie[0]);

          Optional<String> result = CookieUtil.getCookieValue(request, "SESSION");

          assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("return empty when no cookie matches the requested name")
        void returnsEmptyWhenNoNameMatch() {
            when(request.getCookies()).thenReturn(new Cookie[]{
                    new Cookie("session1","123"),
                    new Cookie("authToken","@df45"),
            });

            Optional<String> result = CookieUtil.getCookieValue(request, "SESSION");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns the value when cookie name is present")
        void returnsTheValueWhenCookieNameIsPresent() {
            when(request.getCookies()).thenReturn(new Cookie[]{
                    new Cookie("SESSION","@123"),
            });

            Optional<String> result = CookieUtil.getCookieValue(request, "SESSION");

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo("@123");
        }

        @Test
        @DisplayName("select the right cookie among the several")
        void selectRightCookie() {
            when(request.getCookies()).thenReturn(new Cookie[]{
                    new Cookie("A","1"),
                    new Cookie("B","2"),
                    new Cookie("C","3"),
                    new Cookie("D","4"),
            });

            Optional<String> result = CookieUtil.getCookieValue(request, "B");

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo("2");
        }

        @Test
        @DisplayName("returns the first matching value when multiple cookies share a name")
        void returnsTheFirstMatchingValueWhenMultipleCookies() {
            when(request.getCookies()).thenReturn(new Cookie[]{
                    new Cookie("SESSION","@123"),
                    new Cookie("SESSION","@ABC"),
            });

            Optional<String> result = CookieUtil.getCookieValue(request, "SESSION");

            assertThat(result).isPresent();
            assertThat(result.get()).contains("@123");
        }

        @Test
        @DisplayName("preserves empty value as Optional containing empty string (not Optional.empty)")
        void preservesEmptyValue() {
            when(request.getCookies()).thenReturn(new Cookie[]{
                    new Cookie("SESSION", "")
            });

            Optional<String> result = CookieUtil.getCookieValue(request, "SESSION");

            assertThat(result).isPresent();
            assertThat(result.get()).isEmpty();
        }

        @Test
        @DisplayName("preserves Unicode and special characters in cookie value")
        void preservesUnicodeAndSpecialChars() {
            String value = "tok en+val&x=y;z?#фрагмент";
            when(request.getCookies()).thenReturn(new Cookie[]{
                    new Cookie("SESSION", value)
            });

            Optional<String> result = CookieUtil.getCookieValue(request, "SESSION");

            assertThat(result).contains(value);
        }

        @Test
        @DisplayName("does not match cookies whose name is a prefix of the requested name")
        void doesNotMatchPrefixOfRequestedName() {
            when(request.getCookies()).thenReturn(new Cookie[]{
                    new Cookie("SESSION_EXTRA", "wrong")
            });

            Optional<String> result = CookieUtil.getCookieValue(request, "SESSION");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("setSecureCookie()")
    class SetSecureCookie {
        @Test
        @DisplayName("write Set-cookie header with all attributes")
        void writeSetCookieHeaderWithAllAttributes() {
            CookieUtil.setSecureCookie(response,SESSION_COOKIE, "token-xyt", 1800);

            CookieHeader header = captureParsedHeader();

            assertAll(
                    () -> assertThat(header.name()).isEqualTo(SESSION_COOKIE),
                    () -> assertThat(header.value()).isEqualTo("token-xyt"),
                    () -> assertThat(header.path()).isEqualTo(PATH),
                    () -> assertThat(header.httpOnly()).isTrue(),
                    () -> assertThat(header.secure()).isTrue(),
                    () -> assertThat(header.sameSite()).isEqualTo(SAME_SITE),
                    () -> assertThat(header.maxAge()).isEqualTo(1800)

            );
        }
        @Test
        @DisplayName("produces the canonical header string for happy path ")
        void canonicalHeaderStringForHappyPath() {
            CookieUtil.setSecureCookie(response,SESSION_COOKIE, "token-xyz", 1800);

            String raw = captureRawSetCookieHeader();

            assertThat(raw)
                    .isEqualTo(
                    "AIRLINE_SESSION=token-xyz; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=1800"
            );
        }

        @ParameterizedTest
        @ValueSource(ints =  {-1, 0, 1, 60, 1800, 86_400, 2_592_000, Integer.MAX_VALUE})
        @DisplayName("reflects every supported value in the header")
        void reflectsValueInHeader(int maxAge) {
            CookieUtil.setSecureCookie(response,SESSION_COOKIE, "token-xyz", maxAge);

            CookieHeader header = captureParsedHeader();

            assertAll(
                    () -> assertThat(header.maxAge()).isEqualTo(maxAge),
                    () -> assertThat(header.httpOnly()).isTrue(),
                    () -> assertThat(header.secure()).isTrue(),
                    () -> assertThat(header.path()).isEqualTo(PATH),
                    () -> assertThat(header.sameSite()).isEqualTo(SAME_SITE)
            );
        }

        @Test
        @DisplayName("always sets Path=/ regardless of caller-provided name or value")
        void alwaysSetsRootPath() {
            CookieUtil.setSecureCookie(response, "ANY_NAME", "any-value", 60);

            CookieHeader header = captureParsedHeader();

            assertThat(header.path()).isEqualTo("/");
        }

        @Test
        @DisplayName("preserves empty value as NAME=;")
        void preservesEmptyValue() {
            CookieUtil.setSecureCookie(response, SESSION_COOKIE, "", 60);

            CookieHeader header = captureParsedHeader();

            assertAll(
                    () -> assertThat(header.name()).isEqualTo(SESSION_COOKIE),
                    () -> assertThat(header.value()).isEmpty()
            );
        }

        @Test
        @DisplayName("preserves Unicode and special characters in the cookie value")
        void preservesUnicodeAndSpecialChars() {
            String value = "tok en+val&x=yz?#фрагмент";
            CookieUtil.setSecureCookie(response, SESSION_COOKIE, value, 60);

            CookieHeader header = captureParsedHeader();

            assertThat(header.value()).isEqualTo(value);
        }

        @Test
        @DisplayName("invokes the response.addHeader() exactly one time with name Set-Cookie")
        void invokesAddHeaderOnce() {
            CookieUtil.setSecureCookie(response, SESSION_COOKIE, "v", 60);

            verify(response).addHeader(eq(SET_COOKIE), anyString());
            verifyNoMoreInteractions(response);
        }
    }

    @Nested
    @DisplayName("setSCRFCookie()")
    class SetSCRFCookie {
        @Test
        @DisplayName("uses cookie name resolved from AppConfig (XSRF-TOKEN)")
        void usesCookieName() {
            CookieUtil.setCsrfCookie(response,"csrf-token-value",3600);

            CookieHeader header = captureParsedHeader();

            assertThat(header.name()).isEqualTo(CSRF_COOKIE);
        }

        @Test
        @DisplayName("omits the httpOnly flag so JS can read the cookie")
        void omitTheHttpOnlyFlag() {
           CookieUtil.setCsrfCookie(response,"value",123);

           CookieHeader header = captureParsedHeader();

           assertThat(header.httpOnly()).isFalse();
        }

        @Test
        @DisplayName("still applies Secure, Path, SameSite, Max-Age")
        void appliesSecurePathSameSiteMaxAge() {
            CookieUtil.setCsrfCookie(response,"value",123);

            CookieHeader header = captureParsedHeader();

            assertAll(
                    () -> assertThat(header.sameSite).isEqualTo(SAME_SITE),
                    () -> assertThat(header.secure).isTrue(),
                    () -> assertThat(header.path).isEqualTo(PATH),
                    () -> assertThat(header.maxAge).isEqualTo(123)
            );
        }

        @Test
        @DisplayName("produces the canonical header string")
        void producesCanonicalHeaderString() {
            CookieUtil.setCsrfCookie(response, "csrf-token-value", 3600);

            String raw = captureRawSetCookieHeader();

            assertThat(raw).isEqualTo(
                    "XSRF-TOKEN=csrf-token-value; Path=/; Secure; SameSite=Strict; Max-Age=3600"
            );
        }

        @Test
        @DisplayName("invokes addHeader exactly once with a name 'Set-Cookie'")
        void invokesAddHeaderOnceWithAName() {
            CookieUtil.setCsrfCookie(response,"value",3600);

            verify(response).addHeader(eq(SET_COOKIE), anyString());
            verifyNoMoreInteractions(response);
        }
    }

    @Nested
    @DisplayName("deleteCookie()")
    class DeleteCookie {
        @Test
        @DisplayName("single-arg overload produces Max-Age=0 with HttpOnly=true")
        void singleArgDefaultsToHttpOnlyTrue() {
            CookieUtil.deleteCookie(response, SESSION_COOKIE);

            CookieHeader header = captureParsedHeader();

            assertAll(
                    () -> assertThat(header.name()).isEqualTo(SESSION_COOKIE),
                    () -> assertThat(header.value()).isEmpty(),
                    () -> assertThat(header.maxAge()).isZero(),
                    () -> assertThat(header.httpOnly()).isTrue(),
                    () -> assertThat(header.secure()).isTrue(),
                    () -> assertThat(header.sameSite()).isEqualTo(SAME_SITE),
                    () -> assertThat(header.path()).isEqualTo(PATH)
            );
        }

        @Test
        @DisplayName("single-arg overload produces the canonical deletion header string")
        void singleArgProducesCanonicalString() {
            CookieUtil.deleteCookie(response, SESSION_COOKIE);

            String raw = captureRawSetCookieHeader();

            assertThat(raw).isEqualTo(
                    "AIRLINE_SESSION=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0"
            );
        }

        @Test
        @DisplayName("two-arg overload with httpOnly=true matches single-arg semantics")
        void twoArgWithHttpOnlyTrueMatchesSingleArg() {
            CookieUtil.deleteCookie(response, SESSION_COOKIE, true);

            CookieHeader header = captureParsedHeader();

            assertAll(
                    () -> assertThat(header.httpOnly()).isTrue(),
                    () -> assertThat(header.maxAge()).isZero(),
                    () -> assertThat(header.secure()).isTrue(),
                    () -> assertThat(header.sameSite()).isEqualTo(SAME_SITE)
            );
        }

        @Test
        @DisplayName("two-arg overload with httpOnly=false omits HttpOnly (used for CSRF deletion)")
        void twoArgWithHttpOnlyFalseOmitsHttpOnly() {
            CookieUtil.deleteCookie(response, CSRF_COOKIE, false);

            CookieHeader header = captureParsedHeader();

            assertAll(
                    () -> assertThat(header.name()).isEqualTo(CSRF_COOKIE),
                    () -> assertThat(header.httpOnly()).isFalse(),
                    () -> assertThat(header.maxAge()).isZero(),
                    () -> assertThat(header.secure()).isTrue(),
                    () -> assertThat(header.sameSite()).isEqualTo(SAME_SITE),
                    () -> assertThat(header.path()).isEqualTo(PATH)
            );
        }

        @Test
        @DisplayName("invokes response.addHeader exactly once with name 'Set-Cookie'")
        void invokesAddHeaderExactlyOnce() {
            CookieUtil.deleteCookie(response, SESSION_COOKIE);

            verify(response).addHeader(eq(SET_COOKIE), anyString());
            verifyNoMoreInteractions(response);
        }
    }


    // ============================================================
    // Inner: CookieHeader record + parser
    // ============================================================

    /**
     * Parsed view of a {@code Set-Cookie} header string for attribute-level assertions.
     * Tolerant of attribute ordering and whitespace, strict about presence/values.
     */
    private record CookieHeader(
            String name,
            String value,
            String path,
            boolean httpOnly,
            boolean secure,
            String sameSite,
            int maxAge
    ) {
        static CookieHeader parse(String header) {
            String[] parts = header.split(";");
            int eq = parts[0].indexOf('=');
            String name = parts[0].substring(0, eq);
            String value = parts[0].substring(eq + 1);

            String path = null;
            boolean httpOnly = false;
            boolean secure = false;
            String sameSite = null;
            int maxAge = Integer.MIN_VALUE;

            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if(part.equals("HttpOnly")) {
                    httpOnly = true;
                } else if(part.equals("Secure")) {
                    secure = true;
                } else if(part.startsWith("Path=")) {
                    path = part.substring("Path=".length());
                } else if(part.startsWith("SameSite=")) {
                    sameSite = part.substring("SameSite=".length());
                } else if(part.startsWith("Max-Age=")) {
                    maxAge = Integer.parseInt(part.substring("Max-Age=".length()));
                }
            }
            return new CookieHeader(name, value, path, httpOnly, secure, sameSite, maxAge);
        }
    }
}
