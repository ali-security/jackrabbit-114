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
package org.apache.jackrabbit.jcr2spi.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.jackrabbit.spi.QNodeDefinition;
import org.apache.jackrabbit.spi.NodeId;
import org.apache.jackrabbit.spi.PropertyId;
import org.apache.jackrabbit.spi.QPropertyDefinition;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.QValue;
import org.apache.jackrabbit.jcr2spi.hierarchy.NodeEntry;
import org.apache.jackrabbit.jcr2spi.hierarchy.PropertyEntry;
import org.apache.jackrabbit.jcr2spi.nodetype.ItemDefinitionProvider;

import javax.jcr.RepositoryException;
import javax.jcr.ItemNotFoundException;

import java.util.Iterator;

/**
 * <code>TransientISFactory</code>...
 */
public final class TransientISFactory extends AbstractItemStateFactory implements TransientItemStateFactory, ItemStateCreationListener  {

    private static Logger log = LoggerFactory.getLogger(TransientISFactory.class);

    private final ItemStateFactory workspaceStateFactory;
    private final ItemDefinitionProvider defProvider;

    public TransientISFactory(AbstractItemStateFactory workspaceStateFactory, ItemDefinitionProvider defProvider) {
        this.workspaceStateFactory = workspaceStateFactory;
        this.defProvider = defProvider;
        // start listening to 'creations' on the workspaceStateFactory (and
        // consequently skip an extra notification if the has been built by the
        // workspaceStateFactory.
        workspaceStateFactory.addCreationListener(this);
    }

    //------------------------------------------< TransientItemStateFactory >---
    /**
     * @inheritDoc
     * @see TransientItemStateFactory#createNewNodeState(NodeEntry , Name, QNodeDefinition)
     */
    public NodeState createNewNodeState(NodeEntry entry, Name nodetypeName,
                                        QNodeDefinition definition) {

        NodeState nodeState = new NodeState(entry, nodetypeName, Name.EMPTY_ARRAY, this, definition, defProvider);

        // notify listeners that a node state has been created
        notifyCreated(nodeState);

        return nodeState;
    }

    /**
     * @inheritDoc
     * @see TransientItemStateFactory#createNewPropertyState(PropertyEntry, QPropertyDefinition)
     */
    public PropertyState createNewPropertyState(PropertyEntry entry, QPropertyDefinition definition, QValue[] values, int propertyType) throws RepositoryException {
        PropertyState propState = new PropertyState(entry, this, definition, defProvider, values, propertyType);
        // notify listeners that a property state has been created
        notifyCreated(propState);
        return propState;
    }

    //---------------------------------------------------< ItemStateFactory >---
    /**
     * @inheritDoc
     * @see ItemStateFactory#createRootState(NodeEntry)
     */
    public NodeState createRootState(NodeEntry entry) throws ItemNotFoundException, RepositoryException {
        NodeState state = workspaceStateFactory.createRootState(entry);
        return state;
    }

    /**
     * @inheritDoc
     * @see ItemStateFactory#createNodeState(NodeId,NodeEntry)
     */
    public NodeState createNodeState(NodeId nodeId, NodeEntry entry)
            throws ItemNotFoundException, RepositoryException {
        NodeState state = workspaceStateFactory.createNodeState(nodeId, entry);
        return state;
    }

    /**
     * @inheritDoc
     * @see ItemStateFactory#createDeepNodeState(NodeId, NodeEntry)
     */
    public NodeState createDeepNodeState(NodeId nodeId, NodeEntry anyParent)
            throws ItemNotFoundException, RepositoryException {
        NodeState state = workspaceStateFactory.createDeepNodeState(nodeId, anyParent);
        return state;
    }

    /**
     * @inheritDoc
     * @see ItemStateFactory#createPropertyState(PropertyId, PropertyEntry)
     */
    public PropertyState createPropertyState(PropertyId propertyId,
                                             PropertyEntry entry)
            throws ItemNotFoundException, RepositoryException {
        PropertyState state = workspaceStateFactory.createPropertyState(propertyId, entry);
        return state;

    }

    /**
     * @see ItemStateFactory#createDeepPropertyState(PropertyId, NodeEntry)
     */
    public PropertyState createDeepPropertyState(PropertyId propertyId, NodeEntry anyParent) throws ItemNotFoundException, RepositoryException {
        PropertyState state = workspaceStateFactory.createDeepPropertyState(propertyId, anyParent);
        return state;
    }

    /**
     * @inheritDoc
     * @see ItemStateFactory#getChildNodeInfos(NodeId)
     */
    public Iterator getChildNodeInfos(NodeId nodeId) throws ItemNotFoundException, RepositoryException {
        return workspaceStateFactory.getChildNodeInfos(nodeId);
    }

    /**
     * @inheritDoc
     * @see ItemStateFactory#getNodeReferences(NodeState)
     */
    public PropertyId[] getNodeReferences(NodeState nodeState) {
        if (nodeState.getStatus() == Status.NEW) {
            return new PropertyId[0];
        }
        return workspaceStateFactory.getNodeReferences(nodeState);
    }

    //------------------------------------------< ItemStateCreationListener >---
    /**
     * @see ItemStateCreationListener#created(ItemState)
     */
    public void created(ItemState state) {
        log.debug("ItemState created by WorkspaceItemStateFactory");
        notifyCreated(state);
    }

    /**
     * @see ItemStateCreationListener#statusChanged(ItemState, int)
     */
    public void statusChanged(ItemState state, int previousStatus) {
        // ignore
    }
}