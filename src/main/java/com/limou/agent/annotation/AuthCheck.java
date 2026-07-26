package com.limou.agent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)//作用的地方
@Retention(RetentionPolicy.RUNTIME)//表示注解什么时候生效的
public @interface AuthCheck {
    String mustRole() default "";
}
