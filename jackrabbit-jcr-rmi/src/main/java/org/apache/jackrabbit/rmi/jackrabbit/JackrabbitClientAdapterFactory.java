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
package org.apache.jackrabbit.rmi.jackrabbit;

import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.nodetype.NodeTypeManager;

import org.apache.jackrabbit.api.JackrabbitNodeTypeManager;
import org.apache.jackrabbit.api.JackrabbitWorkspace;
import org.apache.jackrabbit.rmi.client.ClientAdapterFactory;
import org.apache.jackrabbit.rmi.client.LocalAdapterFactory;
import org.apache.jackrabbit.rmi.remote.RemoteNodeTypeManager;
import org.apache.jackrabbit.rmi.remote.RemoteWorkspace;

/**
 * Jackrabbit-specific {@link LocalAdapterFactory}. This factory extends
 * the default {@link ClientAdapterFactory} implementation with adapter
 * classes that implement the Jackrabbit API extension interfaces. The
 * implementation degrades gracefully when used with other repositories.
 */
public class JackrabbitClientAdapterFactory extends ClientAdapterFactory {

    /**
     * Returns a {@link JackrabbitNodeTypeManager} adapter if given a
     * {@link RemoteJackrabbitNodeTypeManager} reference. Alternatively falls
     * back to the default adapter from the parent class.
     *
     * @param remote remote node type manager
     * @return adapted node type manager
     */
    public NodeTypeManager getNodeTypeManager(RemoteNodeTypeManager remote) {
        if (remote instanceof RemoteJackrabbitNodeTypeManager) {
            return new ClientJackrabbitNodeTypeManager(
                    (RemoteJackrabbitNodeTypeManager) remote, this);
        } else {
            return super.getNodeTypeManager(remote);
        }
    }

    /**
     * Returns a {@link JackrabbitWorkspace} adapter if given a
     * {@link RemoteJackrabbitWorkspace} reference. Alternatively falls back
     * to the default adapter from the parent class.
     *
     * @param remote remote workspace
     * @return adapted workspace
     */
    public Workspace getWorkspace(Session session, RemoteWorkspace remote) {
        if (remote instanceof RemoteJackrabbitWorkspace) {
            return new ClientJackrabbitWorkspace(
                    session, (RemoteJackrabbitWorkspace) remote, this);
        } else {
            return super.getWorkspace(session, remote);
        }
    }


}
