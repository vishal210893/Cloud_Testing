package com.solum.cloud.controller;

import com.azure.containers.containerregistry.ContainerRegistryClient;
import com.azure.containers.containerregistry.ContainerRegistryClientBuilder;
import com.azure.containers.containerregistry.ContainerRegistryServiceVersion;
import com.azure.containers.containerregistry.implementation.authentication.ContainerRegistryTokenService;
import com.azure.containers.containerregistry.models.ContainerRegistryAudience;
import com.azure.core.credential.AccessToken;
import com.azure.core.credential.BasicAuthenticationCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.solum.cloud.model.*;
import io.kubernetes.client.Copy;
import io.kubernetes.client.Metrics;
import io.kubernetes.client.custom.*;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.AutoscalingV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Response;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
public class Controller {

    private static final String KUBE_CONFIG_PATH = "C:/Users/SolumTravel/Desktop/config";
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");
    public static final String DEFAULT_NAMESPACE = "default";
    private final RestTemplate restTemplate;

    public Controller(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    @GetMapping(value = "/getpodsinfo", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> printPodName() {
        try {
            ApiClient client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(KUBE_CONFIG_PATH))).build();
            client.setDebugging(true);
            client.setConnectTimeout(300000);
            client.setReadTimeout(300000);

            Configuration.setDefaultApiClient(client);
            Metrics metrics = new Metrics(client);
            CoreV1Api api = new CoreV1Api();

            LinkedHashMap<String, DeploymentInfo.DeploymentInfoBuilder> deploymentInfoBuilderLinkedHashMap = new LinkedHashMap<>();

            V1PodList list = api.listPodForAllNamespaces(null, null, null, null, null, "true", null, null, 600, null);
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
            log.error(e.getMessage(), e);
            return ResponseEntity.ok().body(ExceptionUtils.getStackTrace(e));
        }
    }

    @GetMapping("/restart")
    public ResponseEntity<String> restartDeployment(@RequestParam(name = "service") String serviceName) {
        try {
            ApiClient client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(KUBE_CONFIG_PATH))).build();
            //client.setDebugging(true);
            Configuration.setDefaultApiClient(client);
            AppsV1Api appsV1Api = new AppsV1Api();
            final V1DeploymentList v1DeploymentList = appsV1Api.listDeploymentForAllNamespaces(null, null, null, null, null, null, null, null, 60, null);
            for (V1Deployment v1Deployment : v1DeploymentList.getItems()) {
                if (v1Deployment.getMetadata().getName().contains(serviceName)) {
                    final V1Status v1Status = appsV1Api.deleteNamespacedDeployment(v1Deployment.getMetadata().getName(), DEFAULT_NAMESPACE, "true", null, null, false, null, null);
                    if (v1Status.getStatus().equals("Success")) {
                        log.info("Deployment {} deleted successfully", v1Deployment.getMetadata().getName());
                        v1Deployment.getMetadata().setResourceVersion(null);
                        final V1Deployment namespacedDeployment = appsV1Api.createNamespacedDeployment(DEFAULT_NAMESPACE, v1Deployment, "true", null, "LBS api to restart pods");
                        log.info("New deployment {} started successfully, Deployment Info = \n{}", v1Deployment.getMetadata().getName(), namespacedDeployment.toString());
                        return ResponseEntity.ok().body(v1Deployment.getMetadata().getName() + " restarted successfully");
                    } else {
                        log.error("Deployment = {} failed to delete, Status = {}", v1Deployment.getMetadata().getName(), v1Status.getStatus());
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(ExceptionUtils.getStackTrace(e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>("Failed to restart " + serviceName, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @GetMapping(value = "/log/{pod}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> log(@PathVariable String pod) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ApiClient client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(KUBE_CONFIG_PATH))).build();
            Configuration.setDefaultApiClient(client);
            CoreV1Api coreApi = new CoreV1Api(client);
            V1PodList list = coreApi.listPodForAllNamespaces(null, null, null, null, null, "true", null, null, 60, null);
            for (V1Pod item : list.getItems()) {
                final V1ObjectMeta metadata = item.getMetadata();
                final String podsFullName = metadata.getName();
                if (!podsFullName.equalsIgnoreCase(pod)) {
                    continue;
                }
                final Call call = coreApi.readNamespacedPodLogCall(item.getMetadata().getName(),
                        "default",
                        item.getSpec().getContainers().get(0).getName(),
                        false,
                        null,
                        null,
                        "false",
                        false,
                        null, null, false, null);
                Response response = call.execute();
                if (!response.isSuccessful()) {
                    throw new ApiException(response.code(), "Logs request failed: " + response.code());
                }
                final InputStream is = response.body().byteStream();
                final byte[] bytes = IOUtils.toByteArray(is);
                ZipOutputStream zos = new ZipOutputStream(baos);
                ZipEntry entry = new ZipEntry(pod + "-" + LocalDateTime.now() + ".log");
                zos.putNextEntry(entry);
                zos.write(bytes);
                zos.close();
                is.close();


                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType("application/zip"));
                headers.setContentDispositionFormData("logFile", pod + "-log.zip");
                headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
                log.info("log file successfully transferred for pod {}", pod);
                return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(ExceptionUtils.getStackTrace(e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>("Failed to retrieve logs for pod " + pod, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @GetMapping("/listacr")
    public ResponseEntity<String> listacr() {
        try {
            BasicAuthenticationCredential defaultCredential = new BasicAuthenticationCredential("solumACRms", "wm5kOF1HaXtHEIIu2/UjlZvfTc3cG67/");
            /*ContainerRegistryClient client = new ContainerRegistryClientBuilder()
                    .endpoint("https://solumacrendpointtest.azurecr.io")
                    .audience(ContainerRegistryAudience.AZURE_RESOURCE_MANAGER_PUBLIC_CLOUD)
                    .credential(defaultCredential)
                    .buildClient();*/

            final ContainerRegistryTokenService containerRegistryTokenService = new ContainerRegistryTokenService(defaultCredential, ContainerRegistryAudience.AZURE_RESOURCE_MANAGER_PUBLIC_CLOUD,
                    "https://solumacrms.azurecr.io", ContainerRegistryServiceVersion.V2021_07_01, null, null);
            ContainerRegistryClient client = new ContainerRegistryClientBuilder()
                    .endpoint("https://solumacrms.azurecr.io")
                    .audience(ContainerRegistryAudience.AZURE_RESOURCE_MANAGER_PUBLIC_CLOUD)
                    .credential(new TokenCredential() {
                        @Override
                        public Mono<AccessToken> getToken(TokenRequestContext request) {
                            return Mono.just(new AccessToken("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IlVWVzc6T1JDQjozVkNIOjY2UEI6R1kyWjpOU01KOldIVVE6Qjc3WDpLWTdaOlRaU1A6UkZZWDpIVTdRIn0.eyJqdGkiOiI3YzE1MjhjZi05ZjM5LTQ0YTUtYjVmMS1jMjZkZDNlYzNjNmQiLCJzdWIiOiJzb2x1bUFDUm1zIiwibmJmIjoxNjQzODIzNTM1LCJleHAiOjE2NDM4MjgwMzUsImlhdCI6MTY0MzgyMzUzNSwiaXNzIjoiQXp1cmUgQ29udGFpbmVyIFJlZ2lzdHJ5IiwiYXVkIjoic29sdW1hY3Jtcy5henVyZWNyLmlvIiwidmVyc2lvbiI6IjEuMCIsInJpZCI6ImIyMTg0M2M4ZDhjODQxOTViZWY3NWM4MjhiZTBiMzNlIiwiYWNjZXNzIjpbeyJUeXBlIjoicmVwb3NpdG9yeSIsIk5hbWUiOiIqIiwiQWN0aW9ucyI6WyIqIl19XSwicm9sZXMiOlsiT3duZXIiXSwiZ3JhbnRfdHlwZSI6ImFjY2Vzc190b2tlbiJ9.evNXds2qc9ECXBv0pCuROgFvOUonhdRHAcJKHpPw47snjO8pUVw2Csvyz_UrWzLjS-zcpmnjklKJ4r31POzHmpGzAPVOhDJdhHl5S3gnTNbyJ79QhjLo53YaIIvttp6Fi4yLdZTorvDekowzOSG2_LyGctHdv8azbb_Xz3P-keJPQ-JwoLlRlCvIIySTZw0Qxgqf3K3BFXdgrhwmTfgxzCURoT2zUuFFnYlLUm-GQJ3yStZrqmGVLKQYFg1uxOnqmh7fYPumuztg_2exmbrAoTWhb1jXVzCsuK4Yf2nDIhCYi6lFgrVSAtzdNnsQzGeVM_bGKK3GteoS9qUIe9nLBg",
                                    OffsetDateTime.MAX));
                        }
                    })
                    .buildClient();
            client.listRepositoryNames().forEach(repository -> System.out.println(repository));

            /*RegistryArtifact image = client.getArtifact("cloud_test_img", "digest");
            PagedIterable<ArtifactTagProperties> tags = image.listTagProperties();

            System.out.printf(String.format("%s has the following aliases:", image.getFullyQualifiedReference()));

            for (ArtifactTagProperties tag : tags) {
                System.out.printf(String.format("%s/%s:%s", client.getEndpoint(), "solumACREndpointTest", tag.getName()));
            }*/
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(ExceptionUtils.getStackTrace(e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>("Failed to get details", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @GetMapping("/deploy/{service}")
    public ResponseEntity<String> doSingleDeployment(@PathVariable(name = "service") String serviceName) {
        try {
            ApiClient client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(KUBE_CONFIG_PATH))).build();
            //client.setDebugging(true);
            Configuration.setDefaultApiClient(client);
            AppsV1Api appsV1Api = new AppsV1Api();
            final V1DeploymentList v1DeploymentList = appsV1Api.listDeploymentForAllNamespaces(null, null, null, null, null, null, null, null, 60, null);
            for (V1Deployment v1Deployment : v1DeploymentList.getItems()) {
                System.out.println(v1Deployment.getMetadata().getName());
                if (true) continue;
                if (serviceName.equalsIgnoreCase(DeploymentInitial.DASHBOARD.name())) {
                    serviceName = serviceName + "-deployment";
                }
                if (v1Deployment.getMetadata().getName().contains(serviceName)) {
                    final String fullImageName = v1Deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
                    final String[] split = fullImageName.split("/");
                    final String acrName = split[0];
                    final String imageNameWithTag = split[1];
                    final String[] splitImageAndTag = imageNameWithTag.split(":");
                    final String imageName = splitImageAndTag[0];
                    final Integer tag = Integer.valueOf(splitImageAndTag[1]);
                    int latestTag = getLatestTagForGivenImage(acrName, imageName);
                    if (latestTag > tag) {
                        boolean apiRestarted;
                        if (serviceName.equalsIgnoreCase(DeploymentInitial.DASHBOARD.name().toLowerCase() + "-deployment")) {
                            apiRestarted = updateApiServiceYamlAndRestart(latestTag);
                        } else {
                            apiRestarted = true;
                        }
                        if (apiRestarted) {
                            final V1Status v1Status = appsV1Api.deleteNamespacedDeployment(v1Deployment.getMetadata().getName(), DEFAULT_NAMESPACE, "true", null, null, false, null, null);
                            if (v1Status.getStatus().equals("Success")) {
                                log.info("Deployment {} deleted successfully for acr image {}", v1Deployment.getMetadata().getName(), fullImageName);
                                final String newImage = acrName + "/" + imageName + ":" + latestTag;
                                updateDeploymentInfo(v1Deployment, latestTag, newImage);
                                appsV1Api.createNamespacedDeployment(DEFAULT_NAMESPACE, v1Deployment, "true", null, "LBS api to restart IG for font upload process");
                                log.info("New deployment {} created successfully, for acr image = {} \n", v1Deployment.getMetadata().getName(), newImage);
                                return ResponseEntity.ok().body("Acr image = " + newImage + " successfully deployed for service " + serviceName);
                            }
                        }
                    } else {
                        log.warn("acr image {} already deployed for service {}", fullImageName, serviceName);
                        return new ResponseEntity<>("acr image " + fullImageName + " already deployed for service " + serviceName, HttpStatus.NOT_ACCEPTABLE);
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>("Failed to update deployment " + serviceName + "\n" +
                    ExceptionUtils.getStackTrace(e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>("Failed to update deployment " + serviceName, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @GetMapping(value = "/copy/log", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> copylog(@RequestParam(name = "podName") String pod, @RequestParam(name = "srcPath") String srcPath) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ApiClient client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(KUBE_CONFIG_PATH))).build();
            Configuration.setDefaultApiClient(client);
            CoreV1Api coreApi = new CoreV1Api(client);
            V1PodList list = coreApi.listPodForAllNamespaces(null, null, null, null, null, "true", null, null, 60, null);
            for (V1Pod item : list.getItems()) {
                System.out.println(item);
                final V1ObjectMeta metadata = item.getMetadata();
                final String podsFullName = metadata.getName();
                if (!podsFullName.equalsIgnoreCase(pod)) {
                    continue;
                }

                String folderName = "D:" + "/logtmp";
                FileUtils.forceMkdir(new File(folderName));

                Copy copy = new Copy();
                copy.copyDirectoryFromPod(item, srcPath, Paths.get(folderName));
                File fileToZip = new File(folderName);
                ZipOutputStream zos = new ZipOutputStream(baos);
                zipFile(fileToZip, fileToZip.getName(), zos);
                zos.close();

                FileUtils.deleteDirectory(fileToZip);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType("application/zip"));
                headers.setContentDispositionFormData("logFile", pod + "-log.zip");
                headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
                log.info("log file successfully transferred for pod {}", pod);
                return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(ExceptionUtils.getStackTrace(e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>("Failed to retrieve logs for pod " + pod, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @GetMapping(value = "/push/fileToPod", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pushFile(@RequestParam(name = "podName") String pod,
                                      @RequestParam(name = "srcPath") String srcPath,
                                      @RequestParam(name = "css") MultipartFile xsl) {
        try {
            ApiClient client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(KUBE_CONFIG_PATH))).build();
            Configuration.setDefaultApiClient(client);
            CoreV1Api coreApi = new CoreV1Api(client);
            V1PodList list = coreApi.listPodForAllNamespaces(null, null, null, null, null, "true", null, null, 60, null);
            for (V1Pod item : list.getItems()) {
                System.out.println(item);
                final V1ObjectMeta metadata = item.getMetadata();
                final String podsFullName = metadata.getName();
                if (!podsFullName.equalsIgnoreCase(pod)) {
                    continue;
                }
                Copy copy = new Copy();
                copy.copyFileToPodAsync(DEFAULT_NAMESPACE, pod, item.getSpec().getContainers().get(0).getName(), xsl.getBytes(), Paths.get(srcPath));
                return new ResponseEntity<>("pushed new file", HttpStatus.OK);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>(ExceptionUtils.getStackTrace(e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>("Failed to push file in pod " + pod, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @GetMapping("/getNodeInfo")
    public ResponseEntity<?> printNodeDetails() {
        try {
            ApiClient client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(KUBE_CONFIG_PATH))).build();
            Configuration.setDefaultApiClient(client);
            CoreV1Api api = new CoreV1Api();
            final V1NodeList v1NodeList = api.listNode(null, null, null, null, null, null, null, null, 600, null);
            ArrayList<NodeInfo> nodeInfoList = new ArrayList<>();
            v1NodeList.getItems().forEach((node) -> {
                NodeInfo nodeInfo = new NodeInfo();
                nodeInfo.setNodeName(node.getMetadata().getName());
                nodeInfo.setCreationTime(node.getMetadata().getCreationTimestamp().toLocalDateTime());
                final Map<String, String> labels = node.getMetadata().getLabels();
                if (!labels.isEmpty()) {
                    nodeInfo.setClusterName(labels.get("kubernetes.azure.com/cluster"));
                    nodeInfo.setInstanceType(labels.get("beta.kubernetes.io/instance-type"));
                    nodeInfo.setRegion(labels.get("topology.kubernetes.io/region"));
                }
                nodeInfo.setTotalImage(node.getStatus().getImages().size());
                nodeInfo.setNodeSystemInfo(node.getStatus().getNodeInfo());
                try {
                    nodeCpuAndMemoryInfo(client, nodeInfo);
                } catch (ApiException e) {
                    log.error("Unable to get Node metrics");
                }
                nodeInfoList.add(nodeInfo);
            });
            return ResponseEntity.ok().body(nodeInfoList);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>("unable to get Node info \n" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getConfigMap")
    public ResponseEntity<String> getConfigMaps(@RequestParam String service, @RequestParam String propertyName) {
        try {
            String value = null;
            LocalDateTime localDateTime = LocalDateTime.now();
            ApiClient client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(KUBE_CONFIG_PATH))).build();
            Configuration.setDefaultApiClient(client);
            CoreV1Api api = new CoreV1Api();
            final V1ConfigMapList v1ConfigMapList = api.listConfigMapForAllNamespaces(null, null, null, null, null, null, null, null, 60, null);
            for (V1ConfigMap v1ConfigMap : v1ConfigMapList.getItems()) {
                final String configMapFullName = v1ConfigMap.getMetadata().getName();
                if (configMapFullName.contains("-")) {
                    final String name = configMapFullName.substring(0, configMapFullName.indexOf("-"));
                    if (EnumUtils.isValidEnum(ConfigMapInitial.class, name.toUpperCase())) {
                        if (service.equalsIgnoreCase(name)) {
                            value = v1ConfigMap.getData().get(propertyName.concat(".properties"));
                            localDateTime = v1ConfigMap.getMetadata().getCreationTimestamp().toLocalDateTime();
                            break;
                        }
                    }
                }
            }
            if (StringUtils.hasText(value)) {
                HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.set("creationTimestamp", localDateTime.toString());
                return ResponseEntity.ok().headers(responseHeaders).body(value);
            } else {
                return new ResponseEntity<>("No config map info present for service" + service + " and property " + propertyName, HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ResponseEntity<>("unable to get config map info for service" + service + " and property " + propertyName + "\n" +
                    e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void nodeCpuAndMemoryInfo(ApiClient client, NodeInfo nodeInfo) throws ApiException {
        Metrics metrics = new Metrics(client);
        NodeMetricsList list = metrics.getNodeMetrics();
        for (NodeMetrics nodeMetrics : list.getItems()) {
            if (nodeInfo.getNodeName().equals(nodeMetrics.getMetadata().getName())) {
                final Map<String, Quantity> usage = nodeMetrics.getUsage();
                nodeInfo.setCpu(usage.get("cpu").toSuffixedString());
                nodeInfo.setMemory(DECIMAL_FORMAT.format(usage.get("memory").getNumber().doubleValue() / 1048576.0) + " MB");
            }
        }
    }

    private void createDeploymentInfo(DeploymentInfo.DeploymentInfoBuilder deploymentInfoBuilder, V1Pod item, PodsInfo podsInfo) {
        if (!StringUtils.hasText(deploymentInfoBuilder.build().getContainerName())) {
            deploymentInfoBuilder.imageName(item.getStatus().getContainerStatuses().get(0).getImage());
            deploymentInfoBuilder.containerName(item.getStatus().getContainerStatuses().get(0).getName());
            try {
                DeploymentResources limits = new DeploymentResources();
                limits.setCpu(item.getSpec().getContainers().get(0).getResources().getLimits().get("cpu").toSuffixedString());
                limits.setMemory(item.getSpec().getContainers().get(0).getResources().getLimits().get("memory").toSuffixedString() + "B");
                deploymentInfoBuilder.build().getResources().put("limits", limits);

                DeploymentResources requests = new DeploymentResources();
                requests.setCpu(item.getSpec().getContainers().get(0).getResources().getRequests().get("cpu").toSuffixedString());
                requests.setMemory(item.getSpec().getContainers().get(0).getResources().getRequests().get("memory").toSuffixedString() + "B");
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
        final PodMetricsList podMetricsList = metrics.getPodMetrics(DEFAULT_NAMESPACE);
        return getPodsMetrics(podMetricsList, podsInfo);
    }

    private PodsInfo getPodsMetrics(PodMetricsList podMetricsList, PodsInfo podsInfo) {
        for (PodMetrics podMetrics : podMetricsList.getItems()) {
            if (podsInfo.getInstanceId().equals(podMetrics.getMetadata().getName())) {
                if (podMetrics.getContainers() == null) {
                    continue;
                }
                for (ContainerMetrics container : podMetrics.getContainers()) {
                    podsInfo.setCpu(container.getUsage().get("cpu").toSuffixedString());
                    podsInfo.setMemory(DECIMAL_FORMAT.format(container.getUsage().get("memory").getNumber().doubleValue() / 1048576.0) + " MB");
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

    private boolean updateApiServiceYamlAndRestart(int latestTag) throws ApiException {
        AppsV1Api appsV1Api = new AppsV1Api();
        final V1DeploymentList v1DeploymentList = appsV1Api.listDeploymentForAllNamespaces(null, null, null, null, null, null, null, null, 60, null);
        for (V1Deployment v1Deployment : v1DeploymentList.getItems()) {
            if (v1Deployment.getMetadata().getName().contains(DeploymentInitial.APISERVICE.name().toLowerCase(Locale.ROOT))) {
                final V1Status v1Status = appsV1Api.deleteNamespacedDeployment(v1Deployment.getMetadata().getName(), DEFAULT_NAMESPACE, "true", null, null, false, null, null);
                if (v1Status.getStatus().equals("Success")) {
                    v1Deployment.getMetadata().setResourceVersion(null);
                    final V1Container v1Container = v1Deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
                    final List<V1EnvVar> env = v1Container.getEnv();
                    final List<V1EnvVar> v1EnvVar = env.stream().
                            map(e -> {
                                if (e.getName().equals("dashboard_release_date")) {
                                    e.setValue(LocalDate.now() + " " + LocalTime.now().truncatedTo(ChronoUnit.SECONDS));
                                    return e;
                                }
                                if (e.getName().equals("dashboard_version_info")) {
                                    e.setValue(String.valueOf(latestTag));
                                    return e;
                                }
                                return e;
                            }).collect(Collectors.toList());
                    v1Container.setEnv(v1EnvVar);
                    appsV1Api.createNamespacedDeployment(DEFAULT_NAMESPACE, v1Deployment, "true", null, "LBS api to restart IG for font upload process");
                    return true;
                }
            }
        }
        return false;
    }

    private void updateDeploymentInfo(V1Deployment v1Deployment, int latestTag, String newImage) {
        v1Deployment.getMetadata().setResourceVersion(null);
        final V1Container v1Container = v1Deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        v1Container.setImage(newImage);
        final List<V1EnvVar> env = v1Container.getEnv();
        if (env != null && !env.isEmpty()) {
            final List<V1EnvVar> v1EnvVar = env.stream().
                    map(e -> {
                        if (e.getName().equals("release_date")) {
                            e.setValue(LocalDate.now() + " " + LocalTime.now().truncatedTo(ChronoUnit.SECONDS));
                            return e;
                        }
                        if (e.getName().equals("version_info")) {
                            e.setValue(String.valueOf(latestTag));
                            return e;
                        }
                        return e;
                    }).collect(Collectors.toList());
            v1Container.setEnv(v1EnvVar);
        }
    }

    private int getLatestTagForGivenImage(String acrName, String imageName) {
        String url = "https://" + acrName + "/acr/v1/" + imageName + "/_tags?n=1&orderby=timedesc";
        final ResponseEntity<ImageProperties> exchange = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(createHeaders()), ImageProperties.class);
        final ImageProperties imageProperties = exchange.getBody();
        return Integer.valueOf(imageProperties.getTags().get(0).getName());
    }

    private HttpHeaders createHeaders() {
        String auth = "solumACRms" + ":" + "wm5kOF1HaXtHEIIu2/UjlZvfTc3cG67/";
        byte[] encodedAuth = Base64.encodeBase64(
                auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth);
        final HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("Authorization", authHeader);
        return httpHeaders;
    }

    private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                zipOut.closeEntry();
            } else {
                zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                zipOut.closeEntry();
            }
            File[] children = fileToZip.listFiles();
            for (File childFile : children) {
                zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
            }
            return;
        }
        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOut.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
    }
}
