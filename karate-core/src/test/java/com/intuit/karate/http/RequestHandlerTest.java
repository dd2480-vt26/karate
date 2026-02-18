package com.intuit.karate.http;

import com.intuit.karate.Match;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class RequestHandlerTest {

    static final Logger logger = LoggerFactory.getLogger(RequestHandlerTest.class);

    RequestHandler handler;
    HttpRequestBuilder request;
    Response response;
    List<String> cookies;
    String body;

    @BeforeEach
    void beforeEach() {
        ServerConfig config = new ServerConfig("classpath:demo");
        config.autoCreateSession(true);
        handler = new RequestHandler(config);
        request = new HttpRequestBuilder(null).url("/").method("GET");
    }

    private Response handle() {
        response = handler.handle(request.build().toRequest());
        body = response.getBodyAsString();
        cookies = response.getHeaderValues("Set-Cookie");
        request = new HttpRequestBuilder(null).url("/").method("GET");
        if (cookies != null) {
            request.header("Cookie", cookies);
        }
        return response;
    }

    private void matchHeaderEquals(String name, String expected) {
        Match.Result mr = Match.evaluate(response.getHeader(name)).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
    }

    private void matchHeaderContains(String name, String expected) {
        Match.Result mr = Match.evaluate(response.getHeader(name)).contains(expected);
        assertTrue(mr.pass, mr.message);
    }

    @Test
    void testIndexAndAjaxPost() {
        request.path("index");
        handle();
        matchHeaderContains("Set-Cookie", "karate.sid");
        matchHeaderEquals("Content-Type", "text/html");
        assertTrue(body.startsWith("<!doctype html>"));
        assertTrue(body.contains("<span>John Smith</span>"));
        assertTrue(body.contains("<td>Apple</td>"));
        assertTrue(body.contains("<td>Orange</td>"));
        assertTrue(body.contains("<span>Billie</span>"));
        request.path("person")
                .contentType("application/x-www-form-urlencoded")
                .header("HX-Request", "true")
                .body("firstName=John&lastName=Smith&email=john%40smith.com")
                .method("POST");
        handle();
        assertTrue(body.contains("<span>John</span>"));
    }

    // ---------- Additional tests to improve branch coverage of handle() ----------

    /**
     * R1: If the request path starts with the host context path, it should be stripped before handling.
     */
    @Test
    void shouldStripHostContextPath_WhenRequestPathStartsWithIt() {
        ServerConfig config = new ServerConfig("classpath:demo");
        config.stripContextPathFromRequest(true);
        config.hostContextPath("/context");
        config.autoCreateSession(true); 
        handler = new RequestHandler(config);

        HttpRequestBuilder rb = new HttpRequestBuilder(null).url("/context/index").method("GET");
        response = handler.handle(rb.build().toRequest());

        // If the context path was stripped correctly, the handler should find the "/index" page
        // and return a successful response
        assertEquals(200, response.getStatus());

        // "/index" should be an HTML page
        matchHeaderEquals("Content-Type", "text/html");

        // The response body should contain HTML content
        // Verifies that the stripped path mapped to a valid resource
        assertTrue(response.getBodyAsString().startsWith("<!doctype html>"));
    }
    
    /**
     * R2: If the request path does not start with the context path, it should not be stripped.
     */
    @Test
    void shouldNotStripHostContextPath_WhenRequestPathDoesNotStartWithIt() {
        ServerConfig config = new ServerConfig("classpath:demo");
        config.stripContextPathFromRequest(true);
        config.hostContextPath("/context");
        config.autoCreateSession(true);
        handler = new RequestHandler(config);

        HttpRequestBuilder rb = new HttpRequestBuilder(null).url("/other/index").method("GET");
        Response res = handler.handle(rb.build().toRequest());

        // The server shouldn't be able to find the resource
        assertNotEquals(200, res.getStatus());
    }

    /**
     * R3: When global session is enabled, requests without a session should not be redirected.
     */
    @Test
    void shouldServeRequestWithoutRedirect_WhenGlobalSessionEnabled() {
        ServerConfig config = new ServerConfig("classpath:demo");
        config.useGlobalSession(true);
        config.autoCreateSession(false);
        handler = new RequestHandler(config);

        HttpRequestBuilder rb = new HttpRequestBuilder(null).url("/index").method("GET");
        Response res = handler.handle(rb.build().toRequest());

        // With global session enabled, the handler should allow the request to proceed
        // instead of redirecting due to a missing session
        assertEquals(200, res.getStatus());
        
        // Confirms that the handler didn't redirect by checking the Location header
        assertNull(res.getHeader("Location"));

        // The response body should contain HTML content
        // Verifies that the stripped path mapped to a valid resource
        assertTrue(res.getBodyAsString().startsWith("<!doctype html>"));
    }

    /**
     * R4: Requests to /signin without a session should not be redirected.
     */
    @Test
    void shouldNotRedirect_WhenRequestIsSigninPage() {
        ServerConfig config = new ServerConfig("classpath:demo");
        config.useGlobalSession(false);
        config.autoCreateSession(false);
        config.signinPagePath("/signin");
        config.signoutPagePath("/signout");
        handler = new RequestHandler(config);

        HttpRequestBuilder rb = new HttpRequestBuilder(null).url("/signin").method("GET");
        Response res = handler.handle(rb.build().toRequest());

        // When the user requests the sign-in page, the handler should not redirect them to sign-in again.
        // We verify that the response status is not 302 (redirect)
        assertNotEquals(302, res.getStatus());
    }

    /**
     * R5: Requests to /signout without a session should not be redirected.
     */
    @Test
    void shouldNotRedirect_WhenRequestIsSignoutPage() {
        ServerConfig config = new ServerConfig("classpath:demo");
        config.useGlobalSession(false);
        config.autoCreateSession(false);
        config.signinPagePath("/signin");
        config.signoutPagePath("/signout");
        handler = new RequestHandler(config);

        HttpRequestBuilder rb = new HttpRequestBuilder(null).url("/signout").method("GET");
        Response res = handler.handle(rb.build().toRequest());

        // When the user requests the sign-out page, the handler should not redirect them to sign-in.
        // We verify that the response status is not 302 (redirect)
        assertNotEquals(302, res.getStatus());
    }

    /**
     * R6: Requests to other pages without a session should be redirected to sign-in.
     */
    @Test
    void shouldRedirectToSignin_WhenRequestIsUnauthenticatedAndNotSigninOrSignout() {
        ServerConfig config = new ServerConfig("classpath:demo");
        config.useGlobalSession(false);
        config.autoCreateSession(false);
        config.signinPagePath("/signin");
        config.signoutPagePath("/signout");
        handler = new RequestHandler(config);

        HttpRequestBuilder rb = new HttpRequestBuilder(null).url("/other").method("GET");
        Response res = handler.handle(rb.build().toRequest());

        // Requests to any other pages than /signin or /signout without a valid session
        // should be redirected to the sign-in page
        // We verify that a redirect has occured
        assertEquals(302, res.getStatus());

        // Verify redirection to /signin
        assertTrue(res.getHeader("Location").contains("/signin"));
    }

}
