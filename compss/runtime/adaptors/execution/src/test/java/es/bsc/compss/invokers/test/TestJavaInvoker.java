/*
 *  Copyright 2002-2019 Barcelona Supercomputing Center (www.bsc.es)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package es.bsc.compss.invokers.test;

import es.bsc.compss.types.execution.exceptions.InvalidMapException;
import es.bsc.compss.types.execution.exceptions.JobExecutionException;
import es.bsc.compss.invokers.Invoker;
import es.bsc.compss.invokers.JavaInvoker;
import es.bsc.compss.invokers.test.objects.TestObject;
import es.bsc.compss.invokers.test.utils.ExecutionFlowVerifier;
import es.bsc.compss.invokers.test.utils.FakeInvocation;
import es.bsc.compss.invokers.test.utils.FakeInvocationContext;
import es.bsc.compss.invokers.test.utils.FakeInvocationParam;
import es.bsc.compss.invokers.test.utils.types.Event;
import es.bsc.compss.types.annotations.parameter.DataType;
import es.bsc.compss.types.annotations.parameter.Stream;
import es.bsc.compss.types.execution.Invocation;
import es.bsc.compss.types.execution.InvocationParam;
import es.bsc.compss.types.implementations.MethodImplementation;
import es.bsc.compss.types.resources.MethodResourceDescription;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import static org.junit.Assert.*;
import org.junit.Test;


public class TestJavaInvoker extends TestObject {

    public TestJavaInvoker() {
        super(0);
    }


    private static final String EXISTING_CLASS = "es.bsc.compss.invokers.test.TestJavaInvoker";
    private static final String NON_EXISTING_CLASS = "es.bsc.compss.invokers.test.NonExistentTestJavaInvoker";

    private static final String TEST_NONEXISTENT_METHODNAME = "nonExistent";
    private static final String TEST_EMPTY_METHODNAME = "testEmpty";
    private static final String TEST_READS_METHODNAME = "testReads";
    private static final String TEST_INOUT_METHODNAME = "testInouts";
    private static final String TEST_TARGET_IN_METHODNAME = "testTargetIn";
    private static final String TEST_TARGET_INOUT_METHODNAME = "testTargetInout";

    private static final String TEST_RESULT_METHODNAME = "testResult";

    private static final MethodImplementation TEST_EMPTY;
    private static final MethodImplementation TEST_READS;
    private static final MethodImplementation TEST_INOUT;
    private static final MethodImplementation TEST_TARGET_IN;
    private static final MethodImplementation TEST_TARGET_INOUT;
    private static final MethodImplementation TEST_RESULT;
    private static final MethodImplementation TEST_NONEXISTENT_CLASS;
    private static final MethodImplementation TEST_NONEXISTENT_METHOD;

    static {
        TEST_EMPTY = new MethodImplementation(EXISTING_CLASS, TEST_EMPTY_METHODNAME, 0, 0,
                new MethodResourceDescription());
        TEST_READS = new MethodImplementation(EXISTING_CLASS, TEST_READS_METHODNAME, 0, 0,
                new MethodResourceDescription());
        TEST_INOUT = new MethodImplementation(EXISTING_CLASS, TEST_INOUT_METHODNAME, 0, 0,
                new MethodResourceDescription());
        TEST_TARGET_IN = new MethodImplementation(EXISTING_CLASS, TEST_TARGET_IN_METHODNAME, 0, 0,
                new MethodResourceDescription());
        TEST_TARGET_INOUT = new MethodImplementation(EXISTING_CLASS, TEST_TARGET_INOUT_METHODNAME, 0, 0,
                new MethodResourceDescription());
        TEST_RESULT = new MethodImplementation(EXISTING_CLASS, TEST_RESULT_METHODNAME, 0, 0,
                new MethodResourceDescription());

        TEST_NONEXISTENT_CLASS = new MethodImplementation(NON_EXISTING_CLASS, TEST_NONEXISTENT_METHODNAME, 0, 0,
                new MethodResourceDescription());
        TEST_NONEXISTENT_METHOD = new MethodImplementation(EXISTING_CLASS, TEST_NONEXISTENT_METHODNAME, 0, 0,
                new MethodResourceDescription());
    }

    private static HashMap<Long, ExecutionReport> executions = new HashMap<>();
    private final ExecutionFlowVerifier expectedEvents = new ExecutionFlowVerifier();


    private static File createTempDirectory() throws IOException {
        final File temp;

        temp = File.createTempFile("temp", Long.toString(System.nanoTime()));
        if (!(temp.delete())) {
            throw new IOException("Could not delete temp file: " + temp.getAbsolutePath());
        }
        if (!(temp.mkdir())) {
            throw new IOException("Could not create temp directory: " + temp.getAbsolutePath());
        }
        return (temp);
    }

    private static boolean deleteSandbox(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (null != files) {
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isDirectory()) {
                        deleteSandbox(files[i]);
                    } else {
                        files[i].delete();
                    }
                }
            }
        }
        return (directory.delete());
    }

    @Test
    public void nonExistentClassTest() throws InvalidMapException, IOException, JobExecutionException {

        File sandBoxDir = createTempDirectory();

        FakeInvocation.Builder invBr = new FakeInvocation.Builder();
        invBr = invBr.setImpl(TEST_NONEXISTENT_CLASS);
        Invocation invocation = invBr.build();

        FakeInvocationContext.Builder ctxBdr = new FakeInvocationContext.Builder();
        ctxBdr = ctxBdr.setListener(expectedEvents);
        FakeInvocationContext context = ctxBdr.build();
        try {
            new JavaInvoker(context, invocation, sandBoxDir, null);
        } catch (JobExecutionException jee) {
            if (jee.getMessage().compareTo(JavaInvoker.ERROR_CLASS_REFLECTION) != 0) {
                fail("Test should fail because class could not be found. Obtained error is " + jee.getMessage());
            }
        }
        deleteSandbox(sandBoxDir);
    }

    @Test
    public void nonExistentMethodTest() throws InvalidMapException, IOException, JobExecutionException {

        File sandBoxDir = createTempDirectory();

        FakeInvocation.Builder invBr = new FakeInvocation.Builder();
        invBr = invBr.setImpl(TEST_NONEXISTENT_METHOD);
        Invocation invocation = invBr.build();

        FakeInvocationContext.Builder ctxBdr = new FakeInvocationContext.Builder();
        ctxBdr = ctxBdr.setListener(expectedEvents);
        FakeInvocationContext context = ctxBdr.build();
        try {
            new JavaInvoker(context, invocation, sandBoxDir, null);
        } catch (JobExecutionException jee) {
            if (jee.getMessage().compareTo(JavaInvoker.ERROR_METHOD_REFLECTION) != 0) {
                fail("Test should fail because method could not be found. Obtained error is " + jee.getMessage());
            }
        }
        deleteSandbox(sandBoxDir);
    }

    @Test
    public void parameterMissmatch() throws InvalidMapException, IOException, JobExecutionException {
        File sandBoxDir = createTempDirectory();

        FakeInvocation.Builder invBr = new FakeInvocation.Builder();
        invBr = invBr.setImpl(TEST_EMPTY);
        LinkedList<InvocationParam> params = new LinkedList<>();
        String renaming1 = "d5v3_754478989756456.IT";
        Object value1 = new TestObject(3);
        InvocationParam p1 = new FakeInvocationParam(DataType.OBJECT_T, "", "none", Stream.UNSPECIFIED, "", renaming1,
                false);
        params.add(p1);
        expectedEvents.add(Event.Type.GET_OBJECT, renaming1, value1);
        String renaming2 = "d6v3_754478989756456.IT";
        Object value2 = new TestObject(2);
        InvocationParam p2 = new FakeInvocationParam(DataType.OBJECT_T, "", "none", Stream.UNSPECIFIED, "", renaming2,
                false);
        params.add(p2);
        expectedEvents.add(Event.Type.GET_OBJECT, renaming2, value2);
        invBr = invBr.setParams(params);
        Invocation invocation = invBr.build();

        FakeInvocationContext.Builder ctxBdr = new FakeInvocationContext.Builder();
        ctxBdr = ctxBdr.setListener(expectedEvents);
        FakeInvocationContext context = ctxBdr.build();
        try {
            new JavaInvoker(context, invocation, sandBoxDir, null);
        } catch (JobExecutionException jee) {
            if (jee.getMessage().compareTo(JavaInvoker.ERROR_METHOD_REFLECTION) != 0) {
                fail("Test should fail because method could not be found. Obtained error is " + jee.getMessage());
            }
        }
        deleteSandbox(sandBoxDir);
    }

    @Test
    public void emptyTest() throws InvalidMapException, IOException, JobExecutionException {
        long executorId = Thread.currentThread().getId();
        File sandBoxDir = createTempDirectory();

        FakeInvocation.Builder invBr = new FakeInvocation.Builder();
        invBr = invBr.setImpl(TEST_EMPTY);
        Invocation invocation = invBr.build();

        FakeInvocationContext.Builder ctxBdr = new FakeInvocationContext.Builder();
        ctxBdr = ctxBdr.setListener(expectedEvents);
        FakeInvocationContext context = ctxBdr.build();

        ExecutionReport result = new ExecutionReport(TEST_EMPTY_METHODNAME, false, new Object[0], null, null);
        executions.put(executorId, result);

        Invoker invoker = new JavaInvoker(context, invocation, sandBoxDir, null);
        invoker.processTask();

        ExecutionReport report = executions.remove(executorId);
        report.checkReport(TEST_EMPTY_METHODNAME, true, new Object[] {}, null, null);
        deleteSandbox(sandBoxDir);

    }

    public static void testEmpty() {
        long executorId = Thread.currentThread().getId();
        ExecutionReport report = executions.remove(executorId);
        report.checkReport(TEST_EMPTY_METHODNAME, false, new Object[0], null, null);

        // DO WHATEVER THE METHOD DOES
        ExecutionReport result = new ExecutionReport(TEST_EMPTY_METHODNAME, true, new Object[0], null, null);
        executions.put(executorId, result);
    }

    @Test
    public void readsTest() throws InvalidMapException, IOException, JobExecutionException {
        long executorId = Thread.currentThread().getId();
        File sandBoxDir = createTempDirectory();

        FakeInvocation.Builder invBr = new FakeInvocation.Builder();
        invBr = invBr.setImpl(TEST_READS);
        LinkedList<InvocationParam> params = new LinkedList<>();
        String renaming1 = "d5v3_754478989756456.IT";
        Object value1 = new TestObject(3);
        InvocationParam p1 = new FakeInvocationParam(DataType.OBJECT_T, "", "none", Stream.UNSPECIFIED, "", renaming1,
                false);
        params.add(p1);
        expectedEvents.add(Event.Type.GET_OBJECT, renaming1, value1);
        String renaming2 = "d6v3_754478989756456.IT";
        Object value2 = new TestObject(2);
        InvocationParam p2 = new FakeInvocationParam(DataType.OBJECT_T, "", "none", Stream.UNSPECIFIED, "", renaming2,
                false);
        params.add(p2);
        expectedEvents.add(Event.Type.GET_OBJECT, renaming2, value2);
        invBr = invBr.setParams(params);
        Invocation invocation = invBr.build();

        FakeInvocationContext.Builder ctxBdr = new FakeInvocationContext.Builder();
        ctxBdr = ctxBdr.setListener(expectedEvents);
        FakeInvocationContext context = ctxBdr.build();

        Invoker invoker = new JavaInvoker(context, invocation, sandBoxDir, null);

        ExecutionReport result = new ExecutionReport(TEST_READS_METHODNAME, false, new Object[] { value1, value2 },
                null, null);
        executions.put(executorId, result);
        invoker.processTask();

        ExecutionReport report = executions.remove(executorId);
        report.checkReport(TEST_READS_METHODNAME, true, new Object[] { value1, value2 }, null, null);
        deleteSandbox(sandBoxDir);
    }

    public static void testReads(TestObject a, TestObject b) {
        long executorId = Thread.currentThread().getId();
        ExecutionReport report = executions.remove(executorId);
        report.checkReport(TEST_READS_METHODNAME, false, new Object[] { a, b }, null, null);

        // DO WHATEVER THE METHOD DOES
        ExecutionReport result = new ExecutionReport(TEST_READS_METHODNAME, true, new Object[] { a, b }, null, null);
        executions.put(executorId, result);
    }

    @Test
    public void inoutsTest() throws InvalidMapException, IOException, JobExecutionException {
        long executorId = Thread.currentThread().getId();
        File sandBoxDir = createTempDirectory();

        FakeInvocation.Builder invBr = new FakeInvocation.Builder();
        invBr = invBr.setImpl(TEST_INOUT);
        LinkedList<InvocationParam> params = new LinkedList<>();
        String renaming1 = "d5v3_754478989756456.IT";
        Object value1 = new TestObject(3);
        InvocationParam p1 = new FakeInvocationParam(DataType.OBJECT_T, "", "none", Stream.UNSPECIFIED, "", renaming1,
                true);
        params.add(p1);
        expectedEvents.add(Event.Type.GET_OBJECT, renaming1, value1);
        String renaming2 = "d6v3_754478989756456.IT";
        Object value2 = new TestObject(2);
        InvocationParam p2 = new FakeInvocationParam(DataType.OBJECT_T, "", "none", Stream.UNSPECIFIED, "", renaming2,
                true);
        params.add(p2);
        expectedEvents.add(Event.Type.GET_OBJECT, renaming2, value2);
        invBr = invBr.setParams(params);
        Invocation invocation = invBr.build();

        FakeInvocationContext.Builder ctxBdr = new FakeInvocationContext.Builder();
        ctxBdr = ctxBdr.setListener(expectedEvents);
        FakeInvocationContext context = ctxBdr.build();

        Invoker invoker = new JavaInvoker(context, invocation, sandBoxDir, null);

        ExecutionReport result = new ExecutionReport(TEST_INOUT_METHODNAME, false, new Object[] { value1, value2 },
                null, null);
        executions.put(executorId, result);
        TestObject out1 = new TestObject(4);
        TestObject out2 = new TestObject(4);
        expectedEvents.add(Event.Type.STORE_OBJECT, renaming1, out1.getValue());
        expectedEvents.add(Event.Type.STORE_OBJECT, renaming2, out2.getValue());
        invoker.processTask();

        ExecutionReport report = executions.remove(executorId);
        report.checkReport(TEST_INOUT_METHODNAME, true, new Object[] { out1, out2 }, null, null);
        deleteSandbox(sandBoxDir);
    }

    public static void testInouts(TestObject a, TestObject b) {
        long executorId = Thread.currentThread().getId();
        ExecutionReport report = executions.remove(executorId);
        report.checkReport(TEST_INOUT_METHODNAME, false, new Object[] { a, b }, null, null);
        a.updateValue(a.getValue() + 1);
        b.updateValue(b.getValue() + 2);
        // DO WHATEVER THE METHOD DOES
        ExecutionReport result = new ExecutionReport(TEST_INOUT_METHODNAME, true, new Object[] { a, b }, null, null);
        executions.put(executorId, result);
    }

    @Test
    public void nullTargetTest() throws InvalidMapException, IOException, JobExecutionException {
        File sandBoxDir = createTempDirectory();

        FakeInvocation.Builder invBr = new FakeInvocation.Builder();
        invBr = invBr.setImpl(TEST_TARGET_IN);
        Invocation invocation = invBr.build();

        FakeInvocationContext.Builder ctxBdr = new FakeInvocationContext.Builder();
        ctxBdr = ctxBdr.setListener(expectedEvents);
        FakeInvocationContext context = ctxBdr.build();

        Invoker invoker = new JavaInvoker(context, invocation, sandBoxDir, null);
        try {
            invoker.processTask();
        } catch (NullPointerException npe) {
            // Executing a instance method on null -> Raise Exception
        }
        deleteSandbox(sandBoxDir);
    }

    @Test
    public void targetInTest() throws InvalidMapException, IOException, JobExecutionException {

        long executorId = Thread.currentThread().getId();
        File sandBoxDir = createTempDirectory();

        FakeInvocation.Builder invBr = new FakeInvocation.Builder();
        invBr = invBr.setImpl(TEST_TARGET_IN);
        LinkedList<InvocationParam> params = new LinkedList<>();
        invBr = invBr.setParams(params);
        String renaming = "d5v3_754478989756456.IT";
        TestObject target = new TestJavaInvoker();
        InvocationParam p = new FakeInvocationParam(DataType.OBJECT_T, "", "none", Stream.UNSPECIFIED, "", renaming,
                false);
        invBr = invBr.setTarget(p);
        expectedEvents.add(Event.Type.GET_OBJECT, renaming, target);
        Invocation invocation = invBr.build();

        FakeInvocationContext.Builder ctxBdr = new FakeInvocationContext.Builder();
        ctxBdr = ctxBdr.setListener(expectedEvents);
        FakeInvocationContext context = ctxBdr.build();

        Invoker invoker = new JavaInvoker(context, invocation, sandBoxDir, null);

        ExecutionReport result = new ExecutionReport(TEST_TARGET_IN_METHODNAME, false, new Object[] {}, target, null);
        executions.put(executorId, result);

        invoker.processTask();

        ExecutionReport report = executions.remove(executorId);
        report.checkReport(TEST_TARGET_IN_METHODNAME, true, new Object[] {}, target, null);
        deleteSandbox(sandBoxDir);

    }

    public void testTargetIn() {
        long executorId = Thread.currentThread().getId();
        ExecutionReport report = executions.remove(executorId);
        report.checkReport(TEST_TARGET_IN_METHODNAME, false, new Object[] {}, this, null);

        // DO WHATEVER THE METHOD DOES
        ExecutionReport result = new ExecutionReport(TEST_TARGET_IN_METHODNAME, true, new Object[] {}, this, null);
        executions.put(executorId, result);
    }

    @Test
    public void targetInoutTest() throws InvalidMapException, IOException, JobExecutionException {

        long executorId = Thread.currentThread().getId();
        File sandBoxDir = createTempDirectory();

        FakeInvocation.Builder invBr = new FakeInvocation.Builder();
        invBr = invBr.setImpl(TEST_TARGET_INOUT);
        LinkedList<InvocationParam> params = new LinkedList<>();
        invBr = invBr.setParams(params);
        String renaming = "d5v3_754478989756456.IT";
        TestObject target = new TestJavaInvoker();
        target.updateValue(4);
        InvocationParam p = new FakeInvocationParam(DataType.OBJECT_T, "", "none", Stream.UNSPECIFIED, "", renaming,
                true);
        invBr = invBr.setTarget(p);
        expectedEvents.add(Event.Type.GET_OBJECT, renaming, target);
        Invocation invocation = invBr.build();

        FakeInvocationContext.Builder ctxBdr = new FakeInvocationContext.Builder();
        ctxBdr = ctxBdr.setListener(expectedEvents);
        FakeInvocationContext context = ctxBdr.build();

        Invoker invoker = new JavaInvoker(context, invocation, sandBoxDir, null);

        ExecutionReport result = new ExecutionReport(TEST_TARGET_INOUT_METHODNAME, false, new Object[] {}, target,
                null);
        executions.put(executorId, result);

        expectedEvents.add(Event.Type.STORE_OBJECT, renaming, 5);
        invoker.processTask();
        ExecutionReport report = executions.remove(executorId);
        report.checkReport(TEST_TARGET_INOUT_METHODNAME, true, new Object[] {}, target, null);
        deleteSandbox(sandBoxDir);
    }

    public void testTargetInout() {
        long executorId = Thread.currentThread().getId();
        ExecutionReport report = executions.remove(executorId);
        report.checkReport(TEST_TARGET_INOUT_METHODNAME, false, new Object[] {}, this, null);
        this.updateValue(this.getValue() + 1);
        // DO WHATEVER THE METHOD DOES
        ExecutionReport result = new ExecutionReport(TEST_TARGET_INOUT_METHODNAME, true, new Object[] {}, this, null);
        executions.put(executorId, result);
    }

    @Test
    public void resultTest() throws InvalidMapException, IOException, JobExecutionException {
        long executorId = Thread.currentThread().getId();
        File sandBoxDir = createTempDirectory();

        FakeInvocation.Builder invBr = new FakeInvocation.Builder();
        invBr = invBr.setImpl(TEST_RESULT);
        LinkedList<InvocationParam> params = new LinkedList<>();
        invBr = invBr.setParams(params);
        String renaming = "d5v3_754478989756456.IT";
        InvocationParam p = new FakeInvocationParam(DataType.OBJECT_T, "", "none", Stream.UNSPECIFIED, "", renaming,
                true);
        LinkedList<InvocationParam> results = new LinkedList<>();
        results.add(p);
        invBr = invBr.setResult(results);
        Invocation invocation = invBr.build();

        FakeInvocationContext.Builder ctxBdr = new FakeInvocationContext.Builder();
        ctxBdr = ctxBdr.setListener(expectedEvents);
        FakeInvocationContext context = ctxBdr.build();

        Invoker invoker = new JavaInvoker(context, invocation, sandBoxDir, null);

        ExecutionReport result = new ExecutionReport(TEST_RESULT_METHODNAME, false, new Object[] {}, null,
                new TestObject(5));
        executions.put(executorId, result);

        expectedEvents.add(Event.Type.STORE_OBJECT, renaming, 5);
        invoker.processTask();

        ExecutionReport report = executions.remove(executorId);
        report.checkReport(TEST_RESULT_METHODNAME, true, new Object[] {}, null, null);
        deleteSandbox(sandBoxDir);
    }

    public static TestObject testResult() {
        long executorId = Thread.currentThread().getId();
        ExecutionReport report = executions.remove(executorId);
        report.checkReport(TEST_RESULT_METHODNAME, false, new Object[] {}, null, null);

        TestObject returnValue = new TestObject(5);
        ExecutionReport result = new ExecutionReport(TEST_RESULT_METHODNAME, true, new Object[] {}, null, returnValue);
        executions.put(executorId, result);
        return returnValue;
    }


    private static class ExecutionReport {

        private final String method;
        private final boolean executed;
        private final Object[] arguments;
        private final Object target;
        private final Object result;


        public ExecutionReport(String method, boolean executed, Object[] arguments, Object target, Object result) {
            this.method = method;
            this.executed = executed;
            this.arguments = arguments;
            this.target = target;
            this.result = result;
        }

        public void checkReport(String method, boolean executed, Object[] arguments, Object target, Object result) {

            if (this.method.compareTo(method) != 0) {
                fail("Wrong method execution. Expecting" + method + " instead of " + this.method);
            }

            assertEquals(
                    "Unexpected execution state."
                            + (executed ? "Obtained " + executed + " and " + this.executed + " expected."
                                    : "Obtained " + this.executed + " and " + executed + " expected."),
                    this.executed, executed);

            assertEquals(
                    "The number of read does not match the expected. " + (executed
                            ? "Obtained " + arguments.length + " and " + this.arguments.length + " expected."
                            : "Obtained " + this.arguments.length + " and " + arguments.length + " expected."),
                    this.arguments.length, arguments.length);

            for (int argIdx = 0; argIdx < arguments.length; argIdx++) {
                assertEquals("Unexpected value for parameter " + argIdx + " on " + method + "."
                        + (executed ? "Obtained " + arguments[argIdx] + " and " + this.arguments[argIdx] + " expected."
                                : "Obtained " + this.arguments[argIdx] + " and " + arguments[argIdx] + " expected."),
                        this.arguments[argIdx], arguments[argIdx]);
            }

            if (this.target == null && target == null) {
                assertEquals(
                        "Unexpected target value. "
                                + (executed ? "Obtained " + target + " and " + this.target + " expected."
                                        : "Obtained " + this.target + " and " + target + " expected."),
                        this.target, target);
            }

            if (this.result == null && result == null) {
                assertEquals(
                        "Unexpected result value. "
                                + (executed ? "Obtained " + result + " and " + this.result + " expected."
                                        : "Obtained " + this.result + " and " + result + " expected."),
                        this.result, result);
            }
        }
    }
}
