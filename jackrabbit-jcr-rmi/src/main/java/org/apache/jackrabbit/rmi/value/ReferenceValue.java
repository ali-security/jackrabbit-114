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
package org.apache.jackrabbit.rmi.value;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;

/**
 * @deprecated RMI support is deprecated and will be removed in a future version of Jackrabbit; see <a href=https://issues.apache.org/jira/browse/JCR-4972 target=_blank>Jira ticket JCR-4972</a> for more information.
 * <p>
 * The <code>ReferenceValue</code> class implements the committed value state
 * for Reference values as a part of the State design pattern (Gof) used by
 * this package.
 *
 * @since 0.16.4.1
 */
@Deprecated(forRemoval = true) public class ReferenceValue extends AbstractValue {

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 5245358709465803351L;

    /** The reference value */
    private final String value;

    /**
     * Creates an instance for the given reference <code>value</code>.
     */
    protected ReferenceValue(String value) throws ValueFormatException {
        // TODO: check syntax
        this.value = value;
    }

    /**
     * Returns <code>PropertyType.REFERENCE</code>.
     */
    public int getType() {
        return PropertyType.REFERENCE;
    }

    /**
     * Returns the string representation of the reference value.
     */
    public String getString() throws ValueFormatException, RepositoryException {
        return value;
    }
}
