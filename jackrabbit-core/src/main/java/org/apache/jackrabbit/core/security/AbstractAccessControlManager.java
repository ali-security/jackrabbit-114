/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.security;

import org.apache.jackrabbit.api.jsr283.security.AccessControlException;
import org.apache.jackrabbit.api.jsr283.security.AccessControlManager;
import org.apache.jackrabbit.api.jsr283.security.AccessControlPolicy;
import org.apache.jackrabbit.api.jsr283.security.AccessControlPolicyIterator;
import org.apache.jackrabbit.api.jsr283.security.Privilege;
import org.apache.jackrabbit.core.security.authorization.PrivilegeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.AccessDeniedException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import java.security.Principal;

/**
 * <code>AbstractAccessControlManager</code>...
 */
public abstract class AbstractAccessControlManager implements JackrabbitAccessControlManager {

    private static Logger log = LoggerFactory.getLogger(AbstractAccessControlManager.class);

    /**
     * Always returns all registered <code>Privilege</code>s.
     *
     * @param absPath
     * @return Always returns all registered <code>Privilege</code>s.
     * @see AccessControlManager#getSupportedPrivileges(String)
     */
    public Privilege[] getSupportedPrivileges(String absPath) throws PathNotFoundException, RepositoryException {
        checkInitialized();
        checkValidNodePath(absPath);

        // return all known privileges everywhere.
        return getPrivilegeRegistry().getRegisteredPrivileges();
    }

    /**
     * @see AccessControlManager#privilegeFromName(String)
     */
    public Privilege privilegeFromName(String privilegeName)
            throws AccessControlException, RepositoryException {
        checkInitialized();

        return getPrivilegeRegistry().getPrivilege(privilegeName);
    }

    /**
     * Returns <code>null</code>.
     *
     * @param absPath
     * @return always returns <code>null</code>.
     * @see AccessControlManager#getApplicablePolicies(String)
     */
    public AccessControlPolicy[] getPolicies(String absPath) throws PathNotFoundException, AccessDeniedException, RepositoryException {
        checkInitialized();
        checkPrivileges(absPath, PrivilegeRegistry.READ_AC);

        log.debug("Implementation does not provide applicable policies -> getPolicy() always returns an empty array.");
        return new AccessControlPolicy[0];
    }

    /**
     * Returns an empty iterator.
     *
     * @param absPath
     * @return always returns an empty iterator.
     * @see AccessControlManager#getApplicablePolicies(String)
     */
    public AccessControlPolicyIterator getApplicablePolicies(String absPath) throws PathNotFoundException, AccessDeniedException, RepositoryException {
        checkInitialized();
        checkPrivileges(absPath, PrivilegeRegistry.READ_AC);

        log.debug("Implementation does not provide applicable policies -> returning empty iterator.");
        return AccessControlPolicyIteratorAdapter.EMPTY;
    }

    /**
     * Always throws <code>AccessControlException</code>
     *
     * @see AccessControlManager#setPolicy(String, AccessControlPolicy)
     */
    public void setPolicy(String absPath, AccessControlPolicy policy) throws PathNotFoundException, AccessControlException, AccessDeniedException, RepositoryException {
        checkInitialized();
        checkPrivileges(absPath, PrivilegeRegistry.MODIFY_AC);

        throw new AccessControlException("AccessControlPolicy " + policy + " cannot be applied.");
    }

    /**
     * Always throws <code>AccessControlException</code>
     *
     * @see AccessControlManager#removePolicy(String, AccessControlPolicy)
     */
    public void removePolicy(String absPath, AccessControlPolicy policy) throws PathNotFoundException, AccessControlException, AccessDeniedException, RepositoryException {
        checkInitialized();
        checkPrivileges(absPath, PrivilegeRegistry.MODIFY_AC);

        throw new AccessControlException("No AccessControlPolicy has been set through this API -> Cannot be removed.");
    }


    //-------------------------------------< JackrabbitAccessControlManager >---
    /**
     * {@inheritDoc}
     */
    public AccessControlPolicy[] getApplicablePolicies(Principal principal) throws AccessDeniedException, AccessControlException, UnsupportedRepositoryOperationException, RepositoryException {
        log.debug("Implementation does not provide applicable policies -> returning empty array.");        
        return new AccessControlPolicy[0];
    }

    //--------------------------------------------------------------------------
    /**
     * Check if this manager has been properly initialized.
     *
     * @throws IllegalStateException If this manager has not been properly initialized.
     */
    protected abstract void checkInitialized() throws IllegalStateException;

    /**
     * Check if the specified privileges are granted at <code>absPath</code>.
     *
     * @param absPath
     * @param privileges
     * @throws AccessDeniedException if the session does not have the
     * specified privileges.
     * @throws PathNotFoundException if no node exists at <code>absPath</code>
     * of if the session does not have the privilege to READ it.
     * @throws RepositoryException
     */
    protected abstract void checkPrivileges(String absPath, int privileges) throws AccessDeniedException, PathNotFoundException, RepositoryException;

    /**
     * @return the privilege registry
     * @throws RepositoryException
     */
    protected abstract PrivilegeRegistry getPrivilegeRegistry() throws RepositoryException;

    /**
     * Build a qualified path from the specified <code>absPath</code> and test
     * if it is really absolute and points to an existing node.
     *
     * @param absPath
     * @throws PathNotFoundException if no node at <code>absPath</code> exists
     * or the session does not have privilege to retrieve the node.
     * @throws RepositoryException If the given <code>absPath</code> is not
     * absolute or if some other error occurs.
     */
    protected abstract void checkValidNodePath(String absPath) throws PathNotFoundException, RepositoryException;

}