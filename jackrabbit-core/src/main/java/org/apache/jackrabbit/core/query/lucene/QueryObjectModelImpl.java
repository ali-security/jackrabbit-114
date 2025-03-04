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

import org.apache.jackrabbit.core.query.PropertyTypeRegistry;
import org.apache.jackrabbit.spi.commons.query.jsr283.qom.QueryObjectModelConstants;
import org.apache.jackrabbit.spi.commons.query.qom.QueryObjectModelTree;
import org.apache.jackrabbit.spi.commons.query.qom.ColumnImpl;
import org.apache.jackrabbit.spi.commons.query.qom.OrderingImpl;
import org.apache.jackrabbit.spi.commons.query.qom.DefaultTraversingQOMTreeVisitor;
import org.apache.jackrabbit.spi.commons.query.qom.BindVariableValueImpl;
import org.apache.jackrabbit.spi.commons.query.qom.SelectorImpl;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.ItemManager;
import org.apache.jackrabbit.spi.Name;
import org.apache.lucene.search.Query;

import javax.jcr.RepositoryException;
import javax.jcr.query.QueryResult;

/**
 * <code>QueryObjectModelImpl</code>...
 */
public class QueryObjectModelImpl extends AbstractQueryImpl {

    /**
     * The query object model tree.
     */
    private final QueryObjectModelTree qomTree;

    /**
     * Creates a new query instance from a query string.
     *
     * @param session the session of the user executing this query.
     * @param itemMgr the item manager of the session executing this query.
     * @param index   the search index.
     * @param propReg the property type registry.
     * @param qomTree the query object model tree.
     */
    public QueryObjectModelImpl(SessionImpl session,
                                ItemManager itemMgr,
                                SearchIndex index,
                                PropertyTypeRegistry propReg,
                                QueryObjectModelTree qomTree) {
        super(session, itemMgr, index, propReg);
        this.qomTree = qomTree;
        extractBindVariableNames();
    }

    /**
     * Returns <code>true</code> if this query node needs items under
     * /jcr:system to be queried.
     *
     * @return <code>true</code> if this query node needs content under
     *         /jcr:system to be queried; <code>false</code> otherwise.
     */
    public boolean needsSystemTree() {
        // TODO: analyze QOM tree
        return true;
    }

    /**
     * {@inheritDoc}
     */ 
    public Name[] getSelectorNames() {
        SelectorImpl[] selectors = qomTree.getSource().getSelectors();
        Name[] names = new Name[selectors.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = selectors[i].getSelectorQName();
        };
        return names;
    }

    //-------------------------< ExecutableQuery >------------------------------

    /**
     * Executes this query and returns a <code>{@link javax.jcr.query.QueryResult}</code>.
     *
     * @param offset the offset in the total result set
     * @param limit  the maximum result size
     * @return a <code>QueryResult</code>
     * @throws RepositoryException if an error occurs
     */
    public QueryResult execute(long offset, long limit)
            throws RepositoryException {
        Query query = JQOM2LuceneQueryBuilder.createQuery(qomTree, session,
                index.getContext().getItemStateManager(),
                index.getNamespaceMappings(), index.getTextAnalyzer(),
                propReg, index.getSynonymProvider(), getBindVariableValues(),
                index.getIndexFormatVersion());

        ColumnImpl[] columns = qomTree.getColumns();
        Name[] selectProps = new Name[columns.length];
        for (int i = 0; i < columns.length; i++) {
            selectProps[i] = columns[i].getPropertyQName();
        }
        OrderingImpl[] orderings = qomTree.getOrderings();
        // TODO: there are many kinds of DynamicOperand that can be ordered by
        Name[] orderProps = new Name[orderings.length];
        boolean[] orderSpecs = new boolean[orderings.length];
        for (int i = 0; i < orderings.length; i++) {
            orderSpecs[i] = orderings[i].getOrder() == QueryObjectModelConstants.ORDER_ASCENDING;
        }
        return new QueryResultImpl(index, itemMgr,
                session, session.getAccessManager(),
                // TODO: spell suggestion missing
                this, query, null, selectProps, orderProps, orderSpecs,
                getRespectDocumentOrder(), offset, limit);
    }

    //--------------------------< internal >------------------------------------

    /**
     * Extracts all {@link BindVariableValueImpl} from the {@link #qomTree}
     * and adds it to the set of known variable names.
     */
    private void extractBindVariableNames() {
        try {
            qomTree.accept(new DefaultTraversingQOMTreeVisitor() {
                public Object visit(BindVariableValueImpl node, Object data) {
                    addVariableName(node.getBindVariableQName());
                    return data;
                }
            }, null);
        } catch (Exception e) {
            // will never happen
        }
    }
}
