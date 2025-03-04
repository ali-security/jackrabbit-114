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

import org.apache.commons.collections.map.ReferenceMap;
import org.apache.jackrabbit.commons.AbstractSession;
import org.apache.jackrabbit.jcr2spi.config.CacheBehaviour;
import org.apache.jackrabbit.jcr2spi.config.RepositoryConfig;
import org.apache.jackrabbit.jcr2spi.hierarchy.HierarchyEntry;
import org.apache.jackrabbit.jcr2spi.hierarchy.HierarchyManager;
import org.apache.jackrabbit.jcr2spi.hierarchy.NodeEntry;
import org.apache.jackrabbit.jcr2spi.lock.LockManager;
import org.apache.jackrabbit.jcr2spi.nodetype.EffectiveNodeTypeProvider;
import org.apache.jackrabbit.jcr2spi.nodetype.ItemDefinitionProvider;
import org.apache.jackrabbit.jcr2spi.nodetype.NodeTypeManagerImpl;
import org.apache.jackrabbit.jcr2spi.operation.Move;
import org.apache.jackrabbit.jcr2spi.operation.Operation;
import org.apache.jackrabbit.jcr2spi.security.AccessManager;
import org.apache.jackrabbit.jcr2spi.state.ItemStateFactory;
import org.apache.jackrabbit.jcr2spi.state.ItemStateValidator;
import org.apache.jackrabbit.jcr2spi.state.NodeState;
import org.apache.jackrabbit.jcr2spi.state.PropertyState;
import org.apache.jackrabbit.jcr2spi.state.SessionItemStateManager;
import org.apache.jackrabbit.jcr2spi.state.UpdatableItemStateManager;
import org.apache.jackrabbit.jcr2spi.version.VersionManager;
import org.apache.jackrabbit.jcr2spi.xml.ImportHandler;
import org.apache.jackrabbit.jcr2spi.xml.Importer;
import org.apache.jackrabbit.jcr2spi.xml.SessionImporter;
import org.apache.jackrabbit.spi.IdFactory;
import org.apache.jackrabbit.spi.NameFactory;
import org.apache.jackrabbit.spi.NodeId;
import org.apache.jackrabbit.spi.Path;
import org.apache.jackrabbit.spi.PathFactory;
import org.apache.jackrabbit.spi.QValueFactory;
import org.apache.jackrabbit.spi.SessionInfo;
import org.apache.jackrabbit.spi.XASessionInfo;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NameException;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.spi.commons.conversion.PathResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.spi.commons.value.ValueFactoryQImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.jcr.AccessDeniedException;
import javax.jcr.Credentials;
import javax.jcr.InvalidItemStateException;
import javax.jcr.InvalidSerializedDataException;
import javax.jcr.Item;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.LoginException;
import javax.jcr.NamespaceException;
import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFactory;
import javax.jcr.Workspace;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.Version;
import javax.jcr.version.VersionException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.AccessControlException;
import java.util.Map;

/**
 * <code>SessionImpl</code>...
 */
public class SessionImpl extends AbstractSession
        implements NamespaceResolver, ManagerProvider {

    private static Logger log = LoggerFactory.getLogger(SessionImpl.class);

    private boolean alive;

    /**
     * Listeners (weak references)
     */
    private final Map listeners = new ReferenceMap(ReferenceMap.WEAK, ReferenceMap.WEAK);

    private final Repository repository;
    private final RepositoryConfig config;
    private final WorkspaceImpl workspace;

    private final SessionInfo sessionInfo;

    private NamePathResolver npResolver;
    private final NodeTypeManagerImpl ntManager;

    private final ValueFactory valueFactory;

    private final SessionItemStateManager itemStateManager;
    private final ItemManager itemManager;
    private final ItemStateValidator validator;

    SessionImpl(SessionInfo sessionInfo, Repository repository, RepositoryConfig config)
        throws RepositoryException {

        alive = true;
        this.repository = repository;
        this.config = config;
        this.sessionInfo = sessionInfo;

        workspace = createWorkspaceInstance(config, sessionInfo);

        // build local name-mapping
        npResolver = new DefaultNamePathResolver(this, true);

        // build ValueFactory
        valueFactory = new ValueFactoryQImpl(config.getRepositoryService().getQValueFactory(), npResolver);

        // build nodetype manager
        ntManager = new NodeTypeManagerImpl(workspace.getNodeTypeRegistry(), this, getJcrValueFactory());
        validator = new ItemStateValidator(this, getPathFactory());

        itemStateManager = createSessionItemStateManager(workspace.getUpdatableItemStateManager(), workspace.getItemStateFactory());
        itemManager = createItemManager(getHierarchyManager());
    }

    //--------------------------------------------------< Session interface >---
    /**
     * @see javax.jcr.Session#getRepository()
     */
    public Repository getRepository() {
        return repository;
    }

    /**
     * @see javax.jcr.Session#getUserID()
     */
    public String getUserID() {
        return sessionInfo.getUserID();
    }

    /**
     * Always returns <code>null</code>.
     *
     * @see javax.jcr.Session#getAttribute(String)
     */
    public Object getAttribute(String name) {
        return null;
    }

    /**
     * Always returns an empty String array.
     *
     * @see javax.jcr.Session#getAttributeNames()
     */
    public String[] getAttributeNames() {
        return new String[0];

    }

    /**
     * @see javax.jcr.Session#getWorkspace()
     */
    public Workspace getWorkspace() {
        return workspace;
    }

    /**
     * @see javax.jcr.Session#impersonate(Credentials)
     */
    public Session impersonate(Credentials credentials) throws LoginException, RepositoryException {
        checkIsAlive();
        SessionInfo info = config.getRepositoryService().impersonate(sessionInfo, credentials);
        try {
            if (info instanceof XASessionInfo) {
                return new XASessionImpl((XASessionInfo) info, repository, config);
            } else {
                return new SessionImpl(info, repository, config);
            }
        } catch (RepositoryException ex) {
            config.getRepositoryService().dispose(info);
            throw ex;
        }
    }

    /**
     * @see javax.jcr.Session#getRootNode()
     */
    public Node getRootNode() throws RepositoryException {
        checkIsAlive();

        NodeEntry re = getHierarchyManager().getRootEntry();
        return (Node) itemManager.getItem(re);
    }

    /**
     * @see javax.jcr.Session#getNodeByUUID(String)
     */
    public Node getNodeByUUID(String uuid) throws ItemNotFoundException, RepositoryException {
        // sanity check performed by getNodeById
        Node node = getNodeById(getIdFactory().createNodeId(uuid));
        if (node instanceof NodeImpl && ((NodeImpl)node).isNodeType(NameConstants.MIX_REFERENCEABLE)) {
            return node;
        } else {
            // fall back
            String mixReferenceable = getNameResolver().getJCRName(NameConstants.MIX_REFERENCEABLE);
            if (node.isNodeType(mixReferenceable)) {
                return node;
            }
            // there is a node with that uuid but the node does not expose it
            throw new ItemNotFoundException(uuid);
        }
    }

    /**
     * Retrieve the <code>Node</code> with the given id.
     *
     * @param id
     * @return node with the given <code>NodeId</code>.
     * @throws ItemNotFoundException if no such node exists or if this
     * <code>Session</code> does not have permission to access the node.
     * @throws RepositoryException
     */
    private Node getNodeById(NodeId id) throws ItemNotFoundException, RepositoryException {
        // check sanity of this session
        checkIsAlive();
        try {
            NodeEntry nodeEntry = getHierarchyManager().getNodeEntry(id);
            Item item = getItemManager().getItem(nodeEntry);
            if (item.isNode()) {
                return (Node) item;
            } else {
                log.error("NodeId '" + id + " does not point to a Node");
                throw new ItemNotFoundException(id.toString());
            }
        } catch (AccessDeniedException e) {
            throw new ItemNotFoundException(id.toString());
        }
    }

    /**
     * @see javax.jcr.Session#getItem(String)
     */
    public Item getItem(String absPath) throws PathNotFoundException, RepositoryException {
        checkIsAlive();
        try {
            Path qPath = getQPath(absPath).getNormalizedPath();
            ItemManager itemMgr = getItemManager();
            if (itemMgr.nodeExists(qPath)) {
                return itemMgr.getNode(qPath);
            } else {
                return itemMgr.getProperty(qPath);
            }
        } catch (AccessDeniedException ade) {
            throw new PathNotFoundException(absPath);
        }
    }

    /**
     * @see javax.jcr.Session#itemExists(String)
     */
    public boolean itemExists(String absPath) throws RepositoryException {
        checkIsAlive();
        Path qPath = getQPath(absPath).getNormalizedPath();
        ItemManager itemMgr = getItemManager();
        return (itemMgr.nodeExists(qPath)) ? true : itemMgr.propertyExists(qPath);
    }

    /**
     * @see javax.jcr.Session#move(String, String)
     */
    public void move(String srcAbsPath, String destAbsPath) throws ItemExistsException, PathNotFoundException, VersionException, ConstraintViolationException, LockException, RepositoryException {
        checkSupportedOption(Repository.LEVEL_2_SUPPORTED);
        checkIsAlive();

        // retrieve qualified paths
        Path srcPath = getQPath(srcAbsPath);
        Path destPath = getQPath(destAbsPath);

        // all validation is performed by Move Operation and state-manager
        Operation op = Move.create(srcPath, destPath, getHierarchyManager(), getPathResolver(), true);
        itemStateManager.execute(op);
    }

    /**
     * @see javax.jcr.Session#save()
     */
    public void save() throws AccessDeniedException, ConstraintViolationException, InvalidItemStateException, VersionException, LockException, RepositoryException {
        checkSupportedOption(Repository.LEVEL_2_SUPPORTED);
        // delegate to the root node (including check for isAlive)
        getRootNode().save();
    }

    /**
     * @see javax.jcr.Session#refresh(boolean)
     */
    public void refresh(boolean keepChanges) throws RepositoryException {
        // delegate to the root node (including check for isAlive)
        getRootNode().refresh(keepChanges);
    }

    /**
     * @see javax.jcr.Session#hasPendingChanges()
     */
    public boolean hasPendingChanges() throws RepositoryException {
        checkIsAlive();
        return itemStateManager.hasPendingChanges();
    }

    /**
     * @see javax.jcr.Session#getValueFactory()
     */
    public ValueFactory getValueFactory() throws UnsupportedRepositoryOperationException, RepositoryException {
        // must throw UnsupportedRepositoryOperationException if writing is
        // not supported
        checkSupportedOption(Repository.LEVEL_2_SUPPORTED);
        return getJcrValueFactory();
    }

    /**
     * @see javax.jcr.Session#checkPermission(String, String)
     */
    public void checkPermission(String absPath, String actions) throws AccessControlException, RepositoryException {
        checkIsAlive();
        // build the array of actions to be checked
        String[] actionsArr = actions.split(",");

        Path targetPath = getQPath(absPath);

        boolean isGranted;
        // The given abs-path may point to a non-existing item
        if (itemManager.nodeExists(targetPath)) {
            NodeState nState = getHierarchyManager().getNodeState(targetPath);
            isGranted = getAccessManager().isGranted(nState, actionsArr);
        } else if (itemManager.propertyExists(targetPath)) {
            PropertyState pState = getHierarchyManager().getPropertyState(targetPath);
            isGranted = getAccessManager().isGranted(pState, actionsArr);
        } else {
            NodeState parentState = null;
            Path parentPath = targetPath;
            while (parentState == null) {
                parentPath = parentPath.getAncestor(1);
                if (itemManager.nodeExists(parentPath)) {
                    parentState = getHierarchyManager().getNodeState(parentPath);
                }
            }
            // parentState is the nearest existing nodeState or the root state.
            Path relPath = parentPath.computeRelativePath(targetPath);
            isGranted = getAccessManager().isGranted(parentState, relPath, actionsArr);
        }

        if (!isGranted) {
            throw new AccessControlException("Access control violation: path = " + absPath + ", actions = " + actions);
        }
    }

    /**
     * @see Session#getImportContentHandler(String, int)
     */
    public ContentHandler getImportContentHandler(String parentAbsPath, int uuidBehavior) throws PathNotFoundException, ConstraintViolationException, VersionException, LockException, RepositoryException {
        checkSupportedOption(Repository.LEVEL_2_SUPPORTED);
        checkIsAlive();

        Path parentPath = getQPath(parentAbsPath);
        // NOTE: check if path corresponds to Node and is writable is performed
        // within the SessionImporter.
        Importer importer = new SessionImporter(parentPath, this, itemStateManager, uuidBehavior);
        return new ImportHandler(importer, getNamespaceResolver(), workspace.getNamespaceRegistry(), getNameFactory(), getPathFactory());
    }

    /**
     * @see javax.jcr.Session#importXML(String, java.io.InputStream, int)
     */
    public void importXML(String parentAbsPath, InputStream in, int uuidBehavior) throws IOException, PathNotFoundException, ItemExistsException, ConstraintViolationException, VersionException, InvalidSerializedDataException, LockException, RepositoryException {
        // NOTE: checks are performed by 'getImportContentHandler'
        ImportHandler handler = (ImportHandler) getImportContentHandler(parentAbsPath, uuidBehavior);
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(
                    "http://xml.org/sax/features/namespace-prefixes", false);

            SAXParser parser = factory.newSAXParser();
            parser.parse(new InputSource(in), handler);
        } catch (SAXException se) {
            // check for wrapped repository exception
            Exception e = se.getException();
            if (e != null && e instanceof RepositoryException) {
                throw (RepositoryException) e;
            } else {
                String msg = "failed to parse XML stream";
                log.debug(msg);
                throw new InvalidSerializedDataException(msg, se);
            }
        } catch (ParserConfigurationException e) {
            throw new RepositoryException("SAX parser configuration error", e);
        }
    }

    /**
     * @see javax.jcr.Session#setNamespacePrefix(String, String)
     */
    public void setNamespacePrefix(String prefix, String uri)
            throws RepositoryException {
        super.setNamespacePrefix(prefix, uri);
        // Reset name and path caches
        npResolver = new DefaultNamePathResolver(this, true);
    }

    /**
     * @see javax.jcr.Session#logout()
     */
    public void logout() {
        if (!alive) {
            // ignore
            return;
        }

        // notify listeners that session is about to be closed
        notifyLoggingOut();

        // dispose session item state manager
        itemStateManager.dispose();
        // dispose item manager
        itemManager.dispose();
        // dispose workspace
        workspace.dispose();

        // invalidate session
        alive = false;
        // finally notify listeners that session has been closed
        notifyLoggedOut();
    }

    /**
     * @see javax.jcr.Session#isLive()
     */
    public boolean isLive() {
        return alive;
    }

    /**
     * @see javax.jcr.Session#addLockToken(String)
     */
    public void addLockToken(String lt) {
        try {
            getLockManager().addLockToken(lt);
        } catch (RepositoryException e) {
            log.warn("Unable to add lock token '" +lt+ "' to this session.", e);
        }
    }

    /**
     * @see javax.jcr.Session#getLockTokens()
     */
    public String[] getLockTokens() {
        return getLockManager().getLockTokens();
    }

    /**
     * @see javax.jcr.Session#removeLockToken(String)
     */
    public void removeLockToken(String lt) {
        try {
            getLockManager().removeLockToken(lt);
        } catch (RepositoryException e) {
            log.warn("Unable to remove lock token '" +lt+ "' from this session. (" + e.getMessage() + ")");
        }
    }

    //-------------------------------------------------< NamespaceResolver >--

    public String getPrefix(String uri) throws NamespaceException {
        try {
            return getNamespacePrefix(uri);
        } catch (NamespaceException e) {
            throw e;
        } catch (RepositoryException e) {
            throw new NamespaceException("Namespace not found: " + uri, e);
        }
    }

    public String getURI(String prefix) throws NamespaceException {
        try {
            return getNamespaceURI(prefix);
        } catch (NamespaceException e) {
            throw e;
        } catch (RepositoryException e) {
            throw new NamespaceException("Namespace not found: " + prefix, e);
        }
    }

    //--------------------------------------< register and inform listeners >---
    /**
     * Add a <code>SessionListener</code>
     *
     * @param listener the new listener to be informed on modifications
     */
    public void addListener(SessionListener listener) {
        if (!listeners.containsKey(listener)) {
            listeners.put(listener, listener);
        }
    }

    /**
     * Remove a <code>SessionListener</code>
     *
     * @param listener an existing listener
     */
    public void removeListener(SessionListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notify the listeners that this session is about to be closed.
     */
    private void notifyLoggingOut() {
        // copy listeners to array to avoid ConcurrentModificationException
        SessionListener[] la = (SessionListener[])listeners.values().toArray(new SessionListener[listeners.size()]);
        for (int i = 0; i < la.length; i++) {
            if (la[i] != null) {
                la[i].loggingOut(this);
            }
        }
    }

    /**
     * Notify the listeners that this session has been closed.
     */
    private void notifyLoggedOut() {
        // copy listeners to array to avoid ConcurrentModificationException
        SessionListener[] la = (SessionListener[])listeners.values().toArray(new SessionListener[listeners.size()]);
        for (int i = 0; i < la.length; i++) {
            if (la[i] != null) {
                la[i].loggedOut(this);
            }
        }
    }

    //-------------------------------------------------------< init methods >---
    protected WorkspaceImpl createWorkspaceInstance(RepositoryConfig config, SessionInfo sessionInfo) throws RepositoryException {
        return new WorkspaceImpl(sessionInfo.getWorkspaceName(), this, config, sessionInfo);
    }

    protected SessionItemStateManager createSessionItemStateManager(UpdatableItemStateManager workspaceStateManager, ItemStateFactory isf) throws RepositoryException {
        return new SessionItemStateManager(workspaceStateManager, getValidator(), getQValueFactory(), isf, this);
    }

    protected ItemManager createItemManager(HierarchyManager hierarchyManager) {
        ItemCache cache = new ItemCacheImpl(config.getItemCacheSize());
        ItemManagerImpl imgr = new ItemManagerImpl(hierarchyManager, this, cache);
        return imgr;
    }

    //---------------------------------------------------< ManagerProvider > ---

    public NamePathResolver getNamePathResolver() {
        return npResolver;
    }

    /**
     * @see ManagerProvider#getNameResolver()
     */
    public NameResolver getNameResolver() {
        return npResolver;
    }

    /**
     * @see ManagerProvider#getPathResolver()
     */
    public PathResolver getPathResolver() {
        return npResolver;
    }

    /**
     * @see ManagerProvider#getNamespaceResolver()
     */
    public NamespaceResolver getNamespaceResolver() {
        return this;
    }

    /**
     * @see ManagerProvider#getHierarchyManager()
     */
    public HierarchyManager getHierarchyManager() {
        return workspace.getHierarchyManager();
    }

    /**
     * @see ManagerProvider#getLockManager()
     */
    public LockManager getLockManager() {
        return workspace.getLockManager();
    }

    /**
     * @see ManagerProvider#getAccessManager()
     */
    public AccessManager getAccessManager() {
        return workspace.getAccessManager();
    }

    /**
     * @see ManagerProvider#getVersionManager()
     */
    public VersionManager getVersionManager() {
        return workspace.getVersionManager();
    }

    /**
     * @see ManagerProvider#getItemDefinitionProvider()
     */
    public ItemDefinitionProvider getItemDefinitionProvider() {
        return workspace.getItemDefinitionProvider();
    }

    /**
     * @see ManagerProvider#getEffectiveNodeTypeProvider()
     */
    public EffectiveNodeTypeProvider getEffectiveNodeTypeProvider() {
        return workspace.getEffectiveNodeTypeProvider();
    }

    /**
     * @see ManagerProvider#getQValueFactory()
     */
    public QValueFactory getQValueFactory() throws RepositoryException {
        return config.getRepositoryService().getQValueFactory();
    }

    /**
     * @see ManagerProvider#getJcrValueFactory()
     */
    public ValueFactory getJcrValueFactory() throws RepositoryException {
        return valueFactory;
    }

    //--------------------------------------------------------------------------

    ItemManager getItemManager() {
        return itemManager;
    }

    // TODO public for SessionImport only. review
    public ItemStateValidator getValidator() {
        return validator;
    }

    // TODO public for SessionImport only. review
    public IdFactory getIdFactory() throws RepositoryException {
        return workspace.getIdFactory();
    }

    public NameFactory getNameFactory() throws RepositoryException {
        return workspace.getNameFactory();
    }

    PathFactory getPathFactory() throws RepositoryException {
        return workspace.getPathFactory();
    }

    /**
     * Returns the <code>ItemStateManager</code> associated with this session.
     *
     * @return the <code>ItemStateManager</code> associated with this session
     */
    SessionItemStateManager getSessionItemStateManager() {
        return itemStateManager;
    }

    NodeTypeManagerImpl getNodeTypeManager() {
        return ntManager;
    }

    CacheBehaviour getCacheBehaviour() {
        return config.getCacheBehaviour();
    }

    //--------------------------------------------------------------------------
    SessionImpl switchWorkspace(String workspaceName) throws AccessDeniedException,
        NoSuchWorkspaceException, RepositoryException {
        checkAccessibleWorkspace(workspaceName);

        SessionInfo info = config.getRepositoryService().obtain(sessionInfo, workspaceName);
        if (info instanceof XASessionInfo) {
            return new XASessionImpl((XASessionInfo) info, repository, config);
        } else {
            return new SessionImpl(info, repository, config);
        }
    }

    /**
     * Builds an qualified path from the given absolute path.
     *
     * @param absPath
     * @return
     * @throws RepositoryException if the resulting qualified path is not absolute
     * or if the given path cannot be resolved to a qualified path.
     */
    Path getQPath(String absPath) throws RepositoryException {
        try {
            Path p = getPathResolver().getQPath(absPath);
            if (!p.isAbsolute()) {
                throw new RepositoryException("Not an absolute path: " + absPath);
            }
            return p;
        } catch (NameException mpe) {
            String msg = "Invalid path: " + absPath;
            log.debug(msg);
            throw new RepositoryException(msg, mpe);
        }
    }

    /**
     * Returns the NodeState of the given Node and asserts that the state is
     * listed in the hierarchy built by this Session. If the version
     * was obtained from a different session, the 'corresponding' version
     * state for this session is retrieved.
     *
     * @param version
     * @return the NodeState associated with the specified version.
     */
    NodeState getVersionState(Version version) throws RepositoryException {
        NodeState nodeState;
        if (version.getSession() == this) {
            nodeState = (NodeState) ((NodeImpl) version).getItemState();
        } else {
            Path p = getQPath(version.getPath());
            Path parentPath = p.getAncestor(1);
            HierarchyEntry parentEntry = getHierarchyManager().lookup(parentPath);
            if (parentEntry != null) {
                // make sure the parent entry is up to date
                parentEntry.invalidate(false);
            }
            nodeState = getHierarchyManager().getNodeState(p);
        }
        return nodeState;
    }
    //------------------------------------------------------< check methods >---
    /**
     * Performs a sanity check on this session.
     *
     * @throws RepositoryException if this session has been rendered invalid
     * for some reason (e.g. if this session has been closed explicitly by logout)
     */
    void checkIsAlive() throws RepositoryException {
        // check session status
        if (!alive) {
            throw new RepositoryException("This session has been closed.");
        }
    }

    /**
     * Returns true if the repository supports the given option. False otherwise.
     *
     * @param option Any of the option constants defined by {@link Repository}
     * that either returns 'true' or 'false'. I.e.
     * <ul>
     * <li>{@link Repository#LEVEL_1_SUPPORTED}</li>
     * <li>{@link Repository#LEVEL_2_SUPPORTED}</li>
     * <li>{@link Repository#OPTION_TRANSACTIONS_SUPPORTED}</li>
     * <li>{@link Repository#OPTION_VERSIONING_SUPPORTED}</li>
     * <li>{@link Repository#OPTION_OBSERVATION_SUPPORTED}</li>
     * <li>{@link Repository#OPTION_LOCKING_SUPPORTED}</li>
     * <li>{@link Repository#OPTION_QUERY_SQL_SUPPORTED}</li>
     * </ul>
     * @return true if the repository supports the given option. False otherwise.
     */
    boolean isSupportedOption(String option) {
        String desc = repository.getDescriptor(option);
        return Boolean.valueOf(desc).booleanValue();
    }

    /**
     * Make sure the repository supports the option indicated by the given string.
     *
     * @param option Any of the option constants defined by {@link Repository}
     * that either returns 'true' or 'false'. I.e.
     * <ul>
     * <li>{@link Repository#LEVEL_1_SUPPORTED}</li>
     * <li>{@link Repository#LEVEL_2_SUPPORTED}</li>
     * <li>{@link Repository#OPTION_TRANSACTIONS_SUPPORTED}</li>
     * <li>{@link Repository#OPTION_VERSIONING_SUPPORTED}</li>
     * <li>{@link Repository#OPTION_OBSERVATION_SUPPORTED}</li>
     * <li>{@link Repository#OPTION_LOCKING_SUPPORTED}</li>
     * <li>{@link Repository#OPTION_QUERY_SQL_SUPPORTED}</li>
     * </ul>
     * @throws UnsupportedRepositoryOperationException
     * @throws RepositoryException
     * @see javax.jcr.Repository#getDescriptorKeys()
     */
    void checkSupportedOption(String option) throws UnsupportedRepositoryOperationException, RepositoryException {
        if (!isSupportedOption(option)) {
            throw new UnsupportedRepositoryOperationException(option + " is not supported by this repository.");
        }
    }

    /**
     * Checks if this nodes session has pending changes.
     *
     * @throws InvalidItemStateException if this nodes session has pending changes
     * @throws RepositoryException
     */
    void checkHasPendingChanges() throws RepositoryException {
        // check for pending changes
        if (hasPendingChanges()) {
            String msg = "Unable to perform operation. Session has pending changes.";
            log.debug(msg);
            throw new InvalidItemStateException(msg);
        }
    }

    /**
     * Check if the the workspace with the given name exists and is accessible
     * for this <code>Session</code>.
     *
     * @param workspaceName
     * @throws NoSuchWorkspaceException
     * @throws RepositoryException
     */
    void checkAccessibleWorkspace(String workspaceName) throws NoSuchWorkspaceException, RepositoryException {
        String[] wsps = workspace.getAccessibleWorkspaceNames();
        boolean accessible = false;
        for (int i = 0; i < wsps.length && !accessible; i++) {
            accessible = wsps[i].equals(workspaceName);
        }

        if (!accessible) {
            throw new NoSuchWorkspaceException("Unknown workspace: '" + workspaceName + "'.");
        }
    }
}
