package com.vmware.borathon.interaction;;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import org.json.simple.parser.JSONParser;
import org.junit.BeforeClass;

public class KubernetesResourceTest {

    private static final Client client = ClientBuilder.newClient();
    private static JSONParser parser;
    @BeforeClass
    public static void setUp(){
        parser = new JSONParser();
    }

}