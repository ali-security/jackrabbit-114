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
package org.apache.jackrabbit.core.security.authorization.acl;

import org.apache.jackrabbit.api.jsr283.security.AccessControlManager;
import org.apache.jackrabbit.api.jsr283.security.AccessControlPolicy;
import org.apache.jackrabbit.api.jsr283.security.AccessControlPolicyIterator;
import org.apache.jackrabbit.api.jsr283.security.Privilege;
import org.apache.jackrabbit.core.security.authorization.AbstractEvaluationTest;
import org.apache.jackrabbit.core.security.authorization.JackrabbitAccessControlList;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.test.NotExecutableException;

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import java.util.Collections;
import java.util.Map;
import java.security.Principal;

/**
 * <code>EvaluationTest</code>...
 */
public class EvaluationTest extends AbstractEvaluationTest {

    protected void setUp() throws Exception {
        super.setUp();
        try {
            AccessControlPolicy[] rootPolicies = acMgr.getPolicies("/");
            if (rootPolicies.length == 0 || !(rootPolicies[0] instanceof ACLTemplate)) {
                throw new NotExecutableException();
            }
        } catch (RepositoryException e) {
            throw new NotExecutableException();
        }
    }

    protected void clearACInfo() {
        // nop since ac information is stored with nodes that get removed
        // during the general tear-down.
    }

    protected JackrabbitAccessControlList getPolicy(AccessControlManager acM, String path, Principal principal) throws RepositoryException, AccessDeniedException, NotExecutableException {
        AccessControlPolicyIterator it = acM.getApplicablePolicies(path);
        while (it.hasNext()) {
            AccessControlPolicy acp = it.nextAccessControlPolicy();
            if (acp instanceof ACLTemplate) {
                return (ACLTemplate) acp;
            }
        }
        throw new NotExecutableException("ACLTemplate expected.");
    }

    protected Map getRestrictions(String path) {
        return Collections.EMPTY_MAP;
    }

    public void testAccessControlModification2() throws RepositoryException, NotExecutableException {
        /*
         precondition:
         testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);

        // give 'testUser' READ_AC|MODIFY_AC privileges at 'path'
        Privilege[] privileges = privilegesFromNames(new String[] {
                Privilege.JCR_READ_ACCESS_CONTROL,
                Privilege.JCR_MODIFY_ACCESS_CONTROL
        });
        JackrabbitAccessControlList tmpl = givePrivileges(path, privileges, getRestrictions(path));
        /*
         testuser must
         - still have the inherited READ permission.
         - must have permission to view AC items at 'path' (and below)
         - must have permission to modify AC items at 'path'

         testuser must not have
         - permission to view AC items outside of the tree defined by path.
        */

        // make sure the 'rep:policy' node has been created.
        assertTrue(superuser.itemExists(tmpl.getPath() + "/rep:policy"));

        AccessControlManager testAcMgr = getTestACManager();
        // test: MODIFY_AC granted at 'path'
        assertTrue(testAcMgr.hasPrivileges(path, privilegesFromName(Privilege.JCR_MODIFY_ACCESS_CONTROL)));

        // test if testuser can READ access control on the path and on the
        // entire subtree that gets the policy inherited.
        AccessControlPolicy[] policies = testAcMgr.getPolicies(path);
        testAcMgr.getEffectivePolicies(path);
        testAcMgr.getEffectivePolicies(childNPath);

        // test: READ_AC privilege does not apply outside of the tree.
        try {
            testAcMgr.getPolicies(siblingPath);
            fail("READ_AC privilege must not apply outside of the tree it has applied to.");
        } catch (AccessDeniedException e) {
            // success
        }

        // test: MODIFY_AC privilege does not apply outside of the tree.
        try {
            testAcMgr.setPolicy(siblingPath, policies[0]);
            fail("MODIFY_AC privilege must not apply outside of the tree it has applied to.");
        } catch (AccessDeniedException e) {
            // success
        }

        // test if testuser can modify AC-items
        // 1) add an ac-entry
        ACLTemplate acl = (ACLTemplate) policies[0];
        acl.addAccessControlEntry(getTestUser().getPrincipal(), privilegesFromName(Privilege.JCR_WRITE));
        testAcMgr.setPolicy(path, acl);
        getTestSession().save();

        assertTrue(testAcMgr.hasPrivileges(path,
                privilegesFromName(Privilege.JCR_REMOVE_CHILD_NODES)));

        // 2) remove the policy
        testAcMgr.removePolicy(path, policies[0]);
        getTestSession().save();

        // Finally: testuser removed the policy that granted him permission
        // to modify the AC content. Since testuser removed the policy, it's
        // privileges must be gone again...
        try {
            testAcMgr.getEffectivePolicies(childNPath);
            fail("READ_AC privilege has been revoked -> must throw again.");
        } catch (AccessDeniedException e) {
            // success
        }
        // ... and since the ACE is stored with the policy all right except
        // READ must be gone.
        checkReadOnly(path);
    }

    public void testRemovePermission9() throws NotExecutableException, RepositoryException {
        SessionImpl testSession = getTestSession();
        AccessControlManager testAcMgr = getTestACManager();
        /*
          precondition:
          testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);
        checkReadOnly(childNPath);

        Privilege[] rmChildNodes = privilegesFromName(Privilege.JCR_REMOVE_CHILD_NODES);
        Privilege[] rmNode = privilegesFromName(Privilege.JCR_REMOVE_NODE);

        // add 'remove_child_nodes' at 'path and allow 'remove_node' at childNPath
        givePrivileges(path, rmChildNodes, getRestrictions(path));
        givePrivileges(childNPath, rmNode, getRestrictions(childNPath));
        /*
         expected result:
         - rep:policy node can still not be remove for it is access-control
           content that requires jcr:modifyAccessControl privilege instead.
         */
        String policyPath = childNPath + "/rep:policy";
        assertFalse(testSession.hasPermission(policyPath, SessionImpl.REMOVE_ACTION));
        assertTrue(testAcMgr.hasPrivileges(policyPath, new Privilege[] {rmChildNodes[0], rmNode[0]}));
    }
}