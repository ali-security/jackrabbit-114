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
package org.apache.jackrabbit.ocm.manager.collectionconverter.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.ValueFormatException;

import org.apache.jackrabbit.ocm.exception.ObjectContentManagerException;
import org.apache.jackrabbit.ocm.manager.atomictypeconverter.AtomicTypeConverter;
import org.apache.jackrabbit.ocm.manager.collectionconverter.ManageableCollection;
import org.apache.jackrabbit.ocm.manager.collectionconverter.ManageableObjectsUtil;
import org.apache.jackrabbit.ocm.manager.collectionconverter.ManageableMap;
import org.apache.jackrabbit.ocm.manager.collectionconverter.ManageableObjects;
import org.apache.jackrabbit.ocm.manager.objectconverter.ObjectConverter;
import org.apache.jackrabbit.ocm.mapper.Mapper;
import org.apache.jackrabbit.ocm.mapper.model.CollectionDescriptor;
import org.apache.jackrabbit.ocm.reflection.ReflectionUtils;

/**
 * The <code>ResidualPropertiesCollectionConverterImpl</code> is a collection
 * converter for multiple properties accessed through
 * Node.getProperties(String pattern).
 *
 * @author <a href="mailto:fmeschbe[at]apache[dot]com">Felix Meschberger</a>
 */
public class ResidualPropertiesCollectionConverterImpl extends
        AbstractCollectionConverterImpl {

    /**
     * Constructor
     *
     * @param atomicTypeConverters
     * @param objectConverter
     * @param mapper
     */
    public ResidualPropertiesCollectionConverterImpl(Map atomicTypeConverters,
        ObjectConverter objectConverter, Mapper mapper) {
        super(atomicTypeConverters, objectConverter, mapper);
    }

    /**
     *
     * @see AbstractCollectionConverterImpl#doInsertCollection(Session, Node, CollectionDescriptor, ManageableCollection)
     */
    protected void doInsertCollection(Session session, Node parentNode,
        CollectionDescriptor collectionDescriptor,
        ManageableObjects objects) throws RepositoryException {
        internalSetProperties(session, parentNode, collectionDescriptor,
            objects, false);
    }

    /**
     *
     * @see AbstractCollectionConverterImpl#doUpdateCollection(Session, Node, CollectionDescriptor, ManageableCollection)
     */
    protected void doUpdateCollection(Session session, Node parentNode,
        CollectionDescriptor collectionDescriptor,
        ManageableObjects objects) throws RepositoryException {
        internalSetProperties(session, parentNode, collectionDescriptor,
            objects, true);
    }

    /**
     * @see AbstractCollectionConverterImpl#doGetCollection(Session, Node, CollectionDescriptor, Class)
     */
    protected ManageableObjects doGetCollection(Session session,
        Node parentNode, CollectionDescriptor collectionDescriptor,
        Class collectionFieldClass) throws RepositoryException {
        try {
            String jcrName = getCollectionJcrName(collectionDescriptor);
            PropertyIterator pi = parentNode.getProperties(jcrName);
            if (!pi.hasNext()) {
                return null;
            }

            ManageableObjects objects = ManageableObjectsUtil.getManageableObjects(collectionFieldClass);
            AtomicTypeConverter atomicTypeConverter = getAtomicTypeConverter(collectionDescriptor);

            while (pi.hasNext()) {
                Property prop = pi.nextProperty();

                // ignore protected properties here
                if (prop.getDefinition().isProtected()) {
                    continue;
                }

                // handle multvalues as a list
                Object value;
                if (prop.getDefinition().isMultiple()) {
                    List valueList = new ArrayList();
                    Value[] values = prop.getValues();
                    for (int i = 0; i < values.length; i++) {
                        valueList.add(atomicTypeConverter.getObject(values[i]));
                    }
                    value = valueList;
                } else {
                    value = atomicTypeConverter.getObject(prop.getValue());
                }

                if (objects instanceof Map) {
                    String name = prop.getName();
                    ((Map) objects).put(name, value);
                } else {
                	if (objects instanceof ManageableCollection)
                         ((ManageableCollection)objects).addObject(value);
                	else
                	{
                		String name = prop.getName();
                		((ManageableMap)objects).addObject(name, value);
                	}
                }
            }

            return objects;
        } catch (ValueFormatException vfe) {
            throw new ObjectContentManagerException("Cannot get the collection field : "
                + collectionDescriptor.getFieldName() + "for class "
                + collectionDescriptor.getClassDescriptor().getClassName(), vfe);
        }
    }

    /**
     * @see AbstractCollectionConverterImpl#doIsNull(Session, Node, CollectionDescriptor, Class)
     */
    protected boolean doIsNull(Session session, Node parentNode,
            CollectionDescriptor collectionDescriptor, Class collectionFieldClass)
            throws RepositoryException {
        String jcrName = getCollectionJcrName(collectionDescriptor);
        return (parentNode == null || !parentNode.getProperties(jcrName).hasNext());
    }

    private void internalSetProperties(Session session, Node parentNode,
        CollectionDescriptor collectionDescriptor,
        ManageableObjects objects, boolean removeExisting)
        throws RepositoryException {

        String jcrName = getCollectionJcrName(collectionDescriptor);

        // can only persist maps, not general collections
        if (!(objects instanceof ManageableMap)) {
            return;
        }

        // Delete existing values - before checking for collection !
        if (removeExisting) {
            for (PropertyIterator pi = parentNode.getProperties(jcrName); pi.hasNext();) {
                Property prop = pi.nextProperty();
                if (!prop.getDefinition().isProtected()) {
                    prop.remove();
                }
            }
        }

        AtomicTypeConverter atomicTypeConverter = getAtomicTypeConverter(collectionDescriptor);

        try {
            Map map = (Map) objects.getObjects();
            ValueFactory valueFactory = session.getValueFactory();
            for (Iterator ei = map.entrySet().iterator(); ei.hasNext();) {
                Map.Entry entry = (Map.Entry) ei.next();
                String name = String.valueOf(entry.getKey());

                // verify the property is not an existing protected property
                if (parentNode.hasProperty(name)
                    && parentNode.getProperty(name).getDefinition().isProtected()) {
                    continue;
                }

                Object value = entry.getValue();
                if (value instanceof List) {
                    // multi value
                    List valueList = (List) value;
                    Value[] jcrValues = new Value[valueList.size()];
                    int i = 0;
                    for (Iterator vi = valueList.iterator(); vi.hasNext();) {
                        value = vi.next();
                        jcrValues[i++] = atomicTypeConverter.getValue(
                            valueFactory, value);
                    }
                    parentNode.setProperty(name, jcrValues);
                } else {
                    // single value
                    Value jcrValue = atomicTypeConverter.getValue(valueFactory,
                        value);
                    parentNode.setProperty(name, jcrValue);
                }
            }
        } catch (ValueFormatException vfe) {
            throw new ObjectContentManagerException("Cannot insert collection field : "
                + collectionDescriptor.getFieldName() + " of class "
                + collectionDescriptor.getClassDescriptor().getClassName(), vfe);
        }
    }

    /**
     * Returns the AtomicTypeConverter for the element class of the described
     * collection. If no such converter can be found a ObjectContentManagerException
     * is thrown.
     *
     * @param collectionDescriptor The descriptor of the collection for whose
     *      elements an AtomicTypeConverter is requested.
     *
     * @return The AtomicTypeConverter for the elements of the collection
     *
     * @throws ObjectContentManagerException if no such type converter is registered
     */
    private AtomicTypeConverter getAtomicTypeConverter(CollectionDescriptor collectionDescriptor) {
        String elementClassName = collectionDescriptor.getElementClassName();
        Class elementClass = ReflectionUtils.forName(elementClassName);
        AtomicTypeConverter atc = (AtomicTypeConverter) atomicTypeConverters.get(elementClass);
        if (atc != null) {
            return atc;
        }

        throw new ObjectContentManagerException(
            "Cannot get AtomicTypeConverter for element class "
                + elementClassName + " of class "
                + collectionDescriptor.getClassDescriptor().getClassName());
    }
}
