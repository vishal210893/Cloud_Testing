package com.solum.cloud.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageProperties {
    private String registry;
    private String imageName;
    private List<TagsItem> tags;
}