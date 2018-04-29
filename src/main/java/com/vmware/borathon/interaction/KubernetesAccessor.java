package com.vmware.borathon.interaction;


import com.vmware.borathon.Node;
import com.vmware.borathon.Pod;
import com.vmware.borathon.balancer.WorkLoadBalancerImpl;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.client.Entity.entity;

public class KubernetesAccessor {
    private static final Logger log = LoggerFactory.getLogger(KubernetesAccessor.class);

    private static final int TIMEOUT_MILLIS = 5000;

    private static final Client client = ClientBuilder.newClient();
    JSONParser parser = new JSONParser();

    private long memoryUnitParser(String toBeConverted){
        if(toBeConverted.endsWith("Mi")){
            return Long.valueOf(toBeConverted.subSequence(0,toBeConverted.length()-2).toString());
        }
        else if(toBeConverted.endsWith("Gi")){
            return Long.valueOf(toBeConverted.subSequence(0,toBeConverted.length()-2).toString())*1000;
        }
        else if(toBeConverted.endsWith("Ki")){
            return Long.valueOf(toBeConverted.subSequence(0,toBeConverted.length()-2).toString())/1000;
        }
        else return Long.valueOf(toBeConverted);
    }

    private long cpuUnitParser(String toBeConverted){
        if(toBeConverted.endsWith("m")){
            return Long.valueOf(toBeConverted.subSequence(0,toBeConverted.length()-1).toString());
        }
        else{
            return Long.valueOf(toBeConverted)*1000;
        }
    }


    public boolean getStatus(String podName) throws Exception{
        WebTarget webTarget = client.target("http://localhost:8080/api/v1/namespaces/default/pods/" + podName);
        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        Response response = invocationBuilder.method(HttpMethod.GET, null, Response.class);

        if(response.getStatus()==404){
            return true;
        }else{
            log.info("pod is not ready");
            return false;
        }

    }
    public Collection<Node> getNodesWithPods() throws Exception {

        WebTarget webTarget = client.target("http://localhost:8080/api/v1/namespaces/default/pods");
        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        Response response = invocationBuilder.method(HttpMethod.GET, null, Response.class);

        // response body
        String responseAsString = response.readEntity(String.class);
        JSONObject parsedJsonPods = (JSONObject) parser.parse(responseAsString);

        webTarget = client.target("http://localhost:8080/api/v1/nodes");
        invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        response = invocationBuilder.method(HttpMethod.GET, null, Response.class);
        responseAsString = response.readEntity(String.class);
        JSONObject parsedJsonNodes = (JSONObject) parser.parse(responseAsString);

        Map<String,Node> nodes = new HashMap<>();

        JSONArray nodesJson = (JSONArray) parsedJsonNodes.get("items");
        Iterator iterator = nodesJson.iterator();

        Integer i=0;
        while (iterator.hasNext()){
            JSONObject result = (JSONObject) iterator.next();
            String nodeName = (String) ((JSONObject)result.get("metadata")).get("name");
            String cpu = (String) ((JSONObject) ((JSONObject)result.get("status")).get("capacity")).get("cpu");
            String mem = (String) ((JSONObject) ((JSONObject)result.get("status")).get("capacity")).get("memory");


            long nodeMem = memoryUnitParser(mem);
            long nodeCpu = cpuUnitParser(cpu);
            nodes.put(nodeName,new Node(i.toString(),nodeName,nodeMem,nodeCpu));
            i++;
        }


        JSONArray podsJson = (JSONArray) parsedJsonPods.get("items");
        iterator = podsJson.iterator();

        List<Pod> pods = new ArrayList<>();

        i=0;
        while (iterator.hasNext()){
            JSONObject result = (JSONObject) iterator.next();
            String podName = (String) ((JSONObject)result.get("metadata")).get("name");
            String nodeName = (String) ((JSONObject)result.get("spec")).get("nodeName");

            JSONArray containers = (JSONArray) ((JSONObject)result.get("spec")).get("containers");
            Iterator<JSONObject> containerIterator = containers.iterator();

            Long cpuRequests = 0L;
            Long memRequests = 0L;

            while(containerIterator.hasNext()){
                JSONObject requests = (JSONObject)((JSONObject) containerIterator.next().get("resources")).get("requests");
                cpuRequests += cpuUnitParser((String) requests.get("cpu"));
                memRequests += memoryUnitParser((String) requests.get("memory"));
            }

            Pod currentPod = new Pod(i.toString(),podName,memRequests,cpuRequests);
            pods.add(currentPod);
            nodes.get(nodeName).addPod(currentPod);
            i++;
        }
        return nodes.values();
    }

    public void migratePod(Pod toremove, Node toPlace) throws Exception{
        //delete resourceversion in metadata, and nodeName from spec from the get response of pod
        //appeind annotations in metadata with this value:
        //
        /**
         "annotations": {
            "scheduler.alpha.kubernetes.io/affinity":
         "{\"nodeAffinity\": {\"requiredDuringSchedulingIgnoredDuringExecution\": {\"nodeSelectorTerms\": [{\"matchExpressions\": [{\"key\": \"kubernetes.io/hostname\", \"operator\": \"In\",\"values\": [\"ip-172-20-0-132.ec2.internal\"]}]}]}}}"
         }
         */
        String podName = toremove.getName();
        String originalNodeName;

        WebTarget webTarget = client.target("http://localhost:8080/api/v1/namespaces/default/pods/"+podName);
        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        Response response = invocationBuilder.method(HttpMethod.GET, null, Response.class);
        String responseAsString = response.readEntity(String.class);


        JSONObject podResponse = (JSONObject) parser.parse(responseAsString);

        ((JSONObject)podResponse.get("metadata")).remove("resourceVersion");
        originalNodeName=(String)((JSONObject)podResponse.get("spec")).remove("nodeName");

        Map<String,String> hashm = new HashMap<>();
        String aux = "{\"nodeAffinity\": {\"requiredDuringSchedulingIgnoredDuringExecution\": {\"nodeSelectorTerms\": [{\"matchExpressions\": [{\"key\": \"kubernetes.io/hostname\", \"operator\": \"In\",\"values\": [\""+ toPlace.getName() + "\"]}]}]}}}";
        hashm.put("scheduler.alpha.kubernetes.io/affinity",aux);
        JSONObject obj = new JSONObject(hashm);
        ((JSONObject)podResponse.get("metadata")).put("annotations",obj);


        //deleting the pod from current node
        log.info("deleting the pod {} from current node {}" ,podName,originalNodeName );

        webTarget = client.target("http://localhost:8080/api/v1/namespaces/default/pods/"+podName);
        invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        response = invocationBuilder.method(HttpMethod.DELETE, null, Response.class);

        if(response.getStatus()==200){
            Thread.sleep(TIMEOUT_MILLIS);
            log.info("Pod {} deleted from node {}", podName,originalNodeName);

            while (!getStatus(podName)) {
                log.info("delete operation not yet completed");
                Thread.sleep(TIMEOUT_MILLIS);
            }

            response = client.target("http://localhost:8080/api/v1/namespaces/default/pods/")
                    .request()
                    .method("POST", entity(podResponse.toJSONString(), "application/json"));
            response.getStatus();

            if(response.getStatus()>=200 && response.getStatus()<400 ){
                Thread.sleep(TIMEOUT_MILLIS);

                while (getStatus(podName)) {
                    log.info("create operation not yet completed");
                    Thread.sleep(TIMEOUT_MILLIS);
                }
                webTarget = client.target("http://localhost:8080/api/v1/namespaces/default/pods/"+podName);
                invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
                response = invocationBuilder.method(HttpMethod.GET, null, Response.class);

                log.info("pod successfully placed on new node {}" , toPlace.getName());
            }else{
                log.error("pod creation failed!! stopping");
            }
        }else{
            log.error("pod delete failed from original node. Stopping");
        }


    }

}
