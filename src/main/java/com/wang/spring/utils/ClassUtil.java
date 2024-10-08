package com.wang.spring.utils;

import java.io.File;
import java.io.FileFilter;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ClassUtil {
  /**
   * 获取类加载器
   */
  public static ClassLoader getClassLoader() {
      return Thread.currentThread().getContextClassLoader();
  }

  /**
   * 加载类
   * @param className 类名
   * @param isInitialized 是否初始化
   * @return
   */
  public static Class<?> loadClass(String className, boolean isInitialized) {
      Class<?> cls;
      try {
          cls = Class.forName(className, isInitialized, getClassLoader());
      } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
      }
      return cls;
  }

  /**
   * 加载类（默认将初始化类）
   */
  public static Class<?> loadClass(String className) {
      return loadClass(className, true);
  }

  /**
   * 获取指定包名下的所有类
   */
  public static Set<Class<?>> getClassSet(String packageName) {
      Set<Class<?>> classSet = new HashSet<Class<?>>();
      try {
          Enumeration<URL> urls = getClassLoader().getResources(packageName.replace(".", "/"));
          //遍历资源路径 urls，判断每个 URL 的协议类型：
          //file 协议：表示该资源在文件系统中，调用 addClass 方法处理文件系统中的类。
          //jar 协议：表示该资源在 JAR 文件中，使用 JarURLConnection 处理 JAR 文件中的类。
          while (urls.hasMoreElements()) {
              URL url = urls.nextElement();
              if (url != null) {
                  String protocol = url.getProtocol();
                  if (protocol.equals("file")) {
                      String packagePath = url.getPath().replaceAll("%20", " ");
                      addClass(classSet, packagePath, packageName);
                  } else if (protocol.equals("jar")) {
                      JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection();
                      if (jarURLConnection != null) {
                          JarFile jarFile = jarURLConnection.getJarFile();
                          if (jarFile != null) {
                              Enumeration<JarEntry> jarEntries = jarFile.entries();
                              while (jarEntries.hasMoreElements()) {
                                  JarEntry jarEntry = jarEntries.nextElement();
                                  String jarEntryName = jarEntry.getName();
                                  if (jarEntryName.endsWith(".class")) {
                                      String className = jarEntryName.substring(0, jarEntryName.lastIndexOf(".")).replaceAll("/", ".");
                                      doAddClass(classSet, className);
                                  }
                              }
                          }
                      }
                  }
              }
          }
      } catch (Exception e) {
          throw new RuntimeException(e);
      }
      return classSet;
  }
  /**
   * 将指定路径下的类对象添加到集合classSet中
   * @param classSet
   * @param packagePath
   * @param packageName
   */
  private static void addClass(Set<Class<?>> classSet, String packagePath, String packageName) {
      File[] files = new File(packagePath).listFiles(new FileFilter() {
          public boolean accept(File file) {
              return (file.isFile() && file.getName().endsWith(".class")) || file.isDirectory();
          }
      });
      for (File file : files) {
          String fileName = file.getName();
          if (file.isFile()) {
              // 处理类文件
              String className = fileName.substring(0, fileName.lastIndexOf("."));
              if (packageName != null && packageName != "") {
                  className = packageName + "." + className;
              }
              doAddClass(classSet, className);
          } else {
              // 处理目录
              String subPackagePath = fileName;
              if (packagePath != null && packagePath != "") {
                  subPackagePath = packagePath + "/" + subPackagePath;
              }
              String subPackageName = fileName;
              if (packageName != null && packageName != "") {
                  subPackageName = packageName + "." + subPackageName;
              }
              addClass(classSet, subPackagePath, subPackageName);
          }
      }
  }
  /**
   * 加载并添加类对象
   * @param classSet
   * @param className
   */
  private static void doAddClass(Set<Class<?>> classSet, String className) {
      Class<?> cls = loadClass(className, false);
      classSet.add(cls);
  }
}
