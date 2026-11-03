package com.maplecfo.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.AbstractFinanceTool;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.Money;
import com.maplecfo.planning.ContributionCheck;
import com.maplecfo.planning.ContributionRoom;
import com.maplecfo.planning.RegisteredAccountService;

import java.time.Clock;
import java.time.LocalDate;

/** TFSA / RRSP / FHSA room, and optionally whether a planned contribution fits. */
public class ContributionRoomTool extends AbstractFinanceTool {

    private final RegisteredAccountService service;
    private final Clock clock;

    public ContributionRoomTool(RegisteredAccountService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Override public String name() { return "contribution_room"; }

    @Override public String description() {
        return "Contribution room for a TFSA, RRSP or FHSA this year. Give 'amount' to check whether a planned contribution fits.";
    }

    @Override public JsonNode schema() {
        return schemaOf("account:string!:TFSA|RRSP|FHSA:Registered account type",
                "amount:number::Planned contribution in dollars");
    }

    @Override
    protected ObjectNode doExecute(JsonNode args) {
        AccountType type = AccountType.valueOf(upper(args, "account", "TFSA"));
        LocalDate today = LocalDate.now(clock);
        ObjectNode out = obj();
        out.put("account", type.name());
        out.put("year", today.getYear());
        if (args.hasNonNull("amount")) {
            ContributionCheck c = service.checkContribution(type, money(args, "amount", Money.zero()), today);
            out.put("status", c.status().name());
            if (c.status() == ContributionCheck.Status.UNKNOWN) {
                out.put("missing_profile_fields", String.join(", ", c.missingFields()));
                out.put("summary", "I can't check this yet: the profile is missing " + String.join(", ", c.missingFields()) + ".");
                return out;
            }
            out.put("available_room", c.room().available().toString());
            out.put("amount", c.amount().toString());
            if (c.status() == ContributionCheck.Status.OVER) {
                out.put("excess", c.excess().toString());
                out.put("summary", "A " + c.amount() + " " + type + " contribution would be " + c.excess()
                        + " over your room of " + c.room().available() + ". CRA charges 1% per month on the excess.");
            } else {
                out.put("room_after", c.roomAfter().toString());
                out.put("summary", "A " + c.amount() + " " + type + " contribution fits. You would have "
                        + c.roomAfter() + " of room left.");
            }
            return out;
        }
        var missing = service.missingProfileFields(type);
        if (!missing.isEmpty()) {
            out.put("missing_profile_fields", String.join(", ", missing));
            out.put("summary", "I can't calculate " + type + " room yet: the profile is missing " + String.join(", ", missing) + ".");
            return out;
        }
        ContributionRoom r = service.computeRoom(type, today.getYear());
        out.put("room_created", r.limit().toString());
        out.put("used", r.used().toString());
        out.put("available_room", r.available().toString());
        out.put("summary", "Your " + type + " room for " + today.getYear() + " is " + r.available()
                + " (" + r.limit() + " created, " + r.used() + " used).");
        return out;
    }
}
