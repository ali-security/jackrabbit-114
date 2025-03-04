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
package org.apache.jackrabbit.ocm.manager.interfaces;

import java.util.Collection;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jackrabbit.ocm.RepositoryLifecycleTestSetup;
import org.apache.jackrabbit.ocm.DigesterTestBase;
import org.apache.jackrabbit.ocm.manager.ObjectContentManager;
import org.apache.jackrabbit.ocm.query.Filter;
import org.apache.jackrabbit.ocm.query.Query;
import org.apache.jackrabbit.ocm.query.QueryManager;
import org.apache.jackrabbit.ocm.testmodel.Atomic;
import org.apache.jackrabbit.ocm.testmodel.inheritance.AnotherDescendant;
import org.apache.jackrabbit.ocm.testmodel.inheritance.Descendant;
import org.apache.jackrabbit.ocm.testmodel.inheritance.SubDescendant;
import org.apache.jackrabbit.ocm.testmodel.interfaces.AnotherInterface;
import org.apache.jackrabbit.ocm.testmodel.interfaces.Interface;

/**
 * Test interface (with discreminator field)
 *
 * @author <a href="mailto:christophe.lombart@gmail.com">Christophe Lombart</a>
 */
public class DigesterInterfaceHierarchyTest extends DigesterTestBase {
	private final static Log log = LogFactory.getLog(DigesterInterfaceHierarchyTest.class);

	/**
	 * <p>Defines the test case name for junit.</p>
	 * @param testName The test case name.
	 */
	public DigesterInterfaceHierarchyTest(String testName) throws Exception {
		super(testName);

	}

	public static Test suite() {
		// All methods starting with "test" will be executed in the test suite.
		return new RepositoryLifecycleTestSetup(new TestSuite(
				DigesterInterfaceHierarchyTest.class));
	}


	public void testRetrieveSingleton() {

		try {
			ObjectContentManager ocm = this.getObjectContentManager();

			//---------------------------------------------------------------------------------------------------------
			// Insert
			//---------------------------------------------------------------------------------------------------------			
			AnotherDescendant  anotherDescendant = new AnotherDescendant();
			anotherDescendant.setAnotherDescendantField("anotherDescendantValue");
			anotherDescendant.setAncestorField("ancestorValue");
			anotherDescendant.setPath("/test");
			ocm.insert(anotherDescendant);

			ocm.save();

			//---------------------------------------------------------------------------------------------------------
			// Retrieve
			//---------------------------------------------------------------------------------------------------------						
			Interface result =  (Interface) ocm.getObject("/test");
			assertNotNull("Object is null", result);
			anotherDescendant = (AnotherDescendant) result;
			
			assertEquals("Descendant path is invalid", anotherDescendant.getPath(), "/test");
			assertEquals("Descendant ancestorField is invalid", anotherDescendant.getAncestorField(), "ancestorValue");
			assertEquals("Descendant descendantField is invalid", anotherDescendant	.getAnotherDescendantField(), "anotherDescendantValue");

		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}

	}
	
	
	public void testRetrieveCollection() {
		
		ObjectContentManager ocm = this.getObjectContentManager();

		//---------------------------------------------------------------------------------------------------------	
		// Insert  descendant objects
		//---------------------------------------------------------------------------------------------------------			
		Descendant descendant = new Descendant();
		descendant.setDescendantField("descendantValue");
		descendant.setAncestorField("ancestorValue");
		descendant.setPath("/descendant1");
		ocm.insert(descendant);

		descendant = new Descendant();
		descendant.setDescendantField("descendantValue2");
		descendant.setAncestorField("ancestorValue2");
		descendant.setPath("/descendant2");
		ocm.insert(descendant);

		SubDescendant subDescendant = new SubDescendant();
		subDescendant.setDescendantField("descendantValue2");
		subDescendant.setAncestorField("ancestorValue2");
		subDescendant.setPath("/subdescendant");
		subDescendant.setSubDescendantField("subdescendantvalue");
		ocm.insert(subDescendant);		

		 subDescendant = new SubDescendant();
		subDescendant.setDescendantField("descendantValue3");
		subDescendant.setAncestorField("ancestorValue2");
		subDescendant.setPath("/subdescendant2");
		subDescendant.setSubDescendantField("subdescendantvalue1");
		ocm.insert(subDescendant);		
		
		
		AnotherDescendant anotherDescendant = new AnotherDescendant();
		anotherDescendant.setAnotherDescendantField("anotherDescendantValue");
		anotherDescendant.setAncestorField("ancestorValue3");
		anotherDescendant.setPath("/anotherdescendant1");
		ocm.insert(anotherDescendant);

		anotherDescendant = new AnotherDescendant();
		anotherDescendant.setAnotherDescendantField("anotherDescendantValue");
		anotherDescendant.setAncestorField("ancestorValue4");
		anotherDescendant.setPath("/anotherdescendant2");
		ocm.insert(anotherDescendant);

		anotherDescendant = new AnotherDescendant();
		anotherDescendant.setAnotherDescendantField("anotherDescendantValue2");
		anotherDescendant.setAncestorField("ancestorValue5");
		anotherDescendant.setPath("/anotherdescendant3");
		ocm.insert(anotherDescendant);

		
		Atomic a = new Atomic();
		a.setPath("/atomic");
		a.setBooleanPrimitive(true);
		ocm.insert(a);

		ocm.save();

		//---------------------------------------------------------------------------------------------------------	
		// Retrieve Descendant class (implements  Interface.class)
		//---------------------------------------------------------------------------------------------------------			
		QueryManager queryManager = ocm.getQueryManager();
		Filter filter = queryManager.createFilter(Interface.class);
		Query query = queryManager.createQuery(filter);

		Collection result = ocm.getObjects(query);
		assertEquals("Invalid number of  interface  found", result.size(),3);
		assertTrue("Invalid item in the collection", this.contains(result, "/anotherdescendant1", AnotherDescendant.class));
		assertTrue("Invalid item in the collection", this.contains(result, "/anotherdescendant2", AnotherDescendant.class));
		assertTrue("Invalid item in the collection", this.contains(result, "/anotherdescendant3", AnotherDescendant.class));
		

		//---------------------------------------------------------------------------------------------------------	
		// Retrieve Descendant class and its children (implements  AnotherInterface.class)
		//---------------------------------------------------------------------------------------------------------			
	    queryManager = ocm.getQueryManager();
		filter = queryManager.createFilter(AnotherInterface.class);
		query = queryManager.createQuery(filter);

		result = ocm.getObjects(query);
		assertEquals("Invalid number of  interface  found", result.size(),4);
		assertTrue("Invalid item in the collection", this.contains(result, "/descendant1", Descendant.class));
		assertTrue("Invalid item in the collection", this.contains(result, "/descendant2", Descendant.class));
		assertTrue("Invalid item in the collection", this.contains(result, "/subdescendant",SubDescendant.class));		
		assertTrue("Invalid item in the collection", this.contains(result, "/subdescendant2",SubDescendant.class));

	}
	
}