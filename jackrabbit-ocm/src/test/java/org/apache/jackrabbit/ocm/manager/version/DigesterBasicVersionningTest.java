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
package org.apache.jackrabbit.ocm.manager.version;


import java.util.Date;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jackrabbit.ocm.DigesterTestBase;
import org.apache.jackrabbit.ocm.RepositoryLifecycleTestSetup;
import org.apache.jackrabbit.ocm.manager.ObjectContentManager;
import org.apache.jackrabbit.ocm.testmodel.Page;
import org.apache.jackrabbit.ocm.testmodel.Paragraph;
import org.apache.jackrabbit.ocm.version.Version;
import org.apache.jackrabbit.ocm.version.VersionIterator;

/**
 * Test Query on atomic fields
 *
 * @author <a href="mailto:christophe.lombart@sword-technologies.com">Christophe Lombart</a>
 */
public class DigesterBasicVersionningTest extends DigesterTestBase
{
	private final static Log log = LogFactory.getLog(DigesterBasicVersionningTest.class);
	
	
	/**
	 * <p>Defines the test case name for junit.</p>
	 * @param testName The test case name.
	 */
	public DigesterBasicVersionningTest(String testName) throws Exception
	{
		super(testName);
		
	}

	public static Test suite()
	{
		// All methods starting with "test" will be executed in the test suite.
		return new RepositoryLifecycleTestSetup(
                new TestSuite(DigesterBasicVersionningTest.class));
	}	

	public void testSimpleVersion()
	{
	     ObjectContentManager ocm = getObjectContentManager();
         try
         {
        	
        	 Page page = new Page();
        	 page.setPath("/page");
        	 page.setTitle("Page Title");            	
        	 page.addParagraph(new Paragraph("para1"));
        	 page.addParagraph(new Paragraph("para2"));
        	 ocm.insert(page);
        	 ocm.save();
        	
        	 page.addParagraph(new Paragraph("para3"));
        	 page.setTitle("Page Title 2");
        	 ocm.checkout("/page");
        	 ocm.update(page);
        	 ocm.save();
        	 ocm.checkin("/page");
        	
        	 page.addParagraph(new Paragraph("para4"));
        	 page.setTitle("Page Title 3");
        	 ocm.checkout("/page");
        	 ocm.update(page);
        	 ocm.save();
        	 ocm.checkin("/page");            	

        	 VersionIterator versionIterator = ocm.getAllVersions("/page");
        	 assertNotNull("VersionIterator is null", versionIterator);
        	 assertTrue("Invalid number of versions found", versionIterator.getSize() == 3);
        	
        	 while (versionIterator.hasNext())
        	 {
        		 Version version = (Version) versionIterator.next();
        		 log.info("version found : "+ version.getName() + " - " + version.getPath() + " - " +  version.getCreated().getTime());            		 
        		 if (version.getName().equals("jcr:rootVersion"))
        		 {
        			 continue; 
        		 }
        		 
        		 page = (Page) ocm.getObject("/page", version.getName());
        		 assertNotNull("Page is null for version " + version.getName(), page);
        		 
        		 if (version.getName().equals("1.0"))
        		 {
        			assertEquals("Invalid title for version " + version.getName(),page.getTitle(), "Page Title 2");
        		 }
        		 
        		 if (version.getName().equals("1.1"))
        		 {
        			assertEquals("Invalid title for version " + version.getName(),page.getTitle(), "Page Title 3"); 
        		 } 
        		 
        	 }
        	
        	 Version baseVersion = ocm.getBaseVersion("/page");
        	 log.info("Base version : " + baseVersion.getName());

        	 Version rootVersion = ocm.getRootVersion("/page");
        	 log.info("Root version : " + rootVersion.getName());
        	             	
             //Get the latest version
        	 page = (Page) ocm.getObject( "/page");
        	 assertNotNull("Last version is nulll", page);
        	 assertTrue("Invalid number of paragraph found in the last  version", page.getParagraphs().size() == 4);

        	
         }
         catch(Exception e)
         {
        	 e.printStackTrace();
        	 fail(e.getMessage());
        	
         }
	}

	
	public void testVersionLabels()
	{
		     ObjectContentManager ocm = getObjectContentManager();
             try
             {
            	
            	 Page page = new Page();
            	 page.setPath("/page");
            	 page.setTitle("Page Title");            	
            	 page.addParagraph(new Paragraph("para1"));
            	 page.addParagraph(new Paragraph("para2"));
            	 ocm.insert(page);
            	 ocm.save();
            	

            	 page.addParagraph(new Paragraph("para3"));
            	 ocm.checkout("/page");
            	 ocm.update(page);
            	 ocm.save();
            	 ocm.checkin("/page", new String[] {"A", "B"});
            	
            	 page.addParagraph(new Paragraph("para4"));
            	 ocm.checkout("/page");
            	 ocm.update(page);
            	 ocm.save();
            	 ocm.checkin("/page", new String[] {"C", "D"});         	

            	 String[] allLabels = ocm.getAllVersionLabels("/page");
            	 assertTrue("Incorrect number of labels", allLabels.length == 4);

            	 String[] versionLabels = ocm.getVersionLabels("/page", "1.1");
            	 assertTrue("Incorrect number of labels", versionLabels.length == 2);
            	 assertTrue("Incorrect label", versionLabels[0].equals("C") || versionLabels[0].equals("D"));
            	 assertTrue("Incorrect label", versionLabels[1].equals("C") || versionLabels[0].equals("D"));
            	

             }
             catch(Exception e)
             {
            	 e.printStackTrace();
            	 fail();
             }
	}
	
}