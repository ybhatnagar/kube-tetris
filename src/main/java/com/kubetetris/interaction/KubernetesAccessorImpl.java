package com.kubetetris.interaction;


import com.kubetetris.Main;
import com.kubetetris.Node;
import com.kubetetris.Pod;
import com.kubetetris.loadsimulator.NodeDataGenerator;

import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.util.Config;
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
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.client.Entity.entity;

public class KubernetesAccessorImpl implements KubernetesAccessor{
    private static final Logger log = LoggerFactory.getLogger(KubernetesAccessorImpl.class);

    private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int INTERVAL_TIME_MILLIS = 5000;
    private static final int TIMEOUT = 60000;

    private static final String KUBE_SYSTEM= "kube-system";

    private static final Client client = ClientBuilder.newClient();
    JSONParser parser = new JSONParser();

    @Override
    public List<Node> populateSystem() {
        List<Node> inputNodes = Collections.emptyList();
        try {
            //Get the intial resource map
            inputNodes = getSystemSnapshot();

            //Create pods in the nodes
            List<Node> simulatedNodes = NodeDataGenerator.generateFixedReal();
            createSystemFromFixed(simulatedNodes.get(0),inputNodes.get(0));
            createSystemFromFixed(simulatedNodes.get(1),inputNodes.get(1));
            createSystemFromFixed(simulatedNodes.get(2),inputNodes.get(2));

            //Get the updated resource map
            inputNodes = getSystemSnapshot();
        } catch (ParseException e) {
            e.printStackTrace();
            return inputNodes;
        }
        return inputNodes;
    }

    public List<Node> getSystemSnapshot() throws ParseException{
        Map<String, Node> nodes = getNodesInSystem();
        nodes = fillNodesWithPods("default",nodes); // default
        nodes = fillNodesWithPods(KUBE_SYSTEM,nodes);
        return new ArrayList<>(nodes.values());
    }

    private Map<String, Node> getNodesInSystem() throws ParseException{

        WebTarget webTarget = client.target("http://localhost:8080/api/v1/nodes");
        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        Response response = invocationBuilder.method(HttpMethod.GET, null, Response.class);
        String responseAsString = response.readEntity(String.class);
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


            long nodeMem = memoryUnitParser(mem) - 50;
            long nodeCpu = cpuUnitParser(cpu) - 50;
            nodes.put(nodeName,new Node(i.toString(),nodeName,nodeMem,nodeCpu));
            i++;
        }
        return nodes;
    }

    private boolean getDeleteStatus(String podName){
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

    private Map<String, Node> fillNodesWithPods(String namespace,Map<String,Node> nodes) throws ParseException {

        WebTarget webTarget = client.target("http://localhost:8080/api/v1/namespaces/" + namespace+ "/pods");
        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        Response response = invocationBuilder.method(HttpMethod.GET, null, Response.class);

        // response body
        String responseAsString = response.readEntity(String.class);
        JSONObject parsedJsonPods = (JSONObject) parser.parse(responseAsString);

        Integer i=0;
        JSONArray podsJson = (JSONArray) parsedJsonPods.get("items");
        if(podsJson != null) {
            Iterator iterator = podsJson.iterator();

            List<Pod> pods = new ArrayList<>();

            while (iterator.hasNext()) {
                JSONObject result = (JSONObject) iterator.next();
                String podName = (String) ((JSONObject) result.get("metadata")).get("name");
                String nodeName = (String) ((JSONObject) result.get("spec")).get("nodeName");

                JSONArray containers = (JSONArray) ((JSONObject) result.get("spec")).get("containers");
                Iterator<JSONObject> containerIterator = containers.iterator();

                Long cpuRequests = 0L;
                Long memRequests = 0L;

                while (containerIterator.hasNext()) {
                    JSONObject jsonObject = containerIterator.next();
                    JSONObject requests = (JSONObject) ((JSONObject) jsonObject.get("resources")).get("requests");

                    if(null != requests){
                        cpuRequests += cpuUnitParser((String) requests.get("cpu"));
                        memRequests += memoryUnitParser((String) requests.get("memory"));
                    }

                }

                Pod currentPod;
                if (namespace.equals(KUBE_SYSTEM)) {
                    currentPod = new Pod(i.toString(), podName, memRequests, cpuRequests, true);
                } else {
                    currentPod = new Pod(i.toString(), podName, memRequests, cpuRequests, false);
                }

                if (nodeName != null) {
                    nodes.get(nodeName).addPod(currentPod);
                }

                i++;
            }
            return nodes;
        }
        return nodes;
    }

    public void swapPods(String podA, String nodeA, String podB, String nodeB){
        try {
            //delete podA from nodeA
            //add podB to nodeA
            migratePod(podA, nodeB);

            //delete podB from nodeB
            //add podA to nodeB
            migratePod(podB, nodeA);
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public boolean deletePod(String podName) throws InterruptedException {
        //deleting the pod from current node
        log.info("deleting the pod {} from current node" ,podName );

        WebTarget webTarget;
        Invocation.Builder invocationBuilder;
        Response response;

        webTarget = client.target("http://localhost:8080/api/v1/namespaces/default/pods/"+podName);
        invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        response = invocationBuilder.method(HttpMethod.DELETE, null, Response.class);

        int timeout=TIMEOUT;
        if(response.getStatus()==200){
            Thread.sleep(INTERVAL_TIME_MILLIS);
            log.info("Pod {} deleted from node.Waiting for it to complete.", podName);

            while (timeout>=0 && !getDeleteStatus(podName)) {
                log.info("delete operation not yet completed, sleeping for {} milliseconds",INTERVAL_TIME_MILLIS);
                Thread.sleep(INTERVAL_TIME_MILLIS);
                timeout-=INTERVAL_TIME_MILLIS;
            }
            if(timeout<0){
                log.error("delete timed out, Inconsistent state!!!! please revert the changes done till now, from the stack");
                throw new IllegalStateException("System is in inconsistent state, halting now.");
            }
            else{
                return true;
            }

        }else{
            log.error("pod delete failed from original node. Stopping");
            throw new IllegalStateException("Pod delete failed from original pod.");
        }
    }

    //Fetch the pod details from k8s and append the node Affinity
    public void migratePod(String podName, String toNode) throws IllegalStateException, ParseException,InterruptedException{
        //delete resourceversion in metadata, and nodeName from spec from the get response of pod
        //appeind annotations in metadata with this value:
        //
        /**
         "annotations": {
            "scheduler.alpha.kubernetes.io/affinity":
         "{\"nodeAffinity\": {\"requiredDuringSchedulingIgnoredDuringExecution\": {\"nodeSelectorTerms\": [{\"matchExpressions\": [{\"key\": \"kubernetes.io/hostname\", \"operator\": \"In\",\"values\": [\"ip-172-20-0-132.ec2.internal\"]}]}]}}}"
         }
         */
        //String originalNodeName;

        JSONObject podResponse = getPodData(podName);

        boolean deleteResult = deletePod(podName);
        if(deleteResult){
            createPod(toNode,podResponse);
        }else{
            log.error("pod delete failed");
        }

    }

    public JSONObject getPodData(String podName) throws ParseException {
        WebTarget webTarget = client.target("http://localhost:8080/api/v1/namespaces/default/pods/" + podName);
        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        Response response = invocationBuilder.method(HttpMethod.GET, null, Response.class);
        String responseAsString = response.readEntity(String.class);


        JSONObject podResponse = (JSONObject) parser.parse(responseAsString);

        ((JSONObject) podResponse.get("metadata")).remove("resourceVersion");
        ((JSONObject) podResponse.get("spec")).remove("nodeName");
        return podResponse;
    }

    public void createPod(String onNode, JSONObject podConfig) throws InterruptedException,ParseException {

        log.info("creating on node {} pod with config {}",onNode,podConfig.toString());

        Map<String, String> hashm = new HashMap<>();
        String aux = "{\"nodeAffinity\": {\"requiredDuringSchedulingIgnoredDuringExecution\": {\"nodeSelectorTerms\": [{\"matchExpressions\": [{\"key\": \"kubernetes.io/hostname\", \"operator\": \"In\",\"values\": [\"" + onNode + "\"]}]}]}}}";
        hashm.put("scheduler.alpha.kubernetes.io/affinity", aux);
        JSONObject obj = new JSONObject(hashm);
        ((JSONObject) podConfig.get("metadata")).put("annotations", obj);

        Response response;
        int timeout;
        WebTarget webTarget;
        Invocation.Builder invocationBuilder;

        //Creating pod
        response = client.target("http://localhost:8080/api/v1/namespaces/default/pods/")
                .request()
                .method("POST", entity(podConfig.toJSONString(), "application/json"));

        Thread.sleep(INTERVAL_TIME_MILLIS);
        //response.getDeleteStatus();

        if(response.getStatus()>=200 && response.getStatus()<400 ){
            /*timeout=INTERVAL_TIME_MILLIS;
            while (timeout>=0 && getDeleteStatus(podName)) {
                log.info("create operation not yet completed,sleeping for {} milliseconds", INTERVAL_TIME_MILLIS);
                Thread.sleep(INTERVAL_TIME_MILLIS);
                timeout -= INTERVAL_TIME_MILLIS;
            }
            if(timeout <=0){
                log.error("create pod in new node timed out, Inconsistent state!!!! please revert the changes done till now, from the stack");
                throw new IllegalStateException("System is in inconsistent state, halting now.");
            }*/


            log.info("pod successfully placed on new node {}" , onNode);
        }else{
            log.error("pod creation failed!! stopping");
        }
    }

    public void cleanSystem(){
        try {
            getSystemSnapshot().forEach(node ->{
                node.getPods().values().forEach(pod ->{
                    try {
                        deletePod(pod.getName());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            });
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private long memoryUnitParser(String toBeConverted){
        if(toBeConverted==null){
            return 0;
        }
        if(toBeConverted.endsWith("Mi")){
            return Long.valueOf(toBeConverted.subSequence(0,toBeConverted.length()-2).toString());
        }
        else if(toBeConverted.endsWith("Gi")){
            return Long.valueOf(toBeConverted.subSequence(0,toBeConverted.length()-2).toString())*1024;
        }
        else if(toBeConverted.endsWith("Ki")){
            return Long.valueOf(toBeConverted.subSequence(0,toBeConverted.length()-2).toString())/1024;
        }
        else return Long.valueOf(toBeConverted);
    }

    private long cpuUnitParser(String toBeConverted){
        if(toBeConverted==null){
            return 0;
        }
        if(toBeConverted.endsWith("m")){
            return Long.valueOf(toBeConverted.subSequence(0,toBeConverted.length()-1).toString());
        }
        else{
            return Long.valueOf(toBeConverted)*1000;
        }
    }

    private void createSystemFromFixed(Node simulated, Node actual){
        simulated.getPods().values().forEach(pod ->{
            try {
                createPod(actual.getName(),readPodToBePlaced(actual.getName(),pod.getRequest().getCpuMillicore(),pod.getRequest().getMemoryMB()));
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ParseException e) {
                e.printStackTrace();
            }
        });
    }

    public JSONObject readPodToBePlaced(String nodeName, long cpu, long memoryMB){
        JSONParser parser = new JSONParser();
        Object obj = null;
        try {
            InputStream fileStream = Main.class.getClassLoader().getResourceAsStream("newpod.json");
            obj = parser.parse(new InputStreamReader(fileStream));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        try {
            if(obj != null){
                String podName = randomAlpha(10).toLowerCase();
                String jsonStr = obj.toString().replace("$PODNAME", podName).replace("$CPU",""+(cpu/2)).replace("$MEM",""+(memoryMB)/2);
                return (JSONObject) parser.parse(jsonStr);
            }
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

    private String randomAlpha(int count) {
        StringBuilder builder = new StringBuilder();
        while (count-- != 0) {
            int character = (int)(Math.random()*ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }
}
