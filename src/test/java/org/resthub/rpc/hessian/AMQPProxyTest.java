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
import org.resthub.rpc.serializer.hessian.HessianSerializationHandler;
import org.resthub.rpc.service.EchoService;
import org.resthub.rpc.service.EchoServiceRPCEndpoint;
import org.resthub.rpc.service.FailingService;
import org.resthub.rpc.service.FailingServiceRPCEndpoint;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.TimeoutException;

import static org.testng.AssertJUnit.*;


public class AMQPProxyTest
{
    protected final String HOSTNAME = "localhost";

    protected CachingConnectionFactory connectionFactory;

    private HessianSerializationHandler serializationHandler = new HessianSerializationHandler();

    @BeforeClass(groups = "hessian-serialization")
    protected void setUp() throws Exception
    {
        connectionFactory = new CachingConnectionFactory("localhost", 5672);
        connectionFactory.setUsername("guest");
        connectionFactory.setPassword("guest");

    }

    @AfterClass(groups = "hessian-serialization")
    protected void tearDown() throws Exception
    {
        connectionFactory.destroy();
    }

    @Test(groups = "hessian-serialization")
    public void testEcho() throws Exception
    {


        EchoServiceRPCEndpoint endpoint = new EchoServiceRPCEndpoint();
        endpoint.setSerializationHandler(serializationHandler);
        endpoint.setConnectionFactory(connectionFactory);
        endpoint.run();
        
        AMQPProxyFactory factory = new AMQPProxyFactory();
        factory.setReadTimeout(5000);
        factory.setConnectionFactory(connectionFactory);
        factory.setSerializationHandler(serializationHandler);
        EchoService service = factory.create(EchoService.class);
        String message = "Hello Hessian!";

        assertEquals(message, service.echo(message));
        assertEquals(message, service.echo(message));
        
        endpoint.destroy();
    }

    @Test(groups = "hessian-serialization")
    public void testException() throws Exception
    {
        EchoServiceRPCEndpoint endpoint = new EchoServiceRPCEndpoint();
        endpoint.setConnectionFactory(connectionFactory);
        endpoint.setSerializationHandler(serializationHandler);
        endpoint.run();
        
        AMQPProxyFactory factory = new AMQPProxyFactory();
        factory.setReadTimeout(5000);
        factory.setCompressed(true);
        factory.setConnectionFactory(connectionFactory);
        factory.setSerializationHandler(new HessianSerializationHandler());
        EchoService service = factory.create(EchoService.class);
        String message = "Hello Hessian!";

        try
        {
            service.exception(message);
            fail("No exception thrown");
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            assertEquals("Exception message", message, e.getMessage());
        }
        finally {
            endpoint.destroy();
        }
    }

    @Test(groups = "hessian-serialization")
    public void testTimeout() throws Exception
    {
        FailingServiceRPCEndpoint endpoint = new FailingServiceRPCEndpoint();
        endpoint.setConnectionFactory(connectionFactory);
        endpoint.setSerializationHandler(serializationHandler);
        endpoint.run();
        
        AMQPProxyFactory factory = new AMQPProxyFactory();
        factory.setReadTimeout(3000);
        factory.setConnectionFactory(connectionFactory);
        factory.setSerializationHandler(new HessianSerializationHandler());
        FailingService service = factory.create(FailingService.class);
        
        try
        {
            service.timeout(5000);
            fail("UndeclaredThrowableException expected");
        }
        catch (UndeclaredThrowableException e)
        {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof TimeoutException);
            Thread.sleep(3000);
        }
        finally {
            endpoint.destroy();
        }
    }

    @Test(groups = "hessian-serialization")
    public void testSerializationError() throws Exception
    {
        FailingServiceRPCEndpoint endpoint = new FailingServiceRPCEndpoint();
        endpoint.setConnectionFactory(connectionFactory);
        endpoint.setSerializationHandler(serializationHandler);
        endpoint.run();
        
        AMQPProxyFactory factory = new AMQPProxyFactory();
        factory.setReadTimeout(5000);
        factory.setConnectionFactory(connectionFactory);
        factory.setSerializationHandler(new HessianSerializationHandler());
        FailingService service = factory.create(FailingService.class);
        
        try
        {
            service.getNotSerializable();
            fail("IllegalStateException expected");
        }
        catch (IllegalStateException e)
        {
            assertTrue(e.getMessage().contains("must implement java.io.Serializable"));
        }
        finally {
            endpoint.destroy();
        }
    }
    
    @Test(groups = "hessian-serialization")
    public void testDoNothing() throws Exception
    {
        EchoServiceRPCEndpoint endpoint = new EchoServiceRPCEndpoint();
        endpoint.setConnectionFactory(connectionFactory);
        endpoint.setSerializationHandler(serializationHandler);
        endpoint.run();
        
        AMQPProxyFactory factory = new AMQPProxyFactory();
        factory.setReadTimeout(5000);
        factory.setConnectionFactory(connectionFactory);
        factory.setSerializationHandler(new HessianSerializationHandler());
        EchoService service = factory.create(EchoService.class);
        
        try
        {
            service.doNothing();
        }
        catch (Exception e)
        {
            throw e;
        }
        finally {
            endpoint.destroy();
        }
    }

    @Test(groups = "hessian-serialization")
    public void testError() throws Exception
    {
        FailingServiceRPCEndpoint endpoint = new FailingServiceRPCEndpoint();
        endpoint.setConnectionFactory(connectionFactory);
        endpoint.setSerializationHandler(serializationHandler);
        endpoint.run();

        AMQPProxyFactory factory = new AMQPProxyFactory();
        factory.setReadTimeout(3000);
        factory.setConnectionFactory(connectionFactory);
        factory.setSerializationHandler(new HessianSerializationHandler());
        FailingService service = factory.create(FailingService.class);

        try
        {
            service.error();
            fail("StackOverflowError expected");
        }
        catch (StackOverflowError e)
        {
           //Thread.sleep(3000);
        }
        finally {
            endpoint.destroy();
        }
    }
}
