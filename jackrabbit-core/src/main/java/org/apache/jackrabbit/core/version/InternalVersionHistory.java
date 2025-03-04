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
package org.apache.jackrabbit.core.version;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.uuid.UUID;

import javax.jcr.version.VersionException;

/**
 * This interface defines the internal version history.
 */
public interface InternalVersionHistory extends InternalVersionItem {

    /**
     * Equivalalent to {@link javax.jcr.version.VersionHistory#getRootVersion()}.
     *
     * @see javax.jcr.version.VersionHistory#getRootVersion()
     */
    InternalVersion getRootVersion();

    /**
     * Equivalalent to {@link javax.jcr.version.VersionHistory#getVersion(java.lang.String)}.
     *
     * @see javax.jcr.version.VersionHistory#getVersion(java.lang.String)
     */
    InternalVersion getVersion(Name versionName) throws VersionException;

    /**
     * Checks if the version with the given name exists in this version history.
     *
     * @param versionName the name of the version
     * @return <code>true</code> if the version exists;
     *         <code>false</code> otherwise.
     */
    boolean hasVersion(Name versionName);

    /**
     * Returns the version with the given uuid or <code>null</code> if the
     * respective version does not exist.
     *
     * @param id the id of the version
     * @return the internal version ot <code>null</code>
     */
    InternalVersion getVersion(NodeId id);

    /**
     * Equivalalent to {@link javax.jcr.version.VersionHistory#getVersionByLabel(java.lang.String)}
     * but returns <code>null</code> if the version does not exists.
     *
     * @see javax.jcr.version.VersionHistory#getVersionByLabel(java.lang.String)
     */
    InternalVersion getVersionByLabel(Name label);

    /**
     * Returns the number of versions in this version history.
     *
     * @return the number of versions, including the root version.
     */
    int getNumVersions();

    /**
     * Returns the UUID of the versionable node that this history belongs to.
     *
     * @return the UUID of the versionable node.
     */
    UUID getVersionableUUID();

    /**
     * Returns a string  iterator over all version labels that exist in this
     * version history
     *
     * @return the labels
     */
    Name[] getVersionLabels();

    /**
     * Returns the Id of the version labels node.
     *
     * @return the id of the version labels node.
     */
    NodeId getVersionLabelsId();
}
