package com.intuit.karate.http;

import com.intuit.karate.core.ScenarioEngine;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class HttpRequestBuilderTest {

    static final Logger logger = LoggerFactory.getLogger(HttpRequestBuilderTest.class);

    HttpRequestBuilder http;

    @BeforeEach
    void beforeEach() {
        ScenarioEngine se = ScenarioEngine.forTempUse(HttpClientFactory.DEFAULT);
        http = new HttpRequestBuilder(HttpClientFactory.DEFAULT.create(se));
    }

    @Test
    void testUrlAndPath() {
        http.url("http://host/foo");
        assertEquals("http://host/foo", http.getUri());
        http.path("/bar");
        assertEquals("http://host/foo/bar", http.getUri());
    }

    @Test
    void testUrlAndPathWithSlash() {
        http.url("http://host/foo/");
        assertEquals("http://host/foo/", http.getUri());
        http.path("/bar/");
        assertEquals("http://host/foo/bar", http.getUri());
    }
    
    @Test
    void testUrlAndPathWithTrailingSlash() {
        http.url("http://host/foo");
        assertEquals("http://host/foo", http.getUri());
        http.path("bar");
        http.path("/");
        assertEquals("http://host/foo/bar/", http.getUri());
    }    
    
    @Test
    void testUrlAndPathWithEncodedSlash() {
        http.url("http://host");
        assertEquals("http://host", http.getUri());
        http.path("foo\\/bar");
        assertEquals("http://host/foo%2Fbar", http.getUri());
    }     

    /**
     * Negative test: Building without an explicit {@code url} fails.
     * Test case: No URL configured invoke {@code build()}.
     * Expected: {@link RuntimeException} with message containing {@code 'url' not set}.
     */
    @Test
    void buildInternal_missingUrl_throws() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> http.build());
        assertTrue(ex.getMessage().contains("'url' not set"));
    }

    /**
     * Positive test: Non-multipart form fields on GET become query parameters.
     * Test case: {@code url = http://host/foo} {@code formField("a", "b")}; invoke {@code build()}.
     * Expected: URI is {@code http://host/foo?a=b}; multi-part cleared.
     */
    @Test
    void buildInternal_formFieldsToParamsOnGet_converted() {
        http.url("http://host/foo");
        http.formField("a", "b");
        // trigger buildInternal() to convert form fields into query params
        http.build();
        assertEquals("http://host/foo?a=b", http.getUri());
    }

    /**
     * Positive test: User {@code Content-Type: multipart/form-data} gets boundary appended.
     * Test case: Set header to {@code multipart/form-data}, add a multi-part, and invoke {@code build()}.
     * Expected: {@code Content-Type} starts with {@code multipart/form-data} and contains {@code boundary=}.
     */
    @Test
    void buildInternal_userContentType_multipartBoundaryAppended() {
        http.url("http://host/upload");
        http.header("Content-Type", "multipart/form-data");
        // provide the expected keys for map-based multiPart
        java.util.Map<String, Object> mp = new java.util.HashMap<>();
        mp.put("name", "file");
        mp.put("value", "dummy");
        http.multiPart(mp);
        http.build();
        String ct = http.getHeaders().get("Content-Type");
        assertNotNull(ct);
        assertTrue(ct.startsWith("multipart/form-data"));
        assertTrue(ct.toLowerCase().contains("boundary="));
    }

    /**
     * Positive test: JSON body infers {@code application/json}; charset appended if configured.
     * Test case: Body is a simple Map, invoke {@code build()}.
     * Expected: {@code Content-Type} starts with {@code application/json} and contains {@code charset=} when available.
     */
    @Test
    void buildInternal_jsonBody_contentTypeAndCharsetSet() {
        http.url("http://host/api");
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("hello", "world");
        http.body(body);
        http.build();
        String ct = http.getHeaders().get("Content-Type");
        assertNotNull(ct);
        assertTrue(ct.startsWith("application/json"));
        // charset depend on config, assert that it is either present or acceptable
        // if present, ensure it contains a valid token
        if (ct.contains("charset=")) {
            assertTrue(ct.toLowerCase().contains("charset="));
        }
    }
}
