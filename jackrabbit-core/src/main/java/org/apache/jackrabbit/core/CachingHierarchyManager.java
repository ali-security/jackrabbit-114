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
package org.apache.jackrabbit.core;

import org.apache.commons.collections.map.ReferenceMap;
import org.apache.jackrabbit.core.state.ItemState;
import org.apache.jackrabbit.core.state.ItemStateException;
import org.apache.jackrabbit.core.state.ItemStateManager;
import org.apache.jackrabbit.core.state.NodeState;
import org.apache.jackrabbit.core.state.NodeStateListener;
import org.apache.jackrabbit.core.state.ChildNodeEntry;
import org.apache.jackrabbit.core.util.Dumpable;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.Path;
import org.apache.jackrabbit.spi.commons.conversion.MalformedPathException;
import org.apache.jackrabbit.spi.commons.name.PathBuilder;
import org.apache.jackrabbit.spi.commons.name.PathFactoryImpl;
import org.apache.jackrabbit.spi.commons.name.PathMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Implementation of a <code>HierarchyManager</code> that caches paths of
 * items.
 */
public class CachingHierarchyManager extends HierarchyManagerImpl
        implements NodeStateListener, Dumpable {

    /**
     * Default upper limit of cached states
     */
    public static final int DEFAULT_UPPER_LIMIT = 10000;

    /**
     * Logger instance
     */
    private static Logger log = LoggerFactory.getLogger(CachingHierarchyManager.class);

    /**
     * Mapping of paths to children in the path map
     */
    private final PathMap pathCache = new PathMap();

    /**
     * Mapping of item ids to <code>LRUEntry</code> in the path map
     */
    private final ReferenceMap idCache = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.HARD);

    /**
     * Cache monitor object
     */
    private final Object cacheMonitor = new Object();

    /**
     * Upper limit
     */
    private final int upperLimit;

    /**
     * Head of LRU
     */
    private LRUEntry head;

    /**
     * Tail of LRU
     */
    private LRUEntry tail;

    /**
     * Flag indicating whether consistency checking is enabled.
     */
    private boolean consistencyCheckEnabled;

    /**
     * Create a new instance of this class.
     *
     * @param rootNodeId   root node id
     * @param provider     item state manager
     */
    public CachingHierarchyManager(NodeId rootNodeId,
                                   ItemStateManager provider) {
        super(rootNodeId, provider);
        upperLimit = DEFAULT_UPPER_LIMIT;
    }

    /**
     * Enable or disable consistency checks in this instance.
     *
     * @param enable <code>true</code> to enable consistency checks;
     *               <code>false</code> to disable
     */
    public void enableConsistencyChecks(boolean enable) {
        this.consistencyCheckEnabled = enable;
    }

    //-------------------------------------------------< base class overrides >

    /**
     * {@inheritDoc}
     */
    protected ItemId resolvePath(Path path, int typesAllowed)
            throws RepositoryException {

        Path pathToNode = path;
        if ((typesAllowed & RETURN_NODE) == 0) {
            // if we must not return a node, pass parent path
            // (since we only cache nodes)
            pathToNode = path.getAncestor(1);
        }

        PathMap.Element element = map(pathToNode);
        if (element == null) {
            // not even intermediate match: call base class
            return super.resolvePath(path, typesAllowed);
        }

        LRUEntry entry = (LRUEntry) element.get();
        if (element.hasPath(path)) {
            // exact match: return answer
            synchronized (cacheMonitor) {
                entry.touch();
            }
            return entry.getId();
        }
        Path.Element[] elements = path.getElements();
        try {
            return resolvePath(elements, element.getDepth() + 1, entry.getId(), typesAllowed);
        } catch (ItemStateException e) {
            String msg = "failed to retrieve state of intermediary node";
            log.debug(msg);
            throw new RepositoryException(msg, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void pathResolved(ItemId id, PathBuilder builder)
            throws MalformedPathException {

        if (id.denotesNode()) {
            cache((NodeId) id, builder.getPath());
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Overridden method tries to find a mapping for the intermediate item
     * <code>state</code> and add its path elements to the builder currently
     * being used. If no mapping is found, the item is cached instead after
     * the base implementation has been invoked.
     */
    protected void buildPath(PathBuilder builder, ItemState state)
            throws ItemStateException, RepositoryException {

        if (state.isNode()) {
            PathMap.Element element = get(state.getId());
            if (element != null) {
                try {
                    Path.Element[] elements = element.getPath().getElements();
                    for (int i = elements.length - 1; i >= 0; i--) {
                        builder.addFirst(elements[i]);
                    }
                    return;
                } catch (MalformedPathException mpe) {
                    String msg = "Failed to build path of " + state.getId();
                    log.debug(msg);
                    throw new RepositoryException(msg, mpe);
                }
            }
        }

        super.buildPath(builder, state);

        if (state.isNode()) {
            try {
                cache(((NodeState) state).getNodeId(), builder.getPath());
            } catch (MalformedPathException mpe) {
                log.warn("Failed to build path of " + state.getId());
            }
        }
    }

    //-----------------------------------------------------< HierarchyManager >

    /**
     * {@inheritDoc}
     * <p/>
     * Overridden method simply checks whether we have an item matching the id
     * and returns its path, otherwise calls base implementation.
     */
    public Path getPath(ItemId id)
            throws ItemNotFoundException, RepositoryException {

        if (id.denotesNode()) {
            PathMap.Element element = get(id);
            if (element != null) {
                try {
                    return element.getPath();
                } catch (MalformedPathException mpe) {
                    String msg = "Failed to build path of " + id;
                    log.debug(msg);
                    throw new RepositoryException(msg, mpe);
                }
            }
        }
        return super.getPath(id);
    }

    /**
     * {@inheritDoc}
     */
    public Name getName(ItemId id)
            throws ItemNotFoundException, RepositoryException {

        if (id.denotesNode()) {
            PathMap.Element element = get(id);
            if (element != null) {
                return element.getName();
            }
        }
        return super.getName(id);
    }

    /**
     * {@inheritDoc}
     */
    public int getDepth(ItemId id)
            throws ItemNotFoundException, RepositoryException {

        if (id.denotesNode()) {
            PathMap.Element element = get(id);
            if (element != null) {
                return element.getDepth();
            }
        }
        return super.getDepth(id);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isAncestor(NodeId nodeId, ItemId itemId)
            throws ItemNotFoundException, RepositoryException {

        if (itemId.denotesNode()) {
            PathMap.Element element = get(nodeId);
            if (element != null) {
                PathMap.Element child = get(itemId);
                if (child != null) {
                    return element.isAncestorOf(child);
                }
            }
        }
        return super.isAncestor(nodeId, itemId);
    }

    //----------------------------------------------------< ItemStateListener >

    /**
     * {@inheritDoc}
     */
    public void stateCreated(ItemState created) {
    }

    /**
     * {@inheritDoc}
     */
    public void stateModified(ItemState modified) {
        if (modified.isNode()) {
            nodeModified((NodeState) modified);
        }
    }

    /**
     * {@inheritDoc}
     *
     * If path information is cached for <code>modified</code>, this iterates
     * over all child nodes in the path map, evicting the ones that do not
     * (longer) exist in the underlying <code>NodeState</code>.
     */
    public void nodeModified(NodeState modified) {
        synchronized (cacheMonitor) {
            LRUEntry entry = (LRUEntry) idCache.get(modified.getNodeId());
            if (entry == null) {
                // Item not cached, ignore
                return;
            }
            PathMap.Element[] elements = entry.getElements();
            for (int i = 0; i < elements.length; i++) {
                Iterator iter = elements[i].getChildren();
                while (iter.hasNext()) {
                    PathMap.Element child = (PathMap.Element) iter.next();
                    ChildNodeEntry cne = modified.getChildNodeEntry(
                            child.getName(), child.getNormalizedIndex());
                    if (cne == null) {
                        // Item does not exist, remove
                        evict(child, true);
                        continue;
                    }

                    LRUEntry childEntry = (LRUEntry) child.get();
                    if (childEntry != null && !cne.getId().equals(childEntry.getId())) {
                        // Different child item, remove
                        evict(child, true);
                    }
                }
            }
            checkConsistency();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void stateDestroyed(ItemState destroyed) {
        evictAll(destroyed.getId(), true);
    }

    /**
     * {@inheritDoc}
     */
    public void stateDiscarded(ItemState discarded) {
        if (discarded.isTransient() && !discarded.hasOverlayedState()
                && discarded.getStatus() == ItemState.STATUS_NEW) {
            // a new node has been discarded -> remove from cache
            evictAll(discarded.getId(), true);
        } else if (provider.hasItemState(discarded.getId())) {
            evictAll(discarded.getId(), false);
        } else {
            evictAll(discarded.getId(), true);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void nodeAdded(NodeState state, Name name, int index, NodeId id) {
        // Optimization: ignore notifications for nodes that are not in the cache
        synchronized (cacheMonitor) {
            if (idCache.containsKey(state.getNodeId())) {
                try {
                    Path path = PathFactoryImpl.getInstance().create(getPath(state.getNodeId()), name, index, true);
                    nodeAdded(state, path, id);
                    checkConsistency();
                } catch (PathNotFoundException e) {
                    log.warn("Unable to get path of node " + state.getNodeId()
                            + ", event ignored.");
                } catch (MalformedPathException e) {
                    log.warn("Unable to create path of " + id, e);
                } catch (ItemNotFoundException e) {
                    log.warn("Unable to find item " + state.getNodeId(), e);
                } catch (ItemStateException e) {
                    log.warn("Unable to find item " + id, e);
                } catch (RepositoryException e) {
                    log.warn("Unable to get path of " + state.getNodeId(), e);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Iterate over all cached children of this state and verify each
     * child's position.
     */
    public void nodesReplaced(NodeState state) {
        synchronized (cacheMonitor) {
            LRUEntry entry = (LRUEntry) idCache.get(state.getNodeId());
            if (entry == null) {
                return;
            }
            PathMap.Element[] parents = entry.getElements();
            for (int i = 0; i < parents.length; i++) {
                HashMap newChildrenOrder = new HashMap();
                boolean orderChanged = false;

                Iterator iter = parents[i].getChildren();
                while (iter.hasNext()) {
                    PathMap.Element child = (PathMap.Element) iter.next();
                    LRUEntry childEntry = (LRUEntry) child.get();
                    if (childEntry == null) {
                        /**
                         * Child has no associated UUID information: we're
                         * therefore unable to determine if this child's
                         * position is still accurate and have to assume
                         * the worst and remove it.
                         */
                        evict(child, false);
                        continue;
                    }
                    NodeId childId = childEntry.getId();
                    ChildNodeEntry cne = state.getChildNodeEntry(childId);
                    if (cne == null) {
                        /* Child no longer in parent node state, so remove it */
                        evict(child, false);
                        continue;
                    }

                    /**
                     * Put all children into map of new children order - regardless
                     * whether their position changed or not - as we might need
                     * to reorder them later on.
                     */
                    Path.Element newNameIndex = PathFactoryImpl.getInstance().createElement(
                            cne.getName(), cne.getIndex());
                    newChildrenOrder.put(newNameIndex, child);

                    if (!newNameIndex.equals(child.getPathElement())) {
                        orderChanged = true;
                    }
                }

                if (orderChanged) {
                    /* If at least one child changed its position, reorder */
                    parents[i].setChildren(newChildrenOrder);
                }
            }
            checkConsistency();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void nodeRemoved(NodeState state, Name name, int index, NodeId id) {
        // Optimization: ignore notifications for nodes that are not in the cache
        synchronized (cacheMonitor) {
            if (idCache.containsKey(state.getNodeId())) {
                try {
                    Path path = PathFactoryImpl.getInstance().create(getPath(state.getNodeId()), name, index, true);
                    nodeRemoved(state, path, id);
                    checkConsistency();
                } catch (PathNotFoundException e) {
                    log.warn("Unable to get path of node " + state.getNodeId()
                            + ", event ignored.");
                } catch (MalformedPathException e) {
                    log.warn("Unable to create path of " + id, e);
                } catch (ItemStateException e) {
                    log.warn("Unable to find item " + id, e);
                } catch (ItemNotFoundException e) {
                    log.warn("Unable to get path of " + state.getNodeId(), e);
                } catch (RepositoryException e) {
                    log.warn("Unable to get path of " + state.getNodeId(), e);
                }
            }
        }
    }

    //------------------------------------------------------< private methods >

    /**
     * Return the first cached path that is mapped to given id.
     *
     * @param id node id
     * @return cached element, <code>null</code> if not found
     */
    private PathMap.Element get(ItemId id) {
        synchronized (cacheMonitor) {
            LRUEntry entry = (LRUEntry) idCache.get(id);
            if (entry != null) {
                entry.touch();
                return entry.getElements()[0];
            }
            return null;
        }
    }

    /**
     * Return the nearest cached element in the path map, given a path.
     * The returned element is guaranteed to have an associated object that
     * is not <code>null</code>.
     *
     * @param path path
     * @return cached element, <code>null</code> if not found
     */
    private PathMap.Element map(Path path) {
        synchronized (cacheMonitor) {
            PathMap.Element element = pathCache.map(path, false);
            while (element != null) {
                LRUEntry entry = (LRUEntry) element.get();
                if (entry != null) {
                    entry.touch();
                    return element;
                }
                element = element.getParent();
            }
            return null;
        }
    }

    /**
     * Cache an item in the hierarchy given its id and path.
     *
     * @param id   node id
     * @param path path to item
     */
    private void cache(NodeId id, Path path) {
        synchronized (cacheMonitor) {
            if (isCached(id, path)) {
                return;
            }
            if (idCache.size() >= upperLimit) {
                /**
                 * Remove least recently used item. Scans the LRU list from
                 * head to tail and removes the first item that has no children.
                 */
                LRUEntry entry = head;
                while (entry != null) {
                    PathMap.Element[] elements = entry.getElements();
                    int childrenCount = 0;
                    for (int i = 0; i < elements.length; i++) {
                        childrenCount += elements[i].getChildrenCount();
                    }
                    if (childrenCount == 0) {
                        evictAll(entry.getId(), false);
                        return;
                    }
                    entry = entry.getNext();
                }
            }
            PathMap.Element element = pathCache.put(path);
            if (element.get() != null) {
                if (!id.equals(((LRUEntry) element.get()).getId())) {
                    log.warn("overwriting PathMap.Element");
                }
            }
            LRUEntry entry = (LRUEntry) idCache.get(id);
            if (entry == null) {
                entry = new LRUEntry(id, element);
                idCache.put(id, entry);
            } else {
                entry.addElement(element);
            }
            element.set(entry);

            checkConsistency();
        }
    }

    /**
     * Return a flag indicating whether a certain node and/or path is cached.
     * If <code>path</code> is <code>null</code>, check whether the item is
     * cached at all. If <code>path</code> is <b>not</b> <code>null</code>,
     * check whether the item is cached with that path.
     *
     * @param id item id
     * @param path path, may be <code>null</code>
     * @return <code>true</code> if the item is already cached;
     *         <code>false</code> otherwise
     */
    boolean isCached(NodeId id, Path path) {
        synchronized (cacheMonitor) {
            LRUEntry entry = (LRUEntry) idCache.get(id);
            if (entry == null) {
                return false;
            }
            if (path == null) {
                return true;
            }
            PathMap.Element[] elements = entry.getElements();
            for (int i = 0; i < elements.length; i++) {
                if (elements[i].hasPath(path)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Return a flag indicating whether a certain path is cached.
     *
     * @param id item id
     * @return <code>true</code> if the item is already cached;
     *         <code>false</code> otherwise
     */
    boolean isCached(Path path) {
        synchronized (cacheMonitor) {
            PathMap.Element element = pathCache.map(path, true);
            if (element != null) {
                return element.get() != null;
            }
            return false;
        }
    }

    /**
     * Remove all path mapping for a given item id. Removes the associated
     * <code>LRUEntry</code> and the <code>PathMap.Element</code> with it.
     * Indexes of same name sibling elements are shifted!
     *
     * @param id item id
     */
    private void evictAll(ItemId id, boolean shift) {
        synchronized (cacheMonitor) {
            LRUEntry entry = (LRUEntry) idCache.get(id);
            if (entry != null) {
                PathMap.Element[] elements = entry.getElements();
                for (int i = 0; i < elements.length; i++) {
                    evict(elements[i], shift);
                }
            }
            checkConsistency();
        }
    }

    /**
     * Evict path map element from cache. This will traverse all children
     * of this element and remove the objects associated with them.
     * Index of same name sibling items are shifted!
     *
     * @param element path map element
     */
    private void evict(PathMap.Element element, boolean shift) {
        // assert: synchronized (cacheMonitor)
        element.traverse(new PathMap.ElementVisitor() {
            public void elementVisited(PathMap.Element element) {
                LRUEntry entry = (LRUEntry) element.get();
                if (entry.removeElement(element) == 0) {
                    idCache.remove(entry.getId());
                    entry.remove();
                }
            }
        }, false);
        element.remove(shift);
    }

    /**
     * Invoked when a notification about a child node addition has been received.
     *
     * @param state node state where child was added
     * @param path  path to child node
     * @param id    child node id
     *
     * @throws PathNotFoundException if the path was not found
     */
    private void nodeAdded(NodeState state, Path path, NodeId id)
            throws PathNotFoundException, ItemStateException {

        // assert: synchronized (cacheMonitor)
        PathMap.Element element = null;

        LRUEntry entry = (LRUEntry) idCache.get(id);
        if (entry != null) {
            // child node already cached: this can have the following
            // reasons:
            //    1) node was moved, cached path is outdated
            //    2) node was cloned, cached path is still valid
            NodeState child = null;
            if (hasItemState(id)) {
                child = (NodeState) getItemState(id);
            }
            if (child == null || !child.isShareable()) {
                PathMap.Element[] elements = entry.getElements();
                element = elements[0];
                for (int i = 0; i < elements.length; i++) {
                    elements[i].remove();
                }
            }
        }
        PathMap.Element parent = pathCache.map(path.getAncestor(1), true);
        if (parent != null) {
            parent.insert(path.getNameElement());
        }
        if (element != null) {
            // store remembered element at new position
            pathCache.put(path, element);
        }
    }

    /**
     * Invoked when a notification about a child node removal has been received.
     *
     * @param state node state
     * @param path  node path
     * @param id    node id
     *
     * @throws PathNotFoundException if the path was not found
     */
    private void nodeRemoved(NodeState state, Path path, NodeId id)
            throws PathNotFoundException, ItemStateException {

        // assert: synchronized (cacheMonitor)
        PathMap.Element parent = pathCache.map(path.getAncestor(1), true);
        if (parent == null) {
            return;
        }
        PathMap.Element element = parent.getDescendant(PathFactoryImpl.getInstance().create(
                new Path.Element[] { path.getNameElement() }), true);
        if (element != null) {
            // with SNS, this might evict a child that is NOT the one
            // having <code>id</code>, check first whether item has
            // the id passed as argument
            LRUEntry entry = (LRUEntry) element.get();
            if (entry != null && !entry.getId().equals(id)) {
                return;
            }
            // if item is shareable, remove this path only, otherwise
            // every path this item has been mapped to
            NodeState child = null;
            if (hasItemState(id)) {
                child = (NodeState) getItemState(id);
            }
            if (child == null || !child.isShareable()) {
                evictAll(id, true);
            } else {
                evict(element, true);
            }
        } else {
            // element itself is not cached, but removal might cause SNS
            // index shifting
            parent.remove(path.getNameElement());
        }
    }

    /**
     * Dump contents of path map and elements included to <code>PrintStream</code> given.
     *
     * @param ps print stream to dump to
     */
    public void dump(final PrintStream ps) {
        synchronized (cacheMonitor) {
            pathCache.traverse(new PathMap.ElementVisitor() {
                public void elementVisited(PathMap.Element element) {
                    StringBuffer line = new StringBuffer();
                    for (int i = 0; i < element.getDepth(); i++) {
                        line.append("--");
                    }
                    line.append(element.getName());
                    int index = element.getIndex();
                    if (index != 0 && index != 1) {
                        line.append('[');
                        line.append(index);
                        line.append(']');
                    }
                    line.append("  ");
                    line.append(element.get());
                    ps.println(line.toString());
                }
            }, true);
        }
    }

    /**
     * Check consistency.
     */
    private void checkConsistency() throws IllegalStateException {
        // assert: synchronized (cacheMonitor)
        if (!consistencyCheckEnabled) {
            return;
        }

        int elementsInCache = 0;

        Iterator iter = idCache.values().iterator();
        while (iter.hasNext()) {
            LRUEntry entry = (LRUEntry) iter.next();
            elementsInCache += entry.getElements().length;
        }

        class PathMapElementCounter implements PathMap.ElementVisitor {
            int count;
            public void elementVisited(PathMap.Element element) {
                LRUEntry mappedEntry = (LRUEntry) element.get();
                LRUEntry cachedEntry = (LRUEntry) idCache.get(mappedEntry.getId());
                if (cachedEntry == null) {
                    String msg = "Path element (" + element +
                        " ) cached in path map, associated id (" +
                        mappedEntry.getId() + ") isn't.";
                    throw new IllegalStateException(msg);
                }
                if (cachedEntry != mappedEntry) {
                    String msg = "LRUEntry associated with element (" + element +
                        " ) in path map is not equal to cached LRUEntry (" +
                        cachedEntry.getId() + ").";
                    throw new IllegalStateException(msg);
                }
                PathMap.Element[] elements = cachedEntry.getElements();
                for (int i = 0; i < elements.length; i++) {
                    if (elements[i] == element) {
                        count++;
                        return;
                    }
                }
                String msg = "Element (" + element +
                    ") cached in path map, but not in associated LRUEntry (" +
                    cachedEntry.getId() + ").";
                throw new IllegalStateException(msg);
            }
        }

        PathMapElementCounter counter = new PathMapElementCounter();
        pathCache.traverse(counter, false);
        if (counter.count != elementsInCache) {
            String msg = "PathMap element and cached element count don't match (" +
                counter.count + " != " + elementsInCache + ")";
            throw new IllegalStateException(msg);
        }
    }

    /**
     * Entry in the LRU list
     */
    private class LRUEntry {

        /**
         * Previous entry
         */
        private LRUEntry previous;

        /**
         * Next entry
         */
        private LRUEntry next;

        /**
         * Node id
         */
        private final NodeId id;

        /**
         * Elements in path map
         */
        private PathMap.Element[] elements;

        /**
         * Create a new instance of this class
         *
         * @param id node id
         * @param element the path map element for this entry
         */
        public LRUEntry(NodeId id, PathMap.Element element) {
            this.id = id;
            this.elements = new PathMap.Element[] { element };

            append();
        }

        /**
         * Append entry to end of LRU list
         */
        public void append() {
            if (tail == null) {
                head = this;
                tail = this;
            } else {
                previous = tail;
                tail.next = this;
                tail = this;
            }
        }

        /**
         * Remove entry from LRU list
         */
        public void remove() {
            if (previous != null) {
                previous.next = next;
            }
            if (next != null) {
                next.previous = previous;
            }
            if (head == this) {
                head = next;
            }
            if (tail == this) {
                tail = previous;
            }
            previous = null;
            next = null;
        }

        /**
         * Touch entry. Removes it from its current position in the LRU list
         * and moves it to the end.
         */
        public void touch() {
            remove();
            append();
        }

        /**
         * Return previous LRU entry
         *
         * @return previous LRU entry
         */
        public LRUEntry getPrevious() {
            return previous;
        }

        /**
         * Return next LRU entry
         *
         * @return next LRU entry
         */
        public LRUEntry getNext() {
            return next;
        }

        /**
         * Return node ID
         *
         * @return node ID
         */
        public NodeId getId() {
            return id;
        }

        /**
         * Return elements in path map that are mapped to <code>id</code>. If
         * this entry is a shareable node or one of its descendant, it can
         * be reached by more than one path.
         *
         * @return element in path map
         */
        public PathMap.Element[] getElements() {
            return elements;
        }

        /**
         * Add a mapping to some element.
         */
        public void addElement(PathMap.Element element) {
            PathMap.Element[] tmp = new PathMap.Element[elements.length + 1];
            System.arraycopy(elements, 0, tmp, 0, elements.length);
            tmp[elements.length] = element;
            elements = tmp;
        }

        /**
         * Remove a mapping to some element from this entry.
         *
         * @return number of mappings left
         */
        public int removeElement(PathMap.Element element) {
            boolean found = false;
            for (int i = 0; i < elements.length; i++) {
                if (found) {
                    elements[i - 1] = elements[i];
                } else if (elements[i] == element) {
                    found = true;
                }
            }
            if (found) {
                PathMap.Element[] tmp = new PathMap.Element[elements.length - 1];
                System.arraycopy(elements, 0, tmp, 0, tmp.length);
                elements = tmp;
            }
            return elements.length;
        }

        /**
         * {@inheritDoc}
         */
        public String toString() {
            return id.toString();
        }
    }
}
