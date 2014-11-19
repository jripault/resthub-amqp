package org.resthub.rpc.java.annotation;

import org.resthub.rpc.annotation.RPCClient;
import org.resthub.rpc.service.AnnotatedClientService;
import org.resthub.rpc.service.AnnotatedEndpointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

import static org.fest.assertions.api.Assertions.assertThat;

@ActiveProfiles("resthub-amqp-annotation")
@ContextConfiguration(locations = {"classpath:applicationContext-annotations.xml"})
public class SpringAnnotationRPCTest extends AbstractTestNGSpringContextTests {
    @Autowired
    private AnnotatedClientService annotatedClientService;

    @RPCClient
    private AnnotatedEndpointService annotatedEndpointService;

    @Test
    public void shouldCallEcho() {
        // Given

        // When
        String response = annotatedClientService.callEcho("Le gras, c'est la vie !");

        // Then
        assertThat(response).isEqualTo("Le gras, c'est la vie !");
    }
    @Test
    public void shouldCallMe() {
        // Given

        // When
        String response = annotatedEndpointService.callMe("Julien");

        // Then
        assertThat(response).isEqualTo("Call me, Julien");
    }
}
