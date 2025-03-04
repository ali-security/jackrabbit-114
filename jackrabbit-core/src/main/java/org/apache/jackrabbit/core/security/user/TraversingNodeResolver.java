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

package org.apache.jackrabbit.core.security.user;

import org.apache.jackrabbit.commons.iterator.NodeIteratorAdapter;
import org.apache.jackrabbit.core.NodeImpl;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

/**
 *
 */
class TraversingNodeResolver extends NodeResolver {

    private static final Logger log = LoggerFactory.getLogger(TraversingNodeResolver.class);

    /**
     * Additonally to the NodeType-Argument the resolvers searched is narrowed
     * by indicating a Path to an {@link javax.jcr.Item} as start for the search
     *
     * @param session      to use for repository access
     */
    TraversingNodeResolver(Session session, NamePathResolver resolver) throws RepositoryException {
        super(session, resolver);
    }

    //-------------------------------------------------------< NodeResolver >---
    /**
     * @inheritDoc
     */
    public Node findNode(Name nodeName, Name ntName) throws RepositoryException {
        String sr = getSearchRoot(ntName);
        // TODO: remove cast once 283 is released
        SessionImpl sImpl = (SessionImpl) getSession();
        if (sImpl.nodeExists(sr)) {
            try {
                Node root = sImpl.getNode(sr);
                return collectNode(nodeName, ntName, root.getNodes());
            } catch (PathNotFoundException e) {
                // should not get here
                log.warn("Error while retrieving node " + sr);
            }
        } // else: searchRoot does not exist yet -> omit the search
        return null;
    }

    /**
     * @inheritDoc
     */
    public Node findNode(Name propertyName, String value, Name ntName) throws RepositoryException {
        String sr = getSearchRoot(ntName);
        // TODO: remove cast once 283 is released
        SessionImpl sImpl = (SessionImpl) getSession();
        if (sImpl.nodeExists(sr)) {
            try {
                Node root = sImpl.getNode(sr);
                NodeIterator nodes = collectNodes(value,
                        Collections.singleton(propertyName), ntName,
                        root.getNodes(), true, 1);
                if (nodes.hasNext()) {
                    return nodes.nextNode();
                }
            } catch (PathNotFoundException e) {
                // should not get here
                log.warn("Error while retrieving node " + sr);
            }
        } // else: searchRoot does not exist yet -> omit the search
        return null;
    }

    /**
     * @inheritDoc
     */
    public NodeIterator findNodes(Set propertyNames, String value, Name ntName,
                                  boolean exact, long maxSize) throws RepositoryException {
        String sr = getSearchRoot(ntName);
        // TODO: remove cast once 283 is released
        SessionImpl sImpl = (SessionImpl) getSession();
        if (sImpl.nodeExists(sr)) {
            try {
                Node root = sImpl.getNode(sr);
                return collectNodes(value, propertyNames, ntName, root.getNodes(), exact, maxSize);
            } catch (PathNotFoundException e) {
                // should not get here
                log.warn("Error while retrieving node " + sr);
            }
        } // else: searchRoot does not exist yet -> omit the search
        return NodeIteratorAdapter.EMPTY;
    }

    //--------------------------------------------------------------------------
    /**
     *
     * @param nodeName
     * @param ntName
     * @param nodes
     * @return The first matching node or <code>null</code>.
     */
    private Node collectNode(Name nodeName, Name ntName, NodeIterator nodes) {
        Node match = null;
        while (match == null && nodes.hasNext()) {
            NodeImpl node = (NodeImpl) nodes.nextNode();
            try {
                if (node.isNodeType(ntName) && nodeName.equals(node.getQName())) {
                    match = node;
                } else if (node.hasNodes()) {
                    match = collectNode(nodeName, ntName, node.getNodes());
                }
            } catch (RepositoryException e) {
                log.warn("Internal error while accessing node", e);
            }
        }
        return match;
    }

    /**
     * searches the given value in the range of the given NodeIterator.
     * recurses unitll all matching values in all configured props are found.
     *
     * @param value   the value to be found in the nodes
     * @param props   property to be searched, or null if {@link javax.jcr.Item#getName()}
     * @param ntName  to filter search
     * @param nodes   range of nodes and descendants to be searched
     * @param exact   if set to true the value has to match exactly else a
     * substring is searched
     * @param maxSize
     */
    private NodeIterator collectNodes(String value, Set props, Name ntName,
                                      NodeIterator nodes, boolean exact,
                                      long maxSize) {
        Set matchSet = new HashSet();
        collectNodes(value, props, ntName, nodes, matchSet, exact, maxSize);
        return new NodeIteratorAdapter(matchSet);
    }

    /**
     * searches the given value in the range of the given NodeIterator.
     * recurses unitll all matching values in all configured properties are found.
     *
     * @param value         the value to be found in the nodes
     * @param propertyNames property to be searched, or null if {@link javax.jcr.Item#getName()}
     * @param nodeTypeName  name of nodetypes to search
     * @param itr           range of nodes and descendants to be searched
     * @param matchSet      Set of found matches to append results
     * @param exact         if set to true the value has to match exact
     * @param maxSize
     */
    private void collectNodes(String value, Set propertyNames,
                              Name nodeTypeName, NodeIterator itr,
                              Set matchSet, boolean exact, long maxSize) {
        while (itr.hasNext()) {
            NodeImpl node = (NodeImpl) itr.nextNode();
            try {
                if (matches(node, nodeTypeName, propertyNames, value, exact)) {
                    matchSet.add(node);
                    maxSize--;
                }
                if (node.hasNodes() && maxSize > 0) {
                    collectNodes(value, propertyNames, nodeTypeName,
                            node.getNodes(), matchSet, exact, maxSize);
                }
            } catch (RepositoryException e) {
                log.warn("Internal error while accessing node", e);
            }
        }
    }

    /**
     * 
     * @param node
     * @param nodeTypeName
     * @param propertyNames
     * @param value
     * @param exact
     * @return
     * @throws RepositoryException
     */
    private static boolean matches(NodeImpl node, Name nodeTypeName,
                            Collection propertyNames, String value,
                            boolean exact) throws RepositoryException {

        boolean match = false;
        if (node.isNodeType(nodeTypeName)) {
            if (value == null) {
                match = true;
            } else {
                try {
                    if (propertyNames.isEmpty()) {
                        match = (exact) ? node.getName().equals(value) :
                                node.getName().matches(".*"+value+".*");
                    } else {
                        Iterator pItr = propertyNames.iterator();
                        while (!match && pItr.hasNext()) {
                            Name propertyName = (Name) pItr.next();
                            if (node.hasProperty(propertyName)) {
                                Property prop = node.getProperty(propertyName);
                                if (prop.getDefinition().isMultiple()) {
                                    Value[] values = prop.getValues();
                                    for (int i = 0; i < values.length && !match; i++) {
                                        match = matches(value, values[i].getString(), exact);
                                    }
                                } else {
                                    match = matches(value, prop.getString(), exact);
                                }
                            }
                        }
                    }
                } catch (PatternSyntaxException pe) {
                    log.debug("couldn't search for {}, pattern invalid: {}",
                            value, pe.getMessage());
                }
            }
        }
        return match;
    }

    private static boolean matches(String value, String toMatch, boolean exact) {
        return (exact) ? toMatch.equals(value) : toMatch.matches(".*"+value+".*");
    }
}
