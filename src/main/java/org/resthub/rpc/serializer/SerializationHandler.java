package org.resthub.rpc.serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * User: jripault
 * Date: 20/10/2014
 */
public interface SerializationHandler {
    /**
     * Endpoint side : read the request, invoke method and write the response
     * @param serviceImpl Implementation to use
     * @param serviceAPI Interface called
     * @param in InputStream
     * @param out OutputStream
     */
    void createResponse(Object serviceImpl, Class<?> serviceAPI, InputStream in, OutputStream out) throws Throwable;

    /**
     * Endpoint side : Handle error during invocation
     * @param cause error thrown during invocation
     * @param os outputStream to write in
     */
    void handleError(Throwable cause, ByteArrayOutputStream os) throws IOException;

    /**
     * Client side : Read method's response
     * @param returnType
     * @param is InputStream
     * @return Object from response
     * @throws IOException
     * @throws ClassNotFoundException
     */
    Object readObject(Class<?> returnType, InputStream is) throws Throwable;

    /**
     * Client side : Prepare message containing the method to call and arguments
     * @param method
     * @param args Arguments to pass to the method
     * @param payload OutputStream
     */
    void writeMethodCall(Method method, Object[] args, OutputStream payload) throws IOException;
}
