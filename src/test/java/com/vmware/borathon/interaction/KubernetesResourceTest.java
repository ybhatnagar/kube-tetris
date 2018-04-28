package com.vmware.borathon.interaction;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.junit.BeforeClass;
import org.junit.Test;

public class KubernetesResourceTest {

    private static final Client client = ClientBuilder.newClient();

    @BeforeClass
    public static void setUp(){

    }

    @Test
    public void getPods() {
        WebTarget webTarget = client.target("http://localhost:8080/api/v1/namespaces/default/pods");
        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        Response response = invocationBuilder.method(HttpMethod.GET, null, Response.class);

        // response body
        System.out.println(response.readEntity(String.class));
    }
}