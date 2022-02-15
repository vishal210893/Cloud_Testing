package com.solum.cloud.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kubernetes.client.openapi.models.V1NodeSystemInfo;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeInfo {

    private String nodeName;

    private LocalDateTime creationTime;

    private String clusterName;

    private String instanceType;

    private String region;

    private int totalImage;

    private V1NodeSystemInfo nodeSystemInfo;

    private String cpu;

    private String memory;

}
