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
package org.apache.jackrabbit.ocm.manager.collectionconverter;

import java.util.ArrayList;
import java.util.Collection;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jackrabbit.ocm.AnnotationTestBase;
import org.apache.jackrabbit.ocm.RepositoryLifecycleTestSetup;
import org.apache.jackrabbit.ocm.DigesterTestBase;
import org.apache.jackrabbit.ocm.manager.ObjectContentManager;
import org.apache.jackrabbit.ocm.testmodel.A;
import org.apache.jackrabbit.ocm.testmodel.C;

/**
 * Test DefaultCollectionConverterImpl
 *
 * @author <a href="mailto:christophe.lombart@sword-technologies.com">Christophe Lombart</a>
 */
public class AnnotationDefaultCollectionConverterImplTest extends AnnotationTestBase
{
    private final static Log log = LogFactory.getLog(AnnotationDefaultCollectionConverterImplTest.class);

    /**
     * <p>Defines the test case name for junit.</p>
     * @param testName The test case name.
     */
    public AnnotationDefaultCollectionConverterImplTest(String testName)  throws Exception
    {
        super(testName);
    }

    public static Test suite()
    {
        // All methods starting with "test" will be executed in the test suite.
        return new RepositoryLifecycleTestSetup(new TestSuite(AnnotationDefaultCollectionConverterImplTest.class));
    }


    public void testNull()
    {
        try
        {
        	ObjectContentManager ocm = getObjectContentManager();

            // --------------------------------------------------------------------------------
            // Create and store an object with a null collection field
            // --------------------------------------------------------------------------------
            A a = new A();
            a.setPath("/test");

            ocm.insert(a);
            ocm.save();

            // --------------------------------------------------------------------------------
            // Get the object
            // --------------------------------------------------------------------------------
            a = (A) ocm.getObject( "/test");
            assertNull("a.collection is not null", a.getCollection());

            // --------------------------------------------------------------------------------
            // Update the object
            // --------------------------------------------------------------------------------
            C c1 = new C();
            c1.setId("first");
            c1.setName("First Element");
            C c2 = new C();
            c2.setId("second");
            c2.setName("Second Element");

            C c3 = new C();
            c3.setId("third");
            c3.setName("Third Element");


            Collection collection = new ArrayList();
            collection.add(c1);
            collection.add(c2);
            collection.add(c3);

            a.setCollection(collection);

            ocm.update(a);
            ocm.save();

            // --------------------------------------------------------------------------------
            // Get the object
            // --------------------------------------------------------------------------------
            a = (A) ocm.getObject("/test");
            assertNotNull("a is null", a);
            assertNotNull("a.collection is null", a.getCollection());
            assertTrue("Incorrect collection size", a.getCollection().size() == 3);
            assertTrue("Incorrect a.collection", ((C) a.getCollection().iterator().next()).getId().equals("first"));

            // --------------------------------------------------------------------------------
            // Update the object
            // --------------------------------------------------------------------------------
            a.setCollection(null);
            ocm.update(a);
            ocm.save();

            // --------------------------------------------------------------------------------
            // Get the object
            // --------------------------------------------------------------------------------
            a = (A) ocm.getObject( "/test");
            assertNull("a.collection is not null", a.getCollection());

            // --------------------------------------------------------------------------------
            // Export to check the content
            // --------------------------------------------------------------------------------
            this.exportDocument("target/DefaultCollectionConverterExport.xml", "/test", true, false);

        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception occurs during the unit test : " + e);
        }

    }

    public void testDropElement()
    {
        try
        {
        	ObjectContentManager ocm = getObjectContentManager();

            // --------------------------------------------------------------------------------
            // Create and store an object graph in the repository
            // --------------------------------------------------------------------------------
            A a = new A();
            a.setPath("/test");
            C c1 = new C();
            c1.setId("first");
            c1.setName("First Element");
            C c2 = new C();
            c2.setId("second");
            c2.setName("Second Element");

            C c3 = new C();
            c3.setId("third");
            c3.setName("Third Element");


            Collection collection = new ArrayList();
            collection.add(c1);
            collection.add(c2);
            collection.add(c3);

            a.setCollection(collection);

            ocm.insert(a);
            ocm.save();

            // --------------------------------------------------------------------------------
            // Get the object
            // --------------------------------------------------------------------------------
            a = (A) ocm.getObject( "/test");
            assertNotNull("a.collection is null", a.getCollection());
            assertEquals("Incorrect a.collection size", 3, a.getCollection().size());
            assertTrue("Incorrect a.collection", ((C) a.getCollection().iterator().next()).getId().equals("first"));

            // --------------------------------------------------------------------------------
            // Update the object
            // --------------------------------------------------------------------------------
            c1 = new C();
            c1.setId("new first");
            c1.setName("First Element");

            c2 = new C();
            c2.setId("new second");
            c2.setName("Second Element");

            collection = new ArrayList();
            collection.add(c1);
            collection.add(c2);
            a.setCollection(collection);

            ocm.update(a);
            ocm.save();

            // --------------------------------------------------------------------------------
            // Get the object
            // --------------------------------------------------------------------------------
            a = (A) ocm.getObject( "/test");
            assertNotNull("a is null", a);
            assertNotNull("a.collection is null", a.getCollection());
            assertTrue("Incorrect collection size", a.getCollection().size() == 2);
            assertTrue("Incorrect a.collection", ((C) a.getCollection().iterator().next()).getId().equals("new first"));

            // --------------------------------------------------------------------------------
            // Export to check the content
            // --------------------------------------------------------------------------------
            this.exportDocument("target/DefaultCollectionConverterExport.xml", "/test", true, false);

        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception occurs during the unit test : " + e);
        }

    }

    public void testAddElement()
    {
        try
        {

        	ObjectContentManager ocm = getObjectContentManager();

            // --------------------------------------------------------------------------------
            // Create and store an object graph in the repository
            // --------------------------------------------------------------------------------
            A a = new A();
            a.setPath("/test");
            C c1 = new C();
            c1.setId("first");
            c1.setName("First Element");
            C c2 = new C();
            c2.setId("second");
            c2.setName("Second Element");

            C c3 = new C();
            c3.setId("third");
            c3.setName("Third Element");


            Collection collection = new ArrayList();
            collection.add(c1);
            collection.add(c2);
            collection.add(c3);

            a.setCollection(collection);

            ocm.insert(a);
            ocm.save();

            // --------------------------------------------------------------------------------
            // Get the object
            // --------------------------------------------------------------------------------
            a = (A) ocm.getObject( "/test");
            assertNotNull("a.collection is null", a.getCollection());
            assertEquals("Incorrect a.collection size", 3, a.getCollection().size());
            assertEquals("Incorrect a.collection", "first", ((C) a.getCollection().iterator().next()).getId());

            // --------------------------------------------------------------------------------
            // Update the object
            // --------------------------------------------------------------------------------
            C c4 = new C();
            c4.setId("Fourth");
            c4.setName("Fourth Element");

            collection = new ArrayList();
            collection.add(c1);
            collection.add(c2);
            collection.add(c3);
            collection.add(c4);
            a.setCollection(collection);

            ocm.update(a);
            ocm.save();

            // --------------------------------------------------------------------------------
            // Get the object
            // --------------------------------------------------------------------------------
            a = (A) ocm.getObject( "/test");
            assertNotNull("a is null", a);
            assertNotNull("a.collection is null", a.getCollection());
            assertEquals("Incorrect collection size", 4, a.getCollection().size());
            assertEquals("Incorrect a.collection", "first", ((C) a.getCollection().iterator().next()).getId());

        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception occurs during the unit test : " + e);
        }

    }

}