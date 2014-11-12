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

import org.resthub.rpc.service.EchoService;
import org.resthub.rpc.service.FailingService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

import javax.annotation.Resource;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.TimeoutException;

import static org.testng.AssertJUnit.*;

@ContextConfiguration(locations = {"classpath:applicationContext-hessian-server.xml", "classpath:applicationContext-hessian-client.xml"})
public class SpringAMQPProxyTest extends AbstractTestNGSpringContextTests
{
    protected final String HOSTNAME = "localhost";
    
    @Resource(name="echoServiceTest")
    protected EchoService echoServiceTest;
    
    @Resource(name="echoServiceExceptionTest")
    protected EchoService echoServiceExceptionTest;
    
    @Resource(name="testTimeout")
    protected FailingService testTimeout;
    
    @Resource(name="serializationError")
    protected FailingService serializationError;

    @Test(groups = "hessian-serialization")
    public void testEcho() throws Exception
    {
        String message = "Hello Hessian!";

        assertEquals(message, echoServiceTest.echo(message));
        assertEquals(message, echoServiceTest.echo(message));
    }

    @Test(groups = "hessian-serialization")
    public void testException() throws Exception
    {
       
        String message = "Hello Hessian!";

        try
        {
            echoServiceExceptionTest.exception(message);
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
    }

    @Test(groups = "hessian-serialization")
    public void testTimeout() throws Exception
    {
        try
        {
            testTimeout.timeout(5000);
            fail("UndeclaredThrowableException expected");
        }
        catch (UndeclaredThrowableException e)
        {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof TimeoutException);
            Thread.sleep(3000);
        }
    }

    @Test(groups = "hessian-serialization")
    public void testSerializationError() throws Exception
    {
        try
        {
            serializationError.getNotSerializable();
            fail("IllegalStateException expected");
        }
        catch (IllegalStateException e)
        {
            assertTrue(e.getMessage().contains("must implement java.io.Serializable"));
        }
    }
    
    @Test(groups = "hessian-serialization")
    public void testDoNothing() throws Exception
    {
        try
        {
            echoServiceTest.doNothing();
        }
        catch (Exception e)
        {
            throw e;
        }
    }
}
