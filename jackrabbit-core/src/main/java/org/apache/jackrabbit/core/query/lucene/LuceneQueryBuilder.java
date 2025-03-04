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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import javax.jcr.NamespaceException;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeIterator;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.query.InvalidQueryException;

import org.apache.jackrabbit.core.HierarchyManager;
import org.apache.jackrabbit.core.HierarchyManagerImpl;
import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.SearchManager;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.query.PropertyTypeRegistry;
import org.apache.jackrabbit.core.query.lucene.fulltext.ParseException;
import org.apache.jackrabbit.core.query.lucene.fulltext.QueryParser;
import org.apache.jackrabbit.core.state.ItemStateManager;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.Path;
import org.apache.jackrabbit.spi.commons.conversion.MalformedPathException;
import org.apache.jackrabbit.spi.commons.conversion.NameException;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.PathBuilder;
import org.apache.jackrabbit.spi.commons.query.AndQueryNode;
import org.apache.jackrabbit.spi.commons.query.DefaultQueryNodeVisitor;
import org.apache.jackrabbit.spi.commons.query.DerefQueryNode;
import org.apache.jackrabbit.spi.commons.query.ExactQueryNode;
import org.apache.jackrabbit.spi.commons.query.LocationStepQueryNode;
import org.apache.jackrabbit.spi.commons.query.NodeTypeQueryNode;
import org.apache.jackrabbit.spi.commons.query.NotQueryNode;
import org.apache.jackrabbit.spi.commons.query.OrQueryNode;
import org.apache.jackrabbit.spi.commons.query.OrderQueryNode;
import org.apache.jackrabbit.spi.commons.query.PathQueryNode;
import org.apache.jackrabbit.spi.commons.query.PropertyFunctionQueryNode;
import org.apache.jackrabbit.spi.commons.query.QueryConstants;
import org.apache.jackrabbit.spi.commons.query.QueryNode;
import org.apache.jackrabbit.spi.commons.query.QueryNodeVisitor;
import org.apache.jackrabbit.spi.commons.query.QueryRootNode;
import org.apache.jackrabbit.spi.commons.query.RelationQueryNode;
import org.apache.jackrabbit.spi.commons.query.TextsearchQueryNode;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.util.XMLChar;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a query builder that takes an abstract query tree and creates
 * a lucene {@link org.apache.lucene.search.Query} tree that can be executed
 * on an index.
 * todo introduce a node type hierarchy for efficient translation of NodeTypeQueryNode
 */
public class LuceneQueryBuilder implements QueryNodeVisitor {

    /**
     * Logger for this class
     */
    private static final Logger log = LoggerFactory.getLogger(LuceneQueryBuilder.class);

    /**
     * Root node of the abstract query tree
     */
    private final QueryRootNode root;

    /**
     * Session of the user executing this query
     */
    private final SessionImpl session;

    /**
     * The shared item state manager of the workspace.
     */
    private final ItemStateManager sharedItemMgr;

    /**
     * A hierarchy manager based on {@link #sharedItemMgr} to resolve paths.
     */
    private final HierarchyManager hmgr;

    /**
     * Namespace mappings to internal prefixes
     */
    private final NamespaceMappings nsMappings;

    /**
     * Name and Path resolver
     */
    private final NamePathResolver resolver;

    /**
     * The analyzer instance to use for contains function query parsing
     */
    private final Analyzer analyzer;

    /**
     * The property type registry.
     */
    private final PropertyTypeRegistry propRegistry;

    /**
     * The synonym provider or <code>null</code> if none is configured.
     */
    private final SynonymProvider synonymProvider;

    /**
     * Wether the index format is new or old.
     */
    private final IndexFormatVersion indexFormatVersion;

    /**
     * Exceptions thrown during tree translation
     */
    private final List exceptions = new ArrayList();

    /**
     * Creates a new <code>LuceneQueryBuilder</code> instance.
     *
     * @param root               the root node of the abstract query tree.
     * @param session            of the user executing this query.
     * @param sharedItemMgr      the shared item state manager of the
     *                           workspace.
     * @param hmgr               a hierarchy manager based on sharedItemMgr.
     * @param nsMappings         namespace resolver for internal prefixes.
     * @param analyzer           for parsing the query statement of the contains
     *                           function.
     * @param propReg            the property type registry.
     * @param synonymProvider    the synonym provider or <code>null</code> if
     *                           node is configured.
     * @param indexFormatVersion the index format version for the lucene query.
     */
    private LuceneQueryBuilder(QueryRootNode root,
                               SessionImpl session,
                               ItemStateManager sharedItemMgr,
                               HierarchyManager hmgr,
                               NamespaceMappings nsMappings,
                               Analyzer analyzer,
                               PropertyTypeRegistry propReg,
                               SynonymProvider synonymProvider,
                               IndexFormatVersion indexFormatVersion) {
        this.root = root;
        this.session = session;
        this.sharedItemMgr = sharedItemMgr;
        this.hmgr = hmgr;
        this.nsMappings = nsMappings;
        this.analyzer = analyzer;
        this.propRegistry = propReg;
        this.synonymProvider = synonymProvider;
        this.indexFormatVersion = indexFormatVersion;

        this.resolver = NamePathResolverImpl.create(nsMappings);
    }

    /**
     * Creates a lucene {@link org.apache.lucene.search.Query} tree from an
     * abstract query tree.
     *
     * @param root            the root node of the abstract query tree.
     * @param session         of the user executing the query.
     * @param sharedItemMgr   the shared item state manager of the workspace.
     * @param nsMappings      namespace resolver for internal prefixes.
     * @param analyzer        for parsing the query statement of the contains
     *                        function.
     * @param propReg         the property type registry to lookup type
     *                        information.
     * @param synonymProvider the synonym provider or <code>null</code> if node
     *                        is configured.
     * @param  indexFormatVersion  the index format version to be used
     * @return the lucene query tree.
     * @throws RepositoryException if an error occurs during the translation.
     */
    public static Query createQuery(QueryRootNode root,
                                    SessionImpl session,
                                    ItemStateManager sharedItemMgr,
                                    NamespaceMappings nsMappings,
                                    Analyzer analyzer,
                                    PropertyTypeRegistry propReg,
                                    SynonymProvider synonymProvider,
                                    IndexFormatVersion indexFormatVersion)
            throws RepositoryException {
        HierarchyManager hmgr = new HierarchyManagerImpl(
                RepositoryImpl.ROOT_NODE_ID, sharedItemMgr);
        LuceneQueryBuilder builder = new LuceneQueryBuilder(
                root, session, sharedItemMgr, hmgr, nsMappings,
                analyzer, propReg, synonymProvider, indexFormatVersion);

        Query q = builder.createLuceneQuery();
        if (builder.exceptions.size() > 0) {
            StringBuffer msg = new StringBuffer();
            for (Iterator it = builder.exceptions.iterator(); it.hasNext();) {
                msg.append(it.next().toString()).append('\n');
            }
            throw new RepositoryException("Exception building query: " + msg.toString());
        }
        return q;
    }

    /**
     * Starts the tree traversal and returns the lucene
     * {@link org.apache.lucene.search.Query}.
     *
     * @return the lucene <code>Query</code>.
     * @throws RepositoryException
     */
    private Query createLuceneQuery() throws RepositoryException {
        return (Query) root.accept(this, null);
    }

    //---------------------< QueryNodeVisitor interface >-----------------------

    public Object visit(QueryRootNode node, Object data) throws RepositoryException {
        BooleanQuery root = new BooleanQuery();

        Query wrapped = root;
        if (node.getLocationNode() != null) {
            wrapped = (Query) node.getLocationNode().accept(this, root);
        }

        return wrapped;
    }

    public Object visit(OrQueryNode node, Object data) throws RepositoryException {
        BooleanQuery orQuery = new BooleanQuery();
        Object[] result = node.acceptOperands(this, null);
        for (int i = 0; i < result.length; i++) {
            Query operand = (Query) result[i];
            orQuery.add(operand, Occur.SHOULD);
        }
        return orQuery;
    }

    public Object visit(AndQueryNode node, Object data) throws RepositoryException {
        Object[] result = node.acceptOperands(this, null);
        if (result.length == 0) {
            return null;
        }
        BooleanQuery andQuery = new BooleanQuery();
        for (int i = 0; i < result.length; i++) {
            Query operand = (Query) result[i];
            andQuery.add(operand, Occur.MUST);
        }
        return andQuery;
    }

    public Object visit(NotQueryNode node, Object data) throws RepositoryException {
        Object[] result = node.acceptOperands(this, null);
        if (result.length == 0) {
            return data;
        }
        // join the results
        BooleanQuery b = new BooleanQuery();
        for (int i = 0; i < result.length; i++) {
            b.add((Query) result[i], Occur.SHOULD);
        }
        // negate
        return new NotQuery(b);
    }

    public Object visit(ExactQueryNode node, Object data) {
        String field = "";
        String value = "";
        try {
            field = resolver.getJCRName(node.getPropertyName());
            value = resolver.getJCRName(node.getValue());
        } catch (NamespaceException e) {
            // will never happen, prefixes are created when unknown
        }
        return new TermQuery(new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, value)));
    }

    public Object visit(NodeTypeQueryNode node, Object data) {

        List terms = new ArrayList();
        try {
            String mixinTypesField = resolver.getJCRName(NameConstants.JCR_MIXINTYPES);
            String primaryTypeField = resolver.getJCRName(NameConstants.JCR_PRIMARYTYPE);

            NodeTypeManager ntMgr = session.getWorkspace().getNodeTypeManager();
            NodeType base = ntMgr.getNodeType(session.getJCRName(node.getValue()));

            if (base.isMixin()) {
                // search for nodes where jcr:mixinTypes is set to this mixin
                Term t = new Term(FieldNames.PROPERTIES,
                        FieldNames.createNamedValue(mixinTypesField,
                                resolver.getJCRName(node.getValue())));
                terms.add(t);
            } else {
                // search for nodes where jcr:primaryType is set to this type
                Term t = new Term(FieldNames.PROPERTIES,
                        FieldNames.createNamedValue(primaryTypeField,
                                resolver.getJCRName(node.getValue())));
                terms.add(t);
            }

            // now search for all node types that are derived from base
            NodeTypeIterator allTypes = ntMgr.getAllNodeTypes();
            while (allTypes.hasNext()) {
                NodeType nt = allTypes.nextNodeType();
                NodeType[] superTypes = nt.getSupertypes();
                if (Arrays.asList(superTypes).contains(base)) {
                    Name n = session.getQName(nt.getName());
                    String ntName = nsMappings.translatePropertyName(n);
                    Term t;
                    if (nt.isMixin()) {
                        // search on jcr:mixinTypes
                        t = new Term(FieldNames.PROPERTIES,
                                FieldNames.createNamedValue(mixinTypesField, ntName));
                    } else {
                        // search on jcr:primaryType
                        t = new Term(FieldNames.PROPERTIES,
                                FieldNames.createNamedValue(primaryTypeField, ntName));
                    }
                    terms.add(t);
                }
            }
        } catch (NameException e) {
            exceptions.add(e);
        } catch (RepositoryException e) {
            exceptions.add(e);
        }
        if (terms.size() == 0) {
            // exception occured
            return new BooleanQuery();
        } else if (terms.size() == 1) {
            return new TermQuery((Term) terms.get(0));
        } else {
            BooleanQuery b = new BooleanQuery();
            for (Iterator it = terms.iterator(); it.hasNext();) {
                b.add(new TermQuery((Term) it.next()), Occur.SHOULD);
            }
            return b;
        }
    }

    public Object visit(TextsearchQueryNode node, Object data) {
        try {
            Path relPath = node.getRelativePath();
            String fieldname;
            if (relPath == null || !node.getReferencesProperty()) {
                // fulltext on node
                fieldname = FieldNames.FULLTEXT;
            } else {
                // final path element is a property name
                Name propName = relPath.getNameElement().getName();
                StringBuffer tmp = new StringBuffer();
                tmp.append(nsMappings.getPrefix(propName.getNamespaceURI()));
                tmp.append(":").append(FieldNames.FULLTEXT_PREFIX);
                tmp.append(propName.getLocalName());
                fieldname = tmp.toString();
            }
            QueryParser parser = new QueryParser(
                    fieldname, analyzer, synonymProvider);
            parser.setOperator(QueryParser.DEFAULT_OPERATOR_AND);
            // replace escaped ' with just '
            StringBuffer query = new StringBuffer();
            String textsearch = node.getQuery();
            // the default lucene query parser recognizes 'AND' and 'NOT' as
            // keywords.
            textsearch = textsearch.replaceAll("AND", "and");
            textsearch = textsearch.replaceAll("NOT", "not");
            boolean escaped = false;
            for (int i = 0; i < textsearch.length(); i++) {
                if (textsearch.charAt(i) == '\\') {
                    if (escaped) {
                        query.append("\\\\");
                        escaped = false;
                    } else {
                        escaped = true;
                    }
                } else if (textsearch.charAt(i) == '\'') {
                    if (escaped) {
                        escaped = false;
                    }
                    query.append(textsearch.charAt(i));
                } else {
                    if (escaped) {
                        query.append('\\');
                        escaped = false;
                    }
                    query.append(textsearch.charAt(i));
                }
            }
            Query context = parser.parse(query.toString());
            if (relPath != null && (!node.getReferencesProperty() || relPath.getLength() > 1)) {
                // text search on some child axis
                Path.Element[] elements = relPath.getElements();
                for (int i = elements.length - 1; i >= 0; i--) {
                    Name name = null;
                    if (!elements[i].getName().equals(RelationQueryNode.STAR_NAME_TEST)) {
                        name = elements[i].getName();
                    }
                    // join text search with name test
                    // if path references property that's elements.length - 2
                    // if path references node that's elements.length - 1
                    if (name != null
                            && ((node.getReferencesProperty() && i == elements.length - 2)
                                || (!node.getReferencesProperty() && i == elements.length - 1))) {
                        Query q = new NameQuery(name, indexFormatVersion, nsMappings);
                        BooleanQuery and = new BooleanQuery();
                        and.add(q, Occur.MUST);
                        and.add(context, Occur.MUST);
                        context = and;
                    } else if ((node.getReferencesProperty() && i < elements.length - 2)
                            || (!node.getReferencesProperty() && i < elements.length - 1)) {
                        // otherwise do a parent axis step
                        context = new ParentAxisQuery(context, name,
                                indexFormatVersion, nsMappings);
                    }
                }
                // finally select parent
                context = new ParentAxisQuery(context, null,
                        indexFormatVersion, nsMappings);
            }
            return context;
        } catch (NamespaceException e) {
            exceptions.add(e);
        } catch (ParseException e) {
            exceptions.add(e);
        }
        return null;
    }

    public Object visit(PathQueryNode node, Object data) throws RepositoryException {
        Query context = null;
        LocationStepQueryNode[] steps = node.getPathSteps();
        if (steps.length > 0) {
            if (node.isAbsolute() && !steps[0].getIncludeDescendants()) {
                // eat up first step
                Name nameTest = steps[0].getNameTest();
                if (nameTest == null) {
                    // this is equivalent to the root node
                    context = new TermQuery(new Term(FieldNames.PARENT, ""));
                } else if (nameTest.getLocalName().length() == 0) {
                    // root node
                    context = new TermQuery(new Term(FieldNames.PARENT, ""));
                } else {
                    // then this is a node != the root node
                    // will never match anything!
                    BooleanQuery and = new BooleanQuery();
                    and.add(new TermQuery(new Term(FieldNames.PARENT, "")), Occur.MUST);
                    and.add(new NameQuery(nameTest, indexFormatVersion, nsMappings), Occur.MUST);
                    context = and;
                }
                LocationStepQueryNode[] tmp = new LocationStepQueryNode[steps.length - 1];
                System.arraycopy(steps, 1, tmp, 0, steps.length - 1);
                steps = tmp;
            } else {
                // path is 1) relative or 2) descendant-or-self
                // use root node as context
                context = new TermQuery(new Term(FieldNames.PARENT, ""));
            }
        } else {
            exceptions.add(new InvalidQueryException("Number of location steps must be > 0"));
        }
        // loop over steps
        for (int i = 0; i < steps.length; i++) {
            context = (Query) steps[i].accept(this, context);
        }
        if (data instanceof BooleanQuery) {
            BooleanQuery constraint = (BooleanQuery) data;
            if (constraint.getClauses().length > 0) {
                constraint.add(context, Occur.MUST);
                context = constraint;
            }
        }
        return context;
    }

    public Object visit(LocationStepQueryNode node, Object data) throws RepositoryException {
        Query context = (Query) data;
        BooleanQuery andQuery = new BooleanQuery();

        if (context == null) {
            exceptions.add(new IllegalArgumentException("Unsupported query"));
        }

        // predicate on step?
        Object[] predicates = node.acceptOperands(this, data);
        for (int i = 0; i < predicates.length; i++) {
            andQuery.add((Query) predicates[i], Occur.MUST);
        }

        // check for position predicate
        QueryNode[] pred = node.getPredicates();
        for (int i = 0; i < pred.length; i++) {
            if (pred[i].getType() == QueryNode.TYPE_RELATION) {
                RelationQueryNode pos = (RelationQueryNode) pred[i];
                if (pos.getValueType() == QueryConstants.TYPE_POSITION) {
                    node.setIndex(pos.getPositionValue());
                }
            }
        }

        NameQuery nameTest = null;
        if (node.getNameTest() != null) {
            nameTest = new NameQuery(node.getNameTest(), indexFormatVersion, nsMappings);
        }

        if (node.getIncludeDescendants()) {
            if (nameTest != null) {
                andQuery.add(new DescendantSelfAxisQuery(context, nameTest, false), Occur.MUST);
            } else {
                // descendant-or-self with nametest=*
                if (predicates.length > 0) {
                    // if we have a predicate attached, the condition acts as
                    // the sub query.

                    // only use descendant axis if path is not //*
                    // otherwise the query for the predicate can be used itself
                    PathQueryNode pathNode = (PathQueryNode) node.getParent();
                    if (pathNode.getPathSteps()[0] != node) {
                        Query subQuery = new DescendantSelfAxisQuery(context, andQuery, false);
                        andQuery = new BooleanQuery();
                        andQuery.add(subQuery, Occur.MUST);
                    }
                } else {
                    // todo this will traverse the whole index, optimize!
                    // only use descendant axis if path is not //*
                    PathQueryNode pathNode = (PathQueryNode) node.getParent();
                    if (pathNode.getPathSteps()[0] != node) {
                        if (node.getIndex() == LocationStepQueryNode.NONE) {
                            context = new DescendantSelfAxisQuery(context, false);
                            andQuery.add(context, Occur.MUST);
                        } else {
                            context = new DescendantSelfAxisQuery(context, true);
                            andQuery.add(new ChildAxisQuery(sharedItemMgr,
                                    context, null, node.getIndex(),
                                    indexFormatVersion, nsMappings), Occur.MUST);
                        }
                    } else {
                        andQuery.add(new MatchAllDocsQuery(), Occur.MUST);
                    }
                }
            }
        } else {
            // name test
            if (nameTest != null) {
                andQuery.add(new ChildAxisQuery(sharedItemMgr, context,
                        nameTest.getName(), node.getIndex(), indexFormatVersion,
                        nsMappings), Occur.MUST);
            } else {
                // select child nodes
                andQuery.add(new ChildAxisQuery(sharedItemMgr, context, null,
                        node.getIndex(), indexFormatVersion, nsMappings),
                        Occur.MUST);
            }
        }

        return andQuery;
    }

    public Object visit(DerefQueryNode node, Object data) throws RepositoryException {
        Query context = (Query) data;
        if (context == null) {
            exceptions.add(new IllegalArgumentException("Unsupported query"));
        }

        try {
            String refProperty = resolver.getJCRName(node.getRefProperty());

            if (node.getIncludeDescendants()) {
                Query refPropQuery = Util.createMatchAllQuery(refProperty, indexFormatVersion);
                context = new DescendantSelfAxisQuery(context, refPropQuery, false);
            }

            context = new DerefQuery(context, refProperty, node.getNameTest(),
                    indexFormatVersion, nsMappings);

            // attach predicates
            Object[] predicates = node.acceptOperands(this, data);
            if (predicates.length > 0) {
                BooleanQuery andQuery = new BooleanQuery();
                for (int i = 0; i < predicates.length; i++) {
                    andQuery.add((Query) predicates[i], Occur.MUST);
                }
                andQuery.add(context, Occur.MUST);
                context = andQuery;
            }

        } catch (NamespaceException e) {
            // should never happen
            exceptions.add(e);
        }

        return context;
    }

    public Object visit(RelationQueryNode node, Object data) throws RepositoryException {
        Query query;
        String[] stringValues = new String[1];
        switch (node.getValueType()) {
            case 0:
                // not set: either IS NULL or IS NOT NULL
                break;
            case QueryConstants.TYPE_DATE:
                stringValues[0] = DateField.dateToString(node.getDateValue());
                break;
            case QueryConstants.TYPE_DOUBLE:
                stringValues[0] = DoubleField.doubleToString(node.getDoubleValue());
                break;
            case QueryConstants.TYPE_LONG:
                stringValues[0] = LongField.longToString(node.getLongValue());
                break;
            case QueryConstants.TYPE_STRING:
                if (node.getOperation() == QueryConstants.OPERATION_EQ_GENERAL
                        || node.getOperation() == QueryConstants.OPERATION_EQ_VALUE
                        || node.getOperation() == QueryConstants.OPERATION_NE_GENERAL
                        || node.getOperation() == QueryConstants.OPERATION_NE_VALUE) {
                    // only use coercing on non-range operations
                    Name propertyName = node.getRelativePath().getNameElement().getName();
                    stringValues = getStringValues(propertyName, node.getStringValue());
                } else {
                    stringValues[0] = node.getStringValue();
                }
                break;
            case QueryConstants.TYPE_POSITION:
                // ignore position. is handled in the location step
                return null;
            default:
                throw new IllegalArgumentException("Unknown relation type: "
                        + node.getValueType());
        }

        if (node.getRelativePath() == null
                && node.getOperation() != QueryConstants.OPERATION_SIMILAR
                && node.getOperation() != QueryConstants.OPERATION_SPELLCHECK) {
            exceptions.add(new InvalidQueryException("@* not supported in predicate"));
            return data;
        }

        // get property transformation
        final int[] transform = new int[]{TransformConstants.TRANSFORM_NONE};
        node.acceptOperands(new DefaultQueryNodeVisitor() {
            public Object visit(PropertyFunctionQueryNode node, Object data) {
                if (node.getFunctionName().equals(PropertyFunctionQueryNode.LOWER_CASE)) {
                    transform[0] = TransformConstants.TRANSFORM_LOWER_CASE;
                } else if (node.getFunctionName().equals(PropertyFunctionQueryNode.UPPER_CASE)) {
                    transform[0] = TransformConstants.TRANSFORM_UPPER_CASE;
                }
                return data;
            }
        }, null);

        Path relPath = node.getRelativePath();
        if (node.getOperation() == QueryConstants.OPERATION_SIMILAR) {
            // this is a bit ugly:
            // add the name of a dummy property because relPath actually
            // references a property. whereas the relPath of the similar
            // operation references a node
            PathBuilder builder;
            if (relPath == null) {
                builder = new PathBuilder();
            } else {
                builder = new PathBuilder(relPath);
            }
            builder.addLast(NameConstants.JCR_PRIMARYTYPE);
            try {
                relPath = builder.getPath();
            } catch (MalformedPathException e) {
                // will never happen
            }
        }
        String field = "";
        try {
            field = resolver.getJCRName(relPath.getNameElement().getName());
        } catch (NamespaceException e) {
            // should never happen
            exceptions.add(e);
        }

        // support for fn:name()
        Name propName = relPath.getNameElement().getName();
        if (propName.getNamespaceURI().equals(SearchManager.NS_FN_URI)
                && propName.getLocalName().equals("name()")) {
            if (node.getValueType() != QueryConstants.TYPE_STRING) {
                exceptions.add(new InvalidQueryException("Name function can "
                        + "only be used in conjunction with a string literal"));
                return data;
            }
            if (node.getOperation() != QueryConstants.OPERATION_EQ_VALUE
                    && node.getOperation() != QueryConstants.OPERATION_EQ_GENERAL) {
                exceptions.add(new InvalidQueryException("Name function can "
                        + "only be used in conjunction with an equals operator"));
                return data;
            }
            // check if string literal is a valid XML Name
            if (XMLChar.isValidName(node.getStringValue())) {
                // parse string literal as JCR Name
                try {
                    Name n = session.getQName(ISO9075.decode(node.getStringValue()));
                    query = new NameQuery(n, indexFormatVersion, nsMappings);
                } catch (NameException e) {
                    exceptions.add(e);
                    return data;
                } catch (NamespaceException e) {
                    exceptions.add(e);
                    return data;
                }
            } else {
                // will never match -> create dummy query
                query = new BooleanQuery();
            }
        } else {
            switch (node.getOperation()) {
                case QueryConstants.OPERATION_EQ_VALUE:      // =
                case QueryConstants.OPERATION_EQ_GENERAL:
                    BooleanQuery or = new BooleanQuery();
                    for (int i = 0; i < stringValues.length; i++) {
                        Term t = new Term(FieldNames.PROPERTIES,
                                    FieldNames.createNamedValue(field, stringValues[i]));
                        Query q;
                        if (transform[0] == TransformConstants.TRANSFORM_UPPER_CASE) {
                            q = new CaseTermQuery.Upper(t);
                        } else if (transform[0] == TransformConstants.TRANSFORM_LOWER_CASE) {
                            q = new CaseTermQuery.Lower(t);
                        } else {
                            q = new TermQuery(t);
                        }
                        or.add(q, Occur.SHOULD);
                    }
                    query = or;
                    if (node.getOperation() == QueryConstants.OPERATION_EQ_VALUE) {
                        query = createSingleValueConstraint(or, field);
                    }
                    break;
                case QueryConstants.OPERATION_GE_VALUE:      // >=
                case QueryConstants.OPERATION_GE_GENERAL:
                    or = new BooleanQuery();
                    for (int i = 0; i < stringValues.length; i++) {
                        Term lower = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, stringValues[i]));
                        Term upper = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, "\uFFFF"));
                        or.add(new RangeQuery(lower, upper, true, transform[0]), Occur.SHOULD);
                    }
                    query = or;
                    if (node.getOperation() == QueryConstants.OPERATION_GE_VALUE) {
                        query = createSingleValueConstraint(or, field);
                    }
                    break;
                case QueryConstants.OPERATION_GT_VALUE:      // >
                case QueryConstants.OPERATION_GT_GENERAL:
                    or = new BooleanQuery();
                    for (int i = 0; i < stringValues.length; i++) {
                        Term lower = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, stringValues[i]));
                        Term upper = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, "\uFFFF"));
                        or.add(new RangeQuery(lower, upper, false, transform[0]), Occur.SHOULD);
                    }
                    query = or;
                    if (node.getOperation() == QueryConstants.OPERATION_GT_VALUE) {
                        query = createSingleValueConstraint(or, field);
                    }
                    break;
                case QueryConstants.OPERATION_LE_VALUE:      // <=
                case QueryConstants.OPERATION_LE_GENERAL:      // <=
                    or = new BooleanQuery();
                    for (int i = 0; i < stringValues.length; i++) {
                        Term lower = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, ""));
                        Term upper = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, stringValues[i]));
                        or.add(new RangeQuery(lower, upper, true, transform[0]), Occur.SHOULD);
                    }
                    query = or;
                    if (node.getOperation() == QueryConstants.OPERATION_LE_VALUE) {
                        query = createSingleValueConstraint(query, field);
                    }
                    break;
                case QueryConstants.OPERATION_LIKE:          // LIKE
                    // the like operation always has one string value.
                    // no coercing, see above
                    if (stringValues[0].equals("%")) {
                        query = Util.createMatchAllQuery(field, indexFormatVersion);
                    } else {
                        query = new WildcardQuery(FieldNames.PROPERTIES, field, stringValues[0], transform[0]);
                    }
                    break;
                case QueryConstants.OPERATION_LT_VALUE:      // <
                case QueryConstants.OPERATION_LT_GENERAL:
                    or = new BooleanQuery();
                    for (int i = 0; i < stringValues.length; i++) {
                        Term lower = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, ""));
                        Term upper = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, stringValues[i]));
                        or.add(new RangeQuery(lower, upper, false, transform[0]), Occur.SHOULD);
                    }
                    query = or;
                    if (node.getOperation() == QueryConstants.OPERATION_LT_VALUE) {
                        query = createSingleValueConstraint(or, field);
                    }
                    break;
                case QueryConstants.OPERATION_NE_VALUE:      // !=
                    // match nodes with property 'field' that includes svp and mvp
                    BooleanQuery notQuery = new BooleanQuery();
                    notQuery.add(Util.createMatchAllQuery(field, indexFormatVersion), Occur.SHOULD);
                    // exclude all nodes where 'field' has the term in question
                    for (int i = 0; i < stringValues.length; i++) {
                        Term t = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, stringValues[i]));
                        Query q;
                        if (transform[0] == TransformConstants.TRANSFORM_UPPER_CASE) {
                            q = new CaseTermQuery.Upper(t);
                        } else if (transform[0] == TransformConstants.TRANSFORM_LOWER_CASE) {
                            q = new CaseTermQuery.Lower(t);
                        } else {
                            q = new TermQuery(t);
                        }
                        notQuery.add(q, Occur.MUST_NOT);
                    }
                    // and exclude all nodes where 'field' is multi valued
                    notQuery.add(new TermQuery(new Term(FieldNames.MVP, field)), Occur.MUST_NOT);
                    query = notQuery;
                    break;
                case QueryConstants.OPERATION_NE_GENERAL:    // !=
                    // that's:
                    // all nodes with property 'field'
                    // minus the nodes that have a single property 'field' that is
                    //    not equal to term in question
                    // minus the nodes that have a multi-valued property 'field' and
                    //    all values are equal to term in question
                    notQuery = new BooleanQuery();
                    notQuery.add(Util.createMatchAllQuery(field, indexFormatVersion), Occur.SHOULD);
                    for (int i = 0; i < stringValues.length; i++) {
                        // exclude the nodes that have the term and are single valued
                        Term t = new Term(FieldNames.PROPERTIES, FieldNames.createNamedValue(field, stringValues[i]));
                        Query svp = new NotQuery(new TermQuery(new Term(FieldNames.MVP, field)));
                        BooleanQuery and = new BooleanQuery();
                        Query q;
                        if (transform[0] == TransformConstants.TRANSFORM_UPPER_CASE) {
                            q = new CaseTermQuery.Upper(t);
                        } else if (transform[0] == TransformConstants.TRANSFORM_LOWER_CASE) {
                            q = new CaseTermQuery.Lower(t);
                        } else {
                            q = new TermQuery(t);
                        }
                        and.add(q, Occur.MUST);
                        and.add(svp, Occur.MUST);
                        notQuery.add(and, Occur.MUST_NOT);
                    }
                    // todo above also excludes multi-valued properties that contain
                    //      multiple instances of only stringValues. e.g. text={foo, foo}
                    query = notQuery;
                    break;
                case QueryConstants.OPERATION_NULL:
                    query = new NotQuery(Util.createMatchAllQuery(field, indexFormatVersion));
                    break;
                case QueryConstants.OPERATION_SIMILAR:
                    try {
                        NodeId id = hmgr.resolveNodePath(session.getQPath(node.getStringValue()));
                        if (id != null) {
                            query = new SimilarityQuery(id.getUUID().toString(), analyzer);
                        } else {
                            query = new BooleanQuery();
                        }
                    } catch (Exception e) {
                        exceptions.add(e);
                        query = new BooleanQuery();
                    }
                    break;
                case QueryConstants.OPERATION_NOT_NULL:
                    query = Util.createMatchAllQuery(field, indexFormatVersion);
                    break;
                case QueryConstants.OPERATION_SPELLCHECK:
                    query = Util.createMatchAllQuery(field, indexFormatVersion);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown relation operation: "
                            + node.getOperation());
            }
        }

        if (relPath.getLength() > 1) {
            // child axis in relation
            Path.Element[] elements = relPath.getElements();
            // elements.length - 1 = property name
            // elements.length - 2 = last child axis name test
            for (int i = elements.length - 2; i >= 0; i--) {
                Name name = null;
                if (!elements[i].getName().equals(RelationQueryNode.STAR_NAME_TEST)) {
                    name = elements[i].getName();
                }
                if (i == elements.length - 2) {
                    // join name test with property query if there is one
                    if (name != null) {
                        Query nameTest = new NameQuery(name,
                                indexFormatVersion, nsMappings);
                        BooleanQuery and = new BooleanQuery();
                        and.add(query, Occur.MUST);
                        and.add(nameTest, Occur.MUST);
                        query = and;
                    } else {
                        // otherwise the query can be used as is
                    }
                } else {
                    query = new ParentAxisQuery(query, name,
                            indexFormatVersion, nsMappings);
                }
            }
            // finally select the parent of the selected nodes
            query = new ParentAxisQuery(query, null,
                    indexFormatVersion, nsMappings);
        }

        return query;
    }

    public Object visit(OrderQueryNode node, Object data) {
        return data;
    }

    public Object visit(PropertyFunctionQueryNode node, Object data) {
        return data;
    }

    //---------------------------< internal >-----------------------------------

    /**
     * Wraps a constraint query around <code>q</code> that limits the nodes to
     * those where <code>propName</code> is the name of a single value property
     * on the node instance.
     *
     * @param q        the query to wrap.
     * @param propName the name of a property that only has one value.
     * @return the wrapped query <code>q</code>.
     */
    private Query createSingleValueConstraint(Query q, String propName) {
        // get nodes with multi-values in propName
        Query mvp = new TermQuery(new Term(FieldNames.MVP, propName));
        // now negate, that gives the nodes that have propName as single
        // values but also all others
        Query svp = new NotQuery(mvp);
        // now join the two, which will result in those nodes where propName
        // only contains a single value. This works because q already restricts
        // the result to those nodes that have a property propName
        BooleanQuery and = new BooleanQuery();
        and.add(q, Occur.MUST);
        and.add(svp, Occur.MUST);
        return and;
    }

    /**
     * Returns an array of String values to be used as a term to lookup the search index
     * for a String <code>literal</code> of a certain property name. This method
     * will lookup the <code>propertyName</code> in the node type registry
     * trying to find out the {@link javax.jcr.PropertyType}s.
     * If no property type is found looking up node type information, this
     * method will guess the property type.
     *
     * @param propertyName the name of the property in the relation.
     * @param literal      the String literal in the relation.
     * @return the String values to use as term for the query.
     */
    private String[] getStringValues(Name propertyName, String literal) {
        PropertyTypeRegistry.TypeMapping[] types = propRegistry.getPropertyTypes(propertyName);
        List values = new ArrayList();
        for (int i = 0; i < types.length; i++) {
            switch (types[i].type) {
                case PropertyType.NAME:
                    // try to translate name
                    try {
                        Name n = session.getQName(literal);
                        values.add(nsMappings.translatePropertyName(n));
                        log.debug("Coerced " + literal + " into NAME.");
                    } catch (NameException e) {
                        log.debug("Unable to coerce '" + literal + "' into a NAME: " + e.toString());
                    } catch (NamespaceException e) {
                        log.debug("Unable to coerce '" + literal + "' into a NAME: " + e.toString());
                    }
                    break;
                case PropertyType.PATH:
                    // try to translate path
                    try {
                        Path p = session.getQPath(literal);
                        values.add(resolver.getJCRPath(p));
                        log.debug("Coerced " + literal + " into PATH.");
                    } catch (NameException e) {
                        log.debug("Unable to coerce '" + literal + "' into a PATH: " + e.toString());
                    } catch (NamespaceException e) {
                        log.debug("Unable to coerce '" + literal + "' into a PATH: " + e.toString());
                    }
                    break;
                case PropertyType.DATE:
                    // try to parse date
                    Calendar c = ISO8601.parse(literal);
                    if (c != null) {
                        values.add(DateField.timeToString(c.getTimeInMillis()));
                        log.debug("Coerced " + literal + " into DATE.");
                    } else {
                        log.debug("Unable to coerce '" + literal + "' into a DATE.");
                    }
                    break;
                case PropertyType.DOUBLE:
                    // try to parse double
                    try {
                        double d = Double.parseDouble(literal);
                        values.add(DoubleField.doubleToString(d));
                        log.debug("Coerced " + literal + " into DOUBLE.");
                    } catch (NumberFormatException e) {
                        log.debug("Unable to coerce '" + literal + "' into a DOUBLE: " + e.toString());
                    }
                    break;
                case PropertyType.LONG:
                    // try to parse long
                    try {
                        long l = Long.parseLong(literal);
                        values.add(LongField.longToString(l));
                        log.debug("Coerced " + literal + " into LONG.");
                    } catch (NumberFormatException e) {
                        log.debug("Unable to coerce '" + literal + "' into a LONG: " + e.toString());
                    }
                    break;
                case PropertyType.STRING:
                    values.add(literal);
                    log.debug("Using literal " + literal + " as is.");
                    break;
            }
        }
        if (values.size() == 0) {
            // use literal as is then try to guess other types
            values.add(literal);

            // try to guess property type
            if (literal.indexOf('/') > -1) {
                // might be a path
                try {
                    values.add(resolver.getJCRPath(session.getQPath(literal)));
                    log.debug("Coerced " + literal + " into PATH.");
                } catch (Exception e) {
                    // not a path
                }
            }
            if (XMLChar.isValidName(literal)) {
                // might be a name
                try {
                    Name n = session.getQName(literal);
                    values.add(nsMappings.translatePropertyName(n));
                    log.debug("Coerced " + literal + " into NAME.");
                } catch (Exception e) {
                    // not a name
                }
            }
            if (literal.indexOf(':') > -1) {
                // is it a date?
                Calendar c = ISO8601.parse(literal);
                if (c != null) {
                    values.add(DateField.timeToString(c.getTimeInMillis()));
                    log.debug("Coerced " + literal + " into DATE.");
                }
            } else {
                // long or double are possible at this point
                try {
                    values.add(LongField.longToString(Long.parseLong(literal)));
                    log.debug("Coerced " + literal + " into LONG.");
                } catch (NumberFormatException e) {
                    // not a long
                    // try double
                    try {
                        values.add(DoubleField.doubleToString(Double.parseDouble(literal)));
                        log.debug("Coerced " + literal + " into DOUBLE.");
                    } catch (NumberFormatException e1) {
                        // not a double
                    }
                }
            }
        }
        // if still no values use literal as is
        if (values.size() == 0) {
            values.add(literal);
            log.debug("Using literal " + literal + " as is.");
        }
        return (String[]) values.toArray(new String[values.size()]);
    }
}
