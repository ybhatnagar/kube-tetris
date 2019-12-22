package com.kubetetris.interaction;;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import com.kubetetris.Node;
import com.kubetetris.Pod;
import org.json.simple.parser.JSONParser;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static javax.ws.rs.client.Entity.entity;

@Ignore
public class KubernetesResourceTest {

    private static final Client client = ClientBuilder.newClient();
    private static JSONParser parser;
    private static KubernetesAccessorImpl accessor;
    @BeforeClass
    public static void setUp(){
        parser = new JSONParser();
        accessor = new KubernetesAccessorImpl();
    }

    @Test
    public void testSOme() throws Exception{

        Pod pod = new Pod("0","frontend",10L,10L,false);

        Node node = new Node("0","ip-172-20-0-132.ec2.internal",15000,4000);
        node.addPod(pod);

        Collection<Node> nodes = accessor.getSystemSnapshot();
        List list = new ArrayList(nodes);

    }

}