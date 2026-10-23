package com.maplecfo.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Composite pattern: the "composite" role.
 * A group (for example "Registered" or "Debts") holds accounts or other
 * groups, and its value is the sum of its children's values.
 */
public class AccountGroup implements AssetComponent {

    private final String name;
    private final List<AssetComponent> children = new ArrayList<>();

    public AccountGroup(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public void add(AssetComponent c) {
        Objects.requireNonNull(c, "component");
        if (c == this) {
            throw new IllegalArgumentException("A group cannot contain itself");
        }
        children.add(c);
    }

    public void remove(AssetComponent c) {
        children.remove(c);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Money getValue() {
        Money total = Money.zero();
        for (AssetComponent child : children) {
            total = total.plus(child.getValue());
        }
        return total;
    }

    @Override
    public List<AssetComponent> getChildren() {
        return Collections.unmodifiableList(children);
    }
}
