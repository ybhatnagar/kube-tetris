package com.kubetetris.interaction;

import com.kubetetris.Node;
import com.kubetetris.Pod;
import com.kubetetris.Resource;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CoreV1ApiOverride;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class KubernetesAccessorImplV2 implements KubernetesAccessor{

    private static long DEFAULT_CPU_HEADROOM = 5000;
    private static long DEFAULT_MEMORY_HEADROOM = 5000;

    @Autowired
    CoreV1Api api;


    public void migratePod(String toRemove, String toNode) throws Exception {

        V1Pod podToBeMigrated = getPod(toRemove);

        log.info("Deleting the pod {} from node {}", toRemove,toNode);

        deletePod(podToBeMigrated);

        createPod(toNode,podToBeMigrated);
    }


    private void setNewPodAffinity(String toNode, V1Pod pod) {
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

    public Long getPodRequestForResource(String resource, V1Pod v1Pod){
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
     * This method has to be atomic. TODO: Handle atomicity and failure senarios
     * @param podA
     * @param nodeA
     * @param podB
     * @param nodeB
     * @throws Exception
     */
    public void swapPods(Pod podA, Node nodeA, Pod podB, Node nodeB) throws Exception{

        migratePod(podA.getName(),nodeB.getName());

        nodeA.removePod(podA);
        nodeB.addPod(podA);

        migratePod(podB.getName(), nodeA.getName());

        nodeB.removePod(podB);
        nodeA.addPod(podB);

    }

    /**
     * Gets the pod with the given name.
     * @param name
     * @return
     * @throws ApiException
     */
    public V1Pod getPod(String name) throws ApiException{
        V1Pod pod = api.listPodForAllNamespaces(null, null, "metadata.name=" + name, null, null, null, null, null, null)
                .getItems().get(0);

        return pod;
    }

    /**
     * Responsible for creating a pod on the specified node.
     * @param onNode
     * @param toBeCreated
     * @throws ApiException
     */
    public void createPod(String onNode, V1Pod  toBeCreated) throws ApiException {

        setNewPodAffinity(onNode,toBeCreated);

        toBeCreated.getMetadata().setResourceVersion(null);
        toBeCreated.getSpec().setNodeName(null);

        api.createNamespacedPod(toBeCreated.getMetadata().getNamespace(),toBeCreated,null,null,null);

        log.info("Created pod {} on node {}", toBeCreated,onNode);
    }


    /**
     * https://github.com/kubernetes-client/java/issues/86. 
     * @param pod
     * @return
     * @throws InterruptedException
     */
    public boolean deletePod(V1Pod pod) throws InterruptedException {

        api.getApiClient().getBasePath();

        try {
            api.deleteNamespacedPod(pod.getMetadata().getName(),pod.getMetadata().getNamespace(),null,null,10,null,null,null);
        } catch (ApiException e){
            log.error("Cannot delete pod {} due to error", pod.getMetadata().getName(), e.getMessage());
            return false;
        }

        Thread.sleep(15000);

        return true;
    }
}
