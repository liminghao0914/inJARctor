package javassist.com

import com.android.SdkConstants
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.ide.common.xml.ManifestData
import com.sun.tools.jdeps.JdepsTask
import javassist.*
import javassist.bytecode.MethodInfo
import org.codehaus.groovy.tools.shell.util.PackageHelper
import org.gradle.api.Project
import org.json.simple.JSONObject

import javax.xml.crypto.dsig.TransformException
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.regex.Pattern

class ModifyTransform extends Transform {

//    private static final def CLICK_LISTENER = "android.view.View\$OnClickListener"

    def pool = ClassPool.default
    def project

    //JAR路径
    def FilePath = "/Users/liminghao/Desktop/decompile/inJARctor/classes-enjarify.jar"

    ModifyTransform(Project project) {
        this.project = project
    }

    @Override
    String getName() {
        return "ModifyTransform"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)

        project.android.bootClasspath.each {
            pool.appendClassPath(it.absolutePath)
//            pool.appendClassPath("/Users/liminghao/Downloads/javassist-master")


            println(FilePath)
            mainModify(FilePath)
        }

    }

    //TODO BUG 待解决 可延后
    //TODO 已集成在server端
    private int generateSummaryDotFile(String[] var1){
        int i = JdepsTask.run(var1)
        return i
    }

    //名称转换
    private String toFileNamne(String ClassName){
         return ClassName.replaceAll("\\.", "/")+SdkConstants.DOT_CLASS
    }

    private String toClassName(String fileName){
        def className = fileName.replace("/", ".")
        return className.replace(SdkConstants.DOT_CLASS, "")
    }

    //Jar获取所有class
    private List<String> getAllClassName(String jarName){
        List<String> myClassName = new ArrayList<String>();
        JarFile jarFile = new JarFile(jarName);
        Enumeration<JarEntry> entrys = jarFile.entries();
        while (entrys.hasMoreElements()) {
            JarEntry jarEntry = entrys.nextElement();
            String fileName = jarEntry.getName();
//            println("Find fileName: "+fileName)
            String className = toClassName(fileName)
            myClassName.add(className);
//            println("Find className: "+className);
        }
        return myClassName;
    }

    //获取class中所有method名称
    private List<String> getAllMethodName(CtClass ctClass){
        List<String> AllMethodsName = new ArrayList<String>();
        CtMethod[] methods = ctClass.getDeclaredMethods();
        for (CtMethod m : methods) {
            AllMethodsName.add(m.getName());
        }
        return AllMethodsName;
    }

    //获取class中所有method
    private List<CtMethod> getAllMethod(CtClass ctClass){
        return ctClass.getDeclaredMethods();
    }

    //Jar注入
    private void JarInjector(CtClass ctClass, String FileName){
        byte[] b = ctClass.toBytecode();
        JarHandler jarHandler = new JarHandler();
        jarHandler.replaceJarFile(FilePath, b, FileName);
        println(FileName + " injected!")
    }

    //在方法内第一行插入代码
    private void modifyMethodByInsertAfter(CtClass ctClass, String MethodName, String body){
        CtMethod method = ctClass.getDeclaredMethod(MethodName)
        method.insertAfter(body)
        println(ctClass.name+" InsertAfter "+MethodName)
    }

    //在方法内最后一行插入代码
    private void modifyMethodByInsertBefore(CtClass ctClass, String MethodName, String body){
        CtMethod method = ctClass.getDeclaredMethod(MethodName)
        method.insertBefore(body)
        println(ctClass.name+" InsertBefore "+MethodName)
    }

    //添加方法
    private void addMethodInClass(CtClass ctClass, String body){
        CtMethod cm = CtMethod.make(body, ctClass)
        ctClass.addMethod(cm);
    }

    private void addLocalVariableInMethod(CtClass ctClass,String MethodName,String vname,CtClass type, String value){
        CtMethod method = ctClass.getDeclaredMethod(MethodName)
        method.addLocalVariable(vname,type)
        method.insertBefore(vname+"="+value)
    }

    private void addLocalVariableInMethod(CtClass ctClass,String MethodName,String vname,CtClass type){
        CtMethod method = ctClass.getDeclaredMethod(MethodName)
        method.addLocalVariable(vname,type)
    }

    private void InsertHttpMethod(){
        def HttpMethod = []
    }

    private void addMD5UtilsClass(){
        def ClassName = "com.injarctor.MD5Utils"
        CtClass ctClass = pool.makeClass(ClassName);
        pool.importPackage("java.security.MessageDigest");
        pool.importPackage("java.lang.StringBuilder");
        pool.importPackage("java.lang.String");
        pool.importPackage("java.lang.Integer");
        String getMD5String = "" +
                "    public static String getMD5String(String string) {\n" +
                "        if (string.length()==0) {\n" +
                "            return \"\";\n" +
                "        }\n" +
                "        MessageDigest md5 = null;\n" +
                "        try {\n" +
                "            md5 = MessageDigest.getInstance(\"MD5\");\n" +
                "            byte[] bytes = md5.digest(string.getBytes());\n" +
                "            String result = \"\";\n" +
                "            for (int i=0;i<bytes.length;i++) {\n" +
                "                byte b = bytes[i];\n" +
                "                String temp = Integer.toHexString(b & 0xff);\n" +
                "                if (temp.length() == 1) {\n" +
                "                    temp = \"0\" + temp;\n" +
                "                }\n" +
                "                result += temp;\n" +
                "            }\n" +
                "            return result.toUpperCase();\n" +
                "        } catch (java.lang.Exception e) {\n" +
                "            e.printStackTrace();\n" +
                "        }\n" +
                "        return \"\";\n" +
                "    }" +
                ""
        addMethodInClass(ctClass, getMD5String);
        JarInjector(ctClass, toFileNamne(ClassName));
    }

    private void addHttpConnectionClass(){
        def ClassName = "com.injarctor.HttpConnection"
        CtClass ctClass = pool.makeClass(ClassName);
        //添加package
        pool.importPackage("java.net.URL");
        pool.importPackage("java.net.HttpURLConnection");
        pool.importPackage("org.json.JSONObject");
        pool.importPackage("java.io.OutputStream");
        pool.importPackage("java.io.InputStream");
        CtMethod ctMethod = new CtMethod(
                CtClass.voidType, "HttpPostMethod",
                [pool.get("java.lang.String"), pool.get("java.lang.String")] as CtClass[],
                ctClass);//(String url, String msg)
        ctMethod.setModifiers(Modifier.PUBLIC);
        ctMethod.setBody("{JSONObject  obj = new JSONObject(\$2);\n" +
                "        URL url=new URL(\$1);  \n" +
                "        HttpURLConnection conn = (HttpURLConnection) url.openConnection();  \n" +
                "        conn.setConnectTimeout(5000);  \n" +
                "        conn.setRequestMethod(\"POST\");  \n" +
                "        conn.setDoOutput(true);  \n" +
                "        conn.setDoInput(true);\n" +
                "        conn.setUseCaches(false);  \n" +
                "        conn.connect();  \n" +
                "        byte[] data = (obj.toString()).getBytes();  \n" +
                "        OutputStream outputStream = conn.getOutputStream();  \n" +
                "        outputStream.write(data);\n" +
                "        outputStream.flush();  \n" +
                "        outputStream.close(); \n" +
                "        InputStream inStrm = conn.getInputStream(); \n" +
                "        " +
                "          \n}");
        CtMethod ctMethod2 = new CtMethod(
                CtClass.voidType, "HttpPostAliveMethod",
                [pool.get("java.lang.String"), pool.get("java.lang.String")] as CtClass[],
                ctClass);//(String url, String msg)
        ctMethod2.setModifiers(Modifier.PUBLIC);
        ctMethod2.setBody("{JSONObject  obj = new JSONObject(\$2);\n" +
                "        URL url=new URL(\$1);  \n" +
                "        HttpURLConnection conn = (HttpURLConnection) url.openConnection();  \n" +
                "        conn.setConnectTimeout(5000);  \n" +
                "        conn.setRequestMethod(\"POST\");  \n" +
                "        conn.setRequestProperty(\"Content-Type\", \"application/octet-stream\");  \n" +
                "        conn.setRequestProperty(\"Connection\", \"Keep-Alive\");  \n" +
                "        conn.setDoOutput(true);  \n" +
                "        conn.setDoInput(true);\n" +
                "        conn.setUseCaches(false);  \n" +
                "        conn.connect();  \n" +
                "        byte[] data = (obj.toString()).getBytes();  \n" +
                "        System.out.println(obj.toString());\n" +
                "        OutputStream outputStream = conn.getOutputStream();  \n" +
                "        outputStream.write(data);\n" +
                "        outputStream.flush();  \n" +
                "        InputStream inStrm = conn.getInputStream(); \n" +
                "        " +
                "          \n}");
        ctClass.addMethod(ctMethod);
        ctClass.addMethod(ctMethod2);
        JarInjector(ctClass, toFileNamne(ClassName))
    }

    private void addDeviceUtilsClass(){
        def ClassName = "com.injarctor.DeviceUtils"
        CtClass ctClass = pool.makeClass(ClassName);
        pool.importPackage("org.json.JSONObject");
        pool.importPackage("com.injarctor.MD5Utils");
        pool.importPackage("java.io.BufferedReader")
        pool.importPackage("java.io.FileInputStream")
        pool.importPackage("java.io.FileNotFoundException")
        pool.importPackage("java.io.FileReader")
        pool.importPackage("java.io.IOException")
        pool.importPackage("java.io.InputStreamReader")
        pool.importPackage("java.io.FileFilter");
        pool.importPackage("java.io.File");
        pool.importPackage("java.lang.String");
        pool.importPackage("java.util.regex.Pattern");
        String getDeviceMemoryInfo = "" +
                "public static String getDeviceMemoryInfo() {\n" +
                "        String Path = \"/proc/meminfo\";\n" +
                "        int result = 0;\n" +
                "        FileReader fr = null;\n" +
                "        BufferedReader br = null;\n" +
                "        String mem;\n" +
                "        try{\n" +
                "            fr = new FileReader(Path);\n" +
                "            br = new BufferedReader(fr);\n" +
                "            String text = br.readLine();\n" +
//                "            String[] strArr = text.split(\":\"); " +
                "            String[] strs=text.split(\":\");\n" +
                "            mem = strs[1].replace(\"KB\",\"\").trim();\n" +
//                "            result = Integer.parseInt(mem);\n" +
                "            br.close();\n" +
                "        }catch (Exception e){\n" +
                "            mem = e.toString();\n" +
                "        }\n" +
                "        return mem;\n" +
                "    }"
        String getCurCPU = "" +
                "public static int getCurCPU(){\n" +
                "        String CurPath = \"/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq\";" +
                "        int result = 0;\n" +
                "        FileReader fr = null;\n" +
                "        BufferedReader br = null;\n" +
                "        try{\n" +
                "            fr = new FileReader(CurPath);\n" +
                "            br = new BufferedReader(fr);\n" +
                "            String text = br.readLine();\n" +
                "            result = Integer.parseInt(text.trim());" +
                "            br.close();\n" +
                "        }catch (Exception e){\n" +
                "            result = -1;\n" +
                "        }\n" +
                "        return result;\n" +
                "    }"
        String getCPUCores = "" +
                "public static int getNumberOfCPUCores() {\n" +
                "  int cores=0;\n" +
                "  String path = \"/sys/devices/system/cpu/\";\n" +
                "  try {\n" +
                "    String[] files;\n" +
                "    File file = new File(path);\n" +
                "    File[] tempList = file.listFiles();\n" +
                "    for (int i = 0; i < tempList.length; i++) {\n" +
                "           if(Pattern.matches(\"cpu[0-9]\", tempList[i].getName())){\n" +
//                        "        if (tempList[i].toString().length()==4 || tempList[i].toString().length()==5) {\n" +
            "                       cores++;\n" +
//                            "          files.add(tempList[i].toString());\n" +
//                        "        }\n" +
                "           }" +
                "    }\n" +
                "  } catch (Exception e) {\n" +
                "    cores = -1;\n" +
                "  }\n" +
                "  return cores;\n" +
                "}"
        String getCPUABI="" +
                "public static String getCPUABI() {\n" +
                "    String CPUABI = null;\n" +
                "    if (CPUABI == null) {\n" +
                "        try {\n" +
                "            String os_cpuabi = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(\"getprop ro.product.cpu.abi\").getInputStream())).readLine();\n" +
                "            if (os_cpuabi.contains(\"x86\")) {\n" +
                "                CPUABI = \"x86\";\n" +
                "            } else if (os_cpuabi.contains(\"armeabi-v7a\") || os_cpuabi.contains(\"arm64-v8a\")) {\n" +
                "                CPUABI = \"armeabi\";\n" +
                "            } else {\n" +
                "                CPUABI = null;\n" +
                "            }\n" +
                "        } catch (Exception e) {\n"+
                "        }\n" +
                "    }\n" +
                "    return CPUABI;\n" +
                "}\n"
        String getARMCpuName = "" +
                "public static String getARMCpuName(){\n" +
                "      String str1 = \"/proc/cpuinfo\";\n" +
                "      String str2 = \"\";\n" +
                "      try {\n" +
                "        FileReader fr = new FileReader(str1);\n" +
                "        BufferedReader localBufferedReader = new BufferedReader(fr);\n" +
                "        while ((str2=localBufferedReader.readLine()) != null) {\n" +
                "          if (str2.contains(\"Hardware\")) {\n" +
                "            return str2.split(\":\")[1];\n" +
                "          }\n" +
                "        }\n" +
                "        localBufferedReader.close();\n" +
                "      } catch (IOException e) {\n" +
                "      }\n" +
                "      return null;\n" +
                "  }\n"
        String getX86CpuName = "" +
                "public static String getX86CpuName(){\n" +
                "      String str1 = \"/proc/cpuinfo\";\n" +
                "      String str2 = \"\";\n" +
                "      try {\n" +
                "        FileReader fr = new FileReader(str1);\n" +
                "        BufferedReader localBufferedReader = new BufferedReader(fr);\n" +
                "        while ((str2=localBufferedReader.readLine()) != null) {\n" +
                "          if (str2.contains(\"model name\")) {\n" +
                "            return str2.split(\":\")[1];\n" +
                "          }\n" +
                "        }\n" +
                "        localBufferedReader.close();\n" +
                "      } catch (IOException e) {\n" +
                "      }\n" +
                "      return null;\n" +
                "  }\n"
        String getCpuName = "" +
                "public static String getCpuName(String cpuabi){" +
                "        String cpuname = null;\n" +
                "        if (cpuabi.contains(\"arm\")){" +
                "               cpuname = getARMCpuName();\n" +
                "        }" +
                "        else{" +
                "               cpuname = getX86CpuName();\n" +
                "        }" +
                "        return cpuname;\n" +
                "}"
        String getDeviceAllInfo =
                " public static final JSONObject getDeviceAllInfo() {\n" +
                        "        String mem = getDeviceMemoryInfo();\n" +
                        "        int cpu_freq = getCurCPU();\n" +
                        "        int cpu_cores = getNumberOfCPUCores();\n" +
                        "        String cpuabi = getCPUABI();\n" +
                        "        String cpuname = getCpuName(cpuabi);\n" +
                        "        JSONObject jSONObject = new JSONObject();\n" +
                        "        jSONObject.put(\"type\", \"device\");\n" +
                        "        jSONObject.put(\"BRAND\", android.os.Build.BRAND);\n" +
                        "        jSONObject.put(\"PRODUCT\", android.os.Build.PRODUCT);\n" +
                        "        jSONObject.put(\"CPU_ABI\", cpuabi);\n" +
                        "        jSONObject.put(\"MODEL\", android.os.Build.MODEL);\n" +
                        "        jSONObject.put(\"HARDWARE\", android.os.Build.HARDWARE);\n" +
                        "        jSONObject.put(\"VERSION_HARDWARE\", cpuname);\n" +
                        "        jSONObject.put(\"VERSION_ANDROID\", android.os.Build.VERSION.RELEASE);\n" +
                        "        jSONObject.put(\"MEMORY\",mem);\n" +
                        "        jSONObject.put(\"CPU_FREQ\",cpu_freq);\n" +
                        "        jSONObject.put(\"CPU_CORES\",cpu_cores);\n" +
                        "        jSONObject.put(\"userID\",\"0\");\n" +
                        "        jSONObject.put(\"uid\",  MD5Utils.getMD5String(jSONObject.toString()));\n" +
                        "        return jSONObject;\n" +
                        "    }\n"
        addMethodInClass(ctClass, getCPUCores);
        addMethodInClass(ctClass, getCurCPU);
        addMethodInClass(ctClass, getCPUABI);
        addMethodInClass(ctClass, getARMCpuName);
        addMethodInClass(ctClass, getX86CpuName);
        addMethodInClass(ctClass, getCpuName);
        addMethodInClass(ctClass, getDeviceMemoryInfo);
        addMethodInClass(ctClass, getDeviceAllInfo);
        JarInjector(ctClass, toFileNamne(ClassName));

    }

    private void addScreenUtilsClass(){
        def ClassName = "com.injarctor.ScreenUtils"
        CtClass ctClass = pool.makeClass(ClassName);
        pool.importPackage("android.graphics.Bitmap");
        pool.importPackage("android.view.View");
        pool.importPackage("android.app.Activity");
        pool.importPackage("android.content.Context");
        pool.importPackage("android.view.WindowManager");
        pool.importPackage("android.util.DisplayMetrics");
        String getScreenWidth="" +
                "public static int getScreenWidth(Context context)\n" +
                "  {\n" +
                "    WindowManager wm = (WindowManager) context\n" +
                "        .getSystemService(Context.WINDOW_SERVICE);\n" +
                "    DisplayMetrics outMetrics = new DisplayMetrics();\n" +
                "    wm.getDefaultDisplay().getMetrics(outMetrics);\n" +
                "    return outMetrics.widthPixels;\n" +
                "  }\n" +
                ""
        String getScreenHeight="" +
                "public static int getScreenHeight(Context context)\n" +
                "  {\n" +
                "    WindowManager wm = (WindowManager) context\n" +
                "        .getSystemService(Context.WINDOW_SERVICE);\n" +
                "    DisplayMetrics outMetrics = new DisplayMetrics();\n" +
                "    wm.getDefaultDisplay().getMetrics(outMetrics);\n" +
                "    return outMetrics.heightPixels;\n" +
                "  }" +
                ""
        String snapShotWithStatusBar="" +
                "public static Bitmap snapShotWithStatusBar(Activity activity)\n" +
                "  {\n" +
                "    View view = activity.getWindow().getDecorView();\n" +
                "    view.setDrawingCacheEnabled(true);\n" +
                "    view.buildDrawingCache();\n" +
                "    Bitmap bmp = view.getDrawingCache();\n" +
                "    int width = getScreenWidth(activity);\n" +
                "    int height = getScreenHeight(activity);\n" +
                "    Bitmap bp = null;\n" +
                "    bp = Bitmap.createBitmap(bmp, 0, 0, width, height);\n" +
                "    view.destroyDrawingCache();\n" +
                "    return bp;\n" +
                "\n" +
                "  }" +
                ""
        addMethodInClass(ctClass, getScreenWidth);
        addMethodInClass(ctClass, getScreenHeight);
        addMethodInClass(ctClass, snapShotWithStatusBar);
        JarInjector(ctClass, toFileNamne(ClassName));
    }

    private void addMemoryUtilsClass(){
        def ClassName = "com.injarctor.MemoryUtils"
        CtClass ctClass = pool.makeClass(ClassName);
        pool.importPackage("org.json.JSONObject");
        pool.importPackage("java.lang.Runtime");
        pool.importPackage("android.app.ActivityManager");
        pool.importPackage("com.injarctor.DeviceUtils");
        String getMemory = "" +
                "public static JSONObject getMemoryInfo(){ \n" +
                "   JSONObject jSONObject = new JSONObject();\n" +
                "   Runtime runtime = Runtime.getRuntime(); \n" +
                "   long freeMemory = runtime.freeMemory(); \n" +
                "   long totalMemory = runtime.totalMemory(); \n" +
                "   long maxMemory = runtime.maxMemory(); \n" +
                "   jSONObject.put(\"type\", \"device\");\n" +
                "   jSONObject.put(\"freeMemory\", freeMemory);\n" +
                "   jSONObject.put(\"totalMemory\", totalMemory);\n" +
                "   jSONObject.put(\"maxMemory\", maxMemory);\n" +
                "   jSONObject.put(\"uid\", DeviceUtils.getDeviceAllInfo().getString(\"uid\"));\n" +
                "   return jSONObject;" +
                "}"
        String getDeviceMemoryInfo = "" +
                "public static int getDeviceMemoryInfo() {\n" +
                "        String str1 = \"/proc/meminfo\";\n" +
                "        String str2;\n" +
                "        String[] arrayOfString;\n" +
            "            FileReader localFileReader = new FileReader(str1);\n" +
            "            BufferedReader localBufferedReader = new BufferedReader(localFileReader, 8192);\n" +
            "            str2 = localBufferedReader.readLine();\n" +
            "            arrayOfString = str2.split(\"\\\\s+\");\n" +
            "            int i = Integer.valueOf(arrayOfString[1]).intValue();\n" +
            "            localBufferedReader.close();\n" +
                "        return i;\n" +
                "    }"
        addMethodInClass(ctClass, getMemory);
        addMethodInClass(ctClass, getDeviceMemoryInfo);
        JarInjector(ctClass, toFileNamne(ClassName));
    }

    private void addCPUUtilsClass(){
        def ClassName = "com.injarctor.CPUUtils"
        CtClass ctClass = pool.makeClass(ClassName);
        pool.importPackage("org.json.JSONObject");
        pool.importPackage("java.lang.Runtime");
        pool.importPackage("java.lang.String");
        pool.importPackage("android.app.ActivityManager");
        pool.importPackage("com.injarctor.DeviceUtils");
        pool.importPackage("java.io.BufferedReader")
        pool.importPackage("java.io.FileInputStream")
        pool.importPackage("java.io.FileNotFoundException")
        pool.importPackage("java.io.FileReader")
        pool.importPackage("java.io.IOException")
        pool.importPackage("java.io.InputStreamReader")
        String getCurCPU = "" +
                "public static int getCurCPU(){\n" +
                "        String CurPath = \"/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq\";" +
                "        int result = 0;\n" +
                "        FileReader fr = null;\n" +
                "        BufferedReader br = null;\n" +
                "        try{\n" +
                "            fr = new FileReader(CurPath);\n" +
                "            br = new BufferedReader(fr);\n" +
                "            String text = br.readLine();\n" +
                "            result = Integer.parseInt(text.trim());\n" +
                "        }catch (FileNotFoundException e){\n" +
                "            e.printStackTrace();\n" +
                "        }catch (IOException e){\n" +
                "            e.printStackTrace();\n" +
                "        }finally {\n" +
                "            if (fr != null)\n" +
                "                try{\n" +
                "                    fr.close();\n" +
                "                }catch (IOException e){\n" +
                "                    e.printStackTrace();\n" +
                "                }\n" +
                "            if (br != null)\n" +
                "                try{\n" +
                "                    br.close();\n" +
                "                }catch (IOException e){\n" +
                "                    e.printStackTrace();\n" +
                "                }\n" +
                "        }\n" +
                "        return result;\n" +
                "    }"
        String getMaxCPU = "" +
                "public static int getMaxCPU(){\n" +
                "        String MaxPath = \"/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq\";" +
                "        int result = 0;\n" +
                "        FileReader fr = null;\n" +
                "        BufferedReader br = null;\n" +
                "        try{\n" +
                "            fr = new FileReader(MaxPath);\n" +
                "            br = new BufferedReader(fr);\n" +
                "            String text = br.readLine();\n" +
                "            result = Integer.parseInt(text.trim());\n" +
                "        }catch (FileNotFoundException e){\n" +
                "            e.printStackTrace();\n" +
                "        }catch (IOException e){\n" +
                "            e.printStackTrace();\n" +
                "        }finally {\n" +
                "            if (fr != null)\n" +
                "                try{\n" +
                "                    fr.close();\n" +
                "                }catch (IOException e){\n" +
                "                    e.printStackTrace();\n" +
                "                }\n" +
                "            if (br != null)\n" +
                "                try{\n" +
                "                    br.close();\n" +
                "                }catch (IOException e){\n" +
                "                    e.printStackTrace();\n" +
                "                }\n" +
                "        }\n" +
                "        return result;\n" +
                "    }"
        String getMinCPU = "" +
                "public static int getMinCPU(){\n" +
                "        String MinPath = \"/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq\";" +
                "        int result = 0;\n" +
                "        FileReader fr = null;\n" +
                "        BufferedReader br = null;\n" +
                "        try{\n" +
                "            fr = new FileReader(MinPath);\n" +
                "            br = new BufferedReader(fr);\n" +
                "            String text = br.readLine();\n" +
                "            result = Integer.parseInt(text.trim());\n" +
                "        }catch (FileNotFoundException e){\n" +
                "            e.printStackTrace();\n" +
                "        }catch (IOException e){\n" +
                "            e.printStackTrace();\n" +
                "        }finally {\n" +
                "            if (fr != null)\n" +
                "                try{\n" +
                "                    fr.close();\n" +
                "                }catch (IOException e){\n" +
                "                    e.printStackTrace();\n" +
                "                }\n" +
                "            if (br != null)\n" +
                "                try{\n" +
                "                    br.close();\n" +
                "                }catch (IOException e){\n" +
                "                    e.printStackTrace();\n" +
                "                }\n" +
                "        }\n" +
                "        return result;\n" +
                "    }"
        String getCPUInfo = "" +
                "public static JSONObject getCPUInfo(){ \n" +
                "   JSONObject jSONObject = new JSONObject();\n" +
                "   Integer CurCPU = getCurCPU()+\"\";\n" +
                "   Integer maxCPU = getMaxCPU()+\"\";\n" +
                "   Integer minCPU = getMinCPU()+\"\";\n" +
                "   jSONObject.put(\"type\", \"device\");\n" +
                "   jSONObject.put(\"currentCPU\", CurCPU);\n" +
                "   jSONObject.put(\"maxCPU\", maxCPU);\n" +
                "   jSONObject.put(\"minCPU\", minCPU);\n" +
                "   jSONObject.put(\"uid\", DeviceUtils.getDeviceAllInfo().getString(\"uid\"));\n" +
                "   return jSONObject;" +
                "}"
        addMethodInClass(ctClass, getCurCPU);
        addMethodInClass(ctClass, getMaxCPU);
        addMethodInClass(ctClass, getMinCPU);
        addMethodInClass(ctClass, getCPUInfo);
        JarInjector(ctClass, toFileNamne(ClassName));
    }

    private void addHttpHandlerClass(){
        def ClassName = "com.injarctor.HttpHandler"
        CtClass ctClass = pool.makeClass(ClassName);
        pool.importPackage("com.injarctor.HttpConnection")
        ctClass.addInterface(pool.get("java.lang.Runnable"));
        CtField log = new CtField(pool.get("java.lang.String"), "log", ctClass);
        log.setModifiers(Modifier.PRIVATE);
        ctClass.addField(log);
        ctClass.addMethod(CtNewMethod.getter("getLog",log));
        ctClass.addMethod(CtNewMethod.setter("setLog",log));
        String PostMsg =
                "public static void PostMsg(String msg){\n" +
                        "    HttpConnection conn = new HttpConnection();\n" +
                        "    conn.HttpPostMethod(\"http://21i93061o2.iok.la/api/v1/collector/remote\",msg);\n" +
                        "}"
        String run =
                "public void run() {\n" +
                        "try{" +
                        "            PostMsg(this.log);\n" +
                        "}catch(java.lang.Exception e){" +
                        "System.out.println(\"http Failed! \"+e);" +
                        "}" +
                        "    }"
        addMethodInClass(ctClass, PostMsg);
        addMethodInClass(ctClass, run);
        JarInjector(ctClass, toFileNamne(ClassName));
    }

    private void addHttpAliveHandlerClass(){
        def ClassName = "com.injarctor.HttpAliveHandler"
        CtClass ctClass = pool.makeClass(ClassName);
        pool.importPackage("com.injarctor.HttpConnection");
        pool.importPackage("java.util.Timer");
        ctClass.addInterface(pool.get("java.lang.Runnable"));
        CtField log = new CtField(pool.get("java.lang.String"), "log", ctClass);
        CtField timer = new CtField(pool.get("java.util.Timer"), "timer", ctClass);
        log.setModifiers(Modifier.PRIVATE);
        ctClass.addField(log);
        ctClass.addField(timer);
        ctClass.addMethod(CtNewMethod.getter("getLog",log));
        ctClass.addMethod(CtNewMethod.setter("setLog",log));
        String AliveMsg =
                "public static void AliveMsg(String msg){\n" +
                        "    HttpConnection conn = new HttpConnection();\n" +
                        "    conn.HttpPostAliveMethod(\"http://21i93061o2.iok.la/api/v1/collector/remote\",msg);\n" +
                        "}"
        String run =
                "public void run() {\n" +
                        "try{" +
                        "   while(true){" +
                        "       long t = 500L;\n" +
                        "       Thread.sleep(t);" +
                        "       AliveMsg(this.log);\n" +
                        "   }" +
                        "}catch(java.lang.Exception e){" +
                        "System.out.println(\"http Failed! \"+e);" +
                        "}" +
                        "    }"
        addMethodInClass(ctClass, AliveMsg);
        addMethodInClass(ctClass, run);
        JarInjector(ctClass, toFileNamne(ClassName));
    }

    private void addInJARctorClass(){
        def ClassName = "com.injarctor.InJARctor"
        CtClass ctClass = pool.makeClass(ClassName);
        pool.importPackage("org.json.JSONObject");
        pool.importPackage("android.app.Activity");
        pool.importPackage("com.injarctor.DeviceUtils");
        pool.importPackage("com.injarctor.MemoryUtils");
        pool.importPackage("com.injarctor.HttpHandler");
        pool.importPackage("com.injarctor.HttpAliveHandler");
        pool.importPackage("android.graphics.Bitmap");
        pool.importPackage("com.injarctor.ScreenUtils");
        pool.importPackage("com.injarctor.CPUUtils");
        String SessionStatusBuilder =
                " public static JSONObject SessionStatusBuilder(java.lang.String str, Activity activity, long time) {\n" +
                "        JSONObject jSONObject = new JSONObject();\n" +
                "        jSONObject.put(\"type\", \"session\");\n" +
                "        jSONObject.put(\"status\", str);\n" +
                "        jSONObject.put(\"class\", activity.getClass().getName());\n" +
                "        jSONObject.put(\"timestamp\", time);\n" +
                        "System.out.println(str+\"! \"+activity.getClass().getName());" +
                "        return jSONObject;\n" +
                "    }\n"
        String SessionStatusBuilder_runtime =
                " public static JSONObject SessionStatusBuilder(java.lang.String str, Activity activity, long runtime, long time) {\n" +
                "        JSONObject jSONObject = new JSONObject();\n" +
                "        jSONObject.put(\"type\", \"session\");\n" +
                "        jSONObject.put(\"status\", str);\n" +
                "        jSONObject.put(\"class\", activity.getClass().getName());\n" +
                "        jSONObject.put(\"timestamp\", time);\n" +
                "        jSONObject.put(\"runtime\", runtime);\n" +
                "        jSONObject.put(\"uid\", DeviceUtils.getDeviceAllInfo().getString(\"uid\"));\n" +
                "        System.out.println(str+\"! \"+activity.getClass().getName());" +
                "        return jSONObject;\n" +
                "    }\n"
        String SessionStatusBuilder_all =
                " public static JSONObject SessionStatusBuilder(java.lang.String str, java.lang.String classname, java.lang.String methoddescript, long runtime, long time, long time2) {\n" +
                        "        JSONObject jSONObject = new JSONObject();\n" +
                        "        jSONObject.put(\"type\", \"session\");\n" +
                        "        jSONObject.put(\"status\", str);\n" +
                        "        jSONObject.put(\"class\", classname);\n" +
                        "        jSONObject.put(\"descript\", methoddescript);\n" +
                        "        jSONObject.put(\"timestamp\", time);\n" +
                        "        jSONObject.put(\"timestamp2\", time2);\n" +
                        "        jSONObject.put(\"runtime\", runtime);\n" +
                        "        jSONObject.put(\"uid\", DeviceUtils.getDeviceAllInfo().getString(\"uid\"));\n" +
                        "        return jSONObject;\n" +
                        "    }\n"
        String DeviceInfoReader = "" +
                "public static void DeviceInfoReader() {\n" +
                        "HttpHandler hh = new HttpHandler();\n" +
                        "JSONObject json = DeviceUtils.getDeviceAllInfo();\n;" +
                        "hh.setLog(json.toString());\n" +
                        "new Thread(hh).start();\n" +
                        "    }"
        String MemoryInfoReader = "" +
                "public static void MemoryInfoReader() {\n" +
                        "HttpAliveHandler hh = new HttpAliveHandler();\n" +
                        "JSONObject json = MemoryUtils.getMemoryInfo();\n;" +
                        "hh.setLog(json.toString());\n" +
                        "new Thread(hh).start();\n" +
                "    }"
        String CPUInfoReader = "" +
                "public static void CPUInfoReader() {\n" +
                "HttpAliveHandler hh = new HttpAliveHandler();\n" +
                "JSONObject json = CPUUtils.getCPUInfo();\n;" +
                "hh.setLog(json.toString());\n" +
                "new Thread(hh).start();\n" +
                "    }"
        //TODO snapShot
        String snapShot = "" +
                "public static void snapShot(Activity activity) {\n" +
                "HttpHandler hh = new HttpHandler();\n" +
                "JSONObject json = DeviceUtils.getDeviceAllInfo();\n" +
                "Bitmap bp = ScreenUtils.snapShotWithStatusBar(activity);\n" +
                "hh.setLog(json.toString());\n" +
                "new Thread(hh).start();\n" +
                "    }"
        addMethodInClass(ctClass, SessionStatusBuilder);
        addMethodInClass(ctClass, SessionStatusBuilder_runtime);
        addMethodInClass(ctClass, SessionStatusBuilder_all);
        addMethodInClass(ctClass, DeviceInfoReader);
//        addMethodInClass(ctClass, MemoryInfoReader);
//        addMethodInClass(ctClass, CPUInfoReader);
//        addMethodInClass(ctClass, snapShot);
        JarInjector(ctClass, toFileNamne(ClassName));
    }

    private void addAliveActivityClass(){
        def ClassName = "com.injarctor.AliveActivity"
        CtClass ctClass = pool.makeClass(ClassName);
        pool.importPackage("com.injarctor.InJARctor");
        ctClass.addInterface(pool.get("android.support.v7.app.AppCompatActivity"));
        String onCreate = " " +
                "public void onCreate() {" +
                "   InJARctor.MemoryInfoReader();" +
                "}"
        addMethodInClass(ctClass, onCreate);
        JarInjector(ctClass, toFileNamne(ClassName));
    }
    private void addActivityLifecycleClass(){
        def ClassName = "com.injarctor.ActivityLifecycle"
        CtClass ctClass = pool.makeClass(ClassName);
        pool.importPackage("android.app.Activity");
        pool.importPackage("org.json.JSONObject");
        pool.importPackage("com.injarctor.InJARctor")
        pool.importPackage("com.injarctor.HttpHandler");
        pool.importPackage("java.util.Calendar");
        pool.importPackage("java.lang.Long");
        CtField EntryTime = new CtField(pool.get("java.lang.String"), "EntryTime", ctClass);
        EntryTime.setModifiers(Modifier.PRIVATE);
        ctClass.addField(EntryTime);
        ctClass.addMethod(CtNewMethod.getter("getEntryTime",EntryTime));
        ctClass.addMethod(CtNewMethod.setter("setEntryTime",EntryTime));
        String onCreateEntry =
                "public static void onCreateEntry(Activity activity) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onCreateEntry\", activity, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"

        String onCreateExit =
                "public static void onCreateExit(Activity activity) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onCreateExit\", activity, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"
        String onCreateDelta =
                "public static void onCreateDelta(Activity activity, long runtime) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onCreateDelta\", activity, runtime, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"
        addMethodInClass(ctClass, onCreateEntry);
        addMethodInClass(ctClass, onCreateExit);
        addMethodInClass(ctClass, onCreateDelta);
        String onStartEntry =
                "public static void onStartEntry(Activity activity) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onStartEntry\", activity, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"

        String onStartExit =
                "public static void onStartExit(Activity activity) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onStartExit\", activity, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"
        String onStartDelta =
                "public static void onStartDelta(Activity activity, long runtime) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onStartDelta\", activity, runtime, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"
        addMethodInClass(ctClass, onStartEntry);
        addMethodInClass(ctClass, onStartExit);
        addMethodInClass(ctClass, onStartDelta);
        String onDestroyEntry =
                "public static void onDestroyEntry(Activity activity) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onDestroyEntry\", activity, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"

        String onDestroyExit =
                "public static void onDestroyExit(Activity activity) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onDestroyExit\", activity, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"
        String onDestroyDelta =
                "public static void onDestroyDelta(Activity activity, long runtime) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onDestroyDelta\", activity, runtime, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"
        addMethodInClass(ctClass, onDestroyEntry);
        addMethodInClass(ctClass, onDestroyExit);
        addMethodInClass(ctClass, onDestroyDelta);
        String onResumeEntry =
                "public static void onResumeEntry(Activity activity) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onResumeEntry\", activity, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"

        String onResumeExit =
                "public static void onResumeExit(Activity activity) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onResumeExit\", activity, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"
        String onResumeDelta =
                "public static void onResumeDelta(Activity activity, long runtime) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onResumeDelta\", activity, runtime, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"
        addMethodInClass(ctClass, onResumeEntry);
        addMethodInClass(ctClass, onResumeExit);
        addMethodInClass(ctClass, onResumeDelta);
        String onStopEntry =
                "public static void onStopEntry(Activity activity) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onStopEntry\", activity, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"

        String onStopExit =
                "public static void onStopExit(Activity activity) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onStopExit\", activity, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"
        String onStopDelta =
                "public static void onStopDelta(Activity activity, long runtime) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onStopDelta\", activity, runtime, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"
        addMethodInClass(ctClass, onStopEntry);
        addMethodInClass(ctClass, onStopExit);
        addMethodInClass(ctClass, onStopDelta);
        String onPauseEntry =
                "public static void onPauseEntry(Activity activity) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onPauseEntry\", activity, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"

        String onPauseExit =
                "public static void onPauseExit(Activity activity) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onPauseExit\", activity, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"
        String onPauseDelta =
                "public static void onPauseDelta(Activity activity, long runtime) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onPauseDelta\", activity, runtime, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"
        addMethodInClass(ctClass, onPauseEntry);
        addMethodInClass(ctClass, onPauseExit);
        addMethodInClass(ctClass, onPauseDelta);
        String onRestartEntry =
                "public static void onRestartEntry(Activity activity) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onRestartEntry\", activity, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"

        String onRestartExit =
                "public static void onRestartExit(Activity activity) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onRestartExit\", activity, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"
        String onRestartDelta =
                "public static void onRestartDelta(Activity activity, long runtime) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = Calendar.getInstance().getTimeInMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(\"onRestartDelta\", activity, runtime, time);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"
        addMethodInClass(ctClass, onRestartEntry);
        addMethodInClass(ctClass, onRestartExit);
        addMethodInClass(ctClass, onRestartDelta);
        String AllMethodsDelta =
                "public static void AllMethodsDelta(String MethodName, String ClassName, String MethodDescript, long runtime) {\n" +
                        "HttpHandler hh = new HttpHandler()\n;" +
                        "long time = System.nanoTime();\n" +
                        "long time2 = System.currentTimeMillis();\n" +
                        "JSONObject json = InJARctor.SessionStatusBuilder(MethodName, ClassName, MethodDescript, runtime, time, time2);\n;" +
                        "hh.setLog(json.toString());" +
                        "new Thread(hh).start();\n" +
                        "    }"
        addMethodInClass(ctClass, AllMethodsDelta);
        JarInjector(ctClass, toFileNamne(ClassName));
    }

    private void InsertTimeMonitorAll(CtClass ctClass){
//        List<String> AllMethodsName = getAllMethodName(ctClass);
//        for(String methodname in AllMethodsName) {
//            TimeMonitor(ctClass, methodname);
//        }
        CtMethod[] AllMethods = getAllMethod(ctClass);
        for(CtMethod method in AllMethods) {
            try {
                TimeMonitor(ctClass, method);
            }catch(Exception e){

            }
        }
        JarInjector(ctClass, toFileNamne(ctClass.getName()));
    }

    private void TimeMonitor(CtClass ctClass, CtMethod method){
        try {
            pool.importPackage("com.injarctor.ActivityLifecycle");
//            CtMethod method = ctClass.getDeclaredMethod(MethodName);
            MethodInfo methodInfo = method.getMethodInfo();
            String MethodDescript = methodInfo.getDescriptor();
            String MethodName = method.getName();
            if (ctClass.isFrozen()) {
                ctClass.defrost();
            }
            method.addLocalVariable("begin", CtClass.longType);
            method.addLocalVariable("end", CtClass.longType);
//            method.insertBefore("begin=System.currentTimeMillis();\n");
//            method.insertAfter("end=System.currentTimeMillis();\n");
            method.insertBefore("begin=System.nanoTime();\n");
            method.insertAfter("end=System.nanoTime();\n");
            String Delta = "{" +
                    "ActivityLifecycle.AllMethodsDelta(\""+MethodName+"Delta\",\""+ctClass.getName()+"\",\""+MethodDescript+"\", end-begin);" +
                    "}"
            method.insertAfter(Delta);
            println(ctClass.getName()+" "+MethodName+" injected ");

        }catch (NotFoundException e1) {
//            e1.printStackTrace();
//            println(ctClass.getName()+" NO "+MethodName)
        }catch (Exception e2){
            e2.printStackTrace();
//                println(" failed to get ")
        }
    }

    private void InsertTimeMonitor(CtClass ctClass, String MethodName){
        try {
            if (ctClass.isFrozen()) {
                ctClass.defrost();
            }
            CtMethod method = ctClass.getDeclaredMethod(MethodName);
            pool.importPackage("com.injarctor.ActivityLifecycle");
            String Entry = "{" +
                    "ActivityLifecycle." + MethodName + "Entry(\$0);" +
                    "}"
            method.insertBefore(Entry);
            String Exit = "{" +
                    "ActivityLifecycle." + MethodName + "Exit(\$0);" +
                    "}"
            method.insertAfter(Exit);
            method.addLocalVariable("begin", CtClass.longType);
            method.addLocalVariable("end", CtClass.longType);
            method.insertBefore("begin=System.currentTimeMillis();\n");
            method.insertAfter("end=System.currentTimeMillis();\n");
            String Delta = "{" +
                    "ActivityLifecycle." + MethodName + "Delta(\$0, end-begin);" +
                    "}"
            method.insertAfter(Delta);
            JarInjector(ctClass, toFileNamne(ctClass.getName()));
            println(ctClass.getName()+" "+MethodName+" injected ")
        }catch (NotFoundException e1) {
//            e1.printStackTrace();
//            println(ctClass.getName()+" NO "+MethodName)
        }catch (Exception e2){
            e2.printStackTrace();
//                println(" failed to get ")
        }
    }

    private void InsertSnapShot(CtClass ctClass,CtMethod method){
        pool.importPackage("com.injarctor.ScreenUtils");
        String snapShotWithStatusBar = "" +
                "" +
                ""
        method.insertBefore(snapShotWithStatusBar);
        JarInjector(ctClass, toFileNamne(ctClass.getName()));

    }
    private void InsertDeviceInfoReader(CtClass ctClass, String MethodName){
        try{
            CtMethod method = ctClass.getDeclaredMethod(MethodName);
            pool.importPackage("com.injarctor.InJARctor");
            String InfoReader = "{" +
                    "InJARctor.DeviceInfoReader();" +
                    "}"
            method.insertBefore(InfoReader);
        }catch (NotFoundException e1) {
//            e1.printStackTrace();
    //                println(classname+" failed to get onclick")
        }catch (Exception e2){
            e2.printStackTrace();
    //                println(" failed to get ")
        }
    }

    private void InsertMemoryInfoReader(CtClass ctClass, String MethodName){
        try{
            CtMethod method = ctClass.getDeclaredMethod(MethodName);
            pool.importPackage("com.injarctor.InJARctor");
            String InfoReader = "{" +
                    "InJARctor.MemoryInfoReader();" +
                    "}"
            method.insertBefore(InfoReader);
        }catch (NotFoundException e1) {
//            e1.printStackTrace();
            //                println(classname+" failed to get onclick")
        }catch (Exception e2){
            e2.printStackTrace();
            //                println(" failed to get ")
        }
    }

    private void InsertCPUInfoReader(CtClass ctClass, String MethodName){
        try{
            CtMethod method = ctClass.getDeclaredMethod(MethodName);
            pool.importPackage("com.injarctor.InJARctor");
            String InfoReader = "{" +
                    "InJARctor.CPUInfoReader();" +
                    "}"
            method.insertBefore(InfoReader);
        }catch (NotFoundException e1) {
//            e1.printStackTrace();
            //                println(classname+" failed to get onclick")
        }catch (Exception e2){
            e2.printStackTrace();
            //                println(" failed to get ")
        }
    }

    private void ExtractMethodsID(CtClass ctClass){
        try{
            CtMethod[] AllMethods = getAllMethod(ctClass);
            File file = new File("methods_id.csv");
            FileOutputStream fos = null;
            if(!file.exists()){
                file.createNewFile();
                fos = new FileOutputStream(file);
            }else{
                fos = new FileOutputStream(file,true);
            }
            BufferedReader reader = new BufferedReader(new FileReader(file));
            BufferedWriter writeText = new BufferedWriter(new FileWriter(file, true));
            reader.readLine();
            String line = null;
            int id = 0;
            while((reader.readLine())!=null) {
                id += 1;
            }
            for(CtMethod method in AllMethods) {
                id += 1;
                MethodInfo methodInfo = method.getMethodInfo();
                String MethodDescript = methodInfo.getDescriptor();
                writeText.write(ctClass.getName()+"."+method.getName()+MethodDescript+","+id.toString());
                writeText.newLine();    //换行
            }
            writeText.flush();
            writeText.close();

            while((line=reader.readLine())!=null) {
                println(line)
            }
        }catch (FileNotFoundException e){
            System.out.println("没有找到指定文件");
        }catch (IOException e){
            System.out.println("文件读写出错");
        }
    }
    //修改JAR主函数
    private void mainModify(String filePath){
        println("Start")
        pool.insertClassPath(FilePath)
        println("insertClassPath: "+FilePath);
        //添加inJARctor工具类
        try{
            addHttpConnectionClass();
            addMD5UtilsClass();
            addDeviceUtilsClass();
//            addScreenUtilsClass();
//            addMemoryUtilsClass();
//            addCPUUtilsClass();
            addHttpHandlerClass();
            addHttpAliveHandlerClass();
            addInJARctorClass();
            addActivityLifecycleClass();
//            addAliveActivityClass();
        }catch(Exception e){
            e.printStackTrace();
        }
        List<String> AllClassName = getAllClassName(filePath)
        List<String> MethodNames = new ArrayList<>(Arrays.asList("onCreate", "onStart", "onDestroy", "onResume"));
        for(String classname in AllClassName){
            if(!classname.contains("injarctor") && !classname.equals("R")){
                try{
                    CtClass ctClass = pool.get(classname)
                    InsertDeviceInfoReader(ctClass, "attachBaseContext");
                    InsertTimeMonitorAll(ctClass);
//                    ExtractMethodsID(ctClass);
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
//        for(String classname in AllClassName){
//            if(!classname.contains("android")) {
//                try {
//                    CtClass ctClass = pool.get(classname)
//                    InsertTimeMonitor(ctClass, "onCreate");
//                } catch (Exception e) {
////                    e.printStackTrace();
//                }
//            }
//        }
//        for(String classname in AllClassName){
//            if(!classname.contains("android")) {
//                try {
//                    CtClass ctClass = pool.get(classname)
//                    InsertTimeMonitor(ctClass, "onStart");
//                } catch (Exception e) {
////                    e.printStackTrace();
//                }
//            }
//        }
//        for(String classname in AllClassName){
//            if(!classname.contains("android")) {
//                try {
//                    CtClass ctClass = pool.get(classname)
//                    InsertTimeMonitor(ctClass, "onDestroy");
//                } catch (Exception e) {
////                    e.printStackTrace();
//                }
//            }
//        }
//        for(String classname in AllClassName){
//            if(!classname.contains("android")) {
//                try{
//                CtClass ctClass = pool.get(classname)
//                InsertTimeMonitor(ctClass, "onResume");
//                }catch(Exception e){
//    //                    e.printStackTrace();
//                }
//            }
//        }
//        for(String MethodName in MethodNames){
//            for(String classname in AllClassName){
//                CtClass ctClass = pool.get(classname)
//                InsertTimeMonitorAll(ctClass, MethodName);
//            }
//        }
    }
}