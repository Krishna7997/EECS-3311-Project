package com.maplecfo.command;

import com.maplecfo.domain.ContributionRecord;
import com.maplecfo.planning.RegisteredAccountService;

import java.util.Objects;

/** Logs a TFSA/RRSP/FHSA contribution (F06, proposed automatically by F14); undo removes it. */
public class RecordContributionCommand implements UndoableCommand {

    private final RegisteredAccountService registered;
    private final ContributionRecord record;

    public RecordContributionCommand(RegisteredAccountService registered, ContributionRecord record) {
        this.registered = Objects.requireNonNull(registered);
        this.record = Objects.requireNonNull(record);
    }

    public ContributionRecord record() {
        return record;
    }

    @Override
    public void execute() {
        registered.recordContribution(record);
    }

    @Override
    public void undo() {
        registered.removeContribution(record);
    }

    @Override
    public String describe() {
        return "Record " + record.amount() + " " + record.accountType() + " "
                + record.kind().name().toLowerCase() + " on " + record.date();
    }
}
