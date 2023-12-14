package com.mmt.api.dto.chapter;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ChapterResponse {

    private String key;
    private String label;
    private String icon;
    private List<ChapterResponse> children = new ArrayList<>();

}
