package com.pccw.cloud.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;
import javassist.Modifier;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;

/**
 * 字节码插桩
 * javaagent 代理拦截(插桩入口)
 * javassist 字节码修改工具(插入哪些语句)
 * */
public class CommonAgent {
	
	private static Logger log_ = LoggerFactory.getLogger(CommonAgent.class);
	
	public static String agent ;
	
	//用于获取方法开始时间
	public final static String prefix = "long startTime = System.currentTimeMillis();\n";
	//用于获取方法结束时间
	public final static String postfix = "long endTime = System.currentTimeMillis();";
	
	public static final String startfix = "\n { \n";
	
	public static final String tryfix = "try { \n";
	
	public static final String catchfix = "}catch (Exception e) { \n";
	
	public static final String exceptionfix = "e.printStackTrace();\n";
	
	public static final String endfix = "\n } \n";
	
	public static final String reqfix = "req";
	
	public static final String resfix = "res";
	
	
	
	public static void premain(String args,Instrumentation instrumentation) {
		log_.info("premain 方法开始");
		instrumentation.addTransformer(new ClassFileTransformer() {
			
			public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
					ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
				if(loader==null||className==null) {
					//类加载器或者类名字为空时不去修改对应文件的字节码
					return null;
				}
				
				//修改指定文件的字节码
				String classNameNew = className.replaceAll("/", ".");
				
				try {
					if("javax.servlet.http.HttpServlet".equals(classNameNew)) {
						String traceId = UUID.randomUUID().toString().replace("-", "");
						//创建javassist pool
						ClassPool pool = new ClassPool();
						pool.insertClassPath(new LoaderClassPath(loader));
						//在JAVASSIST import额外的包
						pool.importPackage("javax.servlet.http.HttpServletRequest");
						CtClass ctClass = pool.getCtClass(classNameNew);
						String methodStr = "service";
							String outputStr = "\nSystem.out.println(\"this method " + methodStr
		                            + " cost:\" +(endTime - startTime)/1000 +\" s.\");";
							//获取需要修改的方法实例
							CtMethod method = ctClass.getDeclaredMethod(methodStr);
							//获取参数类型
							CtClass[] parameterCtClasses = new CtClass[method.getParameterTypes().length];
							for (int i = 0; i < parameterCtClasses.length; i++) 
							    parameterCtClasses[i] = pool.getCtClass(method.getParameterTypes()[i].getName());
							
							String[] parameterNames = new String[parameterCtClasses.length];
							CtClass[] cls = method.getParameterTypes();
							for(CtClass c:cls) {
								log_.info(c.getName());
							}
							MethodInfo methodInfo = method.getMethodInfo();
							CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
							LocalVariableAttribute attr = (LocalVariableAttribute) codeAttribute.getAttribute(LocalVariableAttribute.tag);
							TreeMap<Integer, String> sortMap = new TreeMap<Integer, String>();
							for (int i = 0; i < attr.tableLength(); i++) 
							    sortMap.put(attr.index(i), attr.variableName(i));
							int pos = Modifier.isStatic(method.getModifiers()) ? 0 : 1;
							//获取参数名称
							parameterNames = Arrays.copyOfRange(sortMap.values().toArray(new String[0]), pos, parameterNames.length + pos);
//							for(String str :parameterNames) {
//								String temp = "System.out.println(\"";
//								temp += str+" is \"+";
//								temp += str+");";
//								temp += "System.out.println(\"traceId is "+traceId+"\");";
//								method.insertBefore(temp);
//							}
							//输出方法中所有参数信息
							StringBuilder build = new StringBuilder();
							build.append("System.out.println(\" traceId is ").append(traceId).append("\");");
							String parameterName = "";
							for(int i=0 ,j = parameterNames.length ;i < j ;i++) {
								parameterName = parameterNames[i];
								//可以使用$1代表第一个参数,$2代表第二个参数以此类推
								if(reqfix.equals(parameterName)) {
									build.append("\nHttpServletRequest request1 = (HttpServletRequest)req;\n");
								}
								if(resfix.equals(parameterName)) {
									build.append("\nHttpServletResponse response1 = (HttpServletResponse)res;\n");
								}
								build.append("System.out.println(\"").append(parameterName).append(" is \"+$").append((i+1)).append(" );");
							}
							method.insertBefore(build.toString());
							String newMethodName = methodStr + "$Old";
							//将原来的方法名字改变
							method.setName(newMethodName);
							//创建新方法
							CtMethod newMethod = CtNewMethod.copy(method, methodStr, ctClass, null);
							// 构建新的方法体
							StringBuilder bodyStr = new StringBuilder();
							//将本次的方法都放在一个代码块中
							bodyStr.append(startfix);
							bodyStr.append(prefix);
							bodyStr.append(tryfix);
							//调用原方法($$)表示所有的参数
							bodyStr.append(newMethodName + "($$);\n");
							bodyStr.append(catchfix);
							bodyStr.append(exceptionfix);
							bodyStr.append(endfix);
							bodyStr.append(postfix);
							//输出方法耗时
							bodyStr.append(outputStr);
							bodyStr.append(endfix);
							// 替换新方法
//							log_.info(bodyStr.toString());
							newMethod.setBody(bodyStr.toString());
							// 增加新方法
							ctClass.addMethod(newMethod);
						return ctClass.toBytecode();
					}else {
						return null;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}
		});
	}
}