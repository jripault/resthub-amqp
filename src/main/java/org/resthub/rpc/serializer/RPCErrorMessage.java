package org.resthub.rpc.serializer;

import java.io.Serializable;

/**
 * User: jripault
 * Date: 20/10/2014
 */

public class RPCErrorMessage implements Serializable {
    public String className;
    public String message;
    public Throwable cause;

    public RPCErrorMessage(String className, String message, Throwable cause) {
        this.className = className;
        this.message = message;
        this.cause = cause;
    }
}
