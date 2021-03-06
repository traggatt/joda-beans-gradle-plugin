/**
 * Copyright 2014-2015 Andreas Schilling
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package org.joda.beans.gradle.tasks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.joda.beans.gradle.JodaBeansExtension;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;


/**
 * Abstract base class for both {@code validate} and {@code generate} tasks.
 *
 * @author Andreas Schilling
 *
 */
public abstract class AbstractJodaBeansTask extends DefaultTask
{
  private static final String JODA_BEANS_CODE_GEN_CLASS = "org.joda.beans.gen.BeanCodeGen";

  private static final String DEFAULT_INDENT = "4";

  private static final String DEFAULT_STRING_VALUE = "";
  
  private static final boolean DEFAULT_STRICT_VALUE = false;
  
  private static final String GROUP = "JodaBeans";
  
  @Override
  public final String getGroup() {
    return GROUP;
  }

  protected String getSourceDir()
  {
    return ((JodaBeansExtension) getProject().getExtensions().getByName( JodaBeansExtension.ID )).getSourceDir();
  }


  protected String getTestSourceDir()
  {
    return ((JodaBeansExtension) getProject().getExtensions().getByName( JodaBeansExtension.ID )).getTestSourceDir();
  }


  protected String getIndent()
  {
    final String indent =
            ((JodaBeansExtension) getProject().getExtensions().getByName( JodaBeansExtension.ID )).getIndent();
    return indent != null ? indent : DEFAULT_INDENT;
  }


  protected String getPrefix()
  {
    final String prefix =
            ((JodaBeansExtension) getProject().getExtensions().getByName( JodaBeansExtension.ID )).getPrefix();
    return prefix != null ? prefix : DEFAULT_STRING_VALUE;
  }


  protected Integer getVerbose()
  {
    return ((JodaBeansExtension) getProject().getExtensions().getByName( JodaBeansExtension.ID )).getVerbose();
  }

  protected boolean operateRecursive()
  {
    final Boolean recursive =
            ((JodaBeansExtension) getProject().getExtensions().getByName( JodaBeansExtension.ID )).getRecursive();
    return recursive != null ? recursive : true;
  }

  protected boolean isStrict()
  {
    final Boolean strict = 
           ((JodaBeansExtension) getProject().getExtensions().getByName( JodaBeansExtension.ID )).isStrict();
    return strict != null ? strict : DEFAULT_STRICT_VALUE;
  }


  protected abstract String getExecutionType();


  protected void runBeanGenerator()
  {
    getLogger().debug(
            "Running JodaBeans " + getExecutionType() + " in directory: " + getSourceDir()
                    + (Strings.isNullOrEmpty( getTestSourceDir() ) ? "" : ", test directory:" + getTestSourceDir()) );

    final ClassLoader classLoader = obtainClassLoader();
    Class<?> toolClass = null;
    try
    {
      toolClass = classLoader.loadClass( JODA_BEANS_CODE_GEN_CLASS );
    }
    catch( final Exception ex )
    {
      getLogger().error( "Skipping as joda-beans is not in the project compile classpath" );
    }
    final List<String> arguments = buildGeneratorArguments();
    getLogger().debug( "Using arguments " + arguments );
    runTool( toolClass, arguments );
    getLogger().debug( "JodaBeans " + getExecutionType() + " successfully completed." );
  }


  /**
   * Builds the arguments to the tool.
   *
   * @return the arguments, not null
   */
  protected List<String> buildGeneratorArguments()
  {
    final List<String> arguments = Lists.newArrayList();
    if( operateRecursive() )
    {
      arguments.add( "-R" );
    }
    if( getIndent() != null )
    {
      arguments.add( "-indent=" + getIndent() );
    }
    if( getPrefix() != null )
    {
      arguments.add( "-prefix=" + getPrefix() );
    }
    if( getVerbose() != null )
    {
      arguments.add( "-v=" + getVerbose() );
    }
    return arguments;
  }


  /**
   * Obtains the classloader from a set of file paths.
   *
   * @return the classloader, not null
   */
  protected ClassLoader obtainClassLoader()
  {
    return AbstractJodaBeansTask.class.getClassLoader();
  }


  protected int runTool( final Class<?> toolClass, final List<String> argsList )
  {
    final String sourceDir = getSourceDir();
    if( Strings.isNullOrEmpty( sourceDir ) )
    {
      throw new GradleException( "Source directory must be given!" );
    }
    argsList.add( sourceDir );
    int count = invoke( toolClass, argsList );
    // optionally invoke test source
    if( !Strings.isNullOrEmpty( getTestSourceDir() ) )
    {
      argsList.set( argsList.size() - 1, getTestSourceDir() );
      count += invoke( toolClass, argsList );
    }
    return count;
  }


  private int invoke( final Class<?> toolClass, final List<String> argsList )
  {
    final Method createFromArgsMethod = findCreateFromArgsMethod( toolClass );
    final Method processMethod = findProcessMethod( toolClass );
    final Object beanCodeGen = createBuilder( argsList, createFromArgsMethod );
    return invokeBuilder( processMethod, beanCodeGen );
  }


  private Object createBuilder( final List<String> argsList, final Method createFromArgsMethod ) throws GradleException
  {
    final String[] args = argsList.toArray( new String[argsList.size()] );
    try
    {
      return createFromArgsMethod.invoke( null, new Object[] { args } );
    }
    catch( final IllegalArgumentException ex )
    {
      throw new GradleException( "Error invoking BeanCodeGen.createFromArgs()" );
    }
    catch( final IllegalAccessException ex )
    {
      throw new GradleException( "Error invoking BeanCodeGen.createFromArgs()" );
    }
    catch( final InvocationTargetException ex )
    {
      throw new GradleException( "Invalid Joda-Beans Mojo configuration: " + ex.getCause().getMessage(), ex.getCause() );
    }
  }


  private int invokeBuilder( final Method processMethod, final Object beanCodeGen ) throws GradleException
  {
    try
    {
      return (Integer) processMethod.invoke( beanCodeGen );
    }
    catch( final IllegalArgumentException ex )
    {
      throw new GradleException( "Error invoking BeanCodeGen.process()" );
    }
    catch( final IllegalAccessException ex )
    {
      throw new GradleException( "Error invoking BeanCodeGen.process()" );
    }
    catch( final InvocationTargetException ex )
    {
      throw new GradleException( "Error while running Joda-Beans tool: " + ex.getCause().getMessage(), ex.getCause() );
    }
  }


  private Method findCreateFromArgsMethod( final Class<?> toolClass )
  {
    Method createFromArgsMethod = null;
    try
    {
      createFromArgsMethod = toolClass.getMethod( "createFromArgs", String[].class );
    }
    catch( final Exception ex )
    {
      throw new GradleException( "Unable to find method BeanCodeGen.createFromArgs()" );
    }
    return createFromArgsMethod;
  }


  private Method findProcessMethod( final Class<?> toolClass ) throws GradleException
  {
    Method processMethod = null;
    try
    {
      processMethod = toolClass.getMethod( "process" );
    }
    catch( final Exception ex )
    {
      throw new GradleException( "Unable to find method BeanCodeGen.process()" );
    }
    return processMethod;
  }
}
