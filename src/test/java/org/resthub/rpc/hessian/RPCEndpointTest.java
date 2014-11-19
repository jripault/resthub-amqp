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
package org.resthub.rpc.hessian;

import org.resthub.rpc.AMQPProxyFactory;
import org.resthub.rpc.RPCEndpoint;
import org.resthub.rpc.serializer.hessian.HessianSerializationHandler;
import org.resthub.rpc.service.EchoService;
import org.resthub.rpc.service.EchoServiceRPCEndpoint;
import org.resthub.rpc.service.EchoServiceImpl;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;


public class RPCEndpointTest extends AMQPProxyTest
{
    protected void startEndpoint()
    {
        RPCEndpoint RPCEndpoint = new RPCEndpoint(new EchoServiceImpl());
        RPCEndpoint.setConnectionFactory(connectionFactory);
        RPCEndpoint.run();
    }

    private void startEndpointWithPrefix()
    {
        RPCEndpoint RPCEndpoint = new RPCEndpoint();
        RPCEndpoint.setServiceAPI(EchoService.class);
        RPCEndpoint.setServiceImpl(new EchoServiceRPCEndpoint());
        RPCEndpoint.setQueuePrefix("foo");
        RPCEndpoint.setConnectionFactory(connectionFactory);
        RPCEndpoint.setSerializationHandler(new HessianSerializationHandler());
        RPCEndpoint.run();
    }
    
    @Test(groups = "hessian-serialization")
    public void testQueuePrefix() throws Exception
    {
        startEndpointWithPrefix();
        
        AMQPProxyFactory factory = new AMQPProxyFactory();
        factory.setSerializationHandler(new HessianSerializationHandler());
        factory.setReadTimeout(5000);
        factory.setQueuePrefix("foo");
        factory.setConnectionFactory(connectionFactory);
        EchoService service = factory.create(EchoService.class);
        String message = "Hello Hessian!";
        
        assertEquals(message, service.echo(message));
    }
}
