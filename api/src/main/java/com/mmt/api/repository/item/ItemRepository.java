package com.mmt.api.repository.item;

import com.mmt.api.domain.Item;

public interface ItemRepository {

    Item findByConceptId(int conceptId);
}
