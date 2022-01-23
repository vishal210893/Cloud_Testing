package com.solum.cloud.model;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class AutoScalingInfo {

    private int minPods;

    private int maxPods;

    private int targetCPUUtilizationPercentage;

    private int currentCPUUtilizationPercentage;

    private int currentReplicas;

    private int desiredReplicas;

    private OffsetDateTime lastScaleTime;

}
