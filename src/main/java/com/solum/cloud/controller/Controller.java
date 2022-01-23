package com.solum.cloud.controller;

import com.google.gson.reflect.TypeToken;
import com.solum.cloud.model.*;
import io.kubernetes.client.Metrics;
import io.kubernetes.client.custom.*;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AutoscalingV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.Watch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;

@Slf4j
@RestController
public class Controller {

    private static final String KUBE_CONFIG_PATH = "C:/Users/SolumTravel/Desktop/common00config";

    @GetMapping("/getpodsinfo")
    public ResponseEntity<Object> printPodName() {
        try {
            ApiClient client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(KUBE_CONFIG_PATH))).build();
            Configuration.setDefaultApiClient(client);
            Metrics metrics = new Metrics(client);
            CoreV1Api api = new CoreV1Api();

            DeploymentInfo.DeploymentInfoBuilder api_service = DeploymentInfo.builder().name("Api Service").details(new ArrayList<>()).resources(new HashMap<>());
            DeploymentInfo.DeploymentInfoBuilder dashboard = DeploymentInfo.builder().name("Dashboard").details(new ArrayList<>()).resources(new HashMap<>());
            DeploymentInfo.DeploymentInfoBuilder image_generator = DeploymentInfo.builder().name("Image Generator").details(new ArrayList<>()).resources(new HashMap<>());
            DeploymentInfo.DeploymentInfoBuilder lbs = DeploymentInfo.builder().name("Lbs").details(new ArrayList<>()).resources(new HashMap<>());
            DeploymentInfo.DeploymentInfoBuilder ld = DeploymentInfo.builder().name("Ld").details(new ArrayList<>()).resources(new HashMap<>());
            DeploymentInfo.DeploymentInfoBuilder inbound = DeploymentInfo.builder().name("Inbound").details(new ArrayList<>()).resources(new HashMap<>());
            DeploymentInfo.DeploymentInfoBuilder outbound = DeploymentInfo.builder().name("Outbound").details(new ArrayList<>()).resources(new HashMap<>());
            DeploymentInfo.DeploymentInfoBuilder realTime = DeploymentInfo.builder().name("RealTime").details(new ArrayList<>()).resources(new HashMap<>());
            DeploymentInfo.DeploymentInfoBuilder scheduler = DeploymentInfo.builder().name("Scheduler").details(new ArrayList<>()).resources(new HashMap<>());


            V1PodList list = api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, 60, null);
            for (V1Pod item : list.getItems()) {
                final V1ObjectMeta metadata = item.getMetadata();
                final String podsFullName = metadata.getName();
                final String podName = podsFullName.substring(0, podsFullName.indexOf("-"));
                if (!EnumUtils.isValidEnum(DeploymentInitial.class, podName.toUpperCase()) || podsFullName.contains("dashboard-metrics")) {
                    continue;
                }
                final DeploymentInitial deploymentInitial = DeploymentInitial.valueOf(podName.toUpperCase());
                PodsInfo podsInfo;
                switch (deploymentInitial) {
                    case APISERVICE:
                        podsInfo = getPodsInfo(metrics, item, podsFullName);
                        createDeploymentInfo(api_service, item, podsInfo);
                        break;
                    case DASHBOARD:
                        podsInfo = getPodsInfo(metrics, item, podsFullName);
                        createDeploymentInfo(dashboard, item, podsInfo);
                        break;
                    case IMGGENERATOR:
                        podsInfo = getPodsInfo(metrics, item, podsFullName);
                        createDeploymentInfo(image_generator, item, podsInfo);
                        break;
                    case INBOUND:
                        podsInfo = getPodsInfo(metrics, item, podsFullName);
                        createDeploymentInfo(inbound, item, podsInfo);
                        break;
                    case LBS:
                        podsInfo = getPodsInfo(metrics, item, podsFullName);
                        createDeploymentInfo(lbs, item, podsInfo);
                        break;
                    case LD:
                        podsInfo = getPodsInfo(metrics, item, podsFullName);
                        createDeploymentInfo(ld, item, podsInfo);
                        break;
                    case OUTBOUND:
                        podsInfo = getPodsInfo(metrics, item, podsFullName);
                        createDeploymentInfo(outbound, item, podsInfo);
                        break;
                    case REALTIME:
                        podsInfo = getPodsInfo(metrics, item, podsFullName);
                        createDeploymentInfo(realTime, item, podsInfo);
                        break;
                    case SCHEDULER:
                        podsInfo = getPodsInfo(metrics, item, podsFullName);
                        createDeploymentInfo(scheduler, item, podsInfo);
                        break;
                    case FLUENTD:
                        break;
                }
            }

            ArrayList<Object> environmentMsInfo = new ArrayList<>();
            environmentMsInfo.add(api_service.replicas(api_service.build().getDetails().size()).build());
            environmentMsInfo.add(dashboard.replicas(dashboard.build().getDetails().size()).build());
            environmentMsInfo.add(image_generator.replicas(image_generator.build().getDetails().size()).build());
            environmentMsInfo.add(lbs.replicas(lbs.build().getDetails().size()).build());
            environmentMsInfo.add(ld.replicas(ld.build().getDetails().size()).build());
            environmentMsInfo.add(inbound.replicas(inbound.build().getDetails().size()).build());
            environmentMsInfo.add(outbound.replicas(outbound.build().getDetails().size()).build());
            environmentMsInfo.add(realTime.replicas(realTime.build().getDetails().size()).build());
            environmentMsInfo.add(scheduler.replicas(scheduler.build().getDetails().size()).build());
            return ResponseEntity.ok().body(environmentMsInfo);
        } catch (Exception e) {
            return ResponseEntity.ok().body(ExceptionUtils.getStackTrace(e));
        }
    }

    private void createDeploymentInfo(DeploymentInfo.DeploymentInfoBuilder deploymentInfoBuilder, V1Pod item, PodsInfo podsInfo) {
        if (!StringUtils.hasText(deploymentInfoBuilder.build().getContainerName())) {
            deploymentInfoBuilder.imageName(item.getStatus().getContainerStatuses().get(0).getImage());
            deploymentInfoBuilder.containerName(item.getStatus().getContainerStatuses().get(0).getName());
            try {
                DeploymentResources limits = new DeploymentResources();
                limits.setCpu(item.getSpec().getContainers().get(0).getResources().getLimits().get("cpu").toSuffixedString());
                limits.setMemory(item.getSpec().getContainers().get(0).getResources().getLimits().get("memory").toSuffixedString());
                deploymentInfoBuilder.build().getResources().put("limits", limits);

                DeploymentResources requests = new DeploymentResources();
                requests.setCpu(item.getSpec().getContainers().get(0).getResources().getRequests().get("cpu").toSuffixedString());
                requests.setMemory(item.getSpec().getContainers().get(0).getResources().getRequests().get("memory").toSuffixedString());
                deploymentInfoBuilder.build().getResources().put("requests", requests);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                deploymentInfoBuilder.resources(null);
            }
            final V1HorizontalPodAutoscaler v1Hpa = getAutoScalingInfo(podsInfo);
            if (v1Hpa != null) {
                AutoScalingInfo autoScalingInfo = new AutoScalingInfo();
                autoScalingInfo.setMinPods(v1Hpa.getSpec().getMinReplicas());
                autoScalingInfo.setMaxPods(v1Hpa.getSpec().getMaxReplicas());
                autoScalingInfo.setTargetCPUUtilizationPercentage(v1Hpa.getSpec().getTargetCPUUtilizationPercentage());
                autoScalingInfo.setCurrentCPUUtilizationPercentage(v1Hpa.getStatus().getCurrentCPUUtilizationPercentage());
                autoScalingInfo.setCurrentReplicas(v1Hpa.getStatus().getCurrentReplicas());
                autoScalingInfo.setDesiredReplicas(v1Hpa.getStatus().getDesiredReplicas());
                autoScalingInfo.setLastScaleTime(v1Hpa.getStatus().getLastScaleTime());
                deploymentInfoBuilder.autoScalingInfo(autoScalingInfo);
            } else {
                deploymentInfoBuilder.autoScalingInfo(null);
            }
        }
        deploymentInfoBuilder.build().getDetails().add(podsInfo);
    }

    private PodsInfo getPodsInfo(Metrics metrics, V1Pod item, String podsFullName) throws ApiException {
        PodsInfo podsInfo = new PodsInfo();
        podsInfo.setInstanceId(podsFullName);
        podsInfo.setStatus(item.getStatus().getPhase());
        podsInfo.setCreationTime(item.getMetadata().getCreationTimestamp().toLocalDateTime());
        podsInfo.setRestart(item.getStatus().getContainerStatuses().get(0).getRestartCount());
        podsInfo.setNodeName(item.getSpec().getNodeName());
        final PodMetricsList podMetricsList = metrics.getPodMetrics("default");
        podsInfo = getPodsMetrics(podMetricsList, podsInfo);
        return podsInfo;
    }

    private PodsInfo getPodsMetrics(PodMetricsList podMetricsList, PodsInfo podsInfo) {
        for (PodMetrics podMetrics : podMetricsList.getItems()) {
            if (podsInfo.getInstanceId().equals(podMetrics.getMetadata().getName())) {
                if (podMetrics.getContainers() == null) {
                    continue;
                }
                for (ContainerMetrics container : podMetrics.getContainers()) {
                    podsInfo.setCpu(container.getUsage().get("cpu").toSuffixedString());
                    podsInfo.setMemory(container.getUsage().get("memory").toSuffixedString());
                }
            }
        }
        return podsInfo;
    }

    private V1HorizontalPodAutoscaler getAutoScalingInfo(PodsInfo podsInfo) {
        try {
            AutoscalingV1Api autoscalingV1Api = new AutoscalingV1Api();
            final V1HorizontalPodAutoscalerList v1HorizontalPodAutoscalerList = autoscalingV1Api.listHorizontalPodAutoscalerForAllNamespaces(null, null, null, null, null, null, null, null, 60, null);
            for (V1HorizontalPodAutoscaler v1HorizontalPodAutoscaler : v1HorizontalPodAutoscalerList.getItems()) {
                final String hpaFullName = v1HorizontalPodAutoscaler.getMetadata().getName();
                final String hpaDeploymentName = hpaFullName.substring(0, hpaFullName.lastIndexOf("-"));
                if (podsInfo.getInstanceId().contains(hpaDeploymentName)) {
                    return v1HorizontalPodAutoscaler;
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    @GetMapping("/node")
    public void printNodeDetails() throws Exception {
        ApiClient client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(KUBE_CONFIG_PATH))).build();
        Configuration.setDefaultApiClient(client);
        try {
            Metrics metrics = new Metrics(client);
            NodeMetricsList list = metrics.getNodeMetrics();
            for (NodeMetrics item : list.getItems()) {
                System.out.println(item.getMetadata().getName());
                System.out.println("------------------------------");
                for (String key : item.getUsage().keySet()) {
                    System.out.println("\t" + key);
                    System.out.println("\t" + item.getUsage().get(key).toSuffixedString());
                }
                System.out.println();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @GetMapping("/watch")
    public ResponseEntity<HashMap<String, String>> printWatch() throws Exception {
        ApiClient client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(KUBE_CONFIG_PATH))).build();
        Configuration.setDefaultApiClient(client);
        HashMap<String, String> hm = new HashMap<>();
        try {
            CoreV1Api api = new CoreV1Api();
            Watch<V1Namespace> watch = Watch.createWatch(
                    client,
                    api.listNamespaceCall(null, null, null, null, null, 5, null, null, 20, Boolean.TRUE, null),
                    new TypeToken<Watch.Response<V1Namespace>>() {
                    }.getType());

            for (Watch.Response<V1Namespace> item : watch) {
                hm.put(item.type, item.object.getMetadata().getName());
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return ResponseEntity.ok().body(hm);
    }

}
