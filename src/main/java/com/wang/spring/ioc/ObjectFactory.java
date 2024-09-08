package com.wang.spring.ioc;

// 函数式借口, 可以传递一个参数和一个lambda表达式，在进行调用的时候可以使用getObject来调用lambda表达式中的方法
@FunctionalInterface
public interface ObjectFactory {
	public Object getObject();
}
