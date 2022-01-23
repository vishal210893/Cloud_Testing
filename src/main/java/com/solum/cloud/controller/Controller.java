package com.solum.cloud.controller;

import com.google.gson.reflect.TypeToken;
import com.solum.cloud.model.DeploymentInfo;
import com.solum.cloud.model.DeploymentInitial;
import com.solum.cloud.model.PodsInfo;
import io.kubernetes.client.Metrics;
import io.kubernetes.client.custom.*;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.Watch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ArrayList<Object>> printPodName() throws Exception {
        ApiClient client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(KUBE_CONFIG_PATH))).build();
        Configuration.setDefaultApiClient(client);
        Metrics metrics = new Metrics(client);
        CoreV1Api api = new CoreV1Api();

        DeploymentInfo.DeploymentInfoBuilder api_service = DeploymentInfo.builder().name("Api Service").details(new ArrayList<>());
        DeploymentInfo.DeploymentInfoBuilder dashboard = DeploymentInfo.builder().name("Dashboard").details(new ArrayList<>());
        DeploymentInfo.DeploymentInfoBuilder image_generator = DeploymentInfo.builder().name("Image Generator").details(new ArrayList<>());
        DeploymentInfo.DeploymentInfoBuilder lbs = DeploymentInfo.builder().name("Lbs").details(new ArrayList<>());
        DeploymentInfo.DeploymentInfoBuilder ld = DeploymentInfo.builder().name("Ld").details(new ArrayList<>());
        DeploymentInfo.DeploymentInfoBuilder inbound = DeploymentInfo.builder().name("Inbound").details(new ArrayList<>());
        DeploymentInfo.DeploymentInfoBuilder outbound = DeploymentInfo.builder().name("Outbound").details(new ArrayList<>());
        DeploymentInfo.DeploymentInfoBuilder realTime = DeploymentInfo.builder().name("RealTime").details(new ArrayList<>());
        DeploymentInfo.DeploymentInfoBuilder scheduler = DeploymentInfo.builder().name("Scheduler").details(new ArrayList<>());


        V1PodList list = api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, 20, null);
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
                    api_service.build().getDetails().add(podsInfo);
                    break;
                case DASHBOARD:
                    podsInfo = getPodsInfo(metrics, item, podsFullName);
                    dashboard.build().getDetails().add(podsInfo);
                    break;
                case IMGGENERATOR:
                    podsInfo = getPodsInfo(metrics, item, podsFullName);
                    image_generator.build().getDetails().add(podsInfo);
                    break;
                case INBOUND:
                    podsInfo = getPodsInfo(metrics, item, podsFullName);
                    inbound.build().getDetails().add(podsInfo);
                    break;
                case LBS:
                    podsInfo = getPodsInfo(metrics, item, podsFullName);
                    lbs.build().getDetails().add(podsInfo);
                    break;
                case LD:
                    podsInfo = getPodsInfo(metrics, item, podsFullName);
                    ld.build().getDetails().add(podsInfo);
                    break;
                case OUTBOUND:
                    podsInfo = getPodsInfo(metrics, item, podsFullName);
                    outbound.build().getDetails().add(podsInfo);
                    break;
                case REALTIME:
                    podsInfo = getPodsInfo(metrics, item, podsFullName);
                    realTime.build().getDetails().add(podsInfo);
                    break;
                case SCHEDULER:
                    podsInfo = getPodsInfo(metrics, item, podsFullName);
                    scheduler.build().getDetails().add(podsInfo);
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
    }

    private PodsInfo getPodsInfo(Metrics metrics, V1Pod item, String podsFullName) throws ApiException {
        PodsInfo podsInfo = new PodsInfo();
        podsInfo.setInstanceId(podsFullName);
        podsInfo.setStatus(item.getStatus().getPhase());
        podsInfo.setCreationTime(item.getMetadata().getCreationTimestamp().toLocalDateTime());
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
                    podsInfo.setCpu(container.getUsage().get("cpu").toString());
                    podsInfo.setMemory(container.getUsage().get("memory").toString());
                }
            }
        }
        return podsInfo;
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
                    System.out.println("\t" + item.getUsage().get(key));
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

    @GetMapping(value = "/heartbeat")
    public ResponseEntity heartbeat() {
        log.info("Heartbeat status 200 OK");
        return new ResponseEntity(HttpStatus.OK);
    }

}
