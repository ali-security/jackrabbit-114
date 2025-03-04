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

import javax.jcr.RepositoryException;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;

/**
 * This Interface defines the version selector that needs to provide a version,
 * given some hints and a version history. the selector is used in the various
 * restore methods in order to select the correct version of previously versioned
 * OPV=Version children upon restore. JSR170 states: <em>"This determination
 * [of the version] depends on the configuration of the workspace and is outside
 * the scope of this specification."</em>
 * <p/>
 * The version selection in jackrabbit works as follows:<br/>
 * The <code>Node.restore()</code> methods uses the
 * {@link DateVersionSelector} which is initialized with the creation date of
 * the parent version. This selector selects the latest version that is equal
 * or older than the given date. if no such version exists, the initial one
 * is restored.<br/>
 * The <code>Node.restoreByLabel()</code> uses the
 * {@link LabelVersionSelector} which is initialized with the label of the
 * version to be restored. This selector selectes the version with the same
 * label. if no such version exists, the initial one is restored.
 * <p/>
 *
 * @see DateVersionSelector
 * @see LabelVersionSelector
 * @see NodeImpl#restore(String, boolean)
 * @see NodeImpl#restore(Version, boolean)
 * @see NodeImpl#restore(Version, String, boolean)
 * @see NodeImpl#restoreByLabel(String, boolean)
 *
 */
public interface VersionSelector {

    /**
     * Selects a version of the given version history. If this VersionSelector
     * is unable to select one, it can return <code>null</code>. Please note,
     * that a version selector is not allowed to return the root version.
     *
     * @param versionHistory
     * @return A version or <code>null</code>.
     * @throws RepositoryException if an error occurrs.
     */
    Version select(VersionHistory versionHistory) throws RepositoryException;

}
