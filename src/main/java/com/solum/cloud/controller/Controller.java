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
import java.util.*;

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

            LinkedHashMap<String, DeploymentInfo.DeploymentInfoBuilder> deploymentInfoBuilderLinkedHashMap = new LinkedHashMap<>();

            V1PodList list = api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, 60, null);
            for (V1Pod item : list.getItems()) {
                final V1ObjectMeta metadata = item.getMetadata();
                final String podsFullName = metadata.getName();
                final String podName = podsFullName.substring(0, podsFullName.indexOf("-"));
                if (!EnumUtils.isValidEnum(DeploymentInitial.class, podName.toUpperCase()) || podsFullName.contains("dashboard-metrics")) {
                    continue;
                }
                PodsInfo podsInfo = getPodsInfo(metrics, item, podsFullName);
                final DeploymentInfo.DeploymentInfoBuilder deploymentInfoBuilder;
                if (deploymentInfoBuilderLinkedHashMap.containsKey(item.getStatus().getContainerStatuses().get(0).getName())) {
                    deploymentInfoBuilder = deploymentInfoBuilderLinkedHashMap.get(item.getStatus().getContainerStatuses().get(0).getName());
                } else {
                    final String name = item.getStatus().getContainerStatuses().get(0).getName();
                    deploymentInfoBuilderLinkedHashMap.put(name, DeploymentInfo.builder().name(name).details(new ArrayList<>()).resources(new HashMap<>()));
                    deploymentInfoBuilder = deploymentInfoBuilderLinkedHashMap.get(name);
                }
                createDeploymentInfo(deploymentInfoBuilder, item, podsInfo);
            }

            ArrayList<Object> environmentMsInfo = new ArrayList<>();
            deploymentInfoBuilderLinkedHashMap.forEach((k, v) -> {
                environmentMsInfo.add(v.replicas(v.build().getDetails().size()).build());
            });

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
