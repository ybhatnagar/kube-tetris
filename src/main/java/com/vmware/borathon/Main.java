package com.vmware.borathon;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import com.vmware.borathon.interaction.KubernetesAccessor;
import com.vmware.borathon.interaction.KubernetesAccessorImpl;
import com.vmware.borathon.scheduler.CapacityPlacementService;
import com.vmware.borathon.scheduler.CapacityPlacementServiceImpl;
import com.vmware.borathon.scheduler.MigrationPlanDto;
import lombok.extern.slf4j.Slf4j;

import com.vmware.borathon.balancer.WorkLoadBalancer;
import com.vmware.borathon.balancer.WorkLoadBalancerImpl;
import com.vmware.borathon.loadsimulator.NodeDataGenerator;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

@Slf4j
public class Main {
    private static KubernetesAccessor k8S;

    public static void main(String[] args) {
        //Create SystemController and nodes and pods
        SystemController systemController = new SystemControllerImpl();

        //Fetch Nodes from K8S
        k8S = new KubernetesAccessorImpl();
        List<Node> inputNodes = null;
        try {
            inputNodes = k8S.getSystemSnapshot();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        //List<Node> inputNodes = NodeDataGenerator.generate(10, 300);
        inputNodes.forEach(node -> systemController.addNode(node));
        triggerWorkLoadBalancer(systemController, 50);
        placeMyWorkload(systemController);
    }


    private static void triggerWorkLoadBalancer(SystemController systemController, int iterations){
        WorkLoadBalancer workLoadBalancer = new WorkLoadBalancerImpl(systemController, 50);
        workLoadBalancer.balance();
    }

    private static void placeMyWorkload(SystemController systemController){
        CapacityPlacementService capacityPlacementService = new CapacityPlacementServiceImpl();

        Capacity toPlace = new Capacity(1100, 200);
        List<MigrationPlanDto> planDtoList = capacityPlacementService.placeMyWorkload(toPlace, systemController.getNodes());
        System.out.println(planDtoList);
        planDtoList.forEach(migrationPlanDto -> {

            try {
                if ("-1".equals(migrationPlanDto.getPod().getId())){
                    JSONObject readValue = readPodToBePlaced(migrationPlanDto.getToNode(),toPlace.getCpuMillicore(),toPlace.getMemoryMB());
                    k8S.createPod(migrationPlanDto.getToNode(),migrationPlanDto.getPod().getName(),readValue);
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



    private static JSONObject readPodToBePlaced(String nodeName, long cpu, long memoryMB){
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

    private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static String randomAlpha(int count) {
        StringBuilder builder = new StringBuilder();
        while (count-- != 0) {
            int character = (int)(Math.random()*ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }
}
