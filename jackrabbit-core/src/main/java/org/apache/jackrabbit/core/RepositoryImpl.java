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

import EDU.oswego.cs.dl.util.concurrent.Mutex;
import EDU.oswego.cs.dl.util.concurrent.ReadWriteLock;
import EDU.oswego.cs.dl.util.concurrent.ReentrantWriterPreferenceReadWriteLock;
import EDU.oswego.cs.dl.util.concurrent.WriterPreferenceReadWriteLock;
import org.apache.commons.collections.map.ReferenceMap;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.api.JackrabbitRepository;
import org.apache.jackrabbit.commons.AbstractRepository;
import org.apache.jackrabbit.core.cluster.ClusterContext;
import org.apache.jackrabbit.core.cluster.ClusterException;
import org.apache.jackrabbit.core.cluster.ClusterNode;
import org.apache.jackrabbit.core.cluster.WorkspaceEventChannel;
import org.apache.jackrabbit.core.cluster.WorkspaceListener;
import org.apache.jackrabbit.core.cluster.LockEventChannel;
import org.apache.jackrabbit.core.cluster.UpdateEventChannel;
import org.apache.jackrabbit.core.cluster.UpdateEventListener;
import org.apache.jackrabbit.core.config.ClusterConfig;
import org.apache.jackrabbit.core.config.PersistenceManagerConfig;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.jackrabbit.core.config.SecurityManagerConfig;
import org.apache.jackrabbit.core.config.VersioningConfig;
import org.apache.jackrabbit.core.config.WorkspaceConfig;
import org.apache.jackrabbit.core.data.DataStore;
import org.apache.jackrabbit.core.fs.BasedFileSystem;
import org.apache.jackrabbit.core.fs.FileSystem;
import org.apache.jackrabbit.core.fs.FileSystemException;
import org.apache.jackrabbit.core.fs.FileSystemResource;
import org.apache.jackrabbit.core.lock.LockManager;
import org.apache.jackrabbit.core.lock.LockManagerImpl;
import org.apache.jackrabbit.core.nodetype.NodeTypeRegistry;
import org.apache.jackrabbit.core.nodetype.virtual.VirtualNodeTypeStateManager;
import org.apache.jackrabbit.core.observation.DelegatingObservationDispatcher;
import org.apache.jackrabbit.core.observation.EventStateCollection;
import org.apache.jackrabbit.core.observation.ObservationDispatcher;
import org.apache.jackrabbit.core.persistence.PMContext;
import org.apache.jackrabbit.core.persistence.PersistenceManager;
import org.apache.jackrabbit.core.security.JackrabbitSecurityManager;
import org.apache.jackrabbit.core.security.authentication.AuthContext;
import org.apache.jackrabbit.core.security.simple.SimpleSecurityManager;
import org.apache.jackrabbit.core.state.CacheManager;
import org.apache.jackrabbit.core.state.ChangeLog;
import org.apache.jackrabbit.core.state.ISMLocking;
import org.apache.jackrabbit.core.state.ItemStateCacheFactory;
import org.apache.jackrabbit.core.state.ItemStateException;
import org.apache.jackrabbit.core.state.ManagedMLRUItemStateCacheFactory;
import org.apache.jackrabbit.core.state.SharedItemStateManager;
import org.apache.jackrabbit.core.util.RepositoryLock;
import org.apache.jackrabbit.core.value.InternalValue;
import org.apache.jackrabbit.core.version.VersionManager;
import org.apache.jackrabbit.core.version.VersionManagerImpl;
import org.apache.jackrabbit.core.xml.ClonedInputSource;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.spi.commons.namespace.RegistryNamespaceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import javax.jcr.AccessDeniedException;
import javax.jcr.Credentials;
import javax.jcr.LoginException;
import javax.jcr.NamespaceRegistry;
import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.ObservationManager;
import javax.security.auth.Subject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * A <code>RepositoryImpl</code> ...
 */
public class RepositoryImpl extends AbstractRepository
        implements JackrabbitRepository, SessionListener, EventListener, WorkspaceListener {

    private static Logger log = LoggerFactory.getLogger(RepositoryImpl.class);

    /**
     * hardcoded id of the repository root node
     */
    public static final NodeId ROOT_NODE_ID = NodeId.valueOf("cafebabe-cafe-babe-cafe-babecafebabe");

    /**
     * hardcoded id of the "/jcr:system" node
     */
    public static final NodeId SYSTEM_ROOT_NODE_ID = NodeId.valueOf("deadbeef-cafe-babe-cafe-babecafebabe");

    /**
     * hardcoded id of the "/jcr:system/jcr:versionStorage" node
     */
    public static final NodeId VERSION_STORAGE_NODE_ID = NodeId.valueOf("deadbeef-face-babe-cafe-babecafebabe");

    /**
     * hardcoded id of the "/jcr:system/jcr:nodeTypes" node
     */
    public static final NodeId NODETYPES_NODE_ID = NodeId.valueOf("deadbeef-cafe-cafe-cafe-babecafebabe");

    /**
     * the name of the file system resource containing the properties of the
     * repository.
     */
    private static final String PROPERTIES_RESOURCE = "rep.properties";

    /**
     * the repository properties.
     */
    private final Properties repProps;

    // names of well-known repository properties
    public static final String STATS_NODE_COUNT_PROPERTY = "jcr.repository.stats.nodes.count";
    public static final String STATS_PROP_COUNT_PROPERTY = "jcr.repository.stats.properties.count";

    private NodeId rootNodeId;

    private final NamespaceRegistryImpl nsReg;
    private final NodeTypeRegistry ntReg;
    private final VersionManagerImpl vMgr;
    private final VirtualNodeTypeStateManager virtNTMgr;

    /**
     * Security manager
     */
    private JackrabbitSecurityManager securityMgr;

    /**
     * Search manager for the jcr:system tree. May be <code>null</code> if
     * none is configured.
     */
    private SearchManager systemSearchMgr;

    // configuration of the repository
    protected final RepositoryConfig repConfig;

    // the virtual repository file system
    private final FileSystem repStore;

    // sub file system where the repository stores meta data such as uuid of root node, etc.
    private final FileSystem metaDataStore;

    /**
     * Data store for binary properties.
     */
    private final DataStore dataStore;

    /**
     * the delegating observation dispatcher for all workspaces
     */
    private final DelegatingObservationDispatcher delegatingDispatcher =
            new DelegatingObservationDispatcher();

    /**
     * map of workspace names and <code>WorkspaceInfo<code>s.
     */
    private final HashMap wspInfos = new HashMap();

    /**
     * active sessions (weak references)
     */
    private final ReferenceMap activeSessions =
            new ReferenceMap(ReferenceMap.WEAK, ReferenceMap.WEAK);

    // misc. statistics
    private long nodesCount = 0;
    private long propsCount = 0;

    // flag indicating if respository has been shut down
    private boolean disposed = false;

    /**
     * the lock that guards instantiation of multiple repositories.
     */
    private RepositoryLock repLock;

    /**
     * Clustered node used, <code>null</code> if clustering is not configured.
     */
    private ClusterNode clusterNode;

    /**
     * Shutdown lock for guaranteeing that no new sessions are started during
     * repository shutdown and that a repository shutdown is not initiated
     * during a login. Each session login acquires a read lock while the
     * repository shutdown requires a write lock. This guarantees that there
     * can be multiple concurrent logins when the repository is not shutting
     * down, but that only a single shutdown and no concurrent logins can
     * happen simultaneously.
     */
    private final ReadWriteLock shutdownLock = new WriterPreferenceReadWriteLock();

    /**
     * There is one cache manager per repository that manages the sizes of the caches used.
     */
    private final CacheManager cacheMgr = new CacheManager();

    /**
     * There is only one item state cache factory
     */
    private final ItemStateCacheFactory cacheFactory = new ManagedMLRUItemStateCacheFactory(cacheMgr);

    /**
     * Chanel for posting create workspace messages.
     */
    private WorkspaceEventChannel createWorkspaceEventChannel;

    /**
     * private constructor
     *
     * @param repConfig
     */
    protected RepositoryImpl(RepositoryConfig repConfig) throws RepositoryException {

        log.info("Starting repository...");

        boolean succeeded = false;
        try {
            this.repConfig = repConfig;

            // Acquire a lock on the repository home
            repLock = new RepositoryLock(repConfig.getHomeDir());
            repLock.acquire();

            // setup file systems
            repStore = repConfig.getFileSystem();
            String fsRootPath = "/meta";
            try {
                if (!repStore.exists(fsRootPath) || !repStore.isFolder(fsRootPath)) {
                    repStore.createFolder(fsRootPath);
                }
            } catch (FileSystemException fse) {
                String msg = "failed to create folder for repository meta data";
                log.error(msg, fse);
                throw new RepositoryException(msg, fse);
            }
            metaDataStore = new BasedFileSystem(repStore, fsRootPath);

            // init root node uuid
            rootNodeId = loadRootNodeId(metaDataStore);

            // load repository properties
            repProps = loadRepProps();
            nodesCount = Long.parseLong(repProps.getProperty(STATS_NODE_COUNT_PROPERTY, "0"));
            propsCount = Long.parseLong(repProps.getProperty(STATS_PROP_COUNT_PROPERTY, "0"));

            // create registries
            nsReg = createNamespaceRegistry(new BasedFileSystem(repStore, "/namespaces"));
            ntReg = createNodeTypeRegistry(nsReg, new BasedFileSystem(repStore, "/nodetypes"));

            dataStore = repConfig.getDataStore();
            if (dataStore != null) {
                assert InternalValue.USE_DATA_STORE;
            }

            // init workspace configs
            Iterator iter = repConfig.getWorkspaceConfigs().iterator();
            while (iter.hasNext()) {
                WorkspaceConfig config = (WorkspaceConfig) iter.next();
                WorkspaceInfo info = createWorkspaceInfo(config);
                wspInfos.put(config.getName(), info);
            }

            // initialize optional clustering
            // put here before setting up any other external event source that a cluster node
            // will be interested in
            if (repConfig.getClusterConfig() != null) {
                clusterNode = createClusterNode();
                nsReg.setEventChannel(clusterNode);
                ntReg.setEventChannel(clusterNode);
                
                createWorkspaceEventChannel = clusterNode;
                clusterNode.setListener(this);
            }

            // init version manager
            vMgr = createVersionManager(repConfig.getVersioningConfig(),
                    delegatingDispatcher);
            if (clusterNode != null) {
                vMgr.setEventChannel(clusterNode.createUpdateChannel(null));
            }

            // init virtual node type manager
            virtNTMgr = new VirtualNodeTypeStateManager(getNodeTypeRegistry(),
                    delegatingDispatcher, NODETYPES_NODE_ID, SYSTEM_ROOT_NODE_ID);

            // initialize startup workspaces
            initStartupWorkspaces();

            // initialize system search manager
            getSystemSearchManager(repConfig.getDefaultWorkspaceName());

            // after the workspace is initialized we pass a system session to
            // the virtual node type manager

            // todo FIXME the *global* virtual node type manager is using a session that is bound to a single specific workspace...
            virtNTMgr.setSession(getSystemSession(repConfig.getDefaultWorkspaceName()));

            // now start cluster node as last step
            if (clusterNode != null) {
                try {
                    clusterNode.start();
                } catch (ClusterException e) {
                    String msg = "Unable to start clustered node, forcing shutdown...";
                    log.error(msg, e);
                    shutdown();
                    throw new RepositoryException(msg, e);
                }
            }

            // amount of time in seconds before an idle workspace is automatically
            // shut down
            int maxIdleTime = repConfig.getWorkspaceMaxIdleTime();
            if (maxIdleTime != 0) {
                // start workspace janitor thread
                Thread wspJanitor = new Thread(new WorkspaceJanitor(maxIdleTime * 1000));
                wspJanitor.setName("WorkspaceJanitor");
                wspJanitor.setPriority(Thread.MIN_PRIORITY);
                wspJanitor.setDaemon(true);
                wspJanitor.start();
            }

            succeeded = true;
            log.info("Repository started");
        } catch (RepositoryException e) {
            log.error("failed to start Repository: " + e.getMessage(), e);
            throw e;
        } finally {
            if (!succeeded) {
                // repository startup failed, clean up...
                shutdown();
            }
        }
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    /**
     * Get the cache manager of this repository, useful
     * for setting its memory parameters.
     *
     * @return the cache manager
     * @since 1.3
     */
    public CacheManager getCacheManager() {
        return cacheMgr;
    }

    /**
     * Get the item state cache factory of this repository.
     *
     * @return the cache factory
     */
    public ItemStateCacheFactory getItemStateCacheFactory() {
        return cacheFactory;
    }

    /**
     * Get the cluster node. Returns <code>null</code> if this repository
     * is not running clustered.
     *
     * @return cluster node
     */
    public ClusterNode getClusterNode() {
        return clusterNode;
    }

    /**
     * Returns the {@link org.apache.jackrabbit.core.security.JackrabbitSecurityManager SecurityManager}
     * of this <code>Repository</code>
     *
     * @return the security manager
     * @throws RepositoryException if an error occurs.
     */
    protected synchronized JackrabbitSecurityManager getSecurityManager()
            throws RepositoryException {

        if (securityMgr == null) {
            SecurityManagerConfig smc = getConfig().getSecurityConfig().getSecurityManagerConfig();
            String workspaceName = getConfig().getDefaultWorkspaceName();
            if (smc != null && smc.getWorkspaceName() != null) {
                workspaceName = smc.getWorkspaceName();
            }
            SystemSession securitySession = getSystemSession(workspaceName);
            // mark system session as 'active' for that the system workspace does
            // not get disposed by workspace-janitor
            onSessionCreated(securitySession);

            if (smc == null) {
                log.debug("No configuration entry for SecurityManager. Using org.apache.jackrabbit.core.security.simple.SimpleSecurityManager");
                securityMgr = new SimpleSecurityManager();
            } else {
                securityMgr = (JackrabbitSecurityManager) smc.newInstance();
            }

            securityMgr.init(this, securitySession);
            log.info("SecurityManager = " + securityMgr.getClass());
        }
        return securityMgr;
    }

    /**
     * Creates the version manager.
     *
     * @param vConfig the versioning config
     * @return the newly created version manager
     * @throws RepositoryException if an error occurrs
     */
    protected VersionManagerImpl createVersionManager(VersioningConfig vConfig,
                                                      DelegatingObservationDispatcher delegatingDispatcher)
            throws RepositoryException {


        FileSystem fs = vConfig.getFileSystem();
        PersistenceManager pm = createPersistenceManager(vConfig.getHomeDir(),
                fs,
                vConfig.getPersistenceManagerConfig(),
                rootNodeId,
                nsReg,
                ntReg,
                dataStore);

        ISMLocking ismLocking = vConfig.getISMLockingConfig().createISMLocking();

        return new VersionManagerImpl(pm, fs, ntReg, delegatingDispatcher,
                VERSION_STORAGE_NODE_ID, SYSTEM_ROOT_NODE_ID, cacheFactory,
                ismLocking);
    }

    /**
     * Initialize startup workspaces. Base implementation will initialize the
     * default workspace. Derived classes may initialize their own startup
     * workspaces <b>after</b> having called the base implementation.
     *
     * @throws RepositoryException if an error occurs
     */
    protected void initStartupWorkspaces() throws RepositoryException {
        String wspName = repConfig.getDefaultWorkspaceName();
        String secWspName = null;
        SecurityManagerConfig smc = repConfig.getSecurityConfig().getSecurityManagerConfig();
        if (smc != null) {
           secWspName = smc.getWorkspaceName();
        }
        try {
            initWorkspace((WorkspaceInfo) wspInfos.get(wspName));
            if (secWspName != null && !wspInfos.containsKey(secWspName)) {
                createWorkspace(secWspName);
                log.info("created system workspace: {}", secWspName);
            }
        } catch (RepositoryException e) {
            // if default workspace failed to initialize, shutdown again
            log.error("Failed to initialize workspace '" + wspName + "'", e);
            log.error("Unable to start repository, forcing shutdown...");
            shutdown();
            throw e;
        }
    }

    /**
     * Returns the root node uuid.
     * @param fs
     * @return
     * @throws RepositoryException
     */
    protected NodeId loadRootNodeId(FileSystem fs) throws RepositoryException {
        FileSystemResource uuidFile = new FileSystemResource(fs, "rootUUID");
        try {
            if (uuidFile.exists()) {
                try {
                    // load uuid of the repository's root node
                    InputStream in = uuidFile.getInputStream();
/*
                   // uuid is stored in binary format (16 bytes)
                   byte[] bytes = new byte[16];
                   try {
                       in.read(bytes);
                   } finally {
                       try {
                           in.close();
                       } catch (IOException ioe) {
                           // ignore
                       }
                   }
                   rootNodeUUID = new UUID(bytes).toString();            // uuid is stored in binary format (16 bytes)
*/
                    // uuid is stored in text format (36 characters) for better readability
                    char[] chars = new char[36];
                    InputStreamReader reader = new InputStreamReader(in);
                    try {
                        reader.read(chars);
                    } finally {
                        IOUtils.closeQuietly(reader);
                    }
                    return NodeId.valueOf(new String(chars));
                } catch (Exception e) {
                    String msg = "failed to load persisted repository state";
                    log.debug(msg);
                    throw new RepositoryException(msg, e);
                }
            } else {
                // create new uuid
/*
                UUID rootUUID = UUID.randomUUID();     // version 4 uuid
                rootNodeUUID = rootUUID.toString();
*/
                /**
                 * use hard-coded uuid for root node rather than generating
                 * a different uuid per repository instance; using a
                 * hard-coded uuid makes it easier to copy/move entire
                 * workspaces from one repository instance to another.
                 */
                try {
                    // persist uuid of the repository's root node
                    OutputStream out = uuidFile.getOutputStream();
/*
                    // store uuid in binary format
                    try {
                        out.write(rootUUID.getBytes());
                    } finally {
                        try {
                            out.close();
                        } catch (IOException ioe) {
                            // ignore
                        }
                    }
*/
                    // store uuid in text format for better readability
                    OutputStreamWriter writer = new OutputStreamWriter(out);
                    try {
                        writer.write(ROOT_NODE_ID.toString());
                    } finally {
                        IOUtils.closeQuietly(writer);
                    }
                    return ROOT_NODE_ID;
                } catch (Exception e) {
                    String msg = "failed to persist repository state";
                    log.debug(msg);
                    throw new RepositoryException(msg, e);
                }
            }
        } catch (FileSystemException fse) {
            String msg = "failed to access repository state";
            log.debug(msg);
            throw new RepositoryException(msg, fse);
        }
    }

    /**
     * Creates the <code>NamespaceRegistry</code> instance.
     *
     * @param fs
     * @return
     * @throws RepositoryException
     */
    protected NamespaceRegistryImpl createNamespaceRegistry(FileSystem fs)
            throws RepositoryException {
        return new NamespaceRegistryImpl(fs);
    }

    /**
     * Creates the <code>NodeTypeRegistry</code> instance.
     *
     * @param fs
     * @return
     * @throws RepositoryException
     */
    protected NodeTypeRegistry createNodeTypeRegistry(NamespaceRegistry nsReg,
                                                      FileSystem fs)
            throws RepositoryException {
        return NodeTypeRegistry.create(nsReg, fs);
    }

    /**
     * Creates a new <code>RepositoryImpl</code> instance.
     * <p/>
     *
     * @param config the configuration of the repository
     * @return a new <code>RepositoryImpl</code> instance
     * @throws RepositoryException If an error occurs
     */
    public static RepositoryImpl create(RepositoryConfig config)
            throws RepositoryException {
        return new RepositoryImpl(config);
    }

    /**
     * Performs a sanity check on this repository instance.
     *
     * @throws IllegalStateException if this repository has been rendered
     *                               invalid for some reason (e.g. if it has
     *                               been shut down)
     */
    protected void sanityCheck() throws IllegalStateException {
        // check repository status
        if (disposed) {
            throw new IllegalStateException("repository instance has been shut down");
        }
    }

    private void initWorkspace(WorkspaceInfo wspInfo) throws RepositoryException {
        // first initialize workspace info
        if (!wspInfo.initialize()) {
            // workspace has already been initialized, we're done
            return;
        }

        // get system session and Workspace instance
        SessionImpl sysSession = wspInfo.getSystemSession();
        WorkspaceImpl wsp = (WorkspaceImpl) sysSession.getWorkspace();

        /**
         * todo implement 'System' workspace
         * FIXME
         * - there should be one 'System' workspace per repository
         * - the 'System' workspace should have the /jcr:system node
         * - versions, version history and node types should be reflected in
         *   this system workspace as content under /jcr:system
         * - all other workspaces should be dynamic workspaces based on
         *   this 'read-only' system workspace
         *
         * for now, the jcr:system node is created in
         * {@link org.apache.jackrabbit.core.state.SharedItemStateManager#createRootNodeState}
         */

        // register the repository as event listener for keeping repository statistics
        wsp.getObservationManager().addEventListener(this,
                Event.NODE_ADDED | Event.NODE_REMOVED
                | Event.PROPERTY_ADDED | Event.PROPERTY_REMOVED,
                "/", true, null, null, false);

        // register SearchManager as event listener
        SearchManager searchMgr = wspInfo.getSearchManager();
        if (searchMgr != null) {
            wsp.getObservationManager().addEventListener(searchMgr,
                    Event.NODE_ADDED | Event.NODE_REMOVED
                    | Event.PROPERTY_ADDED | Event.PROPERTY_REMOVED
                    | Event.PROPERTY_CHANGED,
                    "/", true, null, null, false);
        }
    }

    /**
     * Returns the system search manager or <code>null</code> if none is
     * configured.
     */
    private SearchManager getSystemSearchManager(String wspName)
            throws RepositoryException {
        if (systemSearchMgr == null) {
            if (repConfig.getSearchConfig() != null) {
                SystemSession defSysSession = getSystemSession(wspName);
                systemSearchMgr = new SearchManager(repConfig.getSearchConfig(),
                        nsReg, ntReg, defSysSession.getItemStateManager(),
                        vMgr.getPersistenceManager(), SYSTEM_ROOT_NODE_ID, null, null);
                ObservationManager obsMgr = defSysSession.getWorkspace().getObservationManager();
                obsMgr.addEventListener(systemSearchMgr, Event.NODE_ADDED
                        | Event.NODE_REMOVED | Event.PROPERTY_ADDED
                        | Event.PROPERTY_CHANGED | Event.PROPERTY_REMOVED,
                        "/" + defSysSession.getJCRName(NameConstants.JCR_SYSTEM),
                        true, null, null, false);
            }
        }
        return systemSearchMgr;
    }

    /**
     * Creates the cluster node.
     *
     * @return clustered node
     */
    protected ClusterNode createClusterNode() throws RepositoryException {
        try {
            ClusterNode clusterNode = new ClusterNode();
            clusterNode.init(new ExternalEventListener());
            return clusterNode;
        } catch (Exception e) {
            throw new RepositoryException(e);
        }
    }

    protected NamespaceRegistryImpl getNamespaceRegistry() {
        // check sanity of this instance
        sanityCheck();

        return nsReg;
    }

    protected NodeTypeRegistry getNodeTypeRegistry() {
        // check sanity of this instance
        sanityCheck();

        return ntReg;
    }

    protected VersionManager getVersionManager() {
        // check sanity of this instance
        sanityCheck();

        return vMgr;
    }

    protected NodeId getRootNodeId() {
        // check sanity of this instance
        sanityCheck();

        return rootNodeId;
    }

    /**
     * Returns the names of <i>all</i> workspaces in this repository.
     *
     * @return the names of all workspaces in this repository.
     * @see javax.jcr.Workspace#getAccessibleWorkspaceNames()
     */
    protected String[] getWorkspaceNames() {
        synchronized (wspInfos) {
            return (String[]) wspInfos.keySet().toArray(new String[wspInfos.keySet().size()]);
        }
    }

    /**
     * Returns the {@link WorkspaceInfo} for the named workspace.
     *
     * @param workspaceName The name of the workspace whose {@link WorkspaceInfo}
     *                      is to be returned. This must not be <code>null</code>.
     * @return The {@link WorkspaceInfo} for the named workspace. This will
     *         never be <code>null</code>.
     * @throws IllegalStateException    If this repository has already been
     *                                  shut down.
     * @throws NoSuchWorkspaceException If the named workspace does not exist.
     */
    protected WorkspaceInfo getWorkspaceInfo(String workspaceName)
            throws IllegalStateException, NoSuchWorkspaceException {
        // check sanity of this instance
        sanityCheck();

        WorkspaceInfo wspInfo;
        synchronized (wspInfos) {
            wspInfo = (WorkspaceInfo) wspInfos.get(workspaceName);
            if (wspInfo == null) {
                throw new NoSuchWorkspaceException(workspaceName);
            }
        }

        try {
            initWorkspace(wspInfo);
        } catch (RepositoryException e) {
            log.error("Unable to initialize workspace '" + workspaceName + "'", e);
            throw new NoSuchWorkspaceException(workspaceName);
        }
        return wspInfo;
    }

    /**
     * Creates a workspace with the given name.
     *
     * @param workspaceName name of the new workspace
     * @throws RepositoryException if a workspace with the given name
     *                             already exists or if another error occurs
     * @see SessionImpl#createWorkspace(String)
     */
    protected void createWorkspace(String workspaceName)
            throws RepositoryException {
        synchronized (wspInfos) {
            if (wspInfos.containsKey(workspaceName)) {
                throw new RepositoryException("workspace '"
                        + workspaceName + "' already exists.");
            }

            // needed to get newly created workspace config file content when runnin in clustered environment
            StringBuffer workspaceConfigContent = clusterNode != null ? new StringBuffer() : null;

            // create the workspace configuration
            WorkspaceConfig config = repConfig.createWorkspaceConfig(workspaceName, workspaceConfigContent);
            WorkspaceInfo info = createWorkspaceInfo(config);
            wspInfos.put(workspaceName, info);

            if (workspaceConfigContent != null && createWorkspaceEventChannel != null) {
                // notify other cluster node that workspace has been created
                InputSource s = new InputSource(new StringReader(workspaceConfigContent.toString()));
                createWorkspaceEventChannel.workspaceCreated(workspaceName, new ClonedInputSource(s));
            }
        }
    }

    public void externalWorkspaceCreated(String workspaceName,
            InputSource configTemplate) throws RepositoryException {

        createWorkspaceInternal(workspaceName, configTemplate);
    }

    /**
     * Creates a workspace with the given name and given workspace configuration
     * template.
     * 
     * The difference between this method and {@link #createWorkspace(String, InputSource)}
     * is that the later notifies the other cluster node that workspace has been created
     * whereas this method only creates the workspace.
     *
     * @param workspaceName  name of the new workspace
     * @param configTemplate the workspace configuration template of the new
     *                       workspace
     * @throws RepositoryException if a workspace with the given name already
     *                             exists or if another error occurs
     * @see SessionImpl#createWorkspace(String,InputSource)
     */
    private void createWorkspaceInternal(String workspaceName,
                                   InputSource configTemplate)
            throws RepositoryException {
        synchronized (wspInfos) {
            if (wspInfos.containsKey(workspaceName)) {
                throw new RepositoryException("workspace '"
                        + workspaceName + "' already exists.");
            }

            // create the workspace configuration
            WorkspaceConfig config = repConfig.createWorkspaceConfig(workspaceName, configTemplate);
            WorkspaceInfo info = createWorkspaceInfo(config);
            wspInfos.put(workspaceName, info);
        }
    }
    
    /**
     * Creates a workspace with the given name and given workspace configuration
     * template.
     *
     * @param workspaceName  name of the new workspace
     * @param configTemplate the workspace configuration template of the new
     *                       workspace
     * @throws RepositoryException if a workspace with the given name already
     *                             exists or if another error occurs
     * @see SessionImpl#createWorkspace(String,InputSource)
     */
    protected void createWorkspace(String workspaceName,
                                   InputSource configTemplate)
            throws RepositoryException {

        if (createWorkspaceEventChannel == null) {
            createWorkspaceInternal(workspaceName, configTemplate);
        }
        else {
        
            ClonedInputSource template = new ClonedInputSource(configTemplate);
            createWorkspaceInternal(workspaceName, template.cloneInputSource());
            createWorkspaceEventChannel.workspaceCreated(workspaceName, template);
        }
    }

    SharedItemStateManager getWorkspaceStateManager(String workspaceName)
            throws NoSuchWorkspaceException, RepositoryException {
        // check sanity of this instance
        sanityCheck();

        return getWorkspaceInfo(workspaceName).getItemStateProvider();
    }

    /**
     * Enables or disables referential integrity checking for given workspace.
     * Disabling referential integrity checks can result in a corrupted
     * workspace, and thus this feature is only available to customized
     * implementations that subclass RepositoryImpl.
     *
     * @see https://issues.apache.org/jira/browse/JCR-954
     * @param workspace name of the workspace
     * @param enabled <code>true</code> to enable integrity checking (default),
     *                <code>false</code> to disable it
     * @throws RepositoryException if an error occurs
     */
    protected void setReferentialIntegrityChecking(
            String workspace, boolean enabled) throws RepositoryException {
        SharedItemStateManager manager = getWorkspaceStateManager(workspace);
        manager.setCheckReferences(enabled);
    }

    ObservationDispatcher getObservationDispatcher(String workspaceName)
            throws NoSuchWorkspaceException {
        // check sanity of this instance
        sanityCheck();

        return getWorkspaceInfo(workspaceName).getObservationDispatcher();
    }

    /**
     * Returns the {@link SearchManager} for the workspace with name
     * <code>workspaceName</code>.
     *
     * @param workspaceName the name of the workspace.
     * @return the <code>SearchManager</code> for the workspace, or
     *         <code>null</code> if the workspace does not have a
     *         <code>SearchManager</code> configured.
     * @throws NoSuchWorkspaceException if there is no workspace with name
     *                                  <code>workspaceName</code>.
     * @throws RepositoryException      if an error occurs while opening the
     *                                  search index.
     */
    SearchManager getSearchManager(String workspaceName)
            throws NoSuchWorkspaceException, RepositoryException {
        // check sanity of this instance
        sanityCheck();

        return getWorkspaceInfo(workspaceName).getSearchManager();
    }

    /**
     * Returns the {@link LockManager} for the workspace with name
     * <code>workspaceName</code>
     *
     * @param workspaceName workspace name
     * @return <code>LockManager</code> for the workspace
     * @throws NoSuchWorkspaceException if such a workspace does not exist
     * @throws RepositoryException      if some other error occurs
     */
    LockManager getLockManager(String workspaceName) throws
            NoSuchWorkspaceException, RepositoryException {
        // check sanity of this instance
        sanityCheck();

        return getWorkspaceInfo(workspaceName).getLockManager();
    }

    /**
     * Returns the {@link SystemSession} for the workspace with name
     * <code>workspaceName</code>
     *
     * @param workspaceName workspace name
     * @return system session of the specified workspace
     * @throws NoSuchWorkspaceException if such a workspace does not exist
     * @throws RepositoryException      if some other error occurs
     */
    SystemSession getSystemSession(String workspaceName)
            throws NoSuchWorkspaceException, RepositoryException {
        // check sanity of this instance
        sanityCheck();

        return getWorkspaceInfo(workspaceName).getSystemSession();
    }

    /**
     * Creates a new repository session on the specified workspace for the
     * <b><i>authenticated</i></b> subject of the given login context and
     * adds it to the <i>active</i> sessions.
     * <p/>
     * Calls {@link #createSessionInstance(AuthContext, WorkspaceConfig)} to
     * create the actual <code>SessionImpl</code> instance.
     *
    * @param loginContext  login context with authenticated subject
     * @param workspaceName workspace name
     * @return a new session
     * @throws NoSuchWorkspaceException if the specified workspace does not exist
     * @throws AccessDeniedException    if the subject of the given login context
     *                                  is not granted access to the specified
     *                                  workspace
     * @throws RepositoryException      if another error occurs
     */
    protected final SessionImpl createSession(AuthContext loginContext,
                                              String workspaceName)
            throws NoSuchWorkspaceException, AccessDeniedException,
            RepositoryException {
        WorkspaceInfo wspInfo = getWorkspaceInfo(workspaceName);
        SessionImpl ses = createSessionInstance(loginContext, wspInfo.getConfig());
        onSessionCreated(ses);
        // reset idle timestamp
        wspInfo.setIdleTimestamp(0);
        return ses;
    }

    /**
     * Creates a new repository session on the specified workspace for the given
     * <b><i>authenticated</i></b> subject and adds it to the <i>active</i>
     * sessions.
     * <p/>
     * Calls {@link #createSessionInstance(Subject, WorkspaceConfig)} to
     * create the actual <code>SessionImpl</code> instance.
     *
     * @param subject       authenticated subject
     * @param workspaceName workspace name
     * @return a new session
     * @throws NoSuchWorkspaceException if the specified workspace does not exist
     * @throws AccessDeniedException    if the subject of the given login context
     *                                  is not granted access to the specified
     *                                  workspace
     * @throws RepositoryException      if another error occurs
     */
    protected final SessionImpl createSession(Subject subject,
                                              String workspaceName)
            throws NoSuchWorkspaceException, AccessDeniedException,
            RepositoryException {
        WorkspaceInfo wspInfo = getWorkspaceInfo(workspaceName);
        SessionImpl ses = createSessionInstance(subject, wspInfo.getConfig());
        onSessionCreated(ses);
        // reset idle timestamp
        wspInfo.setIdleTimestamp(0);
        return ses;
    }

    /**
     * Adds the given session to the list of active sessions and registers this
     * repository as listener.
     *
     * @param session the session to register
     */
    protected void onSessionCreated(SessionImpl session) {
        synchronized (activeSessions) {
            session.addListener(this);
            activeSessions.put(session, session);
        }
    }

    /**
     * Tries to add Principals to a given subject:
     * First Access the Subject from the current AccessControlContext,
     * If Subject is found the LoginContext is evoked for it, in order
     * to possibly allow for extension of preauthenticated Subject.<br>
     * In contrast to a login with Credentials, a Session is created, even if the
     * Authentication failed.<br>
     * If the {@link Subject} is marked to be unmodificable or if the
     * authentication of the the Subject failed Session is build for unchanged
     * Subject.
     *
     * @param workspaceName must not be null
     * @return if a Subject is exsting null else
     * @throws RepositoryException
     * @throws AccessDeniedException
     */
    private Session extendAuthentication(String workspaceName)
            throws RepositoryException, AccessDeniedException {

        Subject subject = null;
        try {
            AccessControlContext acc = AccessController.getContext();
            subject = Subject.getSubject(acc);
        } catch (SecurityException e) {
            log.warn("Can't check for preauthentication. Reason:", e.getMessage());
        }
        if (subject == null) {
            log.debug("No preauthenticated subject found -> return null.");
            return null;
        }

        Session s;
        if (subject.isReadOnly()) {
            log.debug("Preauthenticated Subject is read-only -> create Session");
            s = createSession(subject, workspaceName);
        } else {
            log.debug("Found preauthenticated Subject, try to extend authentication");
            // login either using JAAS or custom LoginModule
            AuthContext authCtx = getSecurityManager().getAuthContext(null, subject);
            try {
                authCtx.login();
                s = createSession(authCtx, workspaceName);
            } catch (javax.security.auth.login.LoginException e) {
                // subject could not be extended
                log.debug("Preauthentication could not be extended");
                s = createSession(subject, workspaceName);
            }
        }
        return s;
    }

    //-------------------------------------------------< JackrabbitRepository >

    /**
     * Shuts down this repository. The shutdown is guarded by a shutdown lock
     * that prevents any new sessions from being started simultaneously.
     */
    public void shutdown() {
        try {
            shutdownLock.writeLock().acquire();
        } catch (InterruptedException e) {
            // TODO: Should this be a checked exception?
            throw new RuntimeException("Shutdown lock could not be acquired", e);
        }

        try {
            // check status of this instance
            if (!disposed) {
                doShutdown();
            }
        } finally {
            shutdownLock.writeLock().release();
        }
    }

    /**
     * Private method that performs the actual shutdown after the shutdown
     * lock has been acquired by the {@link #shutdown()} method.
     */
    private synchronized void doShutdown() {
        log.info("Shutting down repository...");

        // stop optional cluster node
        if (clusterNode != null) {
            clusterNode.stop();
        }

        if (securityMgr != null) {
            securityMgr.close();
        }

        // close active user sessions
        // (copy sessions to array to avoid ConcurrentModificationException;
        // manually copy entries rather than calling ReferenceMap#toArray() in
        // order to work around  http://issues.apache.org/bugzilla/show_bug.cgi?id=25551)
        SessionImpl[] sa;
        synchronized (activeSessions) {
            int cnt = 0;
            sa = new SessionImpl[activeSessions.size()];
            for (Iterator it = activeSessions.values().iterator(); it.hasNext(); cnt++) {
                sa[cnt] = (SessionImpl) it.next();
            }
        }
        for (int i = 0; i < sa.length; i++) {
            if (sa[i] != null) {
                sa[i].logout();
            }
        }

        // shutdown system search manager if there is one
        if (systemSearchMgr != null) {
            systemSearchMgr.close();
        }

        // shut down workspaces
        synchronized (wspInfos) {
            for (Iterator it = wspInfos.values().iterator(); it.hasNext();) {
                WorkspaceInfo wspInfo = (WorkspaceInfo) it.next();
                wspInfo.dispose();
            }
        }

        if (vMgr != null) {
            try {
                vMgr.close();
            } catch (Exception e) {
                log.error("Error while closing Version Manager.", e);
            }
        }

        if (repProps != null) {
            // persist repository properties
            try {
                storeRepProps(repProps);
            } catch (RepositoryException e) {
                log.error("failed to persist repository properties", e);
            }
        }

        if (repStore != null) {
            try {
                // close repository file system
                repStore.close();
            } catch (FileSystemException e) {
                log.error("error while closing repository file system", e);
            }
        }

        // make sure this instance is not used anymore
        disposed = true;

        // wake up threads waiting on this instance's monitor (e.g. workspace janitor)
        notifyAll();

        // finally release repository lock
        repLock.release();

        log.info("Repository has been shutdown");
    }

    /**
     * Returns the configuration of this repository.
     * @return repository configuration
     */
    public RepositoryConfig getConfig() {
        return repConfig;
    }

    /**
     * Returns the repository file system.
     * @return repository file system
     */
    protected FileSystem getFileSystem() {
        return repStore;
    }

    /**
     * Sets the default properties of the repository.
     * <p/>
     * This method loads the <code>Properties</code> from the
     * <code>org/apache/jackrabbit/core/repository.properties</code> resource
     * found in the class path and (re)sets the statistics properties, if not
     * present.
     *
     * @param props the properties object to load
     *
     * @throws RepositoryException if the properties can not be loaded
     */
    protected void setDefaultRepositoryProperties(Properties props)
            throws RepositoryException {
        InputStream in = RepositoryImpl.class.getResourceAsStream("repository.properties");
        try {
            props.load(in);
            in.close();

            // set counts
            if (!props.containsKey(STATS_NODE_COUNT_PROPERTY)) {
                props.setProperty(STATS_NODE_COUNT_PROPERTY, Long.toString(nodesCount));
            }
            if (!props.containsKey(STATS_PROP_COUNT_PROPERTY)) {
                props.setProperty(STATS_PROP_COUNT_PROPERTY, Long.toString(propsCount));
            }
        } catch (IOException e) {
            String msg = "Failed to load repository properties: " + e.toString();
            log.error(msg);
            throw new RepositoryException(msg, e);
        }
    }

    /**
     * Loads the repository properties by executing the following steps:
     * <ul>
     * <li> if the {@link #PROPERTIES_RESOURCE} exists in the meta data store,
     * the properties are loaded from that resource.</li>
     * <li> {@link #setDefaultRepositoryProperties(Properties)} is called
     * afterwards in order to initialize/update the repository properties
     * since some default properties might have changed and need updating.</li>
     * <li> finally {@link #storeRepProps(Properties)} is called in order to
     * persist the newly generated properties.</li>
     * </ul>
     *
     * @return the newly loaded/initialized repository properties
     *
     * @throws RepositoryException
     */
    protected Properties loadRepProps() throws RepositoryException {
        FileSystemResource propFile = new FileSystemResource(metaDataStore, PROPERTIES_RESOURCE);
        try {
            Properties props = new Properties();
            if (propFile.exists()) {
                InputStream in = propFile.getInputStream();
                try {
                    props.load(in);
                } finally {
                    in.close();
                }
            }
            // now set the default props
            setDefaultRepositoryProperties(props);

            // and store
            storeRepProps(props);

            return props;

        } catch (Exception e) {
            String msg = "failed to load repository properties";
            log.debug(msg);
            throw new RepositoryException(msg, e);
        }
    }

    /**
     * Stores the properties to a persistent resource in the meta filesytem.
     *
     * @throws RepositoryException
     */
    protected void storeRepProps(Properties props) throws RepositoryException {
        FileSystemResource propFile = new FileSystemResource(metaDataStore, PROPERTIES_RESOURCE);
        try {
            propFile.makeParentDirs();
            OutputStream os = propFile.getOutputStream();
            try {
                props.store(os, null);
            } finally {
                // make sure stream is closed
                os.close();
            }
        } catch (Exception e) {
            String msg = "failed to persist repository properties";
            log.debug(msg);
            throw new RepositoryException(msg, e);
        }
    }

    /**
     * Creates a workspace persistence manager based on the given
     * configuration. The persistence manager is instantiated using
     * information in the given persistence manager configuration and
     * initialized with a persistence manager context containing the other
     * arguments.
     *
     * @return the created workspace persistence manager
     * @throws RepositoryException if the persistence manager could
     *                             not be instantiated/initialized
     */
    private static PersistenceManager createPersistenceManager(File homeDir,
                                                               FileSystem fs,
                                                               PersistenceManagerConfig pmConfig,
                                                               NodeId rootNodeId,
                                                               NamespaceRegistry nsReg,
                                                               NodeTypeRegistry ntReg,
                                                               DataStore dataStore)
            throws RepositoryException {
        try {
            PersistenceManager pm = (PersistenceManager) pmConfig.newInstance();
            pm.init(new PMContext(homeDir, fs, rootNodeId, nsReg, ntReg, dataStore));
            return pm;
        } catch (Exception e) {
            String msg = "Cannot instantiate persistence manager " + pmConfig.getClassName();
            throw new RepositoryException(msg, e);
        }
    }

    /**
     * Creates a <code>SharedItemStateManager</code> or derivative.
     *
     * @param persistMgr     persistence manager
     * @param rootNodeId     root node id
     * @param ntReg          node type registry
     * @param usesReferences <code>true</code> if the item state manager should use
     *                       node references to verify integrity of its reference properties;
     *                       <code>false</code> otherwise
     * @param cacheFactory   cache factory
     * @return item state manager
     * @throws ItemStateException if an error occurs
     */
    protected SharedItemStateManager createItemStateManager(PersistenceManager persistMgr,
                                                            NodeId rootNodeId,
                                                            NodeTypeRegistry ntReg,
                                                            boolean usesReferences,
                                                            ItemStateCacheFactory cacheFactory,
                                                            ISMLocking locking)
            throws ItemStateException {

        return new SharedItemStateManager(persistMgr, rootNodeId, ntReg, true, cacheFactory, locking);
    }

    //-----------------------------------------------------------< Repository >
    /**
     * {@inheritDoc}
     */
    public Session login(Credentials credentials, String workspaceName)
            throws LoginException, NoSuchWorkspaceException, RepositoryException {
        try {
            shutdownLock.readLock().acquire();
        } catch (InterruptedException e) {
            throw new RepositoryException("Login lock could not be acquired", e);
        }

        try {
            // check sanity of this instance
            sanityCheck();

            if (workspaceName == null) {
                workspaceName = repConfig.getDefaultWorkspaceName();
            }

            // check if workspace exists (will throw NoSuchWorkspaceException if not)
            getWorkspaceInfo(workspaceName);

            if (credentials == null) {
                // try to obtain the identity of the already authenticated
                // subject from access control context
                Session session = extendAuthentication(workspaceName);
                if (session != null) {
                    // successful extended authentication
                    return session;
                } else {
                    log.debug("Attempt to login without Credentials and Subject -> try login with null credentials.");
                }
            }
            // not preauthenticated -> try login with credentials
            AuthContext authCtx = getSecurityManager().getAuthContext(credentials, new Subject());
            authCtx.login();
            // create session
            return createSession(authCtx, workspaceName);

        } catch (SecurityException se) {
            throw new LoginException("Unable to access authentication information", se);
        } catch (javax.security.auth.login.LoginException le) {
            throw new LoginException(le.getMessage(), le);
        } catch (AccessDeniedException ade) {
            // authenticated subject is not authorized for the specified workspace
            throw new LoginException("Workspace access denied", ade);
        } finally {
            shutdownLock.readLock().release();
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getDescriptor(String key) {
        return repProps.getProperty(key);
    }

    /**
     * {@inheritDoc}
     */
    public String[] getDescriptorKeys() {
        String[] keys = (String[]) repProps.keySet().toArray(new String[repProps.keySet().size()]);
        Arrays.sort(keys);
        return keys;
    }

    //------------------------------------------------------< SessionListener >
    /**
     * {@inheritDoc}
     */
    public void loggingOut(SessionImpl session) {
    }

    /**
     * {@inheritDoc}
     */
    public void loggedOut(SessionImpl session) {
        synchronized (activeSessions) {
            // remove session from active sessions
            activeSessions.remove(session);
        }
    }

    //--------------------------------------------------------< EventListener >
    /**
     * {@inheritDoc}
     */
    public void onEvent(EventIterator events) {
        // check status of this instance
        if (disposed) {
            // ignore, repository instance has been shut down
            return;
        }

        synchronized (repProps) {
            while (events.hasNext()) {
                Event event = events.nextEvent();
                long type = event.getType();
                if ((type & Event.NODE_ADDED) == Event.NODE_ADDED) {
                    nodesCount++;
                    repProps.setProperty(STATS_NODE_COUNT_PROPERTY, Long.toString(nodesCount));
                }
                if ((type & Event.NODE_REMOVED) == Event.NODE_REMOVED) {
                    nodesCount--;
                    repProps.setProperty(STATS_NODE_COUNT_PROPERTY, Long.toString(nodesCount));
                }
                if ((type & Event.PROPERTY_ADDED) == Event.PROPERTY_ADDED) {
                    propsCount++;
                    repProps.setProperty(STATS_PROP_COUNT_PROPERTY, Long.toString(propsCount));
                }
                if ((type & Event.PROPERTY_REMOVED) == Event.PROPERTY_REMOVED) {
                    propsCount--;
                    repProps.setProperty(STATS_PROP_COUNT_PROPERTY, Long.toString(propsCount));
                }
            }
        }
    }

    //------------------------------------------< overridable factory methods >
    /**
     * Creates an instance of the {@link SessionImpl} class representing a
     * user authenticated by the <code>loginContext</code> instance attached
     * to the workspace configured by the <code>wspConfig</code>.
     *
     * @throws AccessDeniedException if the subject of the given login context
     *                               is not granted access to the specified
     *                               workspace
     * @throws RepositoryException   If any other error occurs creating the
     *                               session.
     */
    protected SessionImpl createSessionInstance(AuthContext loginContext,
                                                WorkspaceConfig wspConfig)
            throws AccessDeniedException, RepositoryException {

        return new XASessionImpl(this, loginContext, wspConfig);
    }

    /**
     * Creates an instance of the {@link SessionImpl} class representing a
     * user represented by the <code>subject</code> instance attached
     * to the workspace configured by the <code>wspConfig</code>.
     *
     * @throws AccessDeniedException if the subject of the given login context
     *                               is not granted access to the specified
     *                               workspace
     * @throws RepositoryException   If any other error occurs creating the
     *                               session.
     */
    protected SessionImpl createSessionInstance(Subject subject,
                                                WorkspaceConfig wspConfig)
            throws AccessDeniedException, RepositoryException {

        return new XASessionImpl(this, subject, wspConfig);
    }

    /**
     * Creates a new {@link RepositoryImpl.WorkspaceInfo} instance for
     * <code>wspConfig</code>.
     *
     * @param wspConfig the workspace configuration.
     * @return a new <code>WorkspaceInfo</code> instance.
     */
    protected WorkspaceInfo createWorkspaceInfo(WorkspaceConfig wspConfig) {
        return new WorkspaceInfo(wspConfig);
    }

    //--------------------------------------------------------< inner classes >
    /**
     * <code>WorkspaceInfo</code> holds the objects that are shared
     * among multiple per-session <code>WorkspaceImpl</code> instances
     * representing the same named workspace, i.e. the same physical
     * storage.
     */
    protected class WorkspaceInfo implements UpdateEventListener {

        /**
         * workspace configuration (passed in constructor)
         */
        private final WorkspaceConfig config;

        /**
         * file system (instantiated on init)
         */
        private FileSystem fs;

        /**
         * persistence manager (instantiated on init)
         */
        private PersistenceManager persistMgr;

        /**
         * item state provider (instantiated on init)
         */
        private SharedItemStateManager itemStateMgr;

        /**
         * observation dispatcher (instantiated on init)
         */
        private ObservationDispatcher dispatcher;

        /**
         * system session (lazily instantiated)
         */
        private SystemSession systemSession;

        /**
         * search manager (lazily instantiated)
         */
        private SearchManager searchMgr;

        /**
         * lock manager (lazily instantiated)
         */
        private LockManagerImpl lockMgr;

        /**
         * flag indicating whether this instance has been initialized.
         */
        private boolean initialized;

        /**
         * lock that guards the initialization of this instance
         */
        private final ReadWriteLock initLock =
                new ReentrantWriterPreferenceReadWriteLock();

        /**
         * timestamp when the workspace has been determined being idle
         */
        private long idleTimestamp;

        /**
         * mutex for this workspace, used for locking transactions
         */
        private final Mutex xaLock = new Mutex();

        /**
         * Update event channel, used in clustered environment.
         */
        private UpdateEventChannel updateChannel;

        /**
         * Lock event channel, used in clustered environment.
         */
        private LockEventChannel lockChannel;

        /**
         * Creates a new <code>WorkspaceInfo</code> based on the given
         * <code>config</code>.
         *
         * @param config workspace configuration
         */
        protected WorkspaceInfo(WorkspaceConfig config) {
            this.config = config;
            idleTimestamp = 0;
            initialized = false;
        }

        /**
         * Returns the workspace name.
         *
         * @return the workspace name
         */
        protected String getName() {
            return config.getName();
        }

        /**
         * Returns the workspace configuration.
         *
         * @return the workspace configuration
         */
        public WorkspaceConfig getConfig() {
            return config;
        }

        /**
         * Returns the timestamp when the workspace has become idle or zero
         * if the workspace is currently not idle.
         *
         * @return the timestamp when the workspace has become idle or zero if
         *         the workspace is not idle.
         */
        final long getIdleTimestamp() {
            return idleTimestamp;
        }

        /**
         * Sets the timestamp when the workspace has become idle. if
         * <code>ts == 0</code> the workspace is marked as being currently
         * active.
         *
         * @param ts timestamp when workspace has become idle.
         */
        final void setIdleTimestamp(long ts) {
            idleTimestamp = ts;
        }

        /**
         * Returns <code>true</code> if this workspace info is initialized,
         * otherwise returns <code>false</code>.
         *
         * @return <code>true</code> if this workspace info is initialized.
         */
        protected final boolean isInitialized() {
            try {
                if (!initLock.readLock().attempt(0)) {
                    return false;
                }
            } catch (InterruptedException e) {
                return false;
            }
            // can't use 'finally' pattern here
            boolean ret = initialized;
            initLock.readLock().release();
            return ret;
        }

        /**
         * Returns the workspace file system.
         *
         * @return the workspace file system
         */
        protected FileSystem getFileSystem() {
            if (!isInitialized()) {
                throw new IllegalStateException("workspace '" + getName()
                        + "' not initialized");
            }

            return fs;
        }

        /**
         * Returns the workspace persistence manager.
         *
         * @return the workspace persistence manager
         * @throws RepositoryException if the persistence manager could not be
         * instantiated/initialized
         */
        protected PersistenceManager getPersistenceManager()
                throws RepositoryException {
            if (!isInitialized()) {
                throw new IllegalStateException("workspace '" + getName()
                        + "' not initialized");
            }

            return persistMgr;
        }

        /**
         * Returns the workspace item state provider
         *
         * @return the workspace item state provider
         * @throws RepositoryException if the workspace item state provider
         *                             could not be created
         */
        protected SharedItemStateManager getItemStateProvider()
                throws RepositoryException {
            if (!isInitialized()) {
                throw new IllegalStateException("workspace '" + getName()
                        + "' not initialized");
            }

            return itemStateMgr;
        }

        /**
         * Returns the observation dispatcher for this workspace
         *
         * @return the observation dispatcher for this workspace
         */
        protected ObservationDispatcher getObservationDispatcher() {
            if (!isInitialized()) {
                throw new IllegalStateException("workspace '" + getName()
                        + "' not initialized");
            }

            return dispatcher;
        }

        /**
         * Returns the search manager for this workspace.
         *
         * @return the search manager for this workspace, or <code>null</code>
         *         if no <code>SearchManager</code>
         * @throws RepositoryException if the search manager could not be created
         */
        protected SearchManager getSearchManager() throws RepositoryException {
            if (!isInitialized()) {
                throw new IllegalStateException("workspace '" + getName()
                        + "' not initialized");
            }

            synchronized (this) {
                if (searchMgr == null) {
                    if (config.getSearchConfig() == null) {
                        // no search index configured
                        return null;
                    }
                    // search manager is lazily instantiated in order to avoid
                    // 'chicken & egg' bootstrap problems
                    searchMgr = new SearchManager(config.getSearchConfig(),
                            nsReg,
                            ntReg,
                            itemStateMgr,
                            persistMgr,
                            rootNodeId,
                            getSystemSearchManager(getName()),
                            SYSTEM_ROOT_NODE_ID);
                }
                return searchMgr;
            }
        }

        /**
         * Returns the lock manager for this workspace.
         *
         * @return the lock manager for this workspace
         * @throws RepositoryException if the lock manager could not be created
         */
        protected LockManager getLockManager() throws RepositoryException {
            if (!isInitialized()) {
                throw new IllegalStateException("workspace '" + getName()
                        + "' not initialized");
            }

            synchronized (this) {
                // lock manager is lazily instantiated in order to avoid
                // 'chicken & egg' bootstrap problems
                if (lockMgr == null) {
                    lockMgr = new LockManagerImpl(getSystemSession(), fs);
                    if (clusterNode != null && config.isClustered()) {
                        lockChannel = clusterNode.createLockChannel(getName());
                        lockMgr.setEventChannel(lockChannel);
                    }
                }
                return lockMgr;
            }
        }

        /**
         * Returns the system session for this workspace.
         *
         * @return the system session for this workspace
         * @throws RepositoryException if the system session could not be created
         */
        protected SystemSession getSystemSession() throws RepositoryException {
            if (!isInitialized()) {
                throw new IllegalStateException("workspace '" + getName()
                        + "' not initialized");
            }

            synchronized (this) {
                // system session is lazily instantiated in order to avoid
                // 'chicken & egg' bootstrap problems
                if (systemSession == null) {
                    systemSession =
                            SystemSession.create(RepositoryImpl.this, config);
                }
                return systemSession;
            }
        }

        /**
         * Initializes this workspace info. The following components are
         * initialized immediately:
         * <ul>
         * <li>file system</li>
         * <li>persistence manager</li>
         * <li>shared item state manager</li>
         * <li>observation manager factory</li>
         * </ul>
         * The following components are initialized lazily (i.e. on demand)
         * in order to save resources and to avoid 'chicken & egg' bootstrap
         * problems:
         * <ul>
         * <li>system session</li>
         * <li>lock manager</li>
         * <li>search manager</li>
         * </ul>
         * @return <code>true</code> if this instance has been successfully
         *         initialized, <code>false</code> if it is already initialized.
         * @throws RepositoryException if an error occurred during the initialization
         */
        final boolean initialize() throws RepositoryException {
            // check initialize status
            try {
                initLock.readLock().acquire();
            } catch (InterruptedException e) {
                throw new RepositoryException("Unable to aquire read lock.", e);
            }
            try {
                if (initialized) {
                    // already initialized, we're done
                    return false;
                }
            } finally {
                initLock.readLock().release();
            }

            // workspace info was not initialized, now check again with write lock
            try {
                initLock.writeLock().acquire();
            } catch (InterruptedException e) {
                throw new RepositoryException("Unable to aquire write lock.", e);
            }
            try {
                if (initialized) {
                    // already initialized, some other thread was quicker, we're done
                    return false;
                }
                log.info("initializing workspace '" + getName() + "'...");
                doInitialize();
                initialized = true;
                log.info("workspace '" + getName() + "' initialized");
                return true;
            } finally {
                initLock.writeLock().release();
            }
        }

        /**
         * Does the actual initialization work. assumes holding write lock.
         * @throws RepositoryException if an error occurs.
         */
        protected void doInitialize() throws RepositoryException {
            fs = config.getFileSystem();

            persistMgr = createPersistenceManager(new File(config.getHomeDir()),
                    fs,
                    config.getPersistenceManagerConfig(),
                    rootNodeId,
                    nsReg,
                    ntReg,
                    dataStore);

            ISMLocking ismLocking = config.getISMLockingConfig().createISMLocking();

            // create item state manager
            try {
                itemStateMgr = createItemStateManager(persistMgr, rootNodeId,
                        ntReg, true, cacheFactory, ismLocking);
                try {
                    itemStateMgr.addVirtualItemStateProvider(
                            vMgr.getVirtualItemStateProvider());
                    itemStateMgr.addVirtualItemStateProvider(
                            virtNTMgr.getVirtualItemStateProvider());
                } catch (Exception e) {
                    log.error("Unable to add vmgr: " + e.toString(), e);
                }
                if (clusterNode != null && config.isClustered()) {
                    updateChannel = clusterNode.createUpdateChannel(getName());
                    itemStateMgr.setEventChannel(updateChannel);
                    updateChannel.setListener(this);
                }
            } catch (ItemStateException ise) {
                String msg = "failed to instantiate shared item state manager";
                log.debug(msg);
                throw new RepositoryException(msg, ise);
            }

            dispatcher = new ObservationDispatcher();

            // register the observation factory of that workspace
            delegatingDispatcher.addDispatcher(dispatcher);
        }

        /**
         * Disposes this <code>WorkspaceInfo</code> if it has been idle for more
         * than <code>maxIdleTime</code> milliseconds.
         *
         * @param maxIdleTime amount of time in mmilliseconds before an idle
         *                    workspace is automatically shutdown.
         */
        final void disposeIfIdle(long maxIdleTime) {
            try {
                initLock.readLock().acquire();
            } catch (InterruptedException e) {
                return;
            }
            try {
                if (!initialized) {
                    return;
                }
                long currentTS = System.currentTimeMillis();
                if (idleTimestamp == 0) {
                    // set idle timestamp
                    idleTimestamp = currentTS;
                } else {
                    if ((currentTS - idleTimestamp) > maxIdleTime) {
                        // temporarily shutdown workspace
                        log.info("disposing workspace '" + getName()
                                + "' which has been idle for "
                                + (currentTS - idleTimestamp) + " ms");
                        dispose();
                    }
                }
            } finally {
                initLock.readLock().release();
            }
        }

        /**
         * Disposes all objects this <code>WorkspaceInfo</code> is holding.
         */
        final void dispose() {
            try {
                initLock.writeLock().acquire();
            } catch (InterruptedException e) {
                throw new IllegalStateException("Unable to aquire write lock.");
            }
            try {
                if (!initialized) {
                    // nothing to dispose of, we're already done
                    return;
                }

                log.info("shutting down workspace '" + getName() + "'...");
                doDispose();
                // reset idle timestamp
                idleTimestamp = 0;

                initialized = false;
                log.info("workspace '" + getName() + "' has been shutdown");
            } finally {
                initLock.writeLock().release();
            }
        }

        /**
         * Does the actual disposal. assumes holding write lock.
         */
        protected void doDispose() {
            // inform cluster node about disposal
            if (updateChannel != null) {
                updateChannel.setListener(null);
            }
            if (lockChannel != null) {
                lockChannel.setListener(null);
            }

            // deregister the observation factory of that workspace
            delegatingDispatcher.removeDispatcher(dispatcher);

            // dispose observation manager factory
            dispatcher.dispose();
            dispatcher = null;

            // shutdown search managers
            if (searchMgr != null) {
                searchMgr.close();
                searchMgr = null;
            }

            // deregister
            if (securityMgr != null) {
                securityMgr.dispose(getName());
            }


            // close system session
            if (systemSession != null) {
                systemSession.removeListener(RepositoryImpl.this);
                systemSession.logout();
                systemSession = null;
            }

            // dispose shared item state manager
            itemStateMgr.dispose();
            itemStateMgr = null;

            // close persistence manager
            try {
                persistMgr.close();
            } catch (Exception e) {
                log.error("error while closing persistence manager of workspace "
                        + config.getName(), e);
            }
            persistMgr = null;

            // close lock manager
            if (lockMgr != null) {
                lockMgr.close();
                lockMgr = null;
            }

            // close workspace file system
            try {
                fs.close();
            } catch (FileSystemException fse) {
                log.error("error while closing file system of workspace "
                        + config.getName(), fse);
            }
            fs = null;
        }

        /**
         * Locks this workspace info. This is used (and only should be) by
         * the {@link XASessionImpl} in order to lock all internal resources
         * during a commit.
         *
         * @throws TransactionException
         */
        void lockAcquire() throws TransactionException {
            try {
                xaLock.acquire();
            } catch (InterruptedException e) {
                throw new TransactionException("Error while acquiering lock", e);
            }

        }

        /**
         * Unlocks this workspace info. This is used (and only should be) by
         * the {@link XASessionImpl} in order to lock all internal resources
         * during a commit.
         */
        void lockRelease() {
            xaLock.release();
        }

        //----------------------------------------------< UpdateEventListener >

        /**
         * {@inheritDoc}
         */
        public void externalUpdate(ChangeLog external, List events) throws RepositoryException {
            try {
                EventStateCollection esc = new EventStateCollection(
                        getObservationDispatcher(), null, null);
                esc.addAll(events);

                getItemStateProvider().externalUpdate(external, esc);
            } catch (IllegalStateException e) {
                String msg = "Unable to deliver events: " + e.getMessage();
                throw new RepositoryException(msg);
            }
        }

    }

    /**
     * The workspace janitor thread that will shutdown workspaces that have
     * been idle for a certain amount of time.
     */
    private class WorkspaceJanitor implements Runnable {

        /**
         * amount of time in milliseconds before an idle workspace is
         * automatically shutdown.
         */
        private long maxIdleTime;

        /**
         * interval in milliseconds between checks for idle workspaces.
         */
        private long checkInterval;

        /**
         * Creates a new <code>WorkspaceJanitor</code> instance responsible for
         * shutting down idle workspaces.
         *
         * @param maxIdleTime amount of time in milliseconds before an idle
         *                    workspace is automatically shutdown.
         */
        WorkspaceJanitor(long maxIdleTime) {
            this.maxIdleTime = maxIdleTime;
            // compute check interval as 10% of maxIdleTime
            checkInterval = (long) (0.1 * maxIdleTime);
        }

        /**
         * {@inheritDoc}
         * <p/>
         * Performs the following tasks in a <code>while (true)</code> loop:
         * <ol>
         * <li>wait for <code>checkInterval</code> milliseconds</li>
         * <li>build list of initialized but currently inactive workspaces
         *     (excluding the default workspace)</li>
         * <li>shutdown those workspaces that have been idle for at least
         *     <code>maxIdleTime</code> milliseconds</li>
         * </ol>
         */
        public void run() {
            while (true) {
                synchronized (RepositoryImpl.this) {
                    try {
                        RepositoryImpl.this.wait(checkInterval);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                    if (disposed) {
                        return;
                    }
                }
                // get names of workspaces
                Set wspNames;
                synchronized (wspInfos) {
                    wspNames = new HashSet(wspInfos.keySet());
                }
                // remove default workspace (will never be shutdown when idle)
                wspNames.remove(repConfig.getDefaultWorkspaceName());

                synchronized (activeSessions) {
                    // remove workspaces with active sessions
                    for (Iterator it = activeSessions.values().iterator(); it.hasNext();) {
                        SessionImpl ses = (SessionImpl) it.next();
                        wspNames.remove(ses.getWorkspace().getName());
                    }
                }

                // remaining names denote workspaces which currently have not
                // active sessions
                for (Iterator it = wspNames.iterator(); it.hasNext();) {
                    WorkspaceInfo wspInfo;
                    synchronized (wspInfos) {
                        wspInfo = (WorkspaceInfo) wspInfos.get(it.next());
                    }
                    wspInfo.disposeIfIdle(maxIdleTime);
                }
            }
        }
    }

    /**
     * Cluster context passed to a <code>ClusterNode</code>.
     */
    class ExternalEventListener implements ClusterContext {

        /**
         * {@inheritDoc}
         */
        public ClusterConfig getClusterConfig() {
            return getConfig().getClusterConfig();
        }

        /**
         * {@inheritDoc}
         */
        public File getRepositoryHome() {
            return new File(getConfig().getHomeDir());
        }

        /**
         * {@inheritDoc}
         */
        public NamespaceResolver getNamespaceResolver() {
            return new RegistryNamespaceResolver(getNamespaceRegistry());
        }

        /**
         * {@inheritDoc}
         */
        public void updateEventsReady(String workspace) throws RepositoryException {
            // toggle the initialization of some workspace
            getWorkspaceInfo(workspace);
        }

        /**
         * {@inheritDoc}
         */
        public void lockEventsReady(String workspace) throws RepositoryException {
            // toggle the initialization of some workspace's lock manager
            getWorkspaceInfo(workspace).getLockManager();
        }

        /**
         * {@inheritDoc}
         */
        public DataStore getDataStore() {
            return RepositoryImpl.this.getDataStore();
        }
    }
}
