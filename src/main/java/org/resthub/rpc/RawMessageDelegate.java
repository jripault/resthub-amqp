/**
 * Copyright 2012 resthub.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.resthub.rpc;

import org.resthub.rpc.serializer.SerializationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Requests processing
 * @author Antoine Neveu
 *
 */
public class RawMessageDelegate {
    
    private static final Logger logger = LoggerFactory.getLogger(RawMessageDelegate.class);

    private static String SPRING_CORRELATION_ID = "spring_reply_correlation";

    private Class<?> serviceAPI;
    private Object serviceImpl;
    private SerializationHandler serializationHandler;
    
    public RawMessageDelegate(){
        
    }
    
    public RawMessageDelegate(Class<?> serviceAPI, Object serviceImpl, SerializationHandler serializationHandler){
        this.serviceAPI = serviceAPI;
        this.serviceImpl = serviceImpl;
        this.serializationHandler = serializationHandler;
    }
    
    /**
     * Specifies the interface of the service.
     */
    public void setServiceAPI(Class<?> serviceAPI)
    {
        this.serviceAPI = serviceAPI;
    }

    /**
     * Specifies the object implementing the service.
     */
    public void setServiceImpl(Object serviceImpl)
    {
        this.serviceImpl = serviceImpl;
        
    }
    
    /**
     * Message processing
     * @param message
     * @return
     */
    public Message handleMessage(Message message){
        logger.debug("Message received : " + message);
        
        MessageProperties props = message.getMessageProperties();
        boolean compressed = "deflate".equals(props.getContentEncoding());
        
        byte[] response;
        try
        {
            response = createResponseBody(message.getBody(), compressed);
        }
        catch (Throwable e)
        {
            logger.error("Exception occurs during method call", e);
            compressed = false;
            response = createFaultBody(message.getBody(), e);
        }
        
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType("x-application/hessian");
        // Spring correlation ID
        messageProperties.setHeader(SPRING_CORRELATION_ID,
                message.getMessageProperties().getHeaders().get(SPRING_CORRELATION_ID));
        if (compressed)
        {
            messageProperties.setContentEncoding("deflate");
        }
        return new Message(response, messageProperties);
    }
    
    /**
     * Execute a request.
     */
    private byte[] createResponseBody(byte[] request, boolean compressed) throws Throwable
    {
        InputStream in = new ByteArrayInputStream(request);
        if (compressed)
        {
            in = new InflaterInputStream(new ByteArrayInputStream(request), new Inflater(true));
        }

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        OutputStream out;
        if (compressed)
        {
            Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
            out = new DeflaterOutputStream(bout, deflater);
        }
        else
        {
            out = bout;
        }

        // let the serializer take care of reading the request and writing the response
        serializationHandler.createResponse(serviceImpl, serviceAPI, in, out);

        if (out instanceof DeflaterOutputStream)
        {
            ((DeflaterOutputStream) out).finish();
        }
        out.flush();
        out.close();

        return bout.toByteArray();
    }

    private byte[] createFaultBody(byte[] request, Throwable cause)
    {
        try
        {
            ByteArrayInputStream is = new ByteArrayInputStream(request);
            ByteArrayOutputStream os = new ByteArrayOutputStream();

            // let the serializer manage errors
            serializationHandler.handleError(cause, is, os);

            return os.toByteArray();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

}
