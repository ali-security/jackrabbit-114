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
package org.apache.jackrabbit.core.query.lucene;

import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.Payload;

/**
 * <code>SingletonTokenStream</code> implements a token stream that wraps a
 * single value with a given property type. The property type is stored as a
 * payload on the single returned token.
 */
public final class SingletonTokenStream extends TokenStream {

    /**
     * The single token to return.
     */
    private Token t;

    /**
     * Creates a new SingleTokenStream with the given value and a property
     * <code>type</code>.
     *
     * @param value the string value that will be returned with the token.
     * @param type the JCR property type.
     */
    public SingletonTokenStream(String value, int type) {
        super();
        t = new Token(value, 0, value.length());
        t.setPayload(new Payload(new PropertyMetaData(type).toByteArray()));
    }

    /**
     * {@inheritDoc}
     */
    public Token next() {
        try {
            return t;
        } finally {
            t = null;
        }
    }
}
