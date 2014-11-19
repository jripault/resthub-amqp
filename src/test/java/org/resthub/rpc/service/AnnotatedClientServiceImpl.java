package org.resthub.rpc.service;

import org.resthub.rpc.annotation.RPCClient;

public class AnnotatedClientServiceImpl implements AnnotatedClientService {
    @RPCClient
    private EchoService echoService;

    public void setEchoService(EchoService echoService) {
        this.echoService = echoService;
    }

    @Override
    public String callEcho(String msg){
        return this.echoService.echo(msg);
    }
}
