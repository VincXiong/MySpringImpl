package com.wang.spring.aop;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.wang.mybatis.transaction.TransactionFactory;
import com.wang.mybatis.transaction.TransactionManager;
import com.wang.mybatis.transaction.TransactionStatus;
import com.wang.spring.annotation.aop.Transactional;
import com.wang.spring.common.MyProxy;
import com.wang.spring.constants.AdviceTypeConstant;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
/**
 * 使用CGLib实现的代理类，对目标进行增强，CGLIB 动态代理则是基于类代理（字节码提升），通过 ASM（Java 字节码的操作和分析框架）将被代理类的 class 文件加载进来，通过修改其字节码生成子类来处理
 * @author Administrator
 *
 */
public class CGLibProxy implements MethodInterceptor,MyProxy{
	//目标代理方法和增强列表的映射
	Map<Method, Map<String, List<Advice>>> methodAdvicesMap=null;
	//事务管理器
	TransactionManager transactionManager = TransactionFactory.newTransaction();
	
	public CGLibProxy(Map<Method, Map<String, List<Advice>>> methodAdvicesMap) {
		// TODO Auto-generated constructor stub
		this.methodAdvicesMap=methodAdvicesMap;
	}
	/**
	 * 通过CGLib库生成代理类
	 * @param cls
	 * @implNote  Enhancer：来指定要代理的目标对象，实际处理代理逻辑的对象，最终通过调用create（）方法得到代理对象、对这个对象所有的非final方法的调用都会转发给MethodInterceptor
	 */
	@Override
	public Object getProxy(Class<?> cls) {
		// 创建cglib的增强对象
		Enhancer enhancer = new Enhancer();
		// 指定父类，也就是被代理的类
		enhancer.setSuperclass(cls);
		// 指定回掉接口
		enhancer.setCallback(this);
		// 理解为创建一个目标类的子类
		return enhancer.create();
	}
	/**
	 * 拦截目标代理方法，对目标方法进行增强
	 * MethodInterceptor：动态代理对象的方法调用都会转发到intercept方法进行增强
	 */
	@Override
	public Object intercept(Object object, Method method, Object[] arg2, MethodProxy methodProxy) throws Throwable {
		Object result=null;
		TransactionStatus status = geTransactionStatus(object, method);
		status.isTrans = transactionManager.isTransactionPresent();
		//判断是否增强
		Map<String, List<Advice>> advices =  methodAdvicesMap.get(method);
		try {
			//前置增强
			invokeAdvice(method,advices,AdviceTypeConstant.BEFORE);
			//环绕增强
			System.out.println("当前事务是否存在:"+status.isTrans);
			if(isAdviceNeed(method) && advices!=null && advices.containsKey(AdviceTypeConstant.AROUND)) {
				List<Advice> aroundAdvices = advices.get(AdviceTypeConstant.AROUND);
				if(aroundAdvices!=null && !aroundAdvices.isEmpty()) {
					Advice advice = aroundAdvices.get(0);
					JoinPoint joinPoint = new JoinPoint(object, methodProxy, arg2);
					Method adviceMethod = advice.getAdviceMethod();
					Object aspect = advice.getAspect();
					try {
						//开启事务
						beginTransaction(status);
						//执行方法
						result=adviceMethod.invoke(aspect,joinPoint);
						//提交事务
						commitTransaction(status);
					} catch (Throwable e) {
						e.printStackTrace();
						//回滚事务
						for(Class<? extends Throwable> th:status.rollbackFor) {
							if(th.isAssignableFrom(e.getClass())) {
								rollbackTransaction(status);
							}
						}
						
					}
					finally {
						//释放事务
						closeTransaction(status);
					}
					
				}
				else {
					try {
						//开启事务
						beginTransaction(status);
						result= methodProxy.invokeSuper(object, arg2);
						commitTransaction(status);
					} catch (Throwable e) {
						e.printStackTrace();
						//回滚事务
						for(Class<? extends Throwable> th:status.rollbackFor) {
							if(th.isAssignableFrom(e.getClass())) {
								rollbackTransaction(status);
							}
						}
					}
					finally {
						closeTransaction(status);
					}
					
				}
			}
			else {
				try {
					//开启事务
					beginTransaction(status);
					result= methodProxy.invokeSuper(object, arg2);
					commitTransaction(status);
				} catch (Throwable e) {
					e.printStackTrace();
					//回滚事务
					for(Class<? extends Throwable> th:status.rollbackFor) {
						if(th.isAssignableFrom(e.getClass())) {
							rollbackTransaction(status);
						}
					}
				}
				finally {
					closeTransaction(status);
				}
			}
			//后置增强
			invokeAdvice(method,advices, AdviceTypeConstant.AFTER);
		} 
		catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			//异常增强
			invokeAdvice(method,advices, AdviceTypeConstant.AFTERTHROWING);
		}
		finally {
			//返回前增强
			invokeAdvice(method,advices, AdviceTypeConstant.AFTERRETURNING);
		}
		return result;
	}
	//执行通知增强
	private void invokeAdvice(Method method,Map<String, List<Advice>> advices,String type) throws Throwable {
		if(isAdviceNeed(method) && advices!=null && advices.containsKey(type)) {
			List<Advice> adviceList=advices.get(type);
			if(adviceList!=null && !adviceList.isEmpty()) {
				for(Advice advice:adviceList) {
					Method adviceMethod = advice.getAdviceMethod();
					Object aspect = advice.getAspect();
					adviceMethod.invoke(aspect);
				}
			}
		}
		
	}
	/**
	 * 判断是否增强
	 * @param method
	 * @return
	 */
	private boolean isAdviceNeed(Method method) {
		return methodAdvicesMap.containsKey(method);
	}
	/**
	 * 获得TransactionStatus对象
	 * @param object
	 * @param method
	 * @return
	 */
	private TransactionStatus geTransactionStatus(Object object,Method method) {
		TransactionStatus status = new TransactionStatus();
		status.isNeed = false;
		if(transactionManager!=null && (object.getClass().isAnnotationPresent(Transactional.class) || method.isAnnotationPresent(Transactional.class))) {
			status.isNeed = true;
			if(object.getClass().isAnnotationPresent(Transactional.class)) {
				Transactional transactional = object.getClass().getAnnotation(Transactional.class);
				status.isolationLevel = transactional.Isolation();
				status.propagationLevel = transactional.Propagation();
				status.rollbackFor = transactional.rollbackFor();
			}
			if(method.isAnnotationPresent(Transactional.class)){
				Transactional transactional = method.getAnnotation(Transactional.class);
				status.isolationLevel = transactional.Isolation();
				status.propagationLevel = transactional.Propagation();
				status.rollbackFor = transactional.rollbackFor();
			}
		}
		return status;
	}
	/**
	 * 判断是否需要事务管理
	 * @param status
	 * @return
	 */
	private boolean isTrasactionNeed(TransactionStatus status) {
		return status.isNeed;
	}
	/**
	 * 开启事务
	 * @param status
	 * @throws SQLException
	 */
	private void beginTransaction(TransactionStatus status) throws SQLException {
		if(isTrasactionNeed(status)) {
			transactionManager.beginTransaction(status);
			System.out.println("开启事务，事务Id="+transactionManager.getTransactionId());
		}
	}
	/**
	 * 提交事务
	 * @param status
	 * @throws SQLException
	 */
	private void commitTransaction(TransactionStatus status) throws SQLException {
		if(isTrasactionNeed(status)) {
			transactionManager.commit(status);
			System.out.println("提交事务，事务Id="+transactionManager.getTransactionId());
		}
	}
	/**
	 * 回滚事务
	 * @param status
	 * @throws SQLException
	 */
	private void rollbackTransaction(TransactionStatus status) throws SQLException {
		if(isTrasactionNeed(status)) {
			transactionManager.rollback();
			System.out.println("回滚事务，事务Id="+transactionManager.getTransactionId());
		}
	}
	/**
	 * 关闭事务
	 * @param status
	 * @throws SQLException
	 */
	private void closeTransaction(TransactionStatus status) throws SQLException {
		if(isTrasactionNeed(status)) {
			System.out.println("关闭事务，事务Id="+transactionManager.getTransactionId());
			transactionManager.closeTransaction(status);
		}
	}
}
