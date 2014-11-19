package org.resthub.rpc.annotation;

import java.lang.annotation.*;

/**
 * Field/Parameter annotation to indicate that it is linked to a RPCClient. If client does net yet exist,
 * it will be created.
 *  *
 * The bean name will be the interface class, suffixed by "AMQPProxyFactoryBean"
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface RPCClient {

}