package com.maplecfo.persistence.sqlite;

import com.maplecfo.domain.Money;
import com.maplecfo.domain.UserProfile;
import com.maplecfo.persistence.ProfileRepository;

public class SqliteProfileRepository implements ProfileRepository {

    private final Jdbc jdbc;

    public SqliteProfileRepository(DatabaseManager db) {
        this.jdbc = new Jdbc(db);
    }

    @Override
    public UserProfile load() {
        return jdbc.query("SELECT * FROM profile WHERE id = 1", rs -> new UserProfile(
                Jdbc.intOrNull(rs, "birth_year"), rs.getString("province"), Jdbc.intOrNull(rs, "resident_since_year"),
                money(rs.getString("annual_income")), money(rs.getString("rrsp_deduction_limit")),
                Jdbc.intOrNull(rs, "fhsa_opened_year"))).stream().findFirst().orElseGet(UserProfile::new);
    }

    @Override
    public void save(UserProfile p) {
        jdbc.update("""
                INSERT OR REPLACE INTO profile (id, birth_year, province, resident_since_year, annual_income,
                rrsp_deduction_limit, fhsa_opened_year) VALUES (1,?,?,?,?,?,?)""",
                p.getBirthYear(), p.getProvince(), p.getResidentSinceYear(), text(p.getAnnualIncome()),
                text(p.getRrspDeductionLimit()), p.getFhsaOpenedYear());
    }

    private static Money money(String s) {
        return s == null ? null : Money.of(s);
    }

    private static String text(Money m) {
        return m == null ? null : m.getAmount().toPlainString();
    }
}
