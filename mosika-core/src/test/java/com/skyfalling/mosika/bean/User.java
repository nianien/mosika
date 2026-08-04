package com.skyfalling.mosika.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @NonNull
    private String name;
    private int age;
    private Contact contact;

    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Contact {
        String email;
        String phone;
    }
}
