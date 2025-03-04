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

import org.apache.jackrabbit.api.jsr283.security.AccessControlEntry;
import org.apache.jackrabbit.api.jsr283.security.AccessControlException;
import org.apache.jackrabbit.api.jsr283.security.Privilege;
import org.apache.jackrabbit.api.jsr283.security.AbstractAccessControlTest;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.api.security.principal.PrincipalIterator;
import org.apache.jackrabbit.test.NotExecutableException;
import org.apache.jackrabbit.core.security.TestPrincipal;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.security.Principal;
import java.util.Collections;

/**
 * <code>AbstractACLTemplateTest</code>...
 */
public abstract class AbstractACLTemplateTest extends AbstractAccessControlTest {

    protected Principal testPrincipal;
    protected PrincipalManager pMgr;

    protected void setUp() throws Exception {
        super.setUp();

        if (!(superuser instanceof JackrabbitSession)) {
            throw new NotExecutableException();
        }

        pMgr = ((JackrabbitSession) superuser).getPrincipalManager();
        PrincipalIterator it = pMgr.getPrincipals(PrincipalManager.SEARCH_TYPE_NOT_GROUP);
        if (it.hasNext()) {
            testPrincipal = it.nextPrincipal();
        } else {
            throw new NotExecutableException();
        }
    }

    protected static void assertSamePrivileges(Privilege[] privs1, Privilege[] privs2) throws AccessControlException {
        assertEquals(PrivilegeRegistry.getBits(privs1), PrivilegeRegistry.getBits(privs2));
    }

    protected abstract String getTestPath();

    protected abstract JackrabbitAccessControlList createEmptyTemplate(String path) throws RepositoryException;

    public void testEmptyTemplate() throws RepositoryException {
        JackrabbitAccessControlList pt = createEmptyTemplate(getTestPath());

        assertNotNull(pt.getAccessControlEntries());
        assertTrue(pt.getAccessControlEntries().length == 0);
        assertTrue(pt.size() == pt.getAccessControlEntries().length);
        assertTrue(pt.isEmpty());
    }

    public void testGetPath() throws RepositoryException {
        JackrabbitAccessControlList pt = (JackrabbitAccessControlList) createEmptyTemplate(getTestPath());
        assertEquals(getTestPath(), pt.getPath());
    }

    public void testAddInvalidEntry() throws RepositoryException, NotExecutableException {
        Principal unknownPrincipal;
        if (!pMgr.hasPrincipal("an unknown principal")) {
            unknownPrincipal = new TestPrincipal("an unknown principal");
        } else {
            throw new NotExecutableException();
        }
        JackrabbitAccessControlList pt = (JackrabbitAccessControlList) createEmptyTemplate(getTestPath());
        try {
            pt.addAccessControlEntry(unknownPrincipal, privilegesFromName(Privilege.JCR_READ));
            fail("Adding an ACE with an unknown principal should fail");
        } catch (AccessControlException e) {
            // success
        }
    }

    public void testAddInvalidEntry2() throws RepositoryException {
        JackrabbitAccessControlList pt = (JackrabbitAccessControlList) createEmptyTemplate(getTestPath());
        try {
            pt.addAccessControlEntry(testPrincipal, new Privilege[0]);
            fail("Adding an ACE with invalid privileges should fail");
        } catch (AccessControlException e) {
            // success
        }
    }

    public void testRemoveInvalidEntry() throws RepositoryException {
        JackrabbitAccessControlList pt = (JackrabbitAccessControlList) createEmptyTemplate(getTestPath());
        try {
            pt.removeAccessControlEntry(new JackrabbitAccessControlEntry() {
                public boolean isAllow() {
                    return false;
                }
                public int getPrivilegeBits() {
                    return PrivilegeRegistry.READ;
                }
                public String[] getRestrictionNames() {
                    return new String[0];
                }
                public Value getRestriction(String restrictionName) {
                    return null;
                }
                public Principal getPrincipal() {
                    return testPrincipal;
                }

                public Privilege[] getPrivileges() {
                    try {
                        return privilegesFromName(Privilege.JCR_READ);
                    } catch (Exception e) {
                        return new Privilege[0];
                    }
                }
            });
            fail("Passing an unknown ACE should fail");
        } catch (AccessControlException e) {
            // success
        }
    }

    public void testRemoveInvalidEntry2() throws RepositoryException {
        JackrabbitAccessControlList pt = (JackrabbitAccessControlList) createEmptyTemplate(getTestPath());
        try {
            pt.removeAccessControlEntry(new JackrabbitAccessControlEntry() {
                public boolean isAllow() {
                    return false;
                }
                public int getPrivilegeBits() {
                    return 0;
                }
                public String[] getRestrictionNames() {
                    return new String[0];
                }
                public Value getRestriction(String restrictionName) {
                    return null;
                }
                public Principal getPrincipal() {
                    return testPrincipal;
                }
                public Privilege[] getPrivileges() {
                    return new Privilege[0];
                }
            });
            fail("Passing a ACE with invalid privileges should fail");
        } catch (AccessControlException e) {
            // success
        }
    }

    public void testAddEntry() throws RepositoryException, NotExecutableException {
        JackrabbitAccessControlList pt = createEmptyTemplate(getTestPath());
        Privilege[] privs = privilegesFromName(Privilege.JCR_READ);
        assertTrue(pt.addEntry(testPrincipal, privs, true, Collections.EMPTY_MAP));
    }

    public void testAddEntryTwice() throws RepositoryException, NotExecutableException {
        JackrabbitAccessControlList pt = createEmptyTemplate(getTestPath());
        Privilege[] privs = privilegesFromName(Privilege.JCR_READ);

        pt.addEntry(testPrincipal, privs, true, Collections.EMPTY_MAP);
        assertFalse(pt.addEntry(testPrincipal, privs, true, Collections.EMPTY_MAP));
    }

    public void testEffect() throws RepositoryException, NotExecutableException {
        JackrabbitAccessControlList pt = createEmptyTemplate(getTestPath());
        pt.addAccessControlEntry(testPrincipal, privilegesFromName(Privilege.JCR_READ));

        // add deny entry for mod_props
        assertTrue(pt.addEntry(testPrincipal, privilegesFromName(Privilege.JCR_MODIFY_PROPERTIES),
                false, null));

        // test net-effect
        int allows = PrivilegeRegistry.NO_PRIVILEGE;
        int denies = PrivilegeRegistry.NO_PRIVILEGE;
        AccessControlEntry[] entries = pt.getAccessControlEntries();
        for (int i = 0; i < entries.length; i++) {
            AccessControlEntry ace = entries[i];
            if (testPrincipal.equals(ace.getPrincipal()) && ace instanceof JackrabbitAccessControlEntry) {
                int entryBits = ((JackrabbitAccessControlEntry) ace).getPrivilegeBits();
                if (((JackrabbitAccessControlEntry) ace).isAllow()) {
                    allows |= Permission.diff(entryBits, denies);
                } else {
                    denies |= Permission.diff(entryBits, allows);
                }
            }
        }
        assertEquals(PrivilegeRegistry.READ, allows);
        assertEquals(PrivilegeRegistry.MODIFY_PROPERTIES, denies);
    }

    public void testEffect2() throws RepositoryException, NotExecutableException {
        JackrabbitAccessControlList pt = createEmptyTemplate(getTestPath());
        pt.addEntry(testPrincipal, privilegesFromName(Privilege.JCR_READ), true, Collections.EMPTY_MAP);

        // same entry but with revers 'isAllow' flag
        assertTrue(pt.addEntry(testPrincipal, privilegesFromName(Privilege.JCR_READ), false, Collections.EMPTY_MAP));

        // test net-effect
        int allows = PrivilegeRegistry.NO_PRIVILEGE;
        int denies = PrivilegeRegistry.NO_PRIVILEGE;
        AccessControlEntry[] entries = pt.getAccessControlEntries();
        for (int i = 0; i < entries.length; i++) {
            AccessControlEntry ace = entries[i];
            if (testPrincipal.equals(ace.getPrincipal()) && ace instanceof JackrabbitAccessControlEntry) {
                int entryBits = ((JackrabbitAccessControlEntry) ace).getPrivilegeBits();
                if (((JackrabbitAccessControlEntry) ace).isAllow()) {
                    allows |= Permission.diff(entryBits, denies);
                } else {
                    denies |= Permission.diff(entryBits, allows);
                }
            }
        }

        assertEquals(PrivilegeRegistry.NO_PRIVILEGE, allows);
        assertEquals(PrivilegeRegistry.READ, denies);
    }

    public void testRemoveEntry() throws RepositoryException,
            NotExecutableException {
        JackrabbitAccessControlList pt = createEmptyTemplate(getTestPath());
        pt.addAccessControlEntry(testPrincipal, privilegesFromName(Privilege.JCR_READ));
        pt.removeAccessControlEntry(pt.getAccessControlEntries()[0]);
    }

    public void testRemoveNonExisting() throws RepositoryException {
        JackrabbitAccessControlList pt = createEmptyTemplate(getTestPath());
        try {
            pt.removeAccessControlEntry(new AccessControlEntry() {
                public Principal getPrincipal() {
                    return testPrincipal;
                }
                public Privilege[] getPrivileges() {
                    return new Privilege[0];
                }
            });
            fail("Attemt to remove a non-existing, custom ACE must throw AccessControlException.");
        } catch (AccessControlException e) {
            // success
        }
    }
}