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
package org.apache.jackrabbit.core.query.lucene;

import org.apache.jackrabbit.core.ItemManager;
import org.apache.jackrabbit.core.NodeImpl;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.security.AccessManager;
import org.apache.jackrabbit.spi.Name;
import org.apache.lucene.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.QueryResult;
import javax.jcr.query.RowIterator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Implements the <code>javax.jcr.query.QueryResult</code> interface.
 */
public class QueryResultImpl implements QueryResult {

    /**
     * The logger instance for this class
     */
    private static final Logger log = LoggerFactory.getLogger(QueryResultImpl.class);

    /**
     * The search index to execute the query.
     */
    private final SearchIndex index;

    /**
     * The item manager of the session executing the query
     */
    private final ItemManager itemMgr;

    /**
     * The session executing the query
     */
    protected final SessionImpl session;

    /**
     * The access manager of the session that executes the query.
     */
    private final AccessManager accessMgr;

    /**
     * The query instance which created this query result.
     */
    protected final AbstractQueryImpl queryImpl;

    /**
     * The lucene query to execute.
     */
    protected final Query query;

    /**
     * The spell suggestion or <code>null</code> if not available.
     */
    protected final SpellSuggestion spellSuggestion;

    /**
     * The select properties
     */
    protected final Name[] selectProps;

    /**
     * The names of properties to use for ordering the result set.
     */
    protected final Name[] orderProps;

    /**
     * The order specifier for each of the order properties.
     */
    protected final boolean[] orderSpecs;

    /**
     * The result nodes including their score. This list is populated on a lazy
     * basis while a client iterates through the results.
     * <p/>
     * The exact type is: <code>List&lt;ScoreNode[]></code>
     */
    private final List resultNodes = new ArrayList();

    /**
     * This is the raw number of results that matched the query. This number
     * also includes matches which will not be returned due to access
     * restrictions. This value is set whenever hits are obtained.
     */
    private int numResults = -1;

    /**
     * The number of results that are invalid, either because a node does not
     * exist anymore or because the session does not have access to the node.
     */
    private int invalid = 0;

    /**
     * If <code>true</code> nodes are returned in document order.
     */
    private final boolean docOrder;

    /**
     * The excerpt provider or <code>null</code> if none was created yet.
     */
    private ExcerptProvider excerptProvider;

    /**
     * The offset in the total result set
     */
    private final long offset;

    /**
     * The maximum size of this result if limit > 0
     */
    private final long limit;

    /**
     * Creates a new query result.
     *
     * @param index           the search index where the query is executed.
     * @param itemMgr         the item manager of the session executing the
     *                        query.
     * @param session         the session executing the query.
     * @param accessMgr       the access manager of the session executiong the
     *                        query.
     * @param queryImpl       the query instance which created this query
     *                        result.
     * @param query           the lucene query to execute on the index.
     * @param spellSuggestion the spell suggestion or <code>null</code> if none
     *                        is available.
     * @param selectProps     the select properties of the query.
     * @param orderProps      the names of the order properties.
     * @param orderSpecs      the order specs, one for each order property
     *                        name.
     * @param documentOrder   if <code>true</code> the result is returned in
     *                        document order.
     * @param limit           the maximum result size
     * @param offset          the offset in the total result set
     */
    public QueryResultImpl(SearchIndex index,
                           ItemManager itemMgr,
                           SessionImpl session,
                           AccessManager accessMgr,
                           AbstractQueryImpl queryImpl,
                           Query query,
                           SpellSuggestion spellSuggestion,
                           Name[] selectProps,
                           Name[] orderProps,
                           boolean[] orderSpecs,
                           boolean documentOrder,
                           long offset,
                           long limit) throws RepositoryException {
        this.index = index;
        this.itemMgr = itemMgr;
        this.session = session;
        this.accessMgr = accessMgr;
        this.queryImpl = queryImpl;
        this.query = query;
        this.spellSuggestion = spellSuggestion;
        this.selectProps = selectProps;
        this.orderProps = orderProps;
        this.orderSpecs = orderSpecs;
        this.docOrder = orderProps.length == 0 && documentOrder;
        this.offset = offset;
        this.limit = limit;
        // if document order is requested get all results right away
        getResults(docOrder ? Integer.MAX_VALUE : index.getResultFetchSize());
    }

    /**
     * {@inheritDoc}
     */
    public String[] getColumnNames() throws RepositoryException {
        try {
            String[] propNames = new String[selectProps.length];
            for (int i = 0; i < selectProps.length; i++) {
                propNames[i] = session.getJCRName(selectProps[i]);
            }
            return propNames;
        } catch (NamespaceException npde) {
            String msg = "encountered invalid property name";
            log.debug(msg);
            throw new RepositoryException(msg, npde);
        }
    }

    /**
     * {@inheritDoc}
     */
    public NodeIterator getNodes() throws RepositoryException {
        return getNodeIterator();
    }

    /**
     * {@inheritDoc}
     */
    public RowIterator getRows() throws RepositoryException {
        if (excerptProvider == null) {
            try {
                excerptProvider = index.createExcerptProvider(query);
            } catch (IOException e) {
                throw new RepositoryException(e);
            }
        }
        return new RowIteratorImpl(getNodeIterator(), selectProps,
                queryImpl.getSelectorNames(), itemMgr, session,
                excerptProvider, spellSuggestion);
    }

    /**
     * Executes the query for this result and returns hits. The caller must
     * close the query hits when he is done using it.
     *
     * @return hits for this query result.
     * @throws IOException if an error occurs while executing the query.
     */
    protected MultiColumnQueryHits executeQuery() throws IOException {
        return index.executeQuery(session, queryImpl,
                query, orderProps, orderSpecs);
    }

    //--------------------------------< internal >------------------------------

    /**
     * Creates a node iterator over the result nodes.
     *
     * @return a node iterator over the result nodes.
     */
    private ScoreNodeIterator getNodeIterator() {
        if (docOrder) {
            return new DocOrderNodeIteratorImpl(itemMgr, resultNodes, 0);
        } else {
            return new LazyScoreNodeIterator(0);
        }
    }

    /**
     * Attempts to get <code>size</code> results and puts them into {@link
     * #resultNodes}. If the size of {@link #resultNodes} is less than
     * <code>size</code> then there are no more than <code>resultNodes.size()</code>
     * results for this query.
     *
     * @param size the number of results to fetch for the query.
     * @throws RepositoryException if an error occurs while executing the
     *                             query.
     */
    private void getResults(long size) throws RepositoryException {
        if (log.isDebugEnabled()) {
            log.debug("getResults({}) limit={}", new Long(size), new Long(limit));
        }

        long maxResultSize = size;

        // is there any limit?
        if (limit > 0) {
            maxResultSize = limit;
        }

        if (resultNodes.size() >= maxResultSize) {
            // we already have them all
            return;
        }

        // execute it
        MultiColumnQueryHits result = null;
        try {
            long time = System.currentTimeMillis();
            result = executeQuery();
            log.debug("query executed in {} ms",
                    new Long(System.currentTimeMillis() - time));

            if (resultNodes.isEmpty() && offset > 0) {
                // collect result offset into dummy list
                collectScoreNodes(result, new ArrayList(), offset);
            } else {
                int start = resultNodes.size() + invalid + (int) offset;
                result.skip(start);
            }

            time = System.currentTimeMillis();
            collectScoreNodes(result, resultNodes, maxResultSize);
            log.debug("retrieved ScoreNodes in {} ms",
                    new Long(System.currentTimeMillis() - time));

            // update numResults
            numResults = result.getSize();
        } catch (IOException e) {
            log.error("Exception while executing query: ", e);
            // todo throw?
        } finally {
            if (result != null) {
                try {
                    result.close();
                } catch (IOException e) {
                    log.warn("Unable to close query result: " + e);
                }
            }
        }
    }

    /**
     * Collect score nodes from <code>hits</code> into the <code>collector</code>
     * list until the size of <code>collector</code> reaches <code>maxResults</code>
     * or there are not more results.
     *
     * @param hits the raw hits.
     * @param collector where the access checked score nodes are collected.
     * @param maxResults the maximum number of results in the collector.
     * @throws IOException if an error occurs while reading from hits.
     * @throws RepositoryException if an error occurs while checking access rights.
     */
    private void collectScoreNodes(MultiColumnQueryHits hits,
                                   List collector,
                                   long maxResults)
            throws IOException, RepositoryException {
        while (collector.size() < maxResults) {
            ScoreNode[] sn = hits.nextScoreNodes();
            if (sn == null) {
                // no more results
                break;
            }
            // check access
            if (isAccessGranted(sn)) {
                collector.add(sn);
            } else {
                invalid++;
            }
        }
    }

    /**
     * Checks if access is granted to all <code>nodes</code>.
     *
     * @param nodes the nodes to check.
     * @return <code>true</code> if read access is granted to all
     *         <code>nodes</code>.
     * @throws RepositoryException if an error occurs while checking access
     *                             rights.
     */
    private boolean isAccessGranted(ScoreNode[] nodes)
            throws RepositoryException {
        for (int i = 0; i < nodes.length; i++) {
            try {
                // TODO: rather use AccessManager.canRead(Path)
                if (nodes[i] != null && !accessMgr.isGranted(nodes[i].getNodeId(), AccessManager.READ)) {
                    return false;
                }
            } catch (ItemNotFoundException e) {
                // node deleted while query was executed
            }
        }
        return true;
    }

    /**
     * Returns the total number of hits. This is the number of results you
     * will get get if you don't set any limit or offset. Keep in mind that this
     * number may get smaller if nodes are found in the result set which the
     * current session has no permission to access. This method may return
     * <code>-1</code> if the total size is unknown.
     */
    public int getTotalSize() {
        if (numResults == -1) {
            return -1;
        } else {
            return numResults - invalid;
        }
    }

    private final class LazyScoreNodeIterator implements ScoreNodeIterator {

        private int position = -1;

        private boolean initialized = false;

        private NodeImpl next;

        private final int selectorIndex;

        private LazyScoreNodeIterator(int selectorIndex) {
            this.selectorIndex = selectorIndex;
        }

        /**
         * {@inheritDoc}
         */
        public float getScore() {
            initialize();
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return ((ScoreNode[]) resultNodes.get(position))[selectorIndex].getScore();
        }

        /**
         * {@inheritDoc}
         */
        public ScoreNode[] getScoreNodes() {
            initialize();
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return (ScoreNode[]) resultNodes.get(position);
        }

        /**
         * {@inheritDoc}
         */
        public NodeImpl nextNodeImpl() {
            initialize();
            if (next == null) {
                throw new NoSuchElementException();
            }
            NodeImpl n = next;
            fetchNext();
            return n;
        }

        /**
         * {@inheritDoc}
         */
        public Node nextNode() {
            return nextNodeImpl();
        }

        /**
         * {@inheritDoc}
         */
        public void skip(long skipNum) {
            initialize();
            if (skipNum < 0) {
                throw new IllegalArgumentException("skipNum must not be negative");
            }
            if (skipNum == 0) {
                // do nothing
            } else {
                // attempt to get enough results
                try {
                    getResults(position + invalid + (int) skipNum);
                    if (resultNodes.size() >= position + skipNum) {
                        // skip within already fetched results
                        position += skipNum - 1;
                        fetchNext();
                    } else {
                        // not enough results after getResults()
                        throw new NoSuchElementException();
                    }
                } catch (RepositoryException e) {
                    throw new NoSuchElementException(e.getMessage());
                }
            }
        }

        /**
         * {@inheritDoc}
         * <p/>
         * This value may shrink when the query result encounters non-existing
         * nodes or the session does not have access to a node.
         */
        public long getSize() {
            int total = getTotalSize();
            if (total == -1) {
                return -1;
            }
            long size = total - offset;
            if (limit > 0 && size > limit) {
                return limit;
            } else {
                return size;
            }
        }

        /**
         * {@inheritDoc}
         */
        public long getPosition() {
            initialize();
            return position;
        }

        /**
         * @throws UnsupportedOperationException always.
         */
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }

        /**
         * {@inheritDoc}
         */
        public boolean hasNext() {
            initialize();
            return next != null;
        }

        /**
         * {@inheritDoc}
         */
        public Object next() {
            return nextNodeImpl();
        }

        /**
         * Initializes this iterator but only if it is not yet initialized.
         */
        private void initialize() {
            if (!initialized) {
                fetchNext();
                initialized = true;
            }
        }

        /**
         * Fetches the next node to return by this iterator. If this method
         * returns and {@link #next} is <code>null</code> then there is no next
         * node.
         */
        private void fetchNext() {
            next = null;
            int nextPos = position + 1;
            while (next == null) {
                if (nextPos >= resultNodes.size()) {
                    // quick check if there are more results at all
                    // this check is only possible if we have numResults
                    if (numResults != -1 && (nextPos + invalid) >= numResults) {
                        break;
                    }

                    // fetch more results
                    try {
                        int num;
                        if (resultNodes.size() == 0) {
                            num = index.getResultFetchSize();
                        } else {
                            num = resultNodes.size() * 2;
                        }
                        getResults(num);
                    } catch (RepositoryException e) {
                        log.warn("Exception getting more results: " + e);
                    }
                    // check again
                    if (nextPos >= resultNodes.size()) {
                        // no more valid results
                        break;
                    }
                }
                ScoreNode[] sn = (ScoreNode[]) resultNodes.get(nextPos);
                try {
                    next = (NodeImpl) itemMgr.getItem(sn[selectorIndex].getNodeId());
                } catch (RepositoryException e) {
                    log.warn("Exception retrieving Node with UUID: "
                            + sn[selectorIndex].getNodeId() + ": " + e.toString());
                    // remove score node and try next
                    resultNodes.remove(nextPos);
                    invalid++;
                }
            }
            position++;
        }
    }
}
