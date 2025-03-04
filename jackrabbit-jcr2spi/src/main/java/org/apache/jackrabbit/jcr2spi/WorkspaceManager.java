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
package org.apache.jackrabbit.jcr2spi;

import org.apache.jackrabbit.jcr2spi.nodetype.NodeTypeRegistryImpl;
import org.apache.jackrabbit.jcr2spi.nodetype.NodeTypeRegistry;
import org.apache.jackrabbit.jcr2spi.nodetype.NodeTypeStorage;
import org.apache.jackrabbit.jcr2spi.nodetype.ItemDefinitionProvider;
import org.apache.jackrabbit.jcr2spi.nodetype.ItemDefinitionProviderImpl;
import org.apache.jackrabbit.jcr2spi.nodetype.EffectiveNodeTypeProvider;
import org.apache.jackrabbit.jcr2spi.nodetype.NodeTypeCache;
import org.apache.jackrabbit.jcr2spi.state.ItemState;
import org.apache.jackrabbit.jcr2spi.state.ChangeLog;
import org.apache.jackrabbit.jcr2spi.state.UpdatableItemStateManager;
import org.apache.jackrabbit.jcr2spi.state.ItemStateFactory;
import org.apache.jackrabbit.jcr2spi.state.WorkspaceItemStateFactory;
import org.apache.jackrabbit.jcr2spi.state.NodeState;
import org.apache.jackrabbit.jcr2spi.state.TransientItemStateFactory;
import org.apache.jackrabbit.jcr2spi.state.TransientISFactory;
import org.apache.jackrabbit.jcr2spi.state.Status;
import org.apache.jackrabbit.jcr2spi.operation.OperationVisitor;
import org.apache.jackrabbit.jcr2spi.operation.AddNode;
import org.apache.jackrabbit.jcr2spi.operation.AddProperty;
import org.apache.jackrabbit.jcr2spi.operation.Clone;
import org.apache.jackrabbit.jcr2spi.operation.Copy;
import org.apache.jackrabbit.jcr2spi.operation.Move;
import org.apache.jackrabbit.jcr2spi.operation.Remove;
import org.apache.jackrabbit.jcr2spi.operation.SetMixin;
import org.apache.jackrabbit.jcr2spi.operation.SetPropertyValue;
import org.apache.jackrabbit.jcr2spi.operation.ReorderNodes;
import org.apache.jackrabbit.jcr2spi.operation.Operation;
import org.apache.jackrabbit.jcr2spi.operation.Checkout;
import org.apache.jackrabbit.jcr2spi.operation.Checkin;
import org.apache.jackrabbit.jcr2spi.operation.Update;
import org.apache.jackrabbit.jcr2spi.operation.Restore;
import org.apache.jackrabbit.jcr2spi.operation.ResolveMergeConflict;
import org.apache.jackrabbit.jcr2spi.operation.Merge;
import org.apache.jackrabbit.jcr2spi.operation.LockOperation;
import org.apache.jackrabbit.jcr2spi.operation.LockRefresh;
import org.apache.jackrabbit.jcr2spi.operation.LockRelease;
import org.apache.jackrabbit.jcr2spi.operation.AddLabel;
import org.apache.jackrabbit.jcr2spi.operation.RemoveLabel;
import org.apache.jackrabbit.jcr2spi.operation.RemoveVersion;
import org.apache.jackrabbit.jcr2spi.operation.WorkspaceImport;
import org.apache.jackrabbit.jcr2spi.security.AccessManager;
import org.apache.jackrabbit.jcr2spi.observation.InternalEventListener;
import org.apache.jackrabbit.jcr2spi.config.CacheBehaviour;
import org.apache.jackrabbit.jcr2spi.hierarchy.HierarchyEventListener;
import org.apache.jackrabbit.jcr2spi.hierarchy.HierarchyManager;
import org.apache.jackrabbit.jcr2spi.hierarchy.HierarchyManagerImpl;
import org.apache.jackrabbit.spi.Path;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.RepositoryService;
import org.apache.jackrabbit.spi.SessionInfo;
import org.apache.jackrabbit.spi.NodeId;
import org.apache.jackrabbit.spi.IdFactory;
import org.apache.jackrabbit.spi.LockInfo;
import org.apache.jackrabbit.spi.QueryInfo;
import org.apache.jackrabbit.spi.ItemId;
import org.apache.jackrabbit.spi.PropertyId;
import org.apache.jackrabbit.spi.Batch;
import org.apache.jackrabbit.spi.EventBundle;
import org.apache.jackrabbit.spi.EventFilter;
import org.apache.jackrabbit.spi.QNodeTypeDefinition;
import org.apache.jackrabbit.spi.QValue;
import org.apache.jackrabbit.spi.Event;
import org.apache.jackrabbit.spi.NameFactory;
import org.apache.jackrabbit.spi.PathFactory;
import org.apache.jackrabbit.spi.Subscription;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.jcr.RepositoryException;
import javax.jcr.NamespaceRegistry;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.AccessDeniedException;
import javax.jcr.PathNotFoundException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.NamespaceException;
import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.ItemExistsException;
import javax.jcr.InvalidItemStateException;
import javax.jcr.MergeException;
import javax.jcr.Session;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.version.VersionException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.ConstraintViolationException;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.Collection;

import EDU.oswego.cs.dl.util.concurrent.Sync;
import EDU.oswego.cs.dl.util.concurrent.Mutex;

/**
 * <code>WorkspaceManager</code>...
 */
public class WorkspaceManager
        implements UpdatableItemStateManager, NamespaceStorage, AccessManager {

    private static Logger log = LoggerFactory.getLogger(WorkspaceManager.class);

    private final RepositoryService service;
    private final SessionInfo sessionInfo;
    private final NameFactory nameFactory;
    private final PathFactory pathFactory;

    private final ItemStateFactory isf;
    private final HierarchyManager hierarchyManager;
    private final CacheBehaviour cacheBehaviour;

    private final IdFactory idFactory;
    private final NamespaceRegistryImpl nsRegistry;
    private final NodeTypeRegistryImpl ntRegistry;
    private final ItemDefinitionProvider definitionProvider;

    /**
     * Mutex to synchronize the feed thread with client
     * threads that call {@link #execute(Operation)} or {@link
     * #execute(ChangeLog)}.
     */
    private final Sync updateSync = new Mutex();

    /**
     * This is the event polling for changes. If <code>null</code>
     * then the underlying repository service does not support observation.
     */
    private final Thread changeFeed;

    /**
     * List of event listener that are set on this WorkspaceManager to get
     * notifications about local and external changes.
     */
    private final Set listeners = new HashSet();

    /**
     * The current subscription for change events if there are listeners.
     */
    private Subscription subscription;

    public WorkspaceManager(RepositoryService service, SessionInfo sessionInfo,
                            CacheBehaviour cacheBehaviour, int pollTimeout,
                            boolean enableObservation)
        throws RepositoryException {
        this.service = service;
        this.sessionInfo = sessionInfo;
        this.cacheBehaviour = cacheBehaviour;
        this.nameFactory = service.getNameFactory();
        this.pathFactory = service.getPathFactory();

        idFactory = service.getIdFactory();
        nsRegistry = new NamespaceRegistryImpl(this);
        ntRegistry = createNodeTypeRegistry(nsRegistry);
        changeFeed = createChangeFeed(pollTimeout, enableObservation);
        definitionProvider = createDefinitionProvider(getEffectiveNodeTypeProvider());

        TransientItemStateFactory stateFactory = createItemStateFactory();
        this.isf = stateFactory;
        this.hierarchyManager = createHierarchyManager(stateFactory, idFactory);
        createHierarchyListener(hierarchyManager);
    }

    public NamespaceRegistryImpl getNamespaceRegistryImpl() {
        return nsRegistry;
    }

    public NodeTypeRegistry getNodeTypeRegistry() {
        return ntRegistry;
    }

    public ItemDefinitionProvider getItemDefinitionProvider() {
        return definitionProvider;
    }

    public EffectiveNodeTypeProvider getEffectiveNodeTypeProvider() {
        return ntRegistry;
    }

    public HierarchyManager getHierarchyManager() {
        return hierarchyManager;
    }

    public String[] getWorkspaceNames() throws RepositoryException {
        return service.getWorkspaceNames(sessionInfo);
    }

    public IdFactory getIdFactory() throws RepositoryException {
        return idFactory;
    }

    public NameFactory getNameFactory() throws RepositoryException {
        return nameFactory;
    }

    public PathFactory getPathFactory() throws RepositoryException {
        return pathFactory;
    }

    public ItemStateFactory getItemStateFactory() {
        return isf;
    }

    public LockInfo getLockInfo(NodeId nodeId) throws RepositoryException {
        return service.getLockInfo(sessionInfo, nodeId);
    }

    public String[] getLockTokens() {
        return sessionInfo.getLockTokens();
    }

    /**
     * This method always succeeds.
     * This is not compliant to the requirements for {@link Session#addLockToken(String)}
     * as defined by JSR170, which defines that at most one single <code>Session</code>
     * may contain the same lock token. However, with SPI it is not possible
     * to determine, whether another session holds the lock, nor can the client
     * determine, which lock this token belongs to. The latter would be
     * necessary in order to build the 'Lock' object properly.
     *
     * @param lt
     * @throws LockException
     * @throws RepositoryException
     */
    public void addLockToken(String lt) throws LockException, RepositoryException {
        sessionInfo.addLockToken(lt);
        /*
        // TODO: JSR170 defines that a token can be present with one session only.
        //       however, we cannot find out about another session holding the lock.
        //       and neither knows the server, which session is holding a lock token.
        */
    }

    /**
     * Tries to remove the given token from the <code>SessionInfo</code>. If the
     * SessionInfo does not contains the specified token, this method returns
     * silently.<br>
     * Note, that any restriction regarding removal of lock tokens must be asserted
     * before this method is called.
     *
     * @param lt
     * @throws LockException
     * @throws RepositoryException
     */
    public void removeLockToken(String lt) throws LockException, RepositoryException {
        String[] tokems = sessionInfo.getLockTokens();
        for (int i = 0; i < tokems.length; i++) {
            if (tokems[i].equals(lt)) {
                sessionInfo.removeLockToken(lt);
                return;
            }
        }
        // sessionInfo doesn't contain the given lock token and is therefore
        // not the lock holder
        throw new RepositoryException("Unable to remove locktoken '" + lt + "' from Session.");
    }

    /**
     *
     * @return
     * @throws RepositoryException
     */
    public String[] getSupportedQueryLanguages() throws RepositoryException {
        return service.getSupportedQueryLanguages(sessionInfo);
    }

    /**
     * Checks if the query statement is valid.
     *
     * @param statement  the query statement.
     * @param language   the query language.
     * @param namespaces the locally remapped namespaces which might be used in
     *                   the query statement.
     * @throws InvalidQueryException if the query statement is invalid.
     * @throws RepositoryException   if an error occurs while checking the query
     *                               statement.
     */
    public void checkQueryStatement(String statement,
                                    String language,
                                    Map namespaces)
            throws InvalidQueryException, RepositoryException {
        service.checkQueryStatement(sessionInfo, statement, language, namespaces);
    }

    /**
     * @param statement  the query statement.
     * @param language   the query language.
     * @param namespaces the locally remapped namespaces which might be used in
     *                   the query statement.
     * @return
     * @throws RepositoryException
     */
    public QueryInfo executeQuery(String statement, String language, Map namespaces)
            throws RepositoryException {
        return service.executeQuery(sessionInfo, statement, language, namespaces);
    }

    /**
     * Sets the <code>InternalEventListener</code> that gets notifications about
     * local and external changes.
     *
     * @param listener the new listener.
     * @throws RepositoryException if the listener cannot be registered.
     */
    public void addEventListener(InternalEventListener listener)
            throws RepositoryException {
        synchronized (listeners) {
            listeners.add(listener);
            EventFilter[] filters = getEventFilters(listeners);
            if (listeners.size() == 1) {
                subscription = service.createSubscription(sessionInfo, filters);
            } else {
                service.updateEventFilters(subscription, filters);
            }
            listeners.notify();
        }
    }

    /**
     * Updates the event filters on the subscription. The filters are retrieved
     * from the current list of internal event listeners.
     *
     * @throws RepositoryException
     */
    public void updateEventFilters() throws RepositoryException {
        synchronized (listeners) {
            service.updateEventFilters(subscription, getEventFilters(listeners));
        }
    }

    /**
     *
     * @param listener
     */
    public void removeEventListener(InternalEventListener listener)
            throws RepositoryException {
        synchronized (listeners) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                service.dispose(subscription);
                subscription = null;
            } else {
                service.updateEventFilters(subscription, getEventFilters(listeners));
            }
        }
    }

    /**
     * Creates an event filter based on the parameters available in {@link
     * javax.jcr.observation.ObservationManager#addEventListener}.
     *
     * @param eventTypes   A combination of one or more event type constants
     *                     encoded as a bitmask.
     * @param path         an absolute path.
     * @param isDeep       a <code>boolean</code>.
     * @param uuids        array of UUIDs.
     * @param nodeTypes    array of node type names.
     * @param noLocal      a <code>boolean</code>.
     * @return the event filter instance with the given parameters.
     * @throws UnsupportedRepositoryOperationException
     *          if this implementation does not support observation.
     */
    public EventFilter createEventFilter(int eventTypes, Path path, boolean isDeep,
                                         String[] uuids, Name[] nodeTypes,
                                         boolean noLocal)
        throws UnsupportedRepositoryOperationException, RepositoryException {
        return service.createEventFilter(sessionInfo, eventTypes, path, isDeep, uuids, nodeTypes, noLocal);
    }
    //--------------------------------------------------------------------------

    /**
     * Gets the event filters from the passed listener list.
     *
     * @param listeners the internal event listeners.
     */
    private static EventFilter[] getEventFilters(Collection listeners) {
        List filters = new ArrayList();
        for (Iterator it = listeners.iterator(); it.hasNext(); ) {
            InternalEventListener listener = (InternalEventListener) it.next();
            filters.addAll(listener.getEventFilters());
        }
        return (EventFilter[]) filters.toArray(new EventFilter[filters.size()]);
    }

    /**
     *
     * @return
     */
    private TransientItemStateFactory createItemStateFactory() {
        WorkspaceItemStateFactory isf = new WorkspaceItemStateFactory(service, sessionInfo, getItemDefinitionProvider());
        TransientItemStateFactory tisf = new TransientISFactory(isf, getItemDefinitionProvider());
        return tisf;
    }

    /**
     *
     * @return
     */
    private HierarchyManager createHierarchyManager(TransientItemStateFactory tisf, IdFactory idFactory) throws RepositoryException {
        return new HierarchyManagerImpl(tisf, idFactory, getPathFactory());
    }

    /**
     *
     * @return
     */
    private InternalEventListener createHierarchyListener(HierarchyManager hierarchyMgr) {
        InternalEventListener listener = new HierarchyEventListener(this, hierarchyMgr, cacheBehaviour);
        return listener;
    }

    /**
     *
     * @param nsRegistry
     * @return
     */
    private NodeTypeRegistryImpl createNodeTypeRegistry(NamespaceRegistry nsRegistry) {
        NodeTypeStorage ntst = new NodeTypeStorage() {
            public Iterator getAllDefinitions() throws RepositoryException {
                return service.getQNodeTypeDefinitions(sessionInfo);
            }
            public Iterator getDefinitions(Name[] nodeTypeNames) throws NoSuchNodeTypeException, RepositoryException {
                return service.getQNodeTypeDefinitions(sessionInfo, nodeTypeNames);
            }
            public void registerNodeTypes(QNodeTypeDefinition[] nodeTypeDefs) throws NoSuchNodeTypeException, RepositoryException {
                throw new UnsupportedOperationException("NodeType registration not yet defined by the SPI");
            }
            public void reregisterNodeTypes(QNodeTypeDefinition[] nodeTypeDefs) throws NoSuchNodeTypeException, RepositoryException {
                throw new UnsupportedOperationException("NodeType registration not yet defined by the SPI");
            }
            public void unregisterNodeTypes(Name[] nodeTypeNames) throws NoSuchNodeTypeException, RepositoryException {
                throw new UnsupportedOperationException("NodeType registration not yet defined by the SPI");
            }
        };
        NodeTypeCache ntCache = NodeTypeCache.getInstance(service, sessionInfo.getUserID());
        ntst = ntCache.wrap(ntst);
        return NodeTypeRegistryImpl.create(ntst, nsRegistry);
    }

    /**
     *
     * @param entProvider
     * @return
     */
    private ItemDefinitionProvider createDefinitionProvider(EffectiveNodeTypeProvider entProvider) {
        return new ItemDefinitionProviderImpl(entProvider, service, sessionInfo);
    }

    /**
     * Creates a background thread which polls for external changes on the
     * RepositoryService.
     *
     * @param pollTimeout the polling timeout in milliseconds.
     * @param enableObservation if observation should be enabled.
     * @return the background polling thread or <code>null</code> if the underlying
     *         <code>RepositoryService</code> does not support observation.
     */
    private Thread createChangeFeed(int pollTimeout, boolean enableObservation) {
        Thread t = null;
        if (enableObservation) {
            t = new Thread(new ChangePolling(pollTimeout));
            t.setName("Change Polling");
            t.setDaemon(true);
            t.start();
        }
        return t;
    }

    //------------------------------------------< UpdatableItemStateManager >---
    /**
     * Creates a new batch from the single workspace operation and executes it.
     *
     * @see UpdatableItemStateManager#execute(Operation)
     */
    public void execute(Operation operation) throws RepositoryException {
        // block event delivery while changes are executed
        try {
            updateSync.acquire();
        } catch (InterruptedException e) {
            throw new RepositoryException(e);
        }
        try {
            /*
            Execute operation and delegate invalidation of affected item
            states to the operation.
            NOTE, that the invalidation is independant of the cache behaviour
            due to the fact, that local eventbundles are not processed by
            the HierarchyEventListener.
            */
            new OperationVisitorImpl(sessionInfo).execute(operation);
            operation.persisted();
        } finally {
            updateSync.release();
        }
    }

    /**
     * Creates a new batch from the given <code>ChangeLog</code> and executes it.
     *
     * @param changes
     * @throws RepositoryException
     */
    public void execute(ChangeLog changes) throws RepositoryException {
        // block event delivery while changes are executed
        try {
            updateSync.acquire();
        } catch (InterruptedException e) {
            throw new RepositoryException(e);
        }
        try {
            new OperationVisitorImpl(sessionInfo).execute(changes);
            changes.persisted();
        } finally {
            updateSync.release();
        }
    }

    /**
     * Dispose this <code>WorkspaceManager</code>
     */
    public synchronized void dispose() {
        try {
            updateSync.acquire();
            if (changeFeed != null) {
                changeFeed.interrupt();
            }
            hierarchyManager.dispose();
            if (subscription != null) {
                service.dispose(subscription);
            }
            service.dispose(sessionInfo);
        } catch (Exception e) {
            log.warn("Exception while disposing WorkspaceManager: " + e);
        } finally {
            updateSync.release();
        }
        ntRegistry.dispose();
    }
    //------------------------------------------------------< AccessManager >---
    /**
     * @see AccessManager#isGranted(NodeState, Path, String[])
     */
    public boolean isGranted(NodeState parentState, Path relPath, String[] actions)
            throws ItemNotFoundException, RepositoryException {
        if (parentState.getStatus() == Status.NEW) {
            return true;
        }
        // TODO: check again.
        // build itemId from the given state and the relative path without
        // making an attempt to retrieve the proper id of the item possibly
        // identified by the resulting id.
        // the server must be able to deal with paths and with proper ids anyway.
        // TODO: 'createNodeId' is basically wrong since isGranted is unspecific for any item.
        ItemId id = idFactory.createNodeId((NodeId) parentState.getWorkspaceId(), relPath);
        return service.isGranted(sessionInfo, id, actions);
    }

    /**
     * @see AccessManager#isGranted(ItemState, String[])
     */
    public boolean isGranted(ItemState itemState, String[] actions) throws ItemNotFoundException, RepositoryException {
        // a 'new' state can always be read, written and removed
        if (itemState.getStatus() == Status.NEW) {
            return true;
        }
        return service.isGranted(sessionInfo, itemState.getWorkspaceId(), actions);
    }

    /**
     * @see AccessManager#canRead(ItemState)
     */
    public boolean canRead(ItemState itemState) throws ItemNotFoundException, RepositoryException {
        // a 'new' state can always be read
        if (itemState.getStatus() == Status.NEW) {
            return true;
        }
        return service.isGranted(sessionInfo, itemState.getWorkspaceId(), AccessManager.READ);
    }

    /**
     * @see AccessManager#canRemove(ItemState)
     */
    public boolean canRemove(ItemState itemState) throws ItemNotFoundException, RepositoryException {
        // a 'new' state can always be removed again
        if (itemState.getStatus() == Status.NEW) {
            return true;
        }
        return service.isGranted(sessionInfo, itemState.getWorkspaceId(), AccessManager.REMOVE);
    }

    /**
     * @see AccessManager#canAccess(String)
     */
    public boolean canAccess(String workspaceName) throws NoSuchWorkspaceException, RepositoryException {
        String[] wspNames = getWorkspaceNames();
        for (int i = 0; i < wspNames.length; i++) {
            if (wspNames[i].equals(workspaceName)) {
                return true;
            }
        }
        return false;
    }

    //---------------------------------------------------< NamespaceStorage >---

    public Map getRegisteredNamespaces() throws RepositoryException {
        return service.getRegisteredNamespaces(sessionInfo);
    }

    /**
     * @inheritDoc
     */
    public String getPrefix(String uri) throws NamespaceException, RepositoryException {
        return service.getNamespacePrefix(sessionInfo, uri);
    }

    /**
     * @inheritDoc
     */
    public String getURI(String prefix) throws NamespaceException, RepositoryException {
        return service.getNamespaceURI(sessionInfo, prefix);
    }

    /**
     * @inheritDoc
     */
    public void registerNamespace(String prefix, String uri) throws NamespaceException, UnsupportedRepositoryOperationException, AccessDeniedException, RepositoryException {
        service.registerNamespace(sessionInfo, prefix, uri);
    }

    /**
     * @inheritDoc
     */
    public void unregisterNamespace(String uri) throws NamespaceException, UnsupportedRepositoryOperationException, AccessDeniedException, RepositoryException {
        service.unregisterNamespace(sessionInfo, uri);
    }

    //--------------------------------------------------------------------------
    /**
     * Called when local or external events occured. This method is called after
     * changes have been applied to the repository.
     *
     * @param eventBundles the event bundles generated by the repository service
     *                     as the effect of an local or external change.
     * @throws InterruptedException if this thread is interrupted while waiting
     *                              for the {@link #updateSync}.
     */
    private void onEventReceived(EventBundle[] eventBundles,
                                 InternalEventListener[] lstnrs)
            throws InterruptedException {
        if (log.isDebugEnabled()) {
            log.debug("received {} event bundles.", new Integer(eventBundles.length));
            for (int i = 0; i < eventBundles.length; i++) {
                log.debug("IsLocal:  {}", Boolean.valueOf(eventBundles[i].isLocal()));
                for (Iterator it = eventBundles[i].getEvents(); it.hasNext(); ) {
                    Event e = (Event) it.next();
                    String type;
                    switch (e.getType()) {
                        case Event.NODE_ADDED:
                            type = "NodeAdded";
                            break;
                        case Event.NODE_REMOVED:
                            type = "NodeRemoved";
                            break;
                        case Event.PROPERTY_ADDED:
                            type = "PropertyAdded";
                            break;
                        case Event.PROPERTY_CHANGED:
                            type = "PropertyChanged";
                            break;
                        case Event.PROPERTY_REMOVED:
                            type = "PropertyRemoved";
                            break;
                        default:
                            type = "Unknown";
                    }
                    log.debug("  {}; {}", e.getPath(), type);
                }
            }
        }

        // do not deliver events while an operation executes
        updateSync.acquire();
        try {
            // notify listener
            for (int i = 0; i < eventBundles.length; i++) {
                for (int j = 0; j < lstnrs.length; j++) {
                    lstnrs[j].onEvent(eventBundles[i]);
                }
            }
        } finally {
            updateSync.release();
        }
    }

    /**
     * Executes a sequence of operations on the repository service within
     * a given <code>SessionInfo</code>.
     */
    private final class OperationVisitorImpl implements OperationVisitor {

        /**
         * The session info for all operations in this batch.
         */
        private final SessionInfo sessionInfo;

        private Batch batch;

        private OperationVisitorImpl(SessionInfo sessionInfo) {
            this.sessionInfo = sessionInfo;
        }

        /**
         * Executes the operations on the repository service.
         */
        private void execute(ChangeLog changeLog) throws RepositoryException, ConstraintViolationException, AccessDeniedException, ItemExistsException, NoSuchNodeTypeException, UnsupportedRepositoryOperationException, VersionException {
            RepositoryException ex = null;
            try {
                ItemState target = changeLog.getTarget();
                batch = service.createBatch(sessionInfo, target.getId());
                Iterator it = changeLog.getOperations().iterator();
                while (it.hasNext()) {
                    Operation op = (Operation) it.next();
                    log.debug("executing " + op.getName());
                    op.accept(this);
                }
            } catch (RepositoryException e) {
                ex = e;
            } finally {
                if (batch != null) {
                    try {
                        // submit must be called even in case there is an
                        // exception to give the service a chance to clean
                        // up the batch
                        service.submit(batch);
                    } catch (RepositoryException e) {
                        if (ex == null) {
                            ex = e;
                        } else {
                            log.warn("Exception submitting batch", e);
                        }
                    }
                    // reset batch field
                    batch = null;
                }
            }
            if (ex != null) {
                throw ex;
            }
        }

        /**
         * Executes the operations on the repository service.
         */
        private void execute(Operation workspaceOperation) throws RepositoryException, ConstraintViolationException, AccessDeniedException, ItemExistsException, NoSuchNodeTypeException, UnsupportedRepositoryOperationException, VersionException {
            log.debug("executing " + workspaceOperation.getName());
            workspaceOperation.accept(this);
        }

        //-----------------------------------------------< OperationVisitor >---
        /**
         * @inheritDoc
         * @see OperationVisitor#visit(AddNode)
         */
        public void visit(AddNode operation) throws RepositoryException {
            NodeId parentId = operation.getParentId();
            batch.addNode(parentId, operation.getNodeName(), operation.getNodeTypeName(), operation.getUuid());
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(AddProperty)
         */
        public void visit(AddProperty operation) throws RepositoryException {
            NodeId parentId = operation.getParentId();
            Name propertyName = operation.getPropertyName();
            if (operation.isMultiValued()) {
                batch.addProperty(parentId, propertyName, operation.getValues());
            } else {
                QValue value = operation.getValues()[0];
                batch.addProperty(parentId, propertyName, value);
            }
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(Clone)
         */
        public void visit(Clone operation) throws NoSuchWorkspaceException, LockException, ConstraintViolationException, AccessDeniedException, ItemExistsException, UnsupportedRepositoryOperationException, VersionException, RepositoryException {
            NodeId nId = operation.getNodeId();
            NodeId destParentId = operation.getDestinationParentId();
            service.clone(sessionInfo, operation.getWorkspaceName(), nId, destParentId, operation.getDestinationName(), operation.isRemoveExisting());
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(Copy)
         */
        public void visit(Copy operation) throws NoSuchWorkspaceException, LockException, ConstraintViolationException, AccessDeniedException, ItemExistsException, UnsupportedRepositoryOperationException, VersionException, RepositoryException {
            NodeId nId = operation.getNodeId();
            NodeId destParentId = operation.getDestinationParentId();
            service.copy(sessionInfo, operation.getWorkspaceName(), nId, destParentId, operation.getDestinationName());
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(Move)
         */
        public void visit(Move operation) throws LockException, ConstraintViolationException, AccessDeniedException, ItemExistsException, UnsupportedRepositoryOperationException, VersionException, RepositoryException {
            NodeId moveId = operation.getSourceId();
            NodeId destParentId = operation.getDestinationParentId();

            if (batch == null) {
                service.move(sessionInfo, moveId, destParentId, operation.getDestinationName());
            } else {
                batch.move(moveId, destParentId, operation.getDestinationName());
            }
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(Update)
         */
        public void visit(Update operation) throws NoSuchWorkspaceException, AccessDeniedException, LockException, InvalidItemStateException, RepositoryException {
            NodeId nId = operation.getNodeId();
            service.update(sessionInfo, nId, operation.getSourceWorkspaceName());
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(Remove)
         */
        public void visit(Remove operation) throws RepositoryException {
            batch.remove(operation.getRemoveId());
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(SetMixin)
         */
        public void visit(SetMixin operation) throws RepositoryException {
            batch.setMixins(operation.getNodeId(), operation.getMixinNames());
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(SetPropertyValue)
         */
        public void visit(SetPropertyValue operation) throws RepositoryException {
            PropertyId id = operation.getPropertyId();
            if (operation.isMultiValued()) {
                batch.setValue(id, operation.getValues());
            } else {
                batch.setValue(id, operation.getValues()[0]);
            }
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(ReorderNodes)
         */
        public void visit(ReorderNodes operation) throws RepositoryException {
            NodeId parentId = operation.getParentId();
            NodeId insertId = operation.getInsertId();
            NodeId beforeId = operation.getBeforeId();
            batch.reorderNodes(parentId, insertId, beforeId);
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(Checkout)
         */
        public void visit(Checkout operation) throws UnsupportedRepositoryOperationException, LockException, RepositoryException {
            service.checkout(sessionInfo, operation.getNodeId());
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(Checkin)
         */
        public void visit(Checkin operation) throws UnsupportedRepositoryOperationException, LockException, InvalidItemStateException, RepositoryException {
            NodeId newId = service.checkin(sessionInfo, operation.getNodeId());
            operation.setNewVersionId(newId);
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(Restore)
         */
        public void visit(Restore operation) throws VersionException, PathNotFoundException, ItemExistsException, UnsupportedRepositoryOperationException, LockException, InvalidItemStateException, RepositoryException {
            NodeId nId = operation.getNodeId();
            if (nId == null) {
                service.restore(sessionInfo, operation.getVersionIds(), operation.removeExisting());
            } else {
                NodeId targetId;
                Path relPath = operation.getRelativePath();
                if (relPath != null) {
                    targetId = idFactory.createNodeId(nId, relPath);
                } else {
                    targetId = nId;
                }
                NodeId versionId = operation.getVersionIds()[0];
                service.restore(sessionInfo, targetId, versionId, operation.removeExisting());
            }
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(Merge)
         */
        public void visit(Merge operation) throws NoSuchWorkspaceException, AccessDeniedException, MergeException, LockException, InvalidItemStateException, RepositoryException {
            NodeId nId = operation.getNodeId();
            Iterator failed = service.merge(sessionInfo, nId, operation.getSourceWorkspaceName(), operation.bestEffort());
            operation.setFailedIds(failed);
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(ResolveMergeConflict)
         */
        public void visit(ResolveMergeConflict operation) throws VersionException, InvalidItemStateException, UnsupportedRepositoryOperationException, RepositoryException {
            NodeId nId = operation.getNodeId();
            NodeId[] mergedFailedIds = operation.getMergeFailedIds();
            NodeId[] predecessorIds = operation.getPredecessorIds();
            service.resolveMergeConflict(sessionInfo, nId, mergedFailedIds, predecessorIds);
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(LockOperation)
         */
        public void visit(LockOperation operation) throws AccessDeniedException, InvalidItemStateException, UnsupportedRepositoryOperationException, LockException, RepositoryException {
            LockInfo lInfo = service.lock(sessionInfo, operation.getNodeId(), operation.isDeep(), operation.isSessionScoped());
            operation.setLockInfo(lInfo);
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(LockRefresh)
         */
        public void visit(LockRefresh operation) throws AccessDeniedException, InvalidItemStateException, UnsupportedRepositoryOperationException, LockException, RepositoryException {
            service.refreshLock(sessionInfo, operation.getNodeId());
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(LockRelease)
         */
        public void visit(LockRelease operation) throws AccessDeniedException, InvalidItemStateException, UnsupportedRepositoryOperationException, LockException, RepositoryException {
            service.unlock(sessionInfo, operation.getNodeId());
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(AddLabel)
         */
        public void visit(AddLabel operation) throws VersionException, RepositoryException {
            NodeId vhId = operation.getVersionHistoryId();
            NodeId vId = operation.getVersionId();
            service.addVersionLabel(sessionInfo, vhId, vId, operation.getLabel(), operation.moveLabel());
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(RemoveLabel)
         */
        public void visit(RemoveLabel operation) throws VersionException, RepositoryException {
            NodeId vhId = operation.getVersionHistoryId();
            NodeId vId = operation.getVersionId();
            service.removeVersionLabel(sessionInfo, vhId, vId, operation.getLabel());
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(RemoveVersion)
         */
        public void visit(RemoveVersion operation) throws VersionException, AccessDeniedException, ReferentialIntegrityException, RepositoryException {
            NodeId versionId = (NodeId) operation.getRemoveId();
            NodeState vhState = operation.getParentState();
            service.removeVersion(sessionInfo, (NodeId) vhState.getWorkspaceId(), versionId);
        }

        /**
         * @inheritDoc
         * @see OperationVisitor#visit(WorkspaceImport)
         */
        public void visit(WorkspaceImport operation) throws RepositoryException {
            service.importXml(sessionInfo, operation.getNodeId(), operation.getXmlStream(), operation.getUuidBehaviour());
        }
    }

    //------------------------------------------------------< ChangePolling >---
    /**
     * Implements the polling for changes on the repository service.
     */
    private final class ChangePolling implements Runnable {

        /**
         * The polling timeout in milliseconds.
         */
        private final int pollTimeout;

        /**
         * Creates a new change polling with a given polling timeout.
         *
         * @param pollTimeout the timeout in milliseconds.
         */
        private ChangePolling(int pollTimeout) {
            this.pollTimeout = pollTimeout;
        }

        public void run() {
            while (!Thread.interrupted()) {
                try {
                    InternalEventListener[] iel;
                    Subscription subscr;
                    synchronized (listeners) {
                        while (listeners.isEmpty()) {
                            listeners.wait();
                        }
                        iel = (InternalEventListener[]) listeners.toArray(new InternalEventListener[0]);
                        subscr = subscription;
                    }

                    log.debug("calling getEvents() (Workspace={})",
                            sessionInfo.getWorkspaceName());
                    EventBundle[] bundles = service.getEvents(subscr, pollTimeout);
                    log.debug("returned from getEvents() (Workspace={})",
                            sessionInfo.getWorkspaceName());
                    // check if thread had been interrupted while
                    // getting events
                    if (Thread.interrupted()) {
                        log.debug("Thread interrupted, terminating...");
                        break;
                    }
                    if (bundles.length > 0) {
                        onEventReceived(bundles, iel);
                    }
                } catch (UnsupportedRepositoryOperationException e) {
                    log.error("SPI implementation does not support observation: " + e);
                    // terminate
                    break;
                } catch (RepositoryException e) {
                    log.info("Workspace=" + sessionInfo.getWorkspaceName() +
                            ": Exception while retrieving event bundles: " + e);
                    log.debug("Dump:", e);
                } catch (InterruptedException e) {
                    // terminate
                    break;
                } catch (Exception e) {
                    log.warn("Exception in event polling thread: " + e);
                    log.debug("Dump:", e);
                }
            }
        }
    }
}
