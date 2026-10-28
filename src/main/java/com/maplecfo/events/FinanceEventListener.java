package com.maplecfo.events;

/** Observer pattern: the "observer" role. */
public interface FinanceEventListener {

    void onEvent(FinanceEvent e);
}
