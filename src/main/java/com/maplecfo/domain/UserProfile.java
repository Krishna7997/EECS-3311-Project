package com.maplecfo.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Personal facts needed for contribution-room rules (F06).
 * Any field may be unknown (null); {@link #missingFieldsFor(AccountType)} lists
 * what is needed so the app can answer UNKNOWN instead of guessing.
 */
public class UserProfile {

    private Integer birthYear;
    private String province;
    private Integer residentSinceYear;
    private Money annualIncome;
    private Money rrspDeductionLimit;
    private Integer fhsaOpenedYear;

    public UserProfile() {
    }

    public UserProfile(Integer birthYear, String province, Integer residentSinceYear,
                       Money annualIncome, Money rrspDeductionLimit, Integer fhsaOpenedYear) {
        this.birthYear = birthYear;
        this.province = province;
        this.residentSinceYear = residentSinceYear;
        this.annualIncome = annualIncome;
        this.rrspDeductionLimit = rrspDeductionLimit;
        this.fhsaOpenedYear = fhsaOpenedYear;
    }

    public List<String> missingFieldsFor(AccountType type) {
        List<String> missing = new ArrayList<>();
        switch (type) {
            case TFSA -> {
                if (birthYear == null) missing.add("birthYear");
                if (residentSinceYear == null) missing.add("residentSinceYear");
            }
            case RRSP -> {
                if (rrspDeductionLimit == null) missing.add("rrspDeductionLimit");
            }
            case FHSA -> {
                if (fhsaOpenedYear == null) missing.add("fhsaOpenedYear");
            }
            default -> throw new IllegalArgumentException(type + " is not a registered account");
        }
        return missing;
    }

    public Integer getBirthYear() { return birthYear; }
    public void setBirthYear(Integer birthYear) { this.birthYear = birthYear; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public Integer getResidentSinceYear() { return residentSinceYear; }
    public void setResidentSinceYear(Integer residentSinceYear) { this.residentSinceYear = residentSinceYear; }
    public Money getAnnualIncome() { return annualIncome; }
    public void setAnnualIncome(Money annualIncome) { this.annualIncome = annualIncome; }
    public Money getRrspDeductionLimit() { return rrspDeductionLimit; }
    public void setRrspDeductionLimit(Money rrspDeductionLimit) { this.rrspDeductionLimit = rrspDeductionLimit; }
    public Integer getFhsaOpenedYear() { return fhsaOpenedYear; }
    public void setFhsaOpenedYear(Integer fhsaOpenedYear) { this.fhsaOpenedYear = fhsaOpenedYear; }
}
