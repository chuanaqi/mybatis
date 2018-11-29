/**
 *    Copyright 2009-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * 建造者模式：XML配置构建器，继承BaseBuilder
 * 六种构造方法分别支持字符流和字节流
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  /**
   * 是否已解析，XPath解析器,环境
   */
  private boolean parsed;
  private final XPathParser parser;
  private String environment;
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  /**
   * 6个构造函数最后都合流到这个函数，传入XPathParser
   * @param parser
   * @param environment
   * @param props
   */
  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    //调用父类初始化Configuration
    super(new Configuration());
    //TODO (先看主流程，后面再看这个)错误上下文设置成SQL Mapper Configuration(XML文件配置)
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  /**
   * 解析配置
   * @return
   */
  public Configuration parse() {
    //如果已经解析过了，报错
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    //根节点configuration
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  /**
   * 解析配置
   * 要解析的XML的结构
   * <configuration>

       <properties resource="org/apache/ibatis/databases/blog/blog-derby.properties"/>

       <settings>
         <setting name="cacheEnabled" value="true"/>
         <setting name="lazyLoadingEnabled" value="false"/>
         <setting name="multipleResultSetsEnabled" value="true"/>
         <setting name="useColumnLabel" value="true"/>
         <setting name="useGeneratedKeys" value="false"/>
         <setting name="defaultExecutorType" value="SIMPLE"/>
         <setting name="defaultStatementTimeout" value="25"/>
       </settings>

       <typeAliases>
         <typeAlias alias="Author" type="org.apache.ibatis.domain.blog.Author"/>
         <typeAlias alias="Blog" type="org.apache.ibatis.domain.blog.Blog"/>
         <typeAlias alias="Comment" type="org.apache.ibatis.domain.blog.Comment"/>
         <typeAlias alias="Post" type="org.apache.ibatis.domain.blog.Post"/>
         <typeAlias alias="Section" type="org.apache.ibatis.domain.blog.Section"/>
         <typeAlias alias="Tag" type="org.apache.ibatis.domain.blog.Tag"/>
       </typeAliases>

       <typeHandlers>
         <typeHandler javaType="String" jdbcType="VARCHAR" handler="org.apache.ibatis.builder.CustomStringTypeHandler"/>
       </typeHandlers>

       <objectFactory type="org.apache.ibatis.builder.ExampleObjectFactory">
         <property name="objectFactoryProperty" value="100"/>
       </objectFactory>

       <plugins>
         <plugin interceptor="org.apache.ibatis.builder.ExamplePlugin">
            <property name="pluginProperty" value="100"/>
         </plugin>
       </plugins>

       <environments default="development">
         <environment id="development">
           <transactionManager type="JDBC">
             <property name="" value=""/>
           </transactionManager>
           <dataSource type="UNPOOLED">
             <property name="driver" value="${driver}"/>
             <property name="url" value="${url}"/>
             <property name="username" value="${username}"/>
             <property name="password" value="${password}"/>
           </dataSource>
         </environment>
       </environments>

       <mappers>
         <mapper resource="org/apache/ibatis/builder/AuthorMapper.xml"/>
         <mapper resource="org/apache/ibatis/builder/BlogMapper.xml"/>
         <mapper resource="org/apache/ibatis/builder/CachedAuthorMapper.xml"/>
         <mapper resource="org/apache/ibatis/builder/PostMapper.xml"/>
         <mapper resource="org/apache/ibatis/builder/NestedBlogMapper.xml"/>
       </mappers>

   </configuration>
   *
   * @param root
   */
  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      //1.properties
      propertiesElement(root.evalNode("properties"));
      //2.设置
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      //加载自定义虚拟文件系统
      loadCustomVfs(settings);
      //3.类型别名
      typeAliasesElement(root.evalNode("typeAliases"));
      //4.插件
      pluginElement(root.evalNode("plugins"));
      //5.对象工厂
      objectFactoryElement(root.evalNode("objectFactory"));
      //6.对象包装工厂
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      //7.反射工厂
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      //这里应该是一些默认设置
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      //8.环境
      environmentsElement(root.evalNode("environments"));
      //9.databaseIdProvider
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      //10.类型处理器
      typeHandlerElement(root.evalNode("typeHandlers"));
      //11.映射器
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  /**
   * <settings>
       <setting name="cacheEnabled" value="true"/>
       <setting name="lazyLoadingEnabled" value="false"/>
       <setting name="multipleResultSetsEnabled" value="true"/>
       <setting name="useColumnLabel" value="true"/>
       <setting name="useGeneratedKeys" value="false"/>
       <setting name="defaultExecutorType" value="SIMPLE"/>
       <setting name="defaultStatementTimeout" value="25"/>
     </settings>
   * @param context
   * @return
   */
  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  /**
   * 加载自定义虚拟文件系统
   * @param props
   * @throws ClassNotFoundException
   */
  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  /**
   *  <typeAliases>
       <typeAlias alias="Author" type="org.apache.ibatis.domain.blog.Author"/>
       <typeAlias alias="Blog" type="org.apache.ibatis.domain.blog.Blog"/>
       <typeAlias alias="Comment" type="org.apache.ibatis.domain.blog.Comment"/>
       <typeAlias alias="Post" type="org.apache.ibatis.domain.blog.Post"/>
       <typeAlias alias="Section" type="org.apache.ibatis.domain.blog.Section"/>
       <typeAlias alias="Tag" type="org.apache.ibatis.domain.blog.Tag"/>
      </typeAliases>
   * @param parent
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      //遍历子节点
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          //如果是package,调用TypeAliasRegistry.registerAliases，去包下找所有类,然后注册别名(有@Alias注解则用，没有则取类的simpleName)
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          //如果是typeAlias 根据Class名字来注册类型别名
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            //调用TypeAliasRegistry.registerAlias注册
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   * MyBatis 允许你在某一点拦截已映射语句执行的调用。默认情况下,MyBatis 允许使用插件来拦截方法调用
   *  <plugins>
       <plugin interceptor="org.apache.ibatis.builder.ExamplePlugin">
        <property name="pluginProperty" value="100"/>
       </plugin>
      </plugins>
   * @param parent
   * @throws Exception
   */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        String interceptor = child.getStringAttribute("interceptor");
        Properties properties = child.getChildrenAsProperties();
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        interceptorInstance.setProperties(properties);
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  /**
   * 对象工厂,可以自定义对象创建的方式,如使用对象池
   * <objectFactory type="org.apache.ibatis.builder.ExampleObjectFactory">
       <property name="objectFactoryProperty" value="100"/>
     </objectFactory>
   * @param context
   * @throws Exception
   */
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
       String type = context.getStringAttribute("type");
       ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
       configuration.setReflectorFactory(factory);
    }
  }

  /**
   *  解析properties标签
   *  <properties resource="org/apache/ibatis/databases/blog/blog-derby.properties"/>
   *    <property name="username" value="aaa"/>
   *    <property name="password" value="bbb"/>
   *  </properties>
   * @param context
   * @throws Exception
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      //获取子节点所有的Properties
      Properties defaults = context.getChildrenAsProperties();
      //然后查找resource或者url,加入defaults
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      //configuration中的Variables也全部加入defaults
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  private void settingsElement(Properties props) throws Exception {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler> typeHandler = (Class<? extends TypeHandler>)resolveClass(props.getProperty("defaultEnumTypeHandler"));
    configuration.setDefaultEnumTypeHandler(typeHandler);
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    @SuppressWarnings("unchecked")
    Class<? extends Log> logImpl = (Class<? extends Log>)resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }


  /**
   *  <environments default="development">
       <environment id="development">
         <transactionManager type="JDBC">
           <property name="" value=""/>
         </transactionManager>
         <dataSource type="UNPOOLED">
           <property name="driver" value="${driver}"/>
           <property name="url" value="${url}"/>
           <property name="username" value="${username}"/>
           <property name="password" value="${password}"/>
         </dataSource>
       </environment>
     </environments>
   * @param context
   * @throws Exception
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        //循环比较id是否等于environment
        if (isSpecifiedEnvironment(id)) {
          //事务管理器
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          //数据源
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  /**
   * 可以根据不同数据库执行不同的SQL，sql要加databaseId属性
   * 参考org.apache.ibatis.submitted.multidb包里的测试用例
   * <databaseIdProvider type="VENDOR">
   	  <property name="SQL Server" value="sqlserver"/>
   	  <property name="DB2" value="db2"/>
   	  <property name="Oracle" value="oracle" />
   	</databaseIdProvider>
   * @param context
   * @throws Exception
   */
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      //与老版本兼容
      if ("VENDOR".equals(type)) {
          type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      //得到当前的databaseId
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  /**
   * 类型处理器
   *  <typeHandlers>
        <typeHandler javaType="String" jdbcType="VARCHAR" handler="org.apache.ibatis.builder.CustomStringTypeHandler"/>
      </typeHandlers>
   * @param parent
   * @throws Exception
   */
  private void typeHandlerElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          //如果是package 调用TypeHandlerRegistry.register，去包下找所有类
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          //调用TypeHandlerRegistry.register(以下是3种不同的参数形式)
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * 自动扫描包下所有映射器
   * <mappers>
       <mapper resource="org/apache/ibatis/builder/AuthorMapper.xml"/>
       <mapper resource="org/apache/ibatis/builder/BlogMapper.xml"/>
       <mapper resource="org/apache/ibatis/builder/CachedAuthorMapper.xml"/>
       <mapper resource="org/apache/ibatis/builder/PostMapper.xml"/>
       <mapper resource="org/apache/ibatis/builder/NestedBlogMapper.xml"/>
     </mappers>
   * @param parent
   * @throws Exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          //自动扫描包下所有映射器
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          if (resource != null && url == null && mapperClass == null) {
            //使用类路径
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            //TODO 解析mapper标签
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            //使用绝对url路径
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            //使用java类名
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            //直接把这个映射加入配置
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  /**
   * 比较id和environment是否相等
   * @param id
   * @return
   */
  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
