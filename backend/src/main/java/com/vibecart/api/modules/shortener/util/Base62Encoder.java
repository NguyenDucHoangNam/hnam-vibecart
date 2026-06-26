package com.vibecart.api.modules.shortener.util;

import java.util.UUID;
import java.util.Random;
public class Base62Encoder {

    private static final String BASE62_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static String encode(long number) {
        if (number <= 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        while (number > 0) {
            sb.append(BASE62_CHARACTERS.charAt((int) (number % 62)));
            number /= 62;
        }
        return sb.reverse().toString();
    }

    public static String generateShortCode(String uuid) {
        try {
            UUID uid = UUID.fromString(uuid);
            long msb = Math.abs(uid.getMostSignificantBits());
            String code = encode(msb);
            if (code.length() > 8) {
                return code.substring(0, 8);
            }
            return code;
        } catch (Exception e) {

            long randomVal = Math.abs(new Random().nextLong());
            String code = encode(randomVal);
            return code.length() > 8 ? code.substring(0, 8) : code;
        }
    }
}
