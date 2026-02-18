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

import com.intuit.karate.DIYCoverageTracker;
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

    @Override
    public Response handle(Request request) {
        DIYCoverageTracker.branches[0] = true;
        if (stripHostContextPath != null) {
            DIYCoverageTracker.branches[1] = true;
            if (request.getPath().startsWith(stripHostContextPath)) {
                DIYCoverageTracker.branches[2] = true;
                request.setPath(request.getPath().substring(stripHostContextPath.length()));
            } else {
                DIYCoverageTracker.branches[3] = true;
            }
        } else {
            DIYCoverageTracker.branches[4] = true;
        }
        if (SLASH.equals(request.getPath())) {
            DIYCoverageTracker.branches[5] = true;
            request.setPath(config.getHomePagePath());
        } else {
            DIYCoverageTracker.branches[6] = true;
        }
        ServerContext context = contextFactory.apply(request);
        if (request.getResourceType() == null) { // can be set by context factory
            DIYCoverageTracker.branches[7] = true;
            request.setResourceType(ResourceType.fromFileExtension(request.getPath()));
        } else {
            DIYCoverageTracker.branches[8] = true;
        }
        if (!context.isApi() && request.isHttpGetForStaticResource() && context.isHttpGetAllowed()) {
            DIYCoverageTracker.branches[9] = true;
            if (request.getResourcePath() == null) { // can be set by context factory
                DIYCoverageTracker.branches[10] = true;
                request.setResourcePath(request.getPath()); // static resource
            } else {
                DIYCoverageTracker.branches[11] = true;
            }
            try {
                return response().buildStatic(request);
            } finally {
                if (logger.isDebugEnabled()) {
                    DIYCoverageTracker.branches[12] = true;
                    logger.debug("{} {} [{} ms]", request, 200, System.currentTimeMillis() - request.getStartTime());
                } else {
                    DIYCoverageTracker.branches[13] = true;
                }
            }
        } else {
            DIYCoverageTracker.branches[14] = true;
        }
        Session session = context.getSession(); // can be pre-resolved by context-factory
        if (session == null && !context.isStateless()) {
            DIYCoverageTracker.branches[15] = true;
            String sessionId = context.getSessionCookieValue();
            if (sessionId != null) {
                DIYCoverageTracker.branches[16] = true;
                session = sessionStore.get(sessionId);
                if (session != null && isExpired(session)) {
                    DIYCoverageTracker.branches[17] = true;
                    logger.debug("session expired: {}", session);
                    sessionStore.delete(sessionId);
                    session = null;
                } else {
                    DIYCoverageTracker.branches[18] = true;
                }
            } else {
                DIYCoverageTracker.branches[19] = true;
            }
            if (session == null) {
                DIYCoverageTracker.branches[20] = true;
                if (config.isUseGlobalSession()) {
                    DIYCoverageTracker.branches[21] = true;
                    session = ServerConfig.GLOBAL_SESSION;
                } else {
                    DIYCoverageTracker.branches[22] = true;
                    if (config.isAutoCreateSession()) {
                        DIYCoverageTracker.branches[23] = true;
                        context.init();
                        session = context.getSession();
                        logger.debug("auto-created session: {} - {}", request, session);
                    } else if (config.getSigninPagePath().equals(request.getPath())
                            || config.getSignoutPagePath().equals(request.getPath())) {
                        DIYCoverageTracker.branches[24] = true;
                        session = Session.TEMPORARY;
                        logger.debug("auth flow: {}", request);
                    } else {
                        DIYCoverageTracker.branches[25] = true;
                        logger.warn("session not found: {}", request);
                        ResponseBuilder rb = response();
                        if (sessionId != null) {
                            DIYCoverageTracker.branches[26] = true;
                            rb.deleteSessionCookie(sessionId);
                        }
                        if (request.isAjax()) {
                            DIYCoverageTracker.branches[27] = true;
                            rb.ajaxRedirect(signInPath());
                        } else {
                            rb.locationHeader(signInPath());
                        }
                        return rb.buildWithStatus(302);
                    }
                }
            } else {
                DIYCoverageTracker.branches[28] = true;
            }
            context.setSession(session);
        } else {
            DIYCoverageTracker.branches[29] = true;
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
