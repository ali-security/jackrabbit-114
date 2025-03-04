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
package org.apache.jackrabbit.core.query;

import org.apache.jackrabbit.core.ItemManager;
import org.apache.jackrabbit.core.NodeIdIterator;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.core.state.NodeState;
import org.apache.jackrabbit.core.state.NodeStateIterator;
import org.apache.jackrabbit.spi.commons.query.qom.QueryObjectModelTree;

import javax.jcr.RepositoryException;
import javax.jcr.query.InvalidQueryException;

import java.io.IOException;

/**
 * Defines an interface for the actual node indexing and query execution.
 * The goal is to allow different implementations based on the persistent
 * manager in use. Some persistent model might allow to execute a query
 * in an optimized manner, e.g. database persistence.
 */
public interface QueryHandler {

    /**
     * Initializes this query handler. This method is called after the
     * <code>QueryHandler</code> is instantiated.
     *
     * @param context the context for this query handler.
     * @throws IOException if an error occurs during initialization.
     */
    void init(QueryHandlerContext context) throws IOException;

    /**
     * Returns the query handler context that passed in {@link
     * #init(QueryHandlerContext)}.
     *
     * @return the query handler context.
     */
    QueryHandlerContext getContext();

    /**
     * Adds a <code>Node</code> to the search index.
     * @param node the NodeState to add.
     * @throws RepositoryException if an error occurs while indexing the node.
     * @throws IOException if an error occurs while adding the node to the index.
     */
    void addNode(NodeState node) throws RepositoryException, IOException;

    /**
     * Deletes the Node with <code>id</code> from the search index.
     * @param id the <code>id</code> of the node to delete.
     * @throws IOException if an error occurs while deleting the node.
     */
    void deleteNode(NodeId id) throws IOException;

    /**
     * Updates the index in an atomic operation. Some nodes may be removed and
     * added again in the same updateNodes() call, which is equivalent to an
     * node update.
     *
     * @param remove Iterator of <code>NodeIds</code> of nodes to delete
     * @param add    Iterator of <code>NodeState</code> instance to add to the
     *               index.
     * @throws RepositoryException if an error occurs while indexing a node.
     * @throws IOException if an error occurs while updating the index.
     */
    void updateNodes(NodeIdIterator remove, NodeStateIterator add)
            throws RepositoryException, IOException;

    /**
     * Closes this <code>QueryHandler</code> and frees resources attached
     * to this handler.
     */
    void close() throws IOException;

    /**
     * Creates a new query by specifying the query statement itself and the
     * language in which the query is stated.  If the query statement is
     * syntactically invalid, given the language specified, an
     * InvalidQueryException is thrown. <code>language</code> must specify a query language
     * string from among those returned by QueryManager.getSupportedQueryLanguages(); if it is not
     * then an <code>InvalidQueryException</code> is thrown.
     *
     * @param session the session of the current user creating the query object.
     * @param itemMgr the item manager of the current user.
     * @param statement the query statement.
     * @param language the syntax of the query statement.
     * @throws InvalidQueryException if statement is invalid or language is unsupported.
     * @return A <code>Query</code> object.
     */
    ExecutableQuery createExecutableQuery(SessionImpl session,
                                          ItemManager itemMgr,
                                          String statement,
                                          String language)
            throws InvalidQueryException;

    /**
     * Creates a new query by specifying the query object model. If the query
     * object model is considered invalid for the implementing class, an
     * InvalidQueryException is thrown.
     *
     * @param session the session of the current user creating the query
     *                object.
     * @param itemMgr the item manager of the current user.
     * @param qomTree query query object model tree.
     * @return A <code>Query</code> object.
     * @throws InvalidQueryException if the query object model tree is invalid.
     */
    ExecutableQuery createExecutableQuery(SessionImpl session,
                                          ItemManager itemMgr,
                                          QueryObjectModelTree qomTree)
            throws InvalidQueryException;
}
