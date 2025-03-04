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
package org.apache.jackrabbit.core.security.authentication;

import org.apache.jackrabbit.core.config.LoginModuleConfig;
import org.apache.jackrabbit.core.security.principal.PrincipalProviderRegistry;

import javax.jcr.Credentials;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * A AuthContextProvider selects how the current request for login is handled.
 * It selects the mechanism and set-up according configuration.<br>
 * The handler selects if the JAAS-configuration
 * {@link javax.security.auth.login.Configuration} is taken or the fall-back as
 * configured via {@link org.apache.jackrabbit.core.config.RepositoryConfig}.<p>
 * This implementaion selects JAAS under the following condition:
 * <ul>
 * <li>a JAAS Login-{@link javax.security.auth.login.Configuration} is available
 * <li>the configuration contains the configured application name
 * </ul>
 * If the conditions are not met <b>AND</b> a LoginModule is configured in
 * {@link org.apache.jackrabbit.core.config.RepositoryConfig}, that one is taken.
 */
public class AuthContextProvider {

    private boolean initialized;

    /**
     * configuration state -> if a JAAS Configuration exists for this application
     */
    private boolean isJAAS;

    /**
     * Configuration of the optional local LoginModule
     */
    private final LoginModuleConfig config;

    /**
     * Application Name for the LoginConfig entry
     */
    private final String appName;

    /**
     * @param appName LoginConfig application name used for this instance
     * @param config optional LoginModule-configuration to use without JAAS
     */
    public AuthContextProvider(String appName, LoginModuleConfig config) {
        this.appName = appName;
        this.config = config;
    }

    /**
     *
     * @param credentials
     * @param subject
     * @param session
     * @param principalProviderRegistry
     * @return context of for authentication and log-out
     * @throws RepositoryException in case neither an <code>JAASContext</code>
     * nor a <code>LocalContext</code> can be successfully created.
     */
    public AuthContext getAuthContext(Credentials credentials,
                                      Subject subject,
                                      Session session,
                                      PrincipalProviderRegistry principalProviderRegistry,
                                      String adminId,
                                      String anonymousId)
            throws RepositoryException {

        CallbackHandler cbHandler = new CallbackHandlerImpl(credentials, session, principalProviderRegistry, adminId, anonymousId);

        if (isJAAS()) {
            return new JAASAuthContext(appName, cbHandler, subject);
        } else if (isLocal()){
            return new LocalAuthContext(config, cbHandler, subject);
        } else {
            throw new RepositoryException("No Login-Configuration");
        }
    }

    /**
     * @return true if a application entry is available in a JAAS- {@link Configuration}
     */
    public boolean isJAAS() {
        if (!initialized) {
            AppConfigurationEntry[] entries = getJAASConfig();
            isJAAS = null!=entries && entries.length>0;
            initialized = true;
        }
        return isJAAS;
    }

    /**
     * @return true if {@link #isJAAS()} is false and a login-module is configured
     */
    public boolean isLocal() {
        return !(isJAAS() || config == null);
    }

    /**
     * @return options configured for the LoginModules to use
     */
    public Properties[] getModuleConfig() {
        Properties[] props = new Properties[0];
        if (isLocal()) {
            props = new Properties[] {config.getParameters()};
        } else {
            AppConfigurationEntry[] entries = getJAASConfig();
            if(entries != null) {
                List tmp = new ArrayList(entries.length);
                for(int i=0; i < entries.length; i++) {
                    Map opt = entries[i].getOptions();
                    if (opt != null) {
                        Properties prop = new Properties();
                        prop.putAll(opt);
                        tmp.add(prop);
                    }
                }
                props = (Properties[]) tmp.toArray(new Properties[tmp.size()]);
            }
        }
        return props;
    }

    /**
     * @return all JAAS-Login Modules for this application or null if none
     */
    private AppConfigurationEntry[] getJAASConfig() {

        // check if jaas-loginModule or fallback is configured
        Configuration logins = null;
        try {
            logins = Configuration.getConfiguration();
        } catch (Exception e) {
            // means no JAAS configuration file OR no permission to read it
        }
        if (logins != null) {
            try {
                return logins.getAppConfigurationEntry(appName);
            } catch (Exception e) {
                // WLP 9.2.0 throws IllegalArgumentException for unknown appName
            }
        }
        return null;
    }
}
