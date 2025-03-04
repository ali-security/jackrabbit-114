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
package org.apache.jackrabbit.core.security.authorization;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.jsr283.security.AbstractAccessControlTest;
import org.apache.jackrabbit.api.jsr283.security.AccessControlEntry;
import org.apache.jackrabbit.api.jsr283.security.AccessControlPolicy;
import org.apache.jackrabbit.api.jsr283.security.AccessControlPolicyIterator;
import org.apache.jackrabbit.api.jsr283.security.Privilege;
import org.apache.jackrabbit.api.security.principal.PrincipalIterator;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.test.NotExecutableException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <code>JackrabbitAccessControlListTest</code>...
 */
public class JackrabbitAccessControlListTest extends AbstractAccessControlTest {

    private JackrabbitAccessControlList templ;

    protected void setUp() throws Exception {
        super.setUp();

        Node n = testRootNode.addNode(nodeName1, testNodeType);
        superuser.save();

        AccessControlPolicyIterator it = acMgr.getApplicablePolicies(n.getPath());
        if (it.hasNext()) {
            AccessControlPolicy p = it.nextAccessControlPolicy();
            if (p instanceof JackrabbitAccessControlList) {
                templ = (JackrabbitAccessControlList) p;
            } else {
                throw new NotExecutableException("No JackrabbitAccessControlList to test.");
            }
        } else {
            throw new NotExecutableException("No JackrabbitAccessControlList to test.");
        }
    }

    protected void tearDown() throws Exception {
        // make sure transient ac-changes are reverted.
        superuser.refresh(false);
        super.tearDown();
    }

    private Principal getValidPrincipal() throws NotExecutableException, RepositoryException {
        if (!(superuser instanceof JackrabbitSession)) {
            throw new NotExecutableException();
        }

        PrincipalManager pMgr = ((JackrabbitSession) superuser).getPrincipalManager();
        PrincipalIterator it = pMgr.getPrincipals(PrincipalManager.SEARCH_TYPE_NOT_GROUP);
        if (it.hasNext()) {
            return it.nextPrincipal();
        } else {
            throw new NotExecutableException();
        }
    }

    public void testIsEmpty() throws RepositoryException {
        if (templ.isEmpty()) {
            assertEquals(0, templ.getAccessControlEntries().length);
        } else {
            assertTrue(templ.getAccessControlEntries().length > 0);
        }
    }

    public void testSize() {
        if (templ.isEmpty()) {
            assertEquals(0, templ.size());
        } else {
            assertTrue(templ.size() > 0);
        }
    }

    public void testAddEntry() throws NotExecutableException, RepositoryException {
        Principal princ = getValidPrincipal();
        Privilege[] priv = privilegesFromName(Privilege.JCR_ALL);

        List entriesBefore = Arrays.asList(templ.getAccessControlEntries());
        if (templ.addEntry(princ, priv, true, Collections.EMPTY_MAP)) {
            AccessControlEntry[] entries = templ.getAccessControlEntries();
            if (entries.length == 0) {
                fail("GrantPrivileges was successful -> at least 1 entry for principal.");
            }
            int allows = 0;
            for (int i = 0; i < entries.length; i++) {
                AccessControlEntry en = entries[i];
                int bits = PrivilegeRegistry.getBits(en.getPrivileges());
                if (en instanceof JackrabbitAccessControlEntry && ((JackrabbitAccessControlEntry) en).isAllow()) {
                    allows |= bits;
                }
            }
            assertEquals(PrivilegeRegistry.ALL, allows);
        } else {
            AccessControlEntry[] entries = templ.getAccessControlEntries();
            assertEquals("Grant ALL not successful -> entries must not have changed.", entriesBefore, Arrays.asList(entries));
        }
    }

    public void testAddEntry2() throws NotExecutableException, RepositoryException {
        Principal princ = getValidPrincipal();
        Privilege[] privs = privilegesFromName(Privilege.JCR_WRITE);

        int allows = 0;
        templ.addEntry(princ, privs, true, Collections.EMPTY_MAP);
        AccessControlEntry[] entries = templ.getAccessControlEntries();
        assertTrue("GrantPrivileges was successful -> at least 1 entry for principal.", entries.length > 0);

        for (int i = 0; i < entries.length; i++) {
            AccessControlEntry en = entries[i];
            int bits = PrivilegeRegistry.getBits(en.getPrivileges());
            if (en instanceof JackrabbitAccessControlEntry && ((JackrabbitAccessControlEntry) en).isAllow()) {
                allows |= bits;
            }
        }
        assertTrue("After successfully granting WRITE, the entries must reflect this", allows >= PrivilegeRegistry.WRITE);
    }

    public void testAllowWriteDenyRemove() throws NotExecutableException, RepositoryException {
        Principal princ = getValidPrincipal();
        Privilege[] grPriv = privilegesFromName(Privilege.JCR_WRITE);
        Privilege[] dePriv = privilegesFromName(Privilege.JCR_REMOVE_CHILD_NODES);

        templ.addEntry(princ, grPriv, true, Collections.EMPTY_MAP);
        templ.addEntry(princ, dePriv, false, Collections.EMPTY_MAP);

        int allows = PrivilegeRegistry.NO_PRIVILEGE;
        int denies = PrivilegeRegistry.NO_PRIVILEGE;
        AccessControlEntry[] entries = templ.getAccessControlEntries();
        for (int i = 0; i < entries.length; i++) {
            AccessControlEntry en = entries[i];
            if (princ.equals(en.getPrincipal()) && en instanceof JackrabbitAccessControlEntry) {
                JackrabbitAccessControlEntry ace = (JackrabbitAccessControlEntry) en;
                int entryBits = ace.getPrivilegeBits();
                if (ace.isAllow()) {
                    allows |= Permission.diff(entryBits, denies);
                } else {
                    denies |= Permission.diff(entryBits, allows);
                }
            }
        }

        int expectedAllows = Permission.diff(PrivilegeRegistry.WRITE, PrivilegeRegistry.REMOVE_CHILD_NODES);
        assertEquals(expectedAllows, allows & expectedAllows);
        int expectedDenies = PrivilegeRegistry.REMOVE_CHILD_NODES;
        assertEquals(expectedDenies, denies);
    }

    public void testRemoveEntry() throws NotExecutableException, RepositoryException {
        Principal princ = getValidPrincipal();
        Privilege[] grPriv = privilegesFromName(Privilege.JCR_WRITE);

        templ.addEntry(princ, grPriv, true, Collections.EMPTY_MAP);
        AccessControlEntry[] entries = templ.getAccessControlEntries();
        int length = entries.length;
        assertTrue("Grant was both successful -> at least 1 entry.", length > 0);
        for (int i = 0; i < entries.length; i++) {
            templ.removeAccessControlEntry(entries[i]);
            length = length - 1;
            assertEquals(length, templ.size());
            assertEquals(length, templ.getAccessControlEntries().length);
        }

        assertTrue(templ.isEmpty());
        assertEquals(0, templ.size());
        assertEquals(0, templ.getAccessControlEntries().length);
    }
}