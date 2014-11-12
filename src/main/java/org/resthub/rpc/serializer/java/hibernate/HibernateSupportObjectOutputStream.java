package org.resthub.rpc.serializer.java.hibernate;

import org.hibernate.Hibernate;
import org.hibernate.collection.internal.PersistentBag;
import org.hibernate.collection.internal.PersistentList;
import org.hibernate.collection.internal.PersistentMap;
import org.hibernate.collection.internal.PersistentSet;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * User: jripault
 * Date: 05/11/2014
 */
public class HibernateSupportObjectOutputStream extends ObjectOutputStream {
    public HibernateSupportObjectOutputStream(OutputStream out) throws IOException {
        super(out);
        enableReplaceObject(true);
    }

    protected HibernateSupportObjectOutputStream() throws IOException, SecurityException {
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Object replaceObject(Object obj) throws IOException {
        if (PersistentMap.class.isAssignableFrom(obj.getClass())) {
            if (Hibernate.isInitialized(obj)) {
                return new HashMap((Map) obj);
            } else {
                return new HashMap();
            }
        } else if (PersistentSet.class.isAssignableFrom(obj.getClass())) {
            if (Hibernate.isInitialized(obj)) {
                return new HashSet((Collection) obj);
            } else {
                return new HashSet();
            }
        }else if (PersistentList.class.isAssignableFrom(obj.getClass()) ||
            PersistentBag.class.isAssignableFrom(obj.getClass())) {
            if (Hibernate.isInitialized(obj)) {
                return new ArrayList((Collection) obj);
            } else {
                return new ArrayList();
            }
        }
        return super.replaceObject(obj);
    }
}
