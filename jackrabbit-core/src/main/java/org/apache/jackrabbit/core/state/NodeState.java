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
package org.apache.jackrabbit.core.state;

import org.apache.jackrabbit.core.ItemId;
import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.core.nodetype.NodeDefId;
import org.apache.jackrabbit.spi.Name;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * <code>NodeState</code> represents the state of a <code>Node</code>.
 */
public class NodeState extends ItemState {

    /**
     * the name of this node's primary type
     */
    private Name nodeTypeName;

    /**
     * the names of this node's mixin types
     */
    private NameSet mixinTypeNames = new NameSet();

    /**
     * the id of this node.
     */
    private NodeId id;

    /**
     * the id of the parent node or <code>null</code> if this instance
     * represents the root node
     */
    private NodeId parentId;

    /**
     * id of this node's definition
     */
    private NodeDefId defId;

    /**
     * insertion-ordered collection of ChildNodeEntry objects
     */
    private ChildNodeEntries childNodeEntries = new ChildNodeEntries();

    /**
     * set of property names (Name objects)
     */
    private NameSet propertyNames = new NameSet();

    /**
     * Shared set, consisting of the parent ids of this shareable node. This
     * entry is {@link Collections#EMPTY_SET} if this node is not shareable.
     */
    private Set sharedSet = Collections.EMPTY_SET;

    /**
     * Flag indicating whether we are using a read-write shared set.
     */
    private boolean sharedSetRW;

    /**
     * Listener.
     */
    private transient NodeStateListener listener;

    /**
     * Constructs a new node state that is initially connected to an overlayed
     * state.
     *
     * @param overlayedState the backing node state being overlayed
     * @param initialStatus  the initial status of the node state object
     * @param isTransient    flag indicating whether this state is transient or not
     */
    public NodeState(NodeState overlayedState, int initialStatus,
                     boolean isTransient) {
        super(overlayedState, initialStatus, isTransient);
        pull();
    }

    /**
     * Constructs a new node state that is not connected.
     *
     * @param id            id of this node
     * @param nodeTypeName  node type of this node
     * @param parentId      id of the parent node
     * @param initialStatus the initial status of the node state object
     * @param isTransient   flag indicating whether this state is transient or not
     */
    public NodeState(NodeId id, Name nodeTypeName, NodeId parentId,
                     int initialStatus, boolean isTransient) {
        super(initialStatus, isTransient);
        this.id = id;
        this.parentId = parentId;
        this.nodeTypeName = nodeTypeName;
    }

    /**
     * {@inheritDoc}
     */
    protected synchronized void copy(ItemState state, boolean syncModCount) {
        synchronized (state) {
            NodeState nodeState = (NodeState) state;
            id = nodeState.id;
            parentId = nodeState.parentId;
            nodeTypeName = nodeState.nodeTypeName;
            mixinTypeNames = (NameSet) nodeState.mixinTypeNames.clone();
            defId = nodeState.defId;
            propertyNames = (NameSet) nodeState.propertyNames.clone();
            childNodeEntries = (ChildNodeEntries) nodeState.childNodeEntries.clone();
            if (syncModCount) {
                setModCount(state.getModCount());
            }
            sharedSet = nodeState.sharedSet;
            sharedSetRW = false;
        }
    }

    //-------------------------------------------------------< public methods >
    /**
     * {@inheritDoc}
     *
     * @return always true
     */
    public final boolean isNode() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public NodeId getParentId() {
        return parentId;
    }

    /**
     * {@inheritDoc}
     */
    public ItemId getId() {
        return id;
    }

    /**
     * Returns the identifier of this node.
     *
     * @return the id of this node.
     */
    public NodeId getNodeId() {
        return id;
    }

    /**
     * Sets the id of this node's parent.
     *
     * @param parentId the parent node's id or <code>null</code>
     * if either this node state should represent the root node or this node
     * state should be 'free floating', i.e. detached from the workspace's
     * hierarchy.
     */
    public void setParentId(NodeId parentId) {
        this.parentId = parentId;
    }

    /**
     * Returns the name of this node's node type.
     *
     * @return the name of this node's node type.
     */
    public Name getNodeTypeName() {
        return nodeTypeName;
    }

    /**
     * Returns the names of this node's mixin types.
     *
     * @return a set of the names of this node's mixin types.
     */
    public synchronized Set getMixinTypeNames() {
        return mixinTypeNames;
    }

    /**
     * Sets the names of this node's mixin types.
     *
     * @param names set of names of mixin types
     */
    public synchronized void setMixinTypeNames(Set names) {
        mixinTypeNames.replaceAll(names);
    }

    /**
     * Returns the id of the definition applicable to this node state.
     *
     * @return the id of the definition
     */
    public NodeDefId getDefinitionId() {
        return defId;
    }

    /**
     * Sets the id of the definition applicable to this node state.
     *
     * @param defId the id of the definition
     */
    public void setDefinitionId(NodeDefId defId) {
        this.defId = defId;
    }

    /**
     * Determines if there are any child node entries.
     *
     * @return <code>true</code> if there are child node entries,
     *         <code>false</code> otherwise.
     */
    public boolean hasChildNodeEntries() {
        return !childNodeEntries.isEmpty();
    }

    /**
     * Determines if there is a <code>ChildNodeEntry</code> with the
     * specified <code>name</code>.
     *
     * @param name <code>Name</code> object specifying a node name
     * @return <code>true</code> if there is a <code>ChildNodeEntry</code> with
     *         the specified <code>name</code>.
     */
    public synchronized boolean hasChildNodeEntry(Name name) {
        return !childNodeEntries.get(name).isEmpty();
    }

    /**
     * Determines if there is a <code>ChildNodeEntry</code> with the
     * specified <code>NodeId</code>.
     *
     * @param id the id of the child node
     * @return <code>true</code> if there is a <code>ChildNodeEntry</code> with
     *         the specified <code>name</code>.
     */
    public synchronized boolean hasChildNodeEntry(NodeId id) {
        return childNodeEntries.get(id) != null;
    }

    /**
     * Determines if there is a <code>ChildNodeEntry</code> with the
     * specified <code>name</code> and <code>index</code>.
     *
     * @param name  <code>Name</code> object specifying a node name
     * @param index 1-based index if there are same-name child node entries
     * @return <code>true</code> if there is a <code>ChildNodeEntry</code> with
     *         the specified <code>name</code> and <code>index</code>.
     */
    public synchronized boolean hasChildNodeEntry(Name name, int index) {
        return childNodeEntries.get(name, index) != null;
    }

    /**
     * Determines if there is a property entry with the specified
     * <code>Name</code>.
     *
     * @param propName <code>Name</code> object specifying a property name
     * @return <code>true</code> if there is a property entry with the specified
     *         <code>Name</code>.
     */
    public synchronized boolean hasPropertyName(Name propName) {
        return propertyNames.contains(propName);
    }

    /**
     * Returns the <code>ChildNodeEntry</code> with the specified name and index
     * or <code>null</code> if there's no matching entry.
     *
     * @param nodeName <code>Name</code> object specifying a node name
     * @param index    1-based index if there are same-name child node entries
     * @return the <code>ChildNodeEntry</code> with the specified name and index
     *         or <code>null</code> if there's no matching entry.
     */
    public synchronized ChildNodeEntry getChildNodeEntry(Name nodeName, int index) {
        return childNodeEntries.get(nodeName, index);
    }

    /**
     * Returns the <code>ChildNodeEntry</code> with the specified <code>NodeId</code> or
     * <code>null</code> if there's no matching entry.
     *
     * @param id the id of the child node
     * @return the <code>ChildNodeEntry</code> with the specified <code>NodeId</code> or
     *         <code>null</code> if there's no matching entry.
     * @see #addChildNodeEntry
     * @see #removeChildNodeEntry
     */
    public synchronized ChildNodeEntry getChildNodeEntry(NodeId id) {
        return childNodeEntries.get(id);
    }

    /**
     * Returns a list of <code>ChildNodeEntry</code> objects denoting the
     * child nodes of this node.
     *
     * @return list of <code>ChildNodeEntry</code> objects
     * @see #addChildNodeEntry
     * @see #removeChildNodeEntry
     */
    public synchronized List getChildNodeEntries() {
        return childNodeEntries;
    }

    /**
     * Returns a list of <code>ChildNodeEntry</code>s with the specified name.
     *
     * @param nodeName name of the child node entries that should be returned
     * @return list of <code>ChildNodeEntry</code> objects
     * @see #addChildNodeEntry
     * @see #removeChildNodeEntry
     */
    public synchronized List getChildNodeEntries(Name nodeName) {
        return childNodeEntries.get(nodeName);
    }

    /**
     * Adds a new <code>ChildNodeEntry</code>.
     *
     * @param nodeName <code>Name</code> object specifying the name of the new entry.
     * @param id the id the new entry is refering to.
     * @return the newly added <code>ChildNodeEntry</code>
     */
    public synchronized ChildNodeEntry addChildNodeEntry(Name nodeName,
                                                         NodeId id) {
        ChildNodeEntry entry = childNodeEntries.add(nodeName, id);
        notifyNodeAdded(entry);
        return entry;
    }

    /**
     * Renames a new <code>ChildNodeEntry</code>.
     *
     * @param oldName <code>Name</code> object specifying the entry's old name
     * @param index   1-based index if there are same-name child node entries
     * @param newName <code>Name</code> object specifying the entry's new name
     * @return <code>true</code> if the entry was sucessfully renamed;
     *         otherwise <code>false</code>
     */
    public synchronized boolean renameChildNodeEntry(Name oldName, int index,
                                                     Name newName) {
        ChildNodeEntry oldEntry = childNodeEntries.remove(oldName, index);
        if (oldEntry != null) {
            ChildNodeEntry newEntry =
                    childNodeEntries.add(newName, oldEntry.getId());
            notifyNodeAdded(newEntry);
            notifyNodeRemoved(oldEntry);
            return true;
        }
        return false;
    }

    /**
     * Removes a <code>ChildNodeEntry</code>.
     *
     * @param nodeName <code>ChildNodeEntry</code> object specifying a node name
     * @param index    1-based index if there are same-name child node entries
     * @return <code>true</code> if the specified child node entry was found
     *         in the list of child node entries and could be removed.
     */
    public synchronized boolean removeChildNodeEntry(Name nodeName, int index) {
        ChildNodeEntry entry = childNodeEntries.remove(nodeName, index);
        if (entry != null) {
            notifyNodeRemoved(entry);
        }
        return entry != null;
    }

    /**
     * Removes a <code>ChildNodeEntry</code>.
     *
     * @param id the id of the entry to be removed
     * @return <code>true</code> if the specified child node entry was found
     *         in the list of child node entries and could be removed.
     */
    public synchronized boolean removeChildNodeEntry(NodeId id) {
        ChildNodeEntry entry = childNodeEntries.remove(id);
        if (entry != null) {
            notifyNodeRemoved(entry);
        }
        return entry != null;
    }

    /**
     * Removes all <code>ChildNodeEntry</code>s.
     */
    public synchronized void removeAllChildNodeEntries() {
        childNodeEntries.removeAll();
        notifyNodesReplaced();
    }

    /**
     * Sets the list of <code>ChildNodeEntry</code> objects denoting the
     * child nodes of this node.
     * @param nodeEntries list of {@link ChildNodeEntry} or
     * a {@link ChildNodeEntries} list.
     */
    public synchronized void setChildNodeEntries(List nodeEntries) {
        if (nodeEntries instanceof ChildNodeEntries) {
            // optimization
            ChildNodeEntries entries = (ChildNodeEntries) nodeEntries;
            childNodeEntries = (ChildNodeEntries) entries.clone();
        } else {
            childNodeEntries.removeAll();
            childNodeEntries.addAll(nodeEntries);

        }
        notifyNodesReplaced();
    }

    /**
     * Returns the names of this node's properties as a set of
     * <code>QNames</code> objects.
     *
     * @return set of <code>QNames</code> objects
     * @see #addPropertyName
     * @see #removePropertyName
     */
    public synchronized Set getPropertyNames() {
        return propertyNames;
    }

    /**
     * Adds a property name entry.
     *
     * @param propName <code>Name</code> object specifying the property name
     */
    public synchronized void addPropertyName(Name propName) {
        propertyNames.add(propName);
    }

    /**
     * Removes a property name entry.
     *
     * @param propName <code>Name</code> object specifying the property name
     * @return <code>true</code> if the specified property name was found
     *         in the list of property name entries and could be removed.
     */
    public synchronized boolean removePropertyName(Name propName) {
        return propertyNames.remove(propName);
    }

    /**
     * Removes all property name entries.
     */
    public synchronized void removeAllPropertyNames() {
        propertyNames.removeAll();
    }

    /**
     * Sets the set of <code>Name</code> objects denoting the
     * properties of this node.
     * @param propNames set of {@link Name}s.
     */
    public synchronized void setPropertyNames(Set propNames) {
        propertyNames.replaceAll(propNames);
    }

    /**
     * Set the node type name. Needed for deserialization and should therefore
     * not change the internal status.
     *
     * @param nodeTypeName node type name
     */
    public synchronized void setNodeTypeName(Name nodeTypeName) {
        this.nodeTypeName = nodeTypeName;
    }

    /**
     * Return a flag indicating whether this state is shareable, i.e. whether
     * there is at least one member inside its shared set.
     * @return <code>true</code> if this state is shareable.
     */
    public synchronized boolean isShareable() {
        return sharedSet != Collections.EMPTY_SET;
    }

    /**
     * Add a parent to the shared set.
     *
     * @param parentId parent id to add to the shared set
     * @return <code>true</code> if the parent was successfully added;
     *         <code>false</code> otherwise
     */
    public synchronized boolean addShare(NodeId parentId) {
        // check first before making changes
        if (sharedSet.contains(parentId)) {
            return false;
        }
        if (!sharedSetRW) {
            sharedSet = new LinkedHashSet(sharedSet);
            sharedSetRW = true;
        }
        return sharedSet.add(parentId);
    }

    /**
     * Return a flag whether the given parent id appears in the shared set.
     *
     * @param parentId parent id
     * @return <code>true</code> if the parent id appears in the shared set;
     *         <code>false</code> otherwise.
     */
    public synchronized boolean containsShare(NodeId parentId) {
        return sharedSet.contains(parentId);
    }

    /**
     * Return the shared set as an unmodifiable collection.
     *
     * @return unmodifiable collection
     */
    public Set getSharedSet() {
        if (sharedSet != Collections.EMPTY_SET) {
            return Collections.unmodifiableSet(sharedSet);
        }
        return Collections.EMPTY_SET;
    }

    /**
     * Set the shared set of this state to the shared set of another state.
     * This state will get a deep copy of the shared set given.
     *
     * @param set shared set
     */
    public synchronized void setSharedSet(Set set) {
        if (set != Collections.EMPTY_SET) {
            sharedSet = new LinkedHashSet(set);
            sharedSetRW = true;
        } else {
            sharedSet = Collections.EMPTY_SET;
            sharedSetRW = false;
        }
    }

    /**
     * Remove a parent from the shared set. Returns the number of
     * elements in the shared set. If this number is <code>0</code>,
     * the shared set is empty, i.e. there are no more parent items
     * referencing this item and the state is free floating.
     *
     * @param parentId parent id to remove from the shared set
     * @return the number of elements left in the shared set
     */
    public synchronized int removeShare(NodeId parentId) {
        // check first before making changes
        if (sharedSet.contains(parentId)) {
            if (!sharedSetRW) {
                sharedSet = new LinkedHashSet(sharedSet);
                sharedSetRW = true;
            }
            sharedSet.remove(parentId);
            if (parentId.equals(this.parentId)) {
                if (!sharedSet.isEmpty()) {
                    this.parentId = (NodeId) sharedSet.iterator().next();
                } else {
                    this.parentId = null;
                }
            }
        }
        return sharedSet.size();
    }

    //---------------------------------------------------------< diff methods >
    /**
     * Returns a set of <code>Name</code>s denoting those properties that
     * do not exist in the overlayed node state but have been added to
     * <i>this</i> node state.
     *
     * @return set of <code>Name</code>s denoting the properties that have
     *         been added.
     */
    public synchronized Set getAddedPropertyNames() {
        if (!hasOverlayedState()) {
            return propertyNames;
        }

        NodeState other = (NodeState) getOverlayedState();
        HashSet set = new HashSet(propertyNames);
        set.removeAll(other.propertyNames);
        return set;
    }

    /**
     * Returns a list of child node entries that do not exist in the overlayed
     * node state but have been added to <i>this</i> node state.
     *
     * @return list of added child node entries
     */
    public synchronized List getAddedChildNodeEntries() {
        if (!hasOverlayedState()) {
            return childNodeEntries;
        }

        NodeState other = (NodeState) getOverlayedState();
        return childNodeEntries.removeAll(other.childNodeEntries);
    }

    /**
     * Returns a set of <code>Name</code>s denoting those properties that
     * exist in the overlayed node state but have been removed from
     * <i>this</i> node state.
     *
     * @return set of <code>Name</code>s denoting the properties that have
     *         been removed.
     */
    public synchronized Set getRemovedPropertyNames() {
        if (!hasOverlayedState()) {
            return Collections.EMPTY_SET;
        }

        NodeState other = (NodeState) getOverlayedState();
        HashSet set = new HashSet(other.propertyNames);
        set.removeAll(propertyNames);
        return set;
    }

    /**
     * Returns a list of child node entries, that exist in the overlayed node state
     * but have been removed from <i>this</i> node state.
     *
     * @return list of removed child node entries
     */
    public synchronized List getRemovedChildNodeEntries() {
        if (!hasOverlayedState()) {
            return Collections.EMPTY_LIST;
        }

        NodeState other = (NodeState) getOverlayedState();
        return other.childNodeEntries.removeAll(childNodeEntries);
    }

    /**
     * Returns a list of child node entries that exist both in <i>this</i> node
     * state and in the overlayed node state but have been renamed.
     *
     * @return list of renamed child node entries
     */
    public synchronized List getRenamedChildNodeEntries() {
        if (!hasOverlayedState()) {
            return Collections.EMPTY_LIST;
        }

        ChildNodeEntries otherChildNodeEntries =
                ((NodeState) overlayedState).childNodeEntries;

        // do a lazy init
        List renamed = null;

        for (Iterator iter = childNodeEntries.iterator(); iter.hasNext();) {
            ChildNodeEntry cne = (ChildNodeEntry) iter.next();
            ChildNodeEntry cneOther = otherChildNodeEntries.get(cne.getId());
            if (cneOther != null && !cne.getName().equals(cneOther.getName())) {
                // child node entry with same id but different name exists in
                // overlayed and this state => renamed entry detected
                if (renamed == null) {
                    renamed = new ArrayList();
                }
                renamed.add(cne);
            }
        }

        if (renamed == null) {
            return Collections.EMPTY_LIST;
        } else {
            return renamed;
        }
    }

    /**
     * Returns a list of child node entries that exist both in <i>this</i> node
     * state and in the overlayed node state but have been reordered.
     * <p/>
     * The list may include only the minimal set of nodes that have been
     * reordered. That is, even though a certain number of nodes have changed
     * their absolute position the list may include less that this number of
     * nodes.
     * <p/>
     * Example:<br/>
     * Initial state:
     * <pre>
     *  + node1
     *  + node2
     *  + node3
     * </pre>
     * After reorder:
     * <pre>
     *  + node2
     *  + node3
     *  + node1
     * </pre>
     * All nodes have changed their absolute position. The returned list however
     * may only return that <code>node1</code> has been reordered (from the
     * first position to the end).
     *
     * @return list of reordered child node enties.
     */
    public synchronized List getReorderedChildNodeEntries() {
        if (!hasOverlayedState()) {
            return Collections.EMPTY_LIST;
        }

        ChildNodeEntries otherChildNodeEntries =
                ((NodeState) overlayedState).childNodeEntries;

        if (childNodeEntries.isEmpty()
                || otherChildNodeEntries.isEmpty()) {
            return Collections.EMPTY_LIST;
        }

        // build intersections of both collections,
        // each preserving their relative order
        List ours = childNodeEntries.retainAll(otherChildNodeEntries);
        List others = otherChildNodeEntries.retainAll(childNodeEntries);

        // do a lazy init
        List reordered = null;
        // both entry lists now contain the set of nodes that have not
        // been removed or added, but they may have changed their position.
        for (int i = 0; i < ours.size();) {
            ChildNodeEntry entry = (ChildNodeEntry) ours.get(i);
            ChildNodeEntry other = (ChildNodeEntry) others.get(i);
            if (entry == other || entry.getId().equals(other.getId())) {
                // no reorder, move to next child entry
                i++;
            } else {
                // reordered entry detected
                if (reordered == null) {
                    reordered = new ArrayList();
                }
                // Note that this check will not necessarily find the
                // minimal reorder operations required to convert the overlayed
                // child node entries into the current.

                // is there a next entry?
                if (i + 1 < ours.size()) {
                    // if entry is the next in the other list then probably
                    // the other entry at position <code>i</code> was reordered
                    if (entry.getId().equals(((ChildNodeEntry) others.get(i + 1)).getId())) {
                        // scan for the uuid of the other entry in our list
                        for (int j = i; j < ours.size(); j++) {
                            if (((ChildNodeEntry) ours.get(j)).getId().equals(other.getId())) {
                                // found it
                                entry = (ChildNodeEntry) ours.get(j);
                                break;
                            }
                        }
                    }
                }

                reordered.add(entry);
                // remove the entry from both lists
                // entries > i are already cleaned
                for (int j = i; j < ours.size(); j++) {
                    if (((ChildNodeEntry) ours.get(j)).getId().equals(entry.getId())) {
                        ours.remove(j);
                    }
                }
                for (int j = i; j < ours.size(); j++) {
                    if (((ChildNodeEntry) others.get(j)).getId().equals(entry.getId())) {
                        others.remove(j);
                    }
                }
                // if a reorder has been detected index <code>i</code> is not
                // incremented because entries will be shifted when the
                // reordered entry is removed.
            }
        }
        if (reordered == null) {
            return Collections.EMPTY_LIST;
        } else {
            return reordered;
        }
    }

    //--------------------------------------------------< ItemState overrides >

    /**
     * {@inheritDoc}
     * <p/>
     * If the listener passed is at the same time a <code>NodeStateListener</code>
     * we remember it as well.
     */
    public void setContainer(ItemStateListener listener) {
        if (listener instanceof NodeStateListener) {
            if (this.listener != null) {
                throw new IllegalStateException("State already connected to a listener: " + this.listener);
            }
            this.listener = (NodeStateListener) listener;
        }
        super.setContainer(listener);
    }

    //-------------------------------------------------< misc. helper methods >

    /**
     * {@inheritDoc}
     */
    public long calculateMemoryFootprint() {
        /*
        private Name nodeTypeName;
        private Set mixinTypeNames = Collections.EMPTY_SET;
        private NodeId id;
        private NodeId parentId;
        private NodeDefId defId;
        private ChildNodeEntries childNodeEntries = new ChildNodeEntries();
        private boolean sharedChildNodeEntries = false;
        private HashSet propertyNames = new HashSet();
        private boolean sharedPropertyNames = false;

        we assume an average Name localname of 30 chars.
        NodeId = 8 + UUID(24) + hashcode(4) = 36
        Name = 8 + hash(4) + string(38+2*len) + namespace(4) + localName(38+2*len) ~ 250
        NodeDefId = 8 + id(4) = 12
        ChildNodeEntries = 8 + n * (name(256) + index(4) + id(36) + hashentry(16)) ~ n*300
        PropNames = 8 + n * ( name(250))
        */
        return 350 + mixinTypeNames.size() * 250 + childNodeEntries.size() * 300
                + propertyNames.size() * 250;
    }

    /**
     * Notify the listeners that a child node entry has been added
     * @param added the entry that was added
     */
    protected void notifyNodeAdded(ChildNodeEntry added) {
        if (listener != null) {
            listener.nodeAdded(this, added.getName(), added.getIndex(), added.getId());
        }
    }

    /**
     * Notify the listeners that the child node entries have been replaced
     */
    protected void notifyNodesReplaced() {
        if (listener != null) {
            listener.nodesReplaced(this);
        }
    }

    /**
     * Notify the listeners that a child node entry has been removed
     * @param removed the entry that was removed
     */
    protected void notifyNodeRemoved(ChildNodeEntry removed) {
        if (listener != null) {
            listener.nodeRemoved(this, removed.getName(), removed.getIndex(), removed.getId());
        }
    }
}
