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
package org.apache.jackrabbit.spi.commons.value;

import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NameException;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.Path;
import org.apache.jackrabbit.spi.QValue;
import org.apache.jackrabbit.spi.QValueFactory;

import javax.jcr.RepositoryException;
import javax.jcr.PropertyType;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * <code>ValueFormat</code>...
 */
public class ValueFormat {

    /**
     *
     * @param jcrValue
     * @param resolver
     * @param factory
     * @return
     * @throws RepositoryException
     */
    public static QValue getQValue(Value jcrValue, NamePathResolver resolver,
                                   QValueFactory factory) throws RepositoryException {
        if (jcrValue == null) {
            throw new IllegalArgumentException("null value");
        } else if (jcrValue instanceof QValueValue) {
            return ((QValueValue)jcrValue).getQValue();
        } else if (jcrValue.getType() == PropertyType.BINARY) {
            try {
                return factory.create(jcrValue.getStream());
            } catch (IOException e) {
                throw new RepositoryException(e);
            }
        } else if (jcrValue.getType() == PropertyType.DATE) {
            return factory.create(jcrValue.getDate());
        } else if (jcrValue.getType() == PropertyType.DOUBLE) {
            return factory.create(jcrValue.getDouble());
        } else if (jcrValue.getType() == PropertyType.LONG) {
            return factory.create(jcrValue.getLong());
        } else {
            return getQValue(jcrValue.getString(), jcrValue.getType(), resolver, factory);
        }
    }

    /**
     *
     * @param jcrValues
     * @param resolver
     * @param factory
     * @return
     * @throws RepositoryException
     */
    public static QValue[] getQValues(Value[] jcrValues,
                                      NamePathResolver resolver,
                                      QValueFactory factory) throws RepositoryException {
        if (jcrValues == null) {
            throw new IllegalArgumentException("null value");
        }
        List qValues = new ArrayList();
        for (int i = 0; i < jcrValues.length; i++) {
            if (jcrValues[i] != null) {
                qValues.add(getQValue(jcrValues[i], resolver, factory));
            }
        }
        return (QValue[]) qValues.toArray(new QValue[qValues.size()]);
    }

    /**
     *
     * @param jcrValue
     * @param propertyType
     * @param resolver
     * @param factory
     * @return
     * @throws RepositoryException
     */
    public static QValue getQValue(String jcrValue, int propertyType,
                                   NamePathResolver resolver,
                                   QValueFactory factory) throws RepositoryException {
        QValue qValue;
        switch (propertyType) {
            case PropertyType.STRING:
            case PropertyType.BOOLEAN:
            case PropertyType.DOUBLE:
            case PropertyType.LONG:
            case PropertyType.DATE:
            case PropertyType.REFERENCE:
                qValue = factory.create(jcrValue, propertyType);
                break;
            case PropertyType.BINARY:
                qValue = factory.create(jcrValue.getBytes());
                break;
            case PropertyType.NAME:
                try {
                    Name qName = resolver.getQName(jcrValue);
                    qValue = factory.create(qName);
                } catch (NameException e) {
                    throw new RepositoryException(e);
                }
                break;
            case PropertyType.PATH:
                try {
                    Path qPath = resolver.getQPath(jcrValue).getNormalizedPath();
                    qValue = factory.create(qPath);
                } catch (NameException e) {
                    throw new RepositoryException(e);
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid property type.");
        }
        return qValue;
    }

    /**
     * @param qualifiedValue
     * @param resolver
     * @return
     * @throws RepositoryException
     */
    public static Value getJCRValue(QValue qualifiedValue,
                                    NamePathResolver resolver,
                                    ValueFactory factory) throws RepositoryException {
        if (factory instanceof ValueFactoryQImpl) {
            return ((ValueFactoryQImpl)factory).createValue(qualifiedValue);
        } else {
            Value jcrValue;
            int propertyType = qualifiedValue.getType();
            switch (propertyType) {
                case PropertyType.STRING:
                case PropertyType.REFERENCE:
                    jcrValue = factory.createValue(qualifiedValue.getString(), propertyType);
                    break;
                case PropertyType.PATH:
                    Path qPath = qualifiedValue.getPath();
                    jcrValue = factory.createValue(resolver.getJCRPath(qPath), propertyType);
                    break;
                case PropertyType.NAME:
                    Name qName = qualifiedValue.getName();
                    jcrValue = factory.createValue(resolver.getJCRName(qName), propertyType);
                    break;
                case PropertyType.BOOLEAN:
                    jcrValue = factory.createValue(qualifiedValue.getBoolean());
                    break;
                case PropertyType.BINARY:
                    jcrValue = factory.createValue(qualifiedValue.getStream());
                    break;
                case PropertyType.DATE:
                    jcrValue = factory.createValue(qualifiedValue.getCalendar());
                    break;
                case PropertyType.DOUBLE:
                    jcrValue = factory.createValue(qualifiedValue.getDouble());
                    break;
                case PropertyType.LONG:
                    jcrValue = factory.createValue(qualifiedValue.getLong());
                    break;
                default:
                    throw new RepositoryException("illegal internal value type");
            }
            return jcrValue;
        }
    }
}