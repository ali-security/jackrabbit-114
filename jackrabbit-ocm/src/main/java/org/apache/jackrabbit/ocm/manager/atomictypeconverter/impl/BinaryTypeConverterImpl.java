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

package org.apache.jackrabbit.ocm.manager.atomictypeconverter.impl;

import java.io.InputStream;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;

import org.apache.jackrabbit.ocm.exception.IncorrectAtomicTypeException;
import org.apache.jackrabbit.ocm.manager.atomictypeconverter.AtomicTypeConverter;

/**
 *
 * Binary Type Converter
 *
 * @author <a href="mailto:christophe.lombart@gmail.com">Christophe Lombart</a>
 * @author <a href='mailto:the_mindstorm[at]evolva[dot]ro'>Alexandru Popescu</a>
 */
public class BinaryTypeConverterImpl implements AtomicTypeConverter
{
	/**
	 *
	 * @see org.apache.jackrabbit.ocm.manager.atomictypeconverter.AtomicTypeConverter#getValue(java.lang.Object)
	 */
    public Value getValue(ValueFactory valueFactory, Object propValue)
    {
        if (propValue == null)
        {
            return null;
        }
        return valueFactory.createValue((InputStream) propValue);
    }

    /**
     *
     * @see org.apache.jackrabbit.ocm.manager.atomictypeconverter.AtomicTypeConverter#getObject(javax.jcr.Value)
     */
    public Object getObject(Value value)
    {
    	try
    	{
		    return value.getStream();
		}
		catch (RepositoryException e)
		{
			throw new IncorrectAtomicTypeException("Impossible to convert the value : " + value.toString()  , e);
		}
    }


    /**
     *
     * @see org.apache.jackrabbit.ocm.manager.atomictypeconverter.AtomicTypeConverter#getStringValue(java.lang.Object)
     */
	public String getXPathQueryValue(ValueFactory valueFactory,Object object)
	{		
		throw new IncorrectAtomicTypeException("Binary cannot be used in queries");
	}
}
