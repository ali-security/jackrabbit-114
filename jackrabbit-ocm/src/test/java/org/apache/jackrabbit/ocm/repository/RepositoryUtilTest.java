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
package org.apache.jackrabbit.ocm.repository;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeTypeManager;
import javax.transaction.UserTransaction;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jackrabbit.ocm.transaction.jackrabbit.UserTransactionImpl;

/** Testcase for RepositoryUtil.
 *
 * @author <a href="mailto:christophe.lombart@sword-technologies.com">Christophe Lombart</a>
 * @author <a href='mailto:the_mindstorm[at]evolva[dot]ro'>Alexandru Popescu</a>
 */
public class RepositoryUtilTest extends TestCase
{
    private final static Log log = LogFactory.getLog(RepositoryUtilTest.class);
    private static boolean isInit = false;
    /**
     * <p>Defines the test case name for junit.</p>
     * @param testName The test case name.
     */
    public RepositoryUtilTest(String testName)
    {
        super(testName);
    }

    /**
     * @see junit.framework.TestCase#setUp()
     */
    protected void setUp() throws Exception
    {
        super.setUp();
    }

    /**
     * @see junit.framework.TestCase#tearDown()
     */
    public void tearDown() throws Exception
    {
        super.tearDown();
    }

    

    /**
     * Test for getRepository() and login
     *
     */
    public void testRegistryAndLogin()
    {
        try
        {
            Repository repository = RepositoryUtil.getRepository("repositoryTest");
            assertNotNull("The repository is null", repository);
            Session session = RepositoryUtil.login(repository, "superuser", "superuser");
            Node root = session.getRootNode();
            assertNotNull("Root node is null", root);

            Session session2 = RepositoryUtil.login(repository, "superuser", "superuser");
            root = session2.getRootNode();
            assertNotNull("Root node is null", root);

            session.logout();
            session2.logout();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Unable to find the repository : " + e);
        }

    }

    /**
     * Simple unit test to check if custome node types are well defined
     *
     */
    public void testCustomNodeType()
    {
        try
        {
            Repository repository = RepositoryUtil.getRepository("repositoryTest");
            Session session = RepositoryUtil.login(repository, "superuser", "superuser");
            NodeTypeManager nodeTypeManager = session.getWorkspace().getNodeTypeManager();

            // TODO custom node types not implemented yet

            //NodeType nodeType = nodeTypeManager.getNodeType("ocm:folder");
            //assertNotNull("Root node is null", nodeType);

            session.logout();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Unable to find the repository : " + e);
        }
    }



    public void testEncodePath()
    {
         String encodedPath = RepositoryUtil.encodePath("/files/test/1.0");
         assertTrue("Incorrect encoded path", encodedPath.equals("/files/test/_x0031_.0"));

         encodedPath = RepositoryUtil.encodePath("/files/test/12aa/b/34/rrr/1.0");
         assertTrue("Incorrect encoded path", encodedPath.equals("/files/test/_x0031_2aa/b/_x0033_4/rrr/_x0031_.0"));

    }

    public void testUserTransaction()
    {
    	try
		{
			Repository repository = RepositoryUtil.getRepository("repositoryTest");
			assertNotNull("The repository is null", repository);
			Session session = RepositoryUtil.login(repository, "superuser",
					"superuser");

			UserTransaction utx = new UserTransactionImpl(session);

			// start transaction
			utx.begin();

			// add node and save
			Node root = session.getRootNode();
			Node n = root.addNode("test");
			root.save();
			utx.commit();
			
			assertTrue("test node doesn't exist", session.itemExists("/test"));
			
			utx = new UserTransactionImpl(session);
			utx.begin();
			Node test = (Node) session.getItem("/test");
			test.remove();
			session.save();
			utx.rollback();
			
			assertTrue("test node doesn't exist", session.itemExists("/test"));			

			utx = new UserTransactionImpl(session);
			utx.begin();
			test = (Node) session.getItem("/test");
			test.remove();
			session.save();
			utx.commit();
			
			assertFalse("test node exists", session.itemExists("/test"));			
			
		}
		catch (Exception e)
		{
            e.printStackTrace();
            fail("Unable to run user transaction : " + e);
		}
    }

}