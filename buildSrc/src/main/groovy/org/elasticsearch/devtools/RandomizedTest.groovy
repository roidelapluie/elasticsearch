package org.elasticsearch.devtools

import com.carrotsearch.ant.tasks.junit4.JUnit4
import com.carrotsearch.ant.tasks.junit4.dependencies.com.google.common.eventbus.Subscribe
import com.carrotsearch.ant.tasks.junit4.events.aggregated.AggregatedStartEvent
import com.carrotsearch.ant.tasks.junit4.listeners.AggregatedEventListener
import groovy.xml.NamespaceBuilder
import org.apache.tools.ant.RuntimeConfigurable
import org.apache.tools.ant.Task
import org.apache.tools.ant.UnknownElement
import org.gradle.api.DefaultTask
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.ProgressLogger
import org.gradle.logging.ProgressLoggerFactory

import javax.inject.Inject

class RandomizedTest extends DefaultTask {

    static class JUnit4ProgressLogger implements AggregatedEventListener {
        private JUnit4 junit
        Logger logger
        ProgressLoggerFactory factory
        ProgressLogger progressLogger

        @Subscribe
        public void onStart(AggregatedStartEvent e) throws IOException {
            logger.info('START EVENT')
            progressLogger = factory.newOperation(JUnit4ProgressLogger.class)
            progressLogger.setDescription("Running JUnit4 " + e.getSuiteCount() + " test suites")
            progressLogger.started()
            progressLogger.progress("RUNNING TEST")
        }

        @Override
        public void setOuter(JUnit4 junit) {
            logger.info('OUTER SET')
            this.junit = junit;
        }
    }

    @Inject
    protected ProgressLoggerFactory getProgressLoggerFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    void executeTests() {
        def test1 = '**/*Test.class'
        def test2 = '**/*Tests.class'
        def sourceSet = ((SourceSetContainer)getProject().getProperties().get('sourceSets')).getByName('test')
        def workingDir = new File(getProject().buildDir, "run-test")
        ant.taskdef(resource: 'com/carrotsearch/junit4/antlib.xml',
                uri: 'junit4',
                classpath: getProject().configurations.testCompile.asPath)
        def junit4 = NamespaceBuilder.newInstance(ant, 'junit4')
        logger.lifecycle('RUNNING TESTS')
        logger.info('Junit4: ' + junit4)
        junit4.junit4(
                taskName: 'junit4',
                parallelism: 8,
                dir: workingDir) {
            classpath {
                pathElement(path: sourceSet.runtimeClasspath.asPath)
            }
            jvmarg(line: '-ea -esa')
            fileset(dir: sourceSet.output.classesDir) {
                include(name: test1)
                include(name: test2)
            }
            listeners {
                /*junit4.'report-text'(
                        showThrowable: true,
                        showStackTraces: true,
                        showOutput: 'onerror', // TODO: change to property
                        showStatusOk: false,
                        showStatusError: true,
                        showStatusFailure: true,
                        showStatusIgnored: true,
                        showSuiteSummary: true,
                        timestamps: false
                )*/
                makeProgressListener(ant)
            }
        }
    }

    def makeProgressListener(AntBuilder ant) {
        def name = 'gradle-report-listener'
        def logger = new JUnit4ProgressLogger(factory: getProgressLoggerFactory(), logger: getLogger())
        def context = ant.getAntXmlContext();
        def parentWrapper = context.currentWrapper()
        def parent = parentWrapper.getProxy()
        UnknownElement element = new UnknownElement(name)
        element.setProject(context.getProject())
        element.setRealThing(logger)
        //element.setRuntimeConfigurableWrapper(new RuntimeConfigurable(logger, name))
        ((UnknownElement)parent).addChild(element);
        RuntimeConfigurable wrapper = new RuntimeConfigurable(element, name);
        parentWrapper.addChild(wrapper)
        return wrapper.getProxy()
    }
}