package com.solum.cloud.model.metrcis;

import com.azure.monitor.query.models.MetricUnit;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

@Data
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class MetricsResponse {

    private String metricsName;

    private String description;

    private MetricUnit unit;

    private Map<String, String> dimension;

    private Map<Object, Object> data;

    private double sum;

    private double average;

    private String responseCode;

    private String responseMessage;


}
