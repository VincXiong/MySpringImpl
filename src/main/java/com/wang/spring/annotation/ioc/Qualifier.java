package com.wang.spring.annotation.ioc;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
/*
@Qualifier
功能：在存在多个同类型 Bean 时，用于指定要注入的具体 Bean。
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface Qualifier {
	String value() default "";
}
