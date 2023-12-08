package com.mmt.api.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KnowledgeSpace {

    private int id;
    private int toConceptId;
    private int fromConceptId;

}
