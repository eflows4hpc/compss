package integratedtoolkit.gat.worker.utils;

import java.lang.reflect.Method;

import integratedtoolkit.util.ErrorManager;

import integratedtoolkit.types.annotations.parameter.Stream;

import integratedtoolkit.worker.invokers.GenericInvoker;
import integratedtoolkit.worker.invokers.InvokeExecutionException;


public class Invokers {

    private static final String ERROR_INVOKE = "Error invoking requested method";


    public static Object invokeJavaMethod(String className, String methodName, Object target, Class<?>[] types, Object[] values) {
        // Use reflection to get the requested method
        Method method = null;
        try {
            Class<?> methodClass = Class.forName(className);
            method = methodClass.getMethod(methodName, types);
        } catch (ClassNotFoundException e) {
            ErrorManager.error("Application class not found");
        } catch (SecurityException e) {
            ErrorManager.error("Security exception");
        } catch (NoSuchMethodException e) {
            ErrorManager.error("Requested method not found");
        }

        // Invoke the requested method
        Object retValue = null;
        try {
            retValue = method.invoke(target, values);
        } catch (Exception e) {
            ErrorManager.error(ERROR_INVOKE, e);
        }

        return retValue;
    }

    public static Object invokeMPIMethod(String mpiRunner, String mpiBinary, Object target, Object[] values, boolean hasReturn,
            Stream[] streams, String[] prefixes) {

        Object retValue = null;
        try {
            retValue = GenericInvoker.invokeMPIMethod(mpiRunner, mpiBinary, values, hasReturn, streams, prefixes);
        } catch (InvokeExecutionException iee) {
            ErrorManager.error(ERROR_INVOKE, iee);
        }

        return retValue;
    }

    public static Object invokeOmpSsMethod(String ompssBinary, Object target, Object[] values, boolean hasReturn, Stream[] streams,
            String[] prefixes) {
        Object retValue = null;
        try {
            retValue = GenericInvoker.invokeOmpSsMethod(ompssBinary, values, hasReturn, streams, prefixes);
        } catch (InvokeExecutionException iee) {
            ErrorManager.error(ERROR_INVOKE, iee);
        }

        return retValue;
    }

    public static Object invokeOpenCLMethod(String kernel, Object target, Object[] values, boolean hasReturn, Stream[] streams,
            String[] prefixes) {
        ErrorManager.error("ERROR: OpenCL is not supported");

        return null;
    }

    public static Object invokeBinaryMethod(String binary, Object target, Object[] values, boolean hasReturn, Stream[] streams,
            String[] prefixes) {
        Object retValue = null;
        try {
            retValue = GenericInvoker.invokeBinaryMethod(binary, values, hasReturn, streams, prefixes);
        } catch (InvokeExecutionException iee) {
            ErrorManager.error(ERROR_INVOKE, iee);
        }

        return retValue;
    }

}
