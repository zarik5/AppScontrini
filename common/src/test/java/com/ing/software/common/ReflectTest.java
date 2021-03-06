package com.ing.software.common;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.ing.software.common.Reflect.*;
import static org.junit.Assert.*;

/**
 * @author Riccardo Zaglia
 */
public class ReflectTest {

    //invoke

    @Test
    @SuppressWarnings("UnnecessaryBoxing")
    public void testInvokeWithInstance() throws Exception {
        int sum = invoke(new TestClass(), "testPrivateWithParams", new Integer(10), 20.0f);
        assertEquals(30, sum);
    }

    @Test
    public void testInvokeWithClassType() throws Exception {
        TestClass r = invoke(TestClass.class, "testProtectedNoParamsReturnObject");
        assertNotEquals(null, r);
    }

    @Test
    public void testInvokeReturnVoid() throws Exception {
        TestClass tc = new TestClass();
        invoke(tc, "testPublicReturnVoid");
        assertEquals(1, tc.intField);
    }

    @Test
    public void testInvokeNullParam() throws Exception {
        int r = invoke(new TestClass(), "testPrivateWithParams", 0, null);
        assertEquals(-1, r);
    }


    @Test
    public void testInvokeOverload() throws Exception {
        int diff = invoke(new TestClass(), "testPrivateWithParams", 15, 5.0);
        assertEquals(10, diff);
    }


    // getField

    @Test
    public void testGetFieldPrimitive() throws Exception {
        assertEquals(1, (int) getField(new TestClass(), "intField"));
    }

    @Test
    public void testGetFieldObj() throws Exception {
        assertNotEquals(null, getField(TestClass.class, "staticObj"));
    }

    @Test
    public void testGetFieldNull() throws Exception {
        assertEquals(null, (Object) getField(new TestClass(), "nullObj"));
    }


    // setField

    @Test
    public void testSetFieldPrimitive() throws Exception {
        TestClass tc = new TestClass();
        setField(tc, "intField", 2);
        assertEquals(2, tc.intField);
    }

    //These tests are not exhaustive but invoke and getField will be used enough in other tests.
}

class TestClass {

    int intField = 1;
    protected static TestClass staticObj = new TestClass();
    private final TestClass nullObj = null;

    // sum
    private int testPrivateWithParams(int a, Float b) {
        if (b != null)
            return a + b.intValue();
        else
            return -1;
    }

    // difference (overload)
    private int testPrivateWithParams(int a, Double b) {
        if (b != null)
            return a - b.intValue();
        else
            return -1;
    }

    protected static TestClass testProtectedNoParamsReturnObject() {
        return new TestClass();
    }

    public void testPublicReturnVoid() {
        intField = 1;
    }
}