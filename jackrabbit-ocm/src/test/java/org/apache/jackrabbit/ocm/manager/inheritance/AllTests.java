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
package org.apache.jackrabbit.ocm.manager.inheritance;

import org.apache.jackrabbit.ocm.RepositoryLifecycleTestSetup;

import junit.framework.Test;
import junit.framework.TestSuite;


/**
 * Package level tests.
 *
 * @author <a href='mailto:the_mindstorm[at]evolva[dot]ro'>Alexandru Popescu</a>
 */
public class AllTests {

    public static Test suite() {
        return new RepositoryLifecycleTestSetup(buildSuite());
    }

    public static Test buildSuite() {
        TestSuite suite= new TestSuite("Test for org.apache.jackrabbit.ocm.manager.inheritance");
        //$JUnit-BEGIN$
        suite.addTestSuite(AnnotationInheritanceConcreteClassTest.class);
        suite.addTestSuite(AnnotationInheritanceHierarchyTest.class);
        suite.addTestSuite(DigesterInheritanceConcreteClassTest.class);
        suite.addTestSuite(DigesterInheritanceHierarchyTest.class);
        //$JUnit-END$

        return suite;
    }
}
