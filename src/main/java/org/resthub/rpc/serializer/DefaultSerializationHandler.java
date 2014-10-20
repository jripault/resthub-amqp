package org.resthub.rpc.serializer;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Default serializer using Java serialization
 * User: jripault
 * Date: 20/10/2014
 */
public class DefaultSerializationHandler implements SerializationHandler {
    @Override
    public void createResponse(Object serviceImpl, Class<?> serviceAPI, InputStream in, OutputStream out) throws Throwable {
        ObjectInputStream ois = new ObjectInputStream(in);
        ObjectOutputStream oos = new ObjectOutputStream(out);
        RPCCallMessage message = (RPCCallMessage) ois.readObject();
        Method method = serviceImpl.getClass().getMethod( message.methodName, message.argumentsClasses);
        try {
            Object result = method.invoke(serviceImpl, message.arguments);
            oos.writeObject(result);
        }catch(InvocationTargetException ex){
            throw ex.getCause();
        }catch(NotSerializableException nsex){
            throw new IllegalStateException(nsex.getMessage() + " must implement java.io.Serializable");
        } finally {
            oos.close();
        }

    }

    @Override
    public void handleError(Throwable cause, ByteArrayOutputStream os) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(os);
        oos.writeObject(new RPCErrorMessage(cause.getClass().getSimpleName(), cause.getMessage(), cause));
        oos.close();
    }


    @Override
    public Object readObject(Class<?> returnType, InputStream is) throws Throwable {
        ObjectInputStream objectInputStream = new ObjectInputStream(is);
        Object result = objectInputStream.readObject();
        if (result != null && result.getClass() == RPCErrorMessage.class) {
            RPCErrorMessage error = (RPCErrorMessage) result;
            if("IllegalStateException".equals(error.className)){
                throw error.cause;
            }
            throw new Exception(error.message, error.cause);
        }
        return result;
    }

    @Override
    public void writeMethodCall(Method method, Object[] args, OutputStream payload) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(payload);
        oos.writeObject(new RPCCallMessage(method.getName(), args, method.getParameterTypes()));
        oos.close();
    }
}
