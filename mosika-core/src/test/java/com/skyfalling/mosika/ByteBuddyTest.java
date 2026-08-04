package com.skyfalling.mosika;

import com.cudrania.core.reflection.Reflections;
import lombok.SneakyThrows;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static com.cudrania.core.utils.StringUtils.decapitalize;

/**
 * @author skyfalling {@literal <skyfalling@live.com>}
 * @created : 2023/11/6, 星期一
 * Copyright (c) 2004-2029 All Rights Reserved.
 **/
public class ByteBuddyTest {

    @SneakyThrows
    @Test
    public void test() {


        Decorator.FIELDS.put("replace", "123");
        Object instance = new ByteBuddy().subclass(Object.class)
                .defineProperty("replace", Object.class)
                .visit(Advice.to(Decorator.class).on(ElementMatchers.isGetter()))
                .make()
                .load(Thread.currentThread().getContextClassLoader())
                .getLoaded().getDeclaredConstructor().newInstance();

        System.out.println(Reflections.invoke("getReplace", instance, new Object[0]));
    }

   public static class Decorator {
       public static Map<String, Object> FIELDS = new HashMap<>();

        @Advice.OnMethodExit
        static void exit(@Advice.Origin Method method, @Advice.Return(readOnly = false) Object returnValue) {
            String name = method.getName();
            name = decapitalize(name.substring(name.startsWith("is") ? 2 : 3));
            System.out.println("===" + FIELDS.get(name) + ":" + returnValue);
        }

    }

}
