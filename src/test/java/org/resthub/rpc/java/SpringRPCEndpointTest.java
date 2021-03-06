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

import org.resthub.rpc.service.EchoService;
import org.testng.annotations.Test;

import javax.annotation.Resource;

import static org.testng.AssertJUnit.assertEquals;


public class SpringRPCEndpointTest extends SpringAMQPProxyTest
{
    @Resource(name="echoServiceTest")
    protected EchoService echoServicePrefix;
    
    @Test(groups = "java-serialization")
    public void testQueuePrefix() throws Exception
    {
        String message = "Hello Hessian!";
        
        assertEquals(message, echoServicePrefix.echo(message));
    }

}
