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
package org.apache.jackrabbit.core.data.db;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;

import org.apache.jackrabbit.core.data.DataIdentifier;
import org.apache.jackrabbit.core.data.DataStoreException;
import org.apache.jackrabbit.core.persistence.bundle.util.ConnectionRecoveryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents an input stream backed by a database. It allows the
 * stream to be used by keeping the database objects open until the stream is
 * closed. When the stream is finished or closed, then the database objects are
 * freed.
 */
public class DbInputStream extends InputStream {

    private static Logger log = LoggerFactory.getLogger(DbInputStream.class);

    protected DbDataStore store;
    protected DataIdentifier identifier;
    protected boolean endOfStream;
    protected InputStream in;
    
    protected ConnectionRecoveryManager conn;
    protected ResultSet rs;
    

    /**
     * Create a database input stream for the given identifier.
     * Database access is delayed until the first byte is read from the stream.
     * 
     * @param store the database data store
     * @param identifier the data identifier
     */
    protected DbInputStream(DbDataStore store, DataIdentifier identifier) {
        this.store = store;
        this.identifier = identifier;
    }

    /**
     * Open the stream if required.
     * 
     * @throws IOException
     */
    protected void openStream() throws IOException {
        if (endOfStream) {
            throw new EOFException();
        }
        if (in == null) {
            try {
                in = store.openStream(this, identifier);
            } catch (DataStoreException e) {
                IOException e2 = new IOException(e.getMessage());
                e2.initCause(e);
                throw e2;
            }
        }
    }

    /**
     * {@inheritDoc}
     * When the stream is consumed, the database objects held by the instance are closed.
     */
    public int read() throws IOException {
        if (endOfStream) {
            return -1;
        }
        openStream();
        int c = in.read();
        if (c == -1) {
            endOfStream = true;
            close();
        }
        return c;
    }

    /**
     * {@inheritDoc}
     * When the stream is consumed, the database objects held by the instance are closed.
     */
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * {@inheritDoc}
     * When the stream is consumed, the database objects held by the instance are closed.
     */
    public int read(byte[] b, int off, int len) throws IOException {
        if (endOfStream) {
            return -1;
        }
        openStream();
        int c = in.read(b, off, len);
        if (c == -1) {
            endOfStream = true;
            close();
        }
        return c;
    }

    /**
     * {@inheritDoc}
     * When the stream is consumed, the database objects held by the instance are closed.
     */
    public void close() throws IOException {
        if (in != null) {
            in.close();
            in = null;
            // some additional database objects 
            // may need to be closed
            if (rs != null) {
                DatabaseHelper.closeSilently(rs);
                rs = null;
            }
            if (conn != null) {
                try {
                    store.putBack(conn);
                } catch (DataStoreException e) {
                    log.info("Error closing DbResource", e);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public long skip(long n) throws IOException {
        if (endOfStream) {
            return -1;
        }        
        openStream();
        return in.skip(n);
    }

    /**
     * {@inheritDoc}
     */
    public int available() throws IOException {
        if (endOfStream) {
            return 0;
        }        
        openStream();
        return in.available();
    }

    /**
     * {@inheritDoc}
     */
    public void mark(int readlimit) {
        if (endOfStream) {
            return;
        } 
        try {
            openStream();
        } catch (IOException e) {
            log.info("Error getting underlying stream: ", e);
        }
        in.mark(readlimit);
    }

    /**
     * {@inheritDoc}
     */
    public void reset() throws IOException {
        if (endOfStream) {
            throw new EOFException();
        }         
        openStream();
        in.reset();
    }

    /**
     * {@inheritDoc}
     */
    public boolean markSupported() {
        if (endOfStream) {
            return false;
        }      
        try {
            openStream();
        } catch (IOException e) {
            log.info("Error getting underlying stream: ", e);
            return false;
        }
        return in.markSupported();
    }

    /**
     * Set the database connection of this input stream. This object must be
     * closed once the stream is closed.
     * 
     * @param conn the connection
     */
    void setConnection(ConnectionRecoveryManager conn) {
        this.conn = conn;
    }

    /**
     * Set the result set of this input stream. This object must be closed once
     * the stream is closed.
     * 
     * @param rs the result set
     */
    void setResultSet(ResultSet rs) {
        this.rs = rs;
    }

}
