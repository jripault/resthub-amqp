/**
 * Copyright 2010 Emmanuel Bourg
 * Copyright 2012 Resthub.org
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
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Factory for creating Hessian client stubs. The returned stub will
 * call the remote object for all methods.
 *
 * After creation, the stub can be like a regular Java class. Because
 * it makes remote calls, it can throw more exceptions than a Java class.
 * In particular, it may throw protocol exceptions.
 *
 * 
 * @author Emmanuel Bourg
 * @author Scott Ferguson
 * @author Antoine Neveu
 */
public class AMQPProxyFactory implements InitializingBean, DisposableBean
{

    private ConnectionFactory connectionFactory;
    private RabbitTemplate template;
    private AmqpAdmin admin;
    private SimpleMessageListenerContainer listener;
    private String replyQueueName;
    private AtomicBoolean initializing = new AtomicBoolean(false);
    
    protected Class<?> serviceInterface;
    
    private String queuePrefix;

    private boolean isOverloadEnabled = false;

    private boolean debug = false;

    private long readTimeout = -1;
    
    private boolean compressed;

    protected SerializationHandler serializationHandler;

    /**
     * Creates the new proxy factory.
     */
    public AMQPProxyFactory()
    {
        
    }

    /**
     * Returns the prefix of the queue that receives the hessian requests.
     */
    public String getQueuePrefix()
    {
        return queuePrefix;
    }

    /**
     * Sets the prefix of the queue that receives the hessian requests.
     */
    public void setQueuePrefix(String queuePrefix)
    {
        this.queuePrefix = queuePrefix;
    }

    /**
     * Sets the debug mode.
     */
    public void setDebug(boolean isDebug)
    {
        this.debug = isDebug;
    }

    /**
     * Gets the debug mode.
     */
    public boolean isDebug()
    {
        return debug;
    }

    /**
     * Returns true if overloaded methods are allowed (using mangling)
     */
    public boolean isOverloadEnabled()
    {
        return isOverloadEnabled;
    }

    /**
     * set true if overloaded methods are allowed (using mangling)
     */
    public void setOverloadEnabled(boolean isOverloadEnabled)
    {
        this.isOverloadEnabled = isOverloadEnabled;
    }

    /**
     * Returns the socket timeout on requests in milliseconds.
     */
    public long getReadTimeout()
    {
        return readTimeout;
    }

    /**
     * Sets the socket timeout on requests in milliseconds.
     */
    public void setReadTimeout(long timeout)
    {
        readTimeout = timeout;
    }

    /**
     * Indicates if the requests/responses should be compressed.
     */
    public boolean isCompressed()
    {
        return compressed;
    }

    /**
     * Specifies if the requests/responses should be compressed.
     */
    public void setCompressed(boolean compressed) 
    {
        this.compressed = compressed;
    }

    /**
     * Get the connectionFactory
     * @return
     */
    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    /**
     * Set the connectionFactory
     * @param connectionFactory
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }
    
    /**
     * Get the RabbitMQ template
     * @return rabbitTemplate
     */
    public RabbitTemplate getTemplate() {
        return template;
    }

    /**
     * Get the service interface
     * @return serviceInterface
     */
    public Class<?> getServiceInterface(){
        return this.serviceInterface;
    }
    
    /**
     * Set the interface implemented by the proxy.
     * @param serviceInterface the interface the proxy must implement
     * @throws IllegalArgumentException if serviceInterface is null or is not an interface type
     */
    public void setServiceInterface(Class<?> serviceInterface){
        if (null == serviceInterface || ! serviceInterface.isInterface()){
            throw new IllegalArgumentException("'serviceInterface' is null or is not an interface");
        }
        this.serviceInterface = serviceInterface;
    }

    public SerializationHandler getSerializationHandler() {
        return serializationHandler;
    }

    public void setSerializationHandler(SerializationHandler serializationHandler) {
        this.serializationHandler = serializationHandler;
    }

    /**
     * Creates a new proxy from the specified interface.
     * @param api the interface
     * @return the proxy to the object with the specified interface
     */
    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> api){
        if (null == api || ! api.isInterface()){
            throw new IllegalArgumentException("Parameter 'api' is required");
        }
        this.serviceInterface = api;
        this.afterPropertiesSet();
        AMQPProxy handler = new AMQPProxy(this);
        return (T) Proxy.newProxyInstance(api.getClassLoader(), new Class[]{api}, handler);
    }

    /**
     * Create a queue and an exchange for requests
     *
     * @param queueName    the name of the queue
     * @param exchangeName    the name of the exchange
     */
    private void createRequestQueue(AmqpAdmin admin, String queueName, String exchangeName)
    {
        Queue requestQueue = new Queue(queueName, false, false, false);
        admin.declareQueue(requestQueue);
        DirectExchange requestExchange = new DirectExchange(exchangeName, false, false);
        admin.declareExchange(requestExchange);
        Binding requestBinding = BindingBuilder.bind(requestQueue).to(requestExchange).with(queueName);
        admin.declareBinding(requestBinding);
    }
    
    /**
     * Create a queue for response
     *
     * @param queueName    the name of the queue
     */
    private Queue createReplyQueue(AmqpAdmin admin, String queueName)
    {
        Queue replyQueue = new Queue(queueName, false, true, false);
        admin.declareQueue(replyQueue);
        return replyQueue;
    }
    
    /**
     * Return the name of the request exchange for the service.
     * @param cls
     * @return
     */
    public String getRequestExchangeName(Class<?> cls)
    {
        String requestExchange = cls.getSimpleName();
        if (this.queuePrefix != null)
        {
            requestExchange = this.queuePrefix + "." + requestExchange;
        }
        
        return requestExchange;
    }
    
    /**
     * Return the name of the request queue for the service.
     * @param cls
     * @return
     */
    public String getRequestQueueName(Class<?> cls)
    {
        String requestQueue = cls.getSimpleName();
        if (this.queuePrefix != null)
        {
            requestQueue = this.queuePrefix + "." + requestQueue;
        }
        
        return requestQueue;
    }
    
    /**
     * Return the name of the reply queue for the service
     * @param cls
     * @return
     */
    private String getReplyQueueName(Class<?> cls){
        String uuid = UUID.randomUUID().toString();
        String replyQueue = cls.getSimpleName() + "-reply-" + uuid;
        if (this.queuePrefix != null)
        {
            replyQueue = this.queuePrefix + "." + replyQueue;
        }
        
        return replyQueue;
    }
    
    /**
     * Initialize queues and the reply listener
     */
    private void initializeQueues(){
        if (!initializing.compareAndSet(false, true)) {
            return;
        }
        try {
            if (admin == null){
                admin = new RabbitAdmin(connectionFactory);
            }
            this.createRequestQueue(admin, this.getRequestQueueName(this.serviceInterface), this.getRequestExchangeName(this.serviceInterface));
            if (replyQueueName == null) {
                replyQueueName = this.getReplyQueueName(this.serviceInterface);
            }
            Queue replyQueue = this.createReplyQueue(admin, replyQueueName);
            this.template.setReplyQueue(replyQueue);
            
            if (listener == null || !listener.isRunning()){
                listener = new SimpleMessageListenerContainer(this.connectionFactory);
                listener.setMessageListener(this.template);
                listener.setQueues(replyQueue);
                listener.start();
            }
        }
        finally {
            initializing.compareAndSet(true, false);
        }
    }
    
    public void afterPropertiesSet(){
        if (this.connectionFactory == null){
            throw new IllegalArgumentException("Property 'connectionFactory' is required");
        }
        // initialize template
        this.template = new RabbitTemplate(this.connectionFactory);
        if (this.readTimeout > 0){
            this.template.setReplyTimeout(readTimeout);
        }
        this.initializeQueues();
        
        // Add connection listener to recreate queue and reinitialize template when connection fall
        connectionFactory.addConnectionListener(new ConnectionListener() {
            
            public void onCreate(Connection connection) {
                 initializeQueues();
            }
            
            public void onClose(Connection connection) {
            }
            
        });
    }

    /**
     * Destroys the reply listener
     */
    public void destroy() throws Exception {
        listener.destroy();
    }
}

