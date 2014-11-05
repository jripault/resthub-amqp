package org.resthub.rpc.serializer.java.hibernate;

import org.resthub.rpc.serializer.java.ObjectOutputStreamFactory;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * User: jripault
 * Date: 05/11/2014
 */
public class HibernateObjectOutputStreamFactory implements ObjectOutputStreamFactory {
    @Override
    public ObjectOutputStream getObjectOutputStream(OutputStream out) throws IOException {
        return new HibernateSupportObjectOutputStream(out);
    }
}
