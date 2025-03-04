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

import org.apache.jackrabbit.core.NodeImpl;
import org.apache.jackrabbit.core.PropertyImpl;
import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.core.ItemManager;
import org.apache.jackrabbit.spi.commons.conversion.NameException;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.apache.jackrabbit.util.ISO9075;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.PathNotFoundException;
import javax.jcr.NamespaceException;
import javax.jcr.ValueFactory;
import javax.jcr.Node;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;
import java.util.Arrays;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

/**
 * Implements the {@link javax.jcr.query.RowIterator} interface returned by
 * a {@link javax.jcr.query.QueryResult}.
 */
class RowIteratorImpl implements RowIterator {

    /**
     * The logger instance for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(RowIteratorImpl.class);

    /**
     * The value factory.
     */
    private static final ValueFactory VALUE_FACTORY = ValueFactoryImpl.getInstance();

    /**
     * The name of the excerpt function without prefix but with left parenthesis.
     */
    private static final String EXCERPT_FUNC_LPAR = "excerpt(";

    /**
     * The name of the spell check function without prefix but with left
     * parenthesis.
     */
    private static final String SPELLCHECK_FUNC_LPAR = "spellcheck(";

    /**
     * The start Name for the rep:excerpt function: rep:excerpt(
     */
    private static final Name REP_EXCERPT_LPAR = NameFactoryImpl.getInstance().create(
            Name.NS_REP_URI, EXCERPT_FUNC_LPAR);

    /**
     * Iterator over nodes, that constitute the result set.
     */
    private final ScoreNodeIterator nodes;

    /**
     * Array of select property names
     */
    private final Name[] properties;

    /**
     * List of valid selector {@link Name}s.
     */
    private final List selectorNames = new ArrayList();

    /**
     * The item manager of the session that executes the query.
     */
    private final ItemManager itemMgr;

    /**
     * The <code>NamePathResolver</code> of the user <code>Session</code>.
     */
    private final NamePathResolver resolver;

    /**
     * The excerpt provider or <code>null</code> if none is available.
     */
    private final ExcerptProvider excerptProvider;

    /**
     * The spell suggestion or <code>null</code> if none is available.
     */
    private final SpellSuggestion spellSuggestion;

    /**
     * Creates a new <code>RowIteratorImpl</code> that iterates over the result
     * nodes.
     *
     * @param nodes           a <code>ScoreNodeIterator</code> that contains the
     *                        nodes of the query result.
     * @param properties      <code>Name</code> of the select properties.
     * @param selectorNames   the selector names.
     * @param itemMgr         the item manager of the session that executes the
     *                        query.
     * @param resolver        <code>NamespaceResolver</code> of the user
     *                        <code>Session</code>.
     * @param exProvider      the excerpt provider associated with the query
     *                        result that created this row iterator.
     * @param spellSuggestion the spell suggestion associated with the query
     *                        result or <code>null</code> if none is available.
     */
    RowIteratorImpl(ScoreNodeIterator nodes,
                    Name[] properties,
                    Name[] selectorNames,
                    ItemManager itemMgr,
                    NamePathResolver resolver,
                    ExcerptProvider exProvider,
                    SpellSuggestion spellSuggestion) {
        this.nodes = nodes;
        this.properties = properties;
        this.selectorNames.addAll(Arrays.asList(selectorNames));
        this.itemMgr = itemMgr;
        this.resolver = resolver;
        this.excerptProvider = exProvider;
        this.spellSuggestion = spellSuggestion;
    }

    /**
     * Returns the next <code>Row</code> in the iteration.
     *
     * @return the next <code>Row</code> in the iteration.
     * @throws NoSuchElementException if iteration has no more
     *                                <code>Row</code>s.
     */
    public Row nextRow() throws NoSuchElementException {
        return new RowImpl(nodes.getScore(),
                nodes.getScoreNodes(), nodes.nextNodeImpl());
    }

    /**
     * Skip a number of <code>Row</code>s in this iterator.
     *
     * @param skipNum the non-negative number of <code>Row</code>s to skip
     * @throws NoSuchElementException if skipped past the last
     *                                <code>Row</code> in this iterator.
     */
    public void skip(long skipNum) throws NoSuchElementException {
        nodes.skip(skipNum);
    }

    /**
     * Returns the number of <code>Row</code>s in this iterator.
     *
     * @return the number of <code>Row</code>s in this iterator.
     */
    public long getSize() {
        return nodes.getSize();
    }

    /**
     * Returns the current position within this iterator. The number
     * returned is the 0-based index of the next <code>Row</code> in the iterator,
     * i.e. the one that will be returned on the subsequent <code>next</code> call.
     * <p/>
     * Note that this method does not check if there is a next element,
     * i.e. an empty iterator will always return 0.
     *
     * @return the current position withing this iterator.
     */
    public long getPosition() {
        return nodes.getPosition();
    }

    /**
     * @throws UnsupportedOperationException always.
     */
    public void remove() {
        throw new UnsupportedOperationException("remove");
    }

    /**
     * Returns <code>true</code> if the iteration has more <code>Row</code>s.
     * (In other words, returns <code>true</code> if <code>next</code> would
     * return an <code>Row</code> rather than throwing an exception.)
     *
     * @return <code>true</code> if the iterator has more elements.
     */
    public boolean hasNext() {
        return nodes.hasNext();
    }

    /**
     * Returns the next <code>Row</code> in the iteration.
     *
     * @return the next <code>Row</code> in the iteration.
     * @throws NoSuchElementException if iteration has no more <code>Row</code>s.
     */
    public Object next() throws NoSuchElementException {
        return nextRow();
    }

    //---------------------< class RowImpl >------------------------------------

    /**
     * Implements the {@link javax.jcr.query.Row} interface, which represents
     * a row in the query result.
     */
    class RowImpl implements Row {

        /**
         * The score for this result row
         */
        private final float score;

        /**
         * The underlying <code>Node</code> of this result row.
         */
        private final NodeImpl node;

        /**
         * The score nodes associated with this row.
         */
        private final ScoreNode[] sn;

        /**
         * Cached value array for returned by {@link #getValues()}.
         */
        private Value[] values;

        /**
         * Set of select property <code>Name</code>s.
         */
        private Set propertySet;

        /**
         * Creates a new <code>RowImpl</code> instance based on <code>node</code>.
         *
         * @param score the score value for this result row
         * @param sn    the score nodes associated with this row.
         * @param node  the underlying <code>Node</code> for this <code>Row</code>.
         */
        RowImpl(float score, ScoreNode[] sn, NodeImpl node) {
            this.score = score;
            this.sn = sn;
            this.node = node;
        }

        /**
         * Returns an array of all the values in the same order as the property
         * names (column names) returned by
         * {@link javax.jcr.query.QueryResult#getColumnNames()}.
         *
         * @return a <code>Value</code> array.
         * @throws RepositoryException if an error occurs while retrieving the
         *                             values from the <code>Node</code>.
         */
        public Value[] getValues() throws RepositoryException {
            if (values == null) {
                Value[] tmp = new Value[properties.length];
                for (int i = 0; i < properties.length; i++) {
                    if (node.hasProperty(properties[i])) {
                        PropertyImpl prop = node.getProperty(properties[i]);
                        if (!prop.getDefinition().isMultiple()) {
                            if (prop.getDefinition().getRequiredType() == PropertyType.UNDEFINED) {
                                tmp[i] = VALUE_FACTORY.createValue(prop.getString());
                            } else {
                                tmp[i] = prop.getValue();
                            }
                        } else {
                            // mvp values cannot be returned
                            tmp[i] = null;
                        }
                    } else {
                        // property not set or one of the following:
                        // jcr:path / jcr:score / rep:excerpt / rep:spellcheck
                        if (NameConstants.JCR_PATH.equals(properties[i])) {
                            tmp[i] = VALUE_FACTORY.createValue(node.getPath(), PropertyType.PATH);
                        } else if (NameConstants.JCR_SCORE.equals(properties[i])) {
                            tmp[i] = VALUE_FACTORY.createValue(Math.round(score * 1000f));
                        } else if (isExcerptFunction(properties[i])) {
                            tmp[i] = getExcerpt();
                        } else if (isSpellCheckFunction(properties[i])) {
                            tmp[i] = getSpellCheckedStatement();
                        } else {
                            tmp[i] = null;
                        }
                    }
                }
                values = tmp;
            }
            // return a copy of the array
            Value[] ret = new Value[values.length];
            System.arraycopy(values, 0, ret, 0, values.length);
            return ret;
        }

        /**
         * Returns the value of the indicated  property in this <code>Row</code>.
         * <p/>
         * If <code>propertyName</code> is not among the column names of the
         * query result table, an <code>ItemNotFoundException</code> is thrown.
         *
         * @return a <code>Value</code>
         * @throws ItemNotFoundException if <code>propertyName</code> is not
         *                               among the column names of the query result table.
         * @throws RepositoryException   if <code>propertyName</code> is not a
         *                               valid property name.
         */
        public Value getValue(String propertyName) throws ItemNotFoundException, RepositoryException {
            if (propertySet == null) {
                // create the set first
                Set tmp = new HashSet();
                tmp.addAll(Arrays.asList(properties));
                propertySet = tmp;
            }
            try {
                Name prop = resolver.getQName(propertyName);
                if (!propertySet.contains(prop)) {
                    if (isExcerptFunction(propertyName)) {
                        // excerpt function with parameter
                        return getExcerpt(propertyName);
                    } else {
                        throw new ItemNotFoundException(propertyName);
                    }
                }
                if (node.hasProperty(prop)) {
                    Property p = node.getProperty(prop);
                    if (p.getDefinition().getRequiredType() == PropertyType.UNDEFINED) {
                        return VALUE_FACTORY.createValue(p.getString());
                    } else {
                        return p.getValue();
                    }
                } else {
                    // either jcr:score, jcr:path, rep:excerpt,
                    // rep:spellcheck or not set
                    if (NameConstants.JCR_PATH.equals(prop)) {
                        return VALUE_FACTORY.createValue(node.getPath(), PropertyType.PATH);
                    } else if (NameConstants.JCR_SCORE.equals(prop)) {
                        return VALUE_FACTORY.createValue(Math.round(score * 1000f));
                    } else if (isExcerptFunction(prop)) {
                        return getExcerpt();
                    } else if (isSpellCheckFunction(prop)) {
                        return getSpellCheckedStatement();
                    } else {
                        return null;
                    }
                }
            } catch (NameException e) {
                if (isExcerptFunction(propertyName)) {
                    // excerpt function with parameter
                    return getExcerpt(propertyName);
                } else {
                    throw new RepositoryException(e.getMessage(), e);
                }
            }
        }

        /**
         * Returns the <code>Node</code> corresponding to this <code>Row</code>.
         * <p/>
         * A <code>RepositoryException</code> is thrown if this <code>Row</code>
         * contains values from more than one node. This will be the case when more
         * than one selector is included among the columns specified for the query.
         *
         * @return a <code>Node</code>
         * @throws RepositoryException if this query has more than one selector
         * (and therefore, this <code>Row</code> corresponds to more than one
         * <code>Node</code>) or if another error occurs.
         * @since JCR 2.0
         */
        public Node getNode() throws RepositoryException {
            checkSingleSelector("Use getNode(String) instead.");
            return node;
        }

        /**
         * Returns the <code>Node</code> corresponding to this <code>Row</code> and
         * the specified selector.
         *
         * @param selectorName a <code>String</code>
         * @return a <code>Node</code>
         * @throws RepositoryException if <code>selectorName</code> is not the alias
         * of a selector in this query or if another error occurs.
         * @since JCR 2.0
         */
        public Node getNode(String selectorName) throws RepositoryException {
            ScoreNode s = sn[getSelectorIndex(selectorName)];
            if (s == null) {
                // TODO correct?
                return null;
            }
            return (Node) itemMgr.getItem(s.getNodeId());
        }

        /**
         * Equivalent to <code>Row.getNode().getPath()</code>. However, some
         * implementations may be able gain efficiency by not resolving the actual
         * <code>Node</code>.
         *
         * @return a <code>String</code>
         * @throws RepositoryException if this query has more than one selector
         * (and therefore, this <code>Row</code> corresponds to more than one
         * <code>Node</code>) or if another error occurs.
         * @since JCR 2.0
         */
        public String getPath() throws RepositoryException {
            checkSingleSelector("Use getPath(String) instead.");
            return node.getPath();
        }

        /**
         * Equivalent to <code>Row.getNode(selectorName).getPath()</code>. However, some
         * implementations may be able gain efficiency by not resolving the actual
         * <code>Node</code>.
         *
         * @param selectorName a <code>String</code>
         * @return a <code>String</code>
         * @throws RepositoryException if <code>selectorName</code> is not the alias
         * of a selector in this query or if another error occurs.
         * @since JCR 2.0
         */
        public String getPath(String selectorName) throws RepositoryException {
            Node n = getNode(selectorName);
            if (n != null) {
                return n.getPath();
            } else {
                return null;
            }
        }

        /**
         * Returns the full text search score for this row associated with the
         * default selector. This corresponds to the score of a particular node.
         * <p/>
         * If no <code>FullTextSearchScore</code> AQM object is associated with the
         * default selector this method will still return a value. However, in that
         * case the returned value may not be meaningful or may simply reflect the
         * minimum possible relevance level (for example, in some systems this might
         * be a score of 0).
         * <p/>
         * Note, in JCR-SQL2 a <code>FullTextSearchScore</code> AQM object is represented
         * by a <code>SCORE()</code> function. In JCR-JQOM it is represented by a
         * Java object of type <code>javax.jcr.query.qom.FullTextSearchScore</code>.
         *
         * @return a <code>double</code>
         * @throws RepositoryException if this query has more than one selector
         * (and therefore, this <code>Row</code> corresponds to more than one
         * <code>Node</code>) or if another error occurs.
         * @since JCR 2.0
         */
        public double getScore() throws RepositoryException {
            checkSingleSelector("Use getScore(String) instead.");
            return score;
        }

        /**
         * Returns the full text search score for this row associated with the
         * specified selector. This corresponds to the score of a particular node.
         * <p/>
         * If no <code>FullTextSearchScore</code> AQM object is associated with the
         * selector <code>selectorName</code> this method will still return a value.
         * However, in that case the returned value may not be meaningful or may
         * simply reflect the minimum possible relevance level (for example, in some
         * systems this might be a score of 0).
         * <p/>
         * Note, in JCR-SQL2 a <code>FullTextSearchScore</code> AQM object is represented
         * by a <code>SCORE()</code> function. In JCR-JQOM it is represented by a
         * Java object of type <code>javax.jcr.query.qom.FullTextSearchScore</code>.
         *
         * @param selectorName a <code>String</code>
         * @return a <code>String</code>
         * @throws RepositoryException if <code>selectorName</code> is not the alias
         * of a selector in this query or if another error occurs.
         * @since JCR 2.0
         */
        public double getScore(String selectorName) throws RepositoryException {
            ScoreNode s = sn[getSelectorIndex(selectorName)];
            if (s == null) {
                // TODO correct?
                return Double.NaN;
            }
            return s.getScore();
        }

        //-----------------------------< internal >-----------------------------

        /**
         * Checks if there is a single selector and otherwise throws a
         * RepositoryException.
         *
         * @param useInstead message telling, which method to use instead.
         * @throws RepositoryException if there is more than one selector.
         */
        private void checkSingleSelector(String useInstead) throws RepositoryException {
            if (sn.length > 1) {
                String msg = "More than one selector. " + useInstead;
                throw new RepositoryException(msg);
            }
        }

        /**
         * Gets the selector index for the given <code>selectorName</code>.
         *
         * @param selectorName the selector name.
         * @return the selector index.
         * @throws RepositoryException if the selector name is not a valid JCR
         *                             name or the selector name is not the
         *                             alias of a selector in this query.
         */
        private int getSelectorIndex(String selectorName)
                throws RepositoryException {
            int idx = selectorNames.indexOf(resolver.getQName(selectorName));
            if (idx == -1) {
                throw new RepositoryException("Unknown selector name: " + selectorName);
            }
            return idx;
        }

        /**
         * @param name a Name.
         * @return <code>true</code> if <code>name</code> is the rep:excerpt
         *         function, <code>false</code> otherwise.
         */
        private boolean isExcerptFunction(Name name) {
            return name.getNamespaceURI().equals(Name.NS_REP_URI)
                && name.getLocalName().startsWith(EXCERPT_FUNC_LPAR);
        }

        /**
         * @param name a String.
         * @return <code>true</code> if <code>name</code> is the rep:excerpt
         *         function, <code>false</code> otherwise.
         */
        private boolean isExcerptFunction(String name) {
            try {
                return name.startsWith(
                        resolver.getJCRName(REP_EXCERPT_LPAR));
            } catch (NamespaceException e) {
                // will never happen
                return false;
            }
        }

        /**
         * Returns an excerpt for the node associated with this row.
         *
         * @return a StringValue or <code>null</code> if the excerpt cannot be
         *         created or an error occurs.
         */
        private Value getExcerpt() {
            return createExcerpt(node.getNodeId());
        }

        /**
         * Returns an excerpt for the node indicated by the relative path
         * parameter of the rep:excerpt function. The relative path is resolved
         * against the node associated with this row.
         *
         * @param excerptCall the rep:excerpt function with the parameter as
         *                    string.
         * @return a StringValue or <code>null</code> if the excerpt cannot be
         *         created or an error occurs.
         * @throws RepositoryException if the function call is not well-formed.
         */
        private Value getExcerpt(String excerptCall) throws RepositoryException {
            int idx = excerptCall.indexOf(EXCERPT_FUNC_LPAR);
            int end = excerptCall.lastIndexOf(')');
            if (end == -1) {
                throw new RepositoryException("Missing right parenthesis");
            }
            String pathStr = excerptCall.substring(
                    idx + EXCERPT_FUNC_LPAR.length(), end).trim();
            String decodedPath = ISO9075.decode(pathStr);
            try {
                NodeImpl n = (NodeImpl) node.getNode(decodedPath);
                return createExcerpt(n.getNodeId());
            } catch (PathNotFoundException e) {
                // does not exist or references a property
                try {
                    Property p = node.getProperty(decodedPath);
                    return highlight(p.getValue().getString());
                } catch (PathNotFoundException e1) {
                    // does not exist
                    return null;
                }
            }
        }

        /**
         * Creates an excerpt for node with the given <code>id</code>.
         *
         * @return a StringValue or <code>null</code> if the excerpt cannot be
         *         created or an error occurs.
         */
        private Value createExcerpt(NodeId id) {
            if (excerptProvider == null) {
                return null;
            }
            try {
                long time = System.currentTimeMillis();
                String excerpt = excerptProvider.getExcerpt(id, 3, 150);
                time = System.currentTimeMillis() - time;
                log.debug("Created excerpt in {} ms.", new Long(time));
                if (excerpt != null) {
                    return VALUE_FACTORY.createValue(excerpt);
                } else {
                    return null;
                }
            } catch (IOException e) {
                return null;
            }
        }

        /**
         * Highlights the matching terms in the passed <code>text</code>.
         *
         * @return a StringValue or <code>null</code> if highlighting fails.
         */
        private Value highlight(String text) {
            if (!(excerptProvider instanceof HighlightingExcerptProvider)) {
                return null;
            }
            HighlightingExcerptProvider hep =
                    (HighlightingExcerptProvider) excerptProvider;
            try {
                long time = System.currentTimeMillis();
                text = hep.highlight(text);
                time = System.currentTimeMillis() - time;
                log.debug("Highlighted text in {} ms.", new Long(time));
                return VALUE_FACTORY.createValue(text);
            } catch (IOException e) {
                return null;
            }
        }

        /**
         * @param name a Name.
         * @return <code>true</code> if <code>name</code> is the rep:spellcheck
         *         function, <code>false</code> otherwise.
         */
        private boolean isSpellCheckFunction(Name name) {
            return name.getNamespaceURI().equals(Name.NS_REP_URI)
                && name.getLocalName().startsWith(SPELLCHECK_FUNC_LPAR);
        }

        /**
         * Returns the spell checked string of the first relation query node
         * with a spellcheck operation.
         *
         * @return a StringValue or <code>null</code> if the spell checker
         *         thinks the words are spelled correctly. This method also
         *         returns <code>null</code> if no spell checker is configured.
         */
        private Value getSpellCheckedStatement() {
            String v = null;
            if (spellSuggestion != null) {
                try {
                    v = spellSuggestion.getSuggestion();
                } catch (IOException e) {
                    log.warn("Spell checking failed", e);
                }
            }
            if (v != null) {
                return VALUE_FACTORY.createValue(v);
            } else {
                return null;
            }
        }
    }
}
