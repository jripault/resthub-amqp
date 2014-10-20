package org.resthub.rpc.serializer;

import java.io.Serializable;

/**
 * User: jripault
 * Date: 20/10/2014
 */

public class RPCCallMessage implements Serializable {
    public String methodName;
    public Object[] arguments;
    public Class[] argumentsClasses;

    public RPCCallMessage(String methodName, Object[] arguments, Class[] argumentsClasses) {
        this.methodName = methodName;
        this.arguments = arguments;
        this.argumentsClasses = argumentsClasses;
    }
}
