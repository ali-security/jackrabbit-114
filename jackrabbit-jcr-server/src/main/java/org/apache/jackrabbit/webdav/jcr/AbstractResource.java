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
package org.apache.jackrabbit.webdav.jcr;

import org.apache.jackrabbit.server.io.IOUtil;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavLocatorFactory;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.DavSession;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.DavCompliance;
import org.apache.jackrabbit.webdav.jcr.search.SearchResourceImpl;
import org.apache.jackrabbit.webdav.jcr.transaction.TxLockManagerImpl;
import org.apache.jackrabbit.webdav.jcr.version.report.NodeTypesReport;
import org.apache.jackrabbit.webdav.jcr.version.report.LocateByUuidReport;
import org.apache.jackrabbit.webdav.jcr.version.report.RegisteredNamespacesReport;
import org.apache.jackrabbit.webdav.jcr.version.report.RepositoryDescriptorsReport;
import org.apache.jackrabbit.webdav.lock.ActiveLock;
import org.apache.jackrabbit.webdav.lock.LockDiscovery;
import org.apache.jackrabbit.webdav.lock.LockInfo;
import org.apache.jackrabbit.webdav.lock.LockManager;
import org.apache.jackrabbit.webdav.lock.Scope;
import org.apache.jackrabbit.webdav.lock.SupportedLock;
import org.apache.jackrabbit.webdav.lock.Type;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.property.HrefProperty;
import org.apache.jackrabbit.webdav.property.ResourceType;
import org.apache.jackrabbit.webdav.property.DavPropertyNameIterator;
import org.apache.jackrabbit.webdav.property.DavPropertyIterator;
import org.apache.jackrabbit.webdav.search.QueryGrammerSet;
import org.apache.jackrabbit.webdav.search.SearchInfo;
import org.apache.jackrabbit.webdav.search.SearchResource;
import org.apache.jackrabbit.webdav.transaction.TransactionConstants;
import org.apache.jackrabbit.webdav.transaction.TransactionInfo;
import org.apache.jackrabbit.webdav.transaction.TransactionResource;
import org.apache.jackrabbit.webdav.transaction.TxLockManager;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.DeltaVResource;
import org.apache.jackrabbit.webdav.version.OptionsInfo;
import org.apache.jackrabbit.webdav.version.OptionsResponse;
import org.apache.jackrabbit.webdav.version.SupportedMethodSetProperty;
import org.apache.jackrabbit.webdav.version.report.Report;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.apache.jackrabbit.webdav.version.report.SupportedReportSetProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Item;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * <code>AbstractResource</code> provides functionality common to all
 * resources.
 */
abstract class AbstractResource implements DavResource, TransactionResource,
        DeltaVResource, SearchResource {

    private static Logger log = LoggerFactory.getLogger(AbstractResource.class);

    private static final String COMPLIANCE_CLASSES =
            DavCompliance.concatComplianceClasses(new String[] {
                    DavCompliance._1_,
                    DavCompliance._2_,
                    DavCompliance._3_,
                    DavCompliance.VERSION_CONTROL,
                    DavCompliance.VERSION_HISTORY,
                    DavCompliance.CHECKOUT_IN_PLACE,
                    DavCompliance.LABEL,
                    DavCompliance.MERGE,
                    DavCompliance.UPDATE,
                    DavCompliance.WORKSPACE
            });

    private final DavResourceLocator locator;
    private final JcrDavSession session;
    private final DavResourceFactory factory;

    private TxLockManagerImpl txMgr;
    private String transactionId;

    protected boolean initedProps;
    protected DavPropertySet properties = new DavPropertySet();
    protected SupportedLock supportedLock = new SupportedLock();
    protected SupportedReportSetProperty supportedReports = new SupportedReportSetProperty();

    /**
     * Create a new <code>AbstractResource</code>
     *
     * @param locator
     * @param session
     */
    AbstractResource(DavResourceLocator locator, JcrDavSession session,
                     DavResourceFactory factory) {
        if (session == null) {
            throw new IllegalArgumentException("Creating AbstractItemResource: DavSession must not be null and must provide a JCR session.");
        }
        this.locator = locator;
        this.session = session;
        this.factory = factory;
    }

    /**
     * Returns a string listing the compliance classes for this resource as it
     * is required for the DAV response header. This includes DAV 1, 2 which
     * is supported by all derived classes as well as a subset of the
     * classes defined by DeltaV: version-control, version-history, checkout-in-place,
     * label, merge, update and workspace.<br>
     * Those compliance classes are added as required by RFC3253 since all
     * all resources in the jcr-server support at least the reporting and some
     * basic versioning functionality.
     *
     * @return string listing the compliance classes.
     * @see org.apache.jackrabbit.webdav.DavResource#getComplianceClass()
     */
    public String getComplianceClass() {
        return COMPLIANCE_CLASSES;
    }

    /**
     * @see org.apache.jackrabbit.webdav.DavResource#getLocator()
     */
    public DavResourceLocator getLocator() {
        return locator;
    }

    /**
     * Returns the path of the underlying repository item or the item to
     * be created (PUT/MKCOL). If the resource exists but does not represent
     * a repository item <code>null</code> is returned.
     *
     * @return path of the underlying repository item.
     * @see DavResource#getResourcePath()
     * @see org.apache.jackrabbit.webdav.DavResourceLocator#getResourcePath()
     */
    public String getResourcePath() {
        return locator.getResourcePath();
    }

    /**
     * @see DavResource#getHref()
     * @see DavResourceLocator#getHref(boolean)
     */
    public String getHref() {
        return locator.getHref(isCollection());
    }

    /**
     * @see org.apache.jackrabbit.webdav.DavResource#getPropertyNames()
     */
    public DavPropertyName[] getPropertyNames() {
        return getProperties().getPropertyNames();
    }

    /**
     * @see org.apache.jackrabbit.webdav.DavResource#getProperty(org.apache.jackrabbit.webdav.property.DavPropertyName)
     */
    public DavProperty getProperty(DavPropertyName name) {
        return getProperties().get(name);
    }

    /**
     * @see org.apache.jackrabbit.webdav.DavResource#getProperties()
     */
    public DavPropertySet getProperties() {
        if (!initedProps) {
            initProperties();
        }
        return properties;
    }

    /**
     * Throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     *
     * @param property
     * @throws DavException Always throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     * @see org.apache.jackrabbit.webdav.DavResource#setProperty(org.apache.jackrabbit.webdav.property.DavProperty)
     */
    public void setProperty(DavProperty property) throws DavException {
        throw new DavException(DavServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    /**
     * Throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     *
     * @param propertyName
     * @throws DavException Always throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     * @see org.apache.jackrabbit.webdav.DavResource#removeProperty(org.apache.jackrabbit.webdav.property.DavPropertyName)
     */
    public void removeProperty(DavPropertyName propertyName) throws DavException {
        throw new DavException(DavServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    /**
     * Builds a single List from the properties to set and the properties to
     * remove and delegates the list to {@link AbstractResource#alterProperties(List)};
     *
     * @see DavResource#alterProperties(org.apache.jackrabbit.webdav.property.DavPropertySet, org.apache.jackrabbit.webdav.property.DavPropertyNameSet)
     */
    public MultiStatusResponse alterProperties(DavPropertySet setProperties,
                                               DavPropertyNameSet removePropertyNames)
            throws DavException {
        List changeList = new ArrayList();
        if (removePropertyNames != null) {
            DavPropertyNameIterator it = removePropertyNames.iterator();
            while (it.hasNext()) {
                changeList.add(it.next());
            }
        }
        if (setProperties != null) {
            DavPropertyIterator it = setProperties.iterator();
            while (it.hasNext()) {
                changeList.add(it.next());
            }
        }
        return alterProperties(changeList);
    }

    /**
     * Throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     *
     * @see DavResource#alterProperties(List)
     */
    public MultiStatusResponse alterProperties(List changeList) throws DavException {
        throw new DavException(DavServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    /**
     * Throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     *
     * @param destination
     * @throws DavException Always throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     * @see DavResource#move(org.apache.jackrabbit.webdav.DavResource)
     */
    public void move(DavResource destination) throws DavException {
        throw new DavException(DavServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    /**
     * Throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     *
     * @param destination
     * @param shallow
     * @throws DavException Always throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     * @see DavResource#copy(org.apache.jackrabbit.webdav.DavResource, boolean)
     */
    public void copy(DavResource destination, boolean shallow) throws DavException {
        throw new DavException(DavServletResponse.SC_METHOD_NOT_ALLOWED);
    }


    /**
     * Returns true, if the {@link SupportedLock} property contains an entry
     * with the given type and scope. By default resources allow for {@link org.apache.jackrabbit.webdav.transaction.TransactionConstants.XML_TRANSACTION
     * transaction} lock only.
     *
     * @param type
     * @param scope
     * @return true if this resource may be locked by the given type and scope.
     * @see DavResource#isLockable(org.apache.jackrabbit.webdav.lock.Type, org.apache.jackrabbit.webdav.lock.Scope)
     */
    public boolean isLockable(Type type, Scope scope) {
        return supportedLock.isSupportedLock(type, scope);
    }

    /**
     * Returns true if this resource has a lock applied with the given type and scope.
     *
     * @param type
     * @param scope
     * @return true if this resource has a lock applied with the given type and scope.
     * @see DavResource#hasLock(Type, Scope)
     */
    public boolean hasLock(Type type, Scope scope) {
        return getLock(type, scope) != null;
    }

    /**
     * @see DavResource#getLock(Type, Scope)
     */
    public ActiveLock getLock(Type type, Scope scope) {
        ActiveLock lock = null;
        if (TransactionConstants.TRANSACTION.equals(type)) {
            lock = txMgr.getLock(type, scope, this);
        }
        return lock;
    }

    /**
     * @see DavResource#getLocks()
     * todo improve....
     */
    public ActiveLock[] getLocks() {
        List locks = new ArrayList();
        // tx locks
        ActiveLock l = getLock(TransactionConstants.TRANSACTION, TransactionConstants.LOCAL);
        if (l != null) {
            locks.add(l);
        }
        l = getLock(TransactionConstants.TRANSACTION, TransactionConstants.GLOBAL);
        if (l != null) {
            locks.add(l);
        }
        // write lock (either exclusive or session-scoped).
        l = getLock(Type.WRITE, Scope.EXCLUSIVE);
        if (l != null) {
            locks.add(l);
        } else {
            l = getLock(Type.WRITE, ItemResourceConstants.EXCLUSIVE_SESSION);
            if (l != null) {
                locks.add(l);
            }
        }
        return (ActiveLock[]) locks.toArray(new ActiveLock[locks.size()]);
    }

    /**
     * @see DavResource#lock(org.apache.jackrabbit.webdav.lock.LockInfo)
     */
    public ActiveLock lock(LockInfo reqLockInfo) throws DavException {
        if (isLockable(reqLockInfo.getType(), reqLockInfo.getScope())) {
            return txMgr.createLock(reqLockInfo, this);
        } else {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED);
        }
    }

    /**
     * Only transaction lock may be available on this resource.
     *
     * @param info
     * @param lockToken
     * @throws DavException
     * @see DavResource#refreshLock(org.apache.jackrabbit.webdav.lock.LockInfo, String)
     */
    public ActiveLock refreshLock(LockInfo info, String lockToken) throws DavException {
        return txMgr.refreshLock(info, lockToken, this);
    }

    /**
     * Throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED} since only transaction
     * locks may be present on this resource, that need to be released by calling
     * {@link TransactionResource#unlock(String, org.apache.jackrabbit.webdav.transaction.TransactionInfo)}.
     *
     * @param lockToken
     * @throws DavException Always throws {@link DavServletResponse#SC_METHOD_NOT_ALLOWED}
     */
    public void unlock(String lockToken) throws DavException {
        throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED);
    }

    /**
     * @see DavResource#addLockManager(org.apache.jackrabbit.webdav.lock.LockManager)
     */
    public void addLockManager(LockManager lockMgr) {
        if (lockMgr instanceof TxLockManagerImpl) {
            txMgr = (TxLockManagerImpl) lockMgr;
        }
    }

    /**
     * @see org.apache.jackrabbit.webdav.DavResource#getFactory()
     */
    public DavResourceFactory getFactory() {
        return factory;
    }

    //--------------------------------------------------------------------------
    /**
     * @see org.apache.jackrabbit.webdav.transaction.TransactionResource#getSession()
     * @see org.apache.jackrabbit.webdav.observation.ObservationResource#getSession()
     */
    public DavSession getSession() {
        return session;
    }

    //--------------------------------------< TransactionResource interface >---
    /**
     * @see TransactionResource#init(TxLockManager, String)
     */
    public void init(TxLockManager txMgr, String transactionId) {
        this.txMgr = (TxLockManagerImpl) txMgr;
        this.transactionId = transactionId;
    }

    /**
     * @see TransactionResource#unlock(String, org.apache.jackrabbit.webdav.transaction.TransactionInfo)
     */
    public void unlock(String lockToken, TransactionInfo tInfo) throws DavException {
        txMgr.releaseLock(tInfo, lockToken, this);
    }

    /**
     * @see TransactionResource#getTransactionId()
     */
    public String getTransactionId() {
        return transactionId;
    }

    //-------------------------------------------< DeltaVResource interface >---
    /**
     * @param optionsInfo
     * @return object to be used in the OPTIONS response body or <code>null</code>
     * @see DeltaVResource#getOptionResponse(org.apache.jackrabbit.webdav.version.OptionsInfo)
     */
    public OptionsResponse getOptionResponse(OptionsInfo optionsInfo) {
        OptionsResponse oR = null;
        if (optionsInfo != null) {
            oR = new OptionsResponse();
            // currently only DAV:version-history-collection-set and
            // DAV:workspace-collection-set is supported.
            if (optionsInfo.containsElement(DeltaVConstants.XML_VH_COLLECTION_SET, DeltaVConstants.NAMESPACE)) {
                String[] hrefs = new String[] {
                        getLocatorFromItemPath(ItemResourceConstants.VERSIONSTORAGE_PATH).getHref(true)
                };
                oR.addEntry(DeltaVConstants.XML_VH_COLLECTION_SET, DeltaVConstants.NAMESPACE, hrefs);
            }
            if (optionsInfo.containsElement(DeltaVConstants.XML_WSP_COLLECTION_SET, DeltaVConstants.NAMESPACE)) {
                // workspaces cannot be created anywhere.
                oR.addEntry(DeltaVConstants.XML_WSP_COLLECTION_SET, DeltaVConstants.NAMESPACE, new String[0]);
            }
        }
        return oR;
    }

    /**
     * @param reportInfo
     * @return the requested report
     * @throws DavException
     * @see DeltaVResource#getReport(org.apache.jackrabbit.webdav.version.report.ReportInfo)
     */
    public Report getReport(ReportInfo reportInfo) throws DavException {
        if (reportInfo == null) {
            throw new DavException(DavServletResponse.SC_BAD_REQUEST, "A REPORT request must provide a valid XML request body.");
        }
        if (!exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND);
        }

        if (supportedReports.isSupportedReport(reportInfo)) {
            Report report = ReportType.getType(reportInfo).createReport(this, reportInfo);
            return report;
        } else {
            throw new DavException(DavServletResponse.SC_UNPROCESSABLE_ENTITY, "Unkown report "+ reportInfo.getReportName() +"requested.");
        }
    }

    /**
     * The JCR api does not provide methods to create new workspaces. Calling
     * <code>addWorkspace</code> on this resource will always fail.
     *
     * @param workspace
     * @throws DavException Always throws.
     * @see DeltaVResource#addWorkspace(org.apache.jackrabbit.webdav.DavResource)
     */
    public void addWorkspace(DavResource workspace) throws DavException {
        throw new DavException(DavServletResponse.SC_FORBIDDEN);
    }

    /**
     * Return an array of <code>DavResource</code> objects that are referenced
     * by the property with the specified name.
     *
     * @param hrefPropertyName
     * @return array of <code>DavResource</code>s
     * @throws DavException
     * @see DeltaVResource#getReferenceResources(org.apache.jackrabbit.webdav.property.DavPropertyName)
     */
    public DavResource[] getReferenceResources(DavPropertyName hrefPropertyName) throws DavException {
        DavProperty prop = getProperty(hrefPropertyName);
        if (prop == null || !(prop instanceof HrefProperty)) {
            throw new DavException(DavServletResponse.SC_CONFLICT, "Unknown Href-Property '"+hrefPropertyName+"' on resource "+getResourcePath());
        }

        List hrefs = ((HrefProperty)prop).getHrefs();
        DavResource[] refResources = new DavResource[hrefs.size()];
        Iterator hrefIter = hrefs.iterator();
        int i = 0;
        while (hrefIter.hasNext()) {
            refResources[i] = getResourceFromHref((String)hrefIter.next());
            i++;
        }
        return refResources;
    }

    /**
     * Retrieve the <code>DavResource</code> object that is represented by
     * the given href String.
     *
     * @param href
     * @return <code>DavResource</code> object
     */
    private DavResource getResourceFromHref(String href) throws DavException {
        // build a new locator: remove trailing prefix
        DavResourceLocator locator = getLocator();
        String prefix = locator.getPrefix();
        DavResourceLocator loc = locator.getFactory().createResourceLocator(prefix, href);

        // create a new resource object
        try {
            DavResource res;
            if (getRepositorySession().itemExists(loc.getRepositoryPath())) {
                res = createResourceFromLocator(loc);
            } else {
                throw new DavException(DavServletResponse.SC_NOT_FOUND);
            }
            return res;
        } catch (RepositoryException e) {
            throw new JcrDavException(e);
        }
    }

    //-------------------------------------------< SearchResource interface >---
    /**
     * @return
     * @see org.apache.jackrabbit.webdav.search.SearchResource#getQueryGrammerSet()
     */
    public QueryGrammerSet getQueryGrammerSet() {
        return new SearchResourceImpl(getLocator(), session).getQueryGrammerSet();
    }

    /**
     * @param sInfo
     * @return
     * @throws DavException
     * @see SearchResource#search(org.apache.jackrabbit.webdav.search.SearchInfo)
     */
    public MultiStatus search(SearchInfo sInfo) throws DavException {
        return new SearchResourceImpl(getLocator(), session).search(sInfo);
    }

    //--------------------------------------------------------------------------
    /**
     * Fill the set of default properties
     */
    protected void initProperties() {
        if (getDisplayName() != null) {
            properties.add(new DefaultDavProperty(DavPropertyName.DISPLAYNAME, getDisplayName()));
        }
        if (isCollection()) {
            properties.add(new ResourceType(ResourceType.COLLECTION));
            // Windows XP support
            properties.add(new DefaultDavProperty(DavPropertyName.ISCOLLECTION, "1"));
        } else {
            properties.add(new ResourceType(ResourceType.DEFAULT_RESOURCE));
            // Windows XP support
            properties.add(new DefaultDavProperty(DavPropertyName.ISCOLLECTION, "0"));
        }
        // todo: add etag

        // default last modified
        String lastModified = IOUtil.getLastModified(getModificationTime());
        properties.add(new DefaultDavProperty(DavPropertyName.GETLASTMODIFIED, lastModified));

        // default creation time
        properties.add(new DefaultDavProperty(DavPropertyName.CREATIONDATE, DavConstants.creationDateFormat.format(new Date(0))));

        // supported lock property
        properties.add(supportedLock);

        // set current lock information. If no lock is applied to this resource,
        // an empty lockdiscovery will be returned in the response.
        properties.add(new LockDiscovery(getLocks()));

        properties.add(new SupportedMethodSetProperty(getSupportedMethods().split(",\\s")));

        // DeltaV properties
        properties.add(supportedReports);
        // creator-displayname, comment: not value available from jcr
        properties.add(new DefaultDavProperty(DeltaVConstants.CREATOR_DISPLAYNAME, null, true));
        properties.add(new DefaultDavProperty(DeltaVConstants.COMMENT, null, true));

        // 'workspace' property as defined by RFC 3253
        String workspaceHref = getWorkspaceHref();
        if (workspaceHref != null) {
            properties.add(new HrefProperty(DeltaVConstants.WORKSPACE, workspaceHref, true));
        }
        // name of the jcr workspace
        properties.add(new DefaultDavProperty(ItemResourceConstants.JCR_WORKSPACE_NAME,
                getRepositorySession().getWorkspace().getName()));

        // TODO: required supported-live-property-set
    }

    /**
     * Create a new <code>DavResource</code> from the given locator.
     * @param loc
     * @return new <code>DavResource</code>
     */
    protected DavResource createResourceFromLocator(DavResourceLocator loc)
            throws DavException {
        DavResource res = factory.createResource(loc, session);
        if (res instanceof AbstractResource) {
            ((AbstractResource)res).transactionId = this.transactionId;
        }
        return res;
    }

    /**
     * Build a <code>DavResourceLocator</code> from the given itemPath path.
     *
     * @param itemPath
     * @return a new <code>DavResourceLocator</code>
     * @see DavLocatorFactory#createResourceLocator(String, String, String)
     */
    protected DavResourceLocator getLocatorFromItemPath(String itemPath) {
        DavResourceLocator loc = locator.getFactory().createResourceLocator(locator.getPrefix(), locator.getWorkspacePath(), itemPath, false);
        return loc;
    }

    /**
     * Build a new {@link DavResourceLocator} from the given repository item.
     *
     * @param repositoryItem
     * @return a new locator for the specified item.
     * @see #getLocatorFromItemPath(String)
     */
    protected DavResourceLocator getLocatorFromItem(Item repositoryItem) {
        String itemPath = null;
        try {
            if (repositoryItem != null) {
                itemPath = repositoryItem.getPath();
            }
        } catch (RepositoryException e) {
            // ignore: should not occur
            log.warn(e.getMessage());
        }
        return getLocatorFromItemPath(itemPath);
    }

    /**
     * Shortcut for <code>getSession().getRepositorySession()</code>
     *
     * @return repository session present in the {@link AbstractResource#session}.
     */
    protected Session getRepositorySession() {
        return session.getRepositorySession();
    }

    /**
     * Define the set of locks supported by this resource.
     *
     * @see org.apache.jackrabbit.webdav.lock.SupportedLock
     */
    abstract protected void initLockSupport();

    /**
     * Define the set of reports supported by this resource.
     *
     * @see org.apache.jackrabbit.webdav.version.report.SupportedReportSetProperty
     */
    /**
     * Define the set of reports supported by this resource.
     *
     * @see org.apache.jackrabbit.webdav.version.report.SupportedReportSetProperty
     * @see AbstractResource#initSupportedReports()
     */
    protected void initSupportedReports() {
        if (exists()) {
            supportedReports = new SupportedReportSetProperty(new ReportType[] {
                    ReportType.EXPAND_PROPERTY,
                    NodeTypesReport.NODETYPES_REPORT,
                    LocateByUuidReport.LOCATE_BY_UUID_REPORT,
                    RegisteredNamespacesReport.REGISTERED_NAMESPACES_REPORT,
                    RepositoryDescriptorsReport.REPOSITORY_DESCRIPTORS_REPORT
            });
        }
    }

    /**
     * Retrieve the href of the workspace the current session belongs to.
     *
     * @return href of the workspace
     */
    abstract protected String getWorkspaceHref();

    //--------------------------------------------------------------------------
    /**
     * Register the specified event listener with the observation manager present
     * the repository session.
     *
     * @param listener
     * @param nodePath
     * @throws javax.jcr.RepositoryException
     */
    void registerEventListener(EventListener listener, String nodePath) throws RepositoryException {
        getRepositorySession().getWorkspace().getObservationManager().addEventListener(listener, EListener.ALL_EVENTS, nodePath, true, null, null, false);
    }

    /**
     * Unregister the specified event listener with the observation manager present
     * the repository session.
     *
     * @param listener
     * @throws javax.jcr.RepositoryException
     */
    void unregisterEventListener(EventListener listener) throws RepositoryException {
        getRepositorySession().getWorkspace().getObservationManager().removeEventListener(listener);
    }

    //------------------------------------------------------< inner classes >---
    /**
     * Simple EventListener that creates a new {@link org.apache.jackrabbit.webdav.MultiStatusResponse} object
     * for each event and adds it to the specified {@link org.apache.jackrabbit.webdav.MultiStatus}.
     */
    class EListener implements EventListener {

        private static final int ALL_EVENTS = Event.NODE_ADDED | Event.NODE_REMOVED | Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED | Event.PROPERTY_REMOVED;

        private final DavPropertyNameSet propNameSet;
        private MultiStatus ms;

        EListener(DavPropertyNameSet propNameSet, MultiStatus ms) {
            this.propNameSet = propNameSet;
            this.ms = ms;
        }

        /**
         * @see EventListener#onEvent(javax.jcr.observation.EventIterator)
         */
        public void onEvent(EventIterator events) {
            while (events.hasNext()) {
                try {
                    Event e = events.nextEvent();
                    DavResourceLocator loc = getLocatorFromItemPath(e.getPath());
                    DavResource res = createResourceFromLocator(loc);
                    ms.addResponse(new MultiStatusResponse(res, propNameSet));

                } catch (DavException e) {
                    // should not occur
                    log.error("Error while building MultiStatusResponse from Event: " + e.getMessage());
                } catch (RepositoryException e) {
                    // should not occur
                    log.error("Error while building MultiStatusResponse from Event: " + e.getMessage());
                }
            }
        }
    }
}