package com.solum.cloud.controller;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.models.ResponseError;
import com.azure.core.util.Context;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.monitor.query.MetricsQueryClient;
import com.azure.monitor.query.MetricsQueryClientBuilder;
import com.azure.monitor.query.models.*;
import com.solum.cloud.model.metrcis.MetricsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.TreeMap;

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

    @GetMapping(value = "/getApplicationGatewayMetrcis", produces = MediaType.APPLICATION_JSON_VALUE)
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
                    data.put(metric.getUnit(), df.format(sum/size));
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

    @GetMapping(value = "/getVmMetrics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getVmMetrics(@RequestParam String metricType,
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

            MetricsQueryResult metricsQueryResult = metricsQueryClient.queryResourceWithResponse("/subscriptions/888464c6-6e6c-41f1-b6f2-e247a4403e8f/resourceGroups/sti-stage-qa/providers/Microsoft.Compute/virtualMachines/sti-stage-qa-entity-db-01",
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
}

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
