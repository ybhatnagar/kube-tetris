package com.vmware.borathon.interaction;;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.vmware.borathon.Node;
import com.vmware.borathon.Pod;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.client.Entity.entity;

public class KubernetesResourceTest {

    private static final Client client = ClientBuilder.newClient();
    private static JSONParser parser;
    private static KubernetesAccessor accessor;
    @BeforeClass
    public static void setUp(){
        parser = new JSONParser();
        accessor = new KubernetesAccessor();
    }

    @Test
    public void testSOme() throws Exception{

        Pod pod = new Pod("0","frontend",10L,10L,false);

        Node node = new Node("0","ip-172-20-0-132.ec2.internal",15000,4000);
        node.addPod(pod);

        Collection<Node> nodes = accessor.getSystem();
        List list = new ArrayList(nodes);

    }

}