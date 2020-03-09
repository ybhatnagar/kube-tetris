package com.kubetetris.interaction;

import com.kubetetris.Node;
import com.kubetetris.Pod;
import com.kubetetris.Resource;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.proto.V1;
import io.kubernetes.client.util.Config;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class KubernetesAccessorImplV2 implements KubernetesAccessor{

    private static long DEFAULT_CPU_HEADROOM = 5000;
    private static long DEFAULT_MEMORY_HEADROOM = 5000;

    CoreV1Api api;

    {
        try {
            ApiClient client = Config.fromConfig("/Users/yashbhatnagar/Downloads/kubeconfig");
            Configuration.setDefaultApiClient(client);
            api = new CoreV1Api();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    @Override
    public List<Node> populateSystem() {
        return null;
    }

    @Override
    public void migratePod(String toRemove, String toNode) throws Exception {

        V1Pod podToBeMigrated = api.listPodForAllNamespaces(null, null, "metadata.name=" + toRemove, null, null, null, null, null, null)
                .getItems().get(0);


        getPodWithNewAffinity(toNode, podToBeMigrated);

        api.deleteNamespacedPod(podToBeMigrated.getMetadata().getName(),podToBeMigrated.getMetadata().getNamespace(),null,null,10,true,null,null);

        api.createNamespacedPod(podToBeMigrated.getMetadata().getNamespace(),podToBeMigrated,null,null,null);

        log.info("Created pod {} on node {}", toRemove,toNode);

    }

    private void getPodWithNewAffinity(String toNode, V1Pod pod) {
        V1NodeSelectorRequirement nodeSelectorRequirement = new V1NodeSelectorRequirement();

        nodeSelectorRequirement.setKey("kubernetes.io/hostname");
        nodeSelectorRequirement.setOperator("In");
        nodeSelectorRequirement.setValues(Arrays.asList(toNode));

        V1NodeSelectorTerm nodeSelectorTerm = new V1NodeSelectorTerm();
        nodeSelectorTerm.addMatchExpressionsItem(nodeSelectorRequirement);

        V1NodeSelector v1NodeSelector = new V1NodeSelector().addNodeSelectorTermsItem(nodeSelectorTerm);


        V1NodeAffinity nodeAffinity  = new V1NodeAffinity();

        nodeAffinity.setRequiredDuringSchedulingIgnoredDuringExecution(v1NodeSelector);

        if(pod.getSpec()!=null && pod.getSpec().getAffinity()!=null){
            pod.getSpec().getAffinity().setNodeAffinity(nodeAffinity);
        } else{
            V1Affinity v1Affinity = new V1Affinity();
            v1Affinity.setNodeAffinity(nodeAffinity);

            pod.getSpec().setAffinity(v1Affinity);

        }
    }

    @Override
    public List<Node> getSystemSnapshot() throws Exception {

        //TODO: cache this call for a certain time, and use this in swap, migrate and other calls
        V1NodeList v1NodeList = api.listNode(null, null, null, null, null, null, null, 10, null);

        Map<String,Node> nodeMap = v1NodeList.getItems().stream()
                .filter(this::checkIfNodeIsScheduleable)
                .map(v1Node -> {
                    BigDecimal cpu = getNodeResoures("cpu",v1Node);
                    BigDecimal memory = getNodeResoures("memory",v1Node);


                    //should not use long value, should be bigdecimal

            return new Node(v1Node.getMetadata().getName(),v1Node.getMetadata().getName(),memory.longValue(),cpu.longValue());
        }).collect(Collectors.toMap(Resource::getName, node -> node));

        //TODO: cache this call for a certain time, and use this in swap, migrate and other calls
        V1PodList v1PodList = api.listPodForAllNamespaces(null,null,null,null,null,null,null,null,null);

        v1PodList.getItems().forEach(v1Pod -> {

            Long cpuRequest = getPodRequestForResource("cpu",v1Pod);
            Long memRequest = getPodRequestForResource("memory",v1Pod);



            Pod po = new Pod(v1Pod.getMetadata().getName(),v1Pod.getMetadata().getName(),memRequest,cpuRequest,v1Pod.getMetadata().getNamespace().equals("kube-system"));

            nodeMap.get(v1Pod.getSpec().getNodeName()).addPod(po);
        });

        return new ArrayList<>(nodeMap.values());
    }

    private BigDecimal getNodeResoures(String resource, V1Node v1Node){
        try{
            return v1Node.getStatus().getAllocatable().get(resource).getNumber();

        }catch (NullPointerException ex){
            throw new RuntimeException("Node does not has this resource");
        }
    }

    private Long getPodRequestForResource(String resource, V1Pod v1Pod){
        if(v1Pod.getSpec().getContainers()!=null){

            return v1Pod.getSpec().getContainers().stream().mapToLong(container -> {
                if(container.getResources()!=null && container.getResources().getRequests()!=null && container.getResources().getRequests().get(resource) !=null){
                    log.info("pod =" +  v1Pod.getMetadata().getName() + " container = " + container.getName());
                    return container.getResources().getRequests().get(resource).getNumber().longValue();
                } else{
                    //TODO: use the utilization to get the last hour usage and consider the median value

                    return 0L;
                }
            }).sum();
        } else{
            //TODO: use the utilization to get the last hour usage and consider the median value
            return 0L;
        }
    }

    /**
     * filter the nodes which are non scheduleable, like master nodes, without taint, etc
     * @param v1Node
     * @return
     */
    private boolean checkIfNodeIsScheduleable(V1Node v1Node) {
        return ! (v1Node.getSpec()!=null && v1Node.getSpec().getTaints()!= null && v1Node.getSpec().getTaints().stream().anyMatch(v1Taint -> v1Taint.getKey().equals("NoSchedule")));
    }

    /**
     * Thismethod has to be atomic. TODO: Handle atomicity and failure senarios
     * @param podA
     * @param nodeA
     * @param podB
     * @param nodeB
     * @throws Exception
     */
    @Override
    public void swapPods(Pod podA, Node nodeA, Pod podB, Node nodeB) throws Exception{

        migratePod(podA.getName(),nodeB.getName());

        migratePod(podB.getName(), nodeA.getName());

    }

    @Override
    public void createPod(String onNode, JSONObject podConfig) throws InterruptedException, ParseException {

    }

    @Override
    public boolean deletePod(String podName) throws InterruptedException {
        return false;
    }

    @Override
    public JSONObject readPodToBePlaced(String nodeName, long cpu, long memoryMB) {
        return null;
    }

    @Override
    public void cleanSystem() {

    }
}
