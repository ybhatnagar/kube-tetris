package com.kubetetris;

import com.kubetetris.balancer.WorkLoadBalancer;
import com.kubetetris.balancer.WorkLoadBalancerImpl;
import com.kubetetris.interaction.KubernetesAccessor;
import com.kubetetris.interaction.KubernetesAccessorImplV2;
import com.kubetetris.scheduler.CapacityPlacementService;
import com.kubetetris.scheduler.CapacityPlacementServiceImpl;
import com.kubetetris.scheduler.MigrationPlanDto;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

import static org.springframework.core.Ordered.LOWEST_PRECEDENCE;

@SpringBootApplication(scanBasePackages = "com.kubetetris")
@Import({Config.class})
@Slf4j
public class TetrisApplication implements ApplicationRunner{

    private static String ITERATIONS = "iterations";
    private static String BALANCE = "balance";
    private static String PLACE = "place";
    private static String POD = "pod";
    private static Integer DEFAULT_BALANCER_ITERATIONS = 5;

    @Autowired
    KubernetesAccessorImplV2 k8S;

    @Autowired
    SystemController systemController;

    @Autowired
    ApplicationContext ctx;

    @Autowired
    WorkLoadBalancer workLoadBalancer;

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext run = SpringApplication.run(TetrisApplication.class, args);

    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

        log.info("Refreshing the state of the kubernetes cluster");

        List<Node> inputNodes = fetchNodes();

        //Update the system snapshot with latest nodes update with pods
        inputNodes.forEach(node -> systemController.addNode(node));

        if(args.containsOption(PLACE)){
            if(!args.containsOption(POD) || args.getOptionValues(POD)==null ||args.getOptionValues(POD).isEmpty()){
                throw new IllegalArgumentException("Workload Name not provided, please provide the pending podName that has to be placed");
            }
            placeMyWorkload(systemController,args.getOptionValues(POD).get(0));
        }

        if(args.containsOption(BALANCE)){
            triggerWorkLoadBalancer(systemController, getIterations(args));
        }

        SpringApplication.exit(ctx, () -> 0);

    }

    @Bean
    public int getIterations(ApplicationArguments args){
        if(args.containsOption(ITERATIONS) && args.getOptionValues(ITERATIONS) !=null && !args.getOptionValues(ITERATIONS).isEmpty()){
            try{
                return Integer.valueOf(args.getOptionValues(ITERATIONS).get(0));
            } catch (NumberFormatException ex){
                log.info("Incorrect value provided for Banalcing Interations. Defaulting to {}", DEFAULT_BALANCER_ITERATIONS);
                return  DEFAULT_BALANCER_ITERATIONS;
            }
        } else{
            log.info("Number of iterations not provided. Defaulting to {}", DEFAULT_BALANCER_ITERATIONS);
            return DEFAULT_BALANCER_ITERATIONS;
        }
    }


    private List<Node> fetchNodes() throws Exception {
        List<Node> nodes = Collections.emptyList();
        return  k8S.getSystemSnapshot();
    }

    private void triggerWorkLoadBalancer(SystemController systemController, int iterations){

        System.out.println("***********************************************************************************");
        System.out.println("Trying to balance the cpu/memory consumption across nodes");
        System.out.println("***********************************************************************************");


        workLoadBalancer.balance();
    }

    private void placeMyWorkload(SystemController systemController, String podName) throws Exception{

        V1Pod pod = k8S.getPod(podName);

        CapacityPlacementService capacityPlacementService = new CapacityPlacementServiceImpl();

        Capacity toPlace = new Capacity(k8S.getPodRequestForResource("cpu",pod), k8S.getPodRequestForResource("memory",pod));

        List<MigrationPlanDto> planDtoList = capacityPlacementService.placeMyWorkload(toPlace, systemController.getNodes());
        planDtoList.forEach(migrationPlanDto -> {

            try {
                if ("-1".equals(migrationPlanDto.getPod().getId())){
                    k8S.createPod(migrationPlanDto.getToNode(),pod);
                }else{
                    k8S.migratePod(migrationPlanDto.getPod().getName(), migrationPlanDto.getToNode());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
