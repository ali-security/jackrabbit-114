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
package org.apache.jackrabbit.core.nodetype.virtual;

import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.core.NodeImpl;
import org.apache.jackrabbit.core.PropertyImpl;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.nodetype.NodeTypeImpl;
import org.apache.jackrabbit.core.nodetype.NodeTypeRegistry;
import org.apache.jackrabbit.core.nodetype.NodeTypeRegistryListener;
import org.apache.jackrabbit.core.observation.DelegatingObservationDispatcher;
import org.apache.jackrabbit.core.observation.EventState;
import org.apache.jackrabbit.core.virtual.VirtualItemStateProvider;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.Path;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.PathBuilder;
import org.apache.jackrabbit.spi.commons.conversion.MalformedPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;

/**
 * This Class implements a workaround helper for populating observation
 * events for the virtual node states of the jcr:nodeTypes upon nodetype
 * registry changes.
 */
public class VirtualNodeTypeStateManager implements NodeTypeRegistryListener {

    /**
     * the default logger
     */
    private static Logger log = LoggerFactory.getLogger(VirtualNodeTypeStateManager.class);

    /**
     * Path: /jcr:system/jcr:nodeTypes
     */
    private static final Path NODE_TYPES_PATH;

    static {
        try {
            PathBuilder builder = new PathBuilder();
            builder.addRoot();
            builder.addLast(NameConstants.JCR_SYSTEM);
            builder.addLast(NameConstants.JCR_NODETYPES);
            NODE_TYPES_PATH = builder.getPath();
        } catch (MalformedPathException e) {
            // will not happen. path is always valid
            throw new InternalError("Cannot initialize path");
        }
    }

    /**
     * an item state provider for the virtual nodetype states
     */
    private VirtualNodeTypeStateProvider virtProvider;

    /**
     * the node type registry
     */
    private final NodeTypeRegistry ntReg;

    /**
     * the root node id (usually the id of /jcr:system/jcr:nodeTypes)
     */
    private final NodeId rootNodeId;

    /**
     * the id of the roots parent (usually id of /jcr:system)
     */
    private final NodeId parentId;

    /**
     * the system session to generate the observation events
     */
    private SessionImpl systemSession;

    /**
     * the delegtating observation manager, that dispatches the events to
     * all underlying ones.
     */
    private DelegatingObservationDispatcher obsDispatcher;

    /**
     * Creates a new virtual node type state manager
     *
     * @param ntReg
     * @param obs
     * @param rootNodeId
     * @param parentId
     */
    public VirtualNodeTypeStateManager(
            NodeTypeRegistry ntReg, DelegatingObservationDispatcher obs,
            NodeId rootNodeId, NodeId parentId) {
        this.ntReg = ntReg;
        this.obsDispatcher = obs;
        this.rootNodeId = rootNodeId;
        this.parentId = parentId;
        ntReg.addListener(this);
    }

    /**
     * returns the virtual node state provider for the node type states.
     * @return the virtual item state provider
     */
    public synchronized VirtualItemStateProvider getVirtualItemStateProvider() {
        if (virtProvider == null) {
            virtProvider = new VirtualNodeTypeStateProvider(ntReg, rootNodeId, parentId);
        }
        return virtProvider;
    }

    /**
     * Sets the system session. This is needed, since the session should be
     * set, after the workspaces are initialzed.
     *
     * @param systemSession
     */
    public void setSession(SessionImpl systemSession) {
        this.systemSession = systemSession;
    }

    /**
     * {@inheritDoc}
     */
    public void nodeTypeRegistered(Name ntName) {
        try {
            if (virtProvider != null) {
                // allow provider to update
                virtProvider.onNodeTypeAdded(ntName);
            }
            if (systemSession != null) {
                // generate observation events
                NodeImpl root = (NodeImpl) systemSession.getItemManager().getItem(rootNodeId);
                NodeImpl child = root.getNode(ntName);
                List events = new ArrayList();
                recursiveAdd(events, root, child);
                obsDispatcher.dispatch(events, systemSession, NODE_TYPES_PATH);
            }
        } catch (RepositoryException e) {
            log.error("Unable to index new nodetype: " + e.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    public void nodeTypeReRegistered(Name ntName) {
        // lazy implementation
        nodeTypeUnregistered(ntName);
        nodeTypeRegistered(ntName);
    }

    /**
     * {@inheritDoc}
     */
    public void nodeTypeUnregistered(Name ntName) {
        try {
            if (systemSession != null) {
                // generated observation events
                NodeImpl root = (NodeImpl) systemSession.getItemManager().getItem(rootNodeId);
                NodeImpl child = root.getNode(ntName);
                List events = new ArrayList();
                recursiveRemove(events, root, child);
                obsDispatcher.dispatch(events, systemSession, NODE_TYPES_PATH);
            }
            if (virtProvider != null) {
                // allow provider to update
                virtProvider.onNodeTypeRemoved(ntName);
            }
        } catch (RepositoryException e) {
            log.error("Unable to index removed nodetype: " + e.toString());
        }
    }

    /**
     * Adds a subtree of itemstates as 'added' to a list of events
     *
     * @param events
     * @param parent
     * @param node
     * @throws RepositoryException
     */
    private void recursiveAdd(List events, NodeImpl parent, NodeImpl node)
            throws RepositoryException {

        events.add(EventState.childNodeAdded(
                parent.getNodeId(),
                parent.getPrimaryPath(),
                node.getNodeId(),
                node.getPrimaryPath().getNameElement(),
                ((NodeTypeImpl) parent.getPrimaryNodeType()).getQName(),
                parent.getMixinTypeNames(),
                node.getSession()
        ));

        PropertyIterator iter = node.getProperties();
        while (iter.hasNext()) {
            PropertyImpl prop = (PropertyImpl) iter.nextProperty();
            events.add(EventState.propertyAdded(
                    (NodeId) node.getId(),
                    node.getPrimaryPath(),
                    prop.getPrimaryPath().getNameElement(),
                    ((NodeTypeImpl) node.getPrimaryNodeType()).getQName(),
                    node.getMixinTypeNames(),
                    node.getSession()
            ));
        }
        NodeIterator niter = node.getNodes();
        while (niter.hasNext()) {
            NodeImpl n = (NodeImpl) niter.nextNode();
            recursiveAdd(events, node, n);
        }
    }

    /**
     * Adds a subtree of itemstates as 'removed' to a list of events
     *
     * @param events
     * @param parent
     * @param node
     * @throws RepositoryException
     */
    private void recursiveRemove(List events, NodeImpl parent, NodeImpl node)
            throws RepositoryException {

        events.add(EventState.childNodeRemoved(
                parent.getNodeId(),
                parent.getPrimaryPath(),
                node.getNodeId(),
                node.getPrimaryPath().getNameElement(),
                ((NodeTypeImpl) parent.getPrimaryNodeType()).getQName(),
                parent.getMixinTypeNames(),
                node.getSession()
        ));
        NodeIterator niter = node.getNodes();
        while (niter.hasNext()) {
            NodeImpl n = (NodeImpl) niter.nextNode();
            recursiveRemove(events, node, n);
        }
    }
}
