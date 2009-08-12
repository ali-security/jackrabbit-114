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
package org.apache.jackrabbit.core.lock;

import org.apache.jackrabbit.core.NodeImpl;

import javax.jcr.RepositoryException;

/**
 * Extension to standard lock implementation that works in XA environment.
 */
class XALockImpl extends LockImpl {

    /**
     * XA lock manager.
     */
    private final XALockManager lockMgr;
    
    /**
     * Abstract lock info.
     */
    private final AbstractLockInfo info;

    /**
     * Create a new instance of this class.
     * @param info lock information
     * @param node node holding lock
     */
    public XALockImpl(
            XALockManager lockMgr, AbstractLockInfo info, NodeImpl node) {
        super(info, node);

        this.info = info;
        this.lockMgr = lockMgr;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Refresh lock information if XA environment has changed.
     */
    public boolean isLive() throws RepositoryException {
        if (info.mayChange()) {
            if (lockMgr.differentXAEnv(info)) {
                return lockMgr.holdsLock(node);
            }
        }
        return super.isLive();
    }
}
