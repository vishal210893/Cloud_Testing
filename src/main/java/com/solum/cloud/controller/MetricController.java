package com.solum.cloud.controller;

import com.azure.core.util.Context;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.monitor.query.MetricsQueryClient;
import com.azure.monitor.query.MetricsQueryClientBuilder;
import com.azure.monitor.query.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;

@Slf4j
@RestController
public class MetricController {

    @GetMapping(value = "/getEventHubMetrcis", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getEventHubMetrcis() {
        try {
            ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                    .clientId("716804a0-e246-4ea1-88f6-0417a8f00238")
                    .clientSecret("1Sc8Q~SDIQBpLpQ4Sbho~0dw26EJAq~hI8D~RdaH")
                    .tenantId("ec985fe0-7f27-4ef1-841a-45f87dfb0da9")
                    .build();
            MetricsQueryClient metricsQueryClient = new MetricsQueryClientBuilder()
                    .credential(clientSecretCredential)
                    .buildClient();

            MetricsQueryOptions metricsQueryOptions = new MetricsQueryOptions();
            metricsQueryOptions.setTimeInterval(QueryTimeInterval.LAST_4_HOURS);
            metricsQueryOptions.setGranularity(Duration.ofMinutes(1));

            MetricsQueryResult metricsQueryResult = metricsQueryClient.queryResourceWithResponse("/subscriptions/888464c6-6e6c-41f1-b6f2-e247a4403e8f/resourceGroups/common-stage-resource/providers/Microsoft.EventHub/namespaces/common-stage-eventhub00",
                    Arrays.asList("IncomingMessages", "OutgoingMessages"), metricsQueryOptions, Context.NONE).getValue();


            for (MetricResult metric : metricsQueryResult.getMetrics()) {
                System.out.println("Metric name " + metric.getMetricName());
                for (TimeSeriesElement timeSeriesElement : metric.getTimeSeries()) {
                    System.out.println("Dimensions " + timeSeriesElement.getMetadata());
                    for (MetricValue metricValue : timeSeriesElement.getValues()) {
                        final Double total = metricValue.getTotal();
                        if (total > 0) {
                            System.out.println(metricValue.getTimeStamp() + " " + total);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return ResponseEntity.ok().body("ok");
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
            metricsQueryOptions.setAggregations(AggregationType.TOTAL);

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
}

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
