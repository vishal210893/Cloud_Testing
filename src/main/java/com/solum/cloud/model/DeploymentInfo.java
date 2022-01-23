package com.solum.cloud.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

@Getter
@Setter
@Builder
public class DeploymentInfo {

    String name;

    String imageName;

    int minPods;

    int maxPods;

    int replicas;

    ArrayList<PodsInfo> details;

}
