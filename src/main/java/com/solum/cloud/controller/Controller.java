package com.solum.cloud.controller;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.Metrics;
import io.kubernetes.client.custom.ContainerMetrics;
import io.kubernetes.client.custom.NodeMetrics;
import io.kubernetes.client.custom.NodeMetricsList;
import io.kubernetes.client.custom.PodMetrics;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.Watch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;

@Slf4j
@RestController
public class Controller {

    private static final String kubeConfigPath = "/home/vishal/Desktop/config";
    private static final boolean local = true;

    @PostMapping("/printjson")
    public ResponseEntity<String> printResponse(@RequestBody String jsonText) {
        log.info("received json data, length = {}", jsonText.length());
        return ResponseEntity.ok().body("request processed");
    }

    @GetMapping("/getpodsinfo")
    public ResponseEntity<ArrayList<String>> printPodName() throws Exception {
        ApiClient client = null;
        if (local) {
            client = Config.defaultClient();
        } else {
            client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();
        }
        Configuration.setDefaultApiClient(client);
        CoreV1Api api = new CoreV1Api();
        V1PodList list = api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, 20, null);
        ArrayList<String> ar = new ArrayList<>();
        for (V1Pod item : list.getItems()) {
            ar.add(item.getMetadata().getName());
        }
        return ResponseEntity.ok().body(ar);
    }

    @GetMapping("/node")
    public void printNodeDetails() throws Exception {
        ApiClient client = null;
        if (local) {
            client = Config.defaultClient();
        } else {
            client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();
        }
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

            for (PodMetrics item : metrics.getPodMetrics("default").getItems()) {
                System.out.println(item.getMetadata().getName());
                System.out.println("------------------------------");
                if (item.getContainers() == null) {
                    continue;
                }
                for (ContainerMetrics container : item.getContainers()) {
                    System.out.println(container.getName());
                    System.out.println("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
                    for (String key : container.getUsage().keySet()) {
                        System.out.println("\t" + key);
                        System.out.println("\t" + container.getUsage().get(key));
                    }
                    System.out.println();
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @GetMapping("/watch")
    public ResponseEntity<HashMap<String, String>> printWatch() throws Exception {
        ApiClient client = null;
        if (local) {
            client = Config.defaultClient();
        } else {
            client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();
        }
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
