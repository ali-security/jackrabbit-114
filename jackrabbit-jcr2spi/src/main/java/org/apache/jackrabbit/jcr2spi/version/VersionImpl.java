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
package org.apache.jackrabbit.jcr2spi.version;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.jackrabbit.jcr2spi.NodeImpl;
import org.apache.jackrabbit.jcr2spi.ItemManager;
import org.apache.jackrabbit.jcr2spi.SessionImpl;
import org.apache.jackrabbit.jcr2spi.ItemLifeCycleListener;
import org.apache.jackrabbit.jcr2spi.state.NodeState;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.name.NameConstants;

import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.Node;
import javax.jcr.Item;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.nodetype.ConstraintViolationException;
import java.util.Calendar;

/**
 * <code>VersionImpl</code>...
 */
public class VersionImpl extends NodeImpl implements Version {

    private static Logger log = LoggerFactory.getLogger(VersionImpl.class);

    public VersionImpl(SessionImpl session, NodeState state,
                       ItemLifeCycleListener[] listeners) {
        super(session, state, listeners);
    }

    //------------------------------------------------------------< Version >---
    /**
     *
     * @return
     * @throws RepositoryException
     * @see Version#getContainingHistory()
     */
    public VersionHistory getContainingHistory() throws RepositoryException {
        return (VersionHistory) getParent();
    }

    /**
     *
     * @return
     * @throws RepositoryException
     * @see Version#getCreated()
     */
    public Calendar getCreated() throws RepositoryException {
        return getProperty(NameConstants.JCR_CREATED).getDate();
    }

    /**
     *
     * @return
     * @throws RepositoryException
     * @see Version#getSuccessors()
     */
    public Version[] getSuccessors() throws RepositoryException {
        return getVersions(NameConstants.JCR_SUCCESSORS);
    }

    /**
     *
     * @return
     * @throws RepositoryException
     * @see Version#getPredecessors()
     */
    public Version[] getPredecessors() throws RepositoryException {
        return getVersions(NameConstants.JCR_PREDECESSORS);
    }

    //---------------------------------------------------------------< Item >---
    /**
     *
     * @param otherItem
     * @return
     * @see Item#isSame(Item)
     */
    public boolean isSame(Item otherItem) throws RepositoryException {
        checkStatus();
        if (otherItem instanceof VersionImpl) {
            // since all versions are referenceable, protected and live
            // in the same workspace, a simple comparision of the UUIDs is sufficient
            VersionImpl other = ((VersionImpl) otherItem);
            try {
                return getUUID().equals(other.getUUID());
            } catch (RepositoryException e) {
                // should never occur
                log.error("Internal error while retrieving UUID of version.", e);
            }
        }
        return false;
    }

    //-----------------------------------------------------------< ItemImpl >---
    /**
     * Always throws ConstraintViolationException since the version storage is
     * protected.
     *
     * @throws UnsupportedRepositoryOperationException
     * @throws ConstraintViolationException
     * @throws RepositoryException
     */
    protected void checkIsWritable() throws UnsupportedRepositoryOperationException, ConstraintViolationException, RepositoryException {
        super.checkIsWritable();
        throw new ConstraintViolationException("Version is protected");
    }

    /**
     * Always returns false
     *
     * @throws RepositoryException
     * @see NodeImpl#isWritable()
     */
    protected boolean isWritable() throws RepositoryException {
        super.isWritable();
        return false;
    }
    //------------------------------------------------------------< private >---
    /**
     *
     * @param propertyName
     * @return
     */
    private Version[] getVersions(Name propertyName) throws RepositoryException {
        Version[] versions;
        Value[] values = getProperty(propertyName).getValues();
        if (values != null) {
            versions = new Version[values.length];
            for (int i = 0; i < values.length; i++) {
                Node n = session.getNodeByUUID(values[i].getString());
                if (n instanceof Version) {
                    versions[i] = (Version) n;
                } else {
                    throw new RepositoryException("Version property contains invalid value not pointing to a 'Version'");
                }
            }
        } else {
            versions = new Version[0];
        }
        return versions;
    }
}