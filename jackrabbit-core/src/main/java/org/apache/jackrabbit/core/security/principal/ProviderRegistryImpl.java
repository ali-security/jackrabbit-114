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

import org.apache.jackrabbit.core.config.BeanConfig;
import org.apache.jackrabbit.core.config.LoginModuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * This is the default implementation of the {@link PrincipalProviderRegistry}
 * interface.
 */
public class ProviderRegistryImpl implements PrincipalProviderRegistry {

    /** the default logger */
    private static final Logger log = LoggerFactory.getLogger(ProviderRegistryImpl.class);

    private final PrincipalProvider defaultPrincipalProvider;
    private final Map providers = new HashMap();

    /**
     * Create an instance of <code>ProviderRegistryImpl</code> with the given
     * default principal provider.
     * NOTE that the provider must be initialized by the caller.
     *
     * @param defaultPrincipalProvider
     */
    public ProviderRegistryImpl(PrincipalProvider defaultPrincipalProvider) {
        this.defaultPrincipalProvider = defaultPrincipalProvider;
        providers.put(defaultPrincipalProvider.getClass().getName(), defaultPrincipalProvider);
    }


    //------------------------------------------< PrincipalProviderRegistry >---
    /**
     * @see PrincipalProviderRegistry#registerProvider(Properties)
     */
    public PrincipalProvider registerProvider(Properties config) throws RepositoryException {
        PrincipalProvider provider = createProvider(config);
        if (provider != null) {
            synchronized(providers) {
                providers.put(provider.getClass().getName(), provider);
            }
        } else {
            log.debug("Could not register principal provider: " +
                    LoginModuleConfig.PARAM_PRINCIPAL_PROVIDER_CLASS +
                    " configuration entry missing.");
        }
        return provider;
    }

    /**
     * @see PrincipalProviderRegistry#getDefault()
     */
    public PrincipalProvider getDefault() {
        return defaultPrincipalProvider;
    }

    /**
     * @see PrincipalProviderRegistry#getProviders()
     */
    public PrincipalProvider getProvider(String className) {
        synchronized (providers) {
            return (PrincipalProvider) providers.get(className);
        }
    }

    /**
     * @see PrincipalProviderRegistry#getProviders()
     */
    public PrincipalProvider[] getProviders() {
        synchronized (providers) {
            Collection pps = providers.values();
            return (PrincipalProvider[]) pps.toArray(new PrincipalProvider[pps.size()]);
        }
    }

    //--------------------------------------------------------------------------
    /**
     * Read the map and instanciate the class indicated by the
     * {@link LoginModuleConfig#PARAM_PRINCIPAL_PROVIDER_CLASS} key.<br>
     * The class gets set the properties of the given map, via a Bean mechanism
     *
     * @param config
     * @return the new provider instance or <code>null</code> if the
     * configuration does not contain the required entry.
     */
    private PrincipalProvider createProvider(Properties config)
            throws RepositoryException {

        String className = config.getProperty(LoginModuleConfig.PARAM_PRINCIPAL_PROVIDER_CLASS);
        if (className == null) {
            return null;
        }

        try {
            Class pc = Class.forName(className, true, BeanConfig.getDefaultClassLoader());
            PrincipalProvider pp = (PrincipalProvider) pc.newInstance();
            pp.init(config);
            return pp;
        } catch (ClassNotFoundException e) {
            throw new RepositoryException("Unable to create new principal provider.", e);
        } catch (IllegalAccessException e) {
            throw new RepositoryException("Unable to create new principal provider.", e);
        } catch (InstantiationException e) {
            throw new RepositoryException("Unable to create new principal provider.", e);
        } catch (ClassCastException e) {
            throw new RepositoryException("Unable to create new principal provider.", e);
        }
    }
}
