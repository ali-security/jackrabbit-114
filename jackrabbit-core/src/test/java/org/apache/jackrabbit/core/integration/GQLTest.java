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
package org.apache.jackrabbit.core.integration;

import org.apache.jackrabbit.core.query.AbstractQueryTest;
import org.apache.jackrabbit.commons.query.GQL;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.query.RowIterator;
import java.util.Calendar;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;

/**
 * <code>GQLTest</code> performs tests on {@link GQL}.
 */
public class GQLTest extends AbstractQueryTest {

    private static final String SAMPLE_CONTENT = "the quick brown fox jumps over the lazy dog.";

    public void testPath() throws RepositoryException {
        Node n1 = testRootNode.addNode("node1");
        Node n2 = testRootNode.addNode("node2");
        Node n3 = testRootNode.addNode("node3");
        superuser.save();
        RowIterator rows = GQL.execute(createStatement(""), superuser);
        checkResult(rows, new Node[]{n1, n2, n3});
    }

    public void testOrder() throws RepositoryException {
        Node n1 = testRootNode.addNode("node1");
        n1.setProperty("p", 1);
        Node n2 = testRootNode.addNode("node2");
        n2.setProperty("p", 2);
        Node n3 = testRootNode.addNode("node3");
        n3.setProperty("p", 3);
        superuser.save();
        // default: ascending
        String stmt = createStatement("order:p");
        RowIterator rows = GQL.execute(stmt, superuser);
        checkResultSequence(rows, new Node[]{n1, n2, n3});
        // explicit ascending
        stmt = createStatement("order:+p");
        rows = GQL.execute(stmt, superuser);
        checkResultSequence(rows, new Node[]{n1, n2, n3});
        // explicit descending
        stmt = createStatement("order:-p");
        rows = GQL.execute(stmt, superuser);
        checkResultSequence(rows, new Node[]{n3, n2, n1});
    }

    public void testLimit() throws RepositoryException {
        Node n1 = testRootNode.addNode("node1");
        n1.setProperty("p", 1);
        Node n2 = testRootNode.addNode("node2");
        n2.setProperty("p", 2);
        Node n3 = testRootNode.addNode("node3");
        n3.setProperty("p", 3);
        superuser.save();
        // only 2 results
        String stmt = createStatement("order:p limit:2");
        RowIterator rows = GQL.execute(stmt, superuser);
        checkResultSequence(rows, new Node[]{n1, n2});
        // range with open start
        stmt = createStatement("order:p limit:..2");
        rows = GQL.execute(stmt, superuser);
        checkResultSequence(rows, new Node[]{n1, n2});
        // range with open end
        stmt = createStatement("order:p limit:1..");
        rows = GQL.execute(stmt, superuser);
        checkResultSequence(rows, new Node[]{n2, n3});
        // range
        stmt = createStatement("order:p limit:1..2");
        rows = GQL.execute(stmt, superuser);
        checkResultSequence(rows, new Node[]{n2});
        // range with end larger than max results
        stmt = createStatement("order:p limit:1..7");
        rows = GQL.execute(stmt, superuser);
        checkResultSequence(rows, new Node[]{n2, n3});
        // range start larger than end
        // end is ignored in that case
        stmt = createStatement("order:p limit:2..1");
        rows = GQL.execute(stmt, superuser);
        checkResultSequence(rows, new Node[]{n3});
        // range with start larger than max results
        stmt = createStatement("order:p limit:6..10");
        rows = GQL.execute(stmt, superuser);
        checkResultSequence(rows, new Node[]{});
    }

    public void testPhrase() throws RepositoryException {
        Node file1 = addFile(testRootNode, "file1.txt", SAMPLE_CONTENT);
        superuser.save();
        String stmt = createStatement("\"quick brown\"");
        RowIterator rows = GQL.execute(stmt, superuser, "jcr:content");
        checkResult(rows, new Node[]{file1});
    }

    public void testExcludeTerm() throws RepositoryException {
        Node n1 = testRootNode.addNode("node1");
        n1.setProperty("text", "foo");
        Node n2 = testRootNode.addNode("node2");
        n2.setProperty("text", "bar");
        Node n3 = testRootNode.addNode("node3");
        n3.setProperty("text", "foo bar");
        superuser.save();
        String stmt = createStatement("foo -bar");
        RowIterator rows = GQL.execute(stmt, superuser);
        checkResult(rows, new Node[]{n1});
    }

    public void testOptionalTerm() throws RepositoryException {
        Node n1 = testRootNode.addNode("node1");
        n1.setProperty("text", "apache jackrabbit");
        Node n2 = testRootNode.addNode("node2");
        n2.setProperty("text", "apache jcr");
        Node n3 = testRootNode.addNode("node3");
        n3.setProperty("text", "jackrabbit jcr");
        superuser.save();
        String stmt = createStatement("apache jackrabbit OR jcr");
        RowIterator rows = GQL.execute(stmt, superuser);
        checkResult(rows, new Node[]{n1, n2});
    }

    public void testType() throws RepositoryException {
        Node file = addFile(testRootNode, "file1.txt", SAMPLE_CONTENT);
        Node node = testRootNode.addNode("node1");
        node.setProperty("text", SAMPLE_CONTENT);
        superuser.save();
        // only nt:resource
        String stmt = createStatement("quick type:\"nt:resource\"");
        RowIterator rows = GQL.execute(stmt, superuser);
        checkResult(rows, new Node[]{file.getNode("jcr:content")});
        // only nt:unstructured
        stmt = createStatement("quick type:\"nt:unstructured\"");
        rows = GQL.execute(stmt, superuser);
        checkResult(rows, new Node[]{node});
    }

    public void testMixinType() throws RepositoryException {
        Node node = testRootNode.addNode("node1");
        node.setProperty("text", SAMPLE_CONTENT);
        node.addMixin(mixReferenceable);
        superuser.save();
        String stmt = createStatement("quick type:referenceable");
        RowIterator rows = GQL.execute(stmt, superuser);
        checkResult(rows, new Node[]{node});
    }

    public void testTypeInheritance() throws RepositoryException {
        Node file = addFile(testRootNode, "file1.txt", SAMPLE_CONTENT);
        superuser.save();
        // nt:hierarchyNode and sub types
        String stmt = createStatement("quick type:hierarchyNode");
        RowIterator rows = GQL.execute(stmt, superuser, "jcr:content");
        checkResult(rows, new Node[]{file});
    }

    public void testAutoPrefixType() throws RepositoryException {
        Node file = addFile(testRootNode, "file1.txt", SAMPLE_CONTENT);
        Node node = testRootNode.addNode("node1");
        node.setProperty("text", SAMPLE_CONTENT);
        superuser.save();
        // only nt:resource
        String stmt = createStatement("quick type:resource");
        RowIterator rows = GQL.execute(stmt, superuser);
        checkResult(rows, new Node[]{file.getNode("jcr:content")});
        // only nt:unstructured
        stmt = createStatement("quick type:unstructured");
        rows = GQL.execute(stmt, superuser);
        checkResult(rows, new Node[]{node});
    }

    public void testQuotedProperty() throws RepositoryException {
        Node file1 = addFile(testRootNode, "file1.txt", SAMPLE_CONTENT);
        superuser.save();
        String stmt = createStatement("\"jcr:mimeType\":text/plain");
        RowIterator rows = GQL.execute(stmt, superuser, "jcr:content");
        checkResult(rows, new Node[]{file1});
    }

    public void testAutoPrefix() throws RepositoryException {
        Node file1 = addFile(testRootNode, "file1.txt", SAMPLE_CONTENT);
        superuser.save();
        String stmt = createStatement("mimeType:text/plain");
        RowIterator rows = GQL.execute(stmt, superuser, "jcr:content");
        checkResult(rows, new Node[]{file1});
    }

    public void testCommonPathPrefix() throws RepositoryException {
        Node file1 = addFile(testRootNode, "file1.txt", SAMPLE_CONTENT);
        Node file2 = addFile(testRootNode, "file2.txt", SAMPLE_CONTENT);
        Node file3 = addFile(testRootNode, "file3.txt", SAMPLE_CONTENT);
        superuser.save();
        String stmt = createStatement("quick");
        RowIterator rows = GQL.execute(stmt, superuser, "jcr:content");
        checkResult(rows, new Node[]{file1, file2, file3});
    }

    public void testExcerpt() throws RepositoryException {
        addFile(testRootNode, "file1.txt", SAMPLE_CONTENT);
        superuser.save();
        String stmt = createStatement("quick");
        RowIterator rows = GQL.execute(stmt, superuser, "jcr:content");
        assertTrue("Expected result", rows.hasNext());
        String excerpt = rows.nextRow().getValue("rep:excerpt()").getString();
        assertTrue("No excerpt returned", excerpt.startsWith("<div><span>"));
        stmt = createStatement("type:resource quick");
        rows = GQL.execute(stmt, superuser);
        assertTrue("Expected result", rows.hasNext());
        excerpt = rows.nextRow().getValue("rep:excerpt()").getString();
        assertTrue("No excerpt returned", excerpt.startsWith("<div><span>"));
    }

    public void testPrefixedValue() throws RepositoryException {
        Node n1 = testRootNode.addNode("node1");
        n1.setProperty("jcr:title", "a");
        Node n2 = testRootNode.addNode("node2");
        n2.setProperty("jcr:title", "b");
        Node n3 = testRootNode.addNode("node3");
        n3.setProperty("jcr:title", "c");
        superuser.save();
        String stmt = createStatement("order:jcr:title");
        RowIterator rows = GQL.execute(stmt, superuser);
        checkResultSequence(rows, new Node[]{n1, n2, n3});

    }

    public void XXXtestQueryDestruction() throws RepositoryException {
        char[] stmt = createStatement("title:jackrabbit \"apache software\" type:file order:+title limit:10..20").toCharArray();
        for (char c = 0; c < 255; c++) {
            for (int i = 0; i < stmt.length; i++) {
                char orig = stmt[i];
                stmt[i] = c;
                try {
                    GQL.execute(new String(stmt), superuser);
                } finally {
                    stmt[i] = orig;
                }
            }
        }
    }

    protected static Node addFile(Node folder, String name, String contents)
            throws RepositoryException {
        Node file = folder.addNode(name, "nt:file");
        Node resource = file.addNode("jcr:content", "nt:resource");
        resource.setProperty("jcr:lastModified", Calendar.getInstance());
        resource.setProperty("jcr:mimeType", "text/plain");
        resource.setProperty("jcr:encoding", "UTF-8");
        try {
            resource.setProperty("jcr:data", new ByteArrayInputStream(
                    contents.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            // will never happen
        }
        return file;
    }

    protected String createStatement(String stmt) {
        return "path:" + testRoot + " " + stmt;
    }
}
