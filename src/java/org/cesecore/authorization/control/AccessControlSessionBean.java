/*************************************************************************
 *                                                                       *
 *  CESeCore: CE Security Core                                           *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.cesecore.authorization.control;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.apache.log4j.Logger;
import org.cesecore.audit.enums.EventStatus;
import org.cesecore.audit.enums.EventTypes;
import org.cesecore.audit.enums.ModuleTypes;
import org.cesecore.audit.enums.ServiceTypes;
import org.cesecore.audit.log.SecurityEventsLoggerSessionLocal;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.cache.AccessTreeCache;
import org.cesecore.authorization.cache.AccessTreeUpdateSessionLocal;
import org.cesecore.jndi.JndiConstants;
import org.cesecore.roles.access.RoleAccessSessionLocal;

/**
 * Based on cesecore version:
 *       AccessControlSessionBean.java 897 2011-06-20 11:17:25Z johane
 *  
 * @version $Id$
 * 
 */
@Stateless(mappedName = JndiConstants.APP_JNDI_PREFIX + "AccessControlSessionRemote")
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class AccessControlSessionBean implements AccessControlSessionLocal, AccessControlSessionRemote {

    private static final Logger log = Logger.getLogger(AccessControlSessionBean.class);

    @EJB
    private AccessTreeUpdateSessionLocal accessTreeUpdateSession;

    @EJB
    private RoleAccessSessionLocal roleAccessSession;

    @EJB
    private SecurityEventsLoggerSessionLocal securityEventsLoggerSession;

    /** Cache for authorization data */
    private static AccessTreeCache accessTreeCache;

    @Override
    public boolean isAuthorized(AuthenticationToken authenticationToken, String resource) {

        if (updateNeccessary()) {
            updateAuthorizationTree(authenticationToken);
        }

        if (accessTreeCache.getAccessTree().isAuthorized(authenticationToken, resource)) {
            Map<String, Object> details = new LinkedHashMap<String, Object>();
            details.put("resource", resource);
            securityEventsLoggerSession.log(EventTypes.ACCESS_CONTROL, EventStatus.SUCCESS, ModuleTypes.ACCESSCONTROL, ServiceTypes.CORE,
                    authenticationToken.toString(), null, null, null, details);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void forceCacheExpire() {
        if (log.isTraceEnabled()) {
            log.trace("forceCacheExpire");
        }
        if (accessTreeCache != null) {
            accessTreeCache.forceCacheExpire();
        }
    }

    /**
     * Method used check if a reconstruction of authorization tree is needed in the authorization beans.
     * 
     * @return true if update is needed.
     */
    private boolean updateNeccessary() {
        boolean ret = false;
        // Only do the actual SQL query if we might update the configuration due to cache time anyhow
        if (accessTreeCache == null) {
            ret = true;
        } else if (accessTreeCache.needsUpdate()) {
            ret = accessTreeUpdateSession.getAccessTreeUpdateData().updateNeccessary(accessTreeCache.getAccessTreeUpdateNumber());
            // we don't want to run the above query often
        }
        if (log.isTraceEnabled()) {
            log.trace("updateNeccessary: " + false);
        }
        return ret;
    }

    /**
     * method updating authorization tree.
     */
    private void updateAuthorizationTree(AuthenticationToken authenticationToken) {
        if (log.isTraceEnabled()) {
            log.trace(">updateAuthorizationTree");
        }
        int authorizationtreeupdatenumber = accessTreeUpdateSession.getAccessTreeUpdateData().getAccessTreeUpdateNumber();
        if (accessTreeCache == null) {
            accessTreeCache = new AccessTreeCache();
        }

        accessTreeCache.updateAccessTree(roleAccessSession.getAllRoles(), authorizationtreeupdatenumber);
        if (log.isTraceEnabled()) {
            log.trace("<updateAuthorizationTree");
        }

    }

}
