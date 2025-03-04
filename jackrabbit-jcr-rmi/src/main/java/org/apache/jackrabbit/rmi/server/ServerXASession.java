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
package org.apache.jackrabbit.rmi.server;

import java.rmi.RemoteException;

import org.apache.jackrabbit.api.XASession;
import org.apache.jackrabbit.rmi.remote.RemoteXAResource;
import org.apache.jackrabbit.rmi.remote.RemoteXASession;

/**
 * Remote adapter for the Jackrabbit {@link XASession} interface.
 *
 * @since 1.4
 */
public class ServerXASession extends ServerSession implements RemoteXASession {

    /**
     * The adapted transaction enabled local session.
     */
    private XASession session;

    /**
     * Creates a remote adapter for the given local, transaction enabled,
     * session.
     */
    public ServerXASession(XASession session, RemoteAdapterFactory factory)
            throws RemoteException {
        super(session, factory);
        this.session = session;
    }

    public RemoteXAResource getXAResource() throws RemoteException {
        return getFactory().getRemoteXAResource(session.getXAResource());
    }

}
