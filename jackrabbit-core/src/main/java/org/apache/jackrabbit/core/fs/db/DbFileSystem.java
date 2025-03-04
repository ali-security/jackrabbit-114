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
package org.apache.jackrabbit.core.fs.db;

import org.apache.jackrabbit.core.persistence.bundle.util.ConnectionFactory;

import java.sql.Connection;
import java.sql.SQLException;

import javax.jcr.RepositoryException;

/**
 * <code>DbFileSystem</code> is a generic JDBC-based <code>FileSystem</code>
 * implementation for Jackrabbit that persists file system entries in a
 * database table.
 * <p/>
 * It is configured through the following properties:
 * <ul>
 * <li><code>driver</code>: the FQN name of the JDBC driver class</li>
 * <li><code>url</code>: the database url of the form <code>jdbc:subprotocol:subname</code></li>
 * <li><code>user</code>: the database user</li>
 * <li><code>password</code>: the user's password</li>
 * <li><code>schema</code>: type of schema to be used
 * (e.g. <code>mysql</code>, <code>mssql</code>, etc.); </li>
 * <li><code>schemaObjectPrefix</code>: prefix to be prepended to schema objects</li>
 * </ul>
 * The required schema objects are automatically created by executing the DDL
 * statements read from the [schema].ddl file. The .ddl file is read from the
 * resources by calling <code>getClass().getResourceAsStream(schema + ".ddl")</code>.
 * Every line in the specified .ddl file is executed separatly by calling
 * <code>java.sql.Statement.execute(String)</code> where every occurence of the
 * the string <code>"${schemaObjectPrefix}"</code> has been replaced with the
 * value of the property <code>schemaObjectPrefix</code>.
 * <p/>
 * The following is a fragment from a sample configuration using MySQL:
 * <pre>
 *   &lt;FileSystem class="org.apache.jackrabbit.core.fs.db.DbFileSystem"&gt;
 *       &lt;param name="driver" value="com.mysql.jdbc.Driver"/&gt;
 *       &lt;param name="url" value="jdbc:mysql:///test?autoReconnect=true"/&gt;
 *       &lt;param name="schema" value="mysql"/&gt;
 *       &lt;param name="schemaObjectPrefix" value="rep_"/&gt;
 *   &lt;/FileSystem&gt;
 * </pre>
 * The following is a fragment from a sample configuration using Daffodil One$DB Embedded:
 * <pre>
 *   &lt;FileSystem class="org.apache.jackrabbit.core.fs.db.DbFileSystem"&gt;
 *       &lt;param name="driver" value="in.co.daffodil.db.jdbc.DaffodilDBDriver"/&gt;
 *       &lt;param name="url" value="jdbc:daffodilDB_embedded:rep;path=${rep.home}/databases;create=true"/&gt;
 *       &lt;param name="user" value="daffodil"/&gt;
 *       &lt;param name="password" value="daffodil"/&gt;
 *       &lt;param name="schema" value="daffodil"/&gt;
 *       &lt;param name="schemaObjectPrefix" value="rep_"/&gt;
 *   &lt;/FileSystem&gt;
 * </pre>
 * The following is a fragment from a sample configuration using MSSQL:
 * <pre>
 *   &lt;FileSystem class="org.apache.jackrabbit.core.fs.db.DbFileSystem"&gt;
 *       &lt;param name="driver" value="com.microsoft.jdbc.sqlserver.SQLServerDriver"/&gt;
 *       &lt;param name="url" value="jdbc:microsoft:sqlserver://localhost:1433;;DatabaseName=test;SelectMethod=Cursor;"/&gt;
 *       &lt;param name="schema" value="mssql"/&gt;
 *       &lt;param name="user" value="sa"/&gt;
 *       &lt;param name="password" value=""/&gt;
 *       &lt;param name="schemaObjectPrefix" value="rep_"/&gt;
 *   &lt;/FileSystem&gt;
 * </pre>
 * The following is a fragment from a sample configuration using PostgreSQL:
 * <pre>
 *   &lt;FileSystem class="org.apache.jackrabbit.core.fs.db.DbFileSystem"&gt;
 *       &lt;param name="driver" value="org.postgresql.Driver"/&gt;
 *       &lt;param name="url" value="jdbc:postgresql://localhost/test"/&gt;
 *       &lt;param name="schema" value="postgresql"/&gt;
 *       &lt;param name="user" value="postgres"/&gt;
 *       &lt;param name="password" value="postgres"/&gt;
 *       &lt;param name="schemaObjectPrefix" value="rep_"/&gt;
 *   &lt;/FileSystem&gt;
 * </pre>
 * JNDI can be used to get the connection. In this case, use the javax.naming.InitialContext as the driver,
 * and the JNDI name as the URL. If the user and password are configured in the JNDI resource,
 * they should not be configured here. Example JNDI settings:
 * <pre>
 * &lt;param name="driver" value="javax.naming.InitialContext" />
 * &lt;param name="url" value="java:comp/env/jdbc/Test" />
 * </pre>
 * See also {@link DerbyFileSystem}, {@link DB2FileSystem}, {@link OracleFileSystem}.
 */
public class DbFileSystem extends DatabaseFileSystem {

    /**
     * the full qualified JDBC driver name
     */
    protected String driver;

    /**
     * the JDBC connection URL
     */
    protected String url;

    /**
     * the JDBC connection user
     */
    protected String user;

    /**
     * the JDBC connection password
     */
    protected String password;

    //----------------------------------------------------< setters & getters >
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }


    //-------------------------------------------< java.lang.Object overrides >
    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof DbFileSystem) {
            DbFileSystem other = (DbFileSystem) obj;
            if (((driver != null) ? driver.equals(other.driver) : other.driver == null)
                    && ((url != null) ? url.equals(other.url) : other.url == null)
                    && ((user != null) ? user.equals(other.user) : other.user == null)
                    && ((password != null) ? password.equals(other.password) : other.password == null)
                    && super.equals(other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns zero to satisfy the Object equals/hashCode contract.
     * This class is mutable and not meant to be used as a hash key.
     *
     * @return always zero
     * @see Object#hashCode()
     */
    public int hashCode() {
        return 0;
    }

    //--------------------------------------------------< DatabaseFileSystem >

    /**
     * Initialize the JDBC connection.
     *
     * @throws SQLException if an error occurs
     */
    protected Connection getConnection() throws RepositoryException, SQLException {
        return ConnectionFactory.getConnection(driver, url, user, password);
    }

}
