package org.resthub.rpc.service;


import org.resthub.rpc.annotation.RPCEndpoint;

@RPCEndpoint
public class AnnotatedEndpointServiceImpl implements AnnotatedEndpointService {
    public String callMe(String name) {
        return "Call me, " + name;
    }
}
