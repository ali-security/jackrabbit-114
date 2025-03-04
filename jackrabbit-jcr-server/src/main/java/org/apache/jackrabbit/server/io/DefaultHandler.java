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
package org.apache.jackrabbit.server.io;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.NamespaceHelper;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.PropertyIterator;
import javax.jcr.Session;
import javax.jcr.nodetype.PropertyDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;

/**
 * <code>DefaultHandler</code> implements a simple IOHandler that creates 'file'
 * and 'folder' nodes. This handler will create the following nodes:
 * <ul>
 * <li>New <b>Collection</b>: creates a new node with the {@link #getCollectionNodeType()
 * collection nodetype}. The name of the node corresponds to the systemId
 * present on the import context.</li>
 *
 * <li>New <b>Non-Collection</b>: first creates a new node with the {@link #getNodeType()
 * non-collection nodetype}. The name of the node corresponds to the systemId
 * present on the import context. Below it creates a node with name
 * {@link JcrConstants#JCR_CONTENT jcr:content} and the nodetype specified
 * by {@link #getContentNodeType()}.</li>
 * </ul>
 * <p/>
 * Import of the content:<br>
 * The content is imported to the {@link JcrConstants#JCR_DATA} property of the
 * content node. By default this handler will fail on a attempt to create/replace
 * a collection if {@link ImportContext#hasStream()} is <code>true</code>.
 * Subclasses therefore should provide their own {@link #importData(ImportContext, boolean, Node)
 * importData} method, that handles the data according their needs.
 */
public class DefaultHandler implements IOHandler, PropertyHandler {

    private static Logger log = LoggerFactory.getLogger(DefaultHandler.class);

    private String collectionNodetype = JcrConstants.NT_FOLDER;
    private String defaultNodetype = JcrConstants.NT_FILE;
    /* IMPORTANT NOTE: for webDAV compliance the default nodetype of the content
       node has been changed from nt:resource to nt:unstructured. */
    private String contentNodetype = JcrConstants.NT_UNSTRUCTURED;

    private IOManager ioManager;

    /**
     * Creates a new <code>DefaultHandler</code> with default nodetype definitions
     * and without setting the IOManager.
     *
     * @see IOHandler#setIOManager(IOManager)
     */
    public DefaultHandler() {
    }

    /**
     * Creates a new <code>DefaultHandler</code> with default nodetype definitions:<br>
     * <ul>
     * <li>Nodetype for Collection: {@link JcrConstants#NT_FOLDER nt:folder}</li>
     * <li>Nodetype for Non-Collection: {@link JcrConstants#NT_FILE nt:file}</li>
     * <li>Nodetype for Non-Collection content: {@link JcrConstants#NT_RESOURCE nt:resource}</li>
     * </ul>
     *
     * @param ioManager the I/O manager
     */
    public DefaultHandler(IOManager ioManager) {
        this.ioManager = ioManager;
    }

    /**
     * Creates a new <code>DefaultHandler</code>. Please note that the specified
     * nodetypes must match the definitions of the defaults.
     *
     * @param ioManager
     * @param collectionNodetype
     * @param defaultNodetype
     * @param contentNodetype
     */
    public DefaultHandler(IOManager ioManager, String collectionNodetype, String defaultNodetype, String contentNodetype) {
        this.ioManager = ioManager;

        this.collectionNodetype = collectionNodetype;
        this.defaultNodetype = defaultNodetype;
        this.contentNodetype = contentNodetype;
    }

    /**
     * @see IOHandler#getIOManager()
     */
    public IOManager getIOManager() {
        return ioManager;
    }

    /**
     * @see IOHandler#setIOManager(IOManager)
     */
    public void setIOManager(IOManager ioManager) {
        this.ioManager = ioManager;
    }

    /**
     * @see IOHandler#getName()
     */
    public String getName() {
        return getClass().getName();
    }

    /**
     * @see IOHandler#canImport(ImportContext, boolean)
     */
    public boolean canImport(ImportContext context, boolean isCollection) {
        if (context == null || context.isCompleted()) {
            return false;
        }
        Item contextItem = context.getImportRoot();
        return contextItem != null && contextItem.isNode() && context.getSystemId() != null;
    }

    /**
     * @see IOHandler#canImport(ImportContext, DavResource)
     */
    public boolean canImport(ImportContext context, DavResource resource) {
        if (resource == null) {
            return false;
        }
        return canImport(context, resource.isCollection());
    }

    /**
     * @see IOHandler#importContent(ImportContext, boolean)
     */
    public boolean importContent(ImportContext context, boolean isCollection) throws IOException {
        if (!canImport(context, isCollection)) {
            throw new IOException(getName() + ": Cannot import " + context.getSystemId());
        }

        boolean success = false;
        try {
            Node contentNode = getContentNode(context, isCollection);
            success = importData(context, isCollection, contentNode);
            if (success) {
                success = importProperties(context, isCollection, contentNode);
            }
        } catch (RepositoryException e) {
            success = false;
            throw new IOException(e.getMessage());
        } finally {
            // revert any changes made in case the import failed.
            if (!success) {
                try {
                    context.getImportRoot().refresh(false);
                } catch (RepositoryException e) {
                    throw new IOException(e.getMessage());
                }
            }
        }
        return success;
    }

    /**
     * @see IOHandler#importContent(ImportContext, DavResource)
     */
    public boolean importContent(ImportContext context, DavResource resource) throws IOException {
        if (!canImport(context, resource)) {
            throw new IOException(getName() + ": Cannot import " + context.getSystemId());
        }
        return importContent(context, resource.isCollection());
    }

    /**
     * Imports the data present on the import context to the specified content
     * node.
     *
     * @param context
     * @param isCollection
     * @param contentNode
     * @return
     * @throws IOException
     */
    protected boolean importData(ImportContext context, boolean isCollection, Node contentNode) throws IOException, RepositoryException {
        InputStream in = context.getInputStream();
        if (in != null) {
            // NOTE: with the default folder-nodetype (nt:folder) no inputstream
            // is allowed. setting the property would therefore fail.
            if (isCollection) {
                return false;
            }
            try {
                contentNode.setProperty(JcrConstants.JCR_DATA, in);
            } finally {
                in.close();
            }
        }
        // success if no data to import.
        return true;
    }

    /**
     * Imports the properties present on the specified context to the content
     * node.
     *
     * @param context
     * @param isCollection
     * @param contentNode
     * @return
     */
    protected boolean importProperties(ImportContext context, boolean isCollection, Node contentNode) {
        try {
            // set mimeType property upon resource creation but don't modify
            // it on a subsequent PUT. In contrast to a PROPPATCH request, which
            // is handled by  #importProperties(PropertyContext, boolean)}
            if (!contentNode.hasProperty(JcrConstants.JCR_MIMETYPE)) {
                contentNode.setProperty(JcrConstants.JCR_MIMETYPE, context.getMimeType());
            }
        } catch (RepositoryException e) {
            // ignore: property may not be present on the node
        }
        try {
            // set encoding property upon resource creation but don't modify
            // it on a subsequent PUT. In contrast to a PROPPATCH request, which
            // is handled by  #importProperties(PropertyContext, boolean)}
            if (!contentNode.hasProperty(JcrConstants.JCR_ENCODING)) {
                contentNode.setProperty(JcrConstants.JCR_ENCODING, context.getEncoding());
            }
        } catch (RepositoryException e) {
            // ignore: property may not be present on the node
        }
        setLastModified(contentNode, context.getModificationTime());
        return true;
    }

    /**
     * Retrieves/creates the node that will be used to import properties and
     * data. In case of a non-collection this includes and additional content node
     * to be created beside the 'file' node.<br>
     * Please note: If the jcr:content node already exists and contains child
     * nodes, those will be removed in order to make sure, that the import
     * really replaces the existing content of the file-node.
     *
     * @param context
     * @param isCollection
     * @return
     * @throws RepositoryException
     */
    protected Node getContentNode(ImportContext context, boolean isCollection) throws RepositoryException {
        Node parentNode = (Node)context.getImportRoot();
        String name = context.getSystemId();
        if (parentNode.hasNode(name)) {
            parentNode = parentNode.getNode(name);
        } else {
            String ntName = (isCollection) ? getCollectionNodeType() : getNodeType();
            parentNode = parentNode.addNode(name, ntName);
        }
        Node contentNode = null;
        if (isCollection) {
            contentNode = parentNode;
        } else {
            if (parentNode.hasNode(JcrConstants.JCR_CONTENT)) {
                contentNode = parentNode.getNode(JcrConstants.JCR_CONTENT);
                // check if nodetype is compatible (might be update of an existing file)
                if (contentNode.isNodeType(getContentNodeType()) ||
                        !forceCompatibleContentNodes()) {
                    // remove all entries in the jcr:content since replacing content
                    // includes properties (DefaultHandler) and nodes (e.g. ZipHandler)
                    if (contentNode.hasNodes()) {
                        NodeIterator it = contentNode.getNodes();
                        while (it.hasNext()) {
                            it.nextNode().remove();
                        }
                    }
                } else {
                    contentNode.remove();
                    contentNode = null;
                }
            }
            if (contentNode == null) {
                contentNode = parentNode.addNode(JcrConstants.JCR_CONTENT, getContentNodeType());
            }
        }
        return contentNode;
    }

    /**
     * Defines if content nodes should be replace if they don't have the
     * node type given by {@link #getCollectionNodeType()}.
     *
     * @return <code>true</code> if content nodes should be replaced.
     */
    protected boolean forceCompatibleContentNodes() {
        return false;
    }

    /**
     * Returns true if the export root is a node and if it contains a child node
     * with name {@link JcrConstants#JCR_CONTENT jcr:content} in case this
     * export is not intended for a collection.
     *
     * @return true if the export root is a node. If the specified boolean parameter
     * is false (not a collection export) the given export root must contain a
     * child node with name {@link JcrConstants#JCR_CONTENT jcr:content}.
     *
     * @see IOHandler#canExport(ExportContext, boolean)
     */
    public boolean canExport(ExportContext context, boolean isCollection) {
        if (context == null || context.isCompleted()) {
            return false;
        }
        Item exportRoot = context.getExportRoot();
        boolean success = exportRoot != null && exportRoot.isNode();
        if (success && !isCollection) {
            try {
                Node n = ((Node)exportRoot);
                success = n.hasNode(JcrConstants.JCR_CONTENT);
            } catch (RepositoryException e) {
                // should never occur.
                success = false;
            }
        }
        return success;
    }

    /**
     * @see IOHandler#canExport(ExportContext, DavResource)
     */
    public boolean canExport(ExportContext context, DavResource resource) {
        if (resource == null) {
            return false;
        }
        return canExport(context, resource.isCollection());
    }

    /**
     * Retrieves the content node that will be used for exporting properties and
     * data and calls the corresponding methods.
     *
     * @param context the export context
     * @param isCollection <code>true</code> if collection
     * @see #exportProperties(ExportContext, boolean, Node)
     * @see #exportData(ExportContext, boolean, Node)
     */
    public boolean exportContent(ExportContext context, boolean isCollection) throws IOException {
        if (!canExport(context, isCollection)) {
            throw new IOException(getName() + ": Cannot export " + context.getExportRoot());
        }
        try {
            Node contentNode = getContentNode(context, isCollection);
            exportProperties(context, isCollection, contentNode);
            if (context.hasStream()) {
                exportData(context, isCollection, contentNode);
            } // else: missing stream. ignore.
            return true;
        } catch (RepositoryException e) {
            // should never occur, since the proper structure of the content
            // node must be asserted in the 'canExport' call.
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Same as (@link IOHandler#exportContent(ExportContext, boolean)} where
     * the boolean values is defined by {@link DavResource#isCollection()}.
     *
     * @see IOHandler#exportContent(ExportContext, DavResource)
     */
    public boolean exportContent(ExportContext context, DavResource resource) throws IOException {
        if (!canExport(context, resource)) {
            throw new IOException(getName() + ": Cannot export " + context.getExportRoot());
        }
        return exportContent(context, resource.isCollection());
    }

    /**
     * Checks if the given content node contains a jcr:data property
     * and spools its value to the output stream of the export context.<br>
     * Please note, that subclasses that define a different structure of the
     * content node should create their own
     * {@link  #exportData(ExportContext, boolean, Node) exportData} method.
     *
     * @param context export context
     * @param isCollection <code>true</code> if collection
     * @param contentNode the content node
     * @throws IOException if an I/O error occurs
     */
    protected void exportData(ExportContext context, boolean isCollection, Node contentNode) throws IOException, RepositoryException {
        if (contentNode.hasProperty(JcrConstants.JCR_DATA)) {
            Property p = contentNode.getProperty(JcrConstants.JCR_DATA);
            IOUtil.spool(p.getStream(), context.getOutputStream());
        } // else: stream undefined -> contentlength was not set
    }

    /**
     * Retrieves mimetype, encoding and modification time from the content node.
     * The content length is determined by the length of the jcr:data property
     * if it is present. The creation time however is retrieved from the parent
     * node (in case of isCollection == false only).
     *
     * @param context the export context
     * @param isCollection <code>true</code> if collection
     * @param contentNode the content node
     */
    protected void exportProperties(ExportContext context, boolean isCollection, Node contentNode) throws IOException {
        try {
            // only non-collections: 'jcr:created' is present on the parent 'fileNode' only
            if (!isCollection && contentNode.getDepth() > 0 && contentNode.getParent().hasProperty(JcrConstants.JCR_CREATED)) {
                long cTime = contentNode.getParent().getProperty(JcrConstants.JCR_CREATED).getValue().getLong();
                context.setCreationTime(cTime);
            }

            long length = IOUtil.UNDEFINED_LENGTH;
            if (contentNode.hasProperty(JcrConstants.JCR_DATA)) {
                Property p = contentNode.getProperty(JcrConstants.JCR_DATA);
                length = p.getLength();
                context.setContentLength(length);
            }

            String mimeType = null;
            String encoding = null;
            if (contentNode.hasProperty(JcrConstants.JCR_MIMETYPE)) {
                mimeType = contentNode.getProperty(JcrConstants.JCR_MIMETYPE).getString();
            }
            if (contentNode.hasProperty(JcrConstants.JCR_ENCODING)) {
                encoding = contentNode.getProperty(JcrConstants.JCR_ENCODING).getString();
                // ignore "" encodings (although this is avoided during import)
                if ("".equals(encoding)) {
                    encoding = null;
                }
            }
            context.setContentType(mimeType, encoding);

            long modTime = IOUtil.UNDEFINED_TIME;
            if (contentNode.hasProperty(JcrConstants.JCR_LASTMODIFIED)) {
                modTime = contentNode.getProperty(JcrConstants.JCR_LASTMODIFIED).getLong();
                context.setModificationTime(modTime);
            } else {
                context.setModificationTime(System.currentTimeMillis());
            }

            if (length > IOUtil.UNDEFINED_LENGTH && modTime > IOUtil.UNDEFINED_TIME) {
                String etag = "\"" + length + "-" + modTime + "\"";
                context.setETag(etag);
            }
        } catch (RepositoryException e) {
            // should never occur
            log.error("Unexpected error {0} while exporting properties: {1}", e.getClass().getName(), e.getMessage());
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Retrieves the content node that contains the data to be exported. In case
     * isCollection is true, this corresponds to the export root. Otherwise there
     * must be a child node with name {@link JcrConstants#JCR_CONTENT jcr:content}.
     *
     * @param context the export context
     * @param isCollection <code>true</code> if collection
     * @return content node used for the export
     * @throws RepositoryException if an error during repository access occurs.
     */
    protected Node getContentNode(ExportContext context, boolean isCollection) throws RepositoryException {
        Node contentNode = (Node)context.getExportRoot();
        // 'file' nodes must have an jcr:content child node (see canExport)
        if (!isCollection) {
            contentNode = contentNode.getNode(JcrConstants.JCR_CONTENT);
        }
        return contentNode;
    }

    /**
     * Name of the nodetype to be used to create a new collection node (folder)
     *
     * @return nodetype name
     */
    protected String getCollectionNodeType() {
        return collectionNodetype;
    }

    /**
     * Name of the nodetype to be used to create a new non-collection node (file)
     *
     * @return nodetype name
     */
    protected String getNodeType() {
        return defaultNodetype;
    }

    /**
     * Name of the nodetype to be used to create the content node below
     * a new non-collection node, whose name is always {@link JcrConstants#JCR_CONTENT
     * jcr:content}.
     *
     * @return nodetype name
     */
    protected String getContentNodeType() {
        return contentNodetype;
    }

    //----------------------------------------------------< PropertyHandler >---

    public boolean canExport(PropertyExportContext context, boolean isCollection) {
        return canExport((ExportContext) context, isCollection);
    }

    public boolean exportProperties(PropertyExportContext exportContext, boolean isCollection) throws RepositoryException {
        if (!canExport(exportContext, isCollection)) {
            throw new RepositoryException("PropertyHandler " + getName() + " failed to export properties.");
        }

        Node cn = getContentNode(exportContext, isCollection);
        try {
            // export the properties common with normal I/O handling
            exportProperties(exportContext, isCollection, cn);

            // export all other properties as well
            PropertyIterator it = cn.getProperties();
            while (it.hasNext()) {
                Property p = it.nextProperty();
                String name = p.getName();
                PropertyDefinition def = p.getDefinition();
                if (def.isMultiple() || isDefinedByFilteredNodeType(def)) {
                    log.debug("Skip property '" + name + "': not added to webdav property set.");
                    continue;
                }
                if (JcrConstants.JCR_DATA.equals(name)
                    || JcrConstants.JCR_MIMETYPE.equals(name)
                    || JcrConstants.JCR_ENCODING.equals(name)
                    || JcrConstants.JCR_LASTMODIFIED.equals(name)) {
                    continue;
                }

                DavPropertyName davName = getDavName(name, p.getSession());
                exportContext.setProperty(davName, p.getValue().getString());
            }
            return true;
        } catch (IOException e) {
            // should not occur (log output see 'exportProperties')
            return false;
        }
    }

    public boolean canImport(PropertyImportContext context, boolean isCollection) {
        if (context == null || context.isCompleted()) {
            return false;
        }
        Item contextItem = context.getImportRoot();
        try {
            return contextItem != null && contextItem.isNode() && (isCollection || ((Node)contextItem).hasNode(JcrConstants.JCR_CONTENT));
        } catch (RepositoryException e) {
            log.error("Unexpected error: " + e.getMessage());
            return false;
        }
    }

    public Map importProperties(PropertyImportContext importContext, boolean isCollection) throws RepositoryException {
        if (!canImport(importContext, isCollection)) {
            throw new RepositoryException("PropertyHandler " + getName() + " failed import properties");
        }

        // loop over List and remember all properties and propertyNames
        // that failed to be imported (set or remove).
        Map failures = new HashMap();
        List changeList = importContext.getChangeList();

        // for collections the import-root is the target node where properties
        // are altered. in contrast 'non-collections' are with the handler
        // represented by 'file' nodes, that must have a jcr:content child
        // node, which holds all properties except jcr:created.
        // -> see canImport for the corresponding assertions
        Node cn = (Node) importContext.getImportRoot();
        if (!isCollection && cn.hasNode(JcrConstants.JCR_CONTENT)) {
            cn = cn.getNode(JcrConstants.JCR_CONTENT);
        }

        if (changeList != null) {
            Iterator it = changeList.iterator();
            while (it.hasNext()) {
                Object propEntry = it.next();
                try {
                    if (propEntry instanceof DavPropertyName) {
                        // remove
                        DavPropertyName propName = (DavPropertyName) propEntry;
                        removeJcrProperty(propName, cn);
                    } else if (propEntry instanceof DavProperty) {
                        // add or modify property
                        DavProperty prop = (DavProperty)propEntry;
                        setJcrProperty(prop, cn);
                    } else {
                        // ignore any other entry in the change list
                        log.error("unknown object in change list: " + propEntry.getClass().getName());
                    }
                } catch (RepositoryException e) {
                    failures.put(propEntry, e);
                }
            }
        }
        if (failures.isEmpty()) {
            setLastModified(cn, IOUtil.UNDEFINED_LENGTH);
        }
        return failures;
    }

    //------------------------------------------------------------< private >---
    /**
     * Builds a webdav property name from the given jcrName. In case the jcrName
     * contains a namespace prefix that would conflict with any of the predefined
     * webdav namespaces a new prefix is assigned.<br>
     * Please note, that the local part of the jcrName is checked for XML
     * compatibility by calling {@link ISO9075#encode(String)}
     *
     * @param jcrName name of the jcr property
     * @param session session
     * @return a <code>DavPropertyName</code> for the given jcr name.
     * @throws RepositoryException if an error during repository access occurs.
     */
    private DavPropertyName getDavName(String jcrName, Session session) throws RepositoryException {
        // make sure the local name is xml compliant
        String localName = ISO9075.encode(Text.getLocalName(jcrName));
        String prefix = Text.getNamespacePrefix(jcrName);
        String uri = session.getNamespaceURI(prefix);
        Namespace namespace = Namespace.getNamespace(prefix, uri);
        DavPropertyName name = DavPropertyName.create(localName, namespace);
        return name;
    }

    /**
     * Build jcr property name from dav property name. If the property name
     * defines a namespace uri, that has not been registered yet, an attempt
     * is made to register the uri with the prefix defined.
     *
     * @param propName name of the dav property
     * @param session repository session
     * @return jcr name
     * @throws RepositoryException if an error during repository access occurs.
     */
    private String getJcrName(DavPropertyName propName, Session session) throws RepositoryException {
        // remove any encoding necessary for xml compliance
        String pName = ISO9075.decode(propName.getName());
        Namespace propNamespace = propName.getNamespace();
        if (!Namespace.EMPTY_NAMESPACE.equals(propNamespace)) {
            NamespaceHelper helper = new NamespaceHelper(session);
            String prefix = helper.registerNamespace(
                    propNamespace.getPrefix(), propNamespace.getURI());
            pName = prefix + ":" + pName;
        }
        return pName;
    }


    /**
     * @param property dav property
     * @param contentNode the content node
     * @throws RepositoryException if an error during repository access occurs.
     */
    private void setJcrProperty(DavProperty property, Node contentNode) throws RepositoryException {
        // Retrieve the property value. Note, that a 'null' value is replaced
        // by empty string, since setting a jcr property value to 'null'
        // would be equivalent to its removal.
        String value = "";
        if (property.getValue() != null) {
            value = property.getValue().toString();
        }

        DavPropertyName davName = property.getName();
        if (DavPropertyName.GETCONTENTTYPE.equals(davName)) {
            String mimeType = IOUtil.getMimeType(value);
            String encoding = IOUtil.getEncoding(value);
            contentNode.setProperty(JcrConstants.JCR_MIMETYPE, mimeType);
            contentNode.setProperty(JcrConstants.JCR_ENCODING, encoding);
        } else {
            contentNode.setProperty(getJcrName(davName, contentNode.getSession()), value);
        }
    }

    /**
     * @param propertyName dav property name
     * @param contentNode the content node
     * @throws RepositoryException if an error during repository access occurs.
     */
    private void removeJcrProperty(DavPropertyName propertyName, Node contentNode)
            throws RepositoryException {
        if (DavPropertyName.GETCONTENTTYPE.equals(propertyName)) {
            if (contentNode.hasProperty(JcrConstants.JCR_MIMETYPE)) {
                contentNode.getProperty(JcrConstants.JCR_MIMETYPE).remove();
            }
            if (contentNode.hasProperty(JcrConstants.JCR_ENCODING)) {
                contentNode.getProperty(JcrConstants.JCR_ENCODING).remove();
            }
        } else {
            String jcrName = getJcrName(propertyName, contentNode.getSession());
            if (contentNode.hasProperty(jcrName)) {
                contentNode.getProperty(jcrName).remove();
            }
            // removal of non existing property succeeds
        }
    }

    private void setLastModified(Node contentNode, long hint) {
        try {
            Calendar lastMod = Calendar.getInstance();
            if (hint > IOUtil.UNDEFINED_TIME) {
                lastMod.setTimeInMillis(hint);
            } else {
                lastMod.setTime(new Date());
            }
            contentNode.setProperty(JcrConstants.JCR_LASTMODIFIED, lastMod);
        } catch (RepositoryException e) {
            // ignore: property may not be available on the node.
            // deliberately not re-throwing as IOException.
        }
    }

    private static boolean isDefinedByFilteredNodeType(PropertyDefinition def) {
        String ntName = def.getDeclaringNodeType().getName();
        return ntName.equals(JcrConstants.NT_BASE)
               || ntName.equals(JcrConstants.MIX_REFERENCEABLE)
               || ntName.equals(JcrConstants.MIX_VERSIONABLE)
               || ntName.equals(JcrConstants.MIX_LOCKABLE);
    }
}
