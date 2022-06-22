package com.solum.cloud.controller;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.Region;
import com.azure.core.management.profile.AzureProfile;
import com.azure.core.models.ResponseError;
import com.azure.core.util.Context;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.monitor.query.MetricsQueryClient;
import com.azure.monitor.query.MetricsQueryClientBuilder;
import com.azure.monitor.query.models.*;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.compute.models.*;
import com.solum.cloud.model.VmRunCommand;
import com.solum.cloud.model.metrcis.MetricsResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@RestController
public class MetricController {

    @GetMapping(value = "/getEventHubMetrcis", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getEventHubMetrcis(@RequestParam String metricType,
                                                     @RequestParam String startTime,
                                                     @RequestParam String endTime,
                                                     @RequestParam String eventhubName,
                                                     @RequestParam long granularity) {
        try {
            ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                    .clientId("716804a0-e246-4ea1-88f6-0417a8f00238")
                    .clientSecret("1Sc8Q~SDIQBpLpQ4Sbho~0dw26EJAq~hI8D~RdaH")
                    .tenantId("ec985fe0-7f27-4ef1-841a-45f87dfb0da9")
                    .build();
            MetricsQueryClient metricsQueryClient = new MetricsQueryClientBuilder()
                    .credential(clientSecretCredential)
                    .buildClient();

            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            final QueryTimeInterval queryTimeInterval = new QueryTimeInterval(start, end);

            MetricsQueryOptions metricsQueryOptions = new MetricsQueryOptions();
            metricsQueryOptions.setTimeInterval(queryTimeInterval);
            metricsQueryOptions.setGranularity(Duration.ofMinutes(granularity));
            metricsQueryOptions.setFilter("EntityName eq '" + eventhubName + "'");


            MetricsQueryResult metricsQueryResult = metricsQueryClient.queryResourceWithResponse("/subscriptions/888464c6-6e6c-41f1-b6f2-e247a4403e8f/resourceGroups/common-stage-resource/providers/Microsoft.EventHub/namespaces/common-stage-eventhub00",
                    Arrays.asList(metricType), metricsQueryOptions, Context.NONE).getValue();


            MetricsResponse metricsResponse = new MetricsResponse();

            for (MetricResult metric : metricsQueryResult.getMetrics()) {
                metricsResponse.setMetricsName(metric.getMetricName());
                metricsResponse.setDescription(metric.getDescription());
                metricsResponse.setUnit(metric.getUnit());
                for (TimeSeriesElement timeSeriesElement : metric.getTimeSeries()) {
                    metricsResponse.setDimension(timeSeriesElement.getMetadata());
                    HashMap<Object, Object> data = new HashMap<>();
                    for (MetricValue metricValue : timeSeriesElement.getValues()) {
                        data.put(metricValue.getTimeStamp(), metricValue.getTotal());
                    }
                    metricsResponse.setData(data);
                }
            }
            metricsResponse.setResponseCode(HttpStatus.OK.toString());
            metricsResponse.setResponseMessage("Metrics retrieved successfully");
            return ResponseEntity.ok().body(metricsResponse);
        } catch (HttpResponseException e) {
            log.error(e.getMessage(), e);
            MetricsResponse metricsResponse = new MetricsResponse();
            metricsResponse.setResponseCode(String.valueOf(e.getResponse().getStatusCode()));
            metricsResponse.setResponseMessage(((ResponseError) e.getValue()).getMessage());
            return ResponseEntity.status(HttpStatus.valueOf(e.getResponse().getStatusCode())).body(metricsResponse);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            MetricsResponse metricsResponse = new MetricsResponse();
            metricsResponse.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.toString());
            metricsResponse.setResponseMessage(e.getMessage());
            return ResponseEntity.internalServerError().body(metricsResponse);
        }
    }

    @GetMapping(value = "/getApplicationGatewayMetrics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getApplicationGatewayMetrcis(@RequestParam String metricType,
                                                               @RequestParam String startTime,
                                                               @RequestParam String endTime) {

        try {
            ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                    .clientId("716804a0-e246-4ea1-88f6-0417a8f00238")
                    .clientSecret("1Sc8Q~SDIQBpLpQ4Sbho~0dw26EJAq~hI8D~RdaH")
                    .tenantId("ec985fe0-7f27-4ef1-841a-45f87dfb0da9")
                    .build();
            MetricsQueryClient metricsQueryClient = new MetricsQueryClientBuilder()
                    .credential(clientSecretCredential)
                    .buildClient();

            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            final QueryTimeInterval queryTimeInterval = new QueryTimeInterval(start, end);

            MetricsQueryOptions metricsQueryOptions = new MetricsQueryOptions();
            metricsQueryOptions.setTimeInterval(queryTimeInterval);
            metricsQueryOptions.setGranularity(Duration.ofMinutes(1));
            /*metricsQueryOptions.setFilter("HttpStatusGroup eq '5xx' or HttpStatusGroup eq '4xx'");*/
            metricsQueryOptions.setAggregations(AggregationType.COUNT);

            MetricsQueryResult metricsQueryResult = metricsQueryClient.queryResourceWithResponse("/subscriptions/888464c6-6e6c-41f1-b6f2-e247a4403e8f/resourceGroups/MC_common-stage-resource_common-stage-aks00_koreacentral/providers/Microsoft.Network/applicationGateways/commonstageaag00",
                    Arrays.asList(metricType), metricsQueryOptions, Context.NONE).getValue();

            for (MetricResult metric : metricsQueryResult.getMetrics()) {
                System.out.println("Metric name " + metric.getMetricName());
                for (TimeSeriesElement timeSeriesElement : metric.getTimeSeries()) {
                    System.out.println("Dimensions " + timeSeriesElement.getMetadata());
                    for (MetricValue metricValue : timeSeriesElement.getValues()) {
                        final Double total = metricValue.getTotal();
                        if (total != null) {
                            System.out.println(metricValue.getTimeStamp() + " " + total);
                        }
                    }
                }
            }
            return ResponseEntity.ok().body("ok");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());

        }
    }

    @GetMapping(value = "/getApplicationGatewayMetrics/httpStatus", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getApplicationGatewayMetricsHttpStatus(@RequestParam String metricType,
                                                                         @RequestParam String startTime,
                                                                         @RequestParam String endTime,
                                                                         @RequestParam long granularity) {
        try {
            ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                    .clientId("716804a0-e246-4ea1-88f6-0417a8f00238")
                    .clientSecret("1Sc8Q~SDIQBpLpQ4Sbho~0dw26EJAq~hI8D~RdaH")
                    .tenantId("ec985fe0-7f27-4ef1-841a-45f87dfb0da9")
                    .build();
            MetricsQueryClient metricsQueryClient = new MetricsQueryClientBuilder()
                    .credential(clientSecretCredential)
                    .buildClient();

            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            final QueryTimeInterval queryTimeInterval = new QueryTimeInterval(start, end);

            MetricsQueryOptions metricsQueryOptions = new MetricsQueryOptions();
            metricsQueryOptions.setTimeInterval(queryTimeInterval);
            metricsQueryOptions.setGranularity(Duration.ofMinutes(granularity));
            metricsQueryOptions.setFilter("HttpStatusGroup eq '1xx' or HttpStatusGroup eq '2xx' or HttpStatusGroup eq '3xx' or HttpStatusGroup eq '4xx' or HttpStatusGroup eq '5xx'");
            metricsQueryOptions.setAggregations(AggregationType.TOTAL);

            MetricsQueryResult metricsQueryResult = metricsQueryClient.queryResourceWithResponse("/subscriptions/888464c6-6e6c-41f1-b6f2-e247a4403e8f/resourceGroups/MC_common-stage-resource_common-stage-aks00_koreacentral/providers/Microsoft.Network/applicationGateways/commonstageaag00",
                    Arrays.asList(metricType), metricsQueryOptions, Context.NONE).getValue();


            MetricsResponse metricsResponse = new MetricsResponse();
            for (MetricResult metric : metricsQueryResult.getMetrics()) {
                System.out.println("Metric name " + metric.getMetricName());
                for (TimeSeriesElement timeSeriesElement : metric.getTimeSeries()) {
                    System.out.println("Dimensions " + timeSeriesElement.getMetadata());
                    for (MetricValue metricValue : timeSeriesElement.getValues()) {
                        final Double total = metricValue.getTotal();
                        if (total != null) {
                            System.out.println(metricValue.getTimeStamp() + " " + total);
                        }
                    }
                }
            }
            metricsResponse.setResponseCode(HttpStatus.OK.toString());
            metricsResponse.setResponseMessage("Application Gateway metrics retrieved successfully");
            return ResponseEntity.ok().body(metricsResponse);
        } catch (HttpResponseException e) {
            log.error(e.getMessage(), e);
            MetricsResponse metricsResponse = new MetricsResponse();
            metricsResponse.setResponseCode(String.valueOf(e.getResponse().getStatusCode()));
            metricsResponse.setResponseMessage(((ResponseError) e.getValue()).getMessage());
            return new ResponseEntity<>(metricsResponse, HttpStatus.valueOf(e.getResponse().getStatusCode()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            MetricsResponse metricsResponse = new MetricsResponse();
            metricsResponse.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.toString());
            metricsResponse.setResponseMessage(e.getMessage());
            return new ResponseEntity<>(metricsResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @GetMapping(value = "/getStorageMetrics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getStorageMetrics(@RequestParam String metricType,
                                                    @RequestParam String startTime,
                                                    @RequestParam String endTime,
                                                    @RequestParam(required = false) String metricsNamespace) {
        try {
            ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                    .clientId("716804a0-e246-4ea1-88f6-0417a8f00238")
                    .clientSecret("1Sc8Q~SDIQBpLpQ4Sbho~0dw26EJAq~hI8D~RdaH")
                    .tenantId("ec985fe0-7f27-4ef1-841a-45f87dfb0da9")
                    .build();
            MetricsQueryClient metricsQueryClient = new MetricsQueryClientBuilder()
                    .credential(clientSecretCredential)
                    .buildClient();

            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            final QueryTimeInterval queryTimeInterval = new QueryTimeInterval(start, end);

            MetricsQueryOptions metricsQueryOptions = new MetricsQueryOptions();
            metricsQueryOptions.setTimeInterval(queryTimeInterval);
            metricsQueryOptions.setAggregations(AggregationType.TOTAL);
            metricsQueryOptions.setGranularity(Duration.ofMinutes(1));


            MetricsQueryResult metricsQueryResult = metricsQueryClient.queryResourceWithResponse("/subscriptions/888464c6-6e6c-41f1-b6f2-e247a4403e8f/resourceGroups/common-stage-resource/providers/Microsoft.Storage/storageAccounts/commonstagestorage00/blobServices/default",
                    Arrays.asList(metricType), metricsQueryOptions, Context.NONE).getValue();


            MetricsResponse metricsResponse = new MetricsResponse();
            for (MetricResult metric : metricsQueryResult.getMetrics()) {
                metricsResponse.setMetricsName(metric.getMetricName());
                metricsResponse.setDescription(metric.getDescription());
                metricsResponse.setUnit(metric.getUnit());
                for (TimeSeriesElement timeSeriesElement : metric.getTimeSeries()) {
                    final int size = timeSeriesElement.getValues().size();
                    metricsResponse.setDimension(timeSeriesElement.getMetadata().size() > 0 ? timeSeriesElement.getMetadata() : null);
                    HashMap<Object, Object> data = new HashMap<>();
                    DecimalFormat df = new DecimalFormat("#");
                    df.setMaximumFractionDigits(2);
                    final Double sum = timeSeriesElement.getValues().stream()
                            .filter(metricValue -> metricValue.getTotal() != null)
                            .mapToDouble(metricValue -> metricValue.getTotal())
                            .sum();
                    data.put(metric.getUnit(), df.format(sum / size));
                    metricsResponse.setData(data);
                }
            }
            metricsResponse.setResponseCode(HttpStatus.OK.toString());
            metricsResponse.setResponseMessage("Storage metrics retrieved successfully");
            return ResponseEntity.ok().body(metricsResponse);
        } catch (HttpResponseException e) {
            log.error(e.getMessage(), e);
            MetricsResponse metricsResponse = new MetricsResponse();
            metricsResponse.setResponseCode(String.valueOf(e.getResponse().getStatusCode()));
            metricsResponse.setResponseMessage(((ResponseError) e.getValue()).getMessage());
            return new ResponseEntity<>(metricsResponse, HttpStatus.valueOf(e.getResponse().getStatusCode()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            MetricsResponse metricsResponse = new MetricsResponse();
            metricsResponse.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.toString());
            metricsResponse.setResponseMessage(e.getMessage());
            return new ResponseEntity<>(metricsResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value = "/getIoTHubMetrics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getIoTHubMetrics(@RequestParam String metricType,
                                                   @RequestParam String startTime,
                                                   @RequestParam String endTime,
                                                   @RequestParam long granularity) {
        try {
            ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                    .clientId("716804a0-e246-4ea1-88f6-0417a8f00238")
                    .clientSecret("1Sc8Q~SDIQBpLpQ4Sbho~0dw26EJAq~hI8D~RdaH")
                    .tenantId("ec985fe0-7f27-4ef1-841a-45f87dfb0da9")
                    .build();
            MetricsQueryClient metricsQueryClient = new MetricsQueryClientBuilder()
                    .credential(clientSecretCredential)
                    .buildClient();

            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            final QueryTimeInterval queryTimeInterval = new QueryTimeInterval(start, end);

            MetricsQueryOptions metricsQueryOptions = new MetricsQueryOptions();
            metricsQueryOptions.setTimeInterval(queryTimeInterval);
            metricsQueryOptions.setAggregations(AggregationType.TOTAL);
            metricsQueryOptions.setGranularity(Duration.ofMinutes(granularity));

            MetricsQueryResult metricsQueryResult = metricsQueryClient.queryResourceWithResponse("/subscriptions/888464c6-6e6c-41f1-b6f2-e247a4403e8f/resourceGroups/common-stage-resource/providers/Microsoft.Devices/IotHubs/common-stage-iothub00",
                    Arrays.asList(metricType), metricsQueryOptions, Context.NONE).getValue();


            MetricsResponse metricsResponse = new MetricsResponse();
            for (MetricResult metric : metricsQueryResult.getMetrics()) {
                metricsResponse.setMetricsName(metric.getMetricName());
                metricsResponse.setDescription(metric.getDescription());
                metricsResponse.setUnit(metric.getUnit());
                for (TimeSeriesElement timeSeriesElement : metric.getTimeSeries()) {
                    for (MetricValue metricValue : timeSeriesElement.getValues()) {
                        final Double total = metricValue.getTotal();
                        if (total != null) {
                            System.out.println(metricValue.getTimeStamp() + " " + total);
                        }
                    }
                }
            }
            metricsResponse.setResponseCode(HttpStatus.OK.toString());
            metricsResponse.setResponseMessage("IoTHub metrics retrieved successfully");
            return ResponseEntity.ok().body(metricsResponse);
        } catch (HttpResponseException e) {
            log.error(e.getMessage(), e);
            MetricsResponse metricsResponse = new MetricsResponse();
            metricsResponse.setResponseCode(String.valueOf(e.getResponse().getStatusCode()));
            metricsResponse.setResponseMessage(((ResponseError) e.getValue()).getMessage());
            return new ResponseEntity<>(metricsResponse, HttpStatus.valueOf(e.getResponse().getStatusCode()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            MetricsResponse metricsResponse = new MetricsResponse();
            metricsResponse.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.toString());
            metricsResponse.setResponseMessage(e.getMessage());
            return new ResponseEntity<>(metricsResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping(value = "/getVmMetrics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getVmMetrics(@RequestParam(required = false) String customerCode, @RequestBody VmRunCommand vmRunCommand) {
        try {
            ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                    .clientId("716804a0-e246-4ea1-88f6-0417a8f00238")
                    .clientSecret("1Sc8Q~SDIQBpLpQ4Sbho~0dw26EJAq~hI8D~RdaH")
                    .tenantId("ec985fe0-7f27-4ef1-841a-45f87dfb0da9")
                    .build();
          /*  MetricsQueryClient metricsQueryClient = new MetricsQueryClientBuilder()
                    .credential(clientSecretCredential)
                    .buildClient();

            QueryTimeInterval queryTimeInterval;
            if (StringUtils.hasText(startTime)) {
                OffsetDateTime start = OffsetDateTime.parse(startTime);
                OffsetDateTime end = OffsetDateTime.parse(endTime);
                queryTimeInterval = new QueryTimeInterval(start, end);
            } else {
                queryTimeInterval = new QueryTimeInterval(Duration.ofMinutes(1));
            }

            MetricsQueryOptions metricsQueryOptions = new MetricsQueryOptions();
            metricsQueryOptions.setTimeInterval(queryTimeInterval);
            if (metricType.equals("Percentage CPU")) {
                metricsQueryOptions.setAggregations(AggregationType.AVERAGE);
            } else {
                metricsQueryOptions.setAggregations(AggregationType.TOTAL);
            }
            metricsQueryOptions.setGranularity(Duration.ofMinutes(granularity));

            MetricsQueryResult metricsQueryResult = metricsQueryClient.queryResourceWithResponse("/subscriptions/888464c6-6e6c-41f1-b6f2-e247a4403e8f/resourceGroups/sti-stage-qa/providers/Microsoft.Compute/virtualMachines/sti-stage-qa-entity-db-01",
                    Arrays.asList(metricType), metricsQueryOptions, Context.NONE).getValue();


            MetricsResponse metricsResponse = new MetricsResponse();
            for (MetricResult metric : metricsQueryResult.getMetrics()) {
                metricsResponse.setMetricsName(metric.getMetricName());
                metricsResponse.setDescription(metric.getDescription());
                metricsResponse.setUnit(metric.getUnit());
                for (TimeSeriesElement timeSeriesElement : metric.getTimeSeries()) {
                    for (MetricValue metricValue : timeSeriesElement.getValues()) {
                        Double total;
                        if (metricsQueryOptions.getAggregations().get(0).equals(AggregationType.AVERAGE)) {
                            total = metricValue.getAverage();
                        } else {
                            total = metricValue.getTotal();
                        }
                        if (total != null) {
                            System.out.println(metricValue.getTimeStamp() + " " + total);
                        }
                    }
                }
            }
            metricsResponse.setResponseCode(HttpStatus.OK.toString());
            metricsResponse.setResponseMessage("Azure VM metrics retrieved successfully");
            return ResponseEntity.ok().body(metricsResponse);*/

            /* AZURE RESOURCE MANAGER CODING */
            // Please finish 'Set up authentication' step first to set the four environment variables: AZURE_SUBSCRIPTION_ID, AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, AZURE_TENANT_ID
            AzureProfile profile = new AzureProfile(AzureEnvironment.AZURE);

            AzureResourceManager azureResourceManager = AzureResourceManager.configure()
                    .withLogLevel(HttpLogDetailLevel.BASIC)
                    .authenticate(clientSecretCredential, profile)
                    .withSubscription("77cfd0cd-235a-4725-a7e3-75ebe73929d8");

            final VirtualMachine vm = azureResourceManager.virtualMachines().getById("/subscriptions/77cfd0cd-235a-4725-a7e3-75ebe73929d8/resourceGroups/ASIA_PROD_RESOURCE1/providers/Microsoft.Compute/virtualMachines/asia-system-maintenance");

            List<String> ar = new ArrayList<>();
            ar.add("pwd");
            ar.add("cd ../../../../../../home/solum/maintain/labelStatus/");
            ar.add("rm -rf " + customerCode);
            ar.add("cp -r WPT " + customerCode);
            ar.add("cd " + customerCode + "/label-status-process/");
            ar.add("rm -rf dist/ node_modules/ logs/");
            ar.add("sudo rm -r env/application.properties.json");
            String jsonString = getApplicationPropertiesJson(customerCode, vmRunCommand.getDbUri(), vmRunCommand.getMongoUri(), vmRunCommand.getMongoDb(), vmRunCommand.getUpdatePeriod());
            ar.add("echo '" + jsonString + "' >> env/application.properties.json");
            ar.add("sed -i 's/.*\"name\".*/      \"name\": \"" + customerCode + "\",/' ecosystem.config.json");
            ar.add("npm install");
            ar.add("npm run build");
            ar.add("printf '#!/bin/bash\\npm2 start ecosystem.config.json' >> run.sh");
            ar.add("chmod 777 run.sh");
            ar.add("su solum -s run.sh");


            RunCommandInput runCommandInput = new RunCommandInput();
            runCommandInput.withCommandId("RunShellScript").withScript(ar);
            final RunCommandResult ls = vm.runCommand(runCommandInput);

            final List<InstanceViewStatus> value = ls.value();
            StringBuilder sb = new StringBuilder("");
            value.forEach(val -> {
                System.out.println(val.message());
                sb.append(val.message());
            });


            /*
            final Map<Integer, VirtualMachineDataDisk> integerVirtualMachineDataDiskMap = vm.dataDisks();
            System.out.println("hardwareProfile");
            System.out.println("    vmSize: " + vm.size());
            System.out.println("    publisher: " + vm.storageProfile().imageReference().publisher());
            System.out.println("    offer: " + vm.storageProfile().imageReference().offer());
            System.out.println("    sku: " + vm.storageProfile().imageReference().sku());
            System.out.println("    version: " + vm.storageProfile().imageReference().version());

            final StorageProfile storageProfile = vm.storageProfile();
            final DataDisk dataDisk = storageProfile.dataDisks().get(0);
            final Integer diskSizeGB = dataDisk.diskSizeGB();
            System.out.println(diskSizeGB);



            System.out.println("networkProfile");
            System.out.println("    networkInterface: " + vm.primaryNetworkInterfaceId());
            System.out.println("vmAgent");
            System.out.println("  vmAgentVersion: " + vm.instanceView().vmAgent().vmAgentVersion());


           System.out.println("    statuses");
            for (InstanceViewStatus status : vm.instanceView().vmAgent().statuses()) {
                System.out.println("    code: " + status.code());
                System.out.println("    displayStatus: " + status.displayStatus());
                System.out.println("    message: " + status.message());
                System.out.println("    time: " + status.time());
            }
            System.out.println("disks");
            for (DiskInstanceView disk : vm.instanceView().disks()) {
                System.out.println("  name: " + disk.name());
                System.out.println("  statuses");
                for (InstanceViewStatus status : disk.statuses()) {
                    System.out.println("    code: " + status.code());
                    System.out.println("    displayStatus: " + status.displayStatus());
                    System.out.println("    time: " + status.time());
                }
            }
            System.out.println("VM general status");
            System.out.println("  provisioningStatus: " + vm.provisioningState());
            System.out.println("  id: " + vm.id());
            System.out.println("  name: " + vm.name());
            System.out.println("  type: " + vm.type());
            System.out.println("VM instance status");
            for (InstanceViewStatus status : vm.instanceView().statuses()) {
                System.out.println("  code: " + status.code());
                System.out.println("  displayStatus: " + status.displayStatus());
            }*/
            return ResponseEntity.ok().body(sb.toString());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            MetricsResponse metricsResponse = new MetricsResponse();
            metricsResponse.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.toString());
            metricsResponse.setResponseMessage(e.getMessage());
            return new ResponseEntity<>(metricsResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String getApplicationPropertiesJson(String customerCode, String dbUri, String mongoUri, String mongoDb, String updatePeriod) {
        String app_prop_json = "{\n" +
                "  \"NODE_ENV\": \"development\",\n" +
                "\n" +
                "  \"DB_URI\": \"${DB_URI}\",\n" +
                "  \"DB_CUSTOMER_CODE\": \"${CUSTOMER_CODE}\",\n" +
                "\n" +
                "  \"MONGO_URI\": \"${MONGO_URI}\",\n" +
                "  \"MONGO_DB\": \"${MONGO_DB}\",\n" +
                "  \"MONGO_SRC_COLLECTION\": \"${CUSTOMER_CODE}.r_in_out_label_status\",\n" +
                "  \"MONGO_DEST_COLLECTION\": \"${CUSTOMER_CODE}.r_label_status\",\n" +
                "  \"MONGO_CHECK_PERIOD\": 10,\n" +
                "\n" +
                "  \"CONN_POOL_SIZE\": 4000,\n" +
                "  \"CONN_TIMEOUT\": 600000,\n" +
                "  \"CONN_REPEAT_COUNT\": 5,\n" +
                "\n" +
                "  \"INIT_DEST_COLLECTION\": false,\n" +
                "\n" +
                "  \"NUM_PROC_LABEL\": 5,\n" +
                "  \"NUM_PROC_TASK\": 3,\n" +
                "  \"NUM_PROC_TASK_GROUP\": 1,\n" +
                "  \"NUM_CONC_STORE\": 5,\n" +
                "  \"PROC_CHECK_INTERVAL\": 10,\n" +
                "  \"STORE_CHECK_INTERVAL\": 10000,\n" +
                "  \"SCAN_LABELS_TIME_OFFSET\": 30,\n" +
                "  \"STORE_LIST_UPDATE_PERIOD\": ${STORE_LIST_UPDATE_PERIOD},\n" +
                "\n" +
                "  \"SLACK_WEBHOOK_URL\": \"https://hooks.slack.com/services/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\",\n" +
                "\n" +
                "  \"LOG_DIR\": \"./logs\",\n" +
                "  \"LOG_LABEL\": \"LSPRCS\",\n" +
                "  \"MAX_SIZE_PER_LOG\": 100,\n" +
                "  \"MAX_NUM_OF_LOG_FILES\": 30\n" +
                "}";
        HashMap<String, String> actualValues = new HashMap<>();
        actualValues.put("DB_URI", dbUri);
        actualValues.put("CUSTOMER_CODE", "WPT");
        actualValues.put("MONGO_URI", mongoUri);
        actualValues.put("MONGO_DB", mongoDb);
        actualValues.put("STORE_LIST_UPDATE_PERIOD", updatePeriod);
        StringSubstitutor stringSubstitutor = new StringSubstitutor(actualValues);
        String jsonString = stringSubstitutor.replace(app_prop_json);
        return jsonString;
    }


}


      /*
        Valid metrics: Percentage CPU
        -------------------------------------------
        Network In
        Network Out
        Disk Read Bytes
        Disk Write Bytes
        Disk Read Operations/Sec
        Disk Write Operations/Sec
        CPU Credits Remaining
        CPU Credits Consumed
        Data Disk Read Bytes/sec
        Data Disk Write Bytes/sec
        Data Disk Read Operations/Sec
        Data Disk Write Operations/Sec
        Data Disk Queue Depth
        Data Disk Bandwidth Consumed Percentage
        Data Disk IOPS Consumed Percentage
        Data Disk Target Bandwidth
        Data Disk Target IOPS
        Data Disk Max Burst Bandwidth
        Data Disk Max Burst IOPS
        Data Disk Used Burst BPS Credits Percentage
        Data Disk Used Burst IO Credits Percentage
        OS Disk Read Bytes/sec
        OS Disk Write Bytes/sec
        OS Disk Read Operations/Sec
        OS Disk Write Operations/Sec
        OS Disk Queue Depth
        OS Disk Bandwidth Consumed Percentage
        OS Disk IOPS Consumed Percentage
        OS Disk Target Bandwidth
        OS Disk Target IOPS
        OS Disk Max Burst Bandwidth
        OS Disk Max Burst IOPS
        OS Disk Used Burst BPS Credits Percentage
        OS Disk Used Burst IO Credits Percentage
        Inbound Flows
        Outbound Flows
        Inbound Flows Maximum Creation Rate
        Outbound Flows Maximum Creation Rate
        Premium Data Disk Cache Read Hit
        Premium Data Disk Cache Read Miss
        Premium OS Disk Cache Read Hit
        Premium OS Disk Cache Read Miss
        VM Cached Bandwidth Consumed Percentage
        VM Cached IOPS Consumed Percentage
        VM Uncached Bandwidth Consumed Percentage
        VM Uncached IOPS Consumed Percentage
        Network In Total
        Network Out Total
        Available Memory Bytes
       */
     /*
        Valid metrics:
        -------------------
        d2c.telemetry.ingress.allProtocol
        d2c.telemetry.ingress.success
        c2d.commands.egress.complete.success
        c2d.commands.egress.abandon.success
        c2d.commands.egress.reject.success
        C2DMessagesExpired
        devices.totalDevices
        devices.connectedDevices.allProtocol
        d2c.telemetry.egress.success
        d2c.telemetry.egress.dropped
        d2c.telemetry.egress.orphaned
        d2c.telemetry.egress.invalid
        d2c.telemetry.egress.fallback
        d2c.endpoints.egress.eventHubs
        d2c.endpoints.latency.eventHubs
        d2c.endpoints.egress.serviceBusQueues
        d2c.endpoints.latency.serviceBusQueues
        d2c.endpoints.egress.serviceBusTopics
        d2c.endpoints.latency.serviceBusTopics
        d2c.endpoints.egress.builtIn.events
        d2c.endpoints.latency.builtIn.events
        d2c.endpoints.egress.storage
        d2c.endpoints.latency.storage
        d2c.endpoints.egress.storage.bytes
        d2c.endpoints.egress.storage.blobs
        EventGridDeliveries
        EventGridLatency
        RoutingDeliveries
        RoutingDeliveryLatency
        RoutingDataSizeInBytesDelivered
        d2c.twin.read.success
        d2c.twin.read.failure
        d2c.twin.read.size
        d2c.twin.update.success
        d2c.twin.update.failure
        d2c.twin.update.size
        c2d.methods.success
        c2d.methods.failure
        c2d.methods.requestSize
        c2d.methods.responseSize
        c2d.twin.read.success
        c2d.twin.read.failure
        c2d.twin.read.size
        c2d.twin.update.success
        c2d.twin.update.failure
        c2d.twin.update.size
        twinQueries.success
        twinQueries.failure
        twinQueries.resultSize
        jobs.createTwinUpdateJob.success
        jobs.createTwinUpdateJob.failure
        jobs.createDirectMethodJob.success
        jobs.createDirectMethodJob.failure
        jobs.listJobs.success
        jobs.listJobs.failure
        jobs.cancelJob.success
        jobs.cancelJob.failure
        jobs.queryJobs.success
        jobs.queryJobs.failure
        jobs.completed
        jobs.failed
        d2c.telemetry.ingress.sendThrottle
        dailyMessageQuotaUsed
        deviceDataUsage
        deviceDataUsageV2
        totalDeviceCount
        connectedDeviceCount
        configurations
     */

    /*
      Valid metrics:
      --------------
      BlobCapacity
      BlobCount
      BlobProvisionedSize
      ContainerCount
      IndexCapacity
      Transactions
      Ingress
      Egress
      SuccessServerLatency
      SuccessE2ELatency
      Availability
     */

     /*
        Valid metrics for Event hub
        -----------------------------
        SuccessfulRequests
        ServerErrors
        UserErrors
        QuotaExceededErrors
        ThrottledRequests
        IncomingRequests
        IncomingMessages
        OutgoingMessages
        IncomingBytes
        OutgoingBytes
        ActiveConnections
        ConnectionsOpened
        ConnectionsClosed
        CaptureBacklog
        CapturedMessages
        CapturedBytes
        Size
        INREQS
        SUCCREQ
        FAILREQ
        SVRBSY
        INTERR
        MISCERR
        INMSGS
        EHINMSGS
        OUTMSGS
        EHOUTMSGS
        EHINMBS
        EHINBYTES
        EHOUTMBS
        EHOUTBYTES
        EHABL
        EHAMSGS
        EHAMBS
        NamespaceCpuUsage
        NamespaceMemoryUsage
      */

    /*
    FOR APPLICATION GATEWAY
    -----------------------
    Throughput
    UnhealthyHostCount
    HealthyHostCount
    TotalRequests
    AvgRequestCountPerHealthyHost
    FailedRequests
    ResponseStatus
    CurrentConnections
    NewConnectionsPerSecond
    CapacityUnits
    FixedBillableCapacityUnits
    EstimatedBilledCapacityUnits
    ComputeUnits
    BackendResponseStatus
    TlsProtocol
    BytesSent
    BytesReceived
    ClientRtt
    ApplicationGatewayTotalTime
    BackendConnectTime
    BackendFirstByteResponseTime
    BackendLastByteResponseTime
    AzwafTotalRequests
    AzwafCustomRule
    AzwafSecRule
    AzwafBotProtection
    */
