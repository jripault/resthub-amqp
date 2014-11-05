package org.resthub.rpc.serializer.hessian;

import com.caucho.hessian.io.*;
import com.caucho.hessian.server.HessianSkeleton;
import org.resthub.rpc.serializer.SerializationHandler;

import java.io.*;
import java.lang.reflect.Method;
import java.util.zip.DeflaterOutputStream;

/**
 * User: JRI <julien.ripault@atos.net>
 * Date: 05/11/2014
 */
public class HessianSerializationHandler implements SerializationHandler {

    private SerializerFactory _serializerFactory;

    public SerializerFactory getSerializerFactory() {
        if (_serializerFactory == null) {
            _serializerFactory = new SerializerFactory();
        }

        return _serializerFactory;
    }

    @Override
    public void createResponse(Object serviceImpl, Class<?> serviceAPI, InputStream in, OutputStream out) throws Throwable {
        HessianSkeleton skeleton = new HessianSkeleton(serviceImpl, serviceAPI);
        skeleton.invoke(in, out, getSerializerFactory());
    }

    @Override
    public void handleError(Throwable cause, ByteArrayInputStream is, ByteArrayOutputStream os) throws IOException {
        AbstractHessianOutput out = createHessianOutput(new HessianInputFactory().readHeader(is), os);

        out.writeFault(cause.getClass().getSimpleName(), cause.getMessage(), cause);
        out.close();
    }

    @Override
    public Object readObject(Class<?> returnType, InputStream is) throws Throwable {
            int code = is.read();
            AbstractHessianInput in;
            if (code == 'H')
            {
                int major = is.read();
                int minor = is.read();

                in = getHessian2Input(is);

                return in.readReply(returnType);
            }
            else if (code == 'r')
            {
                int major = is.read();
                int minor = is.read();

                in = getHessian2Input(is);

                in.startReplyBody();

                Object value = in.readObject(returnType);

                in.completeReply();

                return value;
            }
            else
            {
                throw new HessianProtocolException("'" + (char) code + "' is an unknown code");
            }
    }

    @Override
    public void writeMethodCall(Method method, Object[] args, OutputStream os) throws IOException {
        String methodName = method.getName();

        AbstractHessianOutput out = getHessianOutput(os);

        out.call(methodName, args);
        if (os instanceof DeflaterOutputStream) {
            ((DeflaterOutputStream) os).finish();
        }
        out.flush();
    }

    private AbstractHessianOutput createHessianOutput(HessianInputFactory.HeaderType header, OutputStream os) {
        AbstractHessianOutput out;

        HessianFactory hessianfactory = new HessianFactory();
        switch (header) {
            case CALL_1_REPLY_1:
                out = hessianfactory.createHessianOutput(os);
                break;

            case CALL_1_REPLY_2:
            case HESSIAN_2:
                out = hessianfactory.createHessian2Output(os);
                break;

            default:
                throw new IllegalStateException(header + " is an unknown Hessian call");
        }

        return out;
    }

    AbstractHessianInput getHessian2Input(InputStream is) {
        AbstractHessianInput in;
        in = new Hessian2Input(is);
        in.setRemoteResolver(null);
        in.setSerializerFactory(getSerializerFactory());
        return in;
    }

    AbstractHessianOutput getHessianOutput(OutputStream os) {
        AbstractHessianOutput out = new Hessian2Output(os);
        out.setSerializerFactory(getSerializerFactory());
        return out;
    }
}
