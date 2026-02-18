/*
 * The MIT License
 *
 * Copyright 2022 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.http;

import com.intuit.karate.template.KarateTemplateEngine;
import com.intuit.karate.template.TemplateUtils;
import java.time.Instant;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class RequestHandler implements ServerHandler {

    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);

    private static final String SLASH = "/";

    private final SessionStore sessionStore;
    private final KarateTemplateEngine templateEngine;
    private final ServerConfig config;
    private final Function<Request, ServerContext> contextFactory;
    private final String stripHostContextPath;

    public RequestHandler(ServerConfig config) {
        this.config = config;
        contextFactory = config.getContextFactory();
        templateEngine = TemplateUtils.forServer(config);
        sessionStore = config.getSessionStore();
        stripHostContextPath = config.isStripContextPathFromRequest() ? config.getHostContextPath() : null;
    }

    // ---------- Refactoring of handle() ----------

    /**
     * Strips the context path from the beginning of the request path.
     *
     * @param request the incoming request
     */
    private void stripContextPath(Request request) {
        if (stripHostContextPath != null) {
            if (request.getPath().startsWith(stripHostContextPath)) {
                request.setPath(request.getPath().substring(stripHostContextPath.length()));
            }
        }
    }

    /**
     * Resolves the home page path for requests to "/".
     *
     * @param request the incoming request
     */
    private void resolveHomePage(Request request) {
        if (SLASH.equals(request.getPath())) {
            request.setPath(config.getHomePagePath());
        }
    }

    /**
     * Assigns a resource type to the request.
     *
     * @param request the incoming request
     */
    private void assignResourceType(Request request) {
        if (request.getResourceType() == null) {
            request.setResourceType(ResourceType.fromFileExtension(request.getPath()));
        }
    }

    /**
     * Attempts to serve the request as a static resource.
     *
     * @param context the server context for this request
     * @param request the incoming request 
     * @return a response with the requested static resource
     */
    private Response tryServingStaticResource(ServerContext context, Request request) {
        if (!context.isApi() && request.isHttpGetForStaticResource() && context.isHttpGetAllowed()) {
            if (request.getResourcePath() == null) { 
                request.setResourcePath(request.getPath());
            }
            try {
                return response().buildStatic(request);
            } finally {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} {} [{} ms]", request, 200, System.currentTimeMillis() - request.getStartTime());
                }
            }
        }

        return null;
    }

    /**
     * Resolves the session for a request, or returns a redirect response if no session is available 
     *
     * @param session the current session from the context
     * @param context the server context
     * @param request the incoming request
     * @return a redirect response (302) if authentication is required, {@code null} otherwise
     */
    private Response resolveSessionOrRedirect(Session session, ServerContext context, Request request) {
        String sessionId = context.getSessionCookieValue();
        if (sessionId != null) {
            session = sessionStore.get(sessionId);
            if (session != null && isExpired(session)) {
                logger.debug("session expired: {}", session);
                sessionStore.delete(sessionId);
                session = null;
            }
        }
        if (session == null) {
            if (config.isUseGlobalSession()) {
                session = ServerConfig.GLOBAL_SESSION;
            } else {
                if (config.isAutoCreateSession()) {
                    context.init();
                    session = context.getSession();
                    logger.debug("auto-created session: {} - {}", request, session);
                } else if (config.getSigninPagePath().equals(request.getPath())
                        || config.getSignoutPagePath().equals(request.getPath())) {
                    session = Session.TEMPORARY;
                    logger.debug("auth flow: {}", request);
                } else {
                    logger.warn("session not found: {}", request);
                    ResponseBuilder rb = response();
                    if (sessionId != null) {
                        rb.deleteSessionCookie(sessionId);
                    }
                    if (request.isAjax()) {
                        rb.ajaxRedirect(signInPath());
                    } else {
                        rb.locationHeader(signInPath());
                    }
                    return rb.buildWithStatus(302);
                }
            }
        }
        context.setSession(session);
        return null;
    }

    /**
     * Handles an incoming HTTP request and produces a Response.
     *
     * @param request the incoming request
     * @return the HTTP response
     */
    @Override
    public Response handle(Request request) {
        stripContextPath(request);
        resolveHomePage(request);

        ServerContext context = contextFactory.apply(request);
        assignResourceType(request);

        Response staticResponse = tryServingStaticResource(context, request);
        if (staticResponse != null) {
            return staticResponse;
        }

        Session session = context.getSession();

        if (session == null && !context.isStateless()) {
            Response redirectResponse = resolveSessionOrRedirect(session, context, request);
            if (redirectResponse != null) {
                return redirectResponse;
            }
        }

        RequestCycle rc = RequestCycle.init(templateEngine, context);
        return rc.handle();
    }

    private String signInPath() {
        String path = config.getSigninPagePath();
        String contextPath = config.getHostContextPath();
        return contextPath == null ? path : contextPath + path.substring(1);
    }

    private boolean isExpired(Session session) {
        int configExpirySeconds = config.getSessionExpirySeconds();
        if (configExpirySeconds == -1) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        long expires = session.getUpdated() + configExpirySeconds;
        if (now > expires) {
            return true;
        }
        session.setUpdated(now);
        session.setExpires(expires);
        return false;
    }

    private ResponseBuilder response() {
        return new ResponseBuilder(config, null);
    }

}
