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
package org.apache.jackrabbit.jcr2spi.query;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RangeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;

import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.QueryInfo;
import org.apache.jackrabbit.spi.QueryResultRow;
import org.apache.jackrabbit.spi.QValue;
import org.apache.jackrabbit.spi.commons.value.ValueFormat;

/**
 * Implements the {@link javax.jcr.query.RowIterator} interface returned by
 * a {@link javax.jcr.query.QueryResult}.
 */
class RowIteratorImpl implements RowIterator {

    /**
     * The result rows from the SPI implementation.
     */
    private final RangeIterator rows;

    /**
     * The column names.
     */
    private final Name[] columnNames;

    /**
     * The <code>NamePathResolver</code> of the user <code>Session</code>.
     */
    private final org.apache.jackrabbit.spi.commons.conversion.NamePathResolver resolver;

    /**
     * The JCR value factory.
     */
    private final ValueFactory vFactory;

    /**
     * Creates a new <code>RowIteratorImpl</code> that iterates over the result
     * nodes.
     *
     * @param queryInfo the query info.
     * @param resolver  <code>NameResolver</code> of the user
     *                  <code>Session</code>.
     * @param vFactory  the JCR value factory.
     */
    RowIteratorImpl(QueryInfo queryInfo, NamePathResolver resolver, ValueFactory vFactory) {
        this.rows = queryInfo.getRows();
        this.columnNames = queryInfo.getColumnNames();
        this.resolver = resolver;
        this.vFactory = vFactory;
    }

    //--------------------------------------------------------< RowIterator >---
    /**
     * Returns the next <code>Row</code> in the iteration.
     *
     * @return the next <code>Row</code> in the iteration.
     * @throws NoSuchElementException if iteration has no more <code>Row</code>s.
     * @see RowIterator#nextRow()
     */
    public Row nextRow() throws NoSuchElementException {
        return new RowImpl((QueryResultRow) rows.next());
    }

    //------------------------------------------------------< RangeIterator >---
    /**
     * Skip a number of <code>Row</code>s in this iterator.
     *
     * @param skipNum the non-negative number of <code>Row</code>s to skip
     * @throws NoSuchElementException if skipped past the last <code>Row</code>
     * in this iterator.
     * @see javax.jcr.RangeIterator#skip(long)
     */
    public void skip(long skipNum) throws NoSuchElementException {
        rows.skip(skipNum);
    }

    /**
     * Returns the number of <code>Row</code>s in this iterator.
     *
     * @return the number of <code>Row</code>s in this iterator.
     * @see RangeIterator#getSize()
     */
    public long getSize() {
        return rows.getSize();
    }

    /**
     * Returns the current position within this iterator. The number
     * returned is the 0-based index of the next <code>Row</code> in the iterator,
     * i.e. the one that will be returned on the subsequent <code>next</code> call.
     * <p/>
     * Note that this method does not check if there is a next element,
     * i.e. an empty iterator will always return 0.
     *
     * @return the current position withing this iterator.
     * @see RangeIterator#getPosition()
     */
    public long getPosition() {
        return rows.getPosition();
    }

    /**
     * @throws UnsupportedOperationException always.
     * @see Iterator#remove()
     */
    public void remove() {
        throw new UnsupportedOperationException("remove");
    }

    /**
     * Returns <code>true</code> if the iteration has more <code>Row</code>s.
     * (In other words, returns <code>true</code> if <code>next</code> would
     * return an <code>Row</code> rather than throwing an exception.)
     *
     * @return <code>true</code> if the iterator has more elements.
     * @see Iterator#hasNext()
     */
    public boolean hasNext() {
        return rows.hasNext();
    }

    /**
     * Returns the next <code>Row</code> in the iteration.
     *
     * @return the next <code>Row</code> in the iteration.
     * @throws NoSuchElementException if iteration has no more <code>Row</code>s.
     * @see Iterator#next()
     */
    public Object next() throws NoSuchElementException {
        return nextRow();
    }

    //---------------------< inner class RowImpl >------------------------------
    /**
     * Implements the {@link javax.jcr.query.Row} interface, which represents
     * a row in the query result.
     */
    class RowImpl implements Row {

        /**
         * The underlying <code>QueryResultRow</code>.
         */
        private final QueryResultRow row;

        /**
         * Cached value array for returned by {@link #getValues()}.
         */
        private Value[] values;

        /**
         * Map of select property <code>Name</code>s. Key: Name, Value:
         * Integer, which refers to the array index in {@link #values}.
         */
        private Map propertyMap;

        /**
         * Creates a new <code>RowImpl</code> instance based on a SPI result
         * row.
         *
         * @param row the underlying query result row
         */
        private RowImpl(QueryResultRow row) {
            this.row = row;
        }

        //------------------------------------------------------------< Row >---
        /**
         * Returns an array of all the values in the same order as the property
         * names (column names) returned by
         * {@link javax.jcr.query.QueryResult#getColumnNames()}.
         *
         * @return a <code>Value</code> array.
         * @throws RepositoryException if an error occurs while retrieving the
         * values from the <code>Node</code>.
         * @see Row#getValues()
         */
        public Value[] getValues() throws RepositoryException {
            if (values == null) {
                QValue[] qVals = row.getValues();
                Value[] tmp = new Value[qVals.length];
                for (int i = 0; i < qVals.length; i++) {
                    if (qVals[i] == null) {
                        tmp[i] = null;
                    } else {
                        tmp[i] = ValueFormat.getJCRValue(
                                qVals[i], resolver, vFactory);
                    }
                }
                values = tmp;
            }
            // return a copy of the array
            Value[] ret = new Value[values.length];
            System.arraycopy(values, 0, ret, 0, values.length);
            return ret;
        }

        /**
         * Returns the value of the indicated  property in this <code>Row</code>.
         * <p/>
         * If <code>propertyName</code> is not among the column names of the
         * query result table, an <code>ItemNotFoundException</code> is thrown.
         *
         * @return a <code>Value</code>
         * @throws ItemNotFoundException if <code>propertyName</code> is not
         *                               among the column names of the query result table.
         * @throws RepositoryException   if <code>propertyName</code> is not a
         *                               valid property name.
         * @see Row#getValue(String)
         */
        public Value getValue(String propertyName) throws ItemNotFoundException, RepositoryException {
            if (propertyMap == null) {
                // create the map first
                Map tmp = new HashMap();
                for (int i = 0; i < columnNames.length; i++) {
                    tmp.put(columnNames[i], new Integer(i));
                }
                propertyMap = tmp;
            }
            try {
                Name prop = resolver.getQName(propertyName);
                Integer idx = (Integer) propertyMap.get(prop);
                if (idx == null) {
                    throw new ItemNotFoundException(propertyName);
                }
                // make sure values are there
                if (values == null) {
                    getValues();
                }
                return values[idx.intValue()];
            } catch (org.apache.jackrabbit.spi.commons.conversion.NameException e) {
                throw new RepositoryException(e.getMessage(), e);
            }
        }
    }
}
