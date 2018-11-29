/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.session;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;

/**
 * Builds {@link SqlSession} instances.
 * 工厂模式 构建SqlSessionFactory的工厂
 * 8种build方法分别支持字节流和字符流
 * @author Clinton Begin
 */
public class SqlSessionFactoryBuilder {

  /**
   * 字符流生成方法，最终调用第四种的build生成SqlSessionFactory
   * @param reader
   * @return
   */

  public SqlSessionFactory build(Reader reader) {
    return build(reader, null, null);
  }

  /**
   * 字符流生成方法，最终调用第四种的build生成SqlSessionFactory
   * @param reader
   * @param environment
   * @return
   */
  public SqlSessionFactory build(Reader reader, String environment) {
    return build(reader, environment, null);
  }

  /**
   * 字符流生成方法，最终调用第四种的build生成SqlSessionFactory
   * @param reader
   * @param properties
   * @return
   */
  public SqlSessionFactory build(Reader reader, Properties properties) {
    return build(reader, null, properties);
  }

  /**
   * 字符流生成方法
   * 可选的参数是environment和properties
   * Environment决定加载哪种环境(开发环境/生产环境)，包括数据源和事务管理器。
   * properties 加载属性配置文件,属性可以用${propName}语法形式多次用在配置文件中
   * @param reader
   * @param environment
   * @param properties
   * @return
   */
  public SqlSessionFactory build(Reader reader, String environment, Properties properties) {
    try {
      //XMLConfigBuilder来解析xml文件，并构建
      XMLConfigBuilder parser = new XMLConfigBuilder(reader, environment, properties);
      return build(parser.parse());
    } catch (Exception e) {
      //捕获异常，包装后抛出
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } finally {
      ErrorContext.instance().reset();
      try {
        reader.close();
      } catch (IOException e) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }

  /**
   * 字节流生成方法，最终调用第八种的build生成SqlSessionFactory
   * @param inputStream
   * @return
   */
  public SqlSessionFactory build(InputStream inputStream) {
    return build(inputStream, null, null);
  }

  /**
   * 字节流生成方法，最终调用第八种的build生成SqlSessionFactory
   * @param inputStream
   * @param environment
   * @return
   */
  public SqlSessionFactory build(InputStream inputStream, String environment) {
    return build(inputStream, environment, null);
  }

  /**
   * 字节流生成方法，最终调用第八种的build生成SqlSessionFactory
   * @param inputStream
   * @param properties
   * @return
   */
  public SqlSessionFactory build(InputStream inputStream, Properties properties) {
    return build(inputStream, null, properties);
  }

  /**
   * 字节流生成方法,与第四种build实现方式相同，入参由Reader换成了InputStream
   * @param inputStream
   * @param environment
   * @param properties
   * @return
   */
  public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
    try {
      XMLConfigBuilder parser = new XMLConfigBuilder(inputStream, environment, properties);
      return build(parser.parse());
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } finally {
      ErrorContext.instance().reset();
      try {
        inputStream.close();
      } catch (IOException e) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }

  /**
   * 使用了Configuration作为参数,并返回DefaultSqlSessionFactory
   * @param config
   * @return
   */
  public SqlSessionFactory build(Configuration config) {
    return new DefaultSqlSessionFactory(config);
  }

}
