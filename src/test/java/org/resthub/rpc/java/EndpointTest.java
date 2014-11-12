/**
 * Copyright 2010 Emmanuel Bourg
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
package org.resthub.rpc.java;

import org.resthub.rpc.AMQPProxyFactory;
import org.resthub.rpc.Endpoint;
import org.resthub.rpc.serializer.java.DefaultObjectOutputStreamFactory;
import org.resthub.rpc.serializer.java.DefaultSerializationHandler;
import org.resthub.rpc.service.EchoService;
import org.resthub.rpc.service.EchoServiceEndpoint;
import org.resthub.rpc.service.EchoServiceImpl;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;


public class EndpointTest extends AMQPProxyTest
{
    protected void startEndpoint()
    {
        Endpoint endpoint = new Endpoint(new EchoServiceImpl());
        endpoint.setConnectionFactory(connectionFactory);
        endpoint.run();
    }

    private void startEndpointWithPrefix()
    {
        Endpoint endpoint = new Endpoint();
        endpoint.setServiceAPI(EchoService.class);
        endpoint.setServiceImpl(new EchoServiceEndpoint());
        endpoint.setQueuePrefix("foo");
        endpoint.setConnectionFactory(connectionFactory);
        DefaultSerializationHandler serializationHandler = new DefaultSerializationHandler();
        serializationHandler.setObjectOutputStreamFactory(new DefaultObjectOutputStreamFactory());
        endpoint.setSerializationHandler(serializationHandler);
        endpoint.run();
    }
    
    @Test(groups = "java-serialization")
    public void testQueuePrefix() throws Exception
    {
        startEndpointWithPrefix();
        
        AMQPProxyFactory factory = new AMQPProxyFactory();
        DefaultSerializationHandler serializationHandler = new DefaultSerializationHandler();
        serializationHandler.setObjectOutputStreamFactory(new DefaultObjectOutputStreamFactory());
        factory.setSerializationHandler(serializationHandler);
        factory.setReadTimeout(5000);
        factory.setQueuePrefix("foo");
        factory.setConnectionFactory(connectionFactory);
        EchoService service = factory.create(EchoService.class);
        String message = "Hello Hessian!";
        
        assertEquals(message, service.echo(message));
    }
}
