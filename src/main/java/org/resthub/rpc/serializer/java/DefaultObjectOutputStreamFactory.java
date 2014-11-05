package org.resthub.rpc.serializer.java;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * User: jripault
 * Date: 05/11/2014
 * Default factory for ObjectOutputStream
 */
public class DefaultObjectOutputStreamFactory implements ObjectOutputStreamFactory {
    @Override
    public ObjectOutputStream getObjectOutputStream(OutputStream out) throws IOException {
        return new ObjectOutputStream(out);
    }
}
