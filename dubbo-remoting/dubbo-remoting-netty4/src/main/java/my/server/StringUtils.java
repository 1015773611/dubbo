package my.server;

/**
 * @author geyu
 * @date 2021/2/1 20:35
 */
public class StringUtils {
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

//    public static String toString(Throwable e){
//
//    }
}
