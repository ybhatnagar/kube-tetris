package com.vmware.borathon;

import java.util.Collections;
import java.util.List;

import com.vmware.borathon.interaction.KubernetesAccessor;
import com.vmware.borathon.interaction.KubernetesAccessorImpl;
import com.vmware.borathon.scheduler.CapacityPlacementService;
import com.vmware.borathon.scheduler.CapacityPlacementServiceImpl;
import com.vmware.borathon.scheduler.MigrationPlanDto;
import lombok.extern.slf4j.Slf4j;

import com.vmware.borathon.balancer.WorkLoadBalancer;
import com.vmware.borathon.balancer.WorkLoadBalancerImpl;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

@Slf4j
public class Main {

    private static KubernetesAccessor k8S;

    public static void main(String[] args) throws InterruptedException {
        //Create SystemController and nodes and pods
        SystemController systemController = new SystemControllerImpl();

        //Fetch Nodes from K8S
        k8S = new KubernetesAccessorImpl();

        List<Node> inputNodes = fetchNodes(false);

        //Update the system snapshot with latest nodes update with pods
        inputNodes.forEach(node -> systemController.addNode(node));

        placeMyWorkload(systemController);

        System.out.println("***********************************************************************************");
        System.out.println("Trying to balance the cpu/memory consumption across nodes");
        System.out.println("***********************************************************************************");

        Thread.sleep(5000);

        triggerWorkLoadBalancer(systemController, 50);

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        k8S.cleanSystem();
    }

    private static List<Node> fetchNodes(boolean podsAlreadyCreated){
        List<Node> nodes = Collections.emptyList();
        if(podsAlreadyCreated){
            //Get the nodes and available pods without creation
            try {
                nodes = k8S.getSystemSnapshot();
            } catch (ParseException e) {
                e.printStackTrace();
            }
        } else{
            //Create the pods using NodeDataGenerator and return the updated nodes
            nodes = k8S.populateSystem();
        }
        return nodes;
    }

    private static void triggerWorkLoadBalancer(SystemController systemController, int iterations){
        WorkLoadBalancer workLoadBalancer = new WorkLoadBalancerImpl(systemController, 50);
        workLoadBalancer.balance();
    }

    private static void placeMyWorkload(SystemController systemController){
        CapacityPlacementService capacityPlacementService = new CapacityPlacementServiceImpl();

        Capacity toPlace = new Capacity(1100, 200);
        List<MigrationPlanDto> planDtoList = capacityPlacementService.placeMyWorkload(toPlace, systemController.getNodes());
        planDtoList.forEach(migrationPlanDto -> {

            try {
                if ("-1".equals(migrationPlanDto.getPod().getId())){
                    JSONObject readValue = k8S.readPodToBePlaced(migrationPlanDto.getToNode(),toPlace.getCpuMillicore(),toPlace.getMemoryMB());
                    k8S.createPod(migrationPlanDto.getToNode(),readValue);
                }else{
                    k8S.migratePod(migrationPlanDto.getPod().getName(), migrationPlanDto.getToNode());
                }
            } catch (ParseException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }
}
