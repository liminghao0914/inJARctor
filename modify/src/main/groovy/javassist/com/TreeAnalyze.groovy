package javassist.com

import com.android.build.gradle.shrinker.parser.Matcher
import javassist.*
import javassist.bytecode.*
import org.gradle.internal.impldep.org.intellij.lang.annotations.Pattern
import com.sun.tools.jdeps.JdepsTask

//TODO JdepsTask.run(): No signature of method: static com.sun.tools.jdeps.JdepsTask.run() is applicable for argument types: ([Ljava.lang.String;) values: [[-e "com.*" -include "com.*" -dotoutput ./  classes-enjarify.jar]]
//  Possible solutions: dump(), any(), grep(), find(), is(java.lang.Object), any(groovy.lang.Closure)
// 待解决 可延后
