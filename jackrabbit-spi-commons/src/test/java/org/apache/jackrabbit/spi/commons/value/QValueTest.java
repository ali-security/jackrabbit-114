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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Calendar;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;

import junit.framework.TestCase;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.Path;
import org.apache.jackrabbit.spi.QValue;
import org.apache.jackrabbit.spi.QValueFactory;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.PathFactoryImpl;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.uuid.UUID;

/**
 * <code>QValueTest</code>...
 */
public class QValueTest extends TestCase {

    private final Calendar CALENDAR = Calendar.getInstance();
    private static final String REFERENCE = UUID.randomUUID().toString();
    private static final Path ROOT_PATH = PathFactoryImpl.getInstance().getRootPath();

    private static final QValueFactory factory = QValueFactoryImpl.getInstance();

    public void testIllegalType() throws RepositoryException {
        try {
            factory.create("any", 54);
            fail("54 is not a valid property type");
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    //-------------------------------------------------------------< DOUBLE >---

    public void testCreateInvalidDoubleValue() throws RepositoryException {
        try {
            factory.create("any", PropertyType.DOUBLE);
            fail("'any' cannot be converted to a valid double value.");
        } catch (ValueFormatException e) {
            // ok
        }
    }

    public void testGetDoubleOnBooleanValue() throws RepositoryException {
        try {
            QValue v = factory.create(true);
            v.getDouble();
            fail("'true' cannot be converted to a valid double value.");
        } catch (ValueFormatException e) {
            // ok
        }
    }

    //---------------------------------------------------------------< LONG >---

    public void testCreateInvalidLongValue() throws RepositoryException {
        try {
            factory.create("any", PropertyType.LONG);
            fail("'any' cannot be converted to a valid long value.");
        } catch (ValueFormatException e) {
            // ok
        }
    }

    public void testGetLongOnBooleanValue() throws RepositoryException {
        try {
            QValue v = factory.create(true);
            v.getLong();
            fail("'true' cannot be converted to a valid long value.");
        } catch (ValueFormatException e) {
            // ok
        }
    }

    //------------------------------------------------------------< BOOLEAN >---
    /**
     * QValueImpl has a final static constant for the TRUE and the FALSE boolean
     * values. Test if the various create methods use the constants (thus always
     * return the 'same' object.
     *
     * @throws RepositoryException
     */
    public void testFinalBooleanValue() throws RepositoryException {
        assertSame(factory.create(true), factory.create(Boolean.TRUE.toString(), PropertyType.BOOLEAN));
        assertSame(factory.create(true), factory.create(true));

        assertSame(factory.create(false), factory.create(Boolean.FALSE.toString(), PropertyType.BOOLEAN));
        assertSame(factory.create(false), factory.create(false));
    }

    /**
     * Test if creating Boolean QValue from boolean and from String with boolean
     * type return equal objects.
     * 
     * @throws RepositoryException
     */
    public void testCreateBooleanValueFromString() throws RepositoryException {
        QValue v = factory.create(Boolean.TRUE.toString(), PropertyType.BOOLEAN);
        assertEquals("Creating boolean type QValue from boolean or String must be equal.",
                factory.create(true), v);

        v = factory.create(Boolean.FALSE.toString(), PropertyType.BOOLEAN);
        assertEquals("Creating boolean type QValue from boolean or String must be equal.",
                factory.create(false), v);
    }

    public void testCreateTrueBooleanValue() throws RepositoryException {
        QValue v = factory.create(true);
        assertEquals("Boolean value must be true", Boolean.TRUE.toString(), v.getString());
        assertEquals("Boolean value must be true", true, v.getBoolean());
    }

    public void testCreateFalseBooleanValue() throws RepositoryException {
        QValue v = factory.create(false);
        assertEquals("Boolean value must be false", Boolean.FALSE.toString(), v.getString());
        assertEquals("Boolean value must be false", false, v.getBoolean());
    }

    public void testCreateTrueFromString() throws ValueFormatException, RepositoryException {
        QValue v = factory.create(Boolean.TRUE.toString(), PropertyType.STRING);
        assertEquals("Boolean value must be true", true, v.getBoolean());
    }

    public void testCreateFalseFromString() throws ValueFormatException, RepositoryException {
        QValue v = factory.create("any", PropertyType.STRING);
        assertEquals("Boolean value must be false", false, v.getBoolean());
    }

    public void testReadBooleanAsLong() throws RepositoryException {
        try {
            QValue v = factory.create(true);
            v.getLong();
        }
        catch (ValueFormatException e) {
            return; // ok
        }
        assertTrue("Cannot convert value to long", false);
    }

    //---------------------------------------------------------------< DATE >---
    public void testNullDateValue() throws IOException, RepositoryException {
        try {
            factory.create((Calendar) null);
            fail();
        } catch (IllegalArgumentException e) {
            // ok
        }
        try {
            factory.create(null, PropertyType.DATE);
            fail();
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    public void testDateValueType() throws RepositoryException {
        QValue v = factory.create(CALENDAR);
        assertTrue("Type of a date value must be PropertyType.DATE", v.getType() == PropertyType.DATE);
    }
    public void testDateValueEquality() throws RepositoryException {
        QValue v = factory.create(CALENDAR);
        QValue otherV = factory.create(CALENDAR);
        assertEquals("Equality of qualified date value must be calculated based on their String representation.", v, otherV);
    }

    public void testDateValueEquality2() throws RepositoryException {
        QValue v = factory.create(CALENDAR);
        QValue otherV = factory.create(v.getString(), PropertyType.DATE);
        assertEquals("Equality of qualified date value must be calculated based on their String representation.", v, otherV);
    }

    public void testDateValueStringRepresentation() throws RepositoryException {
        QValue v = factory.create(CALENDAR);
        String s = ISO8601.format(CALENDAR);
        assertEquals("Expected String representation of qualified date value to be ISO8601 compliant.", s, v.getString());
    }

    //----------------------------------------------------------< REFERENCE >---

    public void testNullReferenceValue() throws IOException, RepositoryException {
        try {
            factory.create(null, PropertyType.REFERENCE);
            fail();
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    public void testReferenceValueType() throws RepositoryException {
        QValue v = factory.create(REFERENCE, PropertyType.REFERENCE);
        assertTrue("Type of a date value must be PropertyType.REFERENCE.", v.getType() == PropertyType.REFERENCE);
    }

    public void testReferenceValueEquality() throws RepositoryException {
        QValue v = factory.create(REFERENCE, PropertyType.REFERENCE);
        QValue otherV = factory.create(REFERENCE, PropertyType.REFERENCE);
        assertEquals("Qualified ref values created from the same string must be equal.", v, otherV);
    }

    public void testEqualityDifferentTypes() throws RepositoryException {
        QValue v = factory.create(REFERENCE, PropertyType.REFERENCE);
        QValue v2 = factory.create(REFERENCE, PropertyType.STRING);
        assertFalse(v.equals(v2));
    }


    //---------------------------------------------------------------< Name >---

    public void testNullNameValue() throws IOException, RepositoryException {
        try {
            factory.create((Name) null);
            fail();
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    public void testNameValueType() throws IOException, RepositoryException {
        QValue v = factory.create(NameConstants.JCR_DATA);
        assertTrue(v.getType() == PropertyType.NAME);
        v = factory.create(NameConstants.JCR_DATA.toString(), PropertyType.NAME);
        assertTrue(v.getType() == PropertyType.NAME);
    }

    public void testNameValueEquality() throws IOException, RepositoryException {
        QValue v = factory.create(NameConstants.JCR_DATA);
        QValue v2 = factory.create(NameConstants.JCR_DATA.toString(), PropertyType.NAME);
        assertTrue(v.equals(v2));
    }

    public void testNameValueGetString() throws IOException, RepositoryException {
        QValue v = factory.create(NameConstants.JCR_DATA);
        assertTrue(v.getString().equals(NameConstants.JCR_DATA.toString()));
    }

    public void testNameValueGetName() throws RepositoryException {
        QValue v = factory.create(NameConstants.JCR_DATA);
        assertTrue(v.getName().equals(NameConstants.JCR_DATA));
    }

    public void testInvalidNameValue() throws RepositoryException {
        try {
            factory.create("abc", PropertyType.NAME);
            fail("'abc' is not a valid Name -> creating QValue should fail.");
        } catch (ValueFormatException e) {
            // ok
        }
    }

    public void testAnyValueGetName() throws RepositoryException {
        try {
            factory.create(12345).getName();
            fail("12345 is not a valid Name value -> QValue.getName() should fail.");
        } catch (ValueFormatException e) {
            // ok
        }
    }

    //---------------------------------------------------------------< Path >---

    public void testNullPathValue() throws IOException, RepositoryException {
        try {
            factory.create((Path) null);
            fail();
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    public void testPathValueType() throws IOException, RepositoryException {
        QValue v = factory.create(ROOT_PATH);
        assertTrue(v.getType() == PropertyType.PATH);
        v = factory.create(ROOT_PATH.toString(), PropertyType.PATH);
        assertTrue(v.getType() == PropertyType.PATH);
    }


    public void testPathValueEquality() throws IOException, RepositoryException {
        QValue v = factory.create(ROOT_PATH);
        QValue v2 = factory.create(ROOT_PATH.toString(), PropertyType.PATH);
        assertTrue(v.equals(v2));
    }

    public void testPathValueGetString() throws IOException, RepositoryException {
        QValue v = factory.create(ROOT_PATH);
        assertTrue(v.getString().equals(ROOT_PATH.toString()));
    }

    public void testPathValueGetPath() throws RepositoryException {
        QValue v = factory.create(ROOT_PATH);
        assertTrue(v.getPath().equals(ROOT_PATH));
    }

    public void testInvalidPathValue() throws RepositoryException {
        try {
            factory.create("abc", PropertyType.PATH);
            fail("'abc' is not a valid Path -> creating QValue should fail.");
        } catch (ValueFormatException e) {
            // ok
        }
    }

    public void testAnyValueGetPath() throws RepositoryException {
        try {
            factory.create(12345).getPath();
            fail("12345 is not a valid Path value -> QValue.getPath() should fail.");
        } catch (ValueFormatException e) {
            // ok
        }
    }

    //-------------------------------------------------------------< BINARY >---

    public void testNullBinaryValue() throws IOException, RepositoryException {
        try {
            factory.create((byte[]) null);
            fail();
        } catch (IllegalArgumentException e) {
            // ok
        }
        try {
            factory.create((InputStream) null);
            fail();
        } catch (IllegalArgumentException e) {
            // ok
        }
        try {
            factory.create((File) null);
            fail();
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    public void testBinaryValueType() throws IOException, RepositoryException {
        QValue v = factory.create(new byte[] {'a', 'b', 'c'});
        assertTrue(v.getType() == PropertyType.BINARY);
    }

    public void testBinarySerializable() throws Exception {
        runBinarySerializableTest(1); // 1k
        runBinarySerializableTest(10); // 10k
        runBinarySerializableTest(100); // 100k
        runBinarySerializableTest(1000); // 1M
    }

    /**
     * Runs binary serializable test using a stream with a size of kBytes.
     * @param size in kBytes.
     */
    private void runBinarySerializableTest(int size) throws Exception {
        File tmp = File.createTempFile("test", "bin");
        OutputStream out = new FileOutputStream(tmp);
        byte[] stuff = new byte[1024];
        Arrays.fill(stuff, (byte) 7);
        for (int i = 0; i < size; i++) {
            out.write(stuff);
        }
        out.close();
        InputStream in = new FileInputStream(tmp);
        QValue v = factory.create(in);
        in.close();
        tmp.delete();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(bout);
        oout.writeObject(v);
        oout.close();
        ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
        ObjectInputStream oin = new ObjectInputStream(bin);
        QValue serValue = (QValue) oin.readObject();
        try {
            InputStream in1 = new BufferedInputStream(v.getStream());
            InputStream in2 = new BufferedInputStream(serValue.getStream());
            int i;
            while ((i = in1.read()) > -1) {
                assertEquals(i, in2.read());
            }
            assertEquals(in2.read(), -1);
            in1.close();
            in2.close();
        } finally {
            v.discard();
            serValue.discard();
        }
    }
}
