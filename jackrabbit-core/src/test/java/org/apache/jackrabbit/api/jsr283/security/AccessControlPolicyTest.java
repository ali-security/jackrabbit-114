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
package org.apache.jackrabbit.api.jsr283.security;

import org.apache.jackrabbit.test.NotExecutableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.AccessDeniedException;
import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

/**
 * <code>AccessControlPolicyTest</code>...
 */
public class AccessControlPolicyTest extends AbstractAccessControlTest {

    private static Logger log = LoggerFactory.getLogger(AccessControlPolicyTest.class);

    private String path;
    private Map addedPolicies = new HashMap();

    protected void setUp() throws Exception {
        super.setUp();

        // policy-option is cover the by the 'OPTION_ACCESS_CONTROL_SUPPORTED' -> see super-class

        Node n = testRootNode.addNode(nodeName1, testNodeType);
        superuser.save();
        path = n.getPath();
    }

    protected void tearDown() throws Exception {
        try {
            for (Iterator it = addedPolicies.keySet().iterator(); it.hasNext();) {
                String path = it.next().toString();
                AccessControlPolicy policy = (AccessControlPolicy) addedPolicies.get(path);
                acMgr.removePolicy(path, policy);
            }
            superuser.save();
        } catch (Exception e) {
            log.error("Unexpected error while removing test policies.", e);
        }
        addedPolicies.clear();
        super.tearDown();
    }

    public void testGetEffectivePolicies() throws RepositoryException, AccessDeniedException, NotExecutableException {
        checkCanReadAc(path);
        // call must succeed without exception
        AccessControlPolicy[] policies = acMgr.getEffectivePolicies(path);
        if (policies == null && policies.length == 0) {
            fail("To every existing node at least a single effective policy applies.");
        }
    }

    public void testGetEffectivePoliciesForNonExistingNode() throws RepositoryException, AccessDeniedException, NotExecutableException {
        String path = getPathToNonExistingNode();
        try {
            acMgr.getEffectivePolicies(path);
            fail("AccessControlManager.getEffectivePolicy for an invalid absPath must throw PathNotFoundException.");
        } catch (PathNotFoundException e) {
            // ok
        }
    }

    public void testGetEffectivePoliciesForProperty() throws RepositoryException, AccessDeniedException, NotExecutableException {
        String path = getPathToProperty();
        try {
            acMgr.getEffectivePolicies(path);
            fail("AccessControlManager.getEffectivePolicy for property must throw PathNotFoundException.");
        } catch (PathNotFoundException e) {
            // ok
        }
    }

    public void testGetPolicies() throws RepositoryException, AccessDeniedException, NotExecutableException {
        checkCanReadAc(path);
        // call must succeed without exception
        AccessControlPolicy[] policies = acMgr.getPolicies(path);

        assertNotNull("AccessControlManager.getPolicies must never return null.", policies);
        for (int i = 0; i < policies.length; i++) {
            if (policies[i] instanceof NamedAccessControlPolicy) {
                assertNotNull("The name of an NamedAccessControlPolicy must not be null.", ((NamedAccessControlPolicy) policies[i]).getName());
            } else if (policies[i] instanceof AccessControlList) {
                assertNotNull("The entries of an AccessControlList must not be null.", ((AccessControlList) policies[i]).getAccessControlEntries());
            }
        }
    }

    public void testGetApplicablePolicies() throws RepositoryException, AccessDeniedException, NotExecutableException {
        checkCanReadAc(path);
        // call must succeed without exception
        AccessControlPolicyIterator it = acMgr.getApplicablePolicies(path);
        assertNotNull("The iterator of applicable policies must not be null", it);
    }

    public void testApplicablePoliciesAreDistinct() throws RepositoryException, AccessDeniedException, NotExecutableException {
        checkCanReadAc(path);
        // call must succeed without exception
        AccessControlPolicyIterator it = acMgr.getApplicablePolicies(path);
        Set AccessControlList = new HashSet();

        while (it.hasNext()) {
            AccessControlPolicy policy = it.nextAccessControlPolicy();
            if (!AccessControlList.add(policy)) {
                fail("The applicable policies present should be unique among the choices. Policy " + policy + " occured multiple times.");
            }
        }
    }

    public void testSetPolicy() throws RepositoryException, AccessDeniedException, NotExecutableException {
        checkCanModifyAc(path);
        AccessControlPolicyIterator it = acMgr.getApplicablePolicies(path);
        if (it.hasNext()) {
            AccessControlPolicy policy = it.nextAccessControlPolicy();
            acMgr.setPolicy(path, policy);
        } else {
            throw new NotExecutableException();
        }
    }

    public void testSetIllegalPolicy() throws RepositoryException, AccessDeniedException, NotExecutableException {
        checkCanModifyAc(path);
        try {
            acMgr.setPolicy(path, new AccessControlPolicy() {});
            fail("SetPolicy with an unknown policy should throw AccessControlException.");
        } catch (AccessControlException e) {
            // success.
        }
    }

    public void testGetPolicyAfterSet() throws RepositoryException, AccessDeniedException, NotExecutableException {
        checkCanReadAc(path);
        checkCanModifyAc(path);

        AccessControlPolicyIterator it = acMgr.getApplicablePolicies(path);
        if (it.hasNext()) {
            AccessControlPolicy policy = it.nextAccessControlPolicy();
            acMgr.setPolicy(path, policy);

            AccessControlPolicy[] policies = acMgr.getPolicies(path);
            for (int i = 0; i < policies.length; i++) {
                if (policy.equals(policies[i])) {
                    // ok
                    return;
                }
            }
            fail("GetPolicies must at least return the policy that has been set before.");
        } else {
            throw new NotExecutableException();
        }
    }

    public void testSetPolicyIsTransient() throws RepositoryException, AccessDeniedException, NotExecutableException {
        checkCanModifyAc(path);

        List currentPolicies = Arrays.asList(acMgr.getPolicies(path));
        AccessControlPolicyIterator it = acMgr.getApplicablePolicies(path);
        if (it.hasNext()) {
            AccessControlPolicy policy = it.nextAccessControlPolicy();
            acMgr.setPolicy(path, policy);
            superuser.refresh(false);

            String mgs = "Reverting 'setPolicy' must change back the return value of getPolicies.";
            if (currentPolicies.isEmpty()) {
                assertTrue(mgs, acMgr.getPolicies(path).length == 0);
            } else {
                assertEquals(mgs, currentPolicies, Arrays.asList(acMgr.getPolicies(path)));
            }
        } else {
            throw new NotExecutableException();
        }
    }

    public void testGetPolicyAfterSave() throws RepositoryException, AccessDeniedException, NotExecutableException {
        checkCanReadAc(path);
        checkCanModifyAc(path);

        AccessControlPolicy policy;
        AccessControlPolicyIterator it = acMgr.getApplicablePolicies(path);
        if (it.hasNext()) {
            policy = it.nextAccessControlPolicy();
            acMgr.setPolicy(path, policy);
            superuser.save();

            // remember for tearDown
            addedPolicies.put(path, policy);
        } else {
            throw new NotExecutableException();
        }

        Session s2 = null;
        try {
            s2 = helper.getSuperuserSession();
            List plcs = Arrays.asList(getAccessControlManager(s2).getPolicies(path));
            // TODO: check again if policies can be compared with equals!
            assertTrue("Policy must be visible to another superuser session.", plcs.contains(policy));
        } finally {
            if (s2 != null) {
                s2.logout();
            }
        }
    }


    public void testNodeIsModifiedAfterSecondSetPolicy() throws RepositoryException, AccessDeniedException, NotExecutableException {
        checkCanModifyAc(path);
        // make sure an policy has been explicitely set.
        AccessControlPolicyIterator it = acMgr.getApplicablePolicies(path);
        if (it.hasNext()) {
            AccessControlPolicy policy = it.nextAccessControlPolicy();
            acMgr.setPolicy(path, policy);
            superuser.save();
            // remember for tearDown
            addedPolicies.put(path, policy);
        } else {
            throw new NotExecutableException();
        }

        // call 'setPolicy' a second time -> Node must be modified.
        it = acMgr.getApplicablePolicies(path);
        try {
            if (it.hasNext()) {
                Item item = superuser.getItem(path);
                AccessControlPolicy policy = it.nextAccessControlPolicy();
                acMgr.setPolicy(path, policy);

                assertTrue("After setting a policy the node must be marked modified.", item.isModified());
            } else {
                throw new NotExecutableException();
            }
        } finally {
            // revert changes
            superuser.refresh(false);
        }
    }

    public void testNodeIsModifiedAfterSetPolicy() throws RepositoryException, AccessDeniedException, NotExecutableException {
        checkCanModifyAc(path);
        AccessControlPolicyIterator it = acMgr.getApplicablePolicies(path);
        if (it.hasNext()) {
            Item item = superuser.getItem(path);

            AccessControlPolicy policy = it.nextAccessControlPolicy();
            acMgr.setPolicy(path, policy);

            assertTrue("After setting a policy the node must be marked modified.", item.isModified());
        } else {
            throw new NotExecutableException();
        }
    }

    public void testRemovePolicy() throws RepositoryException, AccessDeniedException, NotExecutableException {
        checkCanModifyAc(path);

        AccessControlPolicyIterator it = acMgr.getApplicablePolicies(path);
        if (it.hasNext()) {
            AccessControlPolicy policy = it.nextAccessControlPolicy();
            acMgr.setPolicy(path, policy);
            acMgr.removePolicy(path, policy);

            AccessControlPolicy[] plcs = acMgr.getPolicies(path);
            for (int i = 0; i < plcs.length; i++) {
                if (plcs[i].equals(policy)) {
                    fail("RemovePolicy must remove the policy that has been set before.");
                }
            }
        } else {
            throw new NotExecutableException();
        }
    }

    public void testRemovePolicyIsTransient() throws RepositoryException, AccessDeniedException, NotExecutableException {
        checkCanReadAc(path);
        checkCanModifyAc(path);

        AccessControlPolicy[] currentPolicies = acMgr.getPolicies(path);
        int size = currentPolicies.length;
        AccessControlPolicy toRemove;
        if (size == 0) {
            // no policy to remove ->> apply one
            AccessControlPolicyIterator it = acMgr.getApplicablePolicies(path);
            if (it.hasNext()) {
                AccessControlPolicy policy = it.nextAccessControlPolicy();
                acMgr.setPolicy(path, policy);
                superuser.save();

                // remember for teardown
                addedPolicies.put(path, policy);

                toRemove = policy;
                currentPolicies = acMgr.getPolicies(path);
                size = currentPolicies.length;
            } else {
                throw new NotExecutableException();
            }
        } else {
            toRemove = currentPolicies[0];
        }

        // test transient behaviour of the removal
        acMgr.removePolicy(path, toRemove);

        assertEquals("After transient remove AccessControlManager.getPolicies must return less policies.", size - 1, acMgr.getPolicies(path).length);

        // revert changes
        superuser.refresh(false);
        assertEquals("Reverting a Policy removal must restore the original state.", Arrays.asList(currentPolicies), Arrays.asList(acMgr.getPolicies(path)));
    }

    public void testNodeIsModifiedAfterRemovePolicy() throws RepositoryException, AccessDeniedException, NotExecutableException {
        checkCanReadAc(path);
        checkCanModifyAc(path);

        Item item = superuser.getItem(path);
        if (acMgr.getPolicies(path).length == 0) {
            // no policy to remove ->> apply one
            AccessControlPolicyIterator it = acMgr.getApplicablePolicies(path);
            if (it.hasNext()) {
                AccessControlPolicy policy = it.nextAccessControlPolicy();
                acMgr.setPolicy(path, policy);
                superuser.save();

                // remember for teardown
                addedPolicies.put(path, policy);
            } else {
                throw new NotExecutableException();
            }
        }

        // test transient behaviour of the removal
        try {
            AccessControlPolicy[] plcs = acMgr.getPolicies(path);
            if (plcs.length > 0) {
                acMgr.removePolicy(path, plcs[0]);
                assertTrue("After removing a policy the node must be marked modified.", item.isModified());
            }
        } finally {
            item.refresh(false);
        }
    }

    public void testNullPolicyOnNewNode() throws NotExecutableException, RepositoryException, AccessDeniedException {
        Node n;
        try {
            n = ((Node) superuser.getItem(path)).addNode(nodeName2, testNodeType);
        } catch (RepositoryException e) {
            throw new NotExecutableException();
        }

        assertTrue("A new Node must not have an access control policy set.", acMgr.getPolicies(n.getPath()).length == 0);
    }

    public void testSetPolicyOnNewNode() throws NotExecutableException, RepositoryException, AccessDeniedException {
        Node n;
        try {
            n = ((Node) superuser.getItem(path)).addNode(nodeName2, testNodeType);
        } catch (RepositoryException e) {
            throw new NotExecutableException();
        }

        AccessControlPolicyIterator it = acMgr.getApplicablePolicies(n.getPath());
        while (it.hasNext()) {
            AccessControlPolicy policy = it.nextAccessControlPolicy();
            acMgr.setPolicy(n.getPath(), policy);

            AccessControlPolicy[] plcs = acMgr.getPolicies(n.getPath());
            assertNotNull("After calling setPolicy the manager must return a non-null policy array for the new Node.", plcs);
            assertTrue("After calling setPolicy the manager must return a policy array with a length greater than zero for the new Node.", plcs.length > 0);
        }
    }

    public void testRemoveTransientlyAddedPolicy() throws RepositoryException, AccessDeniedException {
        AccessControlPolicy[] ex = acMgr.getPolicies(path);

        AccessControlPolicyIterator it = acMgr.getApplicablePolicies(path);
        while (it.hasNext()) {
            AccessControlPolicy policy = it.nextAccessControlPolicy();
            acMgr.setPolicy(path, policy);
            acMgr.removePolicy(path, policy);

            String msg = "transiently added AND removing a policy must revert " +
                    "the changes made. " +
                    "ACMgr.getPolicies must then return the original value.";
            assertEquals(msg, Arrays.asList(ex), Arrays.asList(acMgr.getPolicies(path)));
        }
    }
}