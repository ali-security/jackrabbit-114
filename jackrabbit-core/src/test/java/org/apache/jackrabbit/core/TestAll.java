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
package org.apache.jackrabbit.core;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Test suite that includes all testcases for the Core module.
 */
public class TestAll extends TestCase {

    /**
     * @return a <code>Test</code> suite that executes all tests inside this
     *         package, except the multi-threading related ones.
     */
    public static Test suite() {
        TestSuite suite = new TestSuite("Core tests");

        suite.addTestSuite(CachingHierarchyManagerTest.class);
        suite.addTestSuite(ShareableNodeTest.class);
        suite.addTestSuite(TransientRepositoryTest.class);
        suite.addTestSuite(XATest.class);
        suite.addTestSuite(RestoreAndCheckoutTest.class);
        suite.addTestSuite(NodeImplTest.class);

        return suite;
    }
}
