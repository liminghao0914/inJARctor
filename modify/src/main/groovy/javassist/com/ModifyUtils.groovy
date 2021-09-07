package javassist.com

import com.android.SdkConstants;

//TODO 优化做
class ModifyUtils {
    //名称转换
    static String toFileNamne(String ClassName){
        return ClassName.replaceAll("\\.", "/")+ SdkConstants.DOT_CLASS
    }

    static String toClassName(String fileName){
        def className = fileName.replace("/", ".")
        return className.replace(SdkConstants.DOT_CLASS, "")
    }
}
