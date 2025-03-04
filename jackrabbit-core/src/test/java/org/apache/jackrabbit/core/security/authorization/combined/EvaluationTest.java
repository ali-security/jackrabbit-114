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
package org.apache.jackrabbit.core.security.authorization.combined;

import org.apache.jackrabbit.api.jsr283.security.AccessControlManager;
import org.apache.jackrabbit.api.jsr283.security.AccessControlPolicy;
import org.apache.jackrabbit.api.jsr283.security.Privilege;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.core.security.authorization.JackrabbitAccessControlList;
import org.apache.jackrabbit.test.NotExecutableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * <code>EvaluationTest</code>...
 */
public class EvaluationTest extends org.apache.jackrabbit.core.security.authorization.acl.EvaluationTest {

    private static Logger log = LoggerFactory.getLogger(EvaluationTest.class);

    private List toClear = new ArrayList();

    protected void setUp() throws Exception {
        super.setUp();

        // simple test to check if proper provider is present:
        getPrincipalBasedPolicy(acMgr, path, getTestUser().getPrincipal());
    }

    protected void clearACInfo() {
        for (Iterator it = toClear.iterator(); it.hasNext();) {
            String path = it.next().toString();
            try {
                AccessControlPolicy[] policies = acMgr.getPolicies(path);
                for (int i = 0; i < policies.length; i++) {
                    acMgr.removePolicy(path, policies[i]);
                    superuser.save();
                }
            } catch (RepositoryException e) {
                // log error and ignore
                log.error(e.getMessage());
            }
        }
    }

    private JackrabbitAccessControlList getPrincipalBasedPolicy(AccessControlManager acM, String path, Principal principal) throws RepositoryException, AccessDeniedException, NotExecutableException {
        if (acM instanceof JackrabbitAccessControlManager) {
            AccessControlPolicy[] tmpls = ((JackrabbitAccessControlManager) acM).getApplicablePolicies(principal);
            for (int i = 0; i < tmpls.length; i++) {
                if (tmpls[i] instanceof JackrabbitAccessControlList) {
                    JackrabbitAccessControlList acl = (JackrabbitAccessControlList) tmpls[i];
                    toClear.add(acl.getPath());
                    return acl;
                }
            }
        }
        throw new NotExecutableException();
    }

    private JackrabbitAccessControlList givePrivileges(String nPath,
                                                       Principal principal,
                                                       Privilege[] privileges,
                                                       Map restrictions,
                                                       boolean nodeBased) throws NotExecutableException, RepositoryException {
        if (nodeBased) {
            return givePrivileges(nPath, principal, privileges, getRestrictions(nPath));
        } else {
            JackrabbitAccessControlList tmpl = getPrincipalBasedPolicy(acMgr, nPath, principal);
            tmpl.addEntry(principal, privileges, true, restrictions);
            acMgr.setPolicy(tmpl.getPath(), tmpl);
            superuser.save();
            // remember for teardown
            toClear.add(tmpl.getPath());
            return tmpl;
        }
    }

    private JackrabbitAccessControlList withdrawPrivileges(String nPath,
                                                       Principal principal,
                                                       Privilege[] privileges,
                                                       Map restrictions,
                                                       boolean nodeBased) throws NotExecutableException, RepositoryException {
        if (nodeBased) {
            return withdrawPrivileges(nPath, principal, privileges, getRestrictions(nPath));
        } else {
            JackrabbitAccessControlList tmpl = getPrincipalBasedPolicy(acMgr, nPath, principal);
            tmpl.addEntry(principal, privileges, false, restrictions);
            acMgr.setPolicy(tmpl.getPath(), tmpl);
            superuser.save();
            // remember for teardown
            toClear.add(tmpl.getPath());
            return tmpl;
        }
    }

    private Map getPrincipalBasedRestrictions(String path) throws RepositoryException, NotExecutableException {
        if (superuser instanceof SessionImpl) {
            Map restr = new HashMap();
            restr.put("rep:nodePath", path);
            return restr;
        } else {
            throw new NotExecutableException();
        }
    }

    public void testCombinedPolicies() throws RepositoryException, NotExecutableException {
        Group testGroup = getTestGroup();
        SessionImpl testSession = getTestSession();
        AccessControlManager testAcMgr = getTestACManager();

        /*
          precondition:
          testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);

        Privilege[] readPrivs = privilegesFromName(Privilege.JCR_READ);
        // nodebased: remove READ privilege for 'testUser' at 'path'
        withdrawPrivileges(path, readPrivs, getRestrictions(path));
        // principalbased: add READ privilege for 'testGroup'
        givePrivileges(path, testGroup.getPrincipal(), readPrivs, getPrincipalBasedRestrictions(path), false);
        /*
         expected result:
         - nodebased wins over principalbased -> READ is denied
         */
        assertFalse(testSession.itemExists(path));
        assertFalse(testSession.hasPermission(path, SessionImpl.READ_ACTION));
        assertFalse(testAcMgr.hasPrivileges(path, readPrivs));

        // remove the nodebased policy
        JackrabbitAccessControlList policy = getPolicy(acMgr, path, getTestUser().getPrincipal());
        acMgr.removePolicy(policy.getPath(), policy);
        superuser.save();

        /*
         expected result:
         - READ privilege is present again.
         */
        assertTrue(testSession.itemExists(path));
        assertTrue(testSession.hasPermission(path, SessionImpl.READ_ACTION));
        assertTrue(testAcMgr.hasPrivileges(path, readPrivs));

        // nodebased: add WRITE privilege for 'testUser' at 'path'
        Privilege[] wrtPrivileges = privilegesFromName(Privilege.JCR_WRITE);
        givePrivileges(path, wrtPrivileges, getRestrictions(path));
        // userbased: deny MODIFY_PROPERTIES privileges for 'testUser'
        Privilege[] modPropPrivs = privilegesFromName(Privilege.JCR_MODIFY_PROPERTIES);
        withdrawPrivileges(path, getTestUser().getPrincipal(), modPropPrivs, getPrincipalBasedRestrictions(path), false);
        /*
         expected result:
         - MODIFY_PROPERTIES privilege still present
         */
        assertTrue(testSession.hasPermission(path+"/anyproperty", SessionImpl.SET_PROPERTY_ACTION));
        assertTrue(testAcMgr.hasPrivileges(path, wrtPrivileges));

        // nodebased: deny MODIFY_PROPERTIES privileges for 'testUser'
        //            on a child node.
        withdrawPrivileges(childNPath, getTestUser().getPrincipal(), modPropPrivs, getRestrictions(childNPath));
        /*
         expected result:
         - MODIFY_PROPERTIES privilege still present at 'path'
         - no-MODIFY_PROPERTIES privilege at 'childNPath'
         */
        assertTrue(testSession.hasPermission(path+"/anyproperty", SessionImpl.SET_PROPERTY_ACTION));
        assertTrue(testAcMgr.hasPrivileges(path, modPropPrivs));

        assertFalse(testSession.hasPermission(childNPath+"/anyproperty", SessionImpl.SET_PROPERTY_ACTION));
        assertFalse(testAcMgr.hasPrivileges(childNPath, modPropPrivs));
    }
}