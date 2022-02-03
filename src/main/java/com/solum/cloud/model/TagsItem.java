package com.solum.cloud.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TagsItem{
	private ChangeableAttributes changeableAttributes;
	private String name;
	private String digest;
	private String createdTime;
	private boolean signed;
	private String lastUpdateTime;
}