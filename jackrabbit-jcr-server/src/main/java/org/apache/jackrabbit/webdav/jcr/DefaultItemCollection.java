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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.jcr.AccessDeniedException;
import javax.jcr.ImportUUIDBehavior;
import javax.jcr.Item;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.lock.Lock;
import javax.jcr.nodetype.NodeType;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.server.io.IOUtil;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.webdav.DavCompliance;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavResourceIterator;
import org.apache.jackrabbit.webdav.DavResourceIteratorImpl;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.io.InputContext;
import org.apache.jackrabbit.webdav.io.OutputContext;
import org.apache.jackrabbit.webdav.jcr.lock.JcrActiveLock;
import org.apache.jackrabbit.webdav.jcr.lock.SessionScopedLockEntry;
import org.apache.jackrabbit.webdav.jcr.nodetype.NodeTypeProperty;
import org.apache.jackrabbit.webdav.jcr.property.ValuesProperty;
import org.apache.jackrabbit.webdav.jcr.version.report.ExportViewReport;
import org.apache.jackrabbit.webdav.jcr.version.report.LocateCorrespondingNodeReport;
import org.apache.jackrabbit.webdav.lock.ActiveLock;
import org.apache.jackrabbit.webdav.lock.LockInfo;
import org.apache.jackrabbit.webdav.lock.Scope;
import org.apache.jackrabbit.webdav.lock.Type;
import org.apache.jackrabbit.webdav.ordering.OrderPatch;
import org.apache.jackrabbit.webdav.ordering.OrderingConstants;
import org.apache.jackrabbit.webdav.ordering.OrderingResource;
import org.apache.jackrabbit.webdav.ordering.OrderingType;
import org.apache.jackrabbit.webdav.ordering.Position;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.property.HrefProperty;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * <code>DefaultItemCollection</code> represents a JCR node item.
 */
public class DefaultItemCollection extends AbstractItemResource
        implements OrderingResource {

    private static Logger log = LoggerFactory.getLogger(DefaultItemCollection.class);
    private static final String TMP_PREFIX = "_tmp_";

    /**
     * Create a new <code>DefaultItemCollection</code>.
     *
     * @param locator
     * @param session
     */
    protected DefaultItemCollection(DavResourceLocator locator,
                                    JcrDavSession session,
                                    DavResourceFactory factory, Item item) {
        super(locator, session, factory, item);
        if (exists() && !(item instanceof Node)) {
            throw new IllegalArgumentException("A collection resource can not be constructed from a Property item.");
        }
    }

    //----------------------------------------------< DavResource interface >---
    /**
     * @see org.apache.jackrabbit.webdav.DavResource#getComplianceClass()
     */
    public String getComplianceClass() {
        String cc = super.getComplianceClass();
        if (isOrderable()) {
            return DavCompliance.concatComplianceClasses(
                new String[] {
                    cc,
                    DavCompliance.ORDERED_COLLECTIONS,
                }
            );
        } else {
            return cc;
        }
    }

    public long getModificationTime() {
        // retrieve mod-time from jcr:lastmodified property if existing
        if (exists()) {
            try {
                if (((Node)item).hasProperty(JcrConstants.JCR_LASTMODIFIED)) {
                    return ((Node)item).getProperty(JcrConstants.JCR_LASTMODIFIED).getLong();
                }
            } catch (RepositoryException e) {
                log.warn("Error while accessing jcr:lastModified property");
            }
        }
        // fallback: return 'now'
        return new Date().getTime();
    }

    /**
     * @see org.apache.jackrabbit.webdav.DavResource#getSupportedMethods()
     */
    public String getSupportedMethods() {
        String ms = super.getSupportedMethods();
        if (isOrderable()) {
            StringBuffer sb = new StringBuffer(ms);
            sb.append(", ").append(OrderingResource.METHODS);
            return sb.toString();
        } else {
            return ms;
        }
    }

    /**
     * Always returns true
     *
     * @return true
     * @see org.apache.jackrabbit.webdav.DavResource#isCollection()
     */
    public boolean isCollection() {
        return true;
    }

    /**
     * If this resource represents an existing <code>Node</code> the system
     * view is spooled as resource content.
     *
     * @param outputContext
     * @throws IOException
     * @see Session#exportSystemView(String, OutputStream, boolean, boolean)
     */
    public void spool(OutputContext outputContext) throws IOException {
        // spool properties
        super.spool(outputContext);
        // spool data
        try {
            OutputStream out = outputContext.getOutputStream();
            if (out != null && exists()) {
                getRepositorySession().exportSystemView(item.getPath(), out, false, true);
            }
        } catch (PathNotFoundException e) {
            log.error("Error while spooling resource content: " + e.getMessage());
        } catch (RepositoryException e) {
            log.error("Error while spooling resource content: " + e.getMessage());
        }
    }

    /**
     * This implementation of the <code>DavResource</code> does only allow
     * to set the mixinnodetypes property. Please note that the existing list of
     * mixin nodetypes will be completely replaced.<br>
     * In order to add / set any other repository property on the underlying
     * {@link javax.jcr.Node} use <code>addMember(DavResource)</code> or
     * <code>addMember(DavResource, InputStream)</code> or modify the value
     * of the corresponding resource.
     *
     * @param property
     * @throws org.apache.jackrabbit.webdav.DavException
     * @see org.apache.jackrabbit.webdav.DavResource#setProperty(org.apache.jackrabbit.webdav.property.DavProperty)
     * @see #JCR_MIXINNODETYPES
     */
    public void setProperty(DavProperty property) throws DavException {
        internalSetProperty(property);
        complete();
    }

    /**
     * Internal method used to set or add the given property
     *
     * @param property
     * @throws DavException
     * @see #setProperty(DavProperty)
     * @see #alterProperties(DavPropertySet, DavPropertyNameSet)
     */
    private void internalSetProperty(DavProperty property) throws DavException {
        if (!exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND);
        }
        if (property.getName().equals(JCR_MIXINNODETYPES)) {
            Node n = (Node)item;
            try {
                NodeType[] existingMixin = n.getMixinNodeTypes();
                NodeTypeProperty mix = new NodeTypeProperty(property);
                Set mixins = mix.getNodeTypeNames();

                for (int i = 0; i < existingMixin.length; i++) {
                    String name = existingMixin[i].getName();
                    if (mixins.contains(name)){
                        // do not add existing mixins
                        mixins.remove(name);
                    } else {
                        // remove mixin that are not contained in the new list
                        n.removeMixin(name);
                    }
                }

                // add the remaining mixing types that are not yet set
                Iterator it = mixins.iterator();
                while (it.hasNext()) {
                    n.addMixin((String)it.next());
                }
            } catch (RepositoryException e) {
                throw new JcrDavException(e);
            }
        } else {
            // all props except for mixinnodetypes are read-only
            throw new DavException(DavServletResponse.SC_CONFLICT);
        }
    }

    /**
     * This implementation of the <code>DavResource</code> does only allow
     * to remove the mixinnodetypes property.
     *
     * @param propertyName
     * @throws org.apache.jackrabbit.webdav.DavException
     * @see org.apache.jackrabbit.webdav.DavResource#removeProperty(org.apache.jackrabbit.webdav.property.DavPropertyName)
     * @see #JCR_MIXINNODETYPES
     */
    public void removeProperty(DavPropertyName propertyName) throws DavException {
        internalRemoveProperty(propertyName);
        complete();
    }

    /**
     * Internal method used to remove the property with the given name.
     *
     * @param propertyName
     * @throws DavException
     * @see #removeProperty(DavPropertyName)
     * @see #alterProperties(DavPropertySet, DavPropertyNameSet)
     */
    private void internalRemoveProperty(DavPropertyName propertyName) throws DavException {
        if (!exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND);
        }
        if (JCR_MIXINNODETYPES.equals(propertyName)) {
            // remove all mixin nodetypes
            try {
                Node n = (Node)item;
                NodeType[] mixins = n.getMixinNodeTypes();
                for (int i = 0; i < mixins.length; i++) {
                    n.removeMixin(mixins[i].getName());
                }
            } catch (RepositoryException e) {
                // NoSuchNodeTypeException, ConstraintViolationException should never occur...
                throw new JcrDavException(e);
            }
        } else {
            // all props except for mixinnodetypes are read-only
            throw new DavException(DavServletResponse.SC_CONFLICT);
        }
    }

    /**
     * Loops over the given <code>List</code>s and alters the properties accordingly.
     * Changes are persisted at the end according to the rules defined with
     * the {@link AbstractItemResource#complete()} method.<p>
     * Please note: since there is only a single property
     * ({@link ItemResourceConstants#JCR_MIXINNODETYPES}
     * that can be set or removed with PROPPATCH, this method either succeeds
     * or throws an exception, even if this violates RFC 2518. Thus no property
     * specific multistatus will be created in case of an error.
     *
     * @param changeList
     * @return
     * @throws DavException
     * @see DavResource#alterProperties(org.apache.jackrabbit.webdav.property.DavPropertySet, org.apache.jackrabbit.webdav.property.DavPropertyNameSet)
     */
    public MultiStatusResponse alterProperties(List changeList) throws DavException {
        Iterator it = changeList.iterator();
        while (it.hasNext()) {
            Object propEntry = it.next();
            if (propEntry instanceof DavPropertyName) {
                // use the internal remove method in order to prevent premature 'save'
                DavPropertyName propName = (DavPropertyName) propEntry;
                internalRemoveProperty(propName);
            } else if (propEntry instanceof DavProperty) {
                // use the internal set method in order to prevent premature 'save'
                DavProperty prop = (DavProperty) propEntry;
                internalSetProperty(prop);
            } else {
                throw new IllegalArgumentException("unknown object in change list: " + propEntry.getClass().getName());
            }
        }
        // TODO: missing undo of successful set/remove if subsequent operation fails
        // NOTE, that this is relevant with transactions only.

        // success: save all changes together if no error occured
        complete();
        return new MultiStatusResponse(getHref(), DavServletResponse.SC_OK);
    }

    /**
     * If the specified resource represents a collection, a new node is {@link Node#addNode(String)
     * added} to the item represented by this resource. If an input stream is specified
     * together with a collection resource {@link Session#importXML(String, java.io.InputStream, int)}
     * is called instead and this resource path is used as <code>parentAbsPath</code> argument.
     * <p/>
     * However, if the specified resource is not of resource type collection a
     * new {@link Property} is set or an existing one is changed by modifying its
     * value.<br>
     * NOTE: with the current implementation it is not possible to create or
     * modify multivalue JCR properties.<br>
     * NOTE: if the JCR property represented by the specified resource has an
     * {@link PropertyType#UNDEFINED undefined} resource type, its value will be
     * changed/set to type {@link PropertyType#BINARY binary}.
     *
     * @param resource
     * @param inputContext
     * @throws org.apache.jackrabbit.webdav.DavException
     * @see org.apache.jackrabbit.webdav.DavResource#addMember(org.apache.jackrabbit.webdav.DavResource, InputContext)
     * @see Node#addNode(String)
     * @see Node#setProperty(String, java.io.InputStream)
     */
    public void addMember(DavResource resource, InputContext inputContext)
            throws DavException {

        /* RFC 2815 states that all 'parents' must exist in order all addition of members */
        if (!exists()) {
            throw new DavException(DavServletResponse.SC_CONFLICT);
        }

        File tmpFile = null;
        try {
            Node n = (Node) item;
            InputStream in = (inputContext != null) ? inputContext.getInputStream() : null;
            String itemPath = getLocator().getRepositoryPath();
            String memberName = getItemName(resource.getLocator().getRepositoryPath());
            if (resource.isCollection()) {
                if (in == null) {
                    // MKCOL without a request body, try if a default-primary-type is defined.
                    n.addNode(memberName);
                } else {
                    // MKCOL, which is not allowed for existing resources
                    int uuidBehavior = ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW;
                    String str = inputContext.getProperty(IMPORT_UUID_BEHAVIOR);
                    if (str != null) {
                        try {
                            uuidBehavior = Integer.parseInt(str);
                        } catch (NumberFormatException e) {
                            throw new DavException(DavServletResponse.SC_BAD_REQUEST);
                        }
                    }
                    if (getTransactionId() == null) {
                        // if not part of a transaction directely import on workspace
                        // since changes would be explicitely saved in the
                        // complete-call.
                        getRepositorySession().getWorkspace().importXML(itemPath, in, uuidBehavior);
                    } else {
                        // changes will not be persisted unless the tx is completed.
                        getRepositorySession().importXML(itemPath, in, uuidBehavior);
                    }
                }
            } else {
                if (in == null) {
                    // PUT: not possible without request body
                    throw new DavException(DavServletResponse.SC_BAD_REQUEST, "Cannot create a new non-collection resource without request body.");
                }
                // PUT : create new or overwrite existing property.
                String ct = inputContext.getContentType();
                int type = JcrValueType.typeFromContentType(ct);
                if (type != PropertyType.UNDEFINED) {
                    // no need to create value/values property. instead
                    // prop-value can be retrieved directly:
                    int pos = ct.indexOf(';');
                    String charSet = (pos > -1) ? ct.substring(pos) : "UTF-8";
                    if (type == PropertyType.BINARY) {
                        n.setProperty(memberName, inputContext.getInputStream());
                    } else {
                        BufferedReader r = new BufferedReader(new InputStreamReader(inputContext.getInputStream(), charSet));
                        String line;
                        StringBuffer value = new StringBuffer();
                        while ((line = r.readLine()) != null) {
                            value.append(line);
                        }
                        n.setProperty(memberName, value.toString(), type);
                    }
                } else {
                    // try to parse the request body into a 'values' property.
                    tmpFile = File.createTempFile(TMP_PREFIX + Text.escape(memberName), null, null);
                    FileOutputStream out = new FileOutputStream(tmpFile);
                    IOUtil.spool(in, out);
                    out.close();
                    // try to parse the request body into a 'values' property.
                    ValuesProperty vp = buildValuesProperty(new FileInputStream(tmpFile));
                    if (vp != null) {
                        if (JCR_VALUE.equals(vp.getName())) {
                            n.setProperty(memberName, vp.getJcrValue());
                        } else {
                            n.setProperty(memberName, vp.getJcrValues());
                        }
                    } else {
                        // request body cannot be parsed into a 'values' property.
                        // fallback: try to import as single value from stream.
                        n.setProperty(memberName, new FileInputStream(tmpFile));
                    }
                }
            }
            if (resource.exists() && resource instanceof AbstractItemResource) {
                // PUT may modify value of existing jcr property. thus, this
                // node is not modified by the 'addMember' call.
                ((AbstractItemResource)resource).complete();
            } else {
                complete();
            }
        } catch (ItemExistsException e) {
            // according to RFC 2518: MKCOL only possible on non-existing/deleted resource
            throw new JcrDavException(e, DavServletResponse.SC_METHOD_NOT_ALLOWED);
        } catch (RepositoryException e) {
            throw new JcrDavException(e);
        } catch (IOException e) {
            throw new DavException(DavServletResponse.SC_UNPROCESSABLE_ENTITY, e.getMessage());
        } finally {
            if (tmpFile != null) {
                tmpFile.delete();
            }
        }
    }

    /**
     * @see org.apache.jackrabbit.webdav.DavResource#getMembers()
     */
    public DavResourceIterator getMembers() {
        ArrayList memberList = new ArrayList();
        if (exists()) {
            try {
                Node n = (Node)item;
                // add all node members
                NodeIterator it = n.getNodes();
                while (it.hasNext()) {
                    Node node = it.nextNode();
                    DavResourceLocator loc = getLocatorFromItem(node);
                    memberList.add(createResourceFromLocator(loc));
                }
                // add all property members
                PropertyIterator propIt = n.getProperties();
                while (propIt.hasNext()) {
                    Property prop = propIt.nextProperty();
                    DavResourceLocator loc = getLocatorFromItem(prop);
                    memberList.add(createResourceFromLocator(loc));
                }
            } catch (RepositoryException e) {
                // ignore
                log.error(e.getMessage());
            } catch (DavException e) {
                // should never occur.
                log.error(e.getMessage());
            }
        }
        return new DavResourceIteratorImpl(memberList);
    }

    /**
     * Removes the repository item represented by the specified member
     * resource.
     *
     * @throws DavException if this resource does not exist or if an error occurs
     * while deleting the underlying item.
     * @see DavResource#removeMember(DavResource)
     * @see javax.jcr.Item#remove()
     */
    public void removeMember(DavResource member) throws DavException {
        Session session = getRepositorySession();
        try {
            String itemPath = member.getLocator().getRepositoryPath();
            if (!exists() || !session.itemExists(itemPath)) {
                throw new DavException(DavServletResponse.SC_NOT_FOUND);
            }
            if (!getResourcePath().equals(Text.getRelativeParent(member.getResourcePath(), 1))) {
                throw new DavException(DavServletResponse.SC_CONFLICT, member.getResourcePath() + "is not member of this resource (" + getResourcePath() + ")");
            }
            getRepositorySession().getItem(itemPath).remove();
            complete();
        } catch (RepositoryException e) {
            log.error("Unexpected error: " + e.getMessage());
            throw new JcrDavException(e);
        }
    }

    /**
     * @param type
     * @param scope
     * @return true if a lock with the specified type and scope is present on
     * this resource, false otherwise. If retrieving the corresponding information
     * fails, false is returned.
     * @see org.apache.jackrabbit.webdav.DavResource#hasLock(org.apache.jackrabbit.webdav.lock.Type, org.apache.jackrabbit.webdav.lock.Scope)
     */
    public boolean hasLock(Type type, Scope scope) {
        if (isLockable(type, scope)) {
            if (Type.WRITE.equals(type)) {
                try {
                    return ((Node) item).isLocked();
                } catch (RepositoryException e) {
                    log.error(e.getMessage());
                }
            } else {
                return super.hasLock(type, scope);
            }
        }
        return false;
    }

    /**
     * Retrieve the lock with the specified type and scope.
     *
     * @param type
     * @param scope
     * @return lock with the specified type and scope is present on this
     * resource or <code>null</code>. NOTE: If retrieving the write lock present
     * on the underlying repository item fails, <code>null</code> is return.
     * @see org.apache.jackrabbit.webdav.DavResource#getLock(org.apache.jackrabbit.webdav.lock.Type, org.apache.jackrabbit.webdav.lock.Scope)
     * @see javax.jcr.Node#getLock() for the write locks.
     */
    public ActiveLock getLock(Type type, Scope scope) {
        ActiveLock lock = null;
        if (Type.WRITE.equals(type)) {
            try {
                if (!exists()) {
                    log.warn("Unable to retrieve lock: no item found at '" + getResourcePath() + "'");
                } else if (((Node) item).isLocked()) {
                    Lock jcrLock = ((Node) item).getLock();
                    lock = new JcrActiveLock(jcrLock);
                }
            } catch (AccessDeniedException e) {
                log.error("Error while accessing resource lock: "+e.getMessage());
            } catch (UnsupportedRepositoryOperationException e) {
                log.error("Error while accessing resource lock: "+e.getMessage());
            } catch (RepositoryException e) {
                log.error("Error while accessing resource lock: "+e.getMessage());
            }
        } else {
            lock = super.getLock(type, scope);
        }
        return lock;
    }

    /**
     * Creates a lock on this resource by locking the underlying
     * {@link javax.jcr.Node node}. Except for the {@link org.apache.jackrabbit.webdav.lock.LockInfo#isDeep()} }
     * all information included in the <code>LockInfo</code> object is ignored.
     * Lock timeout is defined by JCR implementation.
     *
     * @param reqLockInfo
     * @return lock object representing the lock created on this resource.
     * @throws org.apache.jackrabbit.webdav.DavException
     * @see org.apache.jackrabbit.webdav.DavResource#lock(org.apache.jackrabbit.webdav.lock.LockInfo)
     * @see Node#lock(boolean, boolean)
     */
    public ActiveLock lock(LockInfo reqLockInfo) throws DavException {

        if (!isLockable(reqLockInfo.getType(), reqLockInfo.getScope())) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED);
        }

        if (Type.WRITE.equals(reqLockInfo.getType())) {
            if (!exists()) {
                log.warn("Cannot create a write lock for non-existing JCR node (" + getResourcePath() + ")");
                throw new DavException(DavServletResponse.SC_NOT_FOUND);
            }
            try {
                boolean sessionScoped = EXCLUSIVE_SESSION.equals(reqLockInfo.getScope());
                Lock jcrLock = ((Node)item).lock(reqLockInfo.isDeep(), sessionScoped);
                // add reference to DAVSession for this lock
                getSession().addReference(jcrLock.getLockToken());
                return new JcrActiveLock(jcrLock, sessionScoped);

            } catch (RepositoryException e) {
                // UnsupportedRepositoryOperationException should not occur...
                throw new JcrDavException(e);
            }
        } else {
            return super.lock(reqLockInfo);
        }
    }

    /**
     * Refreshes the lock on this resource. With this implementation the
     * {@link javax.jcr.lock lock} present on the underlying {@link javax.jcr.Node node}
     * is refreshed. The timeout indicated by the <code>LockInfo</code>
     * object is ignored.
     *
     * @param reqLockInfo LockInfo as build from the request.
     * @param lockToken
     * @return the updated lock info object.
     * @throws org.apache.jackrabbit.webdav.DavException in case the lock could not be refreshed.
     * @see org.apache.jackrabbit.webdav.DavResource#refreshLock(org.apache.jackrabbit.webdav.lock.LockInfo, String)
     * @see javax.jcr.lock.Lock#refresh()
     */
    public ActiveLock refreshLock(LockInfo reqLockInfo, String lockToken)
            throws DavException {

        if (lockToken == null) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED);
        }

        ActiveLock lock = getLock(reqLockInfo.getType(), reqLockInfo.getScope());
        if (lock == null) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED, "No lock with the given scope/type present on this resource.");
        }

        if (Type.WRITE.equals(lock.getType())) {
            try {
                Lock jcrLock = ((Node) item).getLock();
                jcrLock.refresh();
                return new JcrActiveLock(jcrLock, EXCLUSIVE_SESSION.equals(lock.getScope()));
            } catch (RepositoryException e) {
                /*
                  NOTE: LockException is only thrown by Lock.refresh()
                        the lock exception thrown by Node.getLock() was circumvented
                        by the init test if there is a lock applied...
                  NOTE: UnsupportedRepositoryOperationException should not occur
                */
                throw new JcrDavException(e);
            }
        } else {
            return super.refreshLock(reqLockInfo, lockToken);
        }
    }

    /**
     * Remove the write lock from this resource by unlocking the underlying
     * {@link javax.jcr.Node node}.
     *
     * @param lockToken
     * @throws org.apache.jackrabbit.webdav.DavException
     * @see org.apache.jackrabbit.webdav.DavResource#unlock(String)
     * @see javax.jcr.Node#unlock()
     */
    public void unlock(String lockToken) throws DavException {
        ActiveLock lock = getWriteLock();
        if (lock != null && lockToken.equals(lock.getToken())) {
            try {
                ((Node) item).unlock();
            } catch (RepositoryException e) {
                throw new JcrDavException(e);
            }
        } else {
            super.unlock(lockToken);
        }
    }

    /**
     * Returns the write lock present on this resource or <code>null</code> if
     * no write lock exists. NOTE: that the scope of a write lock may either
     * be {@link org.apache.jackrabbit.webdav.lock.Scope#EXCLUSIVE} or
     * {@link ItemResourceConstants#EXCLUSIVE_SESSION}.
     *
     * @return write lock or <code>null</code>
     * @throws DavException if this resource does not represent a repository item.
     */
    private ActiveLock getWriteLock() throws DavException {
        if (!exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND, "Unable to retrieve write lock for non existing repository item (" + getResourcePath() + ")");
        }
        ActiveLock writeLock = getLock(Type.WRITE, Scope.EXCLUSIVE);
        if (writeLock == null) {
            writeLock = getLock(Type.WRITE, EXCLUSIVE_SESSION);
        }
        return writeLock;
    }

    //-----------------------------------------< OrderingResource interface >---
    /**
     * Returns true if this resource exists and the nodetype defining the
     * underlying repository node allow to reorder this nodes children.
     *
     * @return true if {@link DefaultItemCollection#orderMembers(OrderPatch)}
     * can be called on this resource.
     * @see org.apache.jackrabbit.webdav.ordering.OrderingResource#isOrderable()
     * @see javax.jcr.nodetype.NodeType#hasOrderableChildNodes()
     */
    public boolean isOrderable() {
        boolean orderable = false;
        if (exists()) {
            try {
                orderable = ((Node) item).getPrimaryNodeType().hasOrderableChildNodes();
            } catch (RepositoryException e) {
                log.warn(e.getMessage());
            }
        }
        return orderable;
    }

    /**
     * Reorder the child nodes of the repository item represented by this
     * resource as indicated by the specified {@link OrderPatch} object.
     *
     * @param orderPatch
     * @throws org.apache.jackrabbit.webdav.DavException
     * @see org.apache.jackrabbit.webdav.ordering.OrderingResource#orderMembers(org.apache.jackrabbit.webdav.ordering.OrderPatch)
     * @see Node#orderBefore(String, String)
     */
    public void orderMembers(OrderPatch orderPatch) throws DavException {
        if (!isOrderable()) {
            throw new DavException(DavServletResponse.SC_METHOD_NOT_ALLOWED);
        }
        // only custom ordering is allowed
        if (!OrderingConstants.ORDERING_TYPE_CUSTOM.equalsIgnoreCase(orderPatch.getOrderingType())) {
            throw new DavException(DavServletResponse.SC_UNPROCESSABLE_ENTITY, "Only DAV:custom ordering type supported.");
        }

        OrderPatch.Member[] instructions = orderPatch.getOrderInstructions();
        Node n = (Node)item;
        try {
            for (int i = 0; i < instructions.length; i++) {
                String srcRelPath = Text.unescape(instructions[i].getMemberHandle());
                Position pos = instructions[i].getPosition();
                String destRelPath = getRelDestinationPath(pos, n.getNodes());
                // preform the reordering
                n.orderBefore(srcRelPath, destRelPath);
            }
            complete();
        } catch (RepositoryException e) {
            // UnsupportedRepositoryException should not occur
            throw new JcrDavException(e);
        }
    }

    /**
     * Retrieve the relative path of the child node that acts as destination.
     * A <code>null</code> destination path is used to place the child node indicated
     * by the source path at the end of the list.
     *
     * @param position
     * @param childNodes
     * @return the relative path of the child node used as destination or <code>null</code>
     * if the source node should be placed at the last position.
     * @throws javax.jcr.RepositoryException
     */
    private String getRelDestinationPath(Position position, NodeIterator childNodes)
            throws RepositoryException {

        String destRelPath = null;
        if (OrderingConstants.XML_FIRST.equals(position.getType())) {
            if (childNodes.hasNext()) {
                Node firstChild = childNodes.nextNode();
                // use last segment of node-path instead of name.
                destRelPath = Text.getName(firstChild.getPath());
            }
            // no child nodes available > reordering to 'first' position fails.
            if (destRelPath == null) {
                throw new ItemNotFoundException("No 'first' item found for reordering.");
            }
        } else if (OrderingConstants.XML_AFTER.equals(position.getType())) {
            String afterRelPath = position.getSegment();
            boolean found = false;
            // jcr only knows order-before > retrieve the node that follows the
            // one incidated by the 'afterRelPath'.
            while (childNodes.hasNext() && destRelPath == null) {
                // compare to last segment of node-path instead of name.
                String childRelPath = Text.getName(childNodes.nextNode().getPath());
                if (found) {
                    destRelPath = childRelPath;
                } else {
                    found = afterRelPath.equals(childRelPath);
                }
            }
        } else {
            // before or last. in the latter case the segmet is 'null'
            destRelPath = position.getSegment();
        }
        if (destRelPath != null) {
            destRelPath = Text.unescape(destRelPath);
        }
        return destRelPath;
    }

    //--------------------------------------------------------------------------
    /**
     * Extend the general {@link AbstractResource#supportedLock} field by
     * lock entries specific for this resource: write locks (exclusive or
     * exclusive session-scoped) in case the underlying node has the node
     * type mix:lockable.
     *
     * @see org.apache.jackrabbit.JcrConstants#MIX_LOCKABLE
     */
    protected void initLockSupport() {
        super.initLockSupport();
        // add exclusive write lock if allowed for the given node
        try {
            if (exists() && ((Node)item).isNodeType(JcrConstants.MIX_LOCKABLE)) {
                supportedLock.addEntry(Type.WRITE, Scope.EXCLUSIVE);
                supportedLock.addEntry(new SessionScopedLockEntry());
            }
        } catch (RepositoryException e) {
            log.warn(e.getMessage());
        }
    }

    /**
     * Defines the additional reports supported by this resource (reports
     * specific for resources representing a repository {@link Node node}):
     * <ul>
     * <li>{@link ExportViewReport export view report}</li>
     * <li>{@link LocateCorrespondingNodeReport locate corresponding node report}</li>
     * </ul>
     *
     * @see org.apache.jackrabbit.webdav.version.report.SupportedReportSetProperty
     */
    protected void initSupportedReports() {
        super.initSupportedReports();
        if (exists()) {
            supportedReports.addReportType(ExportViewReport.EXPORTVIEW_REPORT);
            supportedReports.addReportType(LocateCorrespondingNodeReport.LOCATE_CORRESPONDING_NODE_REPORT);
        }
    }

    /**
     * Fill the property set for this resource.
     */
    protected void initProperties() {
        super.initProperties();
        if (exists()) {
            // resource is serialized as system-view (xml)
            properties.add(new DefaultDavProperty(DavPropertyName.GETCONTENTTYPE, "text/xml"));
            Node n = (Node)item;
            // overwrite the default creation date if possible
            try {
                if (n.hasProperty(JcrConstants.JCR_CREATED)) {
                    long creationTime = n.getProperty(JcrConstants.JCR_CREATED).getValue().getLong();
                    properties.add(new DefaultDavProperty(DavPropertyName.CREATIONDATE,
                        DavConstants.creationDateFormat.format(new Date(creationTime))));
                }
            } catch (RepositoryException e) {
                log.warn("Error while accessing jcr:created property");
            }

            // add node-specific resource properties
            try {
                properties.add(new NodeTypeProperty(JCR_PRIMARYNODETYPE, n.getPrimaryNodeType(), false));
                properties.add(new NodeTypeProperty(JCR_MIXINNODETYPES, n.getMixinNodeTypes(), false));
                properties.add(new DefaultDavProperty(JCR_INDEX, new Integer(n.getIndex()), true));
                addHrefProperty(JCR_REFERENCES, n.getReferences(), true);
                if (n.isNodeType(JcrConstants.MIX_REFERENCEABLE)) {
                    properties.add(new DefaultDavProperty(JCR_UUID, n.getUUID(), true));
                }
            } catch (RepositoryException e) {
                log.error("Failed to retrieve primary nodetype property: " + e.getMessage());
            }
            try {
                Item primaryItem = n.getPrimaryItem();
                addHrefProperty(JCR_PRIMARYITEM, new Item[] {primaryItem}, true);
            } catch (ItemNotFoundException e) {
                log.debug("No primary item present on this node '" + getResourcePath() + "'");
            } catch (RepositoryException e) {
                log.error("Error while retrieving primary item: " + e.getMessage());
            }

            // property defined by RFC 3648: this resource always has custom ordering!
            if (isOrderable()) {
                properties.add(new OrderingType(OrderingConstants.ORDERING_TYPE_CUSTOM));
            }
        }
    }

    /**
     * Add a {@link org.apache.jackrabbit.webdav.property.HrefProperty} with the
     * specified property name and values. Each item present in the specified
     * values array is referenced in the resulting property.
     *
     * @param name
     * @param values
     * @param isProtected
     */
    protected void addHrefProperty(DavPropertyName name, Item[] values, boolean isProtected) {
        if (values == null) {
            return;
        }
        String[] pHref = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            pHref[i] = getLocatorFromItem(values[i]).getHref(true);
        }
        properties.add(new HrefProperty(name, pHref, isProtected));
    }

    /**
     * Add a new {@link HrefProperty href property} to the property set, where
     * all items present in the specifed iterator are referenced in the
     * resulting property.
     *
     * @param name
     * @param itemIterator
     * @param isProtected
     * @see #addHrefProperty(DavPropertyName, Item[], boolean)
     */
    protected void addHrefProperty(DavPropertyName name, Iterator itemIterator,
                                   boolean isProtected) {
        ArrayList l = new ArrayList();
        while (itemIterator.hasNext()) {
            l.add(itemIterator.next());
        }
        addHrefProperty(name, (Item[]) l.toArray(new Item[l.size()]), isProtected);
    }

    /**
     * Tries to parse the given input stream as xml document and build a
     * {@link ValuesProperty} out of it.
     *
     * @param in
     * @return values property or 'null' if the given stream cannot be parsed
     * into an XML document or if build the property fails.
     */
    private ValuesProperty buildValuesProperty(InputStream in) {
        String errorMsg = "Cannot parse stream into a 'ValuesProperty'.";
        try {
            Document reqBody = DomUtil.BUILDER_FACTORY.newDocumentBuilder().parse(in);
            DavProperty defaultProp = DefaultDavProperty.createFromXml(reqBody.getDocumentElement());
            ValuesProperty vp = new ValuesProperty(defaultProp, PropertyType.STRING, getRepositorySession().getValueFactory());
            return vp;
        } catch (IOException e) {
            log.debug(errorMsg, e);
        } catch (ParserConfigurationException e) {
            log.debug(errorMsg, e);
        } catch (SAXException e) {
            log.debug(errorMsg, e);
        } catch (DavException e) {
            log.debug(errorMsg, e);
        } catch (RepositoryException e) {
            log.debug(errorMsg, e);
        }
        // cannot parse request body into a 'values' property
        return null;
    }
}
