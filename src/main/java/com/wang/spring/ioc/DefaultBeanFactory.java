package com.wang.spring.ioc;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Resource;
import com.wang.spring.annotation.ioc.Autowired;
import com.wang.spring.annotation.ioc.Bean;
import com.wang.spring.annotation.ioc.Component;
import com.wang.spring.annotation.ioc.Configuration;
import com.wang.spring.annotation.ioc.Qualifier;
import com.wang.spring.annotation.ioc.Service;
import com.wang.spring.annotation.ioc.Value;
import com.wang.spring.annotation.mvc.Controller;
import com.wang.spring.common.MyProxy;
import com.wang.spring.constants.BeanScope;
import com.wang.spring.utils.ConfigUtil;

public class DefaultBeanFactory implements BeanFactory{
	//bean工厂单例
	private static volatile DefaultBeanFactory instance = null;
	//一级缓存Bean容器，IOC容器，直接从此处获取Bean
	private static Map<String, Object> singletonObjects = new ConcurrentHashMap<>();
	//二级缓存，为了将完全地Bean和半成品的Bean分离，避免读取到不完整的Bean
	private static Map<String,Object> earlySingletonObjects=new ConcurrentHashMap<>();
	//三级缓存，值为一个对象工厂，可以返回实例对象
	private static Map<String,ObjectFactory> singletonFactories=new ConcurrentHashMap<>();
	//是否在创建中
	private  static  Set<String> singletonsCurrennlyInCreation=new HashSet<>();
	//Bean的注册信息BeanDefinition容器
	private static Map<String, BeanDefinition> beanDefinitionMap = BeanDefinitionRegistry.getBeanDefinitionMap();
	/**
	 * 初始化Bean
	 */
	static {
		Set<Class<?>> beanClassSet = ClassSetHelper.getBeanClassSet();//ClassSetHelper.getInheritedComponentClassSet(); //
		if(beanClassSet!=null && !beanClassSet.isEmpty()) {
			try {
				for(Class<?> beanClass : beanClassSet) {
					GenericBeanDefinition genericBeanDefinition = new GenericBeanDefinition();
					genericBeanDefinition.setBeanClass(beanClass);
					BeanDefinitionRegistry.registryBeanDefinition(beanClass.getName(), genericBeanDefinition);
				}
				//注册配置的bean
				getInstance().initConfigBean();
				//注册其他所有的bean
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private  DefaultBeanFactory() {
		// TODO Auto-generated constructor stub
	}
	/**
	 * 获取单例Bean工厂
	 * @return
	 */
	public static DefaultBeanFactory getInstance() {
		if(null==instance) {
			 synchronized (DefaultBeanFactory.class){
				if(null==instance) {
					instance=new DefaultBeanFactory();
					return instance;
				}
			}
		}
		return instance;
	}
	public static Map<String,Object> getBeanMap() {
		return singletonObjects;
	}
	
	/**
	 * 根据类的全限定名获取bean
	 */
	@Override
	public Object getBean(String beanName) {
		// TODO Auto-generated method stub
		Object bean = null;
		try {
			bean=doGetBean(beanName,BeanScope.SINGLETON);
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return bean;
	}
	@Override
	public Object getBean(String beanName,BeanScope beanScope) {
		// TODO Auto-generated method stub
		Object bean = null;
		try {
			bean=doGetBean(beanName,beanScope);
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return bean;
	}
	/**
	 * 根据类的class对象获取bean
	 */
	@Override
	public Object getBean(Class<?> cls) {
		// TODO Auto-generated method stub
		Object bean = null;
		try {
			bean=doGetBean(cls.getName(),BeanScope.SINGLETON);
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return bean;
	}
	@Override
	public Object getBean(Class<?> cls,BeanScope beanScope) {
		// TODO Auto-generated method stub
		Object bean = null;
		try {
			bean=doGetBean(cls.getName(),beanScope);
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return bean;
	}
	
	/**
	 * 设置bean
	 */
	@Override
	public void setBean(String beanName, Object obj) {
		// TODO Auto-generated method stub
		try {
			Objects.requireNonNull(beanName, "beanName 不能为空");
			singletonObjects.put(beanName, obj);
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	/**
	 * 设置bean
	 */
	@Override
	public void setBean(Class<?> cls, Object obj) {
		// TODO Auto-generated method stub
		this.setBean(cls.getName(), obj);
	}
	/**
	 * 刷新，重新注入所有的bean
	 */
	@Override
	public void refresh() throws Exception {
		// TODO Auto-generated method stub
		for(Map.Entry<String, BeanDefinition> entry: beanDefinitionMap.entrySet()) {
			getBean(entry.getKey());
		}
	}
	/**
	 * 注入@Configuration中配置bean
	 * @throws Exception
	 */
	private void initConfigBean() throws Exception {
		Set<Class<?>> configClassSet = ClassSetHelper.getClassSetByAnnotation(Configuration.class);
		if(configClassSet==null || configClassSet.isEmpty()) {
			return;
		}
		for(Class<?> configClass : configClassSet) {
			//注册Configuration
			GenericBeanDefinition genericBeanDefinition = new GenericBeanDefinition();
			genericBeanDefinition.setBeanClass(configClass);
			BeanDefinitionRegistry.registryBeanDefinition(configClass.getName(), genericBeanDefinition);
			Object configBean = getBean(configClass);
			Method[] methods = configClass.getDeclaredMethods();
			for(Method method:methods) {
				if(method.isAnnotationPresent(Bean.class)) {
					Class<?> returnClass = method.getReturnType();
					Object bean = method.invoke(configBean);
					String keyName = returnClass.getName();
					singletonObjects.put(keyName, bean);
					System.out.println("成功注入"+configClass.getName()+" 中的  "+returnClass.getName());
				}
				
			}
			singletonObjects.remove(configClass.getName());
		}
	}
	
	/**
	 * 从beanMap获取bean，如果不存在则实例化一个bean
	 * @param beanName
	 * @return
	 * @throws Exception
	 */
	private Object doGetBean(String beanName,BeanScope beanScope) throws Exception{
		Objects.requireNonNull(beanName, "beanName 不能为空");
		//获取单例
		Object bean = getSingleton(beanName);
		if(bean != null) {
			return bean;
		}
		//如果未获取到bean，且bean不在创建中，则置bean的状态为在创建中
		if(!singletonsCurrennlyInCreation.contains(beanName)) {
			singletonsCurrennlyInCreation.add(beanName);
		}
		BeanDefinition beanDefinition = beanDefinitionMap.get(beanName);
		if(beanDefinition==null) {
			throw new Exception("不存在 "+beanName+" 的定义");
		}
		Class<?> beanClass = beanDefinition.getBeanClass();
		// 如果 beanClass 是接口或者有多个实现类，则通过 findImplementClass 查找实际的实现类。
		beanClass = findImplementClass(beanClass,null);
		// 检查是否需要为该类生成代理。如果 BeanDefinition 中配置了代理，则生成代理类，并通过 myProxy.getProxy() 创建代理对象。
		if(beanDefinition.getIsProxy() && beanDefinition.getProxy()!=null) {
			MyProxy myProxy = beanDefinition.getProxy();
			bean=myProxy.getProxy(beanClass);
		}
		else {
			// 如果不需要代理，则通过反射创建该类的实例。
			bean = beanClass.getDeclaredConstructor().newInstance();
		}
		//将实例化后，但未注入属性的bean，放入三级缓存中,用于解决循环依赖问题
		final Object temp = bean;
		singletonFactories.put(beanName, new ObjectFactory() {
			@Override
			public Object getObject() {
				// TODO Auto-generated method stub
				return temp;
			}
		});
		// 如果 Bean 定义中有指定的初始化方法，使用反射调用该方法对 Bean 进行初始化操作
		String initMethodName = beanDefinition.getInitMethodName();
		if(initMethodName!=null) {
			Method method = beanClass.getMethod(initMethodName, null);
			method.invoke(bean, null);
		}
		
		// 对 Bean 的属性进行依赖注入
		fieldInject(beanClass, bean, false);
		//如果三级缓存存在bean，则拿出放入二级缓存中
		if(singletonFactories.containsKey(beanName)) {
			ObjectFactory factory  = singletonFactories.get(beanName);
			earlySingletonObjects.put(beanName, factory.getObject());
			singletonFactories.remove(beanName);
		}
		//如果二级缓存存在bean，则拿出放入一级缓存中
		if(earlySingletonObjects.containsKey(beanName)) {
			bean = earlySingletonObjects.get(beanName);
			singletonObjects.put(beanName, bean);
			earlySingletonObjects.remove(beanName);
		}
		return bean;
	}
	/**
	 * 从缓存中获取单例bean
	 * @param beanName
	 * @return
	 */
	private Object getSingleton(String beanName) {
		//如果一级存在bean，则直接返回
		Object bean = singletonObjects.get(beanName);
		if(bean!=null) {
			return bean;
		}
		//如果一级缓存不存在bean，且bean在创建中，则从二级缓存中拿出半成品bean返回，否则从三级缓存拿出放入二级缓存中
		if(singletonsCurrennlyInCreation.contains(beanName)) {
			bean = earlySingletonObjects.get(beanName);
			if(bean == null) {
				ObjectFactory factory = singletonFactories.get(beanName);
				if(factory != null) {
					bean = factory.getObject();
					earlySingletonObjects.put(beanName, bean);
					singletonFactories.remove(beanName);
				}
			}
		}
		return bean;
	}
	/**
	 * 依赖注入
	 * @param beanClass
	 * @param bean
	 * @param isProxyed
	 * @throws Exception
	 * @implNote 方法用于依赖注入，将带有特定注解（如 @Value、@Autowired 或 @Resource）的字段注入相应的值或实例
	 */

	private void fieldInject(Class<?> beanClass, Object bean, boolean isProxyed) throws Exception{
        Field[] beanFields = beanClass.getDeclaredFields();
        if (beanFields != null && beanFields.length > 0) {
            for (Field beanField : beanFields) {
				// 注入 @Value 注解的字段
            	if(beanField.isAnnotationPresent(Value.class)) {
            		//注入value值
            		String key = beanField.getAnnotation(Value.class).value();
            		Class<?> type = beanField.getType();
            		if(!"".equals(key)) {
            			Object value=null;
            			if(type.equals(String.class)) {
            				value = ConfigUtil.getString(key);
            			}
            			else if (type.equals(Integer.class)) {
							value = ConfigUtil.getInt(key);
						}
            			else if (type.equals(Float.class)) {
							value = ConfigUtil.getFloat(key);
						}
            			else if (type.equals(Boolean.class)) {
							value = ConfigUtil.getBoolean(key);
						}
            			else if (type.equals(Long.class)) {
							value = ConfigUtil.getLong(key);
						}
            			else if (type.equals(Double.class)) {
							value = ConfigUtil.getDouble(key);
						}
            			else {
							throw new RuntimeException("不允许的类型");
						}
						// 使用反射将获取到的值注入到该字段中。
            			beanField.setAccessible(true);
            			beanField.set(bean, value);
            		}
            	}
                //找Autowired/Resource注解属性
				// 注入 @Autowired 和 @Resource 注解的字段
            	else if (beanField.isAnnotationPresent(Autowired.class) || beanField.isAnnotationPresent(Resource.class)) {
                    // 获取字段的类型，并初始化 qualifier 为 null，以用于后续的 @Qualifier 注解处理。
					Class<?> beanFieldClass = beanField.getType();
                    String qualifier = null;
					// @Autowired 注解处理
                    if(beanField.isAnnotationPresent(Autowired.class)) {
                    	if(beanField.isAnnotationPresent(Qualifier.class)) {
                        	qualifier=beanField.getAnnotation(Qualifier.class).value();
                        }
                        //Service找实现
                        beanFieldClass = findImplementClass(beanFieldClass,qualifier);
                    }
					// @Resource 注解处理
                    else if(beanField.isAnnotationPresent(Resource.class)){
                    	qualifier = beanField.getAnnotation(Resource.class).name();
                    	if(qualifier==null || qualifier.equals("")) {
                    		qualifier = beanFieldClass.getSimpleName();
                    	}
                    	Class<?> tmpClass= findImplementClass(null,qualifier);
                    	if(tmpClass==null || tmpClass.isInterface()) {
                    		Class<?> beanAnnotationType = beanField.getAnnotation(Resource.class).type();
                    		if(beanAnnotationType!=java.lang.Object.class) {
                    			beanFieldClass = findImplementClass(beanAnnotationType, null);
                    		}
                    		else {
                    			beanFieldClass = findImplementClass(beanFieldClass, null);
                    		}
                    	}
                    	else {
                    		beanFieldClass = tmpClass;
						}
                    }
                    //根据beanName去找实例(这时是实现类的beanNamCe)
                    beanField.setAccessible(true);
                    try {
                        //反射注入属性实例。
                        beanField.set(bean,getBean(beanFieldClass.getName()) //第一遍缓存没有b，再getBean。
                        );
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
	
	/**
	 * 找到实现类
	 * @param interfaceClass
	 * @return
	 * @implNote 主要逻辑：
	 * 该方法根据接口类或父类以及名称，在所有类中查找实现类或子类。
	 * 如果提供了名称，则优先匹配注解名称或者类名。
	 * 如果找到了多个实现类，返回找到的第一个类。
	 * 步骤：
	 * 如果接口类不为空，查找所有实现类，并根据名称进行精确匹配。
	 * 如果接口类为空，则通过名称进行查找，匹配注解名称或类名。
	 * 最终返回找到的第一个实现类。
	 */
	private static Class<?> findImplementClass(Class<?> interfaceClass,String name){
		Class<?> implementClass = interfaceClass;
		Set<Class<?>> classSet = new HashSet<>();
		for(Class<?> cls : ClassSetHelper.getClassSet()) {
			// 判断当前类 cls 是否是接口类或父类 interfaceClass 的实现类或子类
			if(interfaceClass!=null && interfaceClass.isAssignableFrom(cls) && !interfaceClass.equals(cls)) {
				if(name!=null && !name.equals("")) {
					if(isClassAnnotationedName(cls, name)) {
						return cls;
					}
				}
				classSet.add(cls);
			}
			else if(interfaceClass==null) {
				if(name!=null && !name.equals("")) {
					// 如果类上存在与 name 匹配的注解，或者类名与 name 匹配并且该类不是接口，立即返回该类。
					if(isClassAnnotationedName(cls, name) || (cls.getSimpleName().equals(name) && !cls.isInterface())) {
						return cls;
					}
				}
			}
		if(classSet!=null && !classSet.isEmpty()) {
			implementClass = classSet.iterator().next();
			}
		}
		return implementClass;
	}
	// 判断一个类是否有某些特定注解（如 @Component、@Service、@Controller），并且这些注解的 value 值是否与给定的 name 匹配
	private static boolean isClassAnnotationedName(Class<?> cls,String name) {
		return (cls.isAnnotationPresent(Component.class) && cls.getAnnotation(Component.class).value().equals(name)) ||
				(cls.isAnnotationPresent(Service.class) && cls.getAnnotation(Service.class).value().equals(name)) ||
				(cls.isAnnotationPresent(Controller.class) && cls.getAnnotation(Controller.class).value().equals(name));
	}
	/**
	 * beanMap是否为空
	 */
	@Override
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return singletonObjects==null || singletonObjects.isEmpty();
	}
}
