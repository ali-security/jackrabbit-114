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
package org.apache.jackrabbit.core.security.principal;

import org.apache.commons.collections.map.LRUMap;

import java.security.Principal;
import java.util.Properties;

/**
 * A base class of a principal provider implementing common tasks and a
 * caching facility. Extending classes should only deal with the retrieval of
 * {@link Principal}s from their source, the caching of the principials is done
 * by this implementation.
 * <p/>
 * The {@link PrincipalProvider} mehtods that that involve searching like
 * {@link PrincipalProvider#getGroupMembership(Principal)} are not cached.
 */
public abstract class AbstractPrincipalProvider implements PrincipalProvider {

    /** Option name for the max size of the cache to use */
    public static final String MAXSIZE_KEY = "cacheMaxSize";

    /** flag indicating if the instance has not been {@link #close() closed} */
    private boolean initialized;

    /** the principal cache */
    private LRUMap cache;

    /**
     * Create a new instance of <code>AbstractPrincipalProvider</code>.
     * Initialization and cache are set up upon {@link #init(Properties)}
     */
    protected AbstractPrincipalProvider() {
    }

    /**
     * Check if the instance has been closed {@link #close()}.
     *
     * @throws IllegalStateException if this instance was closed.
     */
    protected void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Not initialized.");
        }
    }

    /**
     * Clear the principal cache.
     */
    protected synchronized void clearCache() {
        cache.clear();
    }

    /**
     * Add an entry to the principal cache.
     */
    protected synchronized void addToCache(Principal principal) {
        cache.put(principal.getName(), principal);
    }

    /**
     * Called if the cache does not contain the principal requested.<br>
     * Implementations should return a {@link Principal} from their source,
     * if it contains one for the given name or <code>null</code>.
     *
     * @param principalName
     * @return Principal or null, if non provided for the given name
     * @see #getPrincipal(String)
     */
    protected abstract Principal providePrincipal(String principalName);

    //--------------------------------------------------< PrincipalProvider >---
    /**
     * {@inheritDoc}
     *
     * {@link #providePrincipal(String)} is called, if no principal with the
     * given name is present in the cache.
     */
    public synchronized Principal getPrincipal(String principalName) {
        checkInitialized();
        Principal principal = (Principal) cache.get(principalName);
        if (principal == null) {
            principal = providePrincipal(principalName);
            if (principal != null) {
                addToCache(principal);
            }
        }
        return principal;
    }

    /**
     * @see PrincipalProvider#init(java.util.Properties)
     */
    public synchronized void init(Properties options) {
        if (initialized) {
            throw new IllegalStateException("already initialized");
        }

        int maxSize = Integer.parseInt(options.getProperty(MAXSIZE_KEY, "1000"));
        cache = new LRUMap(maxSize);

        initialized = true;
    }

    /**
     * Clears the cache and calls the implementation to close their resources
     * @see PrincipalProvider#close()
     */
    public synchronized void close() {
        checkInitialized();
        cache.clear();
        initialized = false;
    }
}
