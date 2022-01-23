package com.solum.cloud.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeploymentInfo {

    private String name;

    private String imageName;

    private String containerName;

    private HashMap<String, DeploymentResources> resources;

    private AutoScalingInfo autoScalingInfo;

    private int replicas;

    private ArrayList<PodsInfo> details;

}
