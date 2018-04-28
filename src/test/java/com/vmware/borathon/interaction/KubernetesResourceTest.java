package com.vmware.borathon.interaction;

import static com.blogspot.toomuchcoding.jsonassert.JsonAssertion.assertThatJson;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
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
        String responseAsString = response.readEntity(String.class);
        DocumentContext parsedJson = JsonPath.parse(responseAsString);

        assertThatJson(parsedJson).field("kind").isEqualTo("PodList");
    }
}