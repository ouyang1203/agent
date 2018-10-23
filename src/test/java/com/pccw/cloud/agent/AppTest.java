package com.pccw.cloud.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AppTest {    
   private static Logger log_ = LoggerFactory.getLogger(AppTest.class);
   public static void main(String[] args) {
	try {
		testInfo("1121","@@@",200);
	} catch (Exception e) {
		
	}
   }
   public static void testInfo(String test,String test11,Integer a) throws Exception{
	   log_.info("main method");
	   throw new RuntimeException("测试异常捕获");
   }
   
}
