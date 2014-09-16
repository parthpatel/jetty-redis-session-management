package org.eclipse.jetty;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import lombok.extern.log4j.Log4j;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.session.AbstractSessionIdManager;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * SessionIdManager is an implementation within Redis which manages session ids
 * and provides uniqueness amongst them. Methods of this class are called by
 * SessionManager and Session class itself during the lifecycle of a session.
 * 
 * A normal getSession(true) call will look like this:
 * 
 * - session = SessionManager.newHttpSession()
 * 
 * -- new RedisNoSQLSession() calls SessionIdManager.newSessionId() which calls
 * idInUse() to check for the use of the id. and then finishes the constructor
 * call by calling SessionManager.save()
 * 
 * -- sessionManager.addSession() which basically adds the session to concurrent
 * hashmap in session manager and also makes a call to sessionIdManager to get
 * the session added in local memory.
 * 
 * - sessionCookie = sessionManager.getSessionCookie(session)
 * 
 * - response.addCookie(sessionCookie)
 * 
 * - return session;
 * 
 * @author parth
 * 
 */
@Log4j
public class RedisSessionIdManager extends AbstractSessionIdManager {
    final JedisPool jedisPool;
    final Server    server;

    public RedisSessionIdManager(JedisPool jedisPool, Server server) {
        this.jedisPool = jedisPool;
        this.server = server;
    }

    /**
     * Called during the session creation to check for the uniqueness of the
     * generated session id.
     */
    @Override
    public boolean idInUse(String id) {
        Jedis jedis = null;
        try {
            log.debug("idInUser called");
            jedis = jedisPool.getResource();
            boolean exists = jedis.exists(id);
            return exists;
        } catch (Exception e) {
            log.fatal("exception while calling redis", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
        // returning true here can cause an infinite loop if redis is down or
        // has some connection issues. on every true Session Manager will keep
        // calling this function with new ids to get one that doesn't have
        // collision.
        return false;
    }

    /**
     * We are not maintaining any local storage, hence this is a no-op for us.
     */
    @Override
    public void addSession(HttpSession session) {
        if (session == null) {
            return;
        }
        log.info("addSession:" + session.getId());
    }

    /**
     * We are not maintaining any local storage, hence this is a no-op for us.
     */
    @Override
    public void removeSession(HttpSession session) {
        // handled by session manager
        log.info("removeSession was called");
    }

    /**
     * We are not maintaining any local storage, hence this is a no-op for us.
     */
    @Override
    public void invalidateAll(String sessionId) {
        // handled by session manager
        log.info("invalidateAll was called");
    }

    /**
     * If Jetty was running in clustered mode where the sessions are stored in a
     * distributed manner across multiple nodes then each session would have a
     * unique id across session which is referred to here as clusterId. The id
     * of the session within that node's storage is called nodeId. The
     * documentation is very vague about inner workings of this and hence I am
     * writing down this documentation of my understanding from the code that I
     * have read.
     * 
     */
    @Override
    public String getClusterId(String nodeId) {
        return nodeId;
    }

    /**
     * See {@link getClusterId} method above.
     */
    @Override
    public String getNodeId(String clusterId, HttpServletRequest request) {
        return clusterId;
    }

}
