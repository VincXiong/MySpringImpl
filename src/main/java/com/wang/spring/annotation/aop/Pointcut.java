package com.wang.spring.annotation.aop;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(METHOD)
public @interface Pointcut {
	// 要表达哪些信息 ：包名、类名、方法名（参数类型）， 如 ： "com.wang.demo.service.UserService.register"

	String value() default "";
}
