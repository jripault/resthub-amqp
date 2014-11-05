package org.resthub.rpc.serializer.java;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * User: jripault
 * Date: 05/11/2014
 * Factory to manage creation of ObjectOutputStream
 */
public interface ObjectOutputStreamFactory {
      ObjectOutputStream getObjectOutputStream(OutputStream out) throws IOException;
}
