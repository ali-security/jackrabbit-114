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
package org.apache.jackrabbit.spi.commons.name;

import org.apache.jackrabbit.spi.NameFactory;
import org.apache.jackrabbit.spi.Name;

/**
 * <code>NameFactoryImpl</code>...
 */
public class NameFactoryImpl implements NameFactory {

    private static final NameFactory INSTANCE = new NameFactoryImpl();

    /**
     * Cache of flyweight name instances.
     *
     * @see https://issues.apache.org/jira/browse/JCR-1663
     */
    private final HashCache cache = new HashCache();

    private NameFactoryImpl() {};

    public static NameFactory getInstance() {
        return INSTANCE;
    }

    //--------------------------------------------------------< NameFactory >---
    /**
     * @see NameFactory#create(String, String)
     */
    public Name create(String namespaceURI, String localName) throws IllegalArgumentException {
        // NOTE: an empty localName and/or URI is valid (e.g. the root node name)
        if (namespaceURI == null) {
            throw new IllegalArgumentException("invalid namespaceURI specified");
        }
        if (localName == null) {
            throw new IllegalArgumentException("invalid localName specified");
        }
        return (Name) cache.get(new NameImpl(namespaceURI, localName));
    }

    /**
     * @see NameFactory#create(String)
     */
    public Name create(String nameString) throws IllegalArgumentException {
        if (nameString == null || "".equals(nameString)) {
            throw new IllegalArgumentException("Invalid Name literal.");
        }
        if (nameString.charAt(0) != '{') {
            throw new IllegalArgumentException("Invalid Name literal");
        }
        int i = nameString.indexOf('}');
        if (i == -1) {
            throw new IllegalArgumentException("Invalid Name literal");
        }
        if (i == nameString.length() - 1) {
            throw new IllegalArgumentException("Invalid Name literal");
        }
        return (Name) cache.get(new NameImpl(
                nameString.substring(1, i), nameString.substring(i + 1)));
    }

    //--------------------------------------------------------< inner class >---
    /**
     * Inner class implementing the <code>Name</code> interface.
     */
    private static class NameImpl implements Name {

        /** The memorized hash code of this qualified name. */
        private transient int hash;

        /** The memorized string representation of this qualified name. */
        private transient String string;

        /** The internalized namespace URI of this qualified name. */
        private final String namespaceURI;

        /** The local part of this qualified name. */
        private final String localName;

        private NameImpl(String namespaceURI, String localName) {
            // internalize namespaceURI to improve performance of comparisons.
            this.namespaceURI = namespaceURI.intern();
            // localName is not internalized in order not to risk huge perm
            // space for large repositories
            this.localName = localName;
            hash = 0;
        }

        //-----------------------------------------------------------< Name >---
        /**
         * @see Name#getLocalName()
         */
        public String getLocalName() {
            return localName;
        }

        /**
         * @see Name#getNamespaceURI()
         */
        public String getNamespaceURI() {
            return namespaceURI;
        }

        //---------------------------------------------------------< Object >---
        /**
         * Returns the string representation of this <code>Name</code> in the
         * following format:
         * <p/>
         * <code><b>{</b>namespaceURI<b>}</b>localName</code>
         *
         * @return the string representation of this <code>Name</code>.
         * @see NameFactory#create(String)
         * @see Object#toString()
         */
        public String toString() {
            // Name is immutable, we can store the string representation
            if (string == null) {
                string = '{' + namespaceURI + '}' + localName;
            }
            return string;
        }

        /**
         * Compares two qualified names for equality. Returns <code>true</code>
         * if the given object is a qualified name and has the same namespace URI
         * and local part as this qualified name.
         *
         * @param obj the object to compare this qualified name with
         * @return <code>true</code> if the object is equal to this qualified name,
         *         <code>false</code> otherwise
         * @see Object#equals(Object)
         */
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof NameImpl) {
                NameImpl other = (NameImpl) obj;
                // we can use == operator for namespaceURI since it is internalized
                return namespaceURI == other.namespaceURI && localName.equals(other.localName);
            }
            // some other Name implementation
            if (obj instanceof Name) {
                Name other = (Name) obj;
                return namespaceURI.equals(other.getNamespaceURI()) && localName.equals(other.getLocalName());
            }
            return false;
        }

        /**
         * Returns the hash code of this qualified name. The hash code is
         * computed from the namespace URI and local part of the qualified
         * name and memorized for better performance.
         *
         * @return hash code
         * @see Object#hashCode()
         */
        public int hashCode() {
            // Name is immutable, we can store the computed hash code value
            int h = hash;
            if (h == 0) {
                h = 17;
                h = 37 * h + namespaceURI.hashCode();
                h = 37 * h + localName.hashCode();
                hash = h;
            }
            return h;
        }

        //------------------------------------------------------< Cloneable >---
        /**
         * Creates a clone of this qualified name.
         * Overriden in order to make <code>clone()</code> public.
         *
         * @return a clone of this instance
         * @throws CloneNotSupportedException never thrown
         * @see Object#clone()
         */
        public Object clone() throws CloneNotSupportedException {
            // Name is immutable, no special handling required
            return super.clone();
        }

        //-----------------------------------------------------< Comparable >---
        /**
         * Compares two qualified names.
         *
         * @param o the object to compare this qualified name with
         * @return comparison result
         * @throws ClassCastException if the given object is not a qualified name
         * @see Comparable#compareTo(Object)
         */
        public int compareTo(Object o) {
            if (this == o) {
                return 0;
            }
            Name other = (Name) o;
            if (namespaceURI.equals(other.getNamespaceURI())) {
                return localName.compareTo(other.getLocalName());
            } else {
                return namespaceURI.compareTo(other.getNamespaceURI());
            }
        }

        //---------------------------------------------------< Serializable >---
        /**
         * Creates a new <code>Name</code> instance using the proper constructor
         * during deserialization in order to make sure that internalized strings
         * are used where appropriate.
         */
        private Object readResolve() {
            return new NameImpl(namespaceURI, localName);
        }
    }
}