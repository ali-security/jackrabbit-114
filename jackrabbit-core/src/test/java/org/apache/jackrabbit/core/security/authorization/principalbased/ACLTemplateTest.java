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
package org.apache.jackrabbit.core.security.authorization.principalbased;

import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.security.authorization.AbstractACLTemplateTest;
import org.apache.jackrabbit.core.security.authorization.JackrabbitAccessControlList;

import javax.jcr.RepositoryException;

/**
 * <code>ACLTemplateTest</code>...
 */
public class ACLTemplateTest extends AbstractACLTemplateTest {

    private String testPath = "/rep:accessControl/users/test";

    protected String getTestPath() {
        return testPath;
    }

    protected JackrabbitAccessControlList createEmptyTemplate(String testPath)
            throws RepositoryException {
        return new ACLTemplate(testPrincipal, testPath, (SessionImpl) superuser);
    }
}