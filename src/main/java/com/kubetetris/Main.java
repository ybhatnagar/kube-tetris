package com.kubetetris;

import java.util.Collections;
import java.util.List;

import com.kubetetris.balancer.WorkLoadBalancer;
import com.kubetetris.balancer.WorkLoadBalancerImpl;
import com.kubetetris.cli.CliArgs;
import com.kubetetris.interaction.KubernetesAccessor;
import com.kubetetris.interaction.KubernetesAccessorImpl;
import com.kubetetris.scheduler.CapacityPlacementService;
import com.kubetetris.scheduler.CapacityPlacementServiceImpl;
import com.kubetetris.scheduler.MigrationPlanDto;
import lombok.extern.slf4j.Slf4j;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

@Slf4j
public class Main {

    private static KubernetesAccessor k8S;

    public static void main(String[] args) throws InterruptedException {

        CliArgs cliArgs = new CliArgs(args);

        //Create SystemController and nodes and pods
        SystemController systemController = new SystemControllerImpl();

        //Fetch Nodes from K8S
        k8S = new KubernetesAccessorImpl();

        //If the command line arguments contains the -podsAlreadyCreated switch anywhere, the switchPresent() method will return true.
        //If not, the switchPresent() method will return false.
        boolean podsAlreadyCreated = cliArgs.switchPresent("-podsAlreadyCreated");
        List<Node> inputNodes = fetchNodes(true);

        //Update the system snapshot with latest nodes update with pods
        inputNodes.forEach(node -> systemController.addNode(node));

        //placeMyWorkload(systemController);

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

        boolean cleanSystem = cliArgs.switchPresent("-cleanSystem");
        if(cleanSystem)
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
        WorkLoadBalancer workLoadBalancer = new WorkLoadBalancerImpl(systemController, 1);
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
