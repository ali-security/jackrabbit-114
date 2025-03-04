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

package org.apache.jackrabbit.ocm.manager.objectconverter.impl;

import javax.jcr.Node;
import javax.jcr.Session;

import org.apache.jackrabbit.ocm.manager.collectionconverter.CollectionConverter;
import org.apache.jackrabbit.ocm.manager.collectionconverter.ManageableObjects;
import org.apache.jackrabbit.ocm.mapper.model.CollectionDescriptor;

public class CollectionLazyLoader extends AbstractLazyLoader {

	private CollectionConverter collectionConverter;
	private Session session;
	private Node collectionParentNode;
	private CollectionDescriptor collectionDescriptor;
	private Class<?> collectionFieldClass;

	public CollectionLazyLoader(CollectionConverter collectionConverter, Session session, Node parentNode,
			CollectionDescriptor collectionDescriptor, Class<?> collectionFieldClass) {
		this.collectionConverter = collectionConverter;
		this.session = session;
		this.collectionParentNode = parentNode;
		this.collectionDescriptor = collectionDescriptor;
		this.collectionFieldClass = collectionFieldClass;
	}

	@Override
	protected Object fetchTarget() {
		if (isInitialized()) {
			throw new IllegalStateException("Proxy already initialized");
		}
		ManageableObjects objects = collectionConverter.getCollection(session, collectionParentNode, collectionDescriptor,
				collectionFieldClass);
		Object target = objects.getObjects();
		clean();
		return target;
	}

	private void clean() {
		collectionConverter = null;
		session = null;
		collectionParentNode = null;
		collectionDescriptor = null;
	}
}
