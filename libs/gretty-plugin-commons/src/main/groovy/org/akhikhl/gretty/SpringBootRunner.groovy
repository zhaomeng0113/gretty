/*
 * gretty
 *
 * Copyright 2013  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.gretty

import java.util.concurrent.Executors
import groovy.json.JsonBuilder
import java.util.concurrent.ExecutorService
import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class SpringBootRunner {
	
  protected static final Logger log = LoggerFactory.getLogger(SpringBootRunner)

  protected final StartBaseTask startTask
  protected final Project project
  protected ServerConfig sconfig
  protected Iterable<WebAppConfig> webAppConfigs
  protected final ExecutorService executorService

  SpringBootRunner(StartBaseTask startTask) {
    this.startTask = startTask
    project = startTask.project
    RunConfig runConfig = startTask.getRunConfig()
    sconfig = runConfig.getServerConfig()
    webAppConfigs = runConfig.getWebAppConfigs()
    executorService = Executors.newSingleThreadExecutor()
  }
  
  private String findMainClass() {
		def bootExtension = project.extensions.findByName('springBoot')
		if(bootExtension && bootExtension.mainClass)
			return bootExtension.mainClass
    def MainClassFinder = Class.forName('org.springframework.boot.loader.tools.MainClassFinder', true, getClass().classLoader)
    return MainClassFinder.findSingleMainClass(project.sourceSets.main.output.classesDir)
  }

  private getCommandLineJson() {
    def json = new JsonBuilder()
    json {
      mainClass findMainClass()
      servicePort sconfig.servicePort
      statusPort sconfig.statusPort
    }
    json
  }

  void run() {
    println "running spring-boot app!"
    runSpringBoot()
  }

  protected void runSpringBoot() {

    def cmdLineJson = getCommandLineJson()
    log.warn 'Command-line json: {}', cmdLineJson.toPrettyString()
    cmdLineJson = cmdLineJson.toString()

    // we are going to pass json as argument to java process.
    // under windows we must escape double quotes in process parameters.
    if(System.getProperty("os.name") =~ /(?i).*windows.*/)
      cmdLineJson = cmdLineJson.replace('"', '\\"')
    
    project.javaexec { spec ->
      spec.classpath = project.files(project.configurations.gretty.files + project.configurations.springBoot.files + [ project.sourceSets.main.output.classesDir, project.sourceSets.main.output.resourcesDir ])
      // project.files(project.configurations.gretty.files + project.configurations.compile.files.findAll { !it.name.startsWith('jetty-webapp') })
      // .files + project.configurations.compile.files.findAll { it.name.startsWith('spring') }
      spec.main = 'org.akhikhl.gretty.SpringBootRunner'
      spec.args = [ cmdLineJson ]
      spec.debug = startTask.debug
      log.debug 'server-config jvmArgs: {}', sconfig.jvmArgs
      spec.jvmArgs sconfig.jvmArgs
      if(startTask.jacoco) {
        String jarg = startTask.jacoco.getAsJvmArg()
        log.debug 'jacoco jvmArgs: {}', jarg
        spec.jvmArgs jarg
      }
    }
  }  
}