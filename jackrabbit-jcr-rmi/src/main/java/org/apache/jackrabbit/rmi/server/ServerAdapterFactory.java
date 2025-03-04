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
import java.util.ArrayList;
import java.util.List;

import javax.jcr.Item;
import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.lock.Lock;
import javax.jcr.nodetype.ItemDefinition;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeIterator;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.ObservationManager;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;
import javax.transaction.xa.XAResource;

import org.apache.jackrabbit.api.XASession;
import org.apache.jackrabbit.rmi.remote.ArrayIterator;
import org.apache.jackrabbit.rmi.remote.BufferIterator;
import org.apache.jackrabbit.rmi.remote.RemoteEventCollection;
import org.apache.jackrabbit.rmi.remote.RemoteItem;
import org.apache.jackrabbit.rmi.remote.RemoteItemDefinition;
import org.apache.jackrabbit.rmi.remote.RemoteIterator;
import org.apache.jackrabbit.rmi.remote.RemoteLock;
import org.apache.jackrabbit.rmi.remote.RemoteNamespaceRegistry;
import org.apache.jackrabbit.rmi.remote.RemoteNode;
import org.apache.jackrabbit.rmi.remote.RemoteNodeDefinition;
import org.apache.jackrabbit.rmi.remote.RemoteNodeType;
import org.apache.jackrabbit.rmi.remote.RemoteNodeTypeManager;
import org.apache.jackrabbit.rmi.remote.RemoteObservationManager;
import org.apache.jackrabbit.rmi.remote.RemoteProperty;
import org.apache.jackrabbit.rmi.remote.RemotePropertyDefinition;
import org.apache.jackrabbit.rmi.remote.RemoteQuery;
import org.apache.jackrabbit.rmi.remote.RemoteQueryManager;
import org.apache.jackrabbit.rmi.remote.RemoteQueryResult;
import org.apache.jackrabbit.rmi.remote.RemoteRepository;
import org.apache.jackrabbit.rmi.remote.RemoteRow;
import org.apache.jackrabbit.rmi.remote.RemoteSession;
import org.apache.jackrabbit.rmi.remote.RemoteVersion;
import org.apache.jackrabbit.rmi.remote.RemoteVersionHistory;
import org.apache.jackrabbit.rmi.remote.RemoteWorkspace;
import org.apache.jackrabbit.rmi.remote.RemoteXAResource;
import org.apache.jackrabbit.rmi.server.iterator.ServerNodeIterator;
import org.apache.jackrabbit.rmi.server.iterator.ServerNodeTypeIterator;
import org.apache.jackrabbit.rmi.server.iterator.ServerPropertyIterator;
import org.apache.jackrabbit.rmi.server.iterator.ServerRowIterator;
import org.apache.jackrabbit.rmi.server.iterator.ServerVersionIterator;

/**
 * Default implementation of the
 * {@link RemoteAdapterFactory RemoteAdapterFactory} interface.
 * This factory uses the server adapters defined in this package as
 * the default adapter implementations. Subclasses can override or extend
 * the default adapters by implementing the corresponding factory methods.
 * <p>
 * The <code>bufferSize</code> property can be used to configure the
 * size of the buffer used by iterators to speed up iterator traversal
 * over the network.
 */
public class ServerAdapterFactory implements RemoteAdapterFactory {

    /** The default iterator buffer size. */
    private static final int DEFAULT_BUFFER_SIZE = 100;

    /** The buffer size of iterators created by this factory. */
    private int bufferSize = DEFAULT_BUFFER_SIZE;

    /**
     * Returns the iterator buffer size.
     *
     * @return iterator buffer size
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Sets the iterator buffer size.
     *
     * @param bufferSize iterator buffer size
     */
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    /**
     * Creates a {@link ServerRepository ServerRepository} instance.
     * {@inheritDoc}
     */
    public RemoteRepository getRemoteRepository(Repository repository)
            throws RemoteException {
        return new ServerRepository(repository, this);
    }

    /**
     * Creates a {@link ServerSession ServerSession} instance.
     * In case the underlying session is transaction enabled, the
     * remote interface is will be transaction enabled too through
     * the {@link ServerXASession}.
     *
     * {@inheritDoc}
     */
    public RemoteSession getRemoteSession(Session session)
            throws RemoteException {
        if (session instanceof XASession) {
            return new ServerXASession((XASession) session, this);
        } else {
            return new ServerSession(session, this);
        }
    }

    /**
     * Creates a {@link ServerWorkspace ServerWorkspace} instance.
     * {@inheritDoc}
     */
    public RemoteWorkspace getRemoteWorkspace(Workspace workspace)
            throws RemoteException {
        return new ServerWorkspace(workspace, this);
    }

    /**
     * Creates a {@link ServerObservationManager ServerObservationManager}
     * instance.
     * {@inheritDoc}
     */
    public RemoteObservationManager getRemoteObservationManager(
        ObservationManager observationManager) throws RemoteException {
        return new ServerObservationManager(observationManager, this);
    }

    /**
     * Creates a {@link ServerNamespaceRegistry ServerNamespaceRegistry}
     * instance.
     * {@inheritDoc}
     */
    public RemoteNamespaceRegistry getRemoteNamespaceRegistry(
            NamespaceRegistry registry)
            throws RemoteException {
        return new ServerNamespaceRegistry(registry, this);
    }

    /**
     * Creates a {@link ServerNodeTypeManager ServerNodeTypeManager} instance.
     * {@inheritDoc}
     */
    public RemoteNodeTypeManager getRemoteNodeTypeManager(
            NodeTypeManager manager)
            throws RemoteException {
        return new ServerNodeTypeManager(manager, this);
    }

    /**
     * Creates a {@link ServerItem ServerItem} instance.
     * {@inheritDoc}
     */
    public RemoteItem getRemoteItem(Item item) throws RemoteException {
        return new ServerItem(item, this);
    }

    /**
     * Creates a {@link ServerProperty ServerProperty} instance.
     * {@inheritDoc}
     */
    public RemoteProperty getRemoteProperty(Property property)
            throws RemoteException {
        return new ServerProperty(property, this);
    }

    /**
     * Creates a {@link ServerNode ServerNode} instance.
     * {@inheritDoc}
     */
    public RemoteNode getRemoteNode(Node node) throws RemoteException {
        return new ServerNode(node, this);
    }

    /**
     * Creates a {@link ServerVersion ServerVersion} instance.
     * {@inheritDoc}
     */
    public RemoteVersion getRemoteVersion(Version version) throws RemoteException {
        return new ServerVersion(version, this);
    }

    /**
     * Creates a {@link ServerVersionHistory ServerVersionHistory} instance.
     * {@inheritDoc}
     */
    public RemoteVersionHistory getRemoteVersionHistory(VersionHistory versionHistory)
            throws RemoteException {
        return new ServerVersionHistory(versionHistory, this);
    }

    /**
     * Creates a {@link ServerNodeType ServerNodeType} instance.
     * {@inheritDoc}
     */
    public RemoteNodeType getRemoteNodeType(NodeType type)
            throws RemoteException {
        return new ServerNodeType(type, this);
    }

    /**
     * Creates a {@link ServerItemDefinition ServerItemDefinition} instance.
     * {@inheritDoc}
     */
    public RemoteItemDefinition getRemoteItemDefinition(ItemDefinition def)
            throws RemoteException {
        return new ServerItemDefinition(def, this);
    }

    /**
     * Creates a {@link ServerNodeDefinition ServerNodeDefinition} instance.
     * {@inheritDoc}
     */
    public RemoteNodeDefinition getRemoteNodeDefinition(NodeDefinition def)
            throws RemoteException {
        return new ServerNodeDefinition(def, this);
    }

    /**
     * Creates a {@link ServerPropertyDefinition ServerPropertyDefinition} instance.
     * {@inheritDoc}
     */
    public RemotePropertyDefinition getRemotePropertyDefinition(PropertyDefinition def)
            throws RemoteException {
        return new ServerPropertyDefinition(def, this);
    }

    /**
     * Creates a {@link ServerLock ServerLock} instance.
     * {@inheritDoc}
     */
    public RemoteLock getRemoteLock(Lock lock) throws RemoteException {
        return new ServerLock(lock);
    }

    /**
     * Creates a {@link ServerQueryManager ServerQueryManager} instance.
     * {@inheritDoc}
     */
    public RemoteQueryManager getRemoteQueryManager(QueryManager manager)
            throws RemoteException {
        return new ServerQueryManager(manager, this);
    }

    /**
     * Creates a {@link ServerQuery ServerQuery} instance.
     * {@inheritDoc}
     */
    public RemoteQuery getRemoteQuery(Query query) throws RemoteException {
        return new ServerQuery(query, this);
    }

    /**
     * Creates a {@link ServerQueryResult ServerQueryResult} instance.
     * {@inheritDoc}
     */
    public RemoteQueryResult getRemoteQueryResult(QueryResult result)
            throws RemoteException {
        return new ServerQueryResult(result, this);
    }

    /**
     * Creates a {@link ServerQueryResult ServerQueryResult} instance.
     * {@inheritDoc}
     */
    public RemoteRow getRemoteRow(Row row) throws RemoteException {
        return new ServerRow(row, this);
    }

    /**
     * Creates a {@link ServerEventCollection ServerEventCollection} instances.
     * {@inheritDoc}
     */
    public RemoteEventCollection getRemoteEvent(long listenerId, EventIterator events)
            throws RemoteException {
        RemoteEventCollection.RemoteEvent[] remoteEvents;
        if (events != null) {
            List eventList = new ArrayList();
            while (events.hasNext()) {
                try {
                    Event event = events.nextEvent();
                    eventList.add(new ServerEventCollection.ServerEvent(
                            event.getType(), event.getPath(), event.getUserID()));
                } catch (RepositoryException re) {
                    throw new RemoteException(re.getMessage(), re);
                }
            }
            remoteEvents = (RemoteEventCollection.RemoteEvent[])
                eventList.toArray(new RemoteEventCollection.RemoteEvent[eventList.size()]);
        } else {
            remoteEvents = new RemoteEventCollection.RemoteEvent[0]; // for safety
        }

        return new ServerEventCollection(listenerId, remoteEvents);
    }

    /**
     * Optimizes the given remote iterator for transmission across the
     * network. This method retrieves the first set of elements from
     * the iterator by calling {@link RemoteIterator#nextObjects()} and
     * then asks for the total size of the iterator. If the size is unkown
     * or greater than the length of the retrieved array, then the elements,
     * the size, and the remote iterator reference are wrapped into a
     * {@link BufferIterator} instance that gets passed over the network.
     * If the retrieved array of elements contains all the elements in the
     * iterator, then the iterator instance is discarded and just the elements
     * are wrapped into a {@link ArrayIterator} instance to be passed to the
     * client.
     * <p>
     * Subclasses can override this method to provide alternative optimizations.
     *
     * @param remote remote iterator
     * @return optimized remote iterator
     * @throws RemoteException on RMI errors
     */
    protected RemoteIterator optimizeIterator(RemoteIterator remote)
            throws RemoteException {
        Object[] elements = remote.nextObjects();
        long size = remote.getSize();
        if (size == -1 || (elements != null && size > elements.length)) {
            return new BufferIterator(elements, size, remote);
        } else {
            return new ArrayIterator(elements);
        }
    }

    /**
     * Creates a {@link ServerNodeIterator} instance. {@inheritDoc}
     */
    public RemoteIterator getRemoteNodeIterator(NodeIterator iterator)
            throws RemoteException {
        return optimizeIterator(
                new ServerNodeIterator(iterator, this, bufferSize));
    }

    /**
     * Creates a {@link ServerPropertyIterator} instance. {@inheritDoc}
     */
    public RemoteIterator getRemotePropertyIterator(PropertyIterator iterator)
            throws RemoteException {
        return optimizeIterator(
                new ServerPropertyIterator(iterator, this, bufferSize));
    }

    /**
     * Creates a {@link ServerVersionIterator} instance. {@inheritDoc}
     */
    public RemoteIterator getRemoteVersionIterator(VersionIterator iterator)
            throws RemoteException {
        return optimizeIterator(
                new ServerVersionIterator(iterator, this, bufferSize));
    }

    /**
     * Creates a {@link ServerNodeTypeIterator} instance. {@inheritDoc}
     */
    public RemoteIterator getRemoteNodeTypeIterator(NodeTypeIterator iterator)
            throws RemoteException {
        return optimizeIterator(
                new ServerNodeTypeIterator(iterator, this, bufferSize));
    }

    /**
     * Creates a {@link ServerRowIterator} instance. {@inheritDoc}
     */
    public RemoteIterator getRemoteRowIterator(RowIterator iterator)
            throws RemoteException {
        return optimizeIterator(
                new ServerRowIterator(iterator, this, bufferSize));
    }

    /**
     * Creates a {@link ServerXAResource} instance.
     */
    public RemoteXAResource getRemoteXAResource(XAResource resource)
            throws RemoteException {
        return new ServerXAResource(resource);
    }

}
