package org.resthub.rpc.annotation;

import java.lang.annotation.*;

/**
 * Annotation for service class to expose it as RPC endpoint.</br>
 * The bean name will be ServiceName suffixed by RPCEndpoint.
 * Le nom du bean HessianEndPoint sera celui du bean service , auquel le suffixe "HessianEndPoint"
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RPCEndpoint {
	
    /**
     * Concurrent consumers count<br/>
     * default value 1
     */
    int threadCount() default 1;

    /**
     * Disable RPCEndpoint for the active profiles listed
     */
    String disableForProfile() default "";

    /**
     * RPCEndpoint enabled if profiles listed are active
     */
    String profile() default "";
    
}