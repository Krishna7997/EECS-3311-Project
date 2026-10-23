package com.maplecfo.domain;

import java.util.List;

/**
 * Composite pattern: the "component" role.
 * A single {@link Account} (leaf) and an {@link AccountGroup} (composite) are
 * treated the same way, so net worth is simply the value of the root group.
 */
public interface AssetComponent {

    String getName();

    /** Signed value: assets are positive, liabilities negative. */
    Money getValue();

    /** Children of a group; a leaf returns an empty list. */
    List<AssetComponent> getChildren();
}
