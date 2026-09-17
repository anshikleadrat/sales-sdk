package com.leadrat.aisdk.introspection;

import java.util.List;
import java.util.Locale;

public class SensitiveFieldHeuristics {

    private static final List<String> PATTERNS = List.of(
            "password", "passwd", "pwd", "secret", "token", "apikey", "api_key",
            "accesskey", "privatekey", "ssn", "socialsecurity", "aadhaar", "pan",
            "dob", "dateofbirth", "birthdate", "salary", "compensation",
            "creditcard", "cardnumber", "cvv", "bankaccount", "accountnumber",
            "ifsc", "iban", "routingnumber", "passport", "license", "otp", "hash", "salt");

    public static boolean isSensitive(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT).replace("_", "");
        return PATTERNS.stream().anyMatch(p -> normalized.contains(p.replace("_", "")));
    }
}
