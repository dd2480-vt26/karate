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

    @Override
    public Response handle(Request request) {
        // CC = 1
        if (stripHostContextPath != null) { // CC = 2
            if (request.getPath().startsWith(stripHostContextPath)) { // CC = 3
                request.setPath(request.getPath().substring(stripHostContextPath.length()));
            }
        }
        if (SLASH.equals(request.getPath())) { // CC = 4
            request.setPath(config.getHomePagePath());
        }
        ServerContext context = contextFactory.apply(request);
        if (request.getResourceType() == null) { // CC = 5
            request.setResourceType(ResourceType.fromFileExtension(request.getPath()));
        }
        if (!context.isApi() && request.isHttpGetForStaticResource() && context.isHttpGetAllowed()) { // CC = 5 + 3 = 8
            if (request.getResourcePath() == null) { // CC = 9
                request.setResourcePath(request.getPath()); 
            }
            try {
                return response().buildStatic(request); 
            } finally {
                if (logger.isDebugEnabled()) { // CC = 10
                    logger.debug("{} {} [{} ms]", request, 200, System.currentTimeMillis() - request.getStartTime());
                }
            }
        }
        Session session = context.getSession(); 
        if (session == null && !context.isStateless()) { // CC = 10 + 2 = 12
            String sessionId = context.getSessionCookieValue();
            if (sessionId != null) { // CC = 13
                session = sessionStore.get(sessionId);
                if (session != null && isExpired(session)) { // CC = 13 + 2 = 15
                    logger.debug("session expired: {}", session);
                    sessionStore.delete(sessionId);
                    session = null;
                }
            }
            if (session == null) { // CC = 16
                if (config.isUseGlobalSession()) { // CC = 17
                    session = ServerConfig.GLOBAL_SESSION;
                } else {
                    if (config.isAutoCreateSession()) { // CC = 18
                        context.init();
                        session = context.getSession();
                        logger.debug("auto-created session: {} - {}", request, session);
                    } else if (config.getSigninPagePath().equals(request.getPath())
                            || config.getSignoutPagePath().equals(request.getPath())) { // CC = 18 + 2 = 20
                        session = Session.TEMPORARY;
                        logger.debug("auth flow: {}", request);
                    } else {
                        logger.warn("session not found: {}", request);
                        ResponseBuilder rb = response();
                        if (sessionId != null) { // CC = 21
                            rb.deleteSessionCookie(sessionId);
                        }
                        if (request.isAjax()) { // CC = 22
                            rb.ajaxRedirect(signInPath());
                        } else {
                            rb.locationHeader(signInPath());
                        }
                        return rb.buildWithStatus(302); 
                    }
                }
            }
            context.setSession(session);
        }
        RequestCycle rc = RequestCycle.init(templateEngine, context);
        return rc.handle();

        // CC = 22
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
